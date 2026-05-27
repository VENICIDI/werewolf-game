"""
Reranker 精排服务
=================

基于 BAAI/bge-reranker-base 的 Cross-Encoder 精排。

为什么需要 Rerank？
- 向量召回（Bi-Encoder）独立编码 query 和 doc，速度快但精度有限
- Reranker（Cross-Encoder）把 query+doc 拼起来同时编码，能捕获细粒度交互
- 工业界经典两阶段范式: **召回多 (k=20~50)** → **精排少 (top_n=3~5)**

设计:
- 懒加载（首次调用才下载模型，避免启动慢）
- CPU 推理友好（base 版本 ~280MB）
- 失败优雅降级（reranker 不可用则原序返回）
"""
from __future__ import annotations

import os
import logging
from typing import List, Optional, Tuple

from langchain_core.documents import Document

logger = logging.getLogger(__name__)

_DEFAULT_MODEL = os.getenv("RERANKER_MODEL", "BAAI/bge-reranker-base")


class RerankerService:
    """BGE Reranker 包装 (Cross-Encoder)"""

    def __init__(self, model_name: str = _DEFAULT_MODEL):
        self.model_name = model_name
        self._model = None
        self._failed = False

    def _ensure_model(self):
        if self._model is not None or self._failed:
            return
        try:
            from sentence_transformers import CrossEncoder
            logger.info(f"Loading reranker model: {self.model_name} ...")
            self._model = CrossEncoder(self.model_name, device="cpu")
            logger.info(f"Reranker loaded: {self.model_name}")
        except Exception as e:
            logger.error(f"Failed to load reranker {self.model_name}: {e}")
            self._failed = True

    def is_available(self) -> bool:
        self._ensure_model()
        return self._model is not None

    def rerank(
        self,
        query: str,
        candidates: List[Tuple[Document, float]],
        top_n: int = 5,
    ) -> List[Tuple[Document, float]]:
        """
        对候选文档精排

        Args:
            query: 原始 query
            candidates: 召回阶段输出的候选 [(Document, recall_score), ...]
            top_n: 精排后保留的 top_n

        Returns:
            重排后的 [(Document, rerank_score), ...]，按 rerank_score 降序
            rerank_score 经过 sigmoid 归一化到 [0, 1]
        """
        if not candidates:
            return []

        self._ensure_model()
        if self._model is None:
            # 模型不可用，原序返回
            return candidates[:top_n]

        try:
            pairs = [[query, doc.page_content] for doc, _ in candidates]
            scores = self._model.predict(pairs, show_progress_bar=False)

            # bge-reranker 输出原始 logit，做 sigmoid 归一化
            import math
            normalized = [1.0 / (1.0 + math.exp(-float(s))) for s in scores]

            reranked = [
                (doc, score)
                for (doc, _), score in zip(candidates, normalized)
            ]
            reranked.sort(key=lambda x: -x[1])
            return reranked[:top_n]
        except Exception as e:
            logger.warning(f"Rerank failed, falling back to recall order: {e}")
            return candidates[:top_n]


# 单例
_reranker: Optional[RerankerService] = None


def get_reranker() -> RerankerService:
    global _reranker
    if _reranker is None:
        _reranker = RerankerService()
    return _reranker
