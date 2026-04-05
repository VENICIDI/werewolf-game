"""
证据分析器 (EvidenceAnalyzer)

从记忆系统中提取可用证据，供贝叶斯推理引擎使用。

分析维度:
- 发言矛盾检测
- 投票方向异常检测
- 身份声明冲突
- 互保/对立关系
"""
from typing import List, Dict, Optional, TYPE_CHECKING
import logging

if TYPE_CHECKING:
    from agents.memory.memory_system import MemorySystem
    from agents.reasoning.bayesian_reasoner import BayesianReasoner

logger = logging.getLogger(__name__)


class EvidenceAnalyzer:
    """证据分析器"""
    
    def __init__(self, my_player_id: int):
        self.my_player_id = my_player_id
    
    def analyze_vote_round(
        self,
        memory: "MemorySystem",
        reasoner: "BayesianReasoner",
        round_num: int,
        votes: Dict[int, int],
        eliminated: Optional[int] = None
    ):
        """
        分析一轮投票，提取证据
        
        检测:
        - 谁投了已知好人（可疑）
        - 谁没投已知狼人（保狼嫌疑）
        - 互投关系
        """
        for voter_id, target_id in votes.items():
            if voter_id == self.my_player_id:
                continue
            
            target_profile = memory.semantic.get_profile(target_id)
            
            # 投票目标是已确认好人 → 投票者可疑
            if target_profile and target_profile.known_role in ("GOOD", "SEER", "WITCH", "HUNTER", "GUARD"):
                reasoner.add_evidence(
                    round=round_num,
                    evidence_type="vote_anomaly",
                    target_id=voter_id,
                    description=f"{voter_id}号投了已知好人{target_id}号"
                )
            
            # 没投已确认狼人 → 保狼嫌疑
            for wolf_id in reasoner.confirmed_wolves:
                if wolf_id in votes.values():
                    continue  # 有人投了
                # 这个voter没投狼人
                if target_id != wolf_id and wolf_id != voter_id:
                    reasoner.add_evidence(
                        round=round_num,
                        evidence_type="protected_wolf",
                        target_id=voter_id,
                        description=f"{voter_id}号在{wolf_id}号被确认狼人后未投票",
                        custom_ratio=1.3
                    )
    
    def analyze_speech_claim(
        self,
        memory: "MemorySystem",
        reasoner: "BayesianReasoner",
        round_num: int,
        speaker_id: int,
        claimed_role: str,
    ):
        """
        分析身份声明
        
        检测:
        - 多人声称同一角色（至少一个假的）
        - 声称与已知信息冲突
        """
        if speaker_id == self.my_player_id:
            return
        
        # 检查是否有其他人也声称同一角色
        for pid, profile in memory.semantic.profiles.items():
            if pid == speaker_id or pid == self.my_player_id:
                continue
            if profile.claimed_role == claimed_role:
                # 两人声称同一角色，至少一个是假的
                # 给两人都增加一点嫌疑
                reasoner.add_evidence(
                    round=round_num,
                    evidence_type="identity_conflict",
                    target_id=speaker_id,
                    description=f"{speaker_id}号和{pid}号都声称{claimed_role}",
                    custom_ratio=1.5
                )
                reasoner.add_evidence(
                    round=round_num,
                    evidence_type="identity_conflict",
                    target_id=pid,
                    description=f"{pid}号和{speaker_id}号都声称{claimed_role}",
                    custom_ratio=1.5
                )
    
    def analyze_death_pattern(
        self,
        memory: "MemorySystem",
        reasoner: "BayesianReasoner",
        round_num: int,
        dead_player_id: int,
        cause: str,
    ):
        """
        分析死亡模式
        
        - 被刀的人的盟友关系 → 与死者对立的人嫌疑增加
        """
        if cause != "killed":
            return
        
        dead_profile = memory.semantic.get_profile(dead_player_id)
        if not dead_profile:
            return
        
        # 死者嫌疑低（大概率好人）→ 与死者关系差的人嫌疑增加
        if dead_profile.suspicion_score < 0.3:
            allies = memory.semantic.get_allies(dead_player_id)
            for pid, profile in memory.semantic.profiles.items():
                if pid == self.my_player_id or pid == dead_player_id:
                    continue
                if not profile.is_alive:
                    continue
                # 如果和死者关系好 → 可能也是好人
                if pid in allies:
                    reasoner.add_evidence(
                        round=round_num,
                        evidence_type="strong_defense",
                        target_id=pid,
                        description=f"{pid}号与被刀的{dead_player_id}号关系好",
                        custom_ratio=0.85
                    )
    
    def apply_survival_bias(
        self,
        reasoner: "BayesianReasoner",
        round_num: int,
        alive_players: List[int],
    ):
        """
        存活偏差: 越晚存活的玩家，如果一直没被怀疑，嫌疑稍增
        深水狼倾向于隐藏较深
        """
        if round_num < 3:
            return  # 前两轮不应用
        
        for pid in alive_players:
            if pid == reasoner.my_player_id:
                continue
            if pid in reasoner.confirmed_wolves or pid in reasoner.confirmed_goods:
                continue
            
            current = reasoner.posteriors.get(pid, 0.33)
            # 如果嫌疑一直很低（0.2~0.4范围），轻微上调
            if 0.15 < current < 0.4:
                reasoner.add_evidence(
                    round=round_num,
                    evidence_type="survival_bias",
                    target_id=pid,
                    description=f"第{round_num}天{pid}号仍存活且嫌疑较低",
                    custom_ratio=1.05
                )
