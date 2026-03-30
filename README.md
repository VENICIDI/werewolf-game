# 基于 RAG 增强的 AI 狼人杀游戏平台

## 项目结构

```
werewolf-game/
├── packages/
│   ├── backend/            # Spring Boot 后端 (Java 17)
│   │   └── src/main/java/com/werewolf/
│   │       ├── config/         # 配置层 (ConfigLoader/GameConfig/RoleConfig/WebConfig)
│   │       ├── controller/     # 控制器 (AuthController/RoomController/GameController)
│   │       ├── dto/            # 数据传输对象
│   │       ├── entity/         # JPA 实体 (User/Room/RoomMember/Game/Player/GameLog)
│   │       ├── exception/      # 全局异常处理
│   │       ├── game/           # 游戏核心引擎
│   │       │   ├── action/         # 行动处理器 (统一分发模式)
│   │       │   ├── GameStateMachine.java
│   │       │   ├── PhaseScheduler.java
│   │       │   ├── RoleAssigner.java
│   │       │   ├── NightActionStore.java
│   │       │   └── VoteManager.java
│   │       ├── repository/     # 数据访问层
│   │       ├── security/       # JWT 认证 + Spring Security
│   │       ├── service/        # 业务服务 (GameService/RoomService/UserService/WechatService)
│   │       └── websocket/      # WebSocket 实时通信
│   ├── frontend/           # Taro + React 前端 (TypeScript)
│   └── ai-service/         # AI 服务 (Python FastAPI + RAG)
│       ├── routers/            # API 路由 (game/knowledge/health)
│       └── services/           # LLM + RAG 服务
├── docs/                   # 项目文档
├── scripts/                # 数据库初始化脚本
├── docker-compose.yml      # 基础设施 (MySQL + Redis + ChromaDB)
├── PROGRESS.md             # 开发进度追踪
├── STEPS.md                # 开发步骤规划
└── README.md
```

## 技术栈

| 层级 | 技术 |
|------|------|
| **前端** | Taro 3.x + React 18 + TypeScript + SCSS |
| **后端** | Spring Boot 3.2 + Java 17 + Spring Security + JWT |
| **数据库** | MySQL 8.0 + Redis 7.x |
| **ORM** | Spring Data JPA + Hibernate |
| **实时通信** | Spring WebSocket |
| **AI 服务** | Python FastAPI + LangChain + ChromaDB (RAG) |
| **LLM** | OpenAI API / Ollama (本地模型) |
| **部署** | Docker Compose |

## 快速开始

### 1. 启动基础设施

```bash
docker-compose up -d   # MySQL + Redis + ChromaDB
```

### 2. 启动后端

```bash
cd packages/backend
./mvnw spring-boot:run
# 默认端口: 8080
```

### 3. 启动前端

```bash
cd packages/frontend
npm install
npm run dev
```

### 4. 启动 AI 服务 (可选)

```bash
cd packages/ai-service
pip install -r requirements.txt
python main.py
# 默认端口: 8000
```

## 后端 API

### 认证 (`/api/auth`)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/register` | 账号密码注册 |
| POST | `/api/auth/login` | 账号密码登录 |
| POST | `/api/auth/wx-login` | 微信小程序登录 |
| GET | `/api/auth/me` | 获取当前用户信息 |

### 房间 (`/api/rooms`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/rooms` | 获取房间列表 |
| POST | `/api/rooms` | 创建房间 |
| GET | `/api/rooms/{roomCode}` | 获取房间详情 |
| POST | `/api/rooms/{roomCode}/join` | 加入房间 |
| POST | `/api/rooms/{roomCode}/leave` | 离开房间 |
| POST | `/api/rooms/{roomCode}/ready` | 设置准备状态 |

### 游戏 (`/api/games`)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/games/room/{roomId}/start` | 开始游戏 |
| GET | `/api/games/{gameId}` | 获取游戏状态 |
| GET | `/api/games/{gameId}/players` | 获取玩家列表 |
| POST | `/api/games/{gameId}/action` | **统一行动入口** |
| GET | `/api/games/{gameId}/logs` | 获取游戏日志 |

### 统一行动入口

所有游戏行动通过 `POST /api/games/{gameId}/action` 提交，请求体：

```json
{
  "action": "kill",
  "targetId": 3
}
```

支持的 `action` 类型：

| action | 说明 | 阶段 | targetId |
|--------|------|------|----------|
| `kill` | 狼人击杀 | 夜晚 WEREWOLF | 被杀玩家ID |
| `check` | 预言家查验 | 夜晚 SEER | 被查玩家ID |
| `save` | 女巫救人 | 夜晚 WITCH | 可选 |
| `poison` | 女巫毒人 | 夜晚 WITCH | 被毒玩家ID |
| `guard` | 守卫守护 | 夜晚 GUARD | 被守玩家ID |
| `vote` | 白天投票 | 白天 VOTING | 投票目标ID (0=弃票) |
| `shoot` | 猎人开枪 | 死亡后 | 被射玩家ID |
| `skip` | 跳过行动 | 任意 | 可选 |

