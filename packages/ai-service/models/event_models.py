"""
事件相关数据模型
"""
from enum import Enum
from typing import Any, Dict, Optional
from pydantic import BaseModel


class EventType(str, Enum):
    """事件类型"""
    GAME_START = "GAME_START"
    PHASE_CHANGE = "PHASE_CHANGE"
    PLAYER_DIED = "PLAYER_DIED"
    PLAYER_SPEECH = "PLAYER_SPEECH"
    VOTE_RESULT = "VOTE_RESULT"
    NIGHT_RESULT = "NIGHT_RESULT"
    GAME_END = "GAME_END"
    
    # 角色技能相关
    SEER_CHECK_RESULT = "SEER_CHECK_RESULT"
    WITCH_SAVE_USED = "WITCH_SAVE_USED"
    WITCH_POISON_USED = "WITCH_POISON_USED"
    HUNTER_SHOOT = "HUNTER_SHOOT"


class GameEvent(BaseModel):
    """游戏事件"""
    game_id: str
    player_id: int
    event_type: EventType
    event_data: Dict[str, Any]
    round: int
    phase: str
    timestamp: Optional[str] = None


class EventNotificationRequest(BaseModel):
    """事件通知请求"""
    game_id: str
    player_id: int
    event_type: EventType
    event_data: Dict[str, Any]
