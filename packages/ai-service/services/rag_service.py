"""
RAG 服务 - 基于 LangChain 的知识检索增强（带混合检索 + Rerank + Query 改写）

检索管线 (可通过环境变量按需开关):

    [用户 Query]
         │
         ├── (可选) Query Rewrite ── HyDE / Multi-Query
         │
         ▼
    [稠密向量检索] (Chroma + BGE Embedding)  ─┐
                                              ├── RRF 融合 (Reciprocal Rank Fusion)
    [稀疏 BM25 检索] (jieba 分词 + Okapi)    ─┘
                                              │
                                              ▼
                                      (可选) Cross-Encoder 精排
                                       BAAI/bge-reranker-base
                                              │
                                              ▼
                                       Top-K 命中切片

环境变量:
    USE_HYBRID_RETRIEVAL   true/false   是否启用 BM25 + 向量混合检索 (默认 true)
    USE_RERANKER           true/false   是否启用 bge-reranker 精排    (默认 true)
    USE_QUERY_REWRITE      none/hyde/multi_query  Query 改写策略     (默认 none)
    RERANK_RECALL_K        int          融合阶段召回数量 (默认 20)
    RERANKER_MODEL         str          reranker 模型 (默认 BAAI/bge-reranker-base)
"""
import os
import time
import asyncio
import logging
from typing import List, Optional, Dict, Tuple, Any
from pathlib import Path

from langchain_community.vectorstores import Chroma
from langchain.text_splitter import RecursiveCharacterTextSplitter
from langchain_community.document_loaders import DirectoryLoader, TextLoader
from langchain_core.documents import Document
from langchain_core.retrievers import BaseRetriever

from services import rag_log_service
from services.hybrid_retriever import BM25Retriever, reciprocal_rank_fusion
from services.reranker_service import get_reranker
from services.query_rewriter import get_query_rewriter

logger = logging.getLogger(__name__)


def _env_bool(key: str, default: bool) -> bool:
    return os.getenv(key, str(default)).strip().lower() in ("1", "true", "yes", "on")


def _short(text: str, n: int = 80) -> str:
    text = (text or "").strip().replace("\n", " ")
    return text if len(text) <= n else text[:n] + "..."


