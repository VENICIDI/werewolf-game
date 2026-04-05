"""
角色策略基类 (RoleStrategy)

定义所有角色策略的统一接口。
每种角色通过实现此接口来提供差异化的行为逻辑。
"""
from abc import ABC, abstractmethod
from typing import TYPE_CHECKING, Optional, List

from models.game_models import GameState, NightActionDecision, VoteDecision
from models.event_models import GameEvent

if TYPE_CHECKING:
    from agents.base_agent import WerewolfAgent


class RoleStrategy(ABC):
    """角色策略抽象基类"""
    
    @property
    @abstractmethod
    def role_name(self) -> str:
        """角色名称"""
        pass
    
    @property
    @abstractmethod
    def role_objective(self) -> str:
        """角色目标描述"""
        pass
    
    @property
    def night_action_type(self) -> Optional[str]:
        """夜间行动类型，None 表示无夜间行动"""
        return None
    
    @abstractmethod
    def plan_night_action(self, agent: "WerewolfAgent", game_state: GameState) -> NightActionDecision:
        """
        规划夜间行动
        
        Args:
            agent: Agent 实例（可访问记忆、推理状态等）
            game_state: 当前游戏状态
            
        Returns:
            NightActionDecision: 夜间行动决策
        """
        pass
    
    @abstractmethod
    def plan_vote(self, agent: "WerewolfAgent", game_state: GameState) -> VoteDecision:
        """
        规划投票决策
        
        Args:
            agent: Agent 实例
            game_state: 当前游戏状态
            
        Returns:
            VoteDecision: 投票决策
        """
        pass
    
    @abstractmethod
    def get_system_prompt(self, agent: "WerewolfAgent") -> str:
        """
        获取角色专属系统提示词
        
        Returns:
            str: 系统提示词（注入 LLM 的 system message）
        """
        pass
    
    def update_on_event(self, agent: "WerewolfAgent", event: GameEvent):
        """
        响应游戏事件，更新角色特有状态
        
        默认实现为空，子类按需覆盖。
        """
        pass
    
    def get_speech_guidance(self, agent: "WerewolfAgent", game_state: GameState) -> str:
        """
        获取发言引导（注入发言生成 Prompt）
        
        Returns:
            str: 发言策略建议
        """
        return f"你是{self.role_name}，目标是{self.role_objective}。根据场上局势合理发言。"
    
    def _get_available_targets(self, agent: "WerewolfAgent", game_state: GameState, exclude_self: bool = True) -> List[int]:
        """获取可选目标（通用工具方法）"""
        targets = list(game_state.alive_players)
        if exclude_self and agent.player_id in targets:
            targets.remove(agent.player_id)
        return targets
