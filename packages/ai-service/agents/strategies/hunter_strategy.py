"""
猎人策略 (HunterStrategy)

核心策略:
- 猎人无夜间主动行动
- 死亡时决定是否开枪及目标
- 投票: 正常分析投票
- 利用开枪威慑保护自己
"""
import random
from typing import TYPE_CHECKING, Optional

from agents.strategies.base_strategy import RoleStrategy
from models.game_models import GameState, NightActionDecision, VoteDecision

if TYPE_CHECKING:
    from agents.base_agent import WerewolfAgent


class HunterStrategy(RoleStrategy):
    
    def __init__(self):
        self.can_shoot = True  # 被毒死则不能开枪
    
    @property
    def role_name(self) -> str:
        return "猎人"
    
    @property
    def role_objective(self) -> str:
        return "正常分析局势，死亡时开枪带走确认的狼人"
    
    @property
    def night_action_type(self) -> Optional[str]:
        return None  # 猎人无夜间主动行动
    
    def plan_night_action(self, agent: "WerewolfAgent", game_state: GameState) -> NightActionDecision:
        """猎人无夜间行动"""
        return NightActionDecision(target_id=0, reason="猎人无夜间行动", confidence=1.0)
    
    def plan_shoot(self, agent: "WerewolfAgent", game_state: GameState) -> NightActionDecision:
        """
        猎人死亡时的开枪决策
        
        Returns:
            target_id=0 表示不开枪
        """
        if not self.can_shoot:
            return NightActionDecision(target_id=0, reason="被毒死，无法开枪", confidence=1.0)
        
        targets = self._get_available_targets(agent, game_state)
        if not targets:
            return NightActionDecision(target_id=0, reason="无可射击目标", confidence=1.0)
        
        # 优先射击已知狼人
        for pid in targets:
            profile = agent.memory.semantic.get_profile(pid)
            if profile and profile.known_role == "WEREWOLF":
                return NightActionDecision(
                    target_id=pid,
                    reason=f"确认{pid}号是狼人，开枪射杀",
                    confidence=0.95
                )
        
        # 射击嫌疑值最高的
        most_suspicious = agent.memory.semantic.get_most_suspicious(exclude=[agent.player_id])
        if most_suspicious and most_suspicious in targets:
            profile = agent.memory.semantic.get_profile(most_suspicious)
            score = profile.suspicion_score if profile else 0.5
            if score > 0.6:
                return NightActionDecision(
                    target_id=most_suspicious,
                    reason=f"{most_suspicious}号嫌疑较高({score:.2f})，开枪射杀",
                    confidence=score
                )
        
        # 不确定就不开枪
        return NightActionDecision(target_id=0, reason="没有确定目标，选择不开枪", confidence=0.5)
    
    def plan_vote(self, agent: "WerewolfAgent", game_state: GameState) -> VoteDecision:
        """猎人投票: 正常好人逻辑"""
        targets = self._get_available_targets(agent, game_state)
        
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
            return VoteDecision(
                target_id=most_suspicious,
                reason=f"{most_suspicious}号嫌疑最高",
                confidence=0.6
            )
        
        target = random.choice(targets) if targets else 0
        return VoteDecision(target_id=target, reason="综合判断投票", confidence=0.4)
    
    def get_system_prompt(self, agent: "WerewolfAgent") -> str:
        return f"""你是一名狼人杀游戏中的猎人。你的座位号是{agent.seat_number}号。
猎人的技能：死亡时可以开枪带走一名玩家。但如果你是被女巫毒死的，则不能开枪。
你的目标是正常分析局势帮助好人阵营，死亡时开枪带走确认的狼人。
注意：猎人身份有威慑力，可以利用这一点保护自己。"""
    
    def get_speech_guidance(self, agent: "WerewolfAgent", game_state: GameState) -> str:
        return "你是猎人，死亡时可以开枪。利用这个威慑保护自己。发言时正常分析，不需要急着暴露身份。"
