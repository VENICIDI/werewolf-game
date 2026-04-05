"""
狼人杀 Agent 基类

每个 AI 玩家是一个独立的 WerewolfAgent 实例。

核心架构:
- MemorySystem: 三层记忆（工作/情景/语义）
- RoleStrategy: 角色策略（策略模式）
- PersonaProfile: 人格档案
- BayesianReasoner: 贝叶斯推理引擎
- SpeechGenerator: LLM 发言生成器
"""
import logging
from typing import List, Optional, Dict, Any

from models.game_models import Role, GameState, NightActionDecision, SpeechDecision, VoteDecision
from models.agent_models import Persona
from models.event_models import GameEvent, EventType
from services.llm_service import LLMService
from services.rag_service import RAGService
from agents.memory.memory_system import MemorySystem
from agents.strategies import create_strategy, RoleStrategy
from agents.persona.persona_profiles import get_persona, PersonaProfile
from agents.reasoning.bayesian_reasoner import BayesianReasoner
from agents.reasoning.evidence_analyzer import EvidenceAnalyzer
from agents.planning.speech_generator import SpeechGenerator

logger = logging.getLogger(__name__)


class WerewolfAgent:
    """
    狼人杀 Agent 基类
    
    核心职责:
    - 存储 Agent 基本信息（game_id, player_id, role, persona）
    - 通过 MemorySystem 管理三层记忆
    - 通过 RoleStrategy 实现角色差异化行为
    - 通过 BayesianReasoner 进行推理分析
    - 通过 SpeechGenerator + LLM 生成自然语言发言
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
        
        # 推理引擎
        self.reasoner = BayesianReasoner(my_player_id=player_id)
        self.evidence_analyzer = EvidenceAnalyzer(my_player_id=player_id)
        
        # LLM 发言生成器
        self.speech_generator: Optional[SpeechGenerator] = None
        self._init_speech_generator()
        
        logger.info(
            f"Agent 创建: Game={game_id}, Player={player_id}, "
            f"Role={role.value}, Persona={self.persona_profile.name}, "
            f"Strategy={self.strategy.role_name}"
        )
    
    def _init_speech_generator(self):
        """初始化 LLM 发言生成器"""
        try:
            llm = self.llm_service.get_llm()
            self.speech_generator = SpeechGenerator(
                llm=llm,
                rag_service=self.rag_service
            )
            logger.info(f"[Agent {self.player_id}] LLM 发言生成器初始化成功")
        except Exception as e:
            logger.warning(f"[Agent {self.player_id}] LLM 不可用，降级为模板发言: {e}")
            self.speech_generator = None
    
    def init_game(self, player_ids: List[int], seat_map: Optional[Dict[int, int]] = None):
        """游戏开始时初始化记忆和推理引擎"""
        self.memory.init_game(player_ids, seat_map)
        self.reasoner.init_players(player_ids)
        
        # 如果是狼人，标记队友为已知身份
        if self.role == Role.WEREWOLF and self.teammates:
            for tm in self.teammates:
                self.memory.semantic.set_known_role(tm, "WEREWOLF")
                self.memory.semantic.update_suspicion(tm, -1.0, "狼人队友")
                self.reasoner.confirm_wolf(tm)
    
    async def receive_event(self, event: GameEvent) -> None:
        """
        接收游戏事件 (Perceive + Think)
        
        1. 记忆系统处理事件
        2. 角色策略处理事件
        3. 推理引擎更新嫌疑值
        """
        logger.debug(f"[Agent {self.player_id}] 收到事件: {event.event_type.value}")
        
        # 1. 记忆系统处理
        self.memory.process_event(event)
        
        # 2. 角色策略处理
        self.strategy.update_on_event(self, event)
        
        # 3. 推理引擎更新
        self._update_reasoning(event)
    
    def _update_reasoning(self, event: GameEvent):
        """根据事件更新推理引擎"""
        et = event.event_type
        data = event.event_data
        round_num = event.round
        
        if et == EventType.SEER_CHECK_RESULT:
            target_id = data.get("target_id")
            result = data.get("result", "")
            if target_id:
                if "狼" in result or result == "WEREWOLF":
                    self.reasoner.confirm_wolf(target_id)
                else:
                    self.reasoner.confirm_good(target_id)
        
        elif et == EventType.VOTE_RESULT:
            votes = data.get("votes", {})
            eliminated = data.get("eliminated_player")
            int_votes = {}
            for v, t in votes.items():
                int_votes[int(v) if isinstance(v, str) else v] = int(t) if isinstance(t, str) else t
            self.evidence_analyzer.analyze_vote_round(
                self.memory, self.reasoner, round_num, int_votes, eliminated
            )
        
        elif et == EventType.PLAYER_DIED:
            player_id = data.get("player_id")
            cause = data.get("cause", "unknown")
            if player_id:
                self.reasoner.mark_dead(player_id)
                self.evidence_analyzer.analyze_death_pattern(
                    self.memory, self.reasoner, round_num, player_id, cause
                )
        
        elif et == EventType.PLAYER_SPEECH:
            speaker_id = data.get("player_id") or data.get("speaker_id")
            content = data.get("content", "")
            # 检测身份声明
            if speaker_id and content:
                claimed = self._detect_role_claim(content)
                if claimed:
                    self.memory.semantic.set_claimed_role(speaker_id, claimed)
                    self.evidence_analyzer.analyze_speech_claim(
                        self.memory, self.reasoner, round_num, speaker_id, claimed
                    )
        
        # 同步推理结果到语义记忆
        self.reasoner.sync_to_semantic(self.memory.semantic)
    
    def _detect_role_claim(self, content: str) -> Optional[str]:
        """检测发言中的身份声明"""
        keywords = {
            "SEER": ["预言家", "我验了", "我查了", "查验结果"],
            "WITCH": ["女巫", "我救了", "我毒了", "解药", "毒药"],
            "HUNTER": ["猎人", "我可以开枪", "带走"],
            "GUARD": ["守卫", "我守了", "守护"],
        }
        for role, words in keywords.items():
            for word in words:
                if word in content:
                    return role
        return None
    
    async def decide_night_action(self, game_state: GameState) -> NightActionDecision:
        """夜间行动决策"""
        logger.info(f"[Agent {self.player_id}] 夜间行动决策 ({self.strategy.role_name})")
        
        self.memory.working.update_phase(game_state.round, game_state.phase.value)
        
        # 应用存活偏差
        self.evidence_analyzer.apply_survival_bias(
            self.reasoner, game_state.round, game_state.alive_players
        )
        self.reasoner.sync_to_semantic(self.memory.semantic)
        
        decision = self.strategy.plan_night_action(self, game_state)
        
        logger.info(
            f"[Agent {self.player_id}] 决策: {decision.reason}, "
            f"target={decision.target_id}, confidence={decision.confidence:.2f}"
        )
        return decision
    
    async def generate_speech(
        self,
        game_state: GameState,
        context: str = "discussion"
    ) -> SpeechDecision:
        """
        生成白天发言
        
        优先使用 LLM 生成，失败时降级为模板。
        """
        logger.info(f"[Agent {self.player_id}] 生成发言 ({context})")
        
        content = None
        
        # 尝试 LLM 生成
        if self.speech_generator:
            try:
                content = await self.speech_generator.generate(agent=self, game_state=game_state, context=context)
            except Exception as e:
                logger.warning(f"[Agent {self.player_id}] LLM 发言失败，降级模板: {e}")
        
        # 降级: 模板发言
        if not content:
            guidance = self.strategy.get_speech_guidance(self, game_state)
            memory_ctx = self.memory.get_full_context()
            content = self._generate_template_speech(game_state, context, guidance, memory_ctx)
        
        emotion = self._determine_emotion(game_state)
        mentioned = self._extract_mentioned_players(content, game_state)
        guidance = self.strategy.get_speech_guidance(self, game_state)
        
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
        """模板发言（LLM 降级方案）"""
        if context == "defense":
            return self._defense_speech()
        
        ranking = self.memory.semantic.get_suspicion_ranking()
        top_suspect = ranking[0] if ranking else None
        
        parts = []
        
        if self.persona_profile.key == "aggressive":
            parts.append("我直接说。")
        elif self.persona_profile.key == "analytical":
            parts.append("我来分析一下目前的局势。")
        elif self.persona_profile.key == "cautious":
            parts.append("我说一下我的看法。")
        else:
            parts.append("大家听我说。")
        
        deaths = self.memory.episodic.deaths_timeline
        if deaths:
            d = deaths[-1]
            parts.append(f"第{d['round']}天{d['player_id']}号死亡。")
        
        if top_suspect:
            pid, score = top_suspect
            profile = self.memory.semantic.get_profile(pid)
            if profile and profile.known_role == "WEREWOLF":
                parts.append(f"我认为{pid}号是狼人，大家集中投{pid}号。")
            elif score > 0.6:
                parts.append(f"从目前信息来看，{pid}号嫌疑比较大。")
            parts.append(f"我倾向投{pid}号。")
        
        return "".join(parts) or "我暂时没有太多信息，再听听其他人怎么说。"
    
    def _defense_speech(self) -> str:
        if self.persona_profile.key == "aggressive":
            return "投我你们就输了！我是好人，怀疑我的人才更可疑。"
        elif self.persona_profile.key == "analytical":
            return "请大家看我之前的发言和投票记录，完全是好人逻辑。投我是浪费机会。"
        elif self.persona_profile.key == "cautious":
            return "我觉得大家可能误解我了。我一直是为好人着想的，请再考虑一下。"
        return "大家冷静一下，投我对好人阵营没有好处。应该集中精力找真正可疑的人。"
    
    def _determine_emotion(self, game_state: GameState) -> str:
        if self.persona_profile.key == "aggressive":
            return "confident"
        elif self.persona_profile.key in ("cautious", "analytical"):
            return "calm"
        return "confident"
    
    def _extract_mentioned_players(self, content: str, game_state: GameState) -> List[int]:
        mentioned = []
        for pid in game_state.alive_players:
            if f"{pid}号" in content:
                mentioned.append(pid)
        return mentioned
    
    async def decide_vote(self, game_state: GameState) -> VoteDecision:
        """投票决策"""
        logger.info(f"[Agent {self.player_id}] 投票决策")
        self.memory.working.update_phase(game_state.round, "VOTING")
        
        decision = self.strategy.plan_vote(self, game_state)
        
        logger.info(
            f"[Agent {self.player_id}] 投票: target={decision.target_id}, "
            f"reason={decision.reason}"
        )
        return decision
    
    def get_info(self) -> dict:
        """获取 Agent 完整信息"""
        return {
            "game_id": self.game_id,
            "player_id": self.player_id,
            "role": self.role.value,
            "persona": self.persona_profile.name,
            "strategy": self.strategy.role_name,
            "seat_number": self.seat_number,
            "teammates": self.teammates,
            "llm_available": self.speech_generator is not None,
            "memory": self.memory.get_info(),
        }
    
    def get_memory_dump(self) -> dict:
        """获取记忆和推理转储（调试用）"""
        ctx = self.memory.get_full_context()
        return {
            "player_id": self.player_id,
            "role": self.role.value,
            **ctx,
            "reasoning": self.reasoner.format_analysis(),
            "memory_stats": self.memory.get_info(),
        }
