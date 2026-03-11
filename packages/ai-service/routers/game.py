"""Game AI router"""

from typing import List, Optional
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

router = APIRouter()


class GameContext(BaseModel):
    """Game context for AI decision"""
    game_id: str
    player_id: str
    role: str
    role_team: str
    day: int
    phase: str  # NIGHT, DAY_DISCUSSION, DAY_VOTE, etc.
    alive_players: List[str]
    dead_players: List[dict]
    messages: List[dict]  # Chat history
    my_checks: Optional[List[dict]] = None  # For prophet
    my_medicine: Optional[bool] = None  # For witch
    my_poison: Optional[bool] = None  # For witch


class AISpeakRequest(BaseModel):
    context: GameContext
    speak_type: str  # NORMAL, DEFENSE, LAST_WORD, etc.


class AISpeakResponse(BaseModel):
    content: str
    emotion: str = "neutral"
    target_player: Optional[str] = None


class AIActionRequest(BaseModel):
    context: GameContext
    action_type: str  # VOTE, KILL, CHECK, SAVE, POISON, SHOOT, etc.


class AIActionResponse(BaseModel):
    action: str
    target: Optional[str] = None
    reason: str


class AIVoteRequest(BaseModel):
    context: GameContext
    candidates: List[str]


class AIVoteResponse(BaseModel):
    vote_for: str
    reason: str


@router.post("/speak", response_model=AISpeakResponse)
async def ai_speak(request: AISpeakRequest):
    """Generate AI speech based on game context"""
    # TODO: Implement AI speech generation with RAG
    return AISpeakResponse(
        content="我是好人，请大家相信我。",
        emotion="calm",
        target_player=None
    )


@router.post("/action", response_model=AIActionResponse)
async def ai_action(request: AIActionRequest):
    """Generate AI action based on game context"""
    # TODO: Implement AI action decision
    return AIActionResponse(
        action=request.action_type,
        target=None,
        reason="暂无明确目标"
    )


@router.post("/vote", response_model=AIVoteResponse)
async def ai_vote(request: AIVoteRequest):
    """Generate AI vote decision"""
    # TODO: Implement AI vote logic
    if request.candidates:
        return AIVoteResponse(
            vote_for=request.candidates[0],
            reason="根据发言判断"
        )
    return AIVoteResponse(
        vote_for="",
        reason="无投票目标"
    )


@router.post("/think")
async def ai_think(context: GameContext):
    """Get AI's internal thinking process"""
    # TODO: Implement AI reasoning
    return {
        "thoughts": "正在分析局势...",
        "suspects": [],
        "trusted": [],
        "strategy": "隐藏身份，观察局势"
    }


@router.get("/roles")
async def get_supported_roles():
    """Get list of supported AI roles"""
    return {
        "roles": [
            {"role": "WEREWOLF", "name": "狼人", "supported": True},
            {"role": "VILLAGER", "name": "平民", "supported": True},
            {"role": "PROPHET", "name": "预言家", "supported": True},
            {"role": "WITCH", "name": "女巫", "supported": True},
            {"role": "HUNTER", "name": "猎人", "supported": True},
            {"role": "GUARD", "name": "守卫", "supported": False},
            {"role": "IDIOT", "name": "白痴", "supported": False},
        ]
    }