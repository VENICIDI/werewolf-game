"""
RAG 管理后台 API
================

提供给毕设答辩演示用的统一 admin 接口:

- GET  /api/admin/overview                  - 总览仪表盘数据
- GET  /api/admin/knowledge/documents       - 知识库源文档列表（含元数据聚合）
- GET  /api/admin/knowledge/documents/{name}/chunks - 单个文档的所有切片
- POST /api/admin/knowledge/search          - RAG 在线检索调试（带相似度分数）
- POST /api/admin/knowledge/rebuild         - 一键重建向量库
- GET  /api/admin/logs/retrieval            - 检索日志（分页/筛选）
- GET  /api/admin/logs/retrieval/stats      - 检索日志聚合统计
- DELETE /api/admin/logs/retrieval          - 清空检索日志
- GET  /api/admin/logs/llm                  - LLM 调用日志列表（按 game_id）
- GET  /api/admin/logs/llm/{game_id}        - 单局游戏完整 LLM 日志
- GET  /api/admin/system/status             - 系统状态（embedding / vectorstore / llm）
"""
from __future__ import annotations

import os
import time
import logging
from pathlib import Path
from typing import Any, Dict, List, Optional

from fastapi import APIRouter, HTTPException, Query
from pydantic import BaseModel, Field

from services.rag_service import get_rag_service
from services import rag_log_service

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/api/admin", tags=["admin"])


# ---------------------------------------------------------------------------
# Models
# ---------------------------------------------------------------------------


class SearchRequest(BaseModel):
    query: str = Field(..., description="检索 query")
    top_k: int = Field(5, ge=1, le=20)
    role_filter: Optional[str] = Field(None, description="按角色过滤")


class SearchHit(BaseModel):
    rank: int
    score: float
    source: str
    role: Optional[str] = None
    doc_type: Optional[str] = None
    game_phase: Optional[str] = None
    content: str


class SearchResponse(BaseModel):
    query: str
    role_filter: Optional[str]
    top_k: int
    duration_ms: float
    hits: List[SearchHit]


# ---------------------------------------------------------------------------
# Overview Dashboard
# ---------------------------------------------------------------------------


@router.get("/overview")
async def get_overview():
    """聚合仪表盘所需的全部数据，一次返回"""
    rag = get_rag_service()
    stats = rag.get_stats() if rag.is_available() else {}
    retrieval_stats = rag_log_service.get_stats(since_hours=24)

    llm_log_count, llm_games = _count_llm_games()

    return {
        "knowledge": {
            "status": stats.get("status", "unknown"),
            "document_count": stats.get("document_count", 0),
            "total_chunks": stats.get("total_chunks", 0),
            "embedding_model": stats.get("embedding_model"),
            "persist_dir": stats.get("persist_dir"),
            "sources": stats.get("sources", []),
        },
        "retrieval": retrieval_stats,
        "llm": {
            "total_games": llm_games,
            "total_log_files": llm_log_count,
        },
    }


def _count_llm_games() -> tuple[int, int]:
    log_dir = _ai_log_dir() / "llm"
    if not log_dir.exists():
        return 0, 0
    files = list(log_dir.glob("game-*-llm.log"))
    return len(files), len({_extract_game_id(f.name) for f in files})


def _extract_game_id(filename: str) -> str:
    # game-{id}-llm.log
    name = filename.removeprefix("game-").removesuffix("-llm.log")
    return name


# ---------------------------------------------------------------------------
# Knowledge Base Inspection
# ---------------------------------------------------------------------------


