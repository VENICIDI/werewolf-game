# 系统架构设计文档（Architecture）

> 本文档为 **当前实现** 的总览（Source of Truth），覆盖运行形态、模块划分、协同时序与最近一轮重构后的事实。
> 关联文档：`ai-agent-design.md`（设计稿）、`ai-agent-memory-implementation.md`（记忆系统实现细节）、`game-state-machine.md`（状态机函数级说明）、`game-flow.md`（流程）、`api.md`、`database.md`。
> 更新时间：2026-04-26。

---

## 目录

- [1. 系统总览](#1-系统总览)
- [2. 运行形态与端口](#2-运行形态与端口)
- [3. 模块边界与职责](#3-模块边界与职责)
- [4. 后端：分层与游戏引擎](#4-后端分层与游戏引擎)
- [5. AI 服务：Agent 体系](#5-ai-服务agent-体系)
- [6. 前端：小程序 / Taro](#6-前端小程序--taro)
- [7. 进程间协议（HTTP / WebSocket）](#7-进程间协议http--websocket)
- [8. 状态协同关键机制](#8-状态协同关键机制)
- [9. AI 事件总线（Phase E）](#9-ai-事件总线phase-e)
- [10. 数据存储](#10-数据存储)
- [11. 启停与运维](#11-启停与运维)
- [12. 演进与已落地的修复](#12-演进与已落地的修复)
- [13. 已知约束与待办](#13-已知约束与待办)

---

## 1. 系统总览

```
┌──────────────┐    HTTP REST          ┌─────────────────────┐    HTTP REST     ┌──────────────────┐
│  微信小程序   │ ───────────────────→ │   Backend  :8080    │ ───────────────→ │  AI Service :8000 │
│  (Taro/React)│                      │   Spring Boot 3.2   │                  │  FastAPI+LangChain│
│              │ ←── WebSocket ──────  │                     │ ←── HTTP回包 ──── │                  │
└──────────────┘    /ws/room/{code}   └────┬────────┬───────┘                  └────────┬─────────┘
                                            │        │                                    │
                                            │        │                                    │
                                       JPA  │        │  Lettuce                           │  Chroma
                                            v        v                                    v
                                      ┌──────────┐  ┌──────────┐                  ┌──────────────┐
                                      │ MySQL    │  │ Redis    │                  │ ChromaDB(本地)│
                                      │ :3306    │  │ :6379    │                  │ ./chroma_db   │
                                      └──────────┘  └──────────┘                  └──────────────┘
                                                                                       ▲
                                                                                       │  本地知识文档
                                                                                  knowledge/*.md
```

- **唯一权威**：所有游戏状态以 Backend 为准；AI 服务无持久状态，前端只做展示与输入。
- **三套通道**：
  - 客户端 → 后端：HTTP REST + WebSocket（实时消息 + 心跳）。
  - 后端 → AI 服务：HTTP REST（决策请求 + **事件推送**）。
  - 后端 → 客户端：WebSocket 单播 / 广播。
- **AI 不直接对前端通信**：所有 AI 行动通过 Backend 落库后，再以 WebSocket 广播。

---

## 2. 运行形态与端口

| 进程 | 端口 | 启动 | 说明 |
|------|------|------|------|
| MySQL | 3306 | `brew services start mysql` 或 docker | 持久化（用户/房间/游戏/日志） |
| Redis | 6379 | `brew services start redis` 或 docker | 心跳/会话/缓存 |
| Backend (Spring Boot) | 8080 | `./mvnw spring-boot:run` | 游戏核心引擎、HTTP API、WebSocket |
| AI Service (FastAPI) | 8000 | `python main.py` | LangChain Agents + RAG |
| AI Speech (FastAPI) | 8001 | `python main.py` | 可选：Whisper STT + TTS |
| 前端开发服务 | 10086 | `npm run dev`（H5）/ `build:weapp`（小程序） | Taro |

统一脚本：`./start.sh {start|stop|restart|status|backend|ai|...}`。

---

## 3. 模块边界与职责

```
packages/
├── backend/              ← Spring Boot：唯一游戏权威
│   └── src/main/java/com/werewolf/
│       ├── ai/           AIPlayerBridge —— 后端到 AI 服务的桥
│       ├── config/       ConfigLoader / GameConfig / RoleConfig / WebConfig
│       ├── controller/   AuthController / RoomController / GameController
│       ├── dto/          请求 / 响应模型
│       ├── entity/       JPA 实体（User/Room/RoomMember/Game/Player/GameLog）
│       ├── exception/    全局异常
│       ├── game/         游戏核心引擎（见 §4）
│       ├── repository/   Spring Data JPA
│       ├── security/     JWT + Spring Security
│       ├── service/      GameService / RoomService / UserService / WechatService
│       └── websocket/    RoomWebSocketHandler + Topic 广播
│
├── ai-service/           ← Python FastAPI + LangChain
│   ├── agents/           Agent 基类 / Manager / 角色策略 / 记忆 / 推理
│   ├── services/         llm_service / rag_service
│   ├── routers/          agent_router / game / knowledge / health
│   ├── prompts/          ChatPromptTemplate
│   ├── knowledge/        knowledge/*.md（RAG 语料）
│   └── chroma_db/        本地 Chroma 持久化
│
├── ai-speech/            ← 可选语音服务（STT/TTS）
└── frontend/             ← Taro + React + TS（微信小程序为主，兼容 H5）
    └── src/
        ├── api/          REST 客户端封装
        ├── pages/        room / game-play / login ...
        ├── components/   播放器 / 玩家头像等
        ├── utils/        websocket / storage / request
        └── app.config.ts 小程序路由
```

---

## 4. 后端：分层与游戏引擎

### 4.1 调用链

```
HTTP / WS
   │
   v
Controller —— DTO 校验 + JWT 校验
   │
   v
Service (GameService / RoomService / ...)
   │
   ├──> ActionDispatcher.dispatch(action, ActionContext)
   │       │
   │       v
   │     GameActionHandler.validate() → execute()
   │       （写库、广播、推送 AI 事件、回调下一阶段）
   │
   ├──> PhaseScheduler  ── 阶段调度 / 自动推进
   │       │
   │       └──> AIPlayerBridge.requestAction / pushEvent
   │
   ├──> DiscussionManager ── 讨论顺序 + ReentrantLock + turn 序号
   ├──> VoteManager       ── VoteSession（带 CAS 幂等锁 tryMarkResolved）
   └──> NightActionStore  ── 夜间行动汇总与同守冲突结算
   │
   v
Repository → MySQL
```

### 4.2 game/ 包关键类

| 组件 | 职责 | 关键方法 |
|------|------|----------|
| `GameStateMachine` | 高层状态：waiting → night → day → checking → finished | `transition()` |
| `PhaseScheduler` | 阶段定时调度，触发夜晚 / 讨论 / 投票 / 结算；`PhaseContext` 含 `startedAt/endsAt` 绝对时间戳 | `scheduleNextPhase()` `executePhaseStart()` `executePhaseEnd()` `getActivePhase()` |
| `DiscussionManager` | 讨论阶段串行发言；`DiscussionContext` 持 `ReentrantLock` + `AtomicInteger turn` + `currentSpeakStartedAt/EndsAt` | `advanceNext(expectedTurn)` `getDiscussionSnapshot()` |
| `VoteManager` | 投票收集、平票处理、结算；`VoteSession.tryMarkResolved()` 防双结算 | `castVote()` `resolveVoting()` |
| `NightActionStore` | 夜间行动落地（含同守冲突解算） | `record()` `settle()` |
| `RoleAssigner` | 按 `GameConfig` 分配角色 | `assignRoles()` |
| `ActionDispatcher` | 按 `action` 字段路由到 8 个 Handler | `register()` `dispatch()` |
| `ActionContext` | 行动上下文 DI 容器，已注入 `aiPlayerBridge` | — |

### 4.3 ActionHandler 责任链

```
GameActionHandler (interface)
├── WerewolfKillHandler   action=kill   阶段=WEREWOLF
├── SeerCheckHandler      action=check  阶段=SEER     ← 推私有 SEER_RESULT 给 AI
├── WitchSaveHandler      action=save   阶段=WITCH    ← 推私有事件
├── WitchPoisonHandler    action=poison 阶段=WITCH    ← 推私有事件
├── GuardProtectHandler   action=guard  阶段=GUARD    ← 推私有事件
├── VoteHandler           action=vote   阶段=VOTING
├── HunterShootHandler    action=shoot  死亡触发
└── SkipHandler           action=skip   任意
```

新增行动只需 **两步**：实现接口 → 在 `ActionDispatcher.init()` 注册。`GameService` / `GameController` 无需改动。

### 4.4 GameService 关键能力

- `executeAction(gameId, userId, action, targetId)`：统一行动入口。
- `buildGameSnapshot(gameId)`：聚合阶段、剩余时间、当前发言人、存活玩家——前端断线重连用。
- `buildPhaseKey(phase, round)`：广播 `PHASE_CHANGE` 时携带的幂等 key。
- `advanceAfterCommit(...)`：通过 `TransactionSynchronizationManager.registerSynchronization` 在事务提交后才推进阶段，避免回滚仍广播。
- `broadcastDeathAnnounce(...)` / `resolveVoting(...)`：在广播给玩家的同时，调用 `AIPlayerBridge.pushPublicEventToAllAIs()` 同步给 AI。
- `AIPlayerBridge` / `DiscussionManager` 通过 `ApplicationContext` 惰性获取，规避循环依赖。

---

## 5. AI 服务：Agent 体系

### 5.1 模块结构

```
agents/
├── base_agent.py          WerewolfAgent 基类 (perceive/think/decide/speak)
├── agent_manager.py       game_id+player_id → Agent 实例池
├── memory/                三层记忆系统（见 ai-agent-memory-implementation.md）
│   ├── memory_system.py     工作 / 情景 / 语义
│   ├── episodic_memory.py
│   └── semantic_memory.py
├── reasoning/             贝叶斯推理 + 证据分析 + Chain of Thought
├── strategies/            6 种角色策略（Strategy Pattern）
├── planning/              ActionPlanner / SpeechGenerator（LCEL Chains）
└── persona/               人格档案
services/
├── llm_service.py         LangChain ChatOpenAI（兼容 deepseek/ollama）
└── rag_service.py         LangChain Chroma + Retriever + LCEL
```

### 5.2 Agent 生命周期

```
Java 后端 startGame()
   │  ┌──> POST /api/agents/create   逐个 AI Player 注册 Agent
   │  │     { game_id, player_id, role, persona, teammates, ... }
   │  v
   AI Service: AgentManager.create_agent() 写入 dict
   │
   游戏中：
     Java 各阶段 ──> POST /api/agents/{...}/act|speak|vote   决策请求（同步等回包）
     Java 关键事件 ──> POST /api/agents/event                 私有/公共事件推送（异步 fire-and-forget）
   │
   v
   Java endGame() ──> POST /api/agents/destroy  清理
```

### 5.3 LangChain 在项目中的角色

| 用途 | 使用的 LangChain 组件 |
|------|------------------------|
| LLM | `ChatOpenAI`（DeepSeek 兼容 endpoint） |
| Embedding | `OpenAIEmbeddings` / `HuggingFaceEmbeddings` |
| 向量库 | `Chroma`（持久化到 `./chroma_db`） |
| 检索 | `as_retriever(search_type="mmr")` |
| 文档加载/切片 | `DirectoryLoader` + `RecursiveCharacterTextSplitter` |
| Prompt 模板 | `ChatPromptTemplate` + `MessagesPlaceholder` |
| Memory | `ConversationSummaryBufferMemory` + 自定义结构化记忆 |
| 决策链 | LCEL（`prompt | llm | OutputParser`） |
| 输出解析 | `JsonOutputParser` / `PydanticOutputParser` |

> 注：原设计中 `services/llm_service.py` 使用裸 httpx 的方案已重构为 LangChain `ChatOpenAI`；OpenAI 兼容 `base_url` 指向 deepseek。

---

## 6. 前端：小程序 / Taro

```
src/
├── app.config.ts          页面注册（小程序路由表）
├── pages/
│   ├── login/             登录（账号密码 / 微信）
│   ├── lobby/             房间大厅
│   ├── room/              房间内（准备 / 增减 AI / 开始游戏）
│   └── game/play/         游戏主界面（核心）
├── components/            玩家卡片 / 系统消息 / 计时条
├── api/
│   ├── auth.ts  room.ts  game.ts   REST 封装（含 getGameSnapshot()）
│   └── request.ts                  fetch + 鉴权
└── utils/
    ├── websocket.ts       带自动重连 + onReconnected 回调
    └── storage.ts
```

### 6.1 game/play 关键状态

| 状态 | 含义 |
|------|------|
| `phaseEndsAt` | 当前阶段绝对结束时间（来自后端） |
| `speakEndsAt` | 当前发言绝对结束时间 |
| `serverClockOffsetRef` | 服务端时钟与本地时钟差，用于校准倒计时 |
| `lastPhaseKeyRef` | `phase + round` 幂等去重，避免 PHASE_CHANGE 重复触发 |
| `currentSpeakerIdRef` | 当前合法发言人；用于 PLAYER_CHAT 时校验，避免双绿色高亮 |

### 6.2 关键流程

- **倒计时**：本地 `setInterval(100ms)` 计算 `endsAt - (now + offset)`，断线重连后由 snapshot 重新校准。
- **断线恢复**：`websocket.setOnReconnected()` → 调用 `getGameSnapshot()` → `applySnapshot()` 全量覆盖 UI。
- **发言切换**：仅依赖后端 `SPEAKER_CHANGE`，不本地推算；`PLAYER_CHAT` 必须匹配 `currentSpeakerIdRef`，否则忽略。

---

## 7. 进程间协议（HTTP / WebSocket）

### 7.1 客户端 ↔ 后端 REST

| 组 | 方法 | 路径 |
|----|------|------|
| 认证 | POST | `/api/auth/{register,login,wx-login}` / GET `/api/auth/me` |
| 房间 | GET / POST | `/api/rooms` `/api/rooms/{code}` `.../{join,leave,ready,add-ai,remove-ai}` |
| 游戏 | POST | `/api/games/room/{roomId}/start` |
| 游戏 | POST | `/api/games/{gameId}/action` ← 统一行动入口 |
| 游戏 | GET | `/api/games/{gameId}` `/players` `/snapshot` `/logs` |

#### 统一行动入口示例

```json
POST /api/games/{gameId}/action
{ "action": "kill" | "check" | "save" | "poison" | "guard" | "vote" | "shoot" | "skip",
  "targetId": 3 }
```

### 7.2 客户端 ↔ 后端 WebSocket

`/ws/room/{roomCode}?token=<JWT>`

| 类型 | 方向 | 说明 |
|------|------|------|
| `GAME_START` | S→C | 携带玩家列表 |
| `ROLE_ASSIGN` | S→C 单播 | 私下通知角色 |
| `PHASE_CHANGE` | S→C | 含 `phase`, `round`, `startedAt`, `endsAt`, `phaseKey` |
| `SPEAKER_CHANGE` | S→C | 含 `currentSpeakerId`, `endsAt`, `turn` |
| `ACTION_CONFIRM` | S→C 单播 | 行动落库回执 |
| `SEER_RESULT` | S→C 单播 | 查验结果 |
| `DEATH_ANNOUNCE` | S→C | 公开死亡 |
| `VOTE_START` / `VOTE_RESULT` | S→C | 投票阶段 |
| `HUNTER_SHOOT` | S→C | 猎人开枪 |
| `GAME_OVER` | S→C | 终局公开身份 |
| `PLAYER_CHAT` | C↔S↔C | 发言 |
| `PLAYER_READY` | C↔S | 准备 |
| `HEARTBEAT` / `HEARTBEAT_ACK` | C↔S | 30s 心跳 |

### 7.3 后端 ↔ AI Service REST

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/agents/create` | 注册 Agent（含 role / persona / teammates） |
| POST | `/api/agents/destroy` | 销毁单局所有 Agent |
| POST | `/api/agents/event` | **事件推送**（私有 + 公开），见 §9 |
| POST | `/api/agents/action/night` | 夜间决策 |
| POST | `/api/agents/action/speak` | 发言生成 |
| POST | `/api/agents/action/vote` | 投票决策 |
| GET | `/api/agents/list` `/stats` | 调试 |

> 兼容旧路由：`/api/game/{speak,action,vote,think}` 仍保留，新逻辑统一收敛到 `/api/agents/*`。

---

## 8. 状态协同关键机制

下表为本轮重构（Phase A–D）落地的可靠性保障：

| 机制 | 实现位置 | 解决的问题 |
|------|----------|-----------|
| **绝对时间戳 + snapshot** | `PhaseContext.endsAt` / `GameController#snapshot` / 前端 `applySnapshot` | 客户端时钟漂移 / 倒计时不一致 / 断线丢失阶段 |
| **`phaseKey = phase+round` 幂等** | `GameService#buildPhaseKey`、前端 `lastPhaseKeyRef` | 重复 PHASE_CHANGE 导致 UI 抖动 |
| **`VoteSession.tryMarkResolved()` CAS** | `VoteManager` | 投票阶段超时 + 全员投票同时触发结算 |
| **`advanceAfterCommit` 事务回调** | `GameService` | DB 回滚但已经广播 / 推 AI |
| **讨论 `ReentrantLock + turn`** | `DiscussionManager#advanceNext(expectedTurn)` | 上一发言未结束就插入下一位（双绿色高亮） |
| **平安夜女巫自动 skip** | `PhaseScheduler#notifyWitchKillTarget` | 没死人时女巫卡住 |
| **WebSocket onReconnected 拉 snapshot** | `frontend/utils/websocket.ts` + `applySnapshot` | 断线后状态错乱 |
| **`currentSpeakerIdRef` 校验** | `frontend/pages/game/play` | PLAYER_CHAT 落到错误高亮 |
| **`AIPlayerBridge` 惰性获取** | `GameService` 通过 `ApplicationContext.getBean` | 循环依赖 |

---

## 9. AI 事件总线（Phase E）

> 解决：「预言家第一晚验了 1 号，第二天发言完全忘了」类的跨回合失忆。
> 思路：**统一事件总线**——所有关键事件都让 AI 知道，由 AI 服务的统一 `MemorySystem.update_on_event` 沉淀，无需对预言家做特例。

### 9.1 推送 API（后端 → AI）

`AIPlayerBridge` 暴露三个粒度：

| 方法 | 用途 | 典型场景 |
|------|------|---------|
| `pushPrivateEventToAI(gameId, targetAi, type, data)` | 单个 AI 私有事件 | 预言家查验结果、女巫救/毒结果、守卫保护对象 |
| `pushPublicEventToAllAIs(gameId, type, data)` | 全场 AI 公共事件 | 阶段切换、死亡公告、投票结果、玩家发言 |
| `pushEventToAIs(gameId, [AIs], type, data)` | 指定一组 AI | 狼人队友互通刀型 |

底层都走 `pushEventInternal()` → `POST /api/agents/event`，**异步、fire-and-forget**，失败仅日志。

### 9.2 当前已落地的推送点

| 触发位置 | 事件 | 范围 |
|----------|------|------|
| `SeerCheckHandler.execute` | `SEER_RESULT` { target, role } | 私有→预言家 |
| `WitchSaveHandler.execute` | `WITCH_SAVE` { target } | 私有→女巫 |
| `WitchPoisonHandler.execute` | `WITCH_POISON` { target } | 私有→女巫 |
| `GuardProtectHandler.execute` | `GUARD_PROTECT` { target } | 私有→守卫 |
| `PhaseScheduler.executePhaseStart` | `PHASE_CHANGE` { phase, round, endsAt } | 公共 |
| `GameService.broadcastDeathAnnounce` | `DEATH_ANNOUNCE` { dead[] } | 公共 |
| `GameService.resolveVoting` | `VOTE_RESULT` { eliminated } | 公共 |
| `RoomWebSocketHandler#PLAYER_CHAT` | `PLAYER_SPEECH` { speakerId, content } | 公共 |

### 9.3 AI 侧消费

`routers/agent_router.py#send_event` → `AgentManager.dispatch_event` → 对每个相关 Agent 调 `agent.perceive(event)`：

- 写入工作记忆 / 情景记忆 `EpisodeRecord`。
- 私有事件直接更新 `known_info`（如 `known_role[target] = "WEREWOLF"`）。
- 触发贝叶斯证据更新（如 SEER_RESULT 给目标 ×10 / ×0.05 似然比）。
- 下一轮 `plan_speech / plan_vote` 自动从 `MemorySystem.get_full_context()` 读到——**预言家由此能记得查杀历史**。

---

## 10. 数据存储

### 10.1 MySQL（6 张核心表）

| 表 | 内容 |
|----|------|
| `users` | 账号密码 + 微信 openid |
| `rooms` | 房间元数据 |
| `room_members` | 准备状态（JPA 自动建） |
| `games` | 游戏实例（mode / phase / round / winner） |
| `players` | 玩家（含 `is_ai` `ai_name` `seat_number` `role` `status`） |
| `game_logs` | 行动 + 事件日志（JSON） |

角色：`VILLAGER / WEREWOLF / SEER / WITCH / HUNTER / GUARD / IDIOT`
阶段：`NIGHT_START → GUARD → WEREWOLF → SEER → WITCH → DAY_START → DISCUSSION → VOTING → EXECUTION`

### 10.2 Redis

- WebSocket 心跳超时检测、Session 缓存。
- 不存游戏权威状态。

### 10.3 ChromaDB

- 仅 AI 服务侧本地持久化（`packages/ai-service/chroma_db/`）。
- 文档源：`packages/ai-service/knowledge/*.md`（8 份策略 / 规则 / 发言模式语料）。

---

## 11. 启停与运维

```bash
./start.sh start          # 一键起 基础设施 + Backend + AI Service
./start.sh restart        # 全量重启
./start.sh status         # 查看各端口状态
./start.sh backend        # 仅起后端
./start.sh ai             # 仅起 AI 服务
./start.sh logs backend   # 实时 tail 后端日志
```

日志：`logs/{backend,frontend,ai-service,ai-speech}.log`
PID：`.pids/*.pid`
基础设施模式：`.infra_mode`（local / docker，由 `setup.sh` 写入）。

---

## 12. 演进与已落地的修复

下面是从「初版设计」到「当前实现」的关键演进。

| 阶段 | 主题 | 关键改动 |
|------|------|---------|
| Phase A | 状态权威化 | `PhaseContext` 加 `startedAt/endsAt`；新增 `GET /api/games/{id}/snapshot`；前端 `applySnapshot()`；倒计时改用绝对时间戳 |
| Phase B | 投票幂等 | `VoteSession` 加 `AtomicBoolean resolved` + `tryMarkResolved()`；超时与全员投完竞争安全 |
| Phase C | 事务安全 | `GameService.advanceAfterCommit()` 用 `TransactionSynchronizationManager`，先 commit 再广播/推 AI |
| Phase D | 阶段一致性 | PHASE_CHANGE 携带 `phaseKey=phase+round`；前端 `lastPhaseKeyRef` 去重；删除 `votingResolved Set` 等死代码 |
| 讨论修复 | 双发言人 | `DiscussionContext` 加 `ReentrantLock` + `AtomicInteger turn`；`advanceNext(expectedTurn)` 校验防过期回调；前端 `currentSpeakerIdRef` 二次校验 |
| 平安夜 | 女巫卡顿 | `PhaseScheduler.notifyWitchKillTarget`：被杀目标为空则自动 skip |
| Phase E | AI 事件总线 | 新增 `AIPlayerBridge.pushPrivate/Public/EventToAIs` + 8 个推送点；AI 侧 `MemorySystem.update_on_event` 统一吸收，跨回合记忆补齐 |
| 兼容修复 | 循环依赖 | `GameService` 通过 `ApplicationContext` 惰性获取 `AIPlayerBridge` / `DiscussionManager` |
| 兼容修复 | AI 玩家 NPE | `SeerCheckHandler` 等处理对 `user == null`（AI 玩家）安全分支 |

---

## 13. 已知约束与待办

- **Agent 状态非持久**：进程重启后丢失；当前依赖 Backend 重新调用 `create_agent`，未实现热恢复（短局够用）。
- **AI 决策超时**：默认 fire-and-forget 推送 + 同步决策，AI 服务慢会拖累阶段；后续可在 `AIPlayerBridge` 加超时熔断。
- **多房间并发**：`DiscussionManager` 锁是 per-game `DiscussionContext`，无全局瓶颈；但 `AgentManager` dict 非线程安全，单实例下 GIL 兜底，未来横扩需要外部存储。
- **观察台 / 复盘**：`game_logs` 已存 JSON，但暂无前端 UI。
- **AI Speech**：默认未启动；仅在需要语音模式时手动 `./start.sh speech`。
- **测试覆盖**：`agents/tests/`、`backend/test` 仅有少量 Smoke，缺少状态机端到端用例。

---

> **文档维护原则**：架构有结构性变更（新模块 / 新协议 / 新状态机阶段）时必须同步本文件；纯实现细节请放到对应专项文档。
