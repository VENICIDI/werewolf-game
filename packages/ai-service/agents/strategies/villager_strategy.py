"""
村民策略 (VillagerStrategy)

核心策略:
- 无夜间技能
- 白天: 通过逻辑分析和观察辨别狼人
- 投票: 根据嫌疑分析投票
"""
import random
from typing import TYPE_CHECKING, Optional

from agents.strategies.base_strategy import RoleStrategy
from models.game_models import GameState, NightActionDecision, VoteDecision

if TYPE_CHECKING:
    from agents.base_agent import WerewolfAgent


class VillagerStrategy(RoleStrategy):
    
    @property
    def role_name(self) -> str:
        return "村民"
    
    @property
    def role_objective(self) -> str:
        return "通过逻辑分析找出狼人，配合神职投出狼人"
    
    @property
    def night_action_type(self) -> Optional[str]:
        return None
    
    def plan_night_action(self, agent: "WerewolfAgent", game_state: GameState) -> NightActionDecision:
        """村民无夜间行动"""
        return NightActionDecision(target_id=0, reason="村民无夜间行动", confidence=1.0)
    
    def plan_vote(self, agent: "WerewolfAgent", game_state: GameState) -> VoteDecision:
        """村民投票: 纯逻辑分析"""
        targets = self._get_available_targets(agent, game_state)
        
        if not targets:
            return VoteDecision(target_id=0, reason="无可投票目标", confidence=0.3)
        
        # 优先投已知狼人
        for pid in targets:
            profile = agent.memory.semantic.get_profile(pid)
            if profile and profile.known_role == "WEREWOLF":
                return VoteDecision(
                    target_id=pid, reason=f"确认{pid}号是狼人", confidence=0.9
                )
        
        # 投向嫌疑最高的
        most_suspicious = agent.memory.semantic.get_most_suspicious(exclude=[agent.player_id])
        if most_suspicious and most_suspicious in targets:
            profile = agent.memory.semantic.get_profile(most_suspicious)
            score = profile.suspicion_score if profile else 0.5
            return VoteDecision(
                target_id=most_suspicious,
                reason=f"{most_suspicious}号嫌疑最高({score:.2f})",
                confidence=max(0.4, score)
            )
        
        target = random.choice(targets)
        return VoteDecision(target_id=target, reason="综合判断投票", confidence=0.4)
    
    def get_system_prompt(self, agent: "WerewolfAgent") -> str:
        return f"""你是一名狼人杀游戏中的普通村民。你的座位号是{agent.player_id}号。
村民没有特殊技能，但你可以通过观察和逻辑分析来辨别狼人。
你的目标是积极参与讨论，提供有价值的分析，配合神职找出狼人并投票出局。
注意: 仔细分析每个人的发言和投票行为。"""
    
    def get_speech_guidance(self, agent: "WerewolfAgent", game_state: GameState) -> str:
        return "你是村民，没有特殊信息。通过分析发言内容、投票方向、死亡信息来推理谁是狼人。积极表达自己的观点。"
