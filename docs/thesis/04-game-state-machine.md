# 第四章 游戏状态机：狼人杀流程的确定性建模

> 毕业设计论文 · 核心技术章节之四
> 项目：基于大语言模型的 AI 狼人杀游戏系统
> 作者：veennyyang

---

## 4.1 引言：为什么要用状态机

### 4.1.1 狼人杀流程的复杂性
12 人板子一局游戏包含：
- **8~10 轮昼夜循环**
- **每轮 5~8 个阶段**（夜晚技能顺序、天亮结算、警长竞选、自由发言、投票、放逐、遗言）
- **12 种角色**的技能触发时机各异
- **多种中断**（自爆、猎人开枪、骑士决斗、女巫毒）

若用 `if-else` 堆砌处理所有分支，代码可读性会在 200 行后崩溃，且极易出现：
- **状态不一致**：前端以为夜晚、后端已进入白天
- **技能错过时机**：女巫救药 / 猎人开枪被吞
- **并发投票**：两个玩家同时投出死人

### 4.1.2 有限状态机（FSM）的价值
| 特性 | 意义 |
|------|------|
| **显式状态枚举** | 所有合法状态一目了然 |
| **转换条件白名单** | 非法跳转被守卫阻断 |
| **动作钩子** | 进入/离开状态时自动触发 |
| **可测试** | 每条转换可单元测试 |
| **可持久化** | 状态名+字段即完整游戏快照 |

---

## 4.2 整体状态层次

项目的状态机采用 **两级层次**：

```mermaid
graph TB
    subgraph "宏观游戏状态 GameStatus"
        GS1[WAITING<br/>等待玩家]
        GS2[STARTING<br/>分配角色]
        GS3[RUNNING<br/>游戏进行中]
        GS4[FINISHED<br/>游戏结束]
    end

    subgraph "微观回合阶段 GamePhase"
        P1[NIGHT_START 夜晚开始]
        P2[WEREWOLF_PHASE 狼人刀人]
        P3[SEER_PHASE 预言家查验]
        P4[WITCH_PHASE 女巫技能]
        P5[GUARD_PHASE 守卫技能]
        P6[DAY_START 天亮]
        P7[SHERIFF_ELECTION 警长竞选]
        P8[DISCUSSION 自由发言]
        P9[VOTE 投票]
        P10[EXECUTION 处决]
        P11[HUNTER_SHOOT 猎人开枪]
        P12[LAST_WORDS 遗言]
    end

    GS3 -.包含.-> P1
    GS3 -.包含.-> P12
```

---

## 4.3 宏观状态转换图

```mermaid
stateDiagram-v2
    [*] --> WAITING: 创建房间
    WAITING --> STARTING: 玩家 ≥ 6 且房主开始
    STARTING --> RUNNING: 角色分配完成 & 首夜开启
    RUNNING --> FINISHED: 胜负条件达成
    FINISHED --> WAITING: 房主重开
    WAITING --> [*]: 房间解散
    FINISHED --> [*]: 房间解散

    note right of STARTING
        一次性阶段
        仅用于角色分配
        立即进入 RUNNING
    end note

    note right of RUNNING
        内部包含完整
        昼夜循环子状态机
    end note
```

---

## 4.4 微观回合阶段图（重点）

