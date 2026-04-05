# 狼人杀 AI Agent 系统设计文档

## 目录

- [1. 设计目标](#1-设计目标)
- [2. 整体架构](#2-整体架构)
- [3. 多 Agent 体系](#3-多-agent-体系)
- [4. Agent 核心能力模型](#4-agent-核心能力模型)
- [5. 记忆系统](#5-记忆系统)
- [6. RAG 知识库](#6-rag-知识库)
- [7. 推理引擎](#7-推理引擎)
- [8. Prompt 工程](#8-prompt-工程)
- [9. Java 后端集成](#9-java-后端集成)
- [10. 目录结构](#10-目录结构)
- [11. 数据流与时序](#11-数据流与时序)
- [12. API 接口设计](#12-api-接口设计)
- [13. 配置与部署](#13-配置与部署)
- [14. 实现计划](#14-实现计划)

---

## 1. 设计目标

### 1.1 核心目标

构建一个 **多 Agent 协作的 AI 狼人杀系统**，每个 AI 玩家是一个独立的 Agent 实例，具备：

- **角色感知** — 根据分配角色自动切换行为策略
- **记忆能力** — 维护短期/长期记忆，跨回合追踪信息
- **推理能力** — 基于贝叶斯推理进行嫌疑分析
- **RAG 增强** — 检索狼人杀策略知识辅助决策
- **人格多样性** — 不同 AI 玩家表现出不同风格
- **自然语言** — 生成拟人化、有逻辑的发言

### 1.2 设计原则

| 原则 | 说明 |
|------|------|
| **Agent 自治** | 每个 AI 玩家是独立 Agent，拥有自己的记忆和推理状态 |
| **角色多态** | 同一 Agent 框架，通过角色策略实现不同行为 |
| **可观测** | 推理过程可追踪、可调试、可回放 |
| **渐进增强** | RAG 知识作为增强而非必须，LLM 降级时仍可运行 |
| **解耦集成** | AI 服务与 Java 后端通过 HTTP 松耦合 |

---

## 2. 整体架构

本系统以 **LangChain** 为核心编排框架，串联 LLM 调用、RAG 检索、Prompt 管理、记忆管理和 Chain 编排。

```
┌─────────────────────────────────────────────────────────────────┐
│                     Java Backend (Spring Boot)                  │
│                                                                 │
│  PhaseScheduler ──> AIPlayerBridge ──HTTP──> AI Service (Python) │
│       │                  │                                      │
│       │            为每个AI玩家                                   │
│       │            在对应阶段                                     │
│       │            自动调用AI决策                                  │
│       │                  │                                      │
│  GameService <───────────┘                                      │
│  (executeAction)                                                │
└─────────────────────────────────────────────────────────────────┘
                              │
                          HTTP API
                              │
┌─────────────────────────────────────────────────────────────────┐
│                   Python AI Service (FastAPI)                    │
│                                                                 │
│  ┌────────────────────────────────────────────────────────┐     │
│  │                  AgentManager                          │     │
│  │  管理所有 Agent 实例的生命周期                            │     │
│  │  game_id + player_id → WerewolfAgent 实例              │     │
│  └────────────┬───────────────────────────────────────────┘     │
│               │                                                 │
│               v                                                 │
│  ┌────────────────────────────────────────────────────────┐     │
│  │              WerewolfAgent (每个AI玩家一个)              │     │
│  │                                                        │     │
│  │  ┌──────────┐  ┌──────────┐  ┌───────────┐           │     │
│  │  │ Memory   │  │ Reasoner │  │ Persona   │           │     │
│  │  │ System   │  │ Engine   │  │ Profile   │           │     │
│  │  └────┬─────┘  └────┬─────┘  └─────┬─────┘           │     │
│  │       │              │              │                  │     │
│  │       v              v              v                  │     │
│  │  ┌──────────────────────────────────────────────┐     │     │
│  │  │          RoleStrategy (策略模式)               │     │     │
│  │  │  WerewolfStrategy | SeerStrategy | ...       │     │     │
│  │  └──────────────────────┬───────────────────────┘     │     │
│  │                         │                              │     │
│  │                         v                              │     │
│  │  ┌──────────────────────────────────────────────┐     │     │
│  │  │      ActionPlanner (LangChain Chains)        │     │     │
│  │  │  decide_night_action() / plan_speech()       │     │     │
│  │  │  decide_vote() / should_reveal_identity()    │     │     │
│  │  └──────────────────────┬───────────────────────┘     │     │
│  │                         │                              │     │
│  └─────────────────────────┼──────────────────────────────┘     │
│                            │                                     │
│  ┌─────────────────────────┼─────────────────────────────────┐  │
│  │           LangChain 核心编排层                              │  │
│  │                         │                                  │  │
│  │    ┌────────────────────┼────────────────┐                │  │
│  │    v                    v                v                │  │
│  │  ┌──────────┐   ┌─────────────┐   ┌────────────┐        │  │
│  │  │ChatOpenAI│   │ RAG Chain   │   │PromptTempl │        │  │
│  │  │ChatOllama│   │ (Retriever  │   │(ChatPrompt │        │  │
│  │  │(BaseLLM) │   │  + Chroma)  │   │ Template)  │        │  │
│  │  └──────────┘   └─────────────┘   └────────────┘        │  │
│  │       │               │                  │               │  │
│  │       │    ┌──────────┘                  │               │  │
│  │       v    v                             v               │  │
│  │  ┌──────────────┐              ┌────────────────┐        │  │
│  │  │  LLMChain /  │              │ConversationBuf │        │  │
│  │  │  LCEL Pipe   │              │ferMemory /     │        │  │
│  │  │  (决策链)     │              │  Summary       │        │  │
│  │  └──────────────┘              │  Memory        │        │  │
│  │                                └────────────────┘        │  │
│  │  OutputParser (StructuredOutputParser / PydanticParser)   │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 2.1 LangChain 在架构中的定位

LangChain 不是可选项，而是整个 AI 决策的**编排中枢**：

| LangChain 组件 | 用途 | 对应位置 |
|---------------|------|---------|
| `ChatOpenAI` / `ChatOllama` | 统一 LLM 接口 | `services/llm_service.py` |
| `ChatPromptTemplate` | 分层 Prompt 模板管理 | `prompts/` |
| `Chroma` + `RetrievalQA` | RAG 检索链 | `services/rag_service.py` |
| `ConversationBufferWindowMemory` | Agent 对话记忆 | `agents/memory/` |
| `ConversationSummaryMemory` | 长期记忆摘要 | `agents/memory/` |
| `LLMChain` / LCEL Pipe | 决策链编排 | `agents/planning/` |
| `StructuredOutputParser` | 结构化输出解析 | `agents/planning/` |
| `RecursiveCharacterTextSplitter` | 知识文档切片 | 知识库初始化 |
| `OpenAIEmbeddings` / `HuggingFaceEmbeddings` | 向量化 | RAG 索引 |

```
关键设计: LangChain LCEL (LangChain Expression Language) 链式调用

  决策链 = prompt | llm | output_parser

  夜间行动链:
    rag_context = retriever.invoke(query)
    decision = (
        night_action_prompt          # ChatPromptTemplate
        | ChatOpenAI(model=...)      # LLM
        | NightActionParser()        # StructuredOutputParser
    ).invoke({
        "role": "WEREWOLF",
        "game_state": ...,
        "memory_summary": ...,
        "rag_context": rag_context,
        "suspicion_map": ...
    })
```

---

## 3. 多 Agent 体系

### 3.1 Agent 生命周期

```
创建游戏 (Java startGame)
    │
    ├─── 为每个 isAi=true 的 Player 创建 Agent
    │    POST /api/agent/create
    │    { game_id, player_id, role, persona }
    │
    v
┌──────────────────────────────────────────────────────┐
│              Agent 活跃期 (游戏进行中)                 │
│                                                      │
│  每个阶段:                                            │
│    Java PhaseScheduler                               │
│      ──> 判断该阶段有哪些 AI 需要行动                  │
│      ──> POST /api/agent/{game_id}/{player_id}/act   │
│      ──> Agent 返回决策                               │
│      ──> Java executeAction() 执行                   │
│                                                      │
│  每个阶段结束:                                        │
│    Java 推送阶段事件给所有 Agent                       │
│    POST /api/agent/{game_id}/event                   │
│    Agent 更新记忆                                     │
│                                                      │
└──────────────────────────────────────────────────────┘
    │
    v
游戏结束 (Java endGame)
    │
    ├─── 销毁所有 Agent 实例
    │    DELETE /api/agent/{game_id}
    v
```

### 3.2 Agent 类型与角色策略

```
                    WerewolfAgent (基类)
                         │
            ┌────────────┼────────────┐
            │            │            │
            v            v            v
     RoleStrategy    Reasoner    MemorySystem
     (角色策略接口)  (推理引擎)    (记忆系统)
            │
    ┌───────┼────────┬───────────┬──────────┬──────────┐
    │       │        │           │          │          │
    v       v        v           v          v          v
Werewolf  Seer    Witch      Hunter     Guard    Villager
Strategy  Strategy Strategy  Strategy  Strategy  Strategy
```

每种角色策略实现统一接口：

```python
class RoleStrategy(ABC):
    """角色策略接口"""

    @abstractmethod
    def plan_night_action(self, agent: "WerewolfAgent") -> NightAction:
        """规划夜间行动"""

    @abstractmethod
    def plan_speech(self, agent: "WerewolfAgent", speech_type: str) -> str:
        """规划白天发言"""

    @abstractmethod
    def plan_vote(self, agent: "WerewolfAgent", candidates: list) -> VoteDecision:
        """规划投票决策"""

    @abstractmethod
    def get_system_prompt(self, agent: "WerewolfAgent") -> str:
        """获取角色专属系统提示词"""

    @abstractmethod
    def update_on_event(self, agent: "WerewolfAgent", event: GameEvent):
        """响应游戏事件,更新角色特有状态"""
```

---

## 4. Agent 核心能力模型

### 4.1 WerewolfAgent 核心属性

```
┌─────────────────────────────────────────────────────┐
│                  WerewolfAgent                      │
│                                                     │
│  身份信息:                                           │
│  ├─ game_id          游戏 ID                        │
│  ├─ player_id        玩家 ID                        │
│  ├─ seat_number      座位号                         │
│  ├─ role             角色 (WEREWOLF/SEER/...)       │
│  └─ persona          人格档案 (性格/风格/说话习惯)    │
│                                                     │
│  核心模块:                                           │
│  ├─ memory           记忆系统 (MemorySystem)        │
│  ├─ reasoner         推理引擎 (BayesianReasoner)    │
│  ├─ strategy         角色策略 (RoleStrategy)        │
│  ├─ planner          行动规划 (ActionPlanner)       │
│  └─ speaker          发言生成 (SpeechGenerator)     │
│                                                     │
│  内部状态:                                           │
│  ├─ suspicion_map    嫌疑值表 {player_id: float}    │
│  ├─ trust_map        信任值表 {player_id: float}    │
│  ├─ identity_claims  身份声明记录                     │
│  ├─ known_info       已知确定信息 (查验结果等)        │
│  └─ emotional_state  情绪状态 (冷静/紧张/愤怒/...)   │
│                                                     │
│  核心方法:                                           │
│  ├─ perceive(event)  感知游戏事件                    │
│  ├─ think()          推理分析                        │
│  ├─ decide(action)   决策行动                        │
│  ├─ speak(type)      生成发言                        │
│  └─ reflect()        回合结束反思                     │
└─────────────────────────────────────────────────────┘
```

### 4.2 感知-思考-行动 循环 (Perceive-Think-Act)

```
    ┌──────────────────────────────────────┐
    │        游戏事件 (GameEvent)           │
    │  PHASE_CHANGE / DEATH_ANNOUNCE /     │
    │  PLAYER_SPEECH / VOTE_RESULT / ...   │
    └──────────────┬───────────────────────┘
                   │
                   v
    ┌──────────────────────────────────────┐
    │  1. PERCEIVE (感知)                  │
    │                                      │
    │  • 解析事件类型和内容                   │
    │  • 写入短期记忆                        │
    │  • 更新已知信息                        │
    │  • 记录其他玩家发言                     │
    └──────────────┬───────────────────────┘
                   │
                   v
    ┌──────────────────────────────────────┐
    │  2. THINK (推理)                     │
    │                                      │
    │  • 贝叶斯嫌疑更新                      │
    │  • 发言逻辑分析                        │
    │  • 行为模式匹配                        │
    │  • RAG 知识检索                        │
    │  • 信息交叉验证                        │
    │  • 输出推理链 (Chain of Thought)       │
    └──────────────┬───────────────────────┘
                   │
                   v
    ┌──────────────────────────────────────┐
    │  3. ACT (行动)                       │
    │                                      │
    │  • 根据角色策略选择行动                 │
    │  • 行动参数确定 (目标选择)              │
    │  • 生成自然语言发言                     │
    │  • 返回结构化决策                       │
    └──────────────────────────────────────┘
```

---

## 5. 记忆系统

### 5.1 三层记忆架构

```
┌─────────────────────────────────────────────────────────────┐
│                    MemorySystem                              │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  Layer 1: 工作记忆 (Working Memory)                  │   │
│  │  • 当前回合的实时信息                                  │   │
│  │  • 当前阶段上下文                                      │   │
│  │  • 最近 N 条发言                                      │   │
│  │  • 容量: 有限, 每回合重置非关键部分                     │   │
│  └─────────────────────────────────────────────────────┘   │
│                         │ 重要信息沉淀                       │
│                         v                                   │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  Layer 2: 情景记忆 (Episodic Memory)                 │   │
│  │  • 每回合的关键事件序列                                │   │
│  │  • 谁在哪个回合说了什么                                │   │
│  │  • 谁在哪个回合投了谁                                  │   │
│  │  • 死亡事件与时间线                                    │   │
│  │  • 结构: List[EpisodeRecord]                         │   │
│  └─────────────────────────────────────────────────────┘   │
│                         │ 抽象归纳                          │
│                         v                                   │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  Layer 3: 语义记忆 (Semantic Memory)                  │   │
│  │  • 玩家画像 (Player Profile)                          │   │
│  │  │  ├─ 发言风格摘要                                   │   │
│  │  │  ├─ 投票倾向                                       │   │
│  │  │  ├─ 可疑行为列表                                   │   │
│  │  │  └─ 声称身份                                       │   │
│  │  • 阵营推断 (Camp Inference)                          │   │
│  │  • 关系图 (谁和谁站边)                                │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 5.3 LangChain Memory 集成

记忆系统的对话层面通过 LangChain Memory 组件实现，与自定义的三层记忆架构协同工作：

```
┌──────────────────────────────────────────────────────────────┐
│                  LangChain Memory 层                         │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  ConversationBufferWindowMemory (k=20)                 │  │
│  │  • 存储最近 20 轮对话 (Agent 与 LLM 的交互)            │  │
│  │  • 每次 LLM 调用自动注入 chat_history                  │  │
│  │  • 对应 → 工作记忆层                                   │  │
│  └────────────────────────────────────────────────────────┘  │
│                         │                                    │
│                    超出窗口时                                  │
│                         v                                    │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  ConversationSummaryBufferMemory                       │  │
│  │  • 早期对话被 LLM 压缩为摘要                           │  │
│  │  • 保留关键信息，节省 token                             │  │
│  │  • 对应 → 语义记忆层                                   │  │
│  └────────────────────────────────────────────────────────┘  │
│                                                              │
│  使用方式 (在 LCEL Chain 中):                                 │
│                                                              │
│  chain = (                                                   │
│    RunnablePassthrough.assign(                               │
│      chat_history=RunnableLambda(                            │
│        memory.load_memory_variables                          │
│      ) | itemgetter("history")                               │
│    )                                                         │
│    | prompt_with_history    # 包含 MessagesPlaceholder       │
│    | llm                                                     │
│    | output_parser                                           │
│  )                                                           │
│                                                              │
└──────────────────────────────────────────────────────────────┘
         │
         │  协同工作
         v
┌──────────────────────────────────────────────────────────────┐
│                  自定义记忆层 (非 LangChain)                   │
│                                                              │
│  EpisodicMemory — 结构化事件记录 (死亡/投票/发言)            │
│  PlayerProfiles — 玩家画像 (嫌疑值/信任值/声称身份)          │
│  BayesianState  — 推理引擎状态 (后验概率)                    │
│                                                              │
│  这些数据以格式化文本注入 Prompt, 而非通过 LangChain Memory   │
└──────────────────────────────────────────────────────────────┘
```

```python
from langchain.memory import ConversationSummaryBufferMemory
from langchain_openai import ChatOpenAI

class AgentMemoryManager:
    """管理 LangChain Memory + 自定义记忆的协同"""

    def __init__(self, llm):
        # LangChain 对话记忆 (自动摘要)
        self.conversation_memory = ConversationSummaryBufferMemory(
            llm=llm,
            max_token_limit=2000,
            memory_key="chat_history",
            return_messages=True
        )
        # 自定义结构化记忆
        self.episodes: List[EpisodeRecord] = []
        self.player_profiles: Dict[int, PlayerProfile] = {}

    def save_agent_interaction(self, input_text: str, output_text: str):
        """保存 Agent 与 LLM 的交互到 LangChain Memory"""
        self.conversation_memory.save_context(
            {"input": input_text},
            {"output": output_text}
        )

    def get_full_context(self) -> dict:
        """获取完整上下文 (LangChain Memory + 自定义记忆)"""
        lc_memory = self.conversation_memory.load_memory_variables({})
        return {
            "chat_history": lc_memory.get("chat_history", []),
            "episode_summary": self._summarize_episodes(),
            "player_profiles": self._format_profiles(),
            "suspicion_ranking": self._format_suspicions()
        }
```

```python
@dataclass
class EpisodeRecord:
    """情景记忆条目"""
    round: int                    # 回合数
    phase: str                    # 阶段
    event_type: str               # 事件类型
    actor_id: Optional[int]       # 行动者
    target_id: Optional[int]      # 目标
    content: Optional[str]        # 内容 (发言文本等)
    timestamp: float              # 时间戳
    importance: float             # 重要度 0~1

@dataclass
class PlayerProfile:
    """玩家画像"""
    player_id: int
    seat_number: int
    claimed_role: Optional[str]         # 声称的角色
    speech_summary: List[str]           # 发言摘要
    vote_history: List[Tuple[int,int]]  # 投票记录 [(round, target)]
    suspicious_behaviors: List[str]     # 可疑行为
    suspicion_score: float              # 嫌疑值 0~1
    trust_score: float                  # 信任值 0~1
    is_alive: bool                      # 是否存活
    known_role: Optional[str]           # 已知角色 (查验结果)
```

---

## 6. RAG 知识库

### 6.1 知识分类

```
┌──────────────────────────────────────────────────────┐
│                RAG Knowledge Base                    │
│                                                      │
│  ┌────────────────────────────────────────────────┐  │
│  │  Collection 1: game_rules (游戏规则)            │  │
│  │  • 基本规则、角色技能、阶段流程                    │  │
│  │  • 特殊规则 (同守冲突、女巫自救等)                │  │
│  └────────────────────────────────────────────────┘  │
│                                                      │
│  ┌────────────────────────────────────────────────┐  │
│  │  Collection 2: role_strategies (角色策略)        │  │
│  │  • 狼人: 刀法/悍跳/倒钩/深水狼 策略              │  │
│  │  • 预言家: 验人策略/跳身份时机/警徽流             │  │
│  │  • 女巫: 救人/毒人策略及决策树                    │  │
│  │  • 村民: 站边/分析/投票策略                      │  │
│  │  • 猎人/守卫: 使用时机与策略                     │  │
│  └────────────────────────────────────────────────┘  │
│                                                      │
│  ┌────────────────────────────────────────────────┐  │
│  │  Collection 3: speech_patterns (发言模式)        │  │
│  │  • 不同角色不同阶段的发言范例                     │  │
│  │  • 逻辑框架模板                                  │  │
│  │  • 伪装/防守/进攻发言模式                        │  │
│  └────────────────────────────────────────────────┘  │
│                                                      │
│  ┌────────────────────────────────────────────────┐  │
│  │  Collection 4: game_analysis (对局分析)          │  │
│  │  • 经典对局复盘                                  │  │
│  │  • 常见板子分析                                  │  │
│  │  • 常见失误与最优策略                             │  │
│  └────────────────────────────────────────────────┘  │
│                                                      │
└──────────────────────────────────────────────────────┘
```

### 6.2 RAG 检索流程 (基于 LangChain)

整个 RAG 流程完全由 LangChain 组件驱动：

```
知识库初始化 (启动时):
═══════════════════════════════════════════════════════

  knowledge/*.md 文档
        │
        v
  ┌──────────────────────────────────┐
  │  RecursiveCharacterTextSplitter  │   LangChain 文档切片
  │  chunk_size=500, overlap=50      │
  └──────────────┬───────────────────┘
                 │
                 v
  ┌──────────────────────────────────┐
  │  OpenAIEmbeddings /              │   LangChain 向量化
  │  HuggingFaceEmbeddings           │
  └──────────────┬───────────────────┘
                 │
                 v
  ┌──────────────────────────────────┐
  │  Chroma.from_documents()         │   LangChain Chroma 封装
  │  persist_directory="./chroma_db" │
  └──────────────────────────────────┘


检索时 (Agent 决策):
═══════════════════════════════════════════════════════

  Agent 需要做决策
        │
        v
  ┌──────────────────────────────────┐
  │  构建检索 Query                   │
  │  f"{role}在{phase}阶段应该如何     │
  │   {action}? 当前局势: {summary}"  │
  └──────────────┬───────────────────┘
                 │
                 v
  ┌──────────────────────────────────┐
  │  Chroma.as_retriever(            │   LangChain Retriever
  │    search_type="mmr",            │   MMR 多样性检索
  │    search_kwargs={               │
  │      "k": 3,                     │
  │      "fetch_k": 10,              │
  │      "filter": {"role": role}    │   元数据过滤
  │    }                             │
  │  )                               │
  └──────────────┬───────────────────┘
                 │
                 v
  ┌──────────────────────────────────┐
  │  RetrievalQA.from_chain_type(    │   LangChain QA Chain
  │    llm=ChatOpenAI(...),          │
  │    chain_type="stuff",           │
  │    retriever=retriever,          │
  │    return_source_documents=True  │
  │  )                               │
  │                                  │
  │  或使用 LCEL 方式:                │
  │  chain = (                       │
  │    {"context": retriever,        │
  │     "question": passthrough}     │
  │    | prompt                      │
  │    | llm                         │
  │    | StrOutputParser()           │
  │  )                               │
  └──────────────┬───────────────────┘
                 │
                 v
  ┌──────────────────────────────────┐
  │  注入 Agent 决策 Prompt          │
  │  作为 {rag_context} 变量         │
  └──────────────────────────────────┘
```

### 6.3 LangChain RAG 核心代码示意

```python
from langchain_openai import ChatOpenAI, OpenAIEmbeddings
from langchain_community.vectorstores import Chroma
from langchain.text_splitter import RecursiveCharacterTextSplitter
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.output_parsers import StrOutputParser
from langchain_core.runnables import RunnablePassthrough

class RAGService:
    """基于 LangChain 的 RAG 服务"""

    def __init__(self, embeddings, persist_dir="./chroma_db"):
        self.embeddings = embeddings
        self.vectorstore = Chroma(
            collection_name="werewolf_knowledge",
            embedding_function=embeddings,
            persist_directory=persist_dir
        )
        self.retriever = self.vectorstore.as_retriever(
            search_type="mmr",
            search_kwargs={"k": 3, "fetch_k": 10}
        )

    def load_documents(self, docs_dir: str):
        """加载知识文档并向量化"""
        from langchain_community.document_loaders import DirectoryLoader, TextLoader
        loader = DirectoryLoader(docs_dir, glob="*.md", loader_cls=TextLoader)
        docs = loader.load()

        splitter = RecursiveCharacterTextSplitter(
            chunk_size=500, chunk_overlap=50,
            separators=["\n## ", "\n### ", "\n\n", "\n", "。", ""]
        )
        chunks = splitter.split_documents(docs)
        self.vectorstore.add_documents(chunks)

    def get_retriever(self, role_filter: str = None):
        """获取 Retriever, 可按角色过滤"""
        search_kwargs = {"k": 3, "fetch_k": 10}
        if role_filter:
            search_kwargs["filter"] = {"role": role_filter}
        return self.vectorstore.as_retriever(
            search_type="mmr", search_kwargs=search_kwargs
        )

    def build_rag_chain(self, llm, prompt_template):
        """构建 RAG Chain (LCEL 方式)"""
        return (
            {"context": self.retriever | self._format_docs,
             "question": RunnablePassthrough()}
            | prompt_template
            | llm
            | StrOutputParser()
        )

    @staticmethod
    def _format_docs(docs):
        return "\n\n".join(doc.page_content for doc in docs)
```

---

## 7. 推理引擎

### 7.1 贝叶斯嫌疑推理

```
┌──────────────────────────────────────────────────────────┐
│                 BayesianReasoner                         │
│                                                          │
│  核心: 维护每个玩家是狼人的后验概率                         │
│                                                          │
│  P(狼人|证据) ∝ P(证据|狼人) × P(狼人)                    │
│                                                          │
│  初始先验:                                                │
│    P(狼人) = 狼人数 / 总人数 (如 3/9 = 0.33)              │
│                                                          │
│  证据更新规则:                                             │
│  ┌──────────────────────────────────────────────────┐    │
│  │ 证据类型                  │ 似然比 (Likelihood)    │    │
│  ├──────────────────────────┼───────────────────────┤    │
│  │ 被预言家查杀              │ ×10.0 (强证据)        │    │
│  │ 被预言家验好人            │ ×0.05 (强证据)        │    │
│  │ 与已知狼人互保            │ ×3.0                  │    │
│  │ 与已知狼人对立            │ ×0.5                  │    │
│  │ 发言逻辑矛盾              │ ×1.5                  │    │
│  │ 投票方向异常              │ ×1.8                  │    │
│  │ 声称身份与事实冲突         │ ×2.5                  │    │
│  │ 存活到后期 (生存偏差)     │ ×1.2 / 回合           │    │
│  │ 被其他可信玩家指认         │ ×1.5                  │    │
│  └──────────────────────────┴───────────────────────┘    │
│                                                          │
│  约束: 所有存活玩家嫌疑值归一化到合理范围                    │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

### 7.2 推理过程 (Chain of Thought)

```
Agent.think() 执行步骤:

Step 1: 信息汇总
    ├─ 从记忆系统提取本回合所有事件
    ├─ 整理存活玩家列表和死亡时间线
    └─ 提取各玩家的身份声明

Step 2: 证据收集
    ├─ 分析每个玩家的发言内容
    ├─ 检查投票是否与发言一致
    ├─ 识别互保/互踩关系
    └─ 检测逻辑矛盾

Step 3: 贝叶斯更新
    ├─ 对每条新证据更新嫌疑值
    ├─ 归一化概率分布
    └─ 输出更新后的嫌疑排名

Step 4: 策略规划
    ├─ 根据角色策略确定行动目标
    ├─ RAG 检索相关策略建议
    └─ 输出行动计划

Step 5: 输出推理链
    └─ JSON 结构化的推理过程记录
```

---

## 8. Prompt 工程 (LangChain ChatPromptTemplate)

### 8.1 分层 Prompt 结构 (LangChain 实现)

所有 Prompt 使用 LangChain 的 `ChatPromptTemplate` + `MessagesPlaceholder` 管理：

```python
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder

# 夜间行动决策 Prompt
night_action_prompt = ChatPromptTemplate.from_messages([
    # Layer 1: System (角色设定 + 人格)
    ("system", """你是一名狼人杀游戏中的{role}。{persona_description}

你的目标: {role_objective}
行为约束:
{role_constraints}
"""),

    # Layer 2: 对话历史 (LangChain Memory 自动注入)
    MessagesPlaceholder(variable_name="chat_history"),

    # Layer 3: 当前局势 + RAG 知识 + 推理结果
    ("human", """当前游戏状态:
- 第{round}天{phase}阶段
- 存活玩家: {alive_players}
- 死亡玩家: {dead_players}
- 你的已知信息: {known_info}

参考策略知识:
{rag_context}

你的推理分析:
{reasoning_summary}
嫌疑排名: {suspicion_ranking}

请选择今晚的行动目标。以 JSON 格式回答:
{{"target_id": <玩家ID>, "reason": "<理由>"}}
""")
])
```

### 8.2 LangChain 决策链 (LCEL)

每种决策场景对应一条 LangChain Chain：

```python
from langchain_core.output_parsers import JsonOutputParser
from langchain_core.runnables import RunnablePassthrough, RunnableLambda

class ActionPlanner:
    """基于 LangChain LCEL 的行动规划器"""

    def __init__(self, llm, rag_service, memory):
        self.llm = llm
        self.rag = rag_service
        self.memory = memory

    def build_night_action_chain(self):
        """夜间行动决策链"""
        return (
            RunnablePassthrough.assign(
                rag_context=lambda x: self.rag.query(
                    f"{x['role']}在夜晚应该如何行动"
                ),
                chat_history=lambda x: self.memory.load_memory_variables({})
                    .get("chat_history", [])
            )
            | night_action_prompt
            | self.llm
            | JsonOutputParser()
        )

    def build_speech_chain(self):
        """发言生成链"""
        return (
            RunnablePassthrough.assign(
                rag_context=lambda x: self.rag.query(
                    f"{x['role']}在白天讨论时的发言策略"
                ),
                chat_history=lambda x: self.memory.load_memory_variables({})
                    .get("chat_history", [])
            )
            | speech_prompt
            | self.llm
            | StrOutputParser()   # 发言直接输出字符串
        )

    def build_vote_chain(self):
        """投票决策链"""
        return (
            RunnablePassthrough.assign(
                rag_context=lambda x: self.rag.query("投票分析技巧"),
                chat_history=lambda x: self.memory.load_memory_variables({})
                    .get("chat_history", [])
            )
            | vote_prompt
            | self.llm
            | JsonOutputParser()
        )
```

### 8.3 结构化输出 (LangChain OutputParser)

```python
from langchain.output_parsers import PydanticOutputParser

class NightActionOutput(BaseModel):
    target_id: int = Field(description="目标玩家ID")
    reason: str = Field(description="行动理由")
    confidence: float = Field(description="信心度 0~1")

class VoteOutput(BaseModel):
    target_id: int = Field(description="投票目标ID, 0表示弃票")
    reason: str = Field(description="投票理由")

class SpeechOutput(BaseModel):
    content: str = Field(description="发言内容, 100-200字")
    emotion: str = Field(description="情绪: calm/suspicious/angry/confident")
    targets_mentioned: List[int] = Field(description="提到的玩家ID列表")

# 在 Prompt 中自动注入格式说明
night_parser = PydanticOutputParser(pydantic_object=NightActionOutput)
# night_parser.get_format_instructions() 自动生成 JSON schema 说明
```

### 8.2 人格系统 (Persona)

```python
PERSONAS = {
    "aggressive": {
        "name": "激进型",
        "description": "说话直接，喜欢怼人，投票果断",
        "speech_style": "简短有力，常用反问",
        "decision_bias": {"accusation": 1.3, "defense": 0.7},
        "temperature": 0.8
    },
    "analytical": {
        "name": "分析型",
        "description": "逻辑缜密，善于总结归纳",
        "speech_style": "条理清晰，喜欢列举证据",
        "decision_bias": {"accusation": 1.0, "defense": 1.0},
        "temperature": 0.5
    },
    "cautious": {
        "name": "谨慎型",
        "description": "保守发言，不轻易表态",
        "speech_style": "委婉，常用'我觉得''可能'",
        "decision_bias": {"accusation": 0.7, "defense": 1.3},
        "temperature": 0.6
    },
    "charismatic": {
        "name": "魅力型",
        "description": "善于说服，带节奏能力强",
        "speech_style": "有感染力，善用煽动",
        "decision_bias": {"accusation": 1.1, "defense": 1.1},
        "temperature": 0.9
    }
}
```

---

## 9. Java 后端集成

### 9.1 AIPlayerBridge 组件

在 Java 后端新增 `AIPlayerBridge` 组件，作为 AI 服务的集成桥梁：

```
┌──────────────────────────────────────────────────┐
│  AIPlayerBridge (Spring @Component)              │
│                                                  │
│  职责:                                           │
│  1. 游戏开始时为 AI 玩家创建 Agent               │
│  2. 在每个阶段为 AI 玩家请求决策                  │
│  3. 将游戏事件推送给 AI Agent                    │
│  4. 游戏结束时销毁 Agent                         │
│                                                  │
│  关键方法:                                        │
│  ├─ createAgents(gameId, aiPlayers)              │
│  ├─ requestAction(gameId, playerId, phase)       │
│  ├─ pushEvent(gameId, event)                     │
│  └─ destroyAgents(gameId)                        │
│                                                  │
│  集成点:                                          │
│  ├─ GameService.startGame() → createAgents()     │
│  ├─ PhaseScheduler 各阶段 → requestAction()      │
│  ├─ 各种广播消息 → pushEvent()                   │
│  └─ GameService.endGame() → destroyAgents()      │
└──────────────────────────────────────────────────┘
```

### 9.2 PhaseScheduler 集成时序

```
PhaseScheduler.executePhaseStart()
    │
    ├── 更新游戏阶段 (updatePhase)
    ├── 广播阶段消息 (broadcastToGame)
    │
    └── 如果阶段有关联角色:
        │
        ├── 获取该角色的 AI 玩家列表
        │   List<Player> aiPlayers = getAiPlayersForPhase(phase)
        │
        └── 对每个 AI 玩家:
            │
            ├── 等待随机延迟 (模拟思考时间, 2~8秒)
            │
            ├── 调用 AIPlayerBridge.requestAction(gameId, playerId, phase)
            │   │
            │   └── HTTP POST → Python AI Service
            │       │
            │       └── Agent.decide() → 返回 {action, targetId, reason}
            │
            └── 调用 GameService.executeAction(gameId, aiUserId, action, targetId)
```

### 9.3 AI 玩家创建流程

```
GameService.startGame()
    │
    ├── createPlayers() 时:
    │   ├── 从 RoomMember 创建真人 Player (isAi=false)
    │   └── 创建 AI Player (isAi=true, aiName="AI-小明")
    │       ├── user = null
    │       ├── isAi = true
    │       └── aiName = 随机中文名
    │
    ├── roleAssigner.assignRoles() 分配角色 (AI和真人一视同仁)
    │
    └── AIPlayerBridge.createAgents()
        │
        └── 对每个 AI Player:
            POST /api/agent/create
            {
                game_id, player_id, seat_number,
                role, persona (随机选择人格),
                game_mode, player_count, role_distribution
            }
```

---

## 10. 目录结构

```
packages/ai-service/
├── main.py                          # FastAPI 应用入口
├── requirements.txt                 # Python 依赖 (含 LangChain 全家桶)
├── .env.example                     # 环境变量示例
│
├── agents/                          # Agent 核心模块
│   ├── __init__.py
│   ├── base_agent.py                # WerewolfAgent 基类
│   ├── agent_manager.py             # Agent 生命周期管理器
│   ├── memory/                      # 记忆系统
│   │   ├── __init__.py
│   │   ├── memory_system.py         # 三层记忆 + LangChain Memory 协同
│   │   ├── working_memory.py        # 工作记忆
│   │   ├── episodic_memory.py       # 情景记忆
│   │   └── semantic_memory.py       # 语义记忆 (玩家画像)
│   ├── reasoning/                   # 推理引擎
│   │   ├── __init__.py
│   │   ├── bayesian_reasoner.py     # 贝叶斯嫌疑推理
│   │   ├── evidence_analyzer.py     # 证据分析器
│   │   └── chain_of_thought.py      # 推理链生成
│   ├── strategies/                  # 角色策略 (策略模式)
│   │   ├── __init__.py
│   │   ├── base_strategy.py         # RoleStrategy 接口
│   │   ├── werewolf_strategy.py     # 狼人策略
│   │   ├── seer_strategy.py         # 预言家策略
│   │   ├── witch_strategy.py        # 女巫策略
│   │   ├── hunter_strategy.py       # 猎人策略
│   │   ├── guard_strategy.py        # 守卫策略
│   │   └── villager_strategy.py     # 村民策略
│   ├── planning/                    # 行动规划 (LangChain LCEL Chains)
│   │   ├── __init__.py
│   │   ├── action_planner.py        # 行动规划器 — LangChain Chain 编排
│   │   └── speech_generator.py      # 发言生成器 — LangChain Chain 编排
│   └── persona/                     # 人格系统
│       ├── __init__.py
│       └── persona_profiles.py      # 人格档案定义
│
├── services/                        # 基础服务 (LangChain 封装)
│   ├── __init__.py
│   ├── llm_service.py               # LLM: ChatOpenAI / ChatOllama 统一封装
│   └── rag_service.py               # RAG: Chroma + Retriever + LCEL Chain
│
├── routers/                         # API 路由
│   ├── __init__.py
│   ├── agent_router.py              # Agent 管理 API
│   ├── knowledge_router.py          # 知识库管理 API
│   └── health_router.py             # 健康检查 API
│
├── models/                          # 数据模型 (Pydantic + LangChain OutputParser)
│   ├── __init__.py
│   ├── game_models.py               # 游戏相关模型
│   ├── agent_models.py              # Agent 相关模型
│   └── event_models.py              # 事件模型
│
├── prompts/                         # Prompt 模板 (LangChain ChatPromptTemplate)
│   ├── __init__.py
│   ├── system_prompts.py            # 角色系统提示词 (ChatPromptTemplate)
│   ├── action_prompts.py            # 行动决策提示词 (ChatPromptTemplate)
│   └── speech_prompts.py            # 发言生成提示词 (ChatPromptTemplate)
│
├── knowledge/                       # 知识库文档 (LangChain DirectoryLoader 加载)
│   ├── 01-game-rules.md             # 游戏规则
│   ├── 02-role-skills.md            # 角色技能详解
│   ├── 03-speech-patterns.md        # 发言模式与技巧
│   ├── 04-common-formations.md      # 常见板子分析
│   ├── 05-werewolf-strategies.md    # 狼人策略
│   ├── 06-seer-strategies.md        # 预言家策略
│   ├── 07-witch-strategies.md       # 女巫策略
│   └── 08-voting-analysis.md        # 投票分析技巧
│
└── tests/                           # 测试
    ├── test_agent.py
    ├── test_reasoning.py
    ├── test_memory.py
    ├── test_chains.py               # LangChain Chain 测试
    └── test_strategies.py
```

---

## 11. 数据流与时序

### 11.1 夜晚阶段完整时序 (以狼人阶段为例)

```
                    Java Backend                          Python AI Service
                        │                                       │
PhaseScheduler          │                                       │
  scheduleNextPhase()   │                                       │
        │               │                                       │
        v               │                                       │
  executePhaseStart()   │                                       │
  phase = WEREWOLF      │                                       │
        │               │                                       │
        ├── updatePhase(WEREWOLF)                               │
        ├── broadcastToGame(PHASE_CHANGE)                       │
        │               │                                       │
        │               │   POST /api/agent/{gid}/event         │
        │               │──────────────────────────────────────>│
        │               │   { type: "PHASE_CHANGE",             │
        │               │     phase: "WEREWOLF", round: 1 }     │
        │               │                                       │
        │               │                    Agent.perceive()   │
        │               │                    更新工作记忆         │
        │               │<──────────────────────────────────────│
        │               │   { status: "ok" }                    │
        │               │                                       │
        │  获取AI狼人列表                                        │
        │  aiWolves = getAiPlayersForPhase("WEREWOLF")          │
        │               │                                       │
        │  for each AI wolf:                                    │
        │    sleep(random 2~8s)  // 模拟思考                     │
        │               │                                       │
        │               │   POST /api/agent/{gid}/{pid}/act     │
        │               │──────────────────────────────────────>│
        │               │   { phase: "WEREWOLF",                │
        │               │     action_type: "kill" }             │
        │               │                                       │
        │               │              Agent.think()            │
        │               │              Agent.decide()           │
        │               │              ┌────────────────┐       │
        │               │              │ 1. 检索记忆     │       │
        │               │              │ 2. 推理分析     │       │
        │               │              │ 3. RAG检索策略  │       │
        │               │              │ 4. LLM决策     │       │
        │               │              │ 5. 返回目标     │       │
        │               │              └────────────────┘       │
        │               │<──────────────────────────────────────│
        │               │   { action: "kill",                   │
        │               │     target_id: 5,                     │
        │               │     reason: "5号可能是预言家",          │
        │               │     thinking: "..." }                 │
        │               │                                       │
        │  GameService.executeAction(                           │
        │    gameId, aiUserId=null,                             │
        │    action="kill", targetId=5)                         │
        │               │                                       │
        v               │                                       │
  executePhaseEnd()     │                                       │
  进入下一阶段           │                                       │
```

### 11.2 白天讨论阶段时序

```
                    Java Backend                          Python AI Service
                        │                                       │
  phase = DISCUSSION    │                                       │
        │               │                                       │
        │  广播讨论开始   │                                       │
        │               │                                       │
        │  按座位顺序, 轮到 AI 玩家时:                            │
        │               │                                       │
        │               │   POST /api/agent/{gid}/{pid}/speak   │
        │               │──────────────────────────────────────>│
        │               │   { phase: "DISCUSSION",              │
        │               │     speech_type: "NORMAL",            │
        │               │     speak_order: 3 }                  │
        │               │                                       │
        │               │              Agent.speak()            │
        │               │              ┌────────────────┐       │
        │               │              │ 1. 汇总推理结果 │       │
        │               │              │ 2. 选择发言策略 │       │
        │               │              │ 3. RAG检索话术  │       │
        │               │              │ 4. LLM生成发言 │       │
        │               │              │ 5. 人格风格修饰 │       │
        │               │              └────────────────┘       │
        │               │<──────────────────────────────────────│
        │               │   { content: "我觉得5号的发言...",      │
        │               │     emotion: "suspicious",            │
        │               │     target: 5 }                       │
        │               │                                       │
        │  广播 AI 发言到房间                                     │
        │  broadcastToRoom(PLAYER_CHAT, {                       │
        │    playerId, seatNumber, content })                   │
        │               │                                       │
        │               │   同时推送给所有Agent更新记忆            │
        │               │   POST /api/agent/{gid}/event         │
        │               │   { type: "PLAYER_SPEECH",            │
        │               │     speaker_id, content }             │
```

---

## 12. API 接口设计

### 12.1 Agent 管理

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/agent/create` | 创建 Agent 实例 |
| DELETE | `/api/agent/{game_id}` | 销毁游戏所有 Agent |
| GET | `/api/agent/{game_id}/{player_id}/status` | 获取 Agent 状态 |

### 12.2 Agent 决策

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/agent/{game_id}/{player_id}/act` | 请求夜间行动决策 |
| POST | `/api/agent/{game_id}/{player_id}/speak` | 请求发言生成 |
| POST | `/api/agent/{game_id}/{player_id}/vote` | 请求投票决策 |

### 12.3 事件推送

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/agent/{game_id}/event` | 推送游戏事件给所有 Agent |

### 12.4 知识库管理

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/knowledge/init` | 初始化知识库 (加载文档) |
| POST | `/api/knowledge/query` | RAG 知识检索 |
| GET | `/api/knowledge/stats` | 知识库统计 |

### 12.5 调试接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/agent/{game_id}/{player_id}/memory` | 查看 Agent 记忆 |
| GET | `/api/agent/{game_id}/{player_id}/reasoning` | 查看推理链 |
| GET | `/api/agent/{game_id}/{player_id}/suspicion` | 查看嫌疑值表 |

### 12.6 请求/响应示例

**创建 Agent:**
```json
// POST /api/agent/create
{
    "game_id": 1,
    "player_id": 5,
    "seat_number": 3,
    "role": "WEREWOLF",
    "persona": "aggressive",
    "game_mode": "standard_9",
    "player_count": 9,
    "role_distribution": { "werewolf": 3, "villager": 3, "seer": 1, "witch": 1, "hunter": 1 },
    "teammates": [7, 12]   // 狼人队友 (仅狼人有)
}
```

**夜间行动决策:**
```json
// POST /api/agent/1/5/act
{
    "phase": "WEREWOLF",
    "action_type": "kill",
    "alive_players": [1,2,3,4,5,6,7,8,9],
    "round": 1
}

// Response
{
    "action": "kill",
    "target_id": 4,
    "reason": "4号位置靠近预言家常验区域，且第一天发言积极，有较大概率是预言家",
    "confidence": 0.72,
    "thinking": {
        "candidates": [
            {"id": 4, "score": 0.72, "reason": "高概率神职"},
            {"id": 2, "score": 0.65, "reason": "发言很有逻辑"}
        ],
        "strategy": "首刀优先击杀预言家"
    }
}
```

**发言生成:**
```json
// POST /api/agent/1/5/speak
{
    "phase": "DISCUSSION",
    "speech_type": "NORMAL",
    "speak_order": 3,
    "round": 2,
    "recent_speeches": [
        {"player_id": 1, "content": "昨晚3号死了..."},
        {"player_id": 2, "content": "我验了6号是好人..."}
    ]
}

// Response
{
    "content": "我来说一下我的看法。2号刚才跳了预言家说验了6号好人，但我觉得这个验人有点刻意。首先6号位置并不是常规首验位，其次2号跳得太着急了，像是做好了准备的。我目前站6号好人，但对2号持保留态度。投票方向我倾向先出8号，8号昨天的发言很水，全程没有有效信息。",
    "emotion": "analytical",
    "targets_mentioned": [2, 6, 8],
    "stance": {
        "trust": [6],
        "suspect": [2, 8]
    }
}
```

---

## 13. 配置与部署

### 13.1 环境变量

```env
# LLM 配置
LLM_PROVIDER=openai              # openai / ollama / deepseek
OPENAI_API_KEY=sk-xxx
OPENAI_BASE_URL=https://api.openai.com/v1
OPENAI_MODEL=gpt-4o-mini         # 推荐: gpt-4o-mini (性价比)
OLLAMA_URL=http://localhost:11434
OLLAMA_MODEL=qwen2.5:7b

# AI Agent 配置
AGENT_THINK_DELAY_MIN=2000       # 模拟思考最小延迟(ms)
AGENT_THINK_DELAY_MAX=8000       # 模拟思考最大延迟(ms)
AGENT_TEMPERATURE=0.7            # LLM 温度
AGENT_MAX_TOKENS=500             # LLM 最大 token

# RAG 配置
CHROMA_DB_PATH=./chroma_db
RAG_TOP_K=3
RAG_SIMILARITY_THRESHOLD=0.7

# 服务配置
SERVICE_HOST=0.0.0.0
SERVICE_PORT=8000
JAVA_BACKEND_URL=http://localhost:8080
LOG_LEVEL=INFO
```

### 13.2 依赖更新

```txt
# Web Framework
fastapi>=0.109.0
uvicorn[standard]>=0.27.0

# HTTP Client
httpx>=0.26.0

# ============ LangChain 核心 (项目核心依赖) ============
langchain>=0.1.0                  # LangChain 核心框架
langchain-core>=0.1.0             # LCEL / Prompt / OutputParser
langchain-community>=0.0.13       # 社区集成 (Chroma/TextLoader 等)
langchain-openai>=0.0.5           # ChatOpenAI / OpenAIEmbeddings

# ============ LangChain 用到的组件 ============
# LLM:       ChatOpenAI, ChatOllama
# Embedding: OpenAIEmbeddings
# VectorDB:  Chroma (via langchain-community)
# Memory:    ConversationSummaryBufferMemory
# Prompt:    ChatPromptTemplate, MessagesPlaceholder
# Chain:     LCEL (| 管道语法), RunnablePassthrough
# Output:    PydanticOutputParser, JsonOutputParser
# Loader:    DirectoryLoader, TextLoader
# Splitter:  RecursiveCharacterTextSplitter

# Vector Database
chromadb>=0.4.22

# OpenAI SDK (LangChain 底层依赖)
openai>=1.12.0

# Environment & Config
python-dotenv>=1.0.0
pydantic>=2.5.3
python-multipart>=0.0.6

# Utilities
numpy>=1.24.0
tiktoken>=0.5.0                   # Token 计数 (Embedding/Memory 需要)
```

### 13.3 LangChain 组件使用全景

```
┌─────────────────────────────────────────────────────────────┐
│             LangChain 在项目中的使用全景                      │
│                                                             │
│  ┌─── LLM ──────────────────────────────────────────────┐  │
│  │  ChatOpenAI(model="gpt-4o-mini", temperature=0.7)    │  │
│  │  ChatOllama(model="qwen2.5:7b")    ← 本地开发备选     │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                             │
│  ┌─── Embedding ────────────────────────────────────────┐  │
│  │  OpenAIEmbeddings(model="text-embedding-3-small")    │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                             │
│  ┌─── Vector Store ─────────────────────────────────────┐  │
│  │  Chroma(embedding_function=..., persist_directory=.) │  │
│  │  ├─ .as_retriever(search_type="mmr")                 │  │
│  │  ├─ .add_documents(chunks)                           │  │
│  │  └─ .similarity_search(query, k=3)                   │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                             │
│  ┌─── Document Processing ──────────────────────────────┐  │
│  │  DirectoryLoader(glob="*.md") → 加载知识文档           │  │
│  │  RecursiveCharacterTextSplitter → 切片                │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                             │
│  ┌─── Prompt ───────────────────────────────────────────┐  │
│  │  ChatPromptTemplate.from_messages([...])              │  │
│  │  MessagesPlaceholder("chat_history")                  │  │
│  │  PromptTemplate(template=..., input_variables=[...])  │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                             │
│  ┌─── Memory ───────────────────────────────────────────┐  │
│  │  ConversationSummaryBufferMemory(max_token_limit=2k) │  │
│  │  ├─ .save_context(input, output)                     │  │
│  │  └─ .load_memory_variables({}) → chat_history        │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                             │
│  ┌─── Chain (LCEL) ─────────────────────────────────────┐  │
│  │  夜间决策链 = prompt | llm | JsonOutputParser()       │  │
│  │  发言生成链 = prompt | llm | StrOutputParser()        │  │
│  │  投票决策链 = prompt | llm | PydanticOutputParser()   │  │
│  │  RAG 检索链 = retriever | format_docs                 │  │
│  │                                                       │  │
│  │  RunnablePassthrough.assign(rag=..., memory=...)     │  │
│  │  RunnableLambda(custom_fn)                           │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                             │
│  ┌─── Output Parser ───────────────────────────────────┐  │
│  │  PydanticOutputParser(NightActionOutput)              │  │
│  │  JsonOutputParser()                                   │  │
│  │  StrOutputParser()                                    │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 14. 实现计划

### Phase 1: 基础 Agent 框架 (2天)

```
✅ 目标: Agent 能创建、接收事件、返回硬编码决策

├── agents/base_agent.py          WerewolfAgent 基类骨架
├── agents/agent_manager.py       AgentManager 生命周期管理
├── models/                       Pydantic 数据模型
├── routers/agent_router.py       Agent API 路由
└── Java: AIPlayerBridge          后端集成桥梁 + PhaseScheduler 调用
```

### Phase 2: 记忆系统 + 角色策略 (2天)

```
✅ 目标: Agent 有记忆, 不同角色有不同行为

├── agents/memory/                三层记忆系统实现
├── agents/strategies/            6种角色策略实现
├── agents/persona/               人格系统
└── 决策逻辑: 基于规则 + 记忆的简单决策
```

### Phase 3: RAG 知识库 (1.5天)

```
✅ 目标: 知识库可检索, 注入决策 Prompt

├── knowledge/                    8个知识文档编写
├── services/rag_service.py       RAG 初始化 + 文档向量化 + 检索
└── 集成到 Agent 决策流程
```

### Phase 4: 推理引擎 + LLM 集成 (2天)

```
✅ 目标: Agent 有推理能力, LLM 生成自然发言

├── agents/reasoning/             贝叶斯推理 + 证据分析
├── agents/planning/              行动规划 + 发言生成
├── prompts/                      Prompt 模板
└── services/llm_service.py       LLM 集成 (多模型支持)
```

### Phase 5: 联调测试 + 优化 (1.5天)

```
✅ 目标: 完整对局可运行

├── 端到端联调 (Java ↔ Python)
├── AI 玩家行为调优
├── 发言质量优化
├── 性能测试 (并发 Agent)
└── tests/                        单元测试
```

### 时间线总览

```
Day 1-2:   Phase 1  基础 Agent 框架 + Java 集成
Day 3-4:   Phase 2  记忆系统 + 角色策略
Day 5:     Phase 3  RAG 知识库 (前半天) + 集成
Day 5-6:   Phase 4  推理引擎 + LLM
Day 7:     Phase 5  联调 + 优化
─────────────────────────────────────────────
Total:     ~7 工作日
```

---

## 附录 A: 与现有代码的关系

### 需要保留的现有代码

| 文件 | 说明 |
|------|------|
| `routers/health.py` | 保留健康检查 |
| `main.py` | 保留应用框架，更新路由注册 |

### 需要重写的模块

| 旧文件 | 新模块 | 说明 |
|--------|--------|------|
| `services/llm_service.py` | `services/llm_service.py` | 从裸 httpx 重构为 **LangChain ChatOpenAI/ChatOllama** |
| `services/rag_service.py` | `services/rag_service.py` | 从裸 ChromaDB 重构为 **LangChain Chroma + Retriever + LCEL Chain** |
| `routers/game.py` | `routers/agent_router.py` | 从简单接口重构为 Agent API |
| `routers/knowledge.py` | `routers/knowledge_router.py` | 从骨架实现为真实 LangChain RAG |

### Java 后端需要新增

| 类 | 说明 |
|---|------|
| `AIPlayerBridge` | AI 服务 HTTP 客户端 + 调用编排 |
| `AIPlayerConfig` | AI 服务 URL/超时等配置 |
| `GameService` 修改 | `createPlayers()` 支持创建 AI 玩家 |
| `PhaseScheduler` 修改 | 各阶段加入 AI 玩家行动调用 |
| `application.yml` 修改 | 新增 AI 服务配置项 |

---

## 附录 B: 关键决策记录

| 决策 | 选择 | 理由 |
|------|------|------|
| **AI 框架** | **LangChain** | 毕设核心技术栈要求；提供 LLM/RAG/Memory/Chain 全链路编排；LCEL 管道语法简洁高效 |
| Agent 运行位置 | Python 服务内 | LangChain 生态在 Python，避免 Java 调 LLM 的笨拙 |
| 状态存储 | 内存 (Dict) + LangChain Memory | 单局游戏生命周期短，无需持久化；LangChain Memory 自动管理对话上下文 |
| Agent 间通信 | 不直接通信 | 通过 Java 后端中转事件，保持架构简单 |
| LLM 选型 | gpt-4o-mini 为主 | 性价比高，响应快，狼人杀不需要最强推理 |
| LLM 接口 | LangChain ChatOpenAI | 统一接口，可无缝切换 ChatOllama 本地模型 |
| RAG 实现 | LangChain Chroma + LCEL | 比裸 ChromaDB 多了文档加载/切片/Retriever/Chain 能力 |
| Prompt 管理 | LangChain ChatPromptTemplate | 模板变量注入、MessagesPlaceholder 历史注入、格式化指令自动生成 |
| 记忆管理 | LangChain ConversationSummaryBufferMemory + 自定义 | LangChain 管对话历史摘要，自定义层管结构化游戏数据 |
| 输出解析 | LangChain PydanticOutputParser | 自动生成 JSON schema 说明，强类型解析，与 Pydantic 模型一致 |
| 推理引擎 | 贝叶斯 + LLM 混合 | 贝叶斯提供结构化推理，LLM 处理模糊分析 |
| 发言生成 | LangChain LCEL Chain | RAG 检索 → Prompt 注入 → LLM 生成 → 输出解析，全链路 LangChain 编排 |