### WebSocket (`/ws/room/{roomCode}`)

连接时通过 `?token=xxx` 或 `Authorization` Header 传递 JWT。

消息类型：

| 类型 | 方向 | 说明 |
|------|------|------|
| `GAME_START` | S→C | 游戏开始，携带玩家列表 |
| `ROLE_ASSIGN` | S→C (单播) | 通知玩家角色 |
| `PHASE_CHANGE` | S→C | 阶段切换 |
| `ACTION_CONFIRM` | S→C (单播) | 行动确认 |
| `SEER_RESULT` | S→C (单播) | 预言家查验结果 |
| `DEATH_ANNOUNCE` | S→C | 死亡公告 |
| `VOTE_START` | S→C | 投票开始 |
| `VOTE_RESULT` | S→C | 投票结果 |
| `HUNTER_SHOOT` | S→C | 猎人开枪 |
| `GAME_OVER` | S→C | 游戏结束，公开所有角色 |
| `PLAYER_CHAT` | C→S→C | 聊天消息 |
| `PLAYER_READY` | C→S→C | 准备状态 |
| `HEARTBEAT` / `HEARTBEAT_ACK` | C↔S | 心跳 |

## 后端架构

### 分层结构

```
Controller → Service → Repository → Database
                ↓
          GameService.executeAction()
                ↓
          ActionDispatcher.dispatch(action, context)
                ↓
          GameActionHandler.validate() → execute()
```

### 游戏核心引擎 (`game/` 包)

| 组件 | 职责 |
|------|------|
| `GameStateMachine` | 游戏状态流转 (waiting→night→day→checking→finished) |
| `PhaseScheduler` | 阶段定时调度，自动推进昼夜阶段并触发结算 |
| `RoleAssigner` | 根据游戏模式配置随机分配角色 |
| `NightActionStore` | 夜间行动内存存储，综合结算死亡 (含同守冲突) |
| `VoteManager` | 白天投票管理 (收集、平票处理、结算) |
| `ActionDispatcher` | 行动分发器，根据 action 字段路由到对应处理器 |

### 行动处理器模式 (`game/action/` 包)

所有游戏行动统一实现 `GameActionHandler` 接口，由 `ActionDispatcher` 根据 `action` 字段自动路由：

```
GameActionHandler (接口)
├── WerewolfKillHandler   — action=kill
├── SeerCheckHandler      — action=check
├── WitchSaveHandler      — action=save
├── WitchPoisonHandler    — action=poison
├── GuardProtectHandler   — action=guard
├── VoteHandler           — action=vote
├── HunterShootHandler    — action=shoot
└── SkipHandler           — action=skip
```

**扩展新行动只需两步：**

**第一步** — 新建处理器，实现 `GameActionHandler` 接口：

```java
package com.werewolf.game.action;

public class MyNewHandler implements GameActionHandler {

    @Override
    public String getAction() {
        return "my_action";  // 对应请求中的 action 字段
    }

    @Override
    public void validate(ActionContext context) {
        // 校验逻辑：阶段、角色、目标是否合法
    }

    @Override
    public Map<String, Object> execute(ActionContext context) {
        // 执行逻辑：修改状态、广播消息
        Map<String, Object> result = new HashMap<>();
        result.put("message", "行动成功");
        return result;
    }
}
```

**第二步** — 在 `ActionDispatcher.init()` 中注册：

```java
@PostConstruct
public void init() {
    // ... 已有处理器
    register(new MyNewHandler());
}
```

无需修改 `GameService`、`GameController` 或任何 DTO。客户端直接发送 `{"action": "my_action", "targetId": ...}` 即可。

## 数据库设计

6 张核心表：

| 表名 | 用途 |
|------|------|
| `users` | 用户 (支持账号密码 + 微信登录) |
| `rooms` | 房间 |
| `room_members` | 房间成员 (JPA 自动建表) |
| `games` | 游戏 |
| `players` | 游戏玩家 (含角色/状态/座位号) |
| `game_logs` | 游戏行动日志 (JSON 格式) |

角色：`VILLAGER` / `WEREWOLF` / `SEER` / `WITCH` / `HUNTER` / `GUARD` / `IDIOT`

游戏阶段：`NIGHT_START` → `GUARD` → `WEREWOLF` → `SEER` → `WITCH` → `DAY_START` → `DISCUSSION` → `VOTING` → `EXECUTION`

## AI 服务 (Python)

| 接口 | 说明 |
|------|------|
| `POST /api/game/speak` | AI 发言生成 |
| `POST /api/game/action` | AI 行动决策 |
| `POST /api/game/vote` | AI 投票决策 |
| `POST /api/game/think` | AI 推理链 |
| `POST /api/knowledge/query` | RAG 知识检索 |
| `GET /api/health` | 健康检查 |

## 开发文档

- [API 文档](docs/api.md)
- [数据库设计](docs/database.md)
