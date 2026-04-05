"""Health check router"""

from fastapi import APIRouter
from pydantic import BaseModel
from agents.agent_manager import agent_manager
from services.llm_service import LLMService
from services.rag_service import RAGService

router = APIRouter()


class HealthResponse(BaseModel):
    status: str
    service: str
    version: str
    llm_available: bool
    chroma_available: bool
    active_agents: int


@router.get("/health", response_model=HealthResponse)
async def health_check():
    """
    Health check endpoint
    检查 FastAPI 服务、LLM 连接、ChromaDB 状态、活跃 Agent 数量
    """
    # 检查 LLM 连接
    llm_available = False
    try:
        llm_service = LLMService()
        llm_available = await llm_service.test_connection()
    except Exception:
        pass
    
    # 检查 ChromaDB 状态
    chroma_available = False
    try:
        rag_service = RAGService()
        chroma_available = rag_service.is_available()
    except Exception:
        pass
    
    # 活跃 Agent 数量
    stats = agent_manager.get_stats()
    active_agents = stats.get("total_agents", 0)
    
    return HealthResponse(
        status="healthy",
        service="werewolf-ai",
        version="1.0.0",
        llm_available=llm_available,
        chroma_available=chroma_available,
        active_agents=active_agents
    )