```mermaid
stateDiagram-v2
    [*] --> NIGHT_START

    NIGHT_START --> WEREWOLF_PHASE: 倒计时到
    WEREWOLF_PHASE --> SEER_PHASE: 狼人完成刀人
    SEER_PHASE --> WITCH_PHASE: 预言家查验完成
    WITCH_PHASE --> GUARD_PHASE: 女巫决策完成
    GUARD_PHASE --> DAY_START: 守卫决策完成

    DAY_START --> CHECK_WIN_1: 公布死亡
    CHECK_WIN_1 --> FINISHED: 胜负分出
    CHECK_WIN_1 --> SHERIFF_ELECTION: 首日且无警长
    CHECK_WIN_1 --> DISCUSSION: 非首日或已有警长

    SHERIFF_ELECTION --> DISCUSSION: 警长产生
    DISCUSSION --> VOTE: 所有玩家发言完
    VOTE --> EXECUTION: 投票完成
    EXECUTION --> HUNTER_SHOOT: 出局者是猎人
    EXECUTION --> LAST_WORDS: 非猎人
    HUNTER_SHOOT --> LAST_WORDS: 开枪结算完
    LAST_WORDS --> CHECK_WIN_2

    CHECK_WIN_2 --> FINISHED: 胜负分出
    CHECK_WIN_2 --> NIGHT_START: 游戏继续

    FINISHED --> [*]
```

### 4.4.1 阶段说明表

| 阶段 | 时长 | 可见玩家 | 关键动作 |
|------|------|---------|---------|
| NIGHT_START | 即时 | 全部 | 播报夜晚开始 |
| WEREWOLF_PHASE | 30s | 狼人 | 选择击杀目标 |
| SEER_PHASE | 30s | 预言家 | 选择查验目标 |
| WITCH_PHASE | 30s | 女巫 | 救/毒/跳过 |
| GUARD_PHASE | 30s | 守卫 | 选择守护目标 |
| DAY_START | 即时 | 全部 | 公布死亡信息 |
| SHERIFF_ELECTION | 120s | 全部 | 上警+竞选演讲+投票 |
| DISCUSSION | 12×60s | 全部 | 轮流发言 |
| VOTE | 30s | 存活玩家 | 投票放逐 |
| EXECUTION | 即时 | 全部 | 公布出局者 |
| HUNTER_SHOOT | 30s | 猎人 | 选择开枪目标 |
| LAST_WORDS | 45s | 出局者 | 遗言 |

---

## 4.5 状态机核心类图

```mermaid
classDiagram
    class GameStateMachine {
        -stateHandlers: Map~String, StateHandler~
        -transitions: Map~String, Map~String, Transition~~
        +addTransition(from, to, cond, action) void
        +tryTransition(ctx, target) boolean
        +autoTransition(ctx) boolean
        +getStateName(status) String
    }

    class Transition {
        +targetState: String
        +condition: Predicate~Context~
        +action: Consumer~Context~
    }

    class Context {
        +game: Game
        +actor: Player
        +payload: Map
    }

    class StateHandler {
        <<interface>>
        +onEnter(ctx) void
        +onExit(ctx) void
        +onAction(ctx, action) void
    }

    class NightPhaseHandler {
        +onEnter(ctx) void
        +onExit(ctx) void
    }

    class DayPhaseHandler {
        +onEnter(ctx) void
    }

    class VotePhaseHandler {
        +onAction(ctx, vote) void
    }

    class PhaseScheduler {
        -executor: ScheduledExecutorService
        -timers: Map~String, ScheduledFuture~
        +scheduleTimeout(gameId, phase, delay) void
        +cancelTimer(gameId) void
    }

    class ActionDispatcher {
        -handlers: Map~ActionType, GameActionHandler~
        +dispatch(ctx, actionType) void
    }

    class GameActionHandler {
        <<interface>>
        +handle(ctx) void
        +actionType() ActionType
    }

    class WerewolfKillHandler {
        +handle(ctx) void
    }
    class SeerCheckHandler {
        +handle(ctx) void
    }
    class WitchSaveHandler {
        +handle(ctx) void
    }
    class WitchPoisonHandler {
        +handle(ctx) void
    }
    class GuardProtectHandler {
        +handle(ctx) void
    }
    class VoteHandler {
        +handle(ctx) void
    }
    class HunterShootHandler {
        +handle(ctx) void
    }

    GameStateMachine o-- Transition
    GameStateMachine ..> Context
    StateHandler <|.. NightPhaseHandler
    StateHandler <|.. DayPhaseHandler
    StateHandler <|.. VotePhaseHandler
    ActionDispatcher o-- GameActionHandler
    GameActionHandler <|.. WerewolfKillHandler
    GameActionHandler <|.. SeerCheckHandler
    GameActionHandler <|.. WitchSaveHandler
    GameActionHandler <|.. WitchPoisonHandler
    GameActionHandler <|.. GuardProtectHandler
    GameActionHandler <|.. VoteHandler
    GameActionHandler <|.. HunterShootHandler
    GameStateMachine ..> PhaseScheduler
    GameStateMachine ..> ActionDispatcher
```

