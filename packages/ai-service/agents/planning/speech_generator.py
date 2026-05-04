"""
LLM 发言生成器 (SpeechGenerator)

基于 LangChain LCEL 的发言生成管道:
1. 收集记忆上下文
2. 获取 RAG 知识
3. 构建 Prompt
4. 调用 LLM 生成
5. 解析输出
"""
import time
import logging
from typing import Optional, Dict, Any, TYPE_CHECKING

from langchain_core.output_parsers import StrOutputParser

from prompts.speech_prompts import SPEECH_PROMPT, DEFENSE_PROMPT
from services.log_service import log_llm_call, get_game_logger

if TYPE_CHECKING:
    from agents.base_agent import WerewolfAgent
    from models.game_models import GameState

logger = logging.getLogger(__name__)


class SpeechGenerator:
    """LLM 发言生成器"""
    
    def __init__(self, llm, rag_service=None):
        """
        Args:
            llm: LangChain BaseChatModel 实例
            rag_service: RAG 服务（可选）
        """
        self.llm = llm
        self.rag_service = rag_service
        self.parser = StrOutputParser()
    
    async def generate(
        self,
        agent: "WerewolfAgent",
        game_state: "GameState",
        context: str = "discussion"
    ) -> str:
        """
        使用 LLM 生成发言
        
        Args:
            agent: Agent 实例
            game_state: 游戏状态
            context: 发言场景
            
        Returns:
            str: 生成的发言文本
        """
        try:
            # 1. 收集所有上下文
            prompt_vars = self._build_prompt_variables(agent, game_state, context)
            
            # 2. 选择 Prompt 模板
            prompt = DEFENSE_PROMPT if context == "defense" else SPEECH_PROMPT
            
            # 3. 构建 Chain 并调用 LLM（记录耗时）
            chain = prompt | self.llm | self.parser
            
            # 格式化 prompt 用于日志记录
            formatted_prompt = prompt.format(**prompt_vars)
            
            start_time = time.time()
            result = await chain.ainvoke(prompt_vars)
            duration_ms = (time.time() - start_time) * 1000
            
            # 4. 记录 LLM 调用日志
            log_llm_call(
                game_id=agent.game_id,
                player_id=agent.player_id,
                action=f"speak/{context}",
                prompt=formatted_prompt,
                response=result,
                duration_ms=duration_ms
            )
            
            # 5. 后处理
            result = self._post_process(result)
            
            logger.info(f"[SpeechGen] Player {agent.player_id} generated ({duration_ms:.0f}ms): {result[:50]}...")
            return result
            
        except Exception as e:
            logger.error(f"[SpeechGen] LLM generation failed: {e}")
            # 降级到模板发言
            return None
    
    def _build_prompt_variables(
        self,
        agent: "WerewolfAgent",
        game_state: "GameState",
        context: str,
    ) -> Dict[str, Any]:
        """构建 Prompt 变量"""
        # 记忆上下文
        memory_ctx = agent.memory.get_full_context()
        
        # 角色 Prompt
        role_prompt = agent.strategy.get_system_prompt(agent)
        
        # 人格描述
        persona_desc = agent.persona_profile.get_prompt_description()
        
        # 游戏上下文
        alive_str = ", ".join(f"{p}号" for p in game_state.alive_players)
        dead_str = ", ".join(f"{p}号" for p in game_state.dead_players) if game_state.dead_players else "无"
        game_context = f"存活玩家: {alive_str}\n死亡玩家: {dead_str}"
        
        # 推理上下文
        reasoning_ctx = memory_ctx.get("suspicion_ranking", "暂无推理数据")
        
        # 发言引导
        guidance = agent.strategy.get_speech_guidance(agent, game_state)
        
        # RAG 知识（如果可用）
        rag_context = ""
        if self.rag_service and self.rag_service.is_available():
            try:
                role_name = agent.strategy.role_name
                query = f"{role_name}在白天讨论时的发言策略"
                docs = self.rag_service.query(query, role_filter=agent.role.value, k=2)
                if docs:
                    rag_context = "\n参考策略:\n" + self.rag_service.format_docs(docs)
            except Exception as e:
                logger.warning(f"RAG query failed: {e}")
        
        # 角色发言示例（few-shot）
        speech_example = agent.strategy.get_speech_example() if hasattr(agent.strategy, 'get_speech_example') else ""
        if speech_example:
            rag_context += f"\n\n{speech_example}"
        
        return {
            "seat_number": agent.seat_number or agent.player_id,
            "role_prompt": role_prompt,
            "persona_description": persona_desc,
            "round": game_state.round,
            "phase": game_state.phase.value,
            "game_context": game_context,
            "memory_context": memory_ctx.get("timeline", ""),
            "reasoning_context": reasoning_ctx + rag_context,
            "speech_guidance": guidance,
            "speech_context": context,
            "alive_players": alive_str,
            "deaths": memory_ctx.get("deaths", "无"),
        }
    
    def _post_process(self, text: str) -> str:
        """后处理 LLM 输出"""
        if not text:
            return ""
        
        # 去除可能的引号包裹
        text = text.strip().strip('"').strip("'")
        
        # 去除角色前缀（LLM 可能加上"我："之类的）
        prefixes_to_remove = ["我：", "我:", "发言：", "发言:", "回答：", "回答:"]
        for prefix in prefixes_to_remove:
            if text.startswith(prefix):
                text = text[len(prefix):].strip()
        
        # 截断过长的发言
        if len(text) > 300:
            # 找到最后一个句号截断
            last_period = text[:300].rfind("。")
            if last_period > 100:
                text = text[:last_period + 1]
            else:
                text = text[:300]
        
        return text
