"""
工作记忆 (Working Memory)

存储当前回合的实时信息:
- 当前阶段上下文
- 最近的发言记录
- 当前回合的事件
- 容量有限，每回合重置非关键部分
"""
from typing import List, Dict, Optional, Any
from dataclasses import dataclass, field
import logging

logger = logging.getLogger(__name__)

MAX_RECENT_SPEECHES = 20


@dataclass
class WorkingMemory:
    """工作记忆 - 当前回合实时信息"""
    
    current_round: int = 0
    current_phase: str = ""
    
    # 当前回合事件
    round_events: List[Dict[str, Any]] = field(default_factory=list)
    
    # 最近 N 条发言
    recent_speeches: List[Dict[str, Any]] = field(default_factory=list)
    
    # 当前回合死亡信息
    round_deaths: List[int] = field(default_factory=list)
    
    # 本回合投票记录
    round_votes: Dict[int, int] = field(default_factory=dict)
    
    # 临时标记（如：被查验结果、女巫信息等）
    temp_flags: Dict[str, Any] = field(default_factory=dict)
    
    def update_phase(self, round_num: int, phase: str):
        """更新当前阶段"""
        if round_num != self.current_round:
            self._on_new_round(round_num)
        self.current_phase = phase
    
    def _on_new_round(self, new_round: int):
        """新回合开始时重置"""
        self.current_round = new_round
        self.round_events.clear()
        self.round_deaths.clear()
        self.round_votes.clear()
        self.temp_flags.clear()
        # recent_speeches 保留（跨回合也有参考价值）
    
    def add_event(self, event_type: str, data: Dict[str, Any]):
        """添加当前回合事件"""
        self.round_events.append({
            "type": event_type,
            "data": data,
            "round": self.current_round,
            "phase": self.current_phase
        })
    
    def add_speech(self, player_id: int, content: str, round_num: int):
        """记录发言"""
        self.recent_speeches.append({
            "player_id": player_id,
            "content": content,
            "round": round_num
        })
        # 保持在最大数量内
        if len(self.recent_speeches) > MAX_RECENT_SPEECHES:
            self.recent_speeches = self.recent_speeches[-MAX_RECENT_SPEECHES:]
    
    def add_death(self, player_id: int):
        """记录死亡"""
        self.round_deaths.append(player_id)
    
    def add_vote(self, voter_id: int, target_id: int):
        """记录投票"""
        self.round_votes[voter_id] = target_id
    
    def set_flag(self, key: str, value: Any):
        """设置临时标记"""
        self.temp_flags[key] = value
    
    def get_flag(self, key: str, default=None) -> Any:
        """获取临时标记"""
        return self.temp_flags.get(key, default)
    
    def format_context(self) -> str:
        """格式化为 Prompt 上下文文本"""
        lines = [f"当前: 第{self.current_round}天 {self.current_phase}阶段"]
        
        if self.round_deaths:
            lines.append(f"本回合死亡: {self.round_deaths}")
        
        if self.recent_speeches:
            lines.append("最近发言:")
            for s in self.recent_speeches[-5:]:
                lines.append(f"  {s['player_id']}号: {s['content'][:80]}")
        
        if self.round_votes:
            vote_str = ", ".join(f"{v}号→{t}号" for v, t in self.round_votes.items())
            lines.append(f"本轮投票: {vote_str}")
        
        return "\n".join(lines)
