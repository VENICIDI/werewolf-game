"""
狼人杀 Agent 基类
每个 AI 玩家是一个独立的 WerewolfAgent 实例
"""
import random
from typing import List, Optional
from models.game_models import Role, GameState, NightActionDecision, SpeechDecision, VoteDecision
from models.agent_models import Persona
from models.event_models import GameEvent
from services.llm_service import LLMService
from services.rag_service import RAGService


class WerewolfAgent:
    """
    狼人杀 Agent 基类
    
    核心职责:
    - 存储 Agent 基本信息（game_id, player_id, role, persona）
    - 接收游戏事件并更新内部状态
    - 提供决策接口（夜间行动/发言/投票）
    
    Phase 1 简化:
    - 记忆系统: 仅占位,存储基本事件列表
    - 推理引擎: 仅占位,返回简单随机决策
    - 角色策略: 仅占位,所有角色使用相同简单逻辑
    - LLM 发言: 返回模板化文本（Phase 4 接入 LLM）
    """
    
    def __init__(
        self,
        game_id: str,
        player_id: int,
        role: Role,
        persona: Persona,
        llm_service: LLMService,
        rag_service: RAGService
    ):
        """
        初始化 Agent
        
        Args:
            game_id: 游戏 ID
            player_id: 玩家 ID
            role: 角色
            persona: 人格档案
            llm_service: LLM 服务
            rag_service: RAG 服务
        """
        self.game_id = game_id
        self.player_id = player_id
        self.role = role
        self.persona = persona
        self.llm_service = llm_service
        self.rag_service = rag_service
        
        # 记忆系统（Phase 1 简化：仅事件列表）
        self.event_memory: List[GameEvent] = []
        
        # 推理状态（Phase 1 简化：空字典占位）
        self.reasoning_state = {}
        
        print(f"✅ Agent 创建成功: Game={game_id}, Player={player_id}, Role={role.value}, Persona={persona.value}")
    
    async def receive_event(self, event: GameEvent) -> None:
        """
        接收游戏事件并更新内部状态
        
        Args:
            event: 游戏事件
        """
        self.event_memory.append(event)
        print(f"[Agent {self.player_id}] 收到事件: {event.event_type.value}")
        
        # Phase 1 简化：仅存储事件，不做复杂处理
        # TODO Phase 2: 更新情景记忆、语义记忆
        # TODO Phase 4: 触发推理引擎更新嫌疑值
    
    async def decide_night_action(self, game_state: GameState) -> NightActionDecision:
        """
        夜间行动决策
        
        Phase 1 简化：随机选择存活玩家（排除自己）
        
        Args:
            game_state: 游戏状态
            
        Returns:
            夜间行动决策
        """
        # 获取可选目标（存活玩家，排除自己）
        available_targets = [
            pid for pid in game_state.alive_players 
            if pid != self.player_id
        ]
        
        if not available_targets:
            # 没有可选目标，返回 skip
            return NightActionDecision(
                action="skip",
                target_id=None,
                reason="无可选目标",
                confidence=1.0
            )
        
        # Phase 1 简化：随机选择
        target_id = random.choice(available_targets)
        
        # 根据角色确定 action 类型
        action_map = {
            Role.WEREWOLF: "kill",
            Role.SEER: "check",
            Role.WITCH: "skip",  # 女巫逻辑复杂，Phase 1 暂时跳过
            Role.GUARD: "guard",
            Role.HUNTER: "skip",  # 猎人夜间不行动
            Role.VILLAGER: "skip"
        }
        
        action = action_map.get(self.role, "skip")
        
        return NightActionDecision(
            action=action,
            target_id=target_id if action != "skip" else None,
            reason=f"Phase 1 随机决策: 选择 {target_id} 号玩家",
            confidence=0.5
        )
    
    async def generate_speech(
        self, 
        game_state: GameState, 
        context: str = "discussion"
    ) -> SpeechDecision:
        """
        生成白天发言
        
        Phase 1 简化：返回模板化发言
        Phase 4 将接入 LLM 生成自然语言
        
        Args:
            game_state: 游戏状态
            context: 发言场景 (discussion/defense/claim_role)
            
        Returns:
            发言决策
        """
        # Phase 1 简化：模板化发言
        templates = {
            "discussion": [
                f"我是{self.player_id}号玩家，昨晚的死亡让我很震惊。",
                f"从目前的局势来看，我觉得{random.choice(game_state.alive_players)}号有些可疑。",
                f"我认为我们需要更多信息才能做出判断。"
            ],
            "defense": [
                f"我是好人，请大家相信我！",
                f"如果我是狼人，我不会这样发言。",
                f"投我就是浪费机会。"
            ],
            "claim_role": [
                f"我的身份是{self.role.value}。",
                f"我可以证明我的身份。"
            ]
        }
        
        content = random.choice(templates.get(context, templates["discussion"]))
        
        return SpeechDecision(
            content=content,
            emotion="neutral",
            targets_mentioned=[],
            strategy="Phase 1 模板发言"
        )
    
    async def decide_vote(self, game_state: GameState) -> VoteDecision:
        """
        投票决策
        
        Phase 1 简化：随机选择存活玩家（排除自己）
        
        Args:
            game_state: 游戏状态
            
        Returns:
            投票决策
        """
        # 获取可投票目标（存活玩家，排除自己）
        available_targets = [
            pid for pid in game_state.alive_players 
            if pid != self.player_id
        ]
        
        if not available_targets:
            return VoteDecision(
                target_id=0,  # 0 表示弃票
                reason="无可投目标，选择弃票"
            )
        
        # Phase 1 简化：随机选择
        target_id = random.choice(available_targets)
        
        return VoteDecision(
            target_id=target_id,
            reason=f"Phase 1 随机投票: 选择 {target_id} 号玩家"
        )
    
    def get_info(self) -> dict:
        """
        获取 Agent 基本信息
        
        Returns:
            Agent 信息字典
        """
        return {
            "game_id": self.game_id,
            "player_id": self.player_id,
            "role": self.role.value,
            "persona": self.persona.value,
            "event_count": len(self.event_memory)
        }
