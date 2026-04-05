---
name: ai-agent-phase1-implementation
overview: 完成 Phase 1：实现 Agent 基类、AgentManager、API 路由，基于 LangChain 构建多 Agent 基础框架
todos:
  - id: explore-java-backend
    content: 使用 [subagent:code-explorer] 探索 Java 后端 PhaseScheduler、Player 实体、GameService.executeAction 方法
    status: completed
  - id: create-agent-dirs
    content: 创建 agents 目录结构（agents/, agents/memory/, agents/reasoning/, agents/strategies/, agents/planning/, agents/persona/）
    status: completed
  - id: implement-base-agent
    content: 实现 Agent 基类 agents/base_agent.py（WerewolfAgent，包含基础框架和占位决策逻辑）
    status: completed
    dependencies:
      - create-agent-dirs
  - id: implement-agent-manager
    content: 实现 AgentManager agents/agent_manager.py（Agent 生命周期管理、单例模式、并发安全）
    status: completed
    dependencies:
      - implement-base-agent
  - id: implement-agent-router
    content: 实现 Agent API 路由 routers/agent_router.py（创建/销毁/事件/决策接口）
    status: completed
    dependencies:
      - implement-agent-manager
  - id: update-health-router
    content: 更新健康检查路由 routers/health_router.py（增加 LLM/Chroma 状态检测）
    status: completed
  - id: update-main-py
    content: 更新 main.py（注册 agent_router，更新路由配置）
    status: completed
    dependencies:
      - implement-agent-router
      - update-health-router
  - id: implement-ai-player-bridge
    content: 实现 Java 后端 AIPlayerBridge 组件（HTTP 调用 AI Service，集成到 PhaseScheduler）
    status: completed
    dependencies:
      - explore-java-backend
  - id: commit-phase1
    content: "提交 Phase 1 完成代码（git add + commit，提交信息：feat: 完成 Phase 1 AI Agent 基础框架）"
    status: completed
    dependencies:
      - implement-base-agent
      - implement-agent-manager
      - implement-agent-router
      - update-health-router
      - update-main-py
      - implement-ai-player-bridge
---

## 用户需求

继续执行 AI Agent 系统的实施工作，完成 Phase 1 基础框架的剩余部分，并在完成阶段性任务后提交代码。

## 当前进度

已完成：

- README.md 更新（反映 LangChain 架构）
- requirements.txt 更新（补全 LangChain 依赖）
- 环境变量模板（.env.example）
- 数据模型层（models/game_models.py, agent_models.py, event_models.py）
- LLM 服务（services/llm_service.py - 基于 LangChain ChatOpenAI/ChatOllama）
- RAG 服务（services/rag_service.py - 基于 LangChain Chroma + Retriever）

## 待完成任务

Phase 1 基础框架剩余部分：

1. 实现 Agent 基类（WerewolfAgent）
2. 实现 AgentManager（Agent 生命周期管理）
3. 实现 Agent API 路由（创建/销毁/事件通知/决策接口）
4. 更新健康检查路由（增加 LLM/Chroma 状态检测）
5. 更新 main.py（注册新路由）
6. Java 后端集成（AIPlayerBridge 组件）
7. 提交阶段性代码

## 功能要点

- **WerewolfAgent**：每个 AI 玩家的核心类，包含角色、人格、记忆系统（初始化占位）、推理引擎（占位）
- **AgentManager**：管理所有 Agent 实例的生命周期，支持创建/销毁/查询
- **Agent API**：提供完整的 REST API 接口供 Java 后端调用
- **AIPlayerBridge**：Java 后端组件，在游戏阶段自动为 AI 玩家调用 Python AI Service

## 技术栈

- **Python**: 3.9+
- **Web 框架**: FastAPI
- **AI 编排**: LangChain
- **LLM**: ChatOpenAI / ChatOllama
- **向量数据库**: ChromaDB
- **Java 后端**: Spring Boot
- **HTTP 客户端**: Java RestTemplate / WebClient

## 实现方案

### 1. Agent 基类设计（WerewolfAgent）

**核心职责**：

- 存储 Agent 基本信息（game_id, player_id, role, persona）
- 初始化记忆系统（当前阶段简化，仅占位）
- 初始化推理引擎（当前阶段简化，仅占位）
- 提供事件处理接口（receive_event）
- 提供决策接口（decide_night_action, generate_speech, decide_vote）

**设计模式**：

