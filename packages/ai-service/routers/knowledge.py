"""
Knowledge base router - 知识库管理 API

提供:
- POST /query: RAG 语义检索
- POST /init: 初始化/重建知识库
- GET /stats: 知识库统计
"""

from typing import List, Optional
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
import logging

from services.rag_service import get_rag_service

logger = logging.getLogger(__name__)
router = APIRouter()


class QueryRequest(BaseModel):
    query: str
    top_k: int = 3
    role_filter: Optional[str] = None


class KnowledgeItem(BaseModel):
    content: str
    source: str
    role: Optional[str] = None
    doc_type: Optional[str] = None


class QueryResponse(BaseModel):
    query: str
    results: List[KnowledgeItem]
    total: int


class InitResponse(BaseModel):
    success: bool
    message: str
    documents_loaded: int
    total_chunks: int
    sources: List[str]


@router.post("/query", response_model=QueryResponse)
async def query_knowledge(request: QueryRequest):
    """RAG 语义检索知识库"""
    try:
        rag = get_rag_service()
        
        if not rag.is_available():
            raise HTTPException(status_code=503, detail="RAG service not available")
        
        docs = await rag.aquery(
            query_text=request.query,
            role_filter=request.role_filter,
            k=request.top_k
        )
        
        results = [
            KnowledgeItem(
                content=doc.page_content,
                source=doc.metadata.get("source", "unknown"),
                role=doc.metadata.get("role"),
                doc_type=doc.metadata.get("doc_type"),
            )
            for doc in docs
        ]
        
        return QueryResponse(
            query=request.query,
            results=results,
            total=len(results)
        )
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Knowledge query failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/init", response_model=InitResponse)
async def init_knowledge():
    """
    初始化/重建知识库
    
    从 knowledge/ 目录加载所有 .md 文档，切片后向量化存入 ChromaDB。
    如果已有数据会先清空再重建。
    """
    try:
        rag = get_rag_service()
        result = rag.rebuild()
        
        return InitResponse(
            success=True,
            message=f"Knowledge base rebuilt: {result.get('documents_loaded', 0)} documents, {result.get('total_chunks', 0)} chunks",
            documents_loaded=result.get("documents_loaded", 0),
            total_chunks=result.get("total_chunks", 0),
            sources=result.get("sources", [])
        )
    except FileNotFoundError as e:
        raise HTTPException(status_code=404, detail=str(e))
    except Exception as e:
        logger.error(f"Knowledge init failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/stats")
async def get_knowledge_stats():
    """获取知识库统计信息"""
    try:
        rag = get_rag_service()
        return rag.get_stats()
    except Exception as e:
        logger.error(f"Knowledge stats failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))