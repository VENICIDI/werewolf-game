"""
混合检索器 (Hybrid Retriever)
==============================

实现:
- BM25 稀疏检索（基于 jieba 中文分词）
- 与向量稠密检索通过 RRF (Reciprocal Rank Fusion) 融合
- 支持 metadata 过滤（与向量检索一致）

为什么要混合检索？
- 向量检索擅长**语义相似**（"狼人首夜刀谁" ≈ "狼人优先击杀谁"）
- BM25 擅长**关键词精准命中**（专有名词、术语，如"警徽流2.0" "悍跳"）
- RRF 是当前业界最稳健的无参融合方案（无需调权重，对分数尺度不敏感）

参考: Microsoft & Google IR 2009: "Reciprocal Rank Fusion outperforms Condorcet
and individual rank learning methods"
"""
from __future__ import annotations

import re
import logging
from typing import Any, Dict, List, Optional, Tuple

from langchain_core.documents import Document

logger = logging.getLogger(__name__)


# ---------------------------------------------------------------------------
# 中文分词
# ---------------------------------------------------------------------------

_JIEBA_INITIALIZED = False


def _tokenize_zh(text: str) -> List[str]:
    """jieba 精确模式分词 + 简单清洗"""
    global _JIEBA_INITIALIZED
    import jieba

    if not _JIEBA_INITIALIZED:
        jieba.initialize()
        _JIEBA_INITIALIZED = True

    text = re.sub(r"\s+", " ", text or "")
    tokens = [t.strip().lower() for t in jieba.lcut(text)]
    return [t for t in tokens if t and len(t) > 0 and not t.isspace()]


# ---------------------------------------------------------------------------
# BM25 Retriever
# ---------------------------------------------------------------------------


class BM25Retriever:
    """
    基于 rank_bm25 的稀疏检索器

    用法:
        bm25 = BM25Retriever(documents)
        hits = bm25.search("狼人首夜首刀策略", k=10)
        # → List[Tuple[Document, float]]  (score 已归一化到 [0, 1])
    """

    def __init__(self, documents: List[Document]):
        from rank_bm25 import BM25Okapi

        self._documents = documents
        self._tokenized_corpus = [_tokenize_zh(d.page_content) for d in documents]
        self._bm25 = BM25Okapi(self._tokenized_corpus)
        logger.info(f"BM25Retriever built with {len(documents)} documents")

    def search(
        self,
        query: str,
        k: int = 10,
        filter_dict: Optional[Dict[str, Any]] = None,
    ) -> List[Tuple[Document, float]]:
        tokens = _tokenize_zh(query)
        if not tokens:
            return []

        scores = self._bm25.get_scores(tokens)
        if scores.size == 0:
            return []

        max_score = float(scores.max()) or 1.0

        ranked: List[Tuple[Document, float]] = []
        for doc, score in zip(self._documents, scores):
            if filter_dict and not _match_filter(doc.metadata, filter_dict):
                continue
            ranked.append((doc, float(score) / max_score))

        ranked.sort(key=lambda x: -x[1])
        return ranked[:k]


def _match_filter(metadata: Dict[str, Any], filter_dict: Dict[str, Any]) -> bool:
    for k, v in (filter_dict or {}).items():
        if metadata.get(k) != v:
            return False
    return True


# ---------------------------------------------------------------------------
# RRF Fusion
# ---------------------------------------------------------------------------


def reciprocal_rank_fusion(
    rankings: List[List[Tuple[Document, float]]],
    k_rrf: int = 60,
    top_k: int = 10,
) -> List[Tuple[Document, float]]:
    """
    Reciprocal Rank Fusion

    score_rrf(d) = sum_{r in rankings} 1 / (k_rrf + rank_r(d))

    Args:
        rankings: 多个有序候选列表，每个是 [(Document, score), ...]，按相关度降序
        k_rrf: RRF 常数，常用 60。值越大 → 各检索器越平等；越小 → 头部权重越高
        top_k: 返回融合后的 top_k

    Returns:
        融合后的 [(Document, rrf_score), ...]，按 rrf_score 降序

    去重策略: 使用 chunk 的 page_content 作为 key（同一段文本即视为同一文档）
    """
    fused: Dict[str, Dict[str, Any]] = {}

    for ranking in rankings:
        for rank, (doc, _score) in enumerate(ranking, start=1):
            key = _doc_key(doc)
            entry = fused.setdefault(key, {"doc": doc, "score": 0.0})
            entry["score"] += 1.0 / (k_rrf + rank)

    results = [(v["doc"], v["score"]) for v in fused.values()]
    results.sort(key=lambda x: -x[1])
    return results[:top_k]


def _doc_key(doc: Document) -> str:
    """构造文档唯一 key 用于去重"""
    src = doc.metadata.get("source", "")
    return f"{src}::{hash(doc.page_content)}"
