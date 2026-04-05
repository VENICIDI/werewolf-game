"""
Agent 管理 API 路由
提供 Agent 创建、销毁、事件通知、决策接口
"""
from fastapi import APIRouter, HTTPException
from agents.agent_manager import agent_manager
from models.agent_models import (
    CreateAgentRequest,
    DestroyAgentRequest,
    AgentEventRequest,
    NightActionRequest,
    SpeechRequest,
    VoteRequest
)
from models.game_models import GameState
from models.event_models import GameEvent

router = APIRouter(prefix="/api/agents", tags=["agents"])


@router.post("/create")
async def create_agent(request: CreateAgentRequest):
    """
    创建 Agent 实例
    
    Body:
        - game_id: 游戏 ID
        - player_id: 玩家 ID
        - role: 角色（WEREWOLF/SEER/WITCH/HUNTER/GUARD/VILLAGER）
        - persona: 人格（aggressive/analytical/cautious/charming）
        - teammates: 狼人队友列表（可选）
        - seat_number: 座位号
        - player_ids: 所有玩家 ID 列表（用于初始化记忆）
        - seat_map: 座位映射
    """
    try:
        agent = await agent_manager.create_agent(
            game_id=request.game_id,
            player_id=request.player_id,
            role=request.role,
            persona=request.persona,
            teammates=request.teammates,
            seat_number=request.seat_number,
            player_ids=request.player_ids,
            seat_map=request.seat_map,
        )
        
        return {
            "success": True,
            "message": "Agent 创建成功",
            "data": agent.get_info()
        }
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"创建失败: {str(e)}")


@router.post("/destroy")
async def destroy_agent(request: DestroyAgentRequest):
    """销毁 Agent 实例"""
    try:
        success = await agent_manager.destroy_agent(
            game_id=request.game_id,
            player_id=request.player_id
        )
        
        if not success:
            raise HTTPException(status_code=404, detail="Agent 不存在")
        
        return {"success": True, "message": "Agent 销毁成功"}
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"销毁失败: {str(e)}")


@router.post("/event")
async def send_event(request: AgentEventRequest):
    """
    推送游戏事件给 Agent
    
    Body:
        - game_id: 游戏 ID
        - player_id: 玩家 ID
        - event_type: 事件类型
        - event_data: 事件数据
        - round: 当前回合
        - phase: 当前阶段
    """
    try:
        agent = await agent_manager.get_agent(
            game_id=request.game_id,
            player_id=request.player_id
        )
        
        if not agent:
            raise HTTPException(status_code=404, detail="Agent 不存在")
        
        # 构建事件对象（包含所有必填字段）
        event = GameEvent(
            game_id=request.game_id,
            player_id=request.player_id,
            event_type=request.event_type,
            event_data=request.event_data,
            round=request.round,
            phase=request.phase,
        )
        
        await agent.receive_event(event)
        
        return {"success": True, "message": "事件推送成功"}
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"事件推送失败: {str(e)}")


@router.post("/action/night")
async def night_action(request: NightActionRequest):
    """
    获取夜间行动决策
    
    Returns:
        - action: 行动类型 (kill/check/save/poison/guard/skip)
        - target_id: 目标 ID
        - reason: 决策理由
        - confidence: 信心值
    """
    try:
        agent = await agent_manager.get_agent(
            game_id=request.game_id,
            player_id=request.player_id
        )
        
        if not agent:
            raise HTTPException(status_code=404, detail="Agent 不存在")
        
        # dict -> GameState 转换
        game_state = GameState(**request.game_state)
        decision = await agent.decide_night_action(game_state)
        
        return {
            "success": True,
            "data": {
                "action": decision.action,
                "target_id": decision.target_id,
                "reason": decision.reason,
                "confidence": decision.confidence
            }
        }
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"决策失败: {str(e)}")


@router.post("/action/speak")
async def speak(request: SpeechRequest):
    """
    生成白天发言
    
    Returns:
        - content: 发言内容
        - emotion: 情感
        - targets_mentioned: 提及的玩家
        - strategy: 策略
    """
    try:
        agent = await agent_manager.get_agent(
            game_id=request.game_id,
            player_id=request.player_id
        )
        
        if not agent:
            raise HTTPException(status_code=404, detail="Agent 不存在")
        
        # dict -> GameState 转换，使用正确的字段名 speak_context
        game_state = GameState(**request.game_state)
        decision = await agent.generate_speech(
            game_state=game_state,
            context=request.speak_context
        )
        
        return {
            "success": True,
            "data": {
                "content": decision.content,
                "emotion": decision.emotion,
                "targets_mentioned": decision.targets_mentioned,
                "strategy": decision.strategy
            }
        }
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"发言生成失败: {str(e)}")


@router.post("/action/vote")
async def vote(request: VoteRequest):
    """
    获取投票决策
    
    Returns:
        - target_id: 投票目标 ID (0=弃票)
        - reason: 投票理由
    """
    try:
        agent = await agent_manager.get_agent(
            game_id=request.game_id,
            player_id=request.player_id
        )
        
        if not agent:
            raise HTTPException(status_code=404, detail="Agent 不存在")
        
        # dict -> GameState 转换
        game_state = GameState(**request.game_state)
        decision = await agent.decide_vote(game_state)
        
        return {
            "success": True,
            "data": {
                "target_id": decision.target_id,
                "reason": decision.reason
            }
        }
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"投票决策失败: {str(e)}")


@router.get("/list")
async def list_agents(game_id: str = None):
    """列出 Agent 实例"""
    try:
        agents = await agent_manager.list_agents(game_id=game_id)
        return {"success": True, "data": agents, "count": len(agents)}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"查询失败: {str(e)}")


@router.get("/stats")
async def get_stats():
    """获取 Agent 统计信息"""
    try:
        stats = agent_manager.get_stats()
        return {"success": True, "data": stats}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"获取统计失败: {str(e)}")
