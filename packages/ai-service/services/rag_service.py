"""
RAG 服务 - 基于 LangChain 的知识检索增强

使用组件:
- Chroma: 向量数据库
- OpenAIEmbeddings: 文本嵌入
- RecursiveCharacterTextSplitter: 文档切片
- DirectoryLoader: 文档加载
"""
import os
import logging
from typing import List, Optional, Dict
from pathlib import Path

from langchain_community.vectorstores import Chroma
from langchain_openai import OpenAIEmbeddings
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
    """
    
    def __init__(self):
        self.persist_dir = os.getenv("CHROMA_PERSIST_DIR", "./chroma_db")
        self.collection_name = f"{os.getenv('CHROMA_COLLECTION_PREFIX', 'werewolf')}_knowledge"
        
        # 初始化 Embedding
        self.embeddings = self._init_embeddings()
        
        # 初始化或加载向量数据库
        self.vectorstore: Optional[Chroma] = None
        self._load_or_create_vectorstore()
    
    def _init_embeddings(self):
        """初始化 Embedding 模型"""
        embedding_model = os.getenv("EMBEDDING_MODEL", "text-embedding-3-small")
        api_key = os.getenv("OPENAI_API_KEY")
        
        if not api_key or api_key.startswith("sk-your-"):
            logger.warning("OPENAI_API_KEY not configured, using default embeddings")
            # 本地开发可用 HuggingFaceEmbeddings
            return None
        
        return OpenAIEmbeddings(
            model=embedding_model,
            api_key=api_key
        )
    
    def _load_or_create_vectorstore(self):
        """加载或创建向量数据库"""
        persist_path = Path(self.persist_dir)
        
        if persist_path.exists() and any(persist_path.iterdir()):
            # 加载已有的向量库
            logger.info(f"Loading existing vectorstore from {self.persist_dir}")
            self.vectorstore = Chroma(
                collection_name=self.collection_name,
                embedding_function=self.embeddings,
                persist_directory=self.persist_dir
            )
        else:
            # 创建新的空向量库
            logger.info(f"Creating new vectorstore at {self.persist_dir}")
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
        """
        格式化文档为文本
        
        Args:
            docs: 文档列表
            
        Returns:
            str: 格式化后的文本
        """
        return "\n\n".join(f"[来源: {doc.metadata.get('source', 'unknown')}]\n{doc.page_content}" for doc in docs)


# 全局单例
_rag_service: Optional[RAGService] = None


def get_rag_service() -> RAGService:
    """获取 RAG 服务单例"""
    global _rag_service
    if _rag_service is None:
        _rag_service = RAGService()
    return _rag_service
