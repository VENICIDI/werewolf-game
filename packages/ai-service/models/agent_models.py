"""
Agent 相关数据模型
"""
from enum import Enum
from typing import Optional
from typing import Optional
from pydantic import BaseModel, Field

from .game_models import Role


class Persona(str, Enum):
    """人格类型"""
    AGGRESSIVE = "aggressive"     # 激进型
    ANALYTICAL = "analytical"     # 分析型
    CAUTIOUS = "cautious"         # 谨慎型
    CHARMING = "charming"         # 魅力型


class AgentStatus(str, Enum):
    """Agent 状态"""
    ACTIVE = "active"
    INACTIVE = "inactive"
    ERROR = "error"


class CreateAgentRequest(BaseModel):
    """创建 Agent 请求"""
    game_id: str
    player_id: int
    role: Role
    persona: Persona = Persona.ANALYTICAL
    teammates: Optional[list] = None  # 狼人队友列表
    seat_number: int = 0
    player_ids: Optional[list] = None  # 所有玩家 ID（用于初始化记忆）
    seat_map: Optional[dict] = None  # 座位映射 {player_id: seat_number}


class DestroyAgentRequest(BaseModel):
    """销毁 Agent 请求"""
    game_id: str
    player_id: int


class AgentInfo(BaseModel):
    """Agent 信息"""
    game_id: str
    player_id: int
    role: Role
    persona: Persona
    status: AgentStatus
    created_at: str


class NightActionRequest(BaseModel):
    """夜间行动请求"""
    game_id: str
    player_id: int
    game_state: dict


class SpeechRequest(BaseModel):
    """发言请求"""
    game_id: str
    player_id: int
    game_state: dict
    speak_context: str = Field(description="discussion/defense/claim_role")


class VoteRequest(BaseModel):
    """投票请求"""
    game_id: str
    player_id: int
    game_state: dict
