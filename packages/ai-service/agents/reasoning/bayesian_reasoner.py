"""
贝叶斯嫌疑推理引擎

核心: 维护每个玩家是狼人的后验概率
P(狼人|证据) ∝ P(证据|狼人) × P(狼人)

证据类型及似然比:
- 被预言家查杀       ×10.0
- 被预言家验好人     ×0.05
- 与已知狼人互保     ×3.0
- 与已知狼人对立     ×0.5
- 发言逻辑矛盾       ×1.5
- 投票方向异常       ×1.8
- 声称身份与事实冲突  ×2.5
- 被多人怀疑         ×1.3
"""
from typing import Dict, List, Optional, Tuple
from dataclasses import dataclass, field
import logging

logger = logging.getLogger(__name__)


@dataclass
class Evidence:
    """证据条目"""
    round: int
    evidence_type: str
    target_id: int
    likelihood_ratio: float  # >1 增加嫌疑, <1 降低嫌疑
    description: str


# 预定义证据的似然比
LIKELIHOOD_RATIOS = {
    "seer_kill": 10.0,       # 被预言家查杀
    "seer_clear": 0.05,      # 被预言家验好人
    "ally_with_wolf": 3.0,   # 与已知狼人互保
    "oppose_wolf": 0.5,      # 与已知狼人对立
    "speech_contradiction": 1.5,  # 发言逻辑矛盾
    "vote_anomaly": 1.8,     # 投票方向异常
    "identity_conflict": 2.5, # 声称身份冲突
    "multiple_accused": 1.3,  # 被多人怀疑
    "survival_bias": 1.1,    # 存活偏差（每轮递增）
    "protected_wolf": 2.0,   # 疑似保狼行为
    "soft_accusation": 1.2,  # 轻度怀疑
    "strong_defense": 0.8,   # 合理辩护（降低嫌疑）
}


class BayesianReasoner:
    """贝叶斯嫌疑推理引擎"""
    
    def __init__(self, my_player_id: int, total_players: int = 9, wolf_count: int = 3):
        self.my_player_id = my_player_id
        self.total_players = total_players
        self.wolf_count = wolf_count
        
        # 先验概率
        self.prior = wolf_count / total_players
        
        # 每个玩家的后验概率 (嫌疑值)
        self.posteriors: Dict[int, float] = {}
        
        # 证据记录
        self.evidence_log: List[Evidence] = []
        
        # 已确认身份
        self.confirmed_wolves: List[int] = []
        self.confirmed_goods: List[int] = []
    
    def init_players(self, player_ids: List[int]):
        """初始化所有玩家的先验概率"""
        for pid in player_ids:
            if pid != self.my_player_id:
                self.posteriors[pid] = self.prior
    
    def add_evidence(
        self,
        round: int,
        evidence_type: str,
        target_id: int,
        description: str = "",
        custom_ratio: Optional[float] = None,
    ):
        """
        添加证据并更新后验概率
        
        Args:
            round: 回合数
            evidence_type: 证据类型（对应 LIKELIHOOD_RATIOS 的 key）
            target_id: 目标玩家 ID
            description: 描述
            custom_ratio: 自定义似然比（覆盖预定义）
        """
        ratio = custom_ratio or LIKELIHOOD_RATIOS.get(evidence_type, 1.0)
        
        evidence = Evidence(
            round=round,
            evidence_type=evidence_type,
            target_id=target_id,
            likelihood_ratio=ratio,
            description=description
        )
        self.evidence_log.append(evidence)
        
        # 贝叶斯更新
        self._update_posterior(target_id, ratio)
        
        logger.debug(
            f"Evidence: {evidence_type} on player {target_id}, "
            f"ratio={ratio:.2f}, new posterior={self.posteriors.get(target_id, 0):.3f}"
        )
    
    def _update_posterior(self, target_id: int, likelihood_ratio: float):
        """贝叶斯更新单个玩家的后验概率"""
        if target_id not in self.posteriors:
            return
        
        prior = self.posteriors[target_id]
        
        # P(wolf|evidence) = P(evidence|wolf) * P(wolf) / P(evidence)
        # P(evidence) = P(evidence|wolf)*P(wolf) + P(evidence|good)*P(good)
        # 假设 P(evidence|good) = 1/likelihood_ratio (简化)
        
        p_wolf = prior
        p_good = 1 - prior
        
        p_evidence_given_wolf = likelihood_ratio
        p_evidence_given_good = 1.0
        
        p_evidence = p_evidence_given_wolf * p_wolf + p_evidence_given_good * p_good
        
        if p_evidence > 0:
            posterior = (p_evidence_given_wolf * p_wolf) / p_evidence
        else:
            posterior = prior
        
        # 限制范围 [0.01, 0.99]
        self.posteriors[target_id] = max(0.01, min(0.99, posterior))
    
    def confirm_wolf(self, player_id: int):
        """确认某玩家是狼人"""
        if player_id not in self.confirmed_wolves:
            self.confirmed_wolves.append(player_id)
        self.posteriors[player_id] = 0.99
    
    def confirm_good(self, player_id: int):
        """确认某玩家是好人"""
        if player_id not in self.confirmed_goods:
            self.confirmed_goods.append(player_id)
        self.posteriors[player_id] = 0.01
    
    def mark_dead(self, player_id: int):
        """标记玩家死亡（不从 posteriors 中移除，保留分析价值）"""
        pass
    
    def get_ranking(self, alive_players: Optional[List[int]] = None) -> List[Tuple[int, float]]:
        """获取嫌疑排名（从高到低）"""
        items = []
        for pid, score in self.posteriors.items():
            if alive_players and pid not in alive_players:
                continue
            if pid == self.my_player_id:
                continue
            items.append((pid, score))
        return sorted(items, key=lambda x: x[1], reverse=True)
    
    def get_top_suspects(self, n: int = 3, alive_players: Optional[List[int]] = None) -> List[Tuple[int, float]]:
        """获取前 N 个嫌疑人"""
        return self.get_ranking(alive_players)[:n]
    
    def format_analysis(self, alive_players: Optional[List[int]] = None) -> str:
        """格式化推理分析结果"""
        ranking = self.get_ranking(alive_players)
        if not ranking:
            return "暂无推理数据"
        
        lines = ["嫌疑分析:"]
        for i, (pid, score) in enumerate(ranking, 1):
            status = ""
            if pid in self.confirmed_wolves:
                status = " [确认狼人]"
            elif pid in self.confirmed_goods:
                status = " [确认好人]"
            lines.append(f"  {i}. {pid}号: {score:.2%}{status}")
        
        if self.confirmed_wolves:
            lines.append(f"已确认狼人: {self.confirmed_wolves}")
        
        return "\n".join(lines)
    
    def sync_to_semantic(self, semantic_memory):
        """将推理结果同步到语义记忆的嫌疑值"""
        for pid, score in self.posteriors.items():
            profile = semantic_memory.get_profile(pid)
            if profile:
                profile.suspicion_score = score
