"""
RAG 服务 - 基于 LangChain 的知识检索增强

使用组件:
- Chroma: 向量数据库
- HuggingFaceEmbeddings: 本地文本嵌入 (BAAI/bge-small-zh-v1.5)
- RecursiveCharacterTextSplitter: 文档切片
- DirectoryLoader: 文档加载

Embedding 策略:
- 默认使用本地 HuggingFace 模型 (免费，中文效果好)
- 可通过环境变量切换为 OpenAI Embedding API
"""
import os
import logging
from typing import List, Optional, Dict
from pathlib import Path

from langchain_community.vectorstores import Chroma
from langchain.text_splitter import RecursiveCharacterTextSplitter
from langchain_community.document_loaders import DirectoryLoader, TextLoader
from langchain_core.documents import Document
from langchain_core.retrievers import BaseRetriever

logger = logging.getLogger(__name__)


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
        
        根据文件名和内容，为文档添加角色、类型等元数据
        """
        source = doc.metadata.get("source", "")
        filename = Path(source).name.lower()
        
        # 根据文件名推断角色
        if "werewolf" in filename or "狼人" in filename:
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
        elif "strateg" in filename or "策略" in filename:
            doc.metadata["doc_type"] = "strategy"
        elif "speech" in filename or "发言" in filename:
            doc.metadata["doc_type"] = "speech"
    
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
        k: int = 3
    ) -> List[Document]:
        """
        查询知识库
        
        Args:
            query_text: 查询文本
            role_filter: 按角色过滤
            k: 返回结果数量
            
        Returns:
            List[Document]: 检索结果
        """
        retriever = self.get_retriever(role_filter=role_filter, k=k)
        return retriever.get_relevant_documents(query_text)
    
    async def aquery(
        self,
        query_text: str,
        role_filter: Optional[str] = None,
        k: int = 3
    ) -> List[Document]:
        """异步查询"""
        retriever = self.get_retriever(role_filter=role_filter, k=k)
        return await retriever.aget_relevant_documents(query_text)
    
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