@router.get("/knowledge/documents")
async def list_documents():
    """列出知识库中所有源文档，聚合 chunk 数 / 角色 / 文档类型"""
    rag = get_rag_service()
    if not rag.is_available() or not rag.vectorstore:
        raise HTTPException(status_code=503, detail="向量库未初始化")

    try:
        coll = rag.vectorstore._collection
        result = coll.get(include=["metadatas", "documents"])
        metas = result.get("metadatas") or []
        docs = result.get("documents") or []

        agg: Dict[str, Dict[str, Any]] = {}
        for meta, content in zip(metas, docs):
            if not meta:
                continue
            src = meta.get("source", "unknown")
            name = Path(src).name
            entry = agg.setdefault(name, {
                "name": name,
                "source": src,
                "chunk_count": 0,
                "char_count": 0,
                "roles": set(),
                "doc_types": set(),
                "game_phases": set(),
            })
            entry["chunk_count"] += 1
            entry["char_count"] += len(content or "")
            if meta.get("role"):
                entry["roles"].add(meta["role"])
            if meta.get("doc_type"):
                entry["doc_types"].add(meta["doc_type"])
            if meta.get("game_phase"):
                entry["game_phases"].add(meta["game_phase"])

        items = []
        for v in agg.values():
            items.append({
                "name": v["name"],
                "source": v["source"],
                "chunk_count": v["chunk_count"],
                "char_count": v["char_count"],
                "roles": sorted(list(v["roles"])),
                "doc_types": sorted(list(v["doc_types"])),
                "game_phases": sorted(list(v["game_phases"])),
            })
        items.sort(key=lambda x: x["name"])

        return {
            "total": len(items),
            "items": items,
        }
    except Exception as e:
        logger.error(f"list_documents failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/knowledge/documents/{name}/chunks")