---

## 4.6 核心转换时序：第 1 天完整流程

```mermaid
sequenceDiagram
    participant PS as PhaseScheduler
    participant FSM as GameStateMachine
    participant AD as ActionDispatcher
    participant WH as WerewolfKillHandler
    participant SH as SeerCheckHandler
    participant NS as NightActionStore
    participant WS as WebSocket

    Note over FSM: 当前 NIGHT_START
    FSM->>WS: broadcast("天黑请闭眼")
    PS->>FSM: 3s 后 transition → WEREWOLF_PHASE
    FSM->>WS: private("狼人请睁眼")
    FSM->>PS: scheduleTimeout(30s)

    Note over AD,NS: 狼人玩家投票
    AD->>WH: handle(kill_target=4)
    WH->>NS: record(WEREWOLF_KILL, 4)
    WH->>FSM: tryTransition(SEER_PHASE)
    FSM->>FSM: condition check OK
    FSM->>WS: broadcast("狼人请闭眼")

    Note over FSM: SEER_PHASE
    FSM->>WS: private("预言家请睁眼")
    AD->>SH: handle(check_target=3)
    SH->>NS: record(SEER_CHECK, 3, result=WOLF)
    SH->>FSM: tryTransition(WITCH_PHASE)

    Note over FSM: 依次 WITCH_PHASE → GUARD_PHASE → DAY_START
    FSM->>NS: resolveDeaths()
    NS-->>FSM: deaths = [4号]
    FSM->>WS: broadcast("天亮了，4号死亡")
    FSM->>FSM: checkWinCondition()
    FSM->>WS: broadcast("进入警长竞选")
```

---

## 4.7 状态一致性与守卫条件

### 4.7.1 Transition Guard（转换守卫）

每条转换必须通过 `Predicate<Context>` 守卫：

| From → To | 守卫条件 |
|----------|---------|
| WAITING → STARTING | `players >= 6` |
| NIGHT_START → WEREWOLF_PHASE | `timer_elapsed || all_ready` |
| WEREWOLF_PHASE → SEER_PHASE | `wolves_decided_all()` |
| SEER_PHASE → WITCH_PHASE | `seer_decided() || seer_dead()` |
| VOTE → EXECUTION | `all_alive_voted() || timeout` |
| CHECK_WIN → NIGHT_START | `winner == NONE` |
| CHECK_WIN → FINISHED | `winner != NONE` |

### 4.7.2 非法转换的处理

```mermaid
flowchart TD
    Req[请求 transition a→b] --> Exist{规则存在?}
    Exist -->|否| Log1[日志 WARN<br/>非法转换]
    Log1 --> Reject[返回 false]
    Exist -->|是| Check[执行 condition]
    Check --> OK{通过?}
    OK -->|否| Log2[日志 INFO<br/>守卫拒绝]
    Log2 --> Reject
    OK -->|是| Action[执行 action]
    Action --> UpdateDB[持久化状态]
    UpdateDB --> Broadcast[WS 广播]
    Broadcast --> Success[返回 true]
```

---

## 4.8 胜负条件判定

