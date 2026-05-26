"""
RAG 检索日志服务

功能:
- 记录每次 RAG 检索（query / role / top_k / results / scores / duration）
- 持久化为 JSONL（每行一条），便于追加和增量读取
- 提供查询、筛选、分页能力
- 提供聚合统计（最近 24h 检索量、平均延迟、命中文档分布等）

设计要点:
- 采用 JSONL 而非数据库，零依赖、易迁移、便于离线分析
- 写入加文件锁，避免并发写入串行问题
- 读取使用反向遍历 + 内存筛选，10w 级日志仍然可用
"""
from __future__ import annotations

import os
import json
import time
import threading
from pathlib import Path
from datetime import datetime, timedelta
from typing import Any, Dict, List, Optional


LOG_DIR = os.getenv("LOG_DIR", os.path.join(os.path.dirname(os.path.dirname(__file__)), "..", "..", "logs"))
RAG_LOG_DIR = os.path.join(LOG_DIR, "ai-service", "rag")
Path(RAG_LOG_DIR).mkdir(parents=True, exist_ok=True)

RAG_LOG_FILE = os.path.join(RAG_LOG_DIR, "retrieval.jsonl")

_write_lock = threading.Lock()


def _ts() -> str:
    return datetime.now().isoformat(timespec="seconds")


def log_retrieval(
    query: str,
    role_filter: Optional[str],
    top_k: int,
    results: List[Dict[str, Any]],
    duration_ms: float,
    source: str = "api",
    game_id: Optional[str] = None,
    player_id: Optional[int] = None,
    extra: Optional[Dict[str, Any]] = None,
) -> Dict[str, Any]:
    """
    记录一条 RAG 检索日志

    Args:
        query: 检索 query 文本
        role_filter: 角色过滤（WEREWOLF/SEER/...）
        top_k: 返回数量
        results: 命中的 chunk 摘要列表 [{source, score, snippet, role, doc_type}]
        duration_ms: 检索耗时（毫秒）
        source: 调用来源 (api/agent/admin)
        game_id: 关联的游戏 ID
        player_id: 关联的玩家 ID
        extra: 额外信息
    """
    entry = {
        "ts": _ts(),
        "ts_ms": int(time.time() * 1000),
        "query": query,
        "role_filter": role_filter,
        "top_k": top_k,
        "duration_ms": round(duration_ms, 2),
        "source": source,
        "game_id": game_id,
        "player_id": player_id,
        "hit_count": len(results),
        "results": results,
        "extra": extra or {},
    }
    with _write_lock:
        with open(RAG_LOG_FILE, "a", encoding="utf-8") as f:
            f.write(json.dumps(entry, ensure_ascii=False) + "\n")
    return entry


