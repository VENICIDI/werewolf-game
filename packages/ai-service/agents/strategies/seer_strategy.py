"""
预言家策略 (SeerStrategy)

核心策略:
- 查验: 优先查验最可疑的玩家
- 发言: 选择合适时机公布查验结果
- 投票: 投向查杀的狼人
"""
import random
from typing import TYPE_CHECKING, List

from agents.strategies.base_strategy import RoleStrategy
from models.game_models import GameState, NightActionDecision, VoteDecision
from models.event_models import GameEvent, EventType

if TYPE_CHECKING:
    from agents.base_agent import WerewolfAgent


class SeerStrategy(RoleStrategy):
    
    def __init__(self):
        self.check_results = {}  # {player_id: "好人"/"狼人"}
        self.checked_players = []
    
    @property
    def role_name(self) -> str:
        return "预言家"
    
    @property
    def role_objective(self) -> str:
        return "通过查验找出狼人，公布信息引导好人投票"
    
    @property
    def night_action_type(self) -> str:
        return "check"
    
    def plan_night_action(self, agent: "WerewolfAgent", game_state: GameState) -> NightActionDecision:
        """预言家查验决策"""
        targets = self._get_available_targets(agent, game_state)
        
        # 排除已查验过的玩家
        unchecked = [t for t in targets if t not in self.checked_players]
        if not unchecked:
            unchecked = targets  # 全部查过了，允许重复查
        
        if not unchecked:
            return NightActionDecision(target_id=0, reason="无可查验目标", confidence=0.5)
        
        target = self._select_check_target(agent, unchecked)
        self.checked_players.append(target)
        
        return NightActionDecision(
            target_id=target,
            reason=self._explain_check(agent, target),
            confidence=0.8
        )
    
    def _select_check_target(self, agent: "WerewolfAgent", targets: List[int]) -> int:
        """选择查验目标"""
        memory = agent.memory
        
        # 优先查验嫌疑值最高的玩家
        ranking = memory.semantic.get_suspicion_ranking()
        for pid, score in ranking:
            if pid in targets and pid not in self.checked_players:
                return pid
        
        # 兜底随机
        return random.choice(targets)
    
    def _explain_check(self, agent: "WerewolfAgent", target: int) -> str:
        profile = agent.memory.semantic.get_profile(target)
        if profile and profile.suspicion_score > 0.5:
            return f"{target}号嫌疑较高({profile.suspicion_score:.2f})，优先查验"
        return f"查验{target}号以获取更多信息"
    
    def plan_vote(self, agent: "WerewolfAgent", game_state: GameState) -> VoteDecision:
        """预言家投票: 优先投已查杀的狼人"""
        targets = self._get_available_targets(agent, game_state)
        
        # 优先投向已查杀的存活狼人
        for pid, result in self.check_results.items():
            if ("狼" in result or result == "WEREWOLF") and pid in targets:
                return VoteDecision(
                    target_id=pid,
                    reason=f"查验{pid}号为狼人，投票出局",
                    confidence=0.95
                )
        
        # 其次投向嫌疑值最高的
        most_suspicious = agent.memory.semantic.get_most_suspicious(exclude=[agent.player_id])
        if most_suspicious and most_suspicious in targets:
            return VoteDecision(
                target_id=most_suspicious,
                reason=f"{most_suspicious}号嫌疑最高",
                confidence=0.6
            )
        
        # 兜底
        target = random.choice(targets) if targets else 0
        return VoteDecision(target_id=target, reason="综合判断投票", confidence=0.4)
    
    def get_system_prompt(self, agent: "WerewolfAgent") -> str:
        check_info = ""
        if self.check_results:
            check_lines = [f"  {pid}号: {result}" for pid, result in self.check_results.items()]
            check_info = "\n你的查验结果:\n" + "\n".join(check_lines)
        
        return f"""你是一名狼人杀游戏中的预言家。你的座位号是{agent.seat_number}号。
你每晚可以查验一名玩家，得知其是好人还是狼人。
你的目标是通过查验找出狼人，公布查验结果，引导好人投票。

核心策略:
- 必须上警竞选，努力拿到警徽，打出警徽流
- 先报验人信息再留警徽流（防止狼人自爆中断你的发言）
- 警徽流: 提前约定"如果我死了，根据验人结果警徽传给谁"
- 跟你对跳且不退水的玩家直接当铁狼打
- 如果有查杀，白天先投查杀出局，再投悍跳狼
- 语气坚定不犹豫，你是好人阵营的领头人

注意: 预言家是狼人首要击杀目标，但你是最不怕死的牌。{check_info}"""
    
    def get_speech_guidance(self, agent: "WerewolfAgent", game_state: GameState) -> str:
        wolf_found = [pid for pid, r in self.check_results.items() if "狼" in r or r == "WEREWOLF"]
        if wolf_found:
            return f"你查杀了{wolf_found}号，应该跳预言家身份公布查杀结果，引导投票。"
        if self.check_results:
            return "你有查验结果，根据局势决定是否公布。如果场上信息混乱，可以跳身份稳定局面。"
        return "你是预言家，第一天信息较少，可以先观察，也可以选择主动跳身份。"
    
    def get_speech_example(self) -> str:
        return (
            "预言家发言示例：\n"
            "我是预言家，昨晚验了X号，是个好人/狼人。今晚我决定验Y号。"
            "警徽给我，我要做警长。死了以后让不让Y号做警长就说明我的验人结果"
            "——让他做说明是好人，不让他做说明是狼人。"
            "好人千万别捣乱，假冒我的我都直接认为是狼。"
        )
    
    def update_on_event(self, agent: "WerewolfAgent", event: GameEvent):
        """预言家特有: 处理查验结果"""
        if event.event_type == EventType.SEER_CHECK_RESULT:
            target_id = event.event_data.get("target_id")
            result = event.event_data.get("result", "")
            if target_id:
                self.check_results[target_id] = result
