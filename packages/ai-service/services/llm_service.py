"""LLM Service - Interface to language models"""

import os
import logging
from typing import AsyncGenerator, Optional

import httpx

logger = logging.getLogger(__name__)


class LLMService:
    """LLM service for generating AI responses"""
    
    def __init__(self):
        self.api_key = os.getenv("OPENAI_API_KEY")
        self.base_url = os.getenv("OPENAI_BASE_URL", "https://api.openai.com/v1")
        self.use_local = os.getenv("USE_LOCAL_MODEL", "false").lower() == "true"
        self.local_url = os.getenv("LOCAL_MODEL_URL", "http://localhost:11434")
        self.client = httpx.AsyncClient(timeout=60.0)
        
    async def generate(
        self,
        prompt: str,
        system_prompt: Optional[str] = None,
        temperature: float = 0.7,
        max_tokens: int = 500
    ) -> str:
        """Generate text using LLM"""
        if self.use_local:
            return await self._generate_local(prompt, system_prompt, temperature, max_tokens)
        else:
            return await self._generate_openai(prompt, system_prompt, temperature, max_tokens)
            
    async def _generate_openai(
        self,
        prompt: str,
        system_prompt: Optional[str],
        temperature: float,
        max_tokens: int
    ) -> str:
        """Generate using OpenAI API"""
        if not self.api_key:
            logger.warning("OpenAI API key not set, returning mock response")
            return "[AI服务未配置，这是模拟回复]"
            
        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json"
        }
        
        messages = []
        if system_prompt:
            messages.append({"role": "system", "content": system_prompt})
        messages.append({"role": "user", "content": prompt})
        
        data = {
            "model": "gpt-3.5-turbo",
            "messages": messages,
            "temperature": temperature,
            "max_tokens": max_tokens
        }
        
        try:
            response = await self.client.post(
                f"{self.base_url}/chat/completions",
                headers=headers,
                json=data
            )
            response.raise_for_status()
            result = response.json()
            return result["choices"][0]["message"]["content"]
        except Exception as e:
            logger.error(f"OpenAI API error: {e}")
            return f"[AI生成错误: {str(e)}]"
            
    async def _generate_local(
        self,
        prompt: str,
        system_prompt: Optional[str],
        temperature: float,
        max_tokens: int
    ) -> str:
        """Generate using local model (Ollama)"""
        data = {
            "model": "llama2",
            "prompt": prompt,
            "system": system_prompt or "",
            "stream": False,
            "options": {
                "temperature": temperature,
                "num_predict": max_tokens
            }
        }
        
        try:
            response = await self.client.post(
                f"{self.local_url}/api/generate",
                json=data
            )
            response.raise_for_status()
            result = response.json()
            return result["response"]
        except Exception as e:
            logger.error(f"Local model error: {e}")
            return f"[本地模型错误: {str(e)}]"
            
    async def generate_stream(
        self,
        prompt: str,
        system_prompt: Optional[str] = None,
        temperature: float = 0.7
    ) -> AsyncGenerator[str, None]:
        """Generate text with streaming"""
        # TODO: Implement streaming
        yield await self.generate(prompt, system_prompt, temperature)
        
    async def close(self):
        """Close HTTP client"""
        await self.client.aclose()