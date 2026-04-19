"""
守卫策略 (GuardStrategy)

核心策略:
- 守护: 优先守护已跳身份的神职
- 不能连续两晚守同一人
- 投票: 正常好人逻辑
"""
import random
from typing import TYPE_CHECKING, Optional, List

from agents.strategies.base_strategy import RoleStrategy
from models.game_models import GameState, NightActionDecision, VoteDecision

if TYPE_CHECKING:
    from agents.base_agent import WerewolfAgent


class GuardStrategy(RoleStrategy):
    
    def __init__(self):
        self.last_guarded: Optional[int] = None  # 上一晚守护的人
        self.guard_history: List[int] = []  # 守护历史
    
    @property
    def role_name(self) -> str:
        return "守卫"
    
    @property
    def role_objective(self) -> str:
        return "守护关键好人（尤其是预言家），防止被狼人击杀"
    
    @property
    def night_action_type(self) -> str:
        return "guard"
    
    def plan_night_action(self, agent: "WerewolfAgent", game_state: GameState) -> NightActionDecision:
        """守卫守护决策"""
        targets = list(game_state.alive_players)  # 守卫可以守自己
        
        # 排除上一晚守过的人（不能连守）
        if self.last_guarded is not None and self.last_guarded in targets:
            targets.remove(self.last_guarded)
        
        if not targets:
            return NightActionDecision(target_id=0, reason="无可守护目标（连守限制）", confidence=0.5)
        
        target = self._select_guard_target(agent, targets, game_state)
        self.last_guarded = target
        self.guard_history.append(target)
        
        return NightActionDecision(
            target_id=target,
            reason=self._explain_guard(agent, target),
            confidence=0.7
        )
    
    def _select_guard_target(self, agent: "WerewolfAgent", targets: List[int], game_state: GameState) -> int:
        """选择守护目标"""
        memory = agent.memory
        
        # 优先级 1: 守护已跳预言家的玩家
        for pid in targets:
            profile = memory.semantic.get_profile(pid)
            if profile and profile.claimed_role == "SEER":
                return pid
        
        # 优先级 2: 守护其他已跳神职
        for pid in targets:
            profile = memory.semantic.get_profile(pid)
            if profile and profile.claimed_role in ("WITCH", "HUNTER"):
                return pid
        
        # 优先级 3: 守护嫌疑值最低（最可能是好人）的玩家
        best_target = None
        lowest_suspicion = 1.0
        for pid in targets:
            if pid == agent.player_id:
                continue  # 优先守别人
            profile = memory.semantic.get_profile(pid)
            if profile and profile.suspicion_score < lowest_suspicion:
                lowest_suspicion = profile.suspicion_score
                best_target = pid
        
        if best_target:
            return best_target
        
        # 兜底: 守自己
        if agent.player_id in targets:
            return agent.player_id
        
        return random.choice(targets)
    
    def _explain_guard(self, agent: "WerewolfAgent", target: int) -> str:
        if target == agent.player_id:
            return "守护自己"
        profile = agent.memory.semantic.get_profile(target)
        if profile and profile.claimed_role:
            return f"守护{target}号（声称{profile.claimed_role}）"
        return f"守护{target}号"
    
    def plan_vote(self, agent: "WerewolfAgent", game_state: GameState) -> VoteDecision:
        """守卫投票: 正常好人逻辑"""
        targets = self._get_available_targets(agent, game_state)
        
        # 优先投已知狼人
        for pid in targets:
            profile = agent.memory.semantic.get_profile(pid)
            if profile and profile.known_role == "WEREWOLF":
                return VoteDecision(
                    target_id=pid, reason=f"确认{pid}号是狼人", confidence=0.9
                )
        
        most_suspicious = agent.memory.semantic.get_most_suspicious(exclude=[agent.player_id])
        if most_suspicious and most_suspicious in targets:
            return VoteDecision(
                target_id=most_suspicious,
                reason=f"{most_suspicious}号嫌疑最高",
                confidence=0.6
            )
        
        target = random.choice(targets) if targets else 0
        return VoteDecision(target_id=target, reason="综合判断投票", confidence=0.4)
    
    def get_system_prompt(self, agent: "WerewolfAgent") -> str:
        last_info = f"\n上一晚守护了{self.last_guarded}号（今晚不能再守）。" if self.last_guarded else ""
        return f"""你是一名狼人杀游戏中的守卫。你的座位号是{agent.seat_number}号。
守卫每晚可以守护一名玩家（包括自己），被守护的玩家当晚不会被狼人杀死。
限制: 不能连续两晚守护同一人。{last_info}
你的目标是守护关键好人（尤其是预言家），防止被狼人击杀。
注意: 守卫不建议轻易暴露身份。"""
    
    def get_speech_guidance(self, agent: "WerewolfAgent", game_state: GameState) -> str:
        return "你是守卫，不建议暴露身份。发言时像普通好人一样分析局势。"
