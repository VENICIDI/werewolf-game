"""
LLM 服务 - 基于 LangChain 的大语言模型封装

支持:
- OpenAI (生产环境)
- Ollama (本地开发)
"""
import os
from typing import Optional
import logging

from langchain_openai import ChatOpenAI
from langchain_core.language_models.chat_models import BaseChatModel

logger = logging.getLogger(__name__)


class LLMService:
    """
    LLM 服务，统一封装 LangChain 的 LLM 接口
    
    支持两种模式:
    1. OpenAI (默认): 使用 ChatOpenAI
    2. Ollama (本地): 使用 ChatOllama
    """
    
    def __init__(self):
        self.use_local = os.getenv("USE_LOCAL_LLM", "false").lower() == "true"
        self.llm: Optional[BaseChatModel] = None
        self._init_llm()
    
    def _init_llm(self):
        """初始化 LLM"""
        if self.use_local:
            self._init_ollama()
        else:
            self._init_openai()
        
        logger.info(f"LLM Service initialized: {'Ollama (local)' if self.use_local else 'OpenAI'}")
    
    def _init_openai(self):
        """初始化 OpenAI"""
        api_key = os.getenv("OPENAI_API_KEY")
        if not api_key or api_key.startswith("sk-your-"):
            raise ValueError("OPENAI_API_KEY not configured in .env")
        
        model = os.getenv("OPENAI_MODEL", "gpt-4o-mini")
        temperature = float(os.getenv("OPENAI_TEMPERATURE", "0.7"))
        
        self.llm = ChatOpenAI(
            model=model,
            temperature=temperature,
            api_key=api_key,
            max_retries=3,
        )
    
    def _init_ollama(self):
        """初始化 Ollama (本地模型)"""
        try:
            from langchain_community.chat_models import ChatOllama
        except ImportError:
            raise ImportError("Please install langchain-ollama: pip install langchain-ollama")
        
        base_url = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")
        model = os.getenv("OLLAMA_MODEL", "qwen2.5:7b")
        
        self.llm = ChatOllama(
            base_url=base_url,
            model=model,
            temperature=0.7,
        )
    
    def get_llm(self) -> BaseChatModel:
        """
        获取 LangChain LLM 实例
        
        Returns:
            BaseChatModel: LangChain LLM 实例 (ChatOpenAI 或 ChatOllama)
        """
        if self.llm is None:
            raise RuntimeError("LLM not initialized")
        return self.llm
    
    async def test_connection(self) -> bool:
        """
        测试 LLM 连接
        
        Returns:
            bool: 连接是否正常
        """
        try:
            response = await self.llm.ainvoke("Hello")
            return bool(response.content)
        except Exception as e:
            logger.error(f"LLM connection test failed: {e}")
            return False


# 全局单例
_llm_service: Optional[LLMService] = None


def get_llm_service() -> LLMService:
    """获取 LLM 服务单例"""
    global _llm_service
    if _llm_service is None:
        _llm_service = LLMService()
    return _llm_service
