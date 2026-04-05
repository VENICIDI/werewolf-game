"""
狼人杀 Agent 基类

每个 AI 玩家是一个独立的 WerewolfAgent 实例。

核心架构:
- MemorySystem: 三层记忆（工作/情景/语义）
- RoleStrategy: 角色策略（策略模式）
- PersonaProfile: 人格档案
- LLM/RAG: Phase 4 接入
"""
import logging
from typing import List, Optional, Dict, Any

from models.game_models import Role, GameState, NightActionDecision, SpeechDecision, VoteDecision
from models.agent_models import Persona
from models.event_models import GameEvent
from services.llm_service import LLMService
from services.rag_service import RAGService
from agents.memory.memory_system import MemorySystem
from agents.strategies import create_strategy, RoleStrategy
from agents.persona.persona_profiles import get_persona, PersonaProfile

logger = logging.getLogger(__name__)


class WerewolfAgent:
    """
    狼人杀 Agent 基类
    
    核心职责:
    - 存储 Agent 基本信息（game_id, player_id, role, persona）
    - 通过 MemorySystem 管理三层记忆
    - 通过 RoleStrategy 实现角色差异化行为
    - 通过 PersonaProfile 影响发言风格
    - 提供决策接口（夜间行动/发言/投票）
    
    Phase 2 实现:
    - ✅ 三层记忆系统（工作记忆/情景记忆/语义记忆）
    - ✅ 6种角色策略（狼人/预言家/女巫/猎人/守卫/村民）
    - ✅ 4种人格档案（激进/分析/谨慎/魅力）
    - LLM 发言: Phase 4 接入（当前仍用模板）
    """
    
    def __init__(
        self,
        game_id: str,
        player_id: int,
        role: Role,
        persona: Persona,
        llm_service: LLMService,
        rag_service: RAGService,
        teammates: Optional[List[int]] = None,
        seat_number: int = 0,
    ):
        # 基本信息
        self.game_id = game_id
        self.player_id = player_id
        self.role = role
        self.seat_number = seat_number
        self.llm_service = llm_service
        self.rag_service = rag_service
        
        # 狼人队友（仅狼人有）
        self.teammates: List[int] = teammates or []
        
        # 记忆系统
        self.memory = MemorySystem(my_player_id=player_id)
        
        # 角色策略
        self.strategy: RoleStrategy = create_strategy(role.value)
        
        # 人格档案
        self.persona_profile: PersonaProfile = get_persona(persona.value)
        
        logger.info(
            f"Agent 创建: Game={game_id}, Player={player_id}, "
            f"Role={role.value}, Persona={self.persona_profile.name}, "
            f"Strategy={self.strategy.role_name}"
        )
    
    def init_game(self, player_ids: List[int], seat_map: Optional[Dict[int, int]] = None):
        """游戏开始时初始化记忆"""
        self.memory.init_game(player_ids, seat_map)
        
        # 如果是狼人，标记队友为已知身份
        if self.role == Role.WEREWOLF and self.teammates:
            for tm in self.teammates:
                self.memory.semantic.set_known_role(tm, "WEREWOLF")
                # 队友嫌疑设为0（对自己来说）
                self.memory.semantic.update_suspicion(tm, -1.0, "狼人队友")
    
    async def receive_event(self, event: GameEvent) -> None:
        """
        接收游戏事件 (Perceive)
        
        事件分发到记忆系统，再分发到角色策略。
        """
        logger.debug(f"[Agent {self.player_id}] 收到事件: {event.event_type.value}")
        
        # 1. 记忆系统处理事件
        self.memory.process_event(event)
        
        # 2. 角色策略处理事件（可能有角色特有逻辑）
        self.strategy.update_on_event(self, event)
    
    async def decide_night_action(self, game_state: GameState) -> NightActionDecision:
        """
        夜间行动决策 (Act)
        
        委托给角色策略处理。
        """
        logger.info(f"[Agent {self.player_id}] 夜间行动决策 ({self.strategy.role_name})")
        
        # 更新工作记忆
        self.memory.working.update_phase(game_state.round, game_state.phase.value)
        
        # 委托给角色策略
        decision = self.strategy.plan_night_action(self, game_state)
        
        logger.info(
            f"[Agent {self.player_id}] 决策: action={decision.reason}, "
            f"target={decision.target_id}, confidence={decision.confidence:.2f}"
        )
        return decision
    
    async def generate_speech(
        self,
        game_state: GameState,
        context: str = "discussion"
    ) -> SpeechDecision:
        """
        生成白天发言 (Act)
        
        Phase 2: 基于记忆和策略的模板化发言
        Phase 4: 将接入 LLM 生成自然语言
        """
        logger.info(f"[Agent {self.player_id}] 生成发言 ({context})")
        
        # 获取角色策略的发言引导
        guidance = self.strategy.get_speech_guidance(self, game_state)
        
        # 获取记忆上下文
        memory_ctx = self.memory.get_full_context()
        
        # Phase 2: 基于记忆的模板化发言（Phase 4 替换为 LLM）
        content = self._generate_template_speech(game_state, context, guidance, memory_ctx)
        
        # 根据人格调整情绪
        emotion = self._determine_emotion(game_state)
        
        # 提取发言中涉及的玩家
        mentioned = self._extract_mentioned_players(content, game_state)
        
        return SpeechDecision(
            content=content,
            emotion=emotion,
            targets_mentioned=mentioned,
            strategy=guidance[:50]
        )
    
    def _generate_template_speech(
        self,
        game_state: GameState,
        context: str,
        guidance: str,
        memory_ctx: Dict[str, str]
    ) -> str:
        """基于记忆和策略生成模板发言（Phase 4 将由 LLM 替代）"""
        round_num = game_state.round
        
        # 获取嫌疑排名
        ranking = self.memory.semantic.get_suspicion_ranking()
        top_suspect = ranking[0] if ranking else None
        
        # 根据角色和场景生成发言
        if context == "defense":
            return self._defense_speech()
        
        parts = []
        
        # 开场
        if self.persona_profile.key == "aggressive":
            parts.append(f"我直接说。")
        elif self.persona_profile.key == "analytical":
            parts.append(f"我来分析一下目前的局势。")
        elif self.persona_profile.key == "cautious":
            parts.append(f"我说一下我的看法。")
        else:
            parts.append(f"大家听我说。")
        
        # 死亡分析
        deaths = self.memory.episodic.deaths_timeline
        if deaths:
            recent_death = deaths[-1]
            parts.append(f"第{recent_death['round']}天{recent_death['player_id']}号死亡，")
            if recent_death['cause'] == 'killed':
                parts.append(f"是被狼人刀的。")
            elif recent_death['cause'] == 'voted':
                parts.append(f"是被投票出局的。")
        
        # 嫌疑分析
        if top_suspect:
            pid, score = top_suspect
            profile = self.memory.semantic.get_profile(pid)
            if profile and profile.known_role == "WEREWOLF":
                parts.append(f"我认为{pid}号是狼人，理由充分。大家集中投{pid}号。")
            elif score > 0.6:
                if self.persona_profile.key == "aggressive":
                    parts.append(f"{pid}号非常可疑，我觉得就是他。")
                elif self.persona_profile.key == "cautious":
                    parts.append(f"我感觉{pid}号有一些可疑的地方，但还需要再观察。")
                else:
                    parts.append(f"从目前的信息来看，{pid}号的嫌疑比较大。")
        
        # 投票倾向
        if top_suspect:
            parts.append(f"我的投票方向倾向{top_suspect[0]}号。")
        
        content = "".join(parts)
        return content if content else "我暂时没有太多信息，再听听其他人怎么说。"
    
    def _defense_speech(self) -> str:
        """防守发言"""
        if self.persona_profile.key == "aggressive":
            return f"投我你们就输了！我是好人，怀疑我的人才更可疑。"
        elif self.persona_profile.key == "analytical":
            return f"请大家看看我之前的发言和投票记录，完全是好人逻辑。投我是浪费好人的机会。"
        elif self.persona_profile.key == "cautious":
            return f"我觉得大家可能误解我了。我的发言一直是为好人着想的，请再考虑一下。"
        else:
            return f"大家冷静一下，投我对好人阵营没有好处。我们应该把精力集中在真正可疑的人身上。"
    
    def _determine_emotion(self, game_state: GameState) -> str:
        """根据局势和人格确定情绪"""
        # 简单逻辑：根据人格偏好和游戏阶段
        if self.persona_profile.key == "aggressive":
            return "confident"
        elif self.persona_profile.key == "cautious":
            return "calm"
        elif self.persona_profile.key == "analytical":
            return "calm"
        else:
            return "confident"
    
    def _extract_mentioned_players(self, content: str, game_state: GameState) -> List[int]:
        """从发言中提取提到的玩家号码"""
        mentioned = []
        for pid in game_state.alive_players:
            if f"{pid}号" in content:
                mentioned.append(pid)
        return mentioned
    
    async def decide_vote(self, game_state: GameState) -> VoteDecision:
        """
        投票决策 (Act)
        
        委托给角色策略处理。
        """
        logger.info(f"[Agent {self.player_id}] 投票决策")
        
        # 更新工作记忆
        self.memory.working.update_phase(game_state.round, "VOTING")
        
        # 委托给角色策略
        decision = self.strategy.plan_vote(self, game_state)
        
        logger.info(
            f"[Agent {self.player_id}] 投票: target={decision.target_id}, "
            f"reason={decision.reason}, confidence={decision.confidence:.2f}"
        )
        return decision
    
    def get_info(self) -> dict:
        """获取 Agent 完整信息"""
        memory_info = self.memory.get_info()
        return {
            "game_id": self.game_id,
            "player_id": self.player_id,
            "role": self.role.value,
            "persona": self.persona_profile.name,
            "strategy": self.strategy.role_name,
            "seat_number": self.seat_number,
            "teammates": self.teammates,
            "memory": memory_info,
        }
    
    def get_memory_dump(self) -> dict:
        """获取记忆转储（调试用）"""
        ctx = self.memory.get_full_context()
        return {
            "player_id": self.player_id,
            "role": self.role.value,
            **ctx,
            "memory_stats": self.memory.get_info(),
        }
