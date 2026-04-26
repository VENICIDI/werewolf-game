# 狼人杀游戏状态机实现详解

> 本文档以**函数级粒度**描述本项目前后端游戏状态机的实际实现。
>
> - **后端**:Spring Boot,状态机分三层(实体枚举状态 / 配置驱动阶段调度 / 命令模式行动分发)
> - **前端**:Taro + React,基于 WebSocket 消息驱动的**响应式本地状态机**

---

## 目录

- [1. 总体架构](#1-总体架构)
- [2. 后端状态机](#2-后端状态机)
  - [2.1 状态模型(Game 实体枚举)](#21-状态模型game-实体枚举)
  - [2.2 GameStateMachine 宏观状态流转](#22-gamestatemachine-宏观状态流转)
  - [2.3 PhaseScheduler 阶段调度(核心)](#23-phasescheduler-阶段调度核心)
  - [2.4 GameService 阶段内业务编排](#24-gameservice-阶段内业务编排)
  - [2.5 行动分发(命令模式)](#25-行动分发命令模式)
  - [2.6 夜晚 / 投票 / 讨论 状态容器](#26-夜晚--投票--讨论-状态容器)
  - [2.7 AI 玩家与阶段机的耦合](#27-ai-玩家与阶段机的耦合)
- [3. 前端状态机](#3-前端状态机)
  - [3.1 状态数据源](#31-状态数据源)
  - [3.2 WebSocket 消息 → 状态更新](#32-websocket-消息--状态更新)
  - [3.3 阶段切换函数 `handlePhaseChange`](#33-阶段切换函数-handlephasechange)
  - [3.4 可行动判定与锁机制](#34-可行动判定与锁机制)
  - [3.5 发言状态机(讨论阶段)](#35-发言状态机讨论阶段)
  - [3.6 投票状态机](#36-投票状态机)
- [4. 完整时序示例:一轮夜晚 → 白天](#4-完整时序示例一轮夜晚--白天)
- [5. 关键设计要点](#5-关键设计要点)

---

## 1. 总体架构

```
┌────────────────────────────────────────────────────────────────────┐
│                         前端(Taro + React)                         │
│                                                                    │
│  game/play/index.tsx  ←——— WebSocket 消息驱动的本地状态机           │
│      ├─ game.status / game.phase / game.round   (UI 状态)          │
│      ├─ submittedPhaseKey  (行动锁 "phase:round")                  │
│      ├─ voteRecords / voteResult   (投票状态)                      │
│      ├─ currentSpeakerId / speakTimeLeft  (讨论发言状态)           │
│      └─ handlePhaseChange / handleVoteStart / handleVoteResult ... │
└────────────────────────────┬───────────────────────────────────────┘
                             │  HTTP  POST /api/games/{id}/action
                             │  WS    PHASE_CHANGE / VOTE_* / ...
                             │
┌────────────────────────────┴───────────────────────────────────────┐
│                          后端(Spring Boot)                         │
│                                                                    │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │  Layer A: 实体层                                              │  │
│  │  Game.GameStatus (PREPARING/RUNNING/PAUSED/FINISHED)          │  │
│  │  Game.GamePhase  (NIGHT_START/GUARD/WEREWOLF/SEER/WITCH/...)  │  │
│  │  Game.Winner     (NONE/WEREWOLF/VILLAGER/THIRD_PARTY)         │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                               ▲                                    │
│  ┌────────────────────────────┴─────────────────────────────────┐  │
│  │  Layer B: 状态机                                              │  │
│  │  GameStateMachine      宏观状态图 (waiting→night→day→...)     │  │
│  │  PhaseScheduler        配置驱动的阶段调度 (核心, 线程+定时器)  │  │
│  │  DiscussionManager     讨论阶段的发言轮转状态机                │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                               ▲                                    │
│  ┌────────────────────────────┴─────────────────────────────────┐  │
│  │  Layer C: 编排服务                                            │  │
│  │  GameService    executeAction / resolveNight / resolveVoting │  │
│  │                 checkWinCondition / endGame                  │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                               ▲                                    │
│  ┌────────────────────────────┴─────────────────────────────────┐  │
│  │  Layer D: 行动分发 (命令模式)                                 │  │
│  │  ActionDispatcher + GameActionHandler (kill/check/save/      │  │
│  │      poison/guard/vote/shoot/skip)                           │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                               ▲                                    │
│  ┌────────────────────────────┴─────────────────────────────────┐  │
│  │  Layer E: 状态容器(内存,按 gameId 隔离)                       │  │
│  │  NightActionStore      夜间行动收集                            │  │
│  │  VoteManager           投票会话                                │  │
│  └──────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────┘
                               ▲
                               │  HTTP
                               ▼
                    ┌────────────────────┐
                    │  Python AI Service │
                    │  AIPlayerBridge    │
                    └────────────────────┘
```

---

## 2. 后端状态机

后端**没有**单一的 "状态机对象" 串起所有逻辑,而是将"游戏状态"拆分成三个维度:

- **Game.GameStatus** — 游戏生命周期(准备/进行/暂停/结束)
- **Game.GamePhase** — 当前阶段(夜间分角色、白天讨论投票)
- **Game.Winner** — 胜负结果(None/狼人/村民/第三方)

状态的前进由 **PhaseScheduler**(配置驱动的阶段调度器)主导,**GameService** 负责阶段内的业务结算与广播。

### 2.1 状态模型(Game 实体枚举)

文件:`packages/backend/src/main/java/com/werewolf/entity/Game.java`

```java
public enum GameStatus {
    PREPARING,  // 准备中
    RUNNING,    // 进行中
    PAUSED,     // 暂停
    FINISHED    // 已结束
}

public enum GamePhase {
    NONE,
    NIGHT_START, GUARD, WEREWOLF, SEER, WITCH, HUNTER,  // 夜晚
    DAY_START, DISCUSSION, VOTING, EXECUTION            // 白天
}

public enum Winner {
    NONE, WEREWOLF, VILLAGER, THIRD_PARTY
}
```

阶段流程由 `packages/backend/src/main/resources/game-flow.json` **数据驱动**,`PhaseScheduler` 按 `flow.night` / `flow.day` 顺序执行。

### 2.2 GameStateMachine 宏观状态流转

文件:`packages/backend/src/main/java/com/werewolf/game/GameStateMachine.java`

这是一个**声明式条件流转表**,维护 `Map<from, Map<to, Transition>>`。

```
waiting ── (≥6 人) ────► starting
starting ── (强制) ─────► night
night   ── (phase=DAY_START) ────► day
day     ── (phase=EXECUTION) ────► checking
checking ── (Winner == NONE) ────► night         ← round + 1
checking ── (Winner != NONE) ────► finished
finished ── (强制) ──────► waiting               ← 重置
```

**核心函数**:

| 函数 | 作用 |
|---|---|
| `GameStateMachine()` / `initTransitions()` | 构造时注册所有 7 条 transition |
| `addTransition(from, to, condition, action)` | 注册一条流转,`Predicate<Context>` 判定 + `Consumer<Context>` 副作用 |
| `tryTransition(ctx, targetState)` | 查找当前状态 → 目标状态的规则,检查 `condition`,通过则执行 `action` |
| `getStateName(GameStatus)` | 枚举到状态字符串的映射 |
| `getAvailableTransitions(status)` | 返回当前状态的可达目标 |
| `registerHandler / handleState` | 为状态注册处理器(当前未在核心链路使用,预留扩展点) |

> **实际代码状况**:`GameStateMachine` 虽然被注入到 `GameService`,但在当前主流程中**并未真正驱动状态推进**——状态推进实际由 `PhaseScheduler` + `GameService` 直接操作 `Game.status` / `Game.currentPhase` 完成。`GameStateMachine` 是一个**规则文档化骨架**,方便后续扩展复杂状态流转(如暂停/断线恢复)。

### 2.3 PhaseScheduler 阶段调度(核心)

文件:`packages/backend/src/main/java/com/werewolf/game/PhaseScheduler.java`

这是**最核心的状态机**,使用 `ScheduledExecutorService` 配合阶段配置驱动阶段前进。

#### 2.3.1 关键字段

| 字段 | 含义 |
|---|---|
| `executor: ScheduledExecutorService` | 4 线程定时执行器,负责阶段倒计时和异步推进 |
| `gameTasks: Map<Long, ScheduledFuture<?>>` | 当前阶段的倒计时 future,可被 cancel 以提前推进 |
| `gameModes: Map<Long, GameMode>` | 每局游戏的模式配置缓存 |
| `activePhase: Map<Long, PhaseContext>` | 每局**当前**阶段上下文(阶段配置/索引/是否夜晚/advanced CAS 标志) |
| `votingResolved: Set<Long>` | 投票结算幂等锁 |

```java
private static class PhaseContext {
    final GameConfig.GameMode gameMode;
    final GameConfig.PhaseConfig phase;
    final int phaseIndex;
    final boolean isNight;
    final AtomicBoolean advanced = new AtomicBoolean(false); // CAS 防重推
}
```

#### 2.3.2 核心函数

| 函数 | 签名 | 作用 |
|---|---|---|
| `startGame` | `(gameId, gameModeId, players)` | 游戏启动入口:延迟 2s 后调用 AI 初始化 + `scheduleNextPhase(gameId, mode, 0, isNight=true)` |
| `scheduleNextPhase` | `(gameId, mode, phaseIndex, isNight)` | **阶段驱动核心**。从 `mode.flow.night/day` 取第 `phaseIndex` 个阶段,立即异步执行 `executePhaseStart`,并用 `executor.schedule` 在 `duration` ms 后触发 `executePhaseEnd` + 递归调度下一阶段 |
| `advanceIfAllActed` | `(gameId)` | **"全员提交即推进"入口**。由 `GameService.executeAction` 在每次提交后回调。检查 `isPhaseAllSubmitted`,CAS 抢占 `advanced`,取消倒计时 future,直接触发 `executePhaseEnd` + 推进 |
| `advanceDiscussionPhase` | `(gameId)` | **讨论阶段"所有人发完"推进**。由 `DiscussionManager` 在最后一位发言完后回调 |
| `executePhaseStart` | `(gameId, phase)` | 阶段开始:`mapToGamePhase` → `GameService.updatePhase` → 广播 `PHASE_CHANGE` → 触发 `AIPlayerBridge.scheduleAIActions` → 特殊阶段处理(voting/witch/discussion) |
| `executePhaseEnd` | `(gameId, phase)` | 阶段结束:广播 `SYSTEM` 结束词(如 "狼人请闭眼") |
| `needsPlayerAction` | `(phase)` | 该阶段是否需要玩家行动(GUARD/WEREWOLF/SEER/WITCH/DISCUSSION/VOTING)|
| `mapToGamePhase` | `(phaseName)` | JSON 字符串 → `GamePhase` 枚举映射 |
| `stopGame` / `pauseGame` / `resumeGame` | | 终止 / 暂停 / 恢复调度 |
| `resetVotingLock` | `(gameId)` | 新一轮投票前清幂等锁 |

#### 2.3.3 阶段推进分支

`scheduleNextPhase` 判定 `phaseIndex >= phases.size()` 时的两个分支:

```
if (isNight && phaseIndex 越界) {
    resolveNight(gameId) → checkWinCondition →
        未结束 → scheduleNextPhase(0, isNight=false)  // 进白天
}
if (!isNight && phaseIndex 越界) {
    checkWinCondition →
        未结束 → enterNewNight(gameId) → scheduleNextPhase(0, isNight=true)  // 回夜晚
}
```

#### 2.3.4 "倒计时 vs 全员提交" 双触发机制

每个阶段同时存在两个触发源,用 `PhaseContext.advanced` 的 **CAS** 保证只执行一次:

```
阶段开始
  │
  ├─ 定时器 future (duration ms 后触发)  ──┐
  │                                        ├─ compareAndSet(false, true) 抢占
  └─ advanceIfAllActed (外部回调触发)  ───┘
       │
       v
  谁抢到谁执行 executePhaseEnd + scheduleNextPhase
  另一方被跳过
```

### 2.4 GameService 阶段内业务编排

文件:`packages/backend/src/main/java/com/werewolf/service/GameService.java`

按功能区块组织,以下列出与状态机强相关的函数:

#### 2.4.1 游戏生命周期

| 函数 | 作用 | 状态变化 |
|---|---|---|
| `startGame(roomId, gameModeId)` | 创建 `Game` → 生成 `Player` → `roleAssigner.assignRoles` → 设置 `status=RUNNING, round=1, phase=NIGHT_START` → 广播 `GAME_START` + `ROLE_ASSIGN` → `phaseScheduler.startGame` | `PREPARING → RUNNING` |
| `createPlayers(game, roomId)` | 从 `RoomMember` 创建 `Player`,打乱座位,识别 AI(用户名以 `[AI]` 开头) | — |
| `endGame(gameId, winner)` | 设置 `status=FINISHED, winner`, 广播 `GAME_OVER`, 停止调度器,清理 `NightActionStore` / `VoteManager` | `RUNNING → FINISHED` |
| `enterNewNight(gameId)` | `round++, phase=NIGHT_START, nightActionStore.resetRound` | — |

#### 2.4.2 统一行动入口

```java
@Transactional
public Map<String, Object> executeAction(Long gameId, Long userId, String action, Long targetId)
```

流程:

1. 查 `Game`、`Player`
2. 死亡校验(`shoot` 例外)
3. 重复提交校验(读 `NightActionStore.isActionSubmitted(phase_playerId)`)
4. 猎人一次性校验(`hunterShot: Set<Long>`)
5. 构建 `ActionContext` → `actionDispatcher.dispatch(action, context)`
6. 标记已提交 `nightActions.markActionSubmitted(phaseKey)`
7. 持久化 `GameLog`
8. 发送 `ACTION_CONFIRM` WebSocket 给操作者(check 除外,它已发 `SEER_RESULT`)
9. 若是 `vote` 且全员已投 → `resolveVoting`
10. 若是 `shoot` → `checkWinCondition`
11. **`phaseScheduler.advanceIfAllActed(gameId)` ← 关键钩子,驱动阶段提前推进**

> 此方法是 HTTP `POST /api/games/{gameId}/action` 的处理入口。
> 对应 AI 的入口 `executeAIAction(gameId, playerId, action, targetId)` 逻辑一致,仅 ID 类型不同、不发 `ACTION_CONFIRM`。

#### 2.4.3 夜晚结算

`resolveNight(gameId)`:
1. 读 `NightActionStore.RoundActions` → 调 `resolveNight()` 计算死亡名单
2. 更新 `Player.status=DEAD, canSpeak=false, canVote=false`,写 `GameLog`
3. 广播 `DEATH_ANNOUNCE`
4. 对死亡的猎人若非被毒,调 `notifyHunterCanShoot`

`notifyWitchKillTarget(gameId)` — 在女巫阶段开始时,把被杀目标信息通过 `WITCH_INFO` 单播给女巫。

#### 2.4.4 投票流程

| 函数 | 作用 |
|---|---|
| `startVoting(gameId)` | 查存活且有投票权的玩家 → `voteManager.startVote(voterIds)` → 清 `votingResolved` / `resetVotingLock` → 广播 `VOTE_START(candidates, voterIds)` |
| `resolveVoting(gameId)` | `voteManager.getVoteSession.resolve()` 得到 `VoteResult` → 更新被放逐玩家状态 → 广播 `VOTE_RESULT(voteDetails, votesByVoter, eliminatedPlayerId, isTie)` → 若被放逐是猎人 → `notifyHunterCanShoot` → `voteManager.clearVote` → `checkWinCondition` |

#### 2.4.5 胜负判定

`checkWinCondition(gameId)`:

```java
long werewolfCount = 存活狼人数
long villagerSide  = 存活好人阵营数
long godCount      = 存活神职数
long civilianCount = villagerSide - godCount

if (werewolfCount == 0)                  endGame(VILLAGER)
else if (godCount == 0 || civilian <= 0) endGame(WEREWOLF)   // 屠神 or 屠民
else if (werewolfCount >= villagerSide)  endGame(WEREWOLF)   // 狼人数 ≥ 好人
```

#### 2.4.6 "全员提交"判定

| 函数 | 作用 |
|---|---|
| `isNightRoleActionPhase(phase)` | 是否是 WEREWOLF/SEER/WITCH/GUARD |
| `isRolePhaseActor(player, phase)` | 玩家角色是否与该夜间阶段匹配 |
| `getExpectedActors(gameId, phase)` | 该阶段应行动者列表(夜间角色 / 投票阶段所有投票权者) |
| `isPhaseAllSubmitted(gameId)` | **核心判定**。夜间阶段:遍历 `expected`,每个都 `nightActions.isActionSubmitted(phase_playerId)`;投票阶段:`voteManager.isAllVoted` 或 session 已 clear |

`PhaseScheduler.advanceIfAllActed` 调用此函数决定是否提前推进。

### 2.5 行动分发(命令模式)

文件:`packages/backend/src/main/java/com/werewolf/game/action/`

```
GameActionHandler (接口)
    ├─ String getAction()           处理器对应的 action 字符串
    ├─ void validate(ActionContext) 合法性校验(抛 RuntimeException)
    └─ Map<String, Object> execute(ActionContext) 执行并返回结果

实现类(8 种):
  WerewolfKillHandler   "kill"    狼人击杀 → nightActions.addWerewolfVote
  SeerCheckHandler      "check"   预言家查验 → 直接回 SEER_RESULT
  WitchSaveHandler      "save"    女巫救人 → witchSaveTarget, witchSaveUsed=true
  WitchPoisonHandler    "poison"  女巫毒人 → witchPoisonTarget, witchPoisonUsed=true
  GuardProtectHandler   "guard"   守卫守护 → guardProtectTarget(校验连守)
  VoteHandler           "vote"    投票 → voteSession.submitVote + 广播 VOTE_UPDATE
  HunterShootHandler    "shoot"   猎人开枪 → 直接击杀目标
  SkipHandler           "skip"    跳过(仅标记已提交)
```

`ActionDispatcher`(`@Component`):

- `@PostConstruct init()` 构造时注册 8 种 handler 到 `handlerRegistry: Map<action, handler>`
- `dispatch(action, context)` = `getHandler(action)` → `validate` → `execute`
- `isSupported(action)` — 检查 action 是否已注册

**校验示例**(`WerewolfKillHandler.validate`):
- 必须是 `WEREWOLF` 角色
- 当前阶段必须是 `Game.GamePhase.WEREWOLF`
- 目标必须存在 + 存活 + 不是自己 + 不是队友

### 2.6 夜晚 / 投票 / 讨论 状态容器

#### 2.6.1 NightActionStore(内存,按 gameId 隔离)

文件:`packages/backend/src/main/java/com/werewolf/game/NightActionStore.java`

核心数据结构 `RoundActions`:

| 字段 | 说明 |
|---|---|
| `werewolfKillTarget: Long` | 本夜狼人选定的击杀目标(多狼协商后统计) |
| `werewolfVotes: Map<wolfId, targetId>` | 每个狼的投票;`addWerewolfVote` 写入后调 `resolveWerewolfKill` 取票数最多者 |
| `seerCheckTarget: Long` | 预言家查验目标 |
| `witchSaveTarget/PoisonTarget: Long` | 女巫本夜救/毒目标 |
| `witchSaveUsed/PoisonUsed: boolean` | **跨回合**持久(整局一次) |
| `guardProtectTarget: Long` | 本夜守卫目标 |
| `guardLastProtectTarget: Long` | 上一夜目标(用于不能连守判定) |
| `submittedActions: Set<String>` | 已提交的 `phase_playerId` 集合 |

关键函数:

- `reset()` — 新一夜清空本夜数据,**保留**女巫药品状态与守卫上夜目标
- `resolveNight()` — 结算死亡名单。规则:
  - 守卫 + 女巫同时保护同一人 → 同守冲突,仍死亡
  - 只有一方保护 → 存活
  - 都未保护 → 死亡
  - 女巫毒人额外死亡(不重复)

#### 2.6.2 VoteManager

文件:`packages/backend/src/main/java/com/werewolf/game/VoteManager.java`

```
VoteManager (@Component)
  └─ voteSessions: Map<gameId, VoteSession>

VoteSession
  ├─ eligibleVoterIds: Set<Long>              有投票权的玩家
  ├─ votes: Map<voterId, targetId>            0 表示弃票
  ├─ submitVote(voterId, targetId): boolean   重复投票返回 false
  ├─ isAllVoted()                             全员已投
  ├─ snapshotVotes()                          当前投票快照(给 VOTE_UPDATE 广播)
  └─ resolve() → VoteResult                   统计最高票,检测平票
```

#### 2.6.3 DiscussionManager — 讨论阶段发言轮转状态机

文件:`packages/backend/src/main/java/com/werewolf/game/DiscussionManager.java`

**讨论阶段内嵌的发言轮转状态机**,独立于 `PhaseScheduler`:

```
DiscussionContext
  ├─ speakOrder: List<Long>         按座位排序的存活玩家
  ├─ currentIndex: int              当前发言人索引
  ├─ speakTimeMs: int               每人发言时长(默认 30s)
  ├─ timeoutFuture: ScheduledFuture 当前发言人的倒计时
  └─ finished: AtomicBoolean        CAS 防重完成标记
```

核心函数:

| 函数 | 作用 |
|---|---|
| `startDiscussion(gameId, speakTimeMs)` | `PhaseScheduler` 在 DISCUSSION 阶段启动时调用。建立 `DiscussionContext`,`moveToSpeaker(ctx, 0)` |
| `moveToSpeaker(ctx, index)` | 核心推进函数。`index >= 人数` → `advanceDiscussionPhase(gameId)` 结束讨论;否则设置 `canSpeak`,广播 `SPEAKER_CHANGE`,按真人 / AI 不同处理 |
| `advanceNext(gameId, currentSpeakerId, reason)` | 外部推进入口。reason: `skip_speech` / `timeout` / `ai_finished` / `ai_failed` / `ai_timeout` |
| `handleAISpeaker(ctx, aiSpeaker)` | 异步调 `AIPlayerBridge.executeAISpeechAndAdvance`,生成完成回调 `advanceNext` |
| `onHumanChat(gameId, speakerId, content)` | 真人在本人发言时段多次聊天时调用,**不切换**,仅把 `PLAYER_SPEECH` 推给所有 AI 记忆 |
| `stopDiscussion(gameId)` | 停止当前讨论上下文并取消 future |

### 2.7 AI 玩家与阶段机的耦合

文件:`packages/backend/src/main/java/com/werewolf/ai/AIPlayerBridge.java`

| 函数 | 作用 | 调用时机 |
|---|---|---|
| `initializeAgents(gameId, allPlayers)` | 为所有 AI 玩家在 Python 侧创建 Agent | `PhaseScheduler.startGame` |
| `scheduleAIActions(gameId, phase)` | 为当前阶段调度所有 AI 玩家的决策(夜间角色 / 投票) | `PhaseScheduler.executePhaseStart` |
| `executeAISpeechAndAdvance(gameId, aiPlayer, onFinished)` | 异步调 Python 生成发言 → 广播 PLAYER_CHAT → 回调推进 | `DiscussionManager.handleAISpeaker` |
| `pushSpeechEventToAllAIs(gameId, speakerId, content)` | 把发言事件推给所有 AI Agent 更新记忆 | `DiscussionManager.onHumanChat` / AI 发言后 |

AI 决策返回后,统一走 `GameService.executeAIAction` 入口,与真人行动走同一分发器,状态演进路径一致。

---

## 3. 前端状态机

文件:`packages/frontend/src/pages/game/play/index.tsx`

前端是一个**典型的 React + WebSocket 本地状态机**:所有状态都是 React state 或 ref,变化由 WebSocket 消息触发,派生出"能否行动"、"是否锁定"、"显示哪个面板"等 UI 决策。

### 3.1 状态数据源

| State / Ref | 类型 | 用途 |
|---|---|---|
| `game: GameData` | `{ gameId, roomCode, status, round, phase, players, myPlayerId, myRole, mySeat, teammates }` | 游戏主状态(由 HTTP `fetchGameData` 初始化,WS 增量更新) |
| `phaseTimeLeft` | `number` | 阶段总倒计时(由 `PHASE_CHANGE.duration` 初始化) |
| `speakTimeLeft` | `number` | 当前发言人剩余时间(由 `SPEAKER_CHANGE.speakTimeMs` 初始化) |
| `currentSpeakerId` | `number \| null` | 讨论阶段当前发言人 ID |
| `selectedTarget` | `number \| null` | 已选中的目标玩家 ID |
| `witchInfo / witchAction` | `{hasSave, hasPoison, killTargetId, ...}` / `'none' \| 'save' \| 'poison'` | 女巫专用 UI 状态 |
| `canHunterShoot` | `boolean` | 猎人可以开枪(HUNTER_SHOOT 消息触发) |
| `voteRecords` | `Record<number, number>` | 实时投票快照 `voterId → targetId(0=弃票)` |
| `voteProgress` | `{voted, total}` | 投票进度条 |
| `voteResult` | `{votesByVoter, voteDetails, isTie, eliminatedPlayerId, ...}` | 最终投票结果(EXECUTION 阶段) |
| **`submittedPhaseKey: string \| null`** | state | 已提交行动的 key `"{phase}:{round}"` |
| **`submittedPhaseKeyRef: RefObject<string>`** | ref | 同 key,**同步读写**防止 setState 异步窗口 |
| **`submittingRef: RefObject<boolean>`** | ref | 请求进行中锁,防止双击 |
| `chatMessages` | `ChatMessage[]` | 聊天列表 |

### 3.2 WebSocket 消息 → 状态更新

`connectWebSocket(roomCode)` 中订阅了 16 种消息,每种对应一个 `useCallback` handler:

| 消息类型 | Handler | 主要作用 |
|---|---|---|
| `PHASE_CHANGE` | `handlePhaseChange` | 更新 `game.phase / round`,重置倒计时、选中目标、锁 |
| `PHASE_ADVANCE` | `handlePhaseAdvance` | 显示 Toast "全员已行动" |
| `PLAYER_CHAT` | `handlePlayerChat` | 追加聊天消息,设置高亮 3s |
| `SPEAKER_CHANGE` | `handleSpeakerChange` | 更新 `currentSpeakerId`,启动 `speakTimeLeft` 倒计时 |
| `DEATH_ANNOUNCE` | `handleDeathAnnounce` | 把死亡玩家 status 置为 DEAD |
| `ROLE_ASSIGN` | `handleRoleAssign` | 保存自己的角色 / 狼队友 |
| `VOTE_START` | `handleVoteStart` | 清空上轮投票状态,初始化进度条 |
| `VOTE_UPDATE` | `handleVoteUpdate` | 更新 `voteRecords / voteProgress` 快照 |
| `VOTE_RESULT` | `handleVoteResult` | 更新 `voteResult`,被放逐玩家置 DEAD |
| `ACTION_CONFIRM` | `handleActionConfirm` | **加锁**:`submittedPhaseKeyRef.current = "phase:round"` |
| `SEER_RESULT` | `handleSeerResult` | 弹 Modal 显示查验结果 |
| `WITCH_INFO` | `handleWitchInfo` | 填充 `witchInfo`(含被杀目标信息) |
| `HUNTER_SHOOT` | `handleHunterShoot` | 触发猎人开枪 UI 或广播射杀结果 |
| `GAME_OVER` | `handleGameOver` | Modal 显示结果后返回房间列表 |
| `SYSTEM` | `handleSystemMessage` | Toast 显示系统文字 |
| `ERROR` | `handleWsError` | Toast 显示错误 |

### 3.3 阶段切换函数 `handlePhaseChange`

```ts
const handlePhaseChange = useCallback((message) => {
  const data = message.data
  // ① 更新游戏主状态
  setGame(prev => ({ ...prev, phase: data.phase, round: data.round }))
  // ② 启动阶段倒计时
  setPhaseTimeLeft(Math.floor(data.duration / 1000))
  // ③ 重置所有阶段内状态
  setSelectedTarget(null)
  setSpeakingPlayerIds(new Set())
  setWitchAction('none')
  // ④ 清除上一阶段的行动锁
  setSubmittedPhaseKey(null)
  setSubmittedSummary('')
  submittedPhaseKeyRef.current = null
  submittingRef.current = false
  // ⑤ 非讨论阶段清理发言人
  if (data.phase !== 'DISCUSSION') setCurrentSpeakerId(null)
  // ⑥ 非女巫阶段清 witchInfo
  if (data.phase !== 'WITCH') setWitchInfo(null)
  // ⑦ 非投票/处决清投票状态
  if (data.phase !== 'VOTING' && data.phase !== 'EXECUTION') {
    setVoteRecords({}); setVoteProgress({...}); setVoteResult(null)
  }
  // ⑧ Toast 阶段提示
  if (data.message) Taro.showToast({ title: data.message, ... })
}, [])
```

### 3.4 可行动判定与锁机制

```ts
canAct(): boolean
  ├─ 猎人开枪优先(死后仍可)
  ├─ 活着 && !isActionLocked()
  └─ 阶段 × 角色匹配:
       WEREWOLF ↔ myRole=WEREWOLF
       SEER     ↔ myRole=SEER
       WITCH    ↔ myRole=WITCH
       GUARD    ↔ myRole=GUARD
       VOTING   ↔ 任何人

isActionLocked(): boolean
  const expected = `${game.phase}:${game.round}`
  const lockedKey = submittedPhaseKeyRef.current || submittedPhaseKey
  return lockedKey === expected

markSubmitted(summary):                    ← 本地立即加锁(不等 WS)
  submittedPhaseKeyRef.current = `${phase}:${round}`
  setSubmittedPhaseKey(...)
  setSubmittedSummary(summary)
```

**双轨加锁**:

- **HTTP 成功后立即** `markSubmitted`(同步 ref,UI 立即锁住)
- **WS `ACTION_CONFIRM`** 兜底再锁一次(避免极端情况下 markSubmitted 未执行)
- **阶段切换 `PHASE_CHANGE`** 清锁

行动提交函数:

| 函数 | 作用 |
|---|---|
| `handleAction()` | 通用行动提交(kill/check/guard/vote/shoot)。若 action 是 save/poison 走专用 |
| `handleWitchSave()` | 女巫救人。`Taro.showModal` 二次确认 → POST → `markSubmitted` |
| `handleWitchPoison()` | 女巫毒人。同上 |
| `handleWitchSkip()` | 女巫跳过。同上 |
| `handleSkip()` | 普通跳过 |

每个函数都采用 `submittingRef.current` 防抖 + `isActionLocked()` 检查,三个守卫条件:

```ts
if (isActionLocked() || submittingRef.current) return   // 状态拦截
if (!selectedTarget && needsTarget) return              // 目标拦截
submittingRef.current = true                            // 进入锁
try { await post(...); markSubmitted(...) }
finally { submittingRef.current = false }
```

### 3.5 发言状态机(讨论阶段)

独立倒计时 `speakTimeLeft` 由 `SPEAKER_CHANGE.speakTimeMs` 启动:

```ts
// 倒计时 effect
useEffect(() => {
  if (speakTimeLeft <= 0) return
  const timer = setInterval(() => {
    setSpeakTimeLeft(prev => Math.max(0, prev - 1))
  }, 1000)
  return () => clearInterval(timer)
}, [speakTimeLeft])

canChat(): phase === 'DISCUSSION' && currentSpeakerId === myPlayerId && alive

handleSendChat()      → wsManager.sendChat(content)    // WS: PLAYER_CHAT
handleSkipSpeech()    → wsManager.sendSkipSpeech()     // WS: SKIP_SPEECH, 触发后端 advanceNext
```

后端 `SPEAKER_CHANGE` 广播驱动前端发言人切换,前端的 `handleSpeakerChange` 更新 `currentSpeakerId` + 每个玩家的 `canSpeak` 字段。

### 3.6 投票状态机

三阶段:

```
VOTE_START          handleVoteStart
  ├─ 清空 voteRecords / voteResult
  ├─ 解锁 submittedPhaseKey
  └─ 初始化 voteProgress = { voted: 0, total: eligibleCount }

VOTE_UPDATE  (每次有人投票)   handleVoteUpdate
  ├─ 更新 voteRecords = { voterId → targetId }
  └─ 更新 voteProgress

VOTE_RESULT  (结算)    handleVoteResult
  ├─ 更新 voteResult = { votesByVoter, voteDetails, isTie, eliminatedPlayerId, ... }
  ├─ 被放逐玩家 status = DEAD
  └─ Toast 结果消息
```

UI 根据 `game.phase === 'VOTING'` / `'EXECUTION'` + `voteResult` 有无,渲染实时快照 / 最终处决面板。

---

## 4. 完整时序示例:一轮夜晚 → 白天

```
 ┌── 后端 ──────────────────────────────────────┐   ┌── 前端 ──────────────┐
 │ GameService.startGame                        │   │                      │
 │   ├─ status=RUNNING, round=1                 │   │                      │
 │   ├─ phase=NIGHT_START                       │   │                      │
 │   ├─ 广播 GAME_START + ROLE_ASSIGN           │──▶│ handleRoleAssign     │
 │   └─ phaseScheduler.startGame                │   │ → 弹角色 Modal       │
 │                                              │   │                      │
 │ PhaseScheduler.scheduleNextPhase(0, true)    │   │                      │
 │   executePhaseStart(NIGHT_START)             │   │                      │
 │     ├─ updatePhase(NIGHT_START)              │   │                      │
 │     └─ 广播 PHASE_CHANGE(duration=3000)      │──▶│ handlePhaseChange    │
 │                                              │   │ → phase=NIGHT_START  │
 │   [3s 后] executePhaseEnd                    │   │ → phaseTimeLeft=3    │
 │                                              │   │                      │
 │ scheduleNextPhase(1, true)                   │   │                      │
 │   executePhaseStart(WEREWOLF_ACTION)         │   │                      │
 │     ├─ updatePhase(WEREWOLF)                 │   │                      │
 │     ├─ 广播 PHASE_CHANGE(duration=30000)     │──▶│ handlePhaseChange    │
 │     └─ aiPlayerBridge.scheduleAIActions      │   │ → phase=WEREWOLF     │
 │                                              │   │ → 清锁/清目标         │
 │                                              │   │                      │
 │                                              │   │ [狼人玩家点击目标]    │
 │                                              │◀──│ POST /action kill    │
 │ executeAction(userId, kill, targetId)        │   │                      │
 │   ├─ actionDispatcher.dispatch("kill")       │   │                      │
 │   │   → nightActions.addWerewolfVote         │   │                      │
 │   ├─ nightActions.markSubmitted(WEREWOLF_id) │   │                      │
 │   ├─ 广播 ACTION_CONFIRM                     │──▶│ handleActionConfirm  │
 │   └─ phaseScheduler.advanceIfAllActed        │   │ → 加锁               │
 │       └─ isPhaseAllSubmitted=true            │   │                      │
 │           ├─ ctx.advanced.compareAndSet      │   │                      │
 │           ├─ 取消倒计时 future               │   │                      │
 │           ├─ 广播 PHASE_ADVANCE              │──▶│ handlePhaseAdvance   │
 │           └─ executePhaseEnd + 推进下一阶段  │   │                      │
 │                                              │   │                      │
 │ ... 预言家阶段 / 女巫阶段 ...                │   │                      │
 │                                              │   │                      │
 │ [夜晚阶段全部结束] resolveNight              │   │                      │
 │   ├─ 计算死亡名单                            │   │                      │
 │   ├─ 广播 DEATH_ANNOUNCE                     │──▶│ handleDeathAnnounce  │
 │   ├─ checkWinCondition (未结束)              │   │                      │
 │   └─ scheduleNextPhase(0, isNight=false)     │   │                      │
 │                                              │   │                      │
 │ ... 天亮 / 讨论(DiscussionManager) / 投票 ...│   │                      │
 │                                              │   │                      │
 │ resolveVoting                                │   │                      │
 │   ├─ session.resolve()                       │   │                      │
 │   ├─ 被放逐 status=DEAD                      │   │                      │
 │   ├─ 广播 VOTE_RESULT                        │──▶│ handleVoteResult     │
 │   └─ checkWinCondition                       │   │                      │
 │       ├─ 未结束 → enterNewNight → round++    │   │                      │
 │       └─ 已结束 → endGame → 广播 GAME_OVER   │──▶│ handleGameOver       │
 └──────────────────────────────────────────────┘   └──────────────────────┘
```

---

## 5. 关键设计要点

### 5.1 配置驱动 vs 硬编码

- `game-flow.json` 声明每个游戏模式的阶段顺序和时长,`PhaseScheduler` 按索引驱动 → **新增阶段/模式无需改代码**
- `ActionDispatcher` 动态注册 handler → **新增行动只需实现 `GameActionHandler` 接口**

### 5.2 "倒计时 vs 全员提交" 双驱动

- 每个阶段同时由两个触发源推进,以 `PhaseContext.advanced: AtomicBoolean` 的 CAS 抢占保证只执行一次
- 好处:真人可以等待倒计时,AI 全员提交即快速推进,减少无效等待

### 5.3 幂等锁

后端关键结算点都有幂等保护:

- `votingResolved: Set<Long>`(PhaseScheduler + GameService 双重)
- `hunterShot: Set<Long>`(猎人一次性)
- `PhaseContext.advanced.compareAndSet(false, true)`(阶段推进一次)
- `DiscussionContext.finished.compareAndSet(false, true)`(讨论结束一次)

### 5.4 前端双轨锁

- `submittedPhaseKey` (state) + `submittedPhaseKeyRef` (ref) 组合,既驱动 UI 更新,又提供事件处理中的同步读取
- 锁 key 带 `round`,自动跨回合失效,无需手动清理

### 5.5 AI 与真人行为一致

- 真人 → `executeAction(gameId, userId, action, targetId)`
- AI   → `executeAIAction(gameId, playerId, action, targetId)`
- 两者内部走同一 `ActionDispatcher`,状态演进路径一致,`advanceIfAllActed` 对两者都生效

### 5.6 发言轮转独立状态机

- `DiscussionManager` 在 DISCUSSION 阶段内部再维护一层发言人轮转状态机
- 每人独立倒计时 + 真人点"结束发言"/AI 完成回调 → `advanceNext` 推进
- 最后一人发完 → 主动回调 `PhaseScheduler.advanceDiscussionPhase` 结束讨论阶段

---

## 附录:关键文件速查

| 模块 | 文件 |
|---|---|
| **后端** | |
| 实体状态枚举 | `packages/backend/src/main/java/com/werewolf/entity/Game.java` |
| 宏观状态机(骨架) | `packages/backend/src/main/java/com/werewolf/game/GameStateMachine.java` |
| **阶段调度核心** | `packages/backend/src/main/java/com/werewolf/game/PhaseScheduler.java` |
| 业务编排 | `packages/backend/src/main/java/com/werewolf/service/GameService.java` |
| 行动分发 | `packages/backend/src/main/java/com/werewolf/game/action/ActionDispatcher.java` |
| 行动接口 | `packages/backend/src/main/java/com/werewolf/game/action/GameActionHandler.java` |
| 夜间状态容器 | `packages/backend/src/main/java/com/werewolf/game/NightActionStore.java` |
| 投票状态容器 | `packages/backend/src/main/java/com/werewolf/game/VoteManager.java` |
| 讨论状态机 | `packages/backend/src/main/java/com/werewolf/game/DiscussionManager.java` |
| AI 集成 | `packages/backend/src/main/java/com/werewolf/ai/AIPlayerBridge.java` |
| 阶段配置 | `packages/backend/src/main/resources/game-flow.json` |
| WS 消息常量 | `packages/backend/src/main/java/com/werewolf/websocket/WebSocketMessage.java` |
| **前端** | |
| 游戏主页面 | `packages/frontend/src/pages/game/play/index.tsx` |
| WebSocket 单例 | `packages/frontend/src/utils/websocket.ts` |
| 游戏 API | `packages/frontend/src/api/game.ts` |
| 房间页面 | `packages/frontend/src/pages/room/index.tsx` |
