# AI Agent 记忆系统实现说明

> 本文档描述本项目 AI Agent **实际落地**的记忆系统实现,对应源码目录:
> `packages/ai-service/agents/memory/`
>
> 与 [`ai-agent-design.md`](./ai-agent-design.md) 设计文档的差异也在文末列出。

---

## 目录

- [1. 模块结构](#1-模块结构)
- [2. 三层记忆架构](#2-三层记忆架构)
- [3. 核心入口:MemorySystem](#3-核心入口memorysystem)
- [4. 事件分发流程](#4-事件分发流程)
- [5. 与 Agent / 推理引擎的耦合](#5-与-agent--推理引擎的耦合)
- [6. 对 Prompt 的输出](#6-对-prompt-的输出)
- [7. 与设计文档的差异](#7-与设计文档的差异)

---

## 1. 模块结构

```
packages/ai-service/agents/memory/
├── __init__.py
├── memory_system.py     # MemorySystem —— 统一入口 / 事件分发中枢
├── working_memory.py    # Layer 1: 工作记忆
├── episodic_memory.py   # Layer 2: 情景记忆
└── semantic_memory.py   # Layer 3: 语义记忆(含 PlayerProfile)
```

对外导出(`__init__.py`):

```python
from agents.memory import (
    MemorySystem,
    WorkingMemory,
    EpisodicMemory, EpisodeRecord,
    SemanticMemory, PlayerProfile,
)
```

---

## 2. 三层记忆架构

```
┌─────────────────────────────────────────────────────────┐
│                    MemorySystem                         │
│                (事件分发 + 上下文聚合)                    │
│                                                         │
│  ┌─────────────────────────────────────────────────┐    │
│  │ Layer 1: WorkingMemory (工作记忆)                │    │
│  │  • current_round / current_phase                │    │
│  │  • round_events / round_deaths / round_votes    │    │
│  │  • recent_speeches (滚动窗口 ≤ 20)              │    │
│  │  • temp_flags (查验结果等临时标记)               │    │
│  │  —— 新回合时自动清空 round_* 与 temp_flags      │    │
│  └─────────────────────────────────────────────────┘    │
│                      │ 重要信息沉淀                      │
│                      v                                  │
│  ┌─────────────────────────────────────────────────┐    │
│  │ Layer 2: EpisodicMemory (情景记忆)              │    │
│  │  • episodes: List[EpisodeRecord]                │    │
│  │  • deaths_timeline                              │    │
│  │  • vote_history                                 │    │
│  │  —— 整局持续累积,按 round / type / importance   │    │
│  │     查询                                         │    │
│  └─────────────────────────────────────────────────┘    │
│                      │ 抽象归纳                          │
│                      v                                  │
│  ┌─────────────────────────────────────────────────┐    │
│  │ Layer 3: SemanticMemory (语义记忆)              │    │
│  │  • profiles: Dict[int, PlayerProfile]           │    │
│  │  • relationships: Dict[(A,B), float]  (-1~+1)   │    │
│  │  —— 玩家画像 + 玩家两两关系图                    │    │
│  └─────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────┘
```

### 2.1 WorkingMemory — 工作记忆

**文件**:`working_memory.py`

| 字段 | 类型 | 说明 |
|---|---|---|
| `current_round` | `int` | 当前回合 |
| `current_phase` | `str` | 当前阶段 |
| `round_events` | `List[Dict]` | 当前回合所有事件 |
| `recent_speeches` | `List[Dict]` | 最近发言滚动窗口,上限 `MAX_RECENT_SPEECHES = 20` |
| `round_deaths` | `List[int]` | 当前回合死亡玩家 ID |
| `round_votes` | `Dict[int, int]` | 当前回合投票 `{voter: target}` |
| `temp_flags` | `Dict[str, Any]` | 临时标记(如 `check_{target}` 查验结果) |

**生命周期**:`update_phase(round_num, phase)` 发现回合数变化时触发 `_on_new_round`,清空 `round_events / round_deaths / round_votes / temp_flags`;**`recent_speeches` 跨回合保留**(用于对照历史发言)。

**格式化输出**:`format_context()` 生成简短的当前阶段上下文 + 最近 5 条发言 + 本轮投票。

### 2.2 EpisodicMemory — 情景记忆

**文件**:`episodic_memory.py`

核心数据结构:

```python
@dataclass
class EpisodeRecord:
    round: int
    phase: str
    event_type: str                   # DEATH / SPEECH / VOTE / VOTE_RESULT /
                                      # CHECK_RESULT / SAVE / POISON /
                                      # GUARD_PROTECT / HUNTER_SHOOT ...
    actor_id: Optional[int] = None
    target_id: Optional[int] = None
    content: Optional[str] = None
    timestamp: float = field(default_factory=time.time)
    importance: float = 0.5           # 0~1,越高越重要
```

除了 `episodes: List[EpisodeRecord]` 这条主时间线,还维护两个**结构化索引**便于快速检索:

- `deaths_timeline: List[{round, player_id, cause}]`
- `vote_history: List[{round, votes: {voter: target}, result}]`

**查询方法**:

- `get_by_round(round)` — 某回合所有事件
- `get_by_type(event_type)` — 某类型事件
- `get_important(min_importance=0.7)` — 重要事件
- `get_player_speeches(pid)` / `get_player_votes(pid)` — 某玩家的发言 / 投票

**输出给 Prompt**:

- `format_timeline(last_n_rounds=3)` — 只输出最近 3 回合且 `importance ≥ 0.5` 的事件,控制上下文规模
- `format_deaths()` — 死亡时间线

**重要度预设**(在 `MemorySystem` 的事件分发中):

| 事件 | importance |
|---|---|
| `DEATH` | 0.9 |
| `HUNTER_SHOOT` | 0.9 |
| `VOTE_RESULT` | 0.8 |
| `SAVE` / `POISON` | 0.8 |
| `CHECK_RESULT` | 1.0 |
| `SPEECH` | 0.4 |

### 2.3 SemanticMemory — 语义记忆

**文件**:`semantic_memory.py`

核心数据结构:

```python
@dataclass
class PlayerProfile:
    player_id: int
    seat_number: int = 0

    # 身份信息
    claimed_role: Optional[str] = None        # 玩家声称身份
    known_role: Optional[str] = None          # 确认身份(如查验结果)

    # 行为记录
    speech_summaries: List[str] = []          # 最近 5 条发言摘要
    vote_targets: List[Tuple[int, int]] = []  # [(round, target)]
    suspicious_behaviors: List[str] = []      # 最近 5 条可疑行为

    # 评估值
    suspicion_score: float = 0.33             # 嫌疑值 0~1
    trust_score: float = 0.5                  # 信任值 0~1

    # 状态
    is_alive: bool = True
    death_round: Optional[int] = None
    death_cause: Optional[str] = None
```

`SemanticMemory` 额外维护:

- `profiles: Dict[int, PlayerProfile]` — 所有玩家画像
- `relationships: Dict[(A, B), float]` — 玩家两两关系强度,`-1` 对立 ~ `+1` 互保,`key` 始终以 `(min, max)` 规范化

**关键方法**:

- `update_suspicion(pid, delta, reason)` — 调整嫌疑值(带上下限截断)
- `set_known_role(pid, role)` — 一旦确认是狼人 `suspicion_score = 0.95`,确认神/民 `= 0.05`
- `set_claimed_role(pid, role)` — 记录玩家跳身份
- `update_relationship(a, b, delta)` — 关系强度更新
- `get_suspicion_ranking(alive_only=True)` — 按嫌疑值降序排,**排除自己**
- `get_most_suspicious() / get_most_trusted() / get_allies(pid)` — 决策常用查询

**输出给 Prompt**:

- `format_profiles()` — 所有玩家画像简短描述
- `format_suspicion_ranking()` — 嫌疑排名(带 `[已知XXX]` / `[声称XXX]` 标注)

---

## 3. 核心入口:MemorySystem

**文件**:`memory_system.py`

```python
class MemorySystem:
    def __init__(self, my_player_id: int):
        self.my_player_id = my_player_id
        self.working = WorkingMemory()
        self.episodic = EpisodicMemory()
        self.semantic = SemanticMemory(my_player_id)

    def init_game(self, player_ids, seat_map=None): ...
    def process_event(self, event: GameEvent): ...    # 事件分发
    def get_full_context(self) -> Dict[str, str]: ... # 聚合给 Prompt
    def get_info(self) -> Dict: ...                   # 调试用
```

`MemorySystem` 作为**单一入口**:Agent 只需持有一个 `memory` 实例,内部负责把同一个事件**同时写入**相应层次的记忆,保证三层数据一致。

---

## 4. 事件分发流程

Agent 收到 `GameEvent` 后,通过 `memory.process_event(event)` 按 `event_type` 分发:

```python
if et == EventType.PHASE_CHANGE:       self._handle_phase_change(...)
elif et == EventType.PLAYER_DIED:      self._handle_death(...)
elif et == EventType.PLAYER_SPEECH:    self._handle_speech(...)
elif et == EventType.VOTE_RESULT:      self._handle_vote_result(...)
elif et == EventType.SEER_CHECK_RESULT:self._handle_check_result(...)
elif et == EventType.WITCH_SAVE_USED:  self._handle_witch_save(...)
elif et == EventType.WITCH_POISON_USED:self._handle_witch_poison(...)
elif et == EventType.HUNTER_SHOOT:     self._handle_hunter_shoot(...)
elif et == EventType.GAME_START:       self._handle_game_start(...)
```

### 4.1 典型事件:PLAYER_SPEECH

一条发言会**同时**写入三层:

```
PLAYER_SPEECH(speaker=3号, content="我验了6号好人...")
    │
    ├── working.add_speech(3, content, round)
    │     → 加入最近发言滚动窗口
    │
    ├── episodic.add_episode(event_type="SPEECH", importance=0.4)
    │     → 追加到事件时间线
    │
    └── semantic.get_or_create(3).add_speech_summary(
            f"第{round}天: {content[:80]}..."
        )
          → 进入玩家画像的最近 5 条发言摘要
```

### 4.2 典型事件:VOTE_RESULT

投票结果会触发**关系图更新**:

```python
def _analyze_vote_relationships(self, votes: Dict[int, int]):
    for voter_a, voter_b in <所有两两组合>:
        if votes[voter_a] == votes[voter_b]:
            # 投同一个人 → 关系 +0.1
            semantic.update_relationship(voter_a, voter_b, +0.1)
        if votes[voter_a] == voter_b and votes[voter_b] == voter_a:
            # 互投 → 关系 -0.2
            semantic.update_relationship(voter_a, voter_b, -0.2)
```

### 4.3 典型事件:SEER_CHECK_RESULT

只有预言家自己能收到,直接**硬改嫌疑值**到极值:

```python
if "狼" in result or result == "WEREWOLF":
    semantic.set_known_role(target_id, "WEREWOLF")  # 嫌疑=0.95
else:
    semantic.set_known_role(target_id, "GOOD")      # 嫌疑=0.05
```

同时写入 `working.temp_flags["check_{target}"] = result` 和 `episodic`(`importance=1.0`)。

---

## 5. 与 Agent / 推理引擎的耦合

`packages/ai-service/agents/base_agent.py` 中的 `WerewolfAgent`:

### 5.1 初始化

```python
self.memory = MemorySystem(my_player_id=player_id)
```

游戏开始:

```python
def init_game(self, player_ids, seat_map=None):
    self.memory.init_game(player_ids, seat_map)
    self.reasoner.init_players(player_ids)

    # 狼人:预置队友为"已知狼人"
    if self.role == Role.WEREWOLF and self.teammates:
        for tm in self.teammates:
            self.memory.semantic.set_known_role(tm, "WEREWOLF")
            self.memory.semantic.update_suspicion(tm, -1.0, "狼人队友")
            self.reasoner.confirm_wolf(tm)
```

### 5.2 感知阶段:memory 与 reasoner 双向同步

`perceive(event)` 中的关键流水线:

```
event
  │
  ├── ① memory.process_event(event)        记忆系统分发
  │
  ├── ② strategy.update_on_event(event)    角色策略更新
  │
  ├── ③ evidence_analyzer.analyze_*(memory, reasoner, ...)
  │       读取 memory 生成证据 → 更新 reasoner 后验概率
  │
  └── ④ reasoner.sync_to_semantic(memory.semantic)
        把贝叶斯后验概率写回 PlayerProfile.suspicion_score
```

关键点:**推理引擎与语义记忆双向同步**,保证 LLM 看到的嫌疑排名与贝叶斯推理结果一致。

---

## 6. 对 Prompt 的输出

`MemorySystem.get_full_context()` 把结构化数据**渲染为中文文本字符串**,供 LangChain `ChatPromptTemplate` 注入:

```python
def get_full_context(self) -> Dict[str, str]:
    return {
        "working_context":   self.working.format_context(),
        "timeline":          self.episodic.format_timeline(),
        "deaths":            self.episodic.format_deaths(),
        "player_profiles":   self.semantic.format_profiles(),
        "suspicion_ranking": self.semantic.format_suspicion_ranking(),
    }
```

### 6.1 在 action_planner 中

`packages/ai-service/agents/planning/action_planner.py`:

```python
# ③ 记忆 + 推理
memory_ctx = agent.memory.get_full_context()
reasoning  = agent.reasoner.format_analysis(game_state.alive_players)
# → 作为变量注入夜间行动 Prompt,交给 LLM 生成 JSON 操作
```

### 6.2 在 speech_generator 中

`packages/ai-service/agents/planning/speech_generator.py`:

```python
memory_ctx    = agent.memory.get_full_context()
reasoning_ctx = memory_ctx.get("suspicion_ranking", "暂无推理数据")
# → 与角色 Prompt / 发言引导一起合成 ChatPromptTemplate 变量
```

### 6.3 调试接口

`get_memory_dump()` 返回 `get_full_context()` + `reasoner.format_analysis()` + `get_info()`,配合 Agent 调试 API `GET /api/agent/{game_id}/{player_id}/memory` 使用。

---

## 7. 与设计文档的差异

[`ai-agent-design.md`](./ai-agent-design.md) 第 5 章与实际实现的差异点:

| 项 | 设计文档 | 实际实现 |
|---|---|---|
| 对话历史管理 | `ConversationSummaryBufferMemory`(LLM 摘要) + 自定义三层并存 | **未引入任何 LangChain Memory**,完全自研三层 |
| `AgentMemoryManager` 协同类 | 独立类统一管理 LangChain Memory + 自定义记忆 | **未实现**,直接由 `MemorySystem` 承担统一管理 |
| 上下文注入方式 | 通过 `MessagesPlaceholder("chat_history")` + LangChain 自动填充 | 由 `get_full_context()` 输出**纯文本字符串**,作为普通 Prompt 变量注入 |
| Token 控制 | 依赖 `ConversationSummaryBufferMemory(max_token_limit=2000)` 自动摘要 | `recent_speeches` 滚动窗口(20) + `format_timeline(last_n_rounds=3)` + `importance ≥ 0.5` 过滤 |

### 设计取舍

实际实现更加**轻量**:

- **不调用额外 LLM 做摘要** — 节省 token 与延迟
- **结构化记忆直接格式化** — 可解释、可调试、可回放,决策链路完全透明
- **三层记忆统一入口** — 单次 `process_event` 即完成多层同步,避免数据不一致

设计文档中提到的 LangChain Memory 组件并非错误方向,但在当前游戏生命周期短(单局通常十几轮)、事件可枚举的场景下,自研实现更直接、更可控。如后续需要跨局知识积累或更复杂的自然对话,可在 `MemorySystem` 基础上叠加 LangChain Memory 层。

---

## 附录:文件与类速查

| 文件 | 主要类 / 数据结构 |
|---|---|
| `memory_system.py` | `MemorySystem` |
| `working_memory.py` | `WorkingMemory` (dataclass) |
| `episodic_memory.py` | `EpisodicMemory`, `EpisodeRecord` |
| `semantic_memory.py` | `SemanticMemory`, `PlayerProfile` |

| Agent 侧调用点 | 位置 |
|---|---|
| 初始化 | `base_agent.py` `WerewolfAgent.__init__` / `init_game` |
| 事件处理 | `base_agent.py` `perceive(event)` |
| 上下文注入 | `planning/action_planner.py`、`planning/speech_generator.py` |
| 调试导出 | `base_agent.py` `get_memory_dump()` |
