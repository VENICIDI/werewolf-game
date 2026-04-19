"""
女巫策略 (WitchStrategy)

核心策略:
- 解药: 优先救神职，第一晚倾向于救
- 毒药: 有确切证据时毒杀狼人
- 发言: 利用刀口信息辅助分析
"""
import random
from typing import TYPE_CHECKING, List, Optional

from agents.strategies.base_strategy import RoleStrategy
from models.game_models import GameState, NightActionDecision, VoteDecision
from models.event_models import GameEvent, EventType

if TYPE_CHECKING:
    from agents.base_agent import WerewolfAgent


class WitchStrategy(RoleStrategy):
    
    def __init__(self):
        self.has_save_potion = True   # 解药
        self.has_poison_potion = True  # 毒药
        self.saved_player: Optional[int] = None
        self.poisoned_player: Optional[int] = None
        self.knife_targets: List[int] = []  # 每晚被刀的人（刀口信息）
    
    @property
    def role_name(self) -> str:
        return "女巫"
    
    @property
    def role_objective(self) -> str:
        return "合理使用解药和毒药，帮助好人阵营获胜"
    
    @property
    def night_action_type(self) -> str:
        return "witch_action"
    
    def plan_night_action(self, agent: "WerewolfAgent", game_state: GameState) -> NightActionDecision:
        """女巫夜间行动决策"""
        # 从工作记忆获取当晚被刀的人
        killed_player = agent.memory.working.get_flag("tonight_killed")
        
        if killed_player:
            self.knife_targets.append(killed_player)
        
        # 决策 1: 是否使用解药
        if killed_player and self.has_save_potion:
            should_save = self._should_save(agent, killed_player, game_state)
            if should_save:
                self.has_save_potion = False
                self.saved_player = killed_player
                return NightActionDecision(
                    target_id=killed_player,
                    reason=f"使用解药救{killed_player}号",
                    confidence=0.8
                )
        
        # 决策 2: 是否使用毒药
        if self.has_poison_potion:
            poison_target = self._should_poison(agent, game_state)
            if poison_target:
                self.has_poison_potion = False
                self.poisoned_player = poison_target
                return NightActionDecision(
                    target_id=poison_target,
                    reason=f"使用毒药毒{poison_target}号",
                    confidence=0.7
                )
        
        # 不使用药水
        return NightActionDecision(
            target_id=0,
            reason="本轮不使用药水",
            confidence=0.6
        )
    
    def _should_save(self, agent: "WerewolfAgent", killed_player: int, game_state: GameState) -> bool:
        """判断是否使用解药"""
        round_num = game_state.round
        
        # 第一晚: 倾向于救（概率上被刀的大概率是好人）
        if round_num == 1:
            # 如果是自己被刀且第一晚，可以自救
            if killed_player == agent.player_id:
                return True
            return True  # 第一晚默认救
        
        # 后续回合: 根据被刀者的信息判断
        profile = agent.memory.semantic.get_profile(killed_player)
        if profile:
            # 被刀者声称/已知是神职 → 救
            if profile.claimed_role in ("SEER", "HUNTER", "GUARD"):
                return True
            if profile.known_role and profile.known_role != "WEREWOLF":
                return True
            # 嫌疑很低（很可能是好人）→ 救
            if profile.suspicion_score < 0.3:
                return True
        
        # 如果是自己被刀（第二晚开始不能自救）
        if killed_player == agent.player_id and round_num > 1:
            return False
        
        return False
    
    def _should_poison(self, agent: "WerewolfAgent", game_state: GameState) -> Optional[int]:
        """判断是否使用毒药，返回毒药目标或 None"""
        # 第一晚不盲毒
        if game_state.round == 1:
            return None
        
        targets = self._get_available_targets(agent, game_state)
        
        # 有确切查杀信息（比如预言家公布了查杀）
        for pid in targets:
            profile = agent.memory.semantic.get_profile(pid)
            if profile and profile.known_role == "WEREWOLF":
                return pid
        
        # 嫌疑值极高的玩家（>0.8）
        ranking = agent.memory.semantic.get_suspicion_ranking()
        for pid, score in ranking:
            if score > 0.8 and pid in targets:
                return pid
        
        return None
    
    def plan_vote(self, agent: "WerewolfAgent", game_state: GameState) -> VoteDecision:
        """女巫投票: 结合刀口信息和嫌疑分析"""
        targets = self._get_available_targets(agent, game_state)
        
        # 优先投已知狼人
        for pid in targets:
            profile = agent.memory.semantic.get_profile(pid)
            if profile and profile.known_role == "WEREWOLF":
                return VoteDecision(
                    target_id=pid,
                    reason=f"确认{pid}号是狼人",
                    confidence=0.9
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
        potion_status = []
        if self.has_save_potion:
            potion_status.append("解药: 未使用")
        else:
            potion_status.append(f"解药: 已用(救了{self.saved_player}号)")
        if self.has_poison_potion:
            potion_status.append("毒药: 未使用")
        else:
            potion_status.append(f"毒药: 已用(毒了{self.poisoned_player}号)")
        
        knife_info = ""
        if self.knife_targets:
            knife_info = "\n你知道的刀口信息: " + ", ".join(
                f"第{i+1}晚刀了{pid}号" for i, pid in enumerate(self.knife_targets)
            )
        
        return f"""你是一名狼人杀游戏中的女巫。你的座位号是{agent.seat_number}号。
你拥有两瓶药水:
  {chr(10).join(potion_status)}
解药可以救活当晚被狼人杀害的人，毒药可以毒杀任意一名玩家。
每瓶药只能用一次，同一晚不能同时使用。第一晚可以自救，之后不能。{knife_info}
你的目标是合理使用药水帮助好人阵营获胜。"""
    
    def get_speech_guidance(self, agent: "WerewolfAgent", game_state: GameState) -> str:
        if self.knife_targets:
            return "你知道刀口信息，可以利用这些信息辅助分析。但注意不要轻易暴露女巫身份。"
        return "你是女巫，掌握药水使用情况。在合适的时候可以跳身份公布信息。"
    
    def update_on_event(self, agent: "WerewolfAgent", event: GameEvent):
        """女巫特有事件处理"""
        pass