- 采用职责分离，将记忆、推理、策略等模块化
- 当前 Phase 1 仅实现基础框架，复杂逻辑（记忆系统、推理引擎、角色策略）留待 Phase 2-4

**关键方法**：

```python
class WerewolfAgent:
    def __init__(self, game_id, player_id, role, persona, llm_service, rag_service)
    async def receive_event(self, event: GameEvent)  # 接收游戏事件
    async def decide_night_action(self, game_state: GameState) -> NightActionDecision
    async def generate_speech(self, game_state: GameState, context: str) -> SpeechDecision
    async def decide_vote(self, game_state: GameState) -> VoteDecision
```

### 2. AgentManager 设计

**核心职责**：

- 管理 Agent 实例池（Dict[Tuple[str, int], WerewolfAgent]）
- 创建 Agent（create_agent）
- 销毁 Agent（destroy_agent）
- 查询 Agent（get_agent, list_agents）
- 单例模式（全局唯一实例）

**数据结构**：

```python
_agents: Dict[Tuple[str, int], WerewolfAgent]  # (game_id, player_id) -> Agent
```

**并发安全**：

- 使用 asyncio.Lock 保护 Agent 池的并发访问

### 3. Agent API 路由

**接口设计**：

| 接口 | 方法 | 路径 | 功能 |
| --- | --- | --- | --- |
| 创建 Agent | POST | /api/agents/create | 创建新 Agent 实例 |
| 销毁 Agent | POST | /api/agents/destroy | 销毁指定 Agent |
| 事件通知 | POST | /api/agents/event | 推送游戏事件给 Agent |
| 夜间行动 | POST | /api/agents/action/night | 获取夜间行动决策 |
| 发言生成 | POST | /api/agents/action/speak | 生成白天发言 |
| 投票决策 | POST | /api/agents/action/vote | 获取投票决策 |


**错误处理**：

- Agent 不存在：404
- 参数错误：400
- 内部错误：500

### 4. 健康检查增强

**检测项**：

- FastAPI 服务状态
- LLM 连接状态（调用 llm_service.test_connection()）
- ChromaDB 状态（检查 vectorstore 是否可用）
- 活跃 Agent 数量

**响应格式**：

```
{
  "status": "healthy",
  "llm_available": true,
  "chroma_available": true,
  "active_agents": 6
}
```

### 5. Java 后端集成（AIPlayerBridge）

**位置**：`packages/backend/src/main/java/com/werewolf/service/AIPlayerBridge.java`

**核心职责**：

- 在 PhaseScheduler 各阶段为 AI 玩家调用 Python AI Service
- 将 AI 决策结果转换为游戏引擎可执行的 Action
- 通过 GameService.executeAction() 执行

**集成点**：

- PhaseScheduler 的 `executePhaseStart()` / `executePhaseEnd()` 中调用
- 判断当前玩家是否为 AI（通过 Player.isAI 字段）
- 异步调用 AI Service，设置超时（10-30秒）

**HTTP 调用**：

- 使用 RestTemplate 或 WebClient
- 配置 AI Service 地址（application.yml）
- 错误处理：超时、连接失败时记录日志并跳过

## 实现细节

### Phase 1 简化策略

为了快速完成基础框架，当前阶段采用以下简化：

1. **记忆系统**：仅占位，存储基本事件列表，不实现三层记忆
2. **推理引擎**：仅占位，返回简单的随机/规则决策
3. **角色策略**：仅占位，所有角色使用相同的简单逻辑
4. **LLM 发言**：当前阶段返回模板化发言，Phase 4 再接入 LLM

### 决策逻辑（临时）

**夜间行动**：随机选择存活玩家（排除自己）
**白天发言**：返回模板化文本
**投票**：随机选择存活玩家（排除自己）

### 性能考虑

- Agent 实例复用，避免重复创建
- 事件处理异步化，避免阻塞
- LLM 调用设置超时（10秒）

### 错误处理

- Agent 不存在时返回 404
- LLM 调用失败时降级为规则决策
- Java 后端调用超时时记录日志并继续游戏

## 代码探索

使用 **code-explorer** subagent 探索 Java 后端的以下内容：

- **目的**：定位 PhaseScheduler 的集成点、Player 实体结构、GameService.executeAction() 方法签名
- **预期结果**：获取准确的 Java 类路径、方法签名、集成点位置，确保 AIPlayerBridge 设计与现有架构兼容