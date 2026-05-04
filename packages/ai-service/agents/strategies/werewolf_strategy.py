"""
狼人策略 (WerewolfStrategy)

核心策略:
- 首刀: 优先击杀高嫌疑神职（预言家优先）
- 白天: 伪装好人，引导投票方向
- 投票: 投向好人阵营（尤其已跳身份的神职）
- 避免暴露队友关系
"""
import random
from typing import TYPE_CHECKING, List, Optional

from agents.strategies.base_strategy import RoleStrategy
from models.game_models import GameState, NightActionDecision, VoteDecision
from models.event_models import GameEvent

if TYPE_CHECKING:
    from agents.base_agent import WerewolfAgent


class WerewolfStrategy(RoleStrategy):
    
    @property
    def role_name(self) -> str:
        return "狼人"
    
    @property
    def role_objective(self) -> str:
        return "隐藏身份，击杀好人，使狼人数量>=好人数量"
    
    @property
    def night_action_type(self) -> str:
        return "kill"
    
    def plan_night_action(self, agent: "WerewolfAgent", game_state: GameState) -> NightActionDecision:
        """狼人夜间击杀决策"""
        targets = self._get_available_targets(agent, game_state)
        
        # 排除狼人队友
        teammates = getattr(agent, "teammates", [])
        targets = [t for t in targets if t not in teammates]
        
        if not targets:
            return NightActionDecision(target_id=0, reason="无可击杀目标", confidence=0.5)
        
        # 策略: 优先击杀已知/疑似神职
        target = self._select_kill_target(agent, targets, game_state)
        
        return NightActionDecision(
            target_id=target,
            reason=self._explain_kill(agent, target),
            confidence=0.7
        )
    
    def _select_kill_target(self, agent: "WerewolfAgent", targets: List[int], game_state: GameState) -> int:
        """选择击杀目标"""
        memory = agent.memory
        
        # 优先级 1: 已声称预言家的玩家
        for pid in targets:
            profile = memory.semantic.get_profile(pid)
            if profile and profile.claimed_role == "SEER":
                return pid
        
        # 优先级 2: 已声称其他神职的玩家
        for pid in targets:
            profile = memory.semantic.get_profile(pid)
            if profile and profile.claimed_role in ("WITCH", "HUNTER", "GUARD"):
                return pid
        
        # 优先级 3: 信任值最高的好人（威胁最大）
        # 嫌疑值低 = 我方认为是好人 = 对我方威胁大
        best_target = None
        lowest_suspicion = 1.0
        for pid in targets:
            profile = memory.semantic.get_profile(pid)
            if profile and profile.suspicion_score < lowest_suspicion:
                lowest_suspicion = profile.suspicion_score
                best_target = pid
        
        if best_target:
            return best_target
        
        # 兜底: 随机
        return random.choice(targets)
    
    def _explain_kill(self, agent: "WerewolfAgent", target: int) -> str:
        """解释击杀理由"""
        profile = agent.memory.semantic.get_profile(target)
        if profile:
            if profile.claimed_role == "SEER":
                return f"{target}号声称预言家，是最大威胁，必须击杀"
            elif profile.claimed_role:
                return f"{target}号声称{profile.claimed_role}，需要击杀神职"
            elif profile.suspicion_score < 0.3:
                return f"{target}号嫌疑低，可能是隐藏的神职"
        return f"选择击杀{target}号"
    
    def plan_vote(self, agent: "WerewolfAgent", game_state: GameState) -> VoteDecision:
        """狼人投票策略: 投向好人"""
        targets = self._get_available_targets(agent, game_state)
        teammates = getattr(agent, "teammates", [])
        
        # 排除狼人队友
        good_targets = [t for t in targets if t not in teammates]
        
        if not good_targets:
            return VoteDecision(target_id=0, reason="无合适投票目标，弃票", confidence=0.3)
        
        # 策略: 投向已被多人怀疑的好人（跟随大众投票不易暴露）
        # 或投向已跳身份的神职
        target = self._select_vote_target(agent, good_targets)
        
        return VoteDecision(
            target_id=target,
            reason=f"投票{target}号",
            confidence=0.6
        )
    
    def _select_vote_target(self, agent: "WerewolfAgent", targets: List[int]) -> int:
        """选择投票目标"""
        memory = agent.memory
        
        # 优先投向嫌疑值最高的好人（被场上怀疑的人）
        # 这里的 suspicion 是从 AI 狼人视角看的，所以选嫌疑高的（场上也在怀疑的）
        most_suspected = memory.semantic.get_most_suspicious(alive_only=True, exclude=getattr(agent, "teammates", []))
        if most_suspected and most_suspected in targets:
            return most_suspected
        
        return random.choice(targets)
    
    def get_system_prompt(self, agent: "WerewolfAgent") -> str:
        teammates = getattr(agent, "teammates", [])
        teammate_str = "、".join(str(t) for t in teammates) if teammates else "无"
        return f"""你是一名狼人杀游戏中的狼人。你的座位号是{agent.seat_number}号。
你的队友是: {teammate_str}号。

核心策略:
- 把自己当成一张神牌，其他三个狼队友就是另外三张神牌
- 四张神牌在你眼里就是四张狼牌，你要骗四张普通村民
- 有三个认识的伙伴会保护你，应该更坦然地发言
- 白天伪装成好人，发言要有逻辑，不要表露杀心
- 不要暴露队友，不要与队友有明显的互保行为
- 可以适当踩一下队友制造对立（倒钩战术）
- 引导投票投向好人阵营，狼队天生四票再拉两票就过半
- 夜晚优先击杀预言家等神职

狼队分工参考: 悍跳狼（跳预言家抢警徽）、冲锋狼（帮拉票）、深水狼（隐藏到后期）、倒钩狼（站边真预言家做身份）"""
    
    def get_speech_guidance(self, agent: "WerewolfAgent", game_state: GameState) -> str:
        return """作为狼人，你需要：
1. 伪装成好人，发言要有逻辑
2. 适当怀疑其他玩家，但不要过度
3. 不要暴露狼人队友
4. 可以适当踩一下队友制造对立
5. 引导投票投向好人阵营"""
    
    def get_speech_example(self) -> str:
        return """狼人发言示例：
假装平民："我手上没牌，但我听下来觉得X号的逻辑有问题。他说验了Y号但又不敢报查杀，这个态度很可疑。我倾向投X号，大家怎么看？"
悍跳预言家："我是预言家，昨晚验了X号，查杀！大家投他！我的警徽流留给Y号和Z号。"
注意：最后一个发言的狼队友可以加一句"D更像一点"暗示其他狼队友集中投票。"""
    
    def update_on_event(self, agent: "WerewolfAgent", event: GameEvent):
        """狼人特有事件处理"""
        if event.event_type.value == "PLAYER_SPEECH":
            speaker_id = event.event_data.get("player_id") or event.event_data.get("speaker_id")
            content = event.event_data.get("content", "")
            
            # 如果有人跳预言家，标记
            if speaker_id and ("预言家" in content or "查验" in content or "验了" in content):
                profile = agent.memory.semantic.get_or_create(speaker_id)
                if not profile.claimed_role:
                    profile.claimed_role = "SEER"
