"""
游戏相关数据模型
"""
from enum import Enum
from typing import List, Optional, Dict
from pydantic import BaseModel, Field


class Role(str, Enum):
    """角色枚举"""
    WEREWOLF = "WEREWOLF"
    SEER = "SEER"
    WITCH = "WITCH"
    HUNTER = "HUNTER"
    GUARD = "GUARD"
    VILLAGER = "VILLAGER"


class GamePhase(str, Enum):
    """游戏阶段枚举"""
    NONE = "NONE"
    NIGHT_START = "NIGHT_START"
    GUARD = "GUARD"
    WEREWOLF = "WEREWOLF"
    SEER = "SEER"
    WITCH = "WITCH"
    HUNTER = "HUNTER"
    DAY_START = "DAY_START"
    DISCUSSION = "DISCUSSION"
    VOTING = "VOTING"
    EXECUTION = "EXECUTION"


class ActionType(str, Enum):
    """行动类型枚举"""
    KILL = "kill"           # 狼人击杀
    CHECK = "check"         # 预言家查验
    SAVE = "save"           # 女巫救人
    POISON = "poison"       # 女巫毒人
    GUARD = "guard"         # 守卫守护
    VOTE = "vote"           # 投票
    SHOOT = "shoot"         # 猎人射击
    SKIP = "skip"           # 跳过


class PlayerInfo(BaseModel):
    """玩家信息"""
    player_id: int
    is_alive: bool
    role: Optional[Role] = None  # AI 自己的角色会知道
    claimed_role: Optional[Role] = None  # 玩家声称的角色


class GameState(BaseModel):
    """游戏状态"""
    game_id: str
    round: int
    phase: GamePhase
    alive_players: List[int]
    dead_players: List[int]
    player_infos: Dict[int, PlayerInfo]
    
    # 夜间结果（白天才知道）
    last_night_deaths: List[int] = Field(default_factory=list)
    
    # 投票记录
    vote_history: List[Dict] = Field(default_factory=list)
    
    # 发言记录
    speech_history: List[Dict] = Field(default_factory=list)


class NightActionDecision(BaseModel):
    """夜间行动决策结果"""
    target_id: int = Field(description="目标玩家ID, 0表示不行动")
    reason: str = Field(description="行动理由")
    confidence: float = Field(ge=0, le=1, description="信心度 0-1")


class SpeechDecision(BaseModel):
    """发言决策结果"""
    content: str = Field(description="发言内容, 100-200字")
    emotion: str = Field(description="情绪: calm/suspicious/angry/confident/nervous")
    targets_mentioned: List[int] = Field(default_factory=list, description="提到的玩家ID列表")
    claim_role: Optional[Role] = Field(default=None, description="是否跳身份")


class VoteDecision(BaseModel):
    """投票决策结果"""
    target_id: int = Field(description="投票目标ID, 0表示弃票")
    reason: str = Field(description="投票理由")
    confidence: float = Field(ge=0, le=1, description="信心度")