class RAGService:
    """
    RAG 服务 - 知识检索增强生成
    
    功能:
    - 加载知识库文档并向量化
    - 提供语义检索能力
    - 支持按角色过滤
    
    Embedding 模型:
    - 默认: BAAI/bge-small-zh-v1.5 (本地，免费，中文优化)
    - 可选: OpenAI text-embedding-3-small (需 API Key)
    """
    
    def __init__(self):
        self.persist_dir = os.getenv("CHROMA_PERSIST_DIR", "./chroma_db")
        self.collection_name = f"{os.getenv('CHROMA_COLLECTION_PREFIX', 'werewolf')}_knowledge"
        self.knowledge_dir = os.getenv("KNOWLEDGE_DIR", "./knowledge")
        
        # 初始化 Embedding
        self.embeddings = self._init_embeddings()
        
        # 初始化或加载向量数据库
        self.vectorstore: Optional[Chroma] = None
        self._initialized = False
        self._load_or_create_vectorstore()

        # BM25 检索器（懒构建）
        self._bm25: Optional[BM25Retriever] = None
        self._bm25_built_at: float = 0.0

    # ------------------------------------------------------------------
    # BM25 / Reranker 懒构建
    # ------------------------------------------------------------------

    def _get_bm25(self) -> Optional[BM25Retriever]:
        """从 Chroma 中拉取所有 chunk 构建 BM25 语料"""
        if self._bm25 is not None:
            return self._bm25
        if not self.vectorstore:
            return None
        try:
            result = self.vectorstore._collection.get(include=["metadatas", "documents"])
            metas = result.get("metadatas") or []
            docs = result.get("documents") or []
            if not docs:
                return None
            documents = [
                Document(page_content=c or "", metadata=m or {})
                for c, m in zip(docs, metas)
            ]
            self._bm25 = BM25Retriever(documents)
            self._bm25_built_at = time.time()
            logger.info(f"BM25 corpus built from Chroma: {len(documents)} docs")
            return self._bm25
        except Exception as e:
            logger.warning(f"Failed to build BM25 corpus: {e}")
            return None

    def invalidate_bm25(self):
        """重建知识库后调用，让 BM25 语料失效"""
        self._bm25 = None
    
    def _init_embeddings(self):
        """
        初始化 Embedding 模型
        
        优先级:
        1. 环境变量指定 USE_LOCAL_EMBEDDING=true → HuggingFace 本地模型
        2. 环境变量有 EMBEDDING_API_KEY → OpenAI Embedding API
        3. 默认 → HuggingFace 本地模型 (bge-small-zh-v1.5)
        """
        use_local = os.getenv("USE_LOCAL_EMBEDDING", "true").lower() == "true"
        embedding_api_key = os.getenv("EMBEDDING_API_KEY", "")
        
        if not use_local and embedding_api_key:
            return self._init_openai_embeddings(embedding_api_key)
        else:
            return self._init_local_embeddings()
    
    def _init_local_embeddings(self):
        """初始化本地 HuggingFace Embedding (BAAI/bge-small-zh-v1.5)"""
        try:
            from langchain_community.embeddings import HuggingFaceEmbeddings
            
            model_name = os.getenv("LOCAL_EMBEDDING_MODEL", "BAAI/bge-small-zh-v1.5")
            
            embeddings = HuggingFaceEmbeddings(
                model_name=model_name,
                model_kwargs={"device": "cpu"},
                encode_kwargs={"normalize_embeddings": True}
            )
            
            logger.info(f"Local HuggingFace Embedding initialized: {model_name}")
            return embeddings
            
        except ImportError:
            logger.error(
                "sentence-transformers not installed. "
                "Run: pip install sentence-transformers"
            )
            raise
        except Exception as e:
            logger.error(f"Failed to init local embedding: {e}")
            raise
    
    def _init_openai_embeddings(self, api_key: str):
        """初始化 OpenAI Embedding API"""
        from langchain_openai import OpenAIEmbeddings
        
        model = os.getenv("EMBEDDING_MODEL", "text-embedding-3-small")
        base_url = os.getenv("EMBEDDING_BASE_URL", "https://api.openai.com/v1")
        
        embeddings = OpenAIEmbeddings(
            model=model,
            api_key=api_key,
            base_url=base_url,
        )
        
        logger.info(f"OpenAI Embedding initialized: model={model}, base_url={base_url}")
        return embeddings
    
    def _load_or_create_vectorstore(self):
        """加载或创建向量数据库"""
        persist_path = Path(self.persist_dir)
        
        if persist_path.exists() and any(persist_path.iterdir()):
            logger.info(f"Loading existing vectorstore from {self.persist_dir}")
            self.vectorstore = Chroma(
                collection_name=self.collection_name,
                embedding_function=self.embeddings,
                persist_directory=self.persist_dir
            )
            self._initialized = True
        else:
            logger.info(f"Creating new vectorstore at {self.persist_dir}")
            persist_path.mkdir(parents=True, exist_ok=True)
            self.vectorstore = Chroma(
                collection_name=self.collection_name,
                embedding_function=self.embeddings,
                persist_directory=self.persist_dir
            )
    
    def load_documents(self, docs_dir: str) -> int:
        """
        加载知识库文档并向量化
        
        Args:
            docs_dir: 文档目录路径
            
        Returns:
            int: 加载的文档数量
        """
        docs_path = Path(docs_dir)
        if not docs_path.exists():
            raise FileNotFoundError(f"Documents directory not found: {docs_dir}")
        
        logger.info(f"Loading documents from {docs_dir}")
        
        # 加载 Markdown 文档
        loader = DirectoryLoader(
            str(docs_path),
            glob="*.md",
            loader_cls=TextLoader,
            loader_kwargs={"encoding": "utf-8"}
        )
        documents = loader.load()
        
        if not documents:
            logger.warning(f"No documents found in {docs_dir}")
            return 0
        
        # 文档切片
        splitter = RecursiveCharacterTextSplitter(
            chunk_size=500,
            chunk_overlap=50,
            separators=["\n## ", "\n### ", "\n\n", "\n", "。", ""]
        )
        chunks = splitter.split_documents(documents)
        
        # 为每个 chunk 添加元数据（角色、类型等）
        for chunk in chunks:
            self._enrich_metadata(chunk)
        
        # 向量化并存储
        self.vectorstore.add_documents(chunks)
        
        logger.info(f"Loaded {len(documents)} documents, split into {len(chunks)} chunks")
        return len(documents)
    
    def _enrich_metadata(self, doc: Document):
        """
        丰富文档元数据
        
        根据文件名和内容，为文档添加角色、类型、阶段、难度等元数据
        """
        source = doc.metadata.get("source", "")
        filename = Path(source).name.lower()
        content_lower = doc.page_content[:200].lower()
        
        # 根据文件名推断角色
        if "werewolf" in filename or "狼人" in filename or "狼队" in filename:
            doc.metadata["role"] = "WEREWOLF"
        elif "seer" in filename or "预言家" in filename:
            doc.metadata["role"] = "SEER"
        elif "witch" in filename or "女巫" in filename:
            doc.metadata["role"] = "WITCH"
        elif "hunter" in filename or "猎人" in filename:
            doc.metadata["role"] = "HUNTER"
        elif "guard" in filename or "守卫" in filename:
            doc.metadata["role"] = "GUARD"
        
        # 文档类型
        if "rule" in filename or "规则" in filename:
            doc.metadata["doc_type"] = "rules"
        elif "strateg" in filename or "策略" in filename or "战术" in filename:
            doc.metadata["doc_type"] = "strategy"
        elif "speech" in filename or "发言" in filename or "模板" in filename:
            doc.metadata["doc_type"] = "speech"
        elif "警" in filename or "警徽" in filename:
            doc.metadata["doc_type"] = "sheriff"
        elif "案例" in filename or "实战" in filename or "实例" in filename:
            doc.metadata["doc_type"] = "case_study"
        
        # 游戏阶段（根据内容推断）
        if any(kw in content_lower for kw in ["警上", "竞选", "上警", "警徽"]):
            doc.metadata["game_phase"] = "sheriff_election"
        elif any(kw in content_lower for kw in ["夜晚", "夜间", "刀", "首刀", "守护"]):
            doc.metadata["game_phase"] = "night"
        elif any(kw in content_lower for kw in ["投票", "归票", "扛推"]):
            doc.metadata["game_phase"] = "vote"
        elif any(kw in content_lower for kw in ["发言", "讨论", "白天"]):
            doc.metadata["game_phase"] = "discussion"
        
        # 难度等级
        if any(kw in filename for kw in ["新手", "入门", "基础"]):
            doc.metadata["difficulty"] = "beginner"
        elif any(kw in filename for kw in ["进阶", "高阶", "详解"]):
            doc.metadata["difficulty"] = "advanced"
        elif any(kw in content_lower for kw in ["悍跳", "倒钩", "深水", "狼查杀狼", "警徽流2.0"]):
            doc.metadata["difficulty"] = "advanced"
    
    def get_retriever(
        self,
        role_filter: Optional[str] = None,
        k: int = 3,
        search_type: str = "mmr"
    ) -> BaseRetriever:
        """
        获取检索器
        
        Args:
            role_filter: 按角色过滤 (WEREWOLF/SEER/...)
            k: 返回结果数量
            search_type: 检索类型 (similarity/mmr)
            
        Returns:
            BaseRetriever: LangChain Retriever
        """
        search_kwargs = {"k": k, "fetch_k": k * 3}
        
        if role_filter:
            search_kwargs["filter"] = {"role": role_filter}
        
        return self.vectorstore.as_retriever(
            search_type=search_type,
            search_kwargs=search_kwargs
        )
    
    def query(
        self,
        query_text: str,
        role_filter: Optional[str] = None,
        k: int = 3,
        source: str = "api",
        game_id: Optional[str] = None,
        player_id: Optional[int] = None,
    ) -> List[Document]:
        """
        查询知识库（同步），自动记录检索日志
        """
        docs_with_scores = self.query_with_scores(
            query_text=query_text,
            role_filter=role_filter,
            k=k,
            source=source,
            game_id=game_id,
            player_id=player_id,
        )
        return [doc for doc, _ in docs_with_scores]

    async def aquery(
        self,
        query_text: str,
        role_filter: Optional[str] = None,
        k: int = 3,
        source: str = "api",
        game_id: Optional[str] = None,
        player_id: Optional[int] = None,
        pipeline: Optional[Dict[str, Any]] = None,
    ) -> List[Document]:
        """
        异步查询入口（默认走完整管线，可享受 LLM Query 改写）

        若 pipeline.query_rewrite='none'（默认），与同步路径行为一致。
        """
        result = await self.aquery_pipeline(
            query_text=query_text,
            role_filter=role_filter,
            k=k,
            source=source,
            game_id=game_id,
            player_id=player_id,
            pipeline=pipeline,
        )
        return [h["doc"] for h in result["hits"]]

    def query_with_scores(
        self,
        query_text: str,
        role_filter: Optional[str] = None,
        k: int = 3,
        source: str = "api",
        game_id: Optional[str] = None,
        player_id: Optional[int] = None,
        pipeline: Optional[Dict[str, Any]] = None,
    ) -> List[Tuple[Document, float]]:
        """
        查询并返回 (Document, score) 列表（同步入口；管线包含 BM25 / Rerank）

        Args:
            pipeline: 覆盖默认 pipeline 配置 dict:
                - use_hybrid: bool
                - use_reranker: bool
                - query_rewrite: 'none' / 'hyde' / 'multi_query'
                - recall_k: int   召回阶段每路保留数
        """
        result = self.query_pipeline_sync(
            query_text=query_text,
            role_filter=role_filter,
            k=k,
            source=source,
            game_id=game_id,
            player_id=player_id,
            pipeline=pipeline,
        )
        return [(h["doc"], h["score"]) for h in result["hits"]]

    def query_pipeline_sync(
        self,
        query_text: str,
        role_filter: Optional[str] = None,
        k: int = 3,
        source: str = "api",
        game_id: Optional[str] = None,
        player_id: Optional[int] = None,
        pipeline: Optional[Dict[str, Any]] = None,
    ) -> Dict[str, Any]:
        """同步入口（兼容老调用），不支持 LLM Query 改写"""
        cfg = self._resolve_pipeline(pipeline, allow_llm_rewrite=False)
        stages: List[Dict[str, Any]] = []
        t_total = time.perf_counter()

        candidates = self._recall_stage(query_text, role_filter, cfg, stages)
        hits = self._rerank_stage(query_text, candidates, k, cfg, stages)
        return self._finalize(query_text, role_filter, k, source, game_id, player_id,
                              cfg, stages, hits, t_total)

    async def aquery_pipeline(
        self,
        query_text: str,
        role_filter: Optional[str] = None,
        k: int = 3,
        source: str = "api",
        game_id: Optional[str] = None,
        player_id: Optional[int] = None,
        pipeline: Optional[Dict[str, Any]] = None,
    ) -> Dict[str, Any]:
        """
        异步管线入口（支持 LLM Query 改写）

        返回 dict 包含:
            - hits: [{rank, doc, score, source, role, doc_type, game_phase, content}]
            - stages: List[Dict] 每个管线阶段的耗时与候选数
            - pipeline: 实际使用的 pipeline 配置
            - duration_ms: 总耗时
        """
        cfg = self._resolve_pipeline(pipeline, allow_llm_rewrite=True)
        stages: List[Dict[str, Any]] = []
        t_total = time.perf_counter()

        # ========== 阶段 0: Query 改写 ==========
        queries = await self._query_rewrite_stage(query_text, cfg, stages)

        # ========== 阶段 1: 召回（每个 query 都走一遍） ==========
        all_candidates: List[Tuple[Document, float]] = []
        seen_keys = set()
        for q in queries:
            cands = self._recall_stage(q, role_filter, cfg, stages, q_label=q)
            for doc, score in cands:
                key = f"{doc.metadata.get('source')}::{hash(doc.page_content)}"
                if key in seen_keys:
                    continue
                seen_keys.add(key)
                all_candidates.append((doc, score))

        # ========== 阶段 2: Rerank ==========
        hits = self._rerank_stage(query_text, all_candidates, k, cfg, stages)

        return self._finalize(query_text, role_filter, k, source, game_id, player_id,
                              cfg, stages, hits, t_total)

    # ------------------------------------------------------------------
    # Pipeline stages
    # ------------------------------------------------------------------

    def _resolve_pipeline(
        self,
        override: Optional[Dict[str, Any]],
        allow_llm_rewrite: bool,
    ) -> Dict[str, Any]:
        # 默认: 混合检索开，Rerank/Query 改写关
        # （Rerank 与 LLM 改写有较大延迟，agent 实时决策默认不启用，仅在 admin 调试器中按需开启）
        cfg = {
            "use_hybrid": _env_bool("USE_HYBRID_RETRIEVAL", True),
            "use_reranker": _env_bool("USE_RERANKER", False),
            "query_rewrite": os.getenv("USE_QUERY_REWRITE", "none").strip().lower() or "none",
            "recall_k": int(os.getenv("RERANK_RECALL_K", "20")),
        }
        if override:
            for k in ("use_hybrid", "use_reranker", "query_rewrite", "recall_k"):
                if k in override and override[k] is not None:
                    cfg[k] = override[k]

        # 同步入口禁用 LLM 改写
        if not allow_llm_rewrite:
            cfg["query_rewrite"] = "none"
        if cfg["query_rewrite"] not in ("none", "hyde", "multi_query"):
            cfg["query_rewrite"] = "none"
        return cfg

    async def _query_rewrite_stage(
        self,
        query: str,
        cfg: Dict[str, Any],
        stages: List[Dict[str, Any]],
    ) -> List[str]:
        mode = cfg.get("query_rewrite", "none")
        if mode == "none":
            return [query]

        t0 = time.perf_counter()
        rewriter = get_query_rewriter()
        if not rewriter.is_available():
            stages.append({"name": "query_rewrite", "mode": mode, "skipped": "rewriter unavailable",
                           "duration_ms": 0.0, "queries": [query]})
            return [query]

        if mode == "hyde":
            hyde_doc = await rewriter.hyde(query)
            queries = [hyde_doc] if hyde_doc else [query]
            stages.append({
                "name": "query_rewrite", "mode": "hyde",
                "duration_ms": round((time.perf_counter() - t0) * 1000, 2),
                "queries": [_short(hyde_doc or query, 80)],
            })
            return queries

        if mode == "multi_query":
            queries = await rewriter.multi_query(query, n=3)
            stages.append({
                "name": "query_rewrite", "mode": "multi_query",
                "duration_ms": round((time.perf_counter() - t0) * 1000, 2),
                "queries": [_short(q, 80) for q in queries],
            })
            return queries

        return [query]

    def _recall_stage(
        self,
        query: str,
        role_filter: Optional[str],
        cfg: Dict[str, Any],
        stages: List[Dict[str, Any]],
        q_label: Optional[str] = None,
    ) -> List[Tuple[Document, float]]:
        """召回阶段：向量 + (可选) BM25 + RRF 融合"""
        filter_dict = {"role": role_filter} if role_filter else None
        recall_k = max(cfg.get("recall_k", 20), 1)

        # 向量召回
        t_v = time.perf_counter()
        try:
            vec_hits = self.vectorstore.similarity_search_with_relevance_scores(
                query, k=recall_k, filter=filter_dict
            ) if self.vectorstore else []
        except Exception as e:
            logger.error(f"vector recall failed: {e}")
            vec_hits = []
        vec_ms = (time.perf_counter() - t_v) * 1000
        stages.append({
            "name": "vector_recall",
            "duration_ms": round(vec_ms, 2),
            "candidates": len(vec_hits),
            "k": recall_k,
            "q": _short(q_label or query, 60),
        })

        if not cfg.get("use_hybrid"):
            return vec_hits

        # BM25 召回
        t_b = time.perf_counter()
        bm25 = self._get_bm25()
        bm25_hits = bm25.search(query, k=recall_k, filter_dict=filter_dict) if bm25 else []
        bm25_ms = (time.perf_counter() - t_b) * 1000
        stages.append({
            "name": "bm25_recall",
            "duration_ms": round(bm25_ms, 2),
            "candidates": len(bm25_hits),
            "k": recall_k,
            "q": _short(q_label or query, 60),
        })

        # RRF 融合
        if not bm25_hits:
            return vec_hits
        t_f = time.perf_counter()
        fused = reciprocal_rank_fusion([vec_hits, bm25_hits], k_rrf=60, top_k=recall_k)
        stages.append({
            "name": "rrf_fusion",
            "duration_ms": round((time.perf_counter() - t_f) * 1000, 2),
            "candidates": len(fused),
            "k_rrf": 60,
        })
        return fused

    def _rerank_stage(
        self,
        query: str,
        candidates: List[Tuple[Document, float]],
        top_k: int,
        cfg: Dict[str, Any],
        stages: List[Dict[str, Any]],
    ) -> List[Tuple[Document, float]]:
        if not candidates:
            return []
        if not cfg.get("use_reranker"):
            return candidates[:top_k]

        t = time.perf_counter()
        reranker = get_reranker()
        reranked = reranker.rerank(query, candidates, top_n=top_k)
        stages.append({
            "name": "rerank",
            "model": reranker.model_name,
            "duration_ms": round((time.perf_counter() - t) * 1000, 2),
            "in_candidates": len(candidates),
            "out_candidates": len(reranked),
        })
        return reranked

    def _finalize(
        self,
        query: str,
        role_filter: Optional[str],
        k: int,
        source: str,
        game_id: Optional[str],
        player_id: Optional[int],
        cfg: Dict[str, Any],
        stages: List[Dict[str, Any]],
        hits: List[Tuple[Document, float]],
        t_total: float,
    ) -> Dict[str, Any]:
        duration_ms = (time.perf_counter() - t_total) * 1000

        formatted_hits = []
        log_items = []
        for i, (doc, score) in enumerate(hits, start=1):
            snippet = (doc.page_content or "").strip().replace("\n", " ")
            snippet_short = snippet[:200] + "..." if len(snippet) > 200 else snippet
            entry = {
                "rank": i,
                "doc": doc,
                "score": round(float(score), 4),
                "source": Path(doc.metadata.get("source", "unknown")).name,
                "role": doc.metadata.get("role"),
                "doc_type": doc.metadata.get("doc_type"),
                "game_phase": doc.metadata.get("game_phase"),
                "content": doc.page_content,
            }
            formatted_hits.append(entry)
            log_items.append({
                "source": entry["source"],
                "role": entry["role"],
                "doc_type": entry["doc_type"],
                "game_phase": entry["game_phase"],
                "score": entry["score"],
                "snippet": snippet_short,
            })

        try:
            rag_log_service.log_retrieval(
                query=query,
                role_filter=role_filter,
                top_k=k,
                results=log_items,
                duration_ms=duration_ms,
                source=source,
                game_id=game_id,
                player_id=player_id,
                extra={"pipeline": cfg, "stages": stages},
            )
        except Exception as e:
            logger.warning(f"Failed to write RAG log: {e}")

        return {
            "query": query,
            "role_filter": role_filter,
            "top_k": k,
            "hits": formatted_hits,
            "stages": stages,
            "pipeline": cfg,
            "duration_ms": round(duration_ms, 2),
        }
    
    def format_docs(self, docs: List[Document]) -> str:
        """格式化文档为文本"""
        return "\n\n".join(
            f"[来源: {doc.metadata.get('source', 'unknown')}]\n{doc.page_content}" 
            for doc in docs
        )
    
    def is_available(self) -> bool:
        """检查 RAG 服务是否可用"""
        try:
            return self.vectorstore is not None and self.embeddings is not None
        except Exception:
            return False
    
    def get_stats(self) -> Dict:
        """获取知识库统计信息"""
        if not self.vectorstore:
            return {"status": "not_initialized", "total_chunks": 0, "sources": []}
        
        try:
            collection = self.vectorstore._collection
            count = collection.count()
            
            # 获取所有来源文件
            sources = set()
            if count > 0:
                result = collection.get(include=["metadatas"])
                for meta in result.get("metadatas", []):
                    if meta and "source" in meta:
                        sources.add(Path(meta["source"]).name)
            
            return {
                "status": "ready" if count > 0 else "empty",
                "total_chunks": count,
                "document_count": len(sources),
                "sources": sorted(list(sources)),
                "persist_dir": self.persist_dir,
                "embedding_model": os.getenv("LOCAL_EMBEDDING_MODEL", "BAAI/bge-small-zh-v1.5"),
                "initialized": self._initialized,
            }
        except Exception as e:
            logger.error(f"Failed to get stats: {e}")
            return {"status": "error", "error": str(e)}
    
    def rebuild(self, docs_dir: Optional[str] = None) -> Dict:
        """
        重建知识库（清空后重新加载）
        
        Args:
            docs_dir: 文档目录，默认使用 self.knowledge_dir
            
        Returns:
            Dict: 构建结果统计
        """
        docs_dir = docs_dir or self.knowledge_dir
        
        # 清空已有数据
        if self.vectorstore:
            try:
                collection = self.vectorstore._collection
                if collection.count() > 0:
                    ids = collection.get()["ids"]
                    collection.delete(ids=ids)
                    logger.info(f"Cleared {len(ids)} existing chunks")
            except Exception as e:
                logger.warning(f"Failed to clear vectorstore: {e}")
        
        # 重新加载
        doc_count = self.load_documents(docs_dir)
        self._initialized = True

        # 让 BM25 语料失效（下次检索时自动重建）
        self.invalidate_bm25()

        stats = self.get_stats()
        stats["action"] = "rebuild"
        stats["documents_loaded"] = doc_count

        return stats


# 全局单例
_rag_service: Optional[RAGService] = None


def get_rag_service() -> RAGService:
    """获取 RAG 服务单例"""
    global _rag_service
    if _rag_service is None:
        _rag_service = RAGService()
    return _rag_service
