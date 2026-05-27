"""
Query 改写服务
==============

支持两种 Query 改写策略:

1) HyDE (Hypothetical Document Embeddings, Gao et al., ACL 2023)
   - 让 LLM 先"假设性"回答 query，把生成的伪文档作为检索 query
   - 原理: 伪答案的语义空间与真实文档更接近，弥补 query-doc 语义鸿沟

2) Multi-Query
   - 让 LLM 把原 query 改写成 N 个不同角度的子 query
   - 并行检索后合并候选，提升召回多样性

二者都通过环境变量开关，默认关闭（节省一次 LLM 调用）
"""
from __future__ import annotations

import os
import re
import logging
import asyncio
from typing import List, Optional

logger = logging.getLogger(__name__)


HYDE_PROMPT_TEMPLATE = """你是一名狼人杀资深玩家和教练。

请就下面的问题，写一段 100-200 字的中文专业回答，
回答中要包含具体的策略、术语、角色名称、阶段判断等关键词。
不要解释你在做什么，直接给出回答。

问题: {query}

回答:"""


MULTI_QUERY_PROMPT_TEMPLATE = """你是一名狼人杀知识库检索专家。

给定一个原始检索 query，请改写成 3 个表达不同但意图一致的中文 query，
覆盖不同角度（如：术语描述、场景描述、对手视角、阶段视角等）。

要求:
- 每行一个 query
- 不要编号
- 不要解释
- 每个 query 必须能独立用于知识库检索

原始 query: {query}

3 个改写 query:"""


class QueryRewriter:
    """Query 改写器：HyDE / Multi-Query"""

    def __init__(self, llm_service=None):
        """
        Args:
            llm_service: LLMService 实例，None 则禁用所有 LLM 改写能力
        """
        self.llm_service = llm_service

    def is_available(self) -> bool:
        return self.llm_service is not None and self.llm_service.llm is not None

    # ------------------------------------------------------------------
    # HyDE
    # ------------------------------------------------------------------

    async def hyde(self, query: str, timeout: float = 10.0) -> Optional[str]:
        """
        HyDE: 生成假设性答案

        Returns:
            生成的伪文档；失败返回 None
        """
        if not self.is_available():
            return None
        try:
            llm = self.llm_service.get_llm()
            prompt = HYDE_PROMPT_TEMPLATE.format(query=query)
            resp = await asyncio.wait_for(llm.ainvoke(prompt), timeout=timeout)
            text = resp.content if hasattr(resp, "content") else str(resp)
            text = (text or "").strip()
            if not text:
                return None
            return text
        except asyncio.TimeoutError:
            logger.warning(f"HyDE timed out for query: {query[:30]}")
            return None
        except Exception as e:
            logger.warning(f"HyDE failed: {e}")
            return None

    # ------------------------------------------------------------------
    # Multi-Query
    # ------------------------------------------------------------------

    async def multi_query(
        self,
        query: str,
        n: int = 3,
        timeout: float = 10.0,
    ) -> List[str]:
        """
        Multi-Query: 生成 N 个改写 query

        Returns:
            包含原 query 的列表（原 query 始终保留作为兜底）
        """
        if not self.is_available():
            return [query]
        try:
            llm = self.llm_service.get_llm()
            prompt = MULTI_QUERY_PROMPT_TEMPLATE.format(query=query)
            resp = await asyncio.wait_for(llm.ainvoke(prompt), timeout=timeout)
            text = resp.content if hasattr(resp, "content") else str(resp)

            rewrites = _parse_multi_query(text or "")[:n]
            rewrites = [q for q in rewrites if q and q != query]
            return [query] + rewrites  # 始终保留原 query
        except asyncio.TimeoutError:
            logger.warning(f"Multi-query timed out for query: {query[:30]}")
            return [query]
        except Exception as e:
            logger.warning(f"Multi-query failed: {e}")
            return [query]


def _parse_multi_query(text: str) -> List[str]:
    """从 LLM 输出中提取改写后的 query 列表"""
    lines = []
    for raw in text.splitlines():
        line = raw.strip()
        if not line:
            continue
        # 去除编号、bullet 等前缀
        line = re.sub(r"^[\d一二三四五六七八九十]+[\.\)、:：]\s*", "", line)
        line = re.sub(r"^[-*•]\s*", "", line)
        line = line.strip(' "\'')
        if line and len(line) <= 100:
            lines.append(line)
    return lines


# ---------------------------------------------------------------------------
# Module-level singleton
# ---------------------------------------------------------------------------

_rewriter: Optional[QueryRewriter] = None


def get_query_rewriter(llm_service=None) -> QueryRewriter:
    global _rewriter
    if _rewriter is None or (llm_service is not None and _rewriter.llm_service is None):
        _rewriter = QueryRewriter(llm_service=llm_service)
    return _rewriter