async def list_document_chunks(name: str):
    """列出某个源文档的所有切片"""
    rag = get_rag_service()
    if not rag.is_available() or not rag.vectorstore:
        raise HTTPException(status_code=503, detail="向量库未初始化")

    try:
        coll = rag.vectorstore._collection
        result = coll.get(include=["metadatas", "documents"])
        metas = result.get("metadatas") or []
        docs = result.get("documents") or []
        ids = result.get("ids") or []

        items = []
        for chunk_id, meta, content in zip(ids, metas, docs):
            if not meta:
                continue
            src_name = Path(meta.get("source", "")).name
            if src_name != name:
                continue
            items.append({
                "id": chunk_id,
                "content": content,
                "char_count": len(content or ""),
                "role": meta.get("role"),
                "doc_type": meta.get("doc_type"),
                "game_phase": meta.get("game_phase"),
                "difficulty": meta.get("difficulty"),
            })

        if not items:
            raise HTTPException(status_code=404, detail=f"未找到文档 {name} 的切片")

        return {
            "name": name,
            "total": len(items),
            "items": items,
        }
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"list_document_chunks failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/knowledge/raw/{name}")
async def get_raw_document(name: str):
    """读取知识库源 Markdown 文件原文"""
    knowledge_dir = Path(os.getenv("KNOWLEDGE_DIR", "./knowledge"))
    target = knowledge_dir / name
    if not target.exists() or not target.is_file():
        raise HTTPException(status_code=404, detail=f"源文件不存在: {name}")
    try:
        content = target.read_text(encoding="utf-8")
        return {
            "name": name,
            "size": len(content),
            "content": content,
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/knowledge/search", response_model=SearchResponse)
async def search_knowledge(req: SearchRequest):
    """RAG 检索调试器：返回带相似度分数的命中结果"""
    rag = get_rag_service()
    if not rag.is_available():
        raise HTTPException(status_code=503, detail="RAG 服务不可用")

    t0 = time.perf_counter()
    pairs = rag.query_with_scores(
        query_text=req.query,
        role_filter=req.role_filter,
        k=req.top_k,
        source="admin",
    )
    duration_ms = round((time.perf_counter() - t0) * 1000, 2)

    hits = []
    for i, (doc, score) in enumerate(pairs, start=1):
        hits.append(SearchHit(
            rank=i,
            score=round(float(score), 4),
            source=Path(doc.metadata.get("source", "unknown")).name,
            role=doc.metadata.get("role"),
            doc_type=doc.metadata.get("doc_type"),
            game_phase=doc.metadata.get("game_phase"),
            content=doc.page_content,
        ))

    return SearchResponse(
        query=req.query,
        role_filter=req.role_filter,
        top_k=req.top_k,
        duration_ms=duration_ms,
        hits=hits,
    )


@router.post("/knowledge/rebuild")
async def rebuild_knowledge():
    """一键重建知识库"""
    try:
        rag = get_rag_service()
        result = rag.rebuild()
        return {"success": True, **result}
    except FileNotFoundError as e:
        raise HTTPException(status_code=404, detail=str(e))
    except Exception as e:
        logger.error(f"rebuild_knowledge failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))


# ---------------------------------------------------------------------------
# Retrieval Logs
# ---------------------------------------------------------------------------


@router.get("/logs/retrieval")
async def get_retrieval_logs(
    keyword: Optional[str] = Query(None),
    role_filter: Optional[str] = Query(None),
    source: Optional[str] = Query(None),
    game_id: Optional[str] = Query(None),
    since_hours: Optional[int] = Query(None, ge=1, le=720),
    page: int = Query(1, ge=1),
    page_size: int = Query(20, ge=1, le=200),
):
    """查询检索日志"""
    return rag_log_service.query_logs(
        keyword=keyword,
        role_filter=role_filter,
        source=source,
        game_id=game_id,
        since_hours=since_hours,
        page=page,
        page_size=page_size,
    )


@router.get("/logs/retrieval/stats")
async def get_retrieval_stats(since_hours: int = Query(24, ge=1, le=720)):
    """检索日志聚合统计"""
    return rag_log_service.get_stats(since_hours=since_hours)


@router.delete("/logs/retrieval")
async def clear_retrieval_logs():
    cleared = rag_log_service.clear_logs()
    return {"success": True, "cleared": cleared}


# ---------------------------------------------------------------------------
# LLM Logs
# ---------------------------------------------------------------------------


def _ai_log_dir() -> Path:
    base = os.getenv("LOG_DIR", os.path.join(os.path.dirname(os.path.dirname(__file__)), "..", "..", "logs"))
    return Path(base) / "ai-service"


@router.get("/logs/llm")
async def list_llm_games():
    """列出有 LLM 日志的所有游戏 ID"""
    log_dir = _ai_log_dir() / "llm"
    if not log_dir.exists():
        return {"total": 0, "items": []}

    items = []
    for f in log_dir.glob("game-*-llm.log"):
        try:
            stat = f.stat()
            items.append({
                "game_id": _extract_game_id(f.name),
                "file": f.name,
                "size": stat.st_size,
                "modified": int(stat.st_mtime * 1000),
            })
        except Exception:
            continue

    items.sort(key=lambda x: -x["modified"])
    return {"total": len(items), "items": items}


@router.get("/logs/llm/{game_id}")
async def get_llm_game_log(
    game_id: str,
    tail: Optional[int] = Query(None, ge=1, le=5000, description="只返回最后 N 行"),
):
    """读取某局游戏完整 LLM 日志"""
    log_file = _ai_log_dir() / "llm" / f"game-{game_id}-llm.log"
    if not log_file.exists():
        raise HTTPException(status_code=404, detail=f"未找到 game={game_id} 的 LLM 日志")
    try:
        content = log_file.read_text(encoding="utf-8", errors="replace")
        if tail:
            lines = content.splitlines()
            content = "\n".join(lines[-tail:])
        return {
            "game_id": game_id,
            "file": log_file.name,
            "size": log_file.stat().st_size,
            "content": content,
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


# ---------------------------------------------------------------------------
# System Status
# ---------------------------------------------------------------------------


@router.get("/system/status")
async def system_status():
    rag = get_rag_service()
    stats = rag.get_stats() if rag.is_available() else {}
    return {
        "rag_available": rag.is_available(),
        "vectorstore": {
            "persist_dir": stats.get("persist_dir"),
            "embedding_model": stats.get("embedding_model"),
            "total_chunks": stats.get("total_chunks", 0),
            "document_count": stats.get("document_count", 0),
        },
        "env": {
            "USE_LOCAL_EMBEDDING": os.getenv("USE_LOCAL_EMBEDDING", "true"),
            "LOCAL_EMBEDDING_MODEL": os.getenv("LOCAL_EMBEDDING_MODEL", "BAAI/bge-small-zh-v1.5"),
            "OPENAI_MODEL": os.getenv("OPENAI_MODEL", ""),
            "OPENAI_BASE_URL": os.getenv("OPENAI_BASE_URL", ""),
            "CHROMA_PERSIST_DIR": os.getenv("CHROMA_PERSIST_DIR", "./chroma_db"),
            "KNOWLEDGE_DIR": os.getenv("KNOWLEDGE_DIR", "./knowledge"),
        },
    }