def _iter_logs_reverse() -> List[Dict[str, Any]]:
    """反向读取所有日志（最新的在最前）"""
    if not os.path.exists(RAG_LOG_FILE):
        return []
    items: List[Dict[str, Any]] = []
    with open(RAG_LOG_FILE, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                items.append(json.loads(line))
            except Exception:
                continue
    items.reverse()
    return items


def query_logs(
    keyword: Optional[str] = None,
    role_filter: Optional[str] = None,
    source: Optional[str] = None,
    game_id: Optional[str] = None,
    since_hours: Optional[int] = None,
    page: int = 1,
    page_size: int = 20,
) -> Dict[str, Any]:
    """
    查询检索日志

    支持的筛选条件:
    - keyword: 在 query 文本中模糊匹配
    - role_filter: 角色过滤
    - source: 调用来源
    - game_id: 游戏 ID
    - since_hours: 最近 N 小时
    """
    items = _iter_logs_reverse()

    since_ts = None
    if since_hours is not None and since_hours > 0:
        since_ts = int((datetime.now() - timedelta(hours=since_hours)).timestamp() * 1000)

    def _match(it: Dict[str, Any]) -> bool:
        if keyword and keyword.strip() and keyword.lower() not in it.get("query", "").lower():
            return False
        if role_filter and it.get("role_filter") != role_filter:
            return False
        if source and it.get("source") != source:
            return False
        if game_id and str(it.get("game_id") or "") != str(game_id):
            return False
        if since_ts and it.get("ts_ms", 0) < since_ts:
            return False
        return True

    filtered = [it for it in items if _match(it)]
    total = len(filtered)
    start = max(0, (page - 1) * page_size)
    end = start + page_size
    page_items = filtered[start:end]

    return {
        "total": total,
        "page": page,
        "page_size": page_size,
        "items": page_items,
    }


def get_stats(since_hours: int = 24) -> Dict[str, Any]:
    """
    聚合统计 - 最近 N 小时

    返回:
    - total: 总检索次数
    - avg_duration_ms: 平均耗时
    - p95_duration_ms: 95 分位耗时
    - by_role: 按角色过滤维度的次数分布
    - by_source: 按来源分布
    - hourly: 最近 24 小时按小时的检索次数（时间序列）
    - top_queries: 出现最多的 query Top 10
    - top_sources: 命中最多的文档源 Top 10
    """
    items = _iter_logs_reverse()
    since_ts = int((datetime.now() - timedelta(hours=since_hours)).timestamp() * 1000)
    recent = [it for it in items if it.get("ts_ms", 0) >= since_ts]

    total = len(recent)
    durations = [float(it.get("duration_ms", 0)) for it in recent]
    avg = round(sum(durations) / total, 2) if total else 0.0
    durations_sorted = sorted(durations)
    p95 = round(durations_sorted[int(len(durations_sorted) * 0.95)], 2) if durations_sorted else 0.0

    by_role: Dict[str, int] = {}
    by_source: Dict[str, int] = {}
    for it in recent:
        role = it.get("role_filter") or "ALL"
        by_role[role] = by_role.get(role, 0) + 1
        src = it.get("source") or "unknown"
        by_source[src] = by_source.get(src, 0) + 1

    hourly: Dict[str, int] = {}
    now = datetime.now().replace(minute=0, second=0, microsecond=0)
    for i in range(since_hours - 1, -1, -1):
        bucket = now - timedelta(hours=i)
        key = bucket.strftime("%H:00")
        hourly[key] = 0
    for it in recent:
        try:
            t = datetime.fromtimestamp(it["ts_ms"] / 1000).replace(minute=0, second=0, microsecond=0)
            key = t.strftime("%H:00")
            if key in hourly:
                hourly[key] += 1
        except Exception:
            continue

    q_counter: Dict[str, int] = {}
    for it in recent:
        q = it.get("query", "")
        q_counter[q] = q_counter.get(q, 0) + 1
    top_queries = sorted(q_counter.items(), key=lambda x: -x[1])[:10]

    src_counter: Dict[str, int] = {}
    for it in recent:
        for r in it.get("results", []):
            s = r.get("source", "unknown")
            src_counter[s] = src_counter.get(s, 0) + 1
    top_sources = sorted(src_counter.items(), key=lambda x: -x[1])[:10]

    return {
        "since_hours": since_hours,
        "total": total,
        "avg_duration_ms": avg,
        "p95_duration_ms": p95,
        "by_role": by_role,
        "by_source": by_source,
        "hourly": [{"hour": k, "count": v} for k, v in hourly.items()],
        "top_queries": [{"query": q, "count": c} for q, c in top_queries],
        "top_sources": [{"source": s, "count": c} for s, c in top_sources],
    }


def clear_logs() -> int:
    """清空检索日志 - 返回被清空的条数"""
    if not os.path.exists(RAG_LOG_FILE):
        return 0
    with _write_lock:
        with open(RAG_LOG_FILE, "r", encoding="utf-8") as f:
            n = sum(1 for _ in f)
        open(RAG_LOG_FILE, "w").close()
    return n