```mermaid
flowchart LR
    Trigger[每次死亡或放逐后触发] --> Count[统计存活阵营]
    Count --> W{狼=0?}
    W -->|是| Good[GOOD_WIN]
    W -->|否| G{好人=狼?}
    G -->|是| Wolf[WOLF_WIN<br/>屠城]
    G -->|否| N{神=0?}
    N -->|是且屠边局| Wolf2[WOLF_WIN<br/>屠神]
    N -->|否| None[NONE 继续]
    N -->|非屠边| M{民=0?}
    M -->|是| Wolf3[WOLF_WIN<br/>屠民]
    M -->|否| None
```

---

## 4.9 定时器与超时

### 4.9.1 双定时器设计

每个阶段同时存在两个定时器：
- **阶段总时长定时器**：到点强制推进
- **心跳定时器**：3s 一次广播剩余时间

```mermaid
sequenceDiagram
    participant FSM as StateMachine
    participant PS as PhaseScheduler
    participant WS as WebSocket

    FSM->>PS: enterPhase(DISCUSSION, 60s)
    PS->>PS: schedule timeout@60s
    loop 每 3s
        PS->>WS: broadcast("剩余 57s")
    end
    PS->>FSM: onTimeout → forceNext()
    FSM->>WS: broadcast("进入投票")
```

### 4.9.2 提前结束机制

若所有存活玩家都已行动，可跳过剩余等待：

```mermaid
flowchart TD
    Action[玩家提交行动] --> Record[写入 ActionStore]
    Record --> Check{全员完成?}
    Check -->|是| Cancel[取消 timeout]
    Cancel --> Next[立即推进状态]
    Check -->|否| Wait[继续等待]
```

---

## 4.10 状态持久化

### 4.10.1 持久化模型

```mermaid
erDiagram
    GAME ||--o{ PLAYER : has
    GAME ||--o{ ACTION_LOG : records
    GAME ||--o{ PHASE_LOG : records

    GAME {
        string id
        string status
        string current_phase
        int current_round
        string winner
        datetime created_at
    }

    PLAYER {
        string id
        int seat
        string role
        string alive
        int votes_received
    }

    ACTION_LOG {
        string id
        string action_type
        int actor
        int target
        json payload
        datetime ts
    }

    PHASE_LOG {
        string id
        string from_state
        string to_state
        datetime ts
    }
```

### 4.10.2 断线重连

玩家断线重连时的恢复流程：

```mermaid
sequenceDiagram
    participant C as Client
    participant WS as WebSocket
    participant FSM as StateMachine
    participant DB as Database

    C->>WS: reconnect(game_id, player_id)
    WS->>DB: loadGame(game_id)
    DB-->>WS: game + phase_logs[last 20]
    WS->>FSM: replayContext()
    WS-->>C: snapshot{phase, deaths, votes, my_role}
    Note over C: 客户端还原到当前状态
```

---

## 4.11 状态机的可测试性

每条 transition 都可以独立单元测试：

```java
@Test
void test_night_to_day_only_after_all_roles_acted() {
    Game game = buildGame(12);
    Context ctx = new Context(game);
    ctx.getGame().setCurrentPhase(WEREWOLF_PHASE);

    // 狼人未全部决策，应被拒绝
    assertFalse(fsm.tryTransition(ctx, "SEER_PHASE"));

    // 模拟所有狼人决策
    nightActionStore.recordAllWolvesVoted(game.getId(), 4);
    assertTrue(fsm.tryTransition(ctx, "SEER_PHASE"));
}
```

覆盖率目标：**所有 transition 必须有正反两组测试**。

---

## 4.12 本章小结

本章通过 **两级层次化有限状态机** 完整建模了狼人杀复杂的昼夜流程，结合守卫条件、定时器、行动分发器、动作日志四大机制，使后端具备：
- **确定性**：任何时刻只能处于一个合法状态
- **可审计性**：每次转换留痕
- **可恢复性**：断线可还原
- **可测试性**：每条转换可单测

下一章将讨论这个严密的状态机如何通过 WebSocket 与前端保持一致——即前后端通信的状态同步问题。
