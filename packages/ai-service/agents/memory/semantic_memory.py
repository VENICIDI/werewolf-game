"""
语义记忆 (Semantic Memory)

存储抽象归纳的信息:
- 玩家画像 (PlayerProfile): 发言风格、投票倾向、可疑行为、声称身份
- 阵营推断
- 关系图 (谁和谁站边)
"""
from typing import List, Optional, Dict, Tuple
from dataclasses import dataclass, field
import logging

logger = logging.getLogger(__name__)


@dataclass
class PlayerProfile:
    """玩家画像"""
    player_id: int
    seat_number: int = 0
    
    # 身份信息
    claimed_role: Optional[str] = None
    known_role: Optional[str] = None  # 确认身份（如预言家查验结果）
    
    # 行为记录
    speech_summaries: List[str] = field(default_factory=list)
    vote_targets: List[Tuple[int, int]] = field(default_factory=list)  # [(round, target)]
    suspicious_behaviors: List[str] = field(default_factory=list)
    
    # 评估值
    suspicion_score: float = 0.33  # 嫌疑值 0~1
    trust_score: float = 0.5  # 信任值 0~1
    
    # 状态
    is_alive: bool = True
    death_round: Optional[int] = None
    death_cause: Optional[str] = None
    
    def add_speech_summary(self, summary: str):
        """添加发言摘要"""
        self.speech_summaries.append(summary)
        # 保留最近 5 条
        if len(self.speech_summaries) > 5:
            self.speech_summaries = self.speech_summaries[-5:]
    
    def add_vote(self, round: int, target: int):
        """记录投票"""
        self.vote_targets.append((round, target))
    
    def add_suspicious(self, behavior: str):
        """记录可疑行为"""
        self.suspicious_behaviors.append(behavior)
        if len(self.suspicious_behaviors) > 5:
            self.suspicious_behaviors = self.suspicious_behaviors[-5:]
    
    def mark_dead(self, round: int, cause: str):
        """标记死亡"""
        self.is_alive = False
        self.death_round = round
        self.death_cause = cause
    
    def format_brief(self) -> str:
        """格式化为简短描述"""
        status = "存活" if self.is_alive else f"死亡(第{self.death_round}天,{self.death_cause})"
        role_str = f"声称{self.claimed_role}" if self.claimed_role else "未声称身份"
        if self.known_role:
            role_str = f"已知{self.known_role}"
        return f"{self.player_id}号: {status}, {role_str}, 嫌疑{self.suspicion_score:.2f}"


class SemanticMemory:
    """语义记忆 - 玩家画像与关系推断"""
    
    def __init__(self, my_player_id: int):
        self.my_player_id = my_player_id
        self.profiles: Dict[int, PlayerProfile] = {}
        
        # 关系图: (A, B) -> 关系强度 (-1 对立 ~ +1 互保)
        self.relationships: Dict[Tuple[int, int], float] = {}
    
    def init_players(self, player_ids: List[int], seat_map: Optional[Dict[int, int]] = None):
        """初始化所有玩家画像"""
        for pid in player_ids:
            if pid not in self.profiles:
                seat = seat_map.get(pid, 0) if seat_map else 0
                self.profiles[pid] = PlayerProfile(player_id=pid, seat_number=seat)
    
    def get_profile(self, player_id: int) -> Optional[PlayerProfile]:
        """获取玩家画像"""
        return self.profiles.get(player_id)
    
    def get_or_create(self, player_id: int) -> PlayerProfile:
        """获取或创建玩家画像"""
        if player_id not in self.profiles:
            self.profiles[player_id] = PlayerProfile(player_id=player_id)
        return self.profiles[player_id]
    
    def update_suspicion(self, player_id: int, delta: float, reason: str = ""):
        """更新嫌疑值"""
        profile = self.get_or_create(player_id)
        old = profile.suspicion_score
        profile.suspicion_score = max(0.0, min(1.0, profile.suspicion_score + delta))
        if reason:
            logger.debug(f"Player {player_id} suspicion: {old:.2f} -> {profile.suspicion_score:.2f} ({reason})")
    
    def set_known_role(self, player_id: int, role: str):
        """设置已知角色（如预言家查验结果）"""
        profile = self.get_or_create(player_id)
        profile.known_role = role
        
        # 根据已知角色调整嫌疑值
        if role == "WEREWOLF":
            profile.suspicion_score = 0.95
        elif role in ("SEER", "WITCH", "HUNTER", "GUARD", "VILLAGER"):
            profile.suspicion_score = 0.05
    
    def set_claimed_role(self, player_id: int, role: str):
        """记录玩家声称的角色"""
        profile = self.get_or_create(player_id)
        profile.claimed_role = role
    
    def update_relationship(self, player_a: int, player_b: int, delta: float):
        """更新两个玩家的关系（正=互保，负=对立）"""
        key = (min(player_a, player_b), max(player_a, player_b))
        current = self.relationships.get(key, 0.0)
        self.relationships[key] = max(-1.0, min(1.0, current + delta))
    
    def get_suspicion_ranking(self, alive_only: bool = True) -> List[Tuple[int, float]]:
        """获取嫌疑值排名（从高到低）"""
        items = []
        for pid, profile in self.profiles.items():
            if pid == self.my_player_id:
                continue
            if alive_only and not profile.is_alive:
                continue
            items.append((pid, profile.suspicion_score))
        return sorted(items, key=lambda x: x[1], reverse=True)
    
    def get_most_suspicious(self, alive_only: bool = True, exclude: Optional[List[int]] = None) -> Optional[int]:
        """获取最可疑的玩家"""
        exclude = exclude or []
        ranking = self.get_suspicion_ranking(alive_only)
        for pid, score in ranking:
            if pid not in exclude:
                return pid
        return None
    
    def get_most_trusted(self, alive_only: bool = True) -> Optional[int]:
        """获取最可信的玩家"""
        ranking = self.get_suspicion_ranking(alive_only)
        if ranking:
            return ranking[-1][0]  # 嫌疑值最低的
        return None
    
    def get_allies(self, player_id: int) -> List[int]:
        """获取与某玩家关系好的人"""
        allies = []
        for (a, b), strength in self.relationships.items():
            if strength > 0.3:
                if a == player_id:
                    allies.append(b)
                elif b == player_id:
                    allies.append(a)
        return allies
    
    def format_profiles(self, alive_only: bool = True) -> str:
        """格式化所有玩家画像"""
        lines = ["=== 玩家画像 ==="]
        for pid, profile in sorted(self.profiles.items()):
            if pid == self.my_player_id:
                continue
            if alive_only and not profile.is_alive:
                continue
            lines.append(profile.format_brief())
        return "\n".join(lines)
    
    def format_suspicion_ranking(self) -> str:
        """格式化嫌疑排名"""
        ranking = self.get_suspicion_ranking()
        if not ranking:
            return "暂无嫌疑数据"
        lines = ["嫌疑排名:"]
        for i, (pid, score) in enumerate(ranking, 1):
            profile = self.profiles[pid]
            extra = ""
            if profile.known_role:
                extra = f" [已知{profile.known_role}]"
            elif profile.claimed_role:
                extra = f" [声称{profile.claimed_role}]"
            lines.append(f"  {i}. {pid}号 嫌疑{score:.2f}{extra}")
        return "\n".join(lines)
