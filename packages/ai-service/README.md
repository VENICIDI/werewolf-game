# Werewolf AI Service

基于 **LangChain** 框架的 AI 狼人杀多 Agent 系统

## 🎯 项目简介

本项目实现了一个完整的 AI 狼人杀 Agent 系统，每个 AI 玩家是一个独立的智能体，具备：

- **感知-思考-行动循环 (PTA)**: 接收游戏事件 → 推理分析 → 输出决策
- **三层记忆系统**: 工作记忆 + 情景记忆 + 语义记忆
- **贝叶斯推理引擎**: 基于证据持续更新嫌疑值
- **RAG 知识增强**: 从知识库检索相关策略辅助决策
- **人格多样性**: 4 种人格档案，AI 行为各具特色

## ✨ 核心特性

- 🤖 **多 Agent 架构** - 每个 AI 玩家独立运行，互不干扰
- 🧠 **LangChain 驱动** - 全链路 LLM/RAG/Memory/Chain 编排
- 🎭 **6 种角色策略** - 狼人/预言家/女巫/猎人/守卫/村民差异化行为
- 💭 **结构化推理** - 贝叶斯推理 + LLM 混合决策
- 💬 **自然语言发言** - LLM 生成流畅、符合角色的游戏发言
- 🔌 **无缝集成** - 通过 REST API 与 Java 游戏引擎对接

## 🛠️ 技术栈

| 组件 | 技术 | 用途 |
|------|------|------|
| **Web 框架** | FastAPI | API 服务器 |
| **AI 编排** | **LangChain** | **核心框架（LLM/RAG/Memory/Chain 编排）** |
| **LLM** | ChatOpenAI / ChatOllama | 大语言模型接口 |
| **Embedding** | OpenAIEmbeddings | 文本向量化 |
| **向量数据库** | ChromaDB | RAG 知识检索 |
| **Prompt 管理** | ChatPromptTemplate | 模板化 Prompt |
| **记忆管理** | ConversationSummaryBufferMemory | 对话历史摘要 |
| **输出解析** | PydanticOutputParser | 结构化输出 |

## 🚀 快速开始

### 前置要求

- Python 3.9+
- OpenAI API Key（或本地 Ollama 环境）

### 1. 安装依赖

```bash
cd packages/ai-service
python -m venv venv
source venv/bin/activate  # Windows: venv\Scripts\activate
pip install -r requirements.txt
```

### 2. 配置环境变量

```bash
cp .env.example .env
```

编辑 `.env` 文件：

```bash
# OpenAI 配置（生产环境）
OPENAI_API_KEY=sk-...
OPENAI_MODEL=gpt-4o-mini
EMBEDDING_MODEL=text-embedding-3-small

# Ollama 配置（本地开发）
# OLLAMA_BASE_URL=http://localhost:11434
# OLLAMA_MODEL=qwen2.5:7b

# AI 服务配置
AI_SERVICE_PORT=8000
LOG_LEVEL=INFO

# ChromaDB 配置
CHROMA_PERSIST_DIR=./chroma_db
```

### 3. 初始化知识库（首次启动）

```bash
python scripts/init_knowledge.py
```

这将加载 `knowledge/` 目录下的 Markdown 文档并向量化到 ChromaDB。

### 4. 启动服务

```bash
python main.py
```

服务将在 http://localhost:8000 启动

### 5. 查看 API 文档

- **Swagger UI**: http://localhost:8000/docs
- **ReDoc**: http://localhost:8000/redoc
- **健康检查**: http://localhost:8000/api/health

## 📡 API 接口

### Agent 管理

#### 创建 Agent

```bash
POST /api/agents/create
{
  "game_id": "game-001",
  "player_id": 1,
  "role": "WEREWOLF",
  "persona": "aggressive"  // aggressive/analytical/cautious/charming
}
```

#### 销毁 Agent

```bash
POST /api/agents/destroy
{
  "game_id": "game-001",
  "player_id": 1
}
```

### 游戏事件通知

#### 推送事件给 Agent

```bash
POST /api/agents/event
{
  "game_id": "game-001",
  "player_id": 1,
  "event_type": "PLAYER_DIED",
  "event_data": {
    "died_player_id": 3,
    "round": 1,
    "phase": "DAY_START"
  }
}
```

### Agent 决策

#### 夜间行动决策

```bash
POST /api/agents/action/night
{
  "game_id": "game-001",
  "player_id": 1,
  "game_state": {
    "round": 1,
    "phase": "WEREWOLF",
    "alive_players": [1, 2, 3, 4, 5, 6],
    "dead_players": [],
    ...
  }
}

Response:
{
  "target_id": 3,
  "reason": "3号玩家发言激进，疑似预言家",
  "confidence": 0.75
}
```

