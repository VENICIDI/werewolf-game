"""
Agent 生命周期管理器
单例模式，管理所有 Agent 实例的创建/销毁/查询
"""
import asyncio
from typing import Dict, Optional, Tuple, List
from agents.base_agent import WerewolfAgent
from models.game_models import Role
from models.agent_models import Persona
from services.llm_service import LLMService
from services.rag_service import RAGService


class AgentManager:
    """
    Agent 管理器（单例模式）
    
    核心职责:
    - 管理 Agent 实例池 (game_id, player_id) -> WerewolfAgent
    - 创建 Agent（根据角色和人格）
    - 销毁 Agent
    - 查询 Agent（get/list）
    - 并发安全（asyncio.Lock）
    """
    
    _instance: Optional["AgentManager"] = None
    _lock = asyncio.Lock()
    
    def __new__(cls):
        """单例模式：确保全局唯一实例"""
        if cls._instance is None:
            cls._instance = super().__new__(cls)
            cls._instance._initialized = False
        return cls._instance
    
    def __init__(self):
        """初始化（仅执行一次）"""
        if self._initialized:
            return
        
        # Agent 池: (game_id, player_id) -> WerewolfAgent
        self._agents: Dict[Tuple[str, int], WerewolfAgent] = {}
        
        # 并发锁
        self._lock = asyncio.Lock()
        
        # 依赖服务（懒加载）
        self._llm_service: Optional[LLMService] = None
        self._rag_service: Optional[RAGService] = None
        
        self._initialized = True
        print("✅ AgentManager 初始化完成（单例模式）")
    
    def set_services(self, llm_service: LLMService, rag_service: RAGService):
        """
        设置依赖服务（在 FastAPI startup 时调用）
        
        Args:
            llm_service: LLM 服务
            rag_service: RAG 服务
        """
        self._llm_service = llm_service
        self._rag_service = rag_service
        print("✅ AgentManager 服务依赖注入完成")
    
    async def create_agent(
        self,
        game_id: str,
        player_id: int,
        role: Role,
        persona: Persona
    ) -> WerewolfAgent:
        """
        创建 Agent 实例
        
        Args:
            game_id: 游戏 ID
            player_id: 玩家 ID
            role: 角色
            persona: 人格档案
            
        Returns:
            创建的 Agent 实例
            
        Raises:
            ValueError: Agent 已存在或服务未初始化
        """
        async with self._lock:
            key = (game_id, player_id)
            
            # 检查是否已存在
            if key in self._agents:
                raise ValueError(
                    f"Agent 已存在: game_id={game_id}, player_id={player_id}"
                )
            
            # 检查服务是否已注入
            if not self._llm_service or not self._rag_service:
                raise ValueError(
                    "LLM/RAG 服务未初始化，请先调用 set_services()"
                )
            
            # 创建 Agent
            agent = WerewolfAgent(
                game_id=game_id,
                player_id=player_id,
                role=role,
                persona=persona,
                llm_service=self._llm_service,
                rag_service=self._rag_service
            )
            
            # 存入池中
            self._agents[key] = agent
            
            print(f"✅ 创建 Agent: {key}, 当前总数: {len(self._agents)}")
            return agent
    
    async def destroy_agent(self, game_id: str, player_id: int) -> bool:
        """
        销毁 Agent 实例
        
        Args:
            game_id: 游戏 ID
            player_id: 玩家 ID
            
        Returns:
            是否成功销毁
        """
        async with self._lock:
            key = (game_id, player_id)
            
            if key not in self._agents:
                print(f"⚠️  Agent 不存在: {key}")
                return False
            
            # 移除
            del self._agents[key]
            print(f"✅ 销毁 Agent: {key}, 剩余总数: {len(self._agents)}")
            return True
    
    async def get_agent(
        self, 
        game_id: str, 
        player_id: int
    ) -> Optional[WerewolfAgent]:
        """
        获取 Agent 实例
        
        Args:
            game_id: 游戏 ID
            player_id: 玩家 ID
            
        Returns:
            Agent 实例，不存在返回 None
        """
        async with self._lock:
            key = (game_id, player_id)
            return self._agents.get(key)
    
    async def list_agents(self, game_id: Optional[str] = None) -> List[dict]:
        """
        列出 Agent 实例
        
        Args:
            game_id: 游戏 ID（可选，传入则只返回该游戏的 Agent）
            
        Returns:
            Agent 信息列表
        """
        async with self._lock:
            if game_id:
                # 仅返回指定游戏的 Agent
                return [
                    agent.get_info()
                    for (gid, _), agent in self._agents.items()
                    if gid == game_id
                ]
            else:
                # 返回所有 Agent
                return [agent.get_info() for agent in self._agents.values()]
    
    async def clear_game(self, game_id: str) -> int:
        """
        清除指定游戏的所有 Agent
        
        Args:
            game_id: 游戏 ID
            
        Returns:
            清除的 Agent 数量
        """
        async with self._lock:
            keys_to_remove = [
                key for key in self._agents.keys() 
                if key[0] == game_id
            ]
            
            for key in keys_to_remove:
                del self._agents[key]
            
            count = len(keys_to_remove)
            print(f"✅ 清除游戏 {game_id} 的所有 Agent: {count} 个")
            return count
    
    def get_stats(self) -> dict:
        """
        获取统计信息
        
        Returns:
            统计信息字典
        """
        return {
            "total_agents": len(self._agents),
            "games": len(set(key[0] for key in self._agents.keys()))
        }


# 全局单例实例
agent_manager = AgentManager()
