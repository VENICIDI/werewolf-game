"""Knowledge base router"""

from typing import List
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

router = APIRouter()


class QueryRequest(BaseModel):
    query: str
    top_k: int = 3


class KnowledgeItem(BaseModel):
    content: str
    source: str
    score: float


class QueryResponse(BaseModel):
    query: str
    results: List[KnowledgeItem]


class ReloadRequest(BaseModel):
    pass


class ReloadResponse(BaseModel):
    success: bool
    message: str
    document_count: int


@router.post("/query", response_model=QueryResponse)
async def query_knowledge(request: QueryRequest):
    """Query knowledge base using RAG"""
    # TODO: Implement RAG query
    return QueryResponse(
        query=request.query,
        results=[
            KnowledgeItem(
                content="狼人杀是一款多人社交推理游戏...",
                source="01-游戏规则.md",
                score=0.95
            )
        ]
    )


@router.post("/reload", response_model=ReloadResponse)
async def reload_knowledge():
    """Reload knowledge base from files"""
    # TODO: Implement knowledge reload
    return ReloadResponse(
        success=True,
        message="Knowledge base reloaded successfully",
        document_count=5
    )


@router.get("/stats")
async def get_knowledge_stats():
    """Get knowledge base statistics"""
    # TODO: Implement stats
    return {
        "document_count": 5,
        "total_chunks": 50,
        "sources": [
            "01-游戏规则.md",
            "02-角色技能.md",
            "03-发言技巧.md",
            "04-常见板子.md",
            "05-AI策略指南.md"
        ]
    }