#### 白天发言生成

```bash
POST /api/agents/action/speak
{
  "game_id": "game-001",
  "player_id": 1,
  "game_state": {...},
  "speak_context": "discussion"  // discussion/defense/claim_role
}

Response:
{
  "content": "昨晚3号玩家被刀，我认为...",
  "emotion": "confident",
  "targets_mentioned": [4, 5]
}
```

#### 投票决策

```bash
POST /api/agents/action/vote
{
  "game_id": "game-001",
  "player_id": 1,
  "game_state": {...}
}

Response:
{
  "target_id": 5,
  "reason": "5号玩家发言逻辑矛盾，嫌疑最大"
}
```

### 知识库查询

#### RAG 检索

```bash
POST /api/knowledge/query
{
  "query": "预言家在第一天应该如何发言？",
  "role_filter": "SEER",
  "top_k": 3
}

Response:
{
  "results": [
    {
      "content": "预言家第一天发言策略：...",
      "source": "06-seer-strategies.md",
      "relevance": 0.92
    },
    ...
  ]
}
```

### 健康检查

```bash
GET /api/health

Response:
{
  "status": "healthy",
  "llm_available": true,
  "chroma_available": true,
  "active_agents": 6
}
```

## 📁 项目结构

```
ai-service/
├── main.py                          # FastAPI 应用入口
├── requirements.txt                 # Python 依赖（LangChain 全家桶）
├── .env.example                     # 环境变量模板
│
├── agents/                          # Agent 核心模块
│   ├── base_agent.py                # WerewolfAgent 基类
│   ├── agent_manager.py             # Agent 生命周期管理
│   ├── memory/                      # 记忆系统
│   │   ├── memory_system.py         # 三层记忆架构
│   │   ├── working_memory.py        # 工作记忆
│   │   ├── episodic_memory.py       # 情景记忆（事件序列）
│   │   └── semantic_memory.py       # 语义记忆（玩家画像）
│   ├── reasoning/                   # 推理引擎
│   │   ├── bayesian_reasoner.py     # 贝叶斯嫌疑推理
│   │   ├── evidence_analyzer.py     # 证据分析器
│   │   └── chain_of_thought.py      # 推理链生成
│   ├── strategies/                  # 角色策略（策略模式）
│   │   ├── base_strategy.py         # RoleStrategy 接口
│   │   ├── werewolf_strategy.py     # 狼人策略
│   │   ├── seer_strategy.py         # 预言家策略
│   │   ├── witch_strategy.py        # 女巫策略
│   │   ├── hunter_strategy.py       # 猎人策略
│   │   ├── guard_strategy.py        # 守卫策略
│   │   └── villager_strategy.py     # 村民策略
│   ├── planning/                    # 行动规划（LangChain Chains）
│   │   ├── action_planner.py        # 行动规划器
│   │   └── speech_generator.py      # 发言生成器
│   └── persona/                     # 人格系统
│       └── persona_profiles.py      # 人格档案定义
│
├── services/                        # 基础服务（LangChain 封装）
│   ├── llm_service.py               # LLM: ChatOpenAI / ChatOllama
│   └── rag_service.py               # RAG: Chroma + Retriever + LCEL
│
├── routers/                         # API 路由
│   ├── agent_router.py              # Agent 管理 API
│   ├── knowledge_router.py          # 知识库 API
│   └── health_router.py             # 健康检查 API
│
├── models/                          # 数据模型（Pydantic）
│   ├── game_models.py               # 游戏相关模型
│   ├── agent_models.py              # Agent 相关模型
│   └── event_models.py              # 事件模型
│
├── prompts/                         # Prompt 模板（LangChain）
│   ├── system_prompts.py            # 角色系统提示词
│   ├── action_prompts.py            # 行动决策提示词
│   └── speech_prompts.py            # 发言生成提示词
│
├── knowledge/                       # 知识库文档
│   ├── 01-game-rules.md             # 游戏规则
│   ├── 02-role-skills.md            # 角色技能详解
│   ├── 03-speech-patterns.md        # 发言模式与技巧
│   ├── 04-common-formations.md      # 常见板子分析
│   ├── 05-werewolf-strategies.md    # 狼人策略
│   ├── 06-seer-strategies.md        # 预言家策略
│   ├── 07-witch-strategies.md       # 女巫策略
│   └── 08-voting-analysis.md        # 投票分析技巧
│
├── scripts/                         # 工具脚本
│   └── init_knowledge.py            # 知识库初始化
│
└── tests/                           # 测试
    ├── test_agent.py
    ├── test_reasoning.py
    ├── test_memory.py
    ├── test_chains.py
    └── test_strategies.py
```

