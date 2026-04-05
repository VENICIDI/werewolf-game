"""
情景记忆 (Episodic Memory)

存储每回合的关键事件序列:
- 谁在哪个回合说了什么
- 谁在哪个回合投了谁
- 死亡事件与时间线
- 按时间排序，可按回合/事件类型查询
"""
from typing import List, Optional, Dict, Any
from dataclasses import dataclass, field
import time
import logging

logger = logging.getLogger(__name__)


@dataclass
class EpisodeRecord:
    """情景记忆条目"""
    round: int
    phase: str
    event_type: str
    actor_id: Optional[int] = None
    target_id: Optional[int] = None
    content: Optional[str] = None
    timestamp: float = field(default_factory=time.time)
    importance: float = 0.5  # 0~1，越高越重要

    def to_text(self) -> str:
        """转为文本描述"""
        parts = [f"第{self.round}天{self.phase}"]
        
        if self.event_type == "DEATH":
            parts.append(f"{self.target_id}号玩家死亡")
        elif self.event_type == "SPEECH":
            content_preview = (self.content[:60] + "...") if self.content and len(self.content) > 60 else self.content
            parts.append(f"{self.actor_id}号发言: {content_preview}")
        elif self.event_type == "VOTE":
            parts.append(f"{self.actor_id}号投票给{self.target_id}号")
        elif self.event_type == "VOTE_RESULT":
            parts.append(f"投票结果: {self.target_id}号被投出")
        elif self.event_type == "CHECK_RESULT":
            result = self.content or ""
            parts.append(f"查验{self.target_id}号: {result}")
        elif self.event_type == "SAVE":
            parts.append(f"女巫救了{self.target_id}号")
        elif self.event_type == "POISON":
            parts.append(f"女巫毒了{self.target_id}号")
        elif self.event_type == "GUARD_PROTECT":
            parts.append(f"守卫守护了{self.target_id}号")
        elif self.event_type == "HUNTER_SHOOT":
            parts.append(f"猎人开枪射杀{self.target_id}号")
        else:
            parts.append(f"{self.event_type}")
            if self.content:
                parts.append(f": {self.content[:40]}")
        
        return " ".join(parts)


class EpisodicMemory:
    """情景记忆 - 关键事件序列"""
    
    def __init__(self):
        self.episodes: List[EpisodeRecord] = []
        # 结构化索引
        self.deaths_timeline: List[Dict[str, Any]] = []  # [{round, player_id, cause}]
        self.vote_history: List[Dict[str, Any]] = []  # [{round, votes: {voter: target}, result}]
    
    def add_episode(
        self,
        round: int,
        phase: str,
        event_type: str,
        actor_id: Optional[int] = None,
        target_id: Optional[int] = None,
        content: Optional[str] = None,
        importance: float = 0.5
    ) -> EpisodeRecord:
        """添加一条情景记忆"""
        record = EpisodeRecord(
            round=round,
            phase=phase,
            event_type=event_type,
            actor_id=actor_id,
            target_id=target_id,
            content=content,
            importance=importance
        )
        self.episodes.append(record)
        return record
    
    def record_death(self, round: int, player_id: int, cause: str = "unknown"):
        """记录死亡事件"""
        self.deaths_timeline.append({
            "round": round,
            "player_id": player_id,
            "cause": cause
        })
        self.add_episode(
            round=round, phase="", event_type="DEATH",
            target_id=player_id, content=cause, importance=0.9
        )
    
    def record_vote_round(self, round: int, votes: Dict[int, int], result: Optional[int] = None):
        """记录一轮投票"""
        self.vote_history.append({
            "round": round,
            "votes": votes.copy(),
            "result": result
        })
        # 记录投票结果为情景
        if result:
            self.add_episode(
                round=round, phase="VOTING", event_type="VOTE_RESULT",
                target_id=result, importance=0.8
            )
    
    def get_by_round(self, round: int) -> List[EpisodeRecord]:
        """获取某回合的所有事件"""
        return [e for e in self.episodes if e.round == round]
    
    def get_by_type(self, event_type: str) -> List[EpisodeRecord]:
        """获取某类型的所有事件"""
        return [e for e in self.episodes if e.event_type == event_type]
    
    def get_important(self, min_importance: float = 0.7) -> List[EpisodeRecord]:
        """获取重要事件"""
        return [e for e in self.episodes if e.importance >= min_importance]
    
    def get_player_speeches(self, player_id: int) -> List[EpisodeRecord]:
        """获取某玩家的所有发言"""
        return [e for e in self.episodes if e.event_type == "SPEECH" and e.actor_id == player_id]
    
    def get_player_votes(self, player_id: int) -> List[Dict]:
        """获取某玩家的投票记录"""
        results = []
        for vh in self.vote_history:
            if player_id in vh["votes"]:
                results.append({
                    "round": vh["round"],
                    "target": vh["votes"][player_id]
                })
        return results
    
    def format_timeline(self, last_n_rounds: int = 3) -> str:
        """格式化为时间线文本"""
        if not self.episodes:
            return "暂无历史事件"
        
        max_round = max(e.round for e in self.episodes)
        start_round = max(1, max_round - last_n_rounds + 1)
        
        lines = []
        for r in range(start_round, max_round + 1):
            round_events = self.get_by_round(r)
            if round_events:
                lines.append(f"--- 第{r}天 ---")
                for e in round_events:
                    if e.importance >= 0.5:
                        lines.append(f"  {e.to_text()}")
        
        return "\n".join(lines) if lines else "暂无重要事件"
    
    def format_deaths(self) -> str:
        """格式化死亡时间线"""
        if not self.deaths_timeline:
            return "暂无死亡"
        lines = []
        for d in self.deaths_timeline:
            lines.append(f"第{d['round']}天: {d['player_id']}号死亡({d['cause']})")
        return "\n".join(lines)