## 🏗️ 架构说明

### 整体架构

```
Java 游戏引擎 (PhaseScheduler)
        │
        ├─> AIPlayerBridge ──HTTP─> Python AI Service
        │                              │
        │                              v
        │                    ┌─────────────────┐
        │                    │ AgentManager    │
        │                    │ (Agent 池管理)  │
        │                    └────────┬────────┘
        │                             │
        v                             v
   executeAction()           WerewolfAgent 实例
   (执行AI决策)               ┌──────────────┐
                             │ Memory       │
                             │ Reasoning    │
                             │ Strategy     │
                             │ Planning     │
                             └──────────────┘
                                     │
                       ┌─────────────┼─────────────┐
                       v             v             v
                   LLM Chain    RAG Chain    Persona
```

### LangChain 核心使用点

| 组件 | LangChain 类 | 用途 |
|------|--------------|------|
| **LLM 调用** | `ChatOpenAI` / `ChatOllama` | 统一 LLM 接口 |
| **Prompt 管理** | `ChatPromptTemplate` | 模板化 Prompt |
| **RAG 检索** | `Chroma.as_retriever()` + LCEL | 知识检索链 |
| **文档加载** | `DirectoryLoader` + `RecursiveCharacterTextSplitter` | 知识库向量化 |
| **对话记忆** | `ConversationSummaryBufferMemory` | 历史对话摘要 |
| **决策链** | LCEL 管道 (`prompt \| llm \| parser`) | 行动决策编排 |
| **输出解析** | `PydanticOutputParser` | 结构化输出 |

### Agent 感知-思考-行动循环

```
┌─────────────┐
│  感知阶段    │  接收游戏事件 (死亡/发言/投票)
│  Perceive   │  更新记忆系统
└──────┬──────┘
       │
       v
┌─────────────┐
│  思考阶段    │  推理引擎分析嫌疑值
│  Reason     │  RAG 检索相关策略
│            │  结合人格特质
└──────┬──────┘
       │
       v
┌─────────────┐
│  行动阶段    │  夜间行动决策 (kill/check/save...)
│  Act        │  白天发言生成
│            │  投票决策
└─────────────┘
```

## 📚 知识库

知识库位于 `knowledge/` 目录，包含 8 个 Markdown 文档：

| 文档 | 内容 | 字数 |
|------|------|------|
| `01-game-rules.md` | 游戏基本规则、胜负判定 | ~1500 |
| `02-role-skills.md` | 6 种角色的技能详解 | ~2000 |
| `03-speech-patterns.md` | 发言技巧、说服策略 | ~2500 |
| `04-common-formations.md` | 常见板子配置分析 | ~1200 |
| `05-werewolf-strategies.md` | 狼人阵营策略 | ~2000 |
| `06-seer-strategies.md` | 预言家策略 | ~1800 |
| `07-witch-strategies.md` | 女巫策略 | ~1500 |
| `08-voting-analysis.md` | 投票分析技巧 | ~1800 |

**向量化配置**:
- Chunk Size: 500
- Chunk Overlap: 50
- Embedding Model: `text-embedding-3-small`
- Retriever: MMR (最大边际相关性)

## 🚀 开发计划

- [x] Phase 0: 架构设计与文档
- [ ] **Phase 1: 基础框架（当前阶段）**
  - [ ] Agent 基类 + AgentManager
  - [ ] LangChain LLM 服务封装
  - [ ] 基础 API 路由
  - [ ] Java 后端 AIPlayerBridge
- [ ] Phase 2: 记忆系统 + 角色策略
  - [ ] 三层记忆架构 + LangChain Memory
  - [ ] 6 种角色策略实现
  - [ ] 基础行动决策逻辑
- [ ] Phase 3: RAG 知识库
  - [ ] 编写 8 个知识库文档
  - [ ] LangChain 文档加载与向量化
  - [ ] RAG 检索服务
- [ ] Phase 4: 推理引擎 + 发言生成
  - [ ] 贝叶斯推理引擎
  - [ ] LangChain LCEL 决策链
  - [ ] Prompt 工程
- [ ] Phase 5: 联调与优化
  - [ ] 端到端测试
  - [ ] 性能优化
  - [ ] 人格多样性调优

## 🔗 相关文档

- [架构设计文档](../../docs/ai-agent-design.md)
- [游戏流程文档](../../docs/game-flow.md)

## 📄 License

MIT