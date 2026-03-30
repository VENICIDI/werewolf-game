# 毕设进度追踪

## 项目信息
- **项目名称**: 基于 RAG 增强的 AI 狼人杀游戏平台
- **技术栈**: Spring Boot + Taro/React + Python AI
- **开始日期**: 2026-03-10
- **预计完成**: 2026-05-19

---

## 总体进度

| 阶段 | 进度 | 状态 |
|------|------|------|
| 第一阶段：基础架构 | 100% | ✅ 已完成 |
| 第二阶段：游戏核心 | 90% | ✅ 基本完成 |
| 第三阶段：AI 系统 | 15% | 🔄 进行中 |
| 第四阶段：完善测试 | 0% | ⏳ 待开始 |
| 第五阶段：论文答辩 | 0% | ⏳ 待开始 |

---

## 已完成步骤

### Step 1: 项目初始化 ✅
**完成时间**: 2026-03-10

- [x] 创建 Monorepo 结构 (npm workspaces)
- [x] 配置 Spring Boot 3.2 后端 (pom.xml + application.yml)
- [x] 配置 Taro + React 前端
- [x] 配置 Docker 环境 (MySQL 8.0 + Redis 7 + ChromaDB)
- [x] 设计数据库 5 张核心表 (users/rooms/games/players/game_logs)
- [x] 创建 JPA 实体 (User/Room/RoomMember/Game/Player)
- [x] 创建 Repository 层 (5个 JPA Repository)
- [x] 编写 init.sql 初始化脚本
- [x] 编写数据库文档和 API 文档

### Step 2: 用户认证系统 ✅
**完成时间**: 2026-03-10

**后端**:
- [x] JWT 工具类 JwtUtil (HMAC-SHA 签名/解析/验证/过期检测)
- [x] Spring Security 配置 (CSRF禁用/无状态Session/JWT过滤器/CORS)
- [x] JwtAuthenticationFilter (Bearer Token 提取→验证→SecurityContext)
- [x] CustomUserDetailsService (数据库加载用户)
- [x] UserContextInterceptor (解析JWT注入userId到request)
- [x] DTO 封装 (ApiResponse/LoginRequest/LoginResponse/RegisterRequest)
- [x] WxLoginRequest/WxLoginResponse (微信登录DTO)
- [x] AuthController (注册/登录/微信登录/获取当前用户)
- [x] WechatService (微信 jscode2session 接口对接)
- [x] GlobalExceptionHandler (参数校验/认证/权限/业务/通用异常)
- [x] WebConfig (拦截器注册 + RestTemplate Bean)

**前端**:
- [x] HTTP 请求封装 (request.ts)
- [x] Auth API 封装 (auth.ts)
- [x] 登录页面 (login/)
- [x] 注册页面 (register/)
- [x] 首页登录状态显示 (index/)
- [x] 认证守卫 (auth-guard.ts)

### Step 3: 房间管理系统 ✅
**完成时间**: 2026-03-10

**后端**:
- [x] RoomMember 实体 + RoomMemberRepository
- [x] Room DTO (CreateRoomRequest/JoinRoomRequest/RoomResponse)
- [x] RoomService (创建房间/6位随机码/加入/离开/房主转让/密码验证/准备状态)
- [x] RoomController (房间列表/创建/详情/加入/离开/准备 — 6个API)

**前端**:
- [x] Room API 封装 (room.ts)
- [x] 房间列表页面 (room-list/)
- [x] 房间详情页面 (room/)

### Step 4: WebSocket 通信框架 ✅
**完成时间**: 2026-03-10

**后端**:
- [x] WebSocketConfig (注册 /ws/room/{roomCode})
- [x] WebSocketHandshakeInterceptor (JWT Token验证/用户信息注入)
- [x] WebSocketMessage (12种消息类型常量)
- [x] RoomWebSocketHandler (ConcurrentHashMap会话管理/聊天/准备/心跳/广播)

**前端**:
- [x] WebSocketManager 封装 (websocket.ts)
- [x] 房间页面集成 WebSocket

### Step 5: 游戏逻辑引擎 🔄 部分完成
**开始时间**: 2026-03-10

**已完成**:
- [x] ConfigLoader 配置加载器 (game-flow.json + roles-complete.json)
- [x] GameConfig / RoleConfig 配置POJO
- [x] RoleAssigner 角色分配器 (角色池构建/随机分配/阵营判断)
- [x] GameStateMachine 状态机框架 (waiting→starting→night→day→checking→finished)
- [x] PhaseScheduler 阶段调度器框架 (ScheduledExecutorService + 昼夜阶段切换)
- [x] GameService 骨架 (startGame/handleNightAction/endGame/checkWinCondition)
- [x] 12 个 JSON 配置文件

**未完成**:
- [ ] GameService.createPlayers() — 从 RoomMember 创建 Player 的实际实现
- [ ] 狼人击杀逻辑 — 记录击杀目标、结算死亡
- [ ] 预言家查验逻辑 — 返回查验结果给玩家
- [ ] 女巫救人/毒人逻辑 — 解药/毒药使用记录与结算
- [ ] 守卫守护逻辑 — 守护记录与同守冲突
- [ ] 猎人开枪逻辑 — 死亡时触发射击
- [ ] 投票/处决逻辑 — 收集投票、平票处理
- [ ] 夜晚结算逻辑 — 综合守护/击杀/救人/毒人确定死亡玩家
- [ ] PhaseScheduler 与 GameService 联动 — 阶段内等待玩家操作
- [ ] WebSocket 广播各阶段事件 — 通知特定玩家行动
- [ ] 游戏日志记录 (game_logs 表写入)
- [ ] GameController — 游戏操作 API (开始游戏/提交行动/投票)

### Step 6: 前端游戏界面 ✅
**完成时间**: 2026-03-10

**前端**:
- [x] Game API 封装 (game.ts)
- [x] 游戏主界面 (game/)
- [x] 个人中心 (profile/)

### Step 7: AI 服务搭建 🔄 骨架已完成
**开始时间**: 2026-03-10

**已完成**:
- [x] Python FastAPI 服务框架 (main.py + 路由 + 生命周期管理)
- [x] LLM 接口封装 (llm_service.py — OpenAI + Ollama 双模式)
- [x] RAG 服务封装 (rag_service.py — ChromaDB 初始化/添加/查询/统计)
- [x] 健康检查接口 (health.py)
- [x] 知识库管理接口骨架 (knowledge.py)
- [x] 游戏 AI 决策接口骨架 (game.py — speak/action/vote/think)
- [x] Pydantic 数据模型 (GameContext/AISpeakRequest/AIActionRequest 等)

**未完成**:
- [ ] AI 发言生成 — 接入 LLM + RAG 实际生成
- [ ] AI 行动决策 — 根据游戏状态做出合理行动
- [ ] AI 投票决策 — 分析发言确定投票目标
- [ ] AI 推理链 — 嫌疑人/信任列表维护
- [ ] 与 Java 后端 HTTP 通信集成

---

## 当前步骤

### Step 5 续: 游戏核心逻辑完善 ✅
**完成时间**: 2026-03-30

**核心任务**:
1. ✅ GameService.createPlayers() 实现 — 从 RoomMember 创建 Player 并分配座位号
2. ✅ 夜晚行动结算 — NightActionStore (狼人投票/预言家查验/女巫救毒/守卫守护/同守冲突)
3. ✅ 白天投票/处决逻辑 — VoteManager (投票收集/平票处理/处决)
4. ✅ GameController — 7个API (开始游戏/状态/玩家/行动/投票/猎人开枪/日志)
5. ✅ WebSocket 阶段广播 — 阶段变化/角色分配/死亡公告/投票结果/猎人开枪/游戏结束
6. ✅ 游戏日志记录 — GameLog 实体 + GameLogRepository

---

## 待办步骤

| 步骤 | 名称 | 预计时间 | 优先级 | 状态 |
|------|------|----------|--------|------|
| Step 5 续 | 游戏核心逻辑完善 | 3-4 天 | 🔥 高 | ✅ 已完成 |
| Step 8 | RAG 知识库构建 | 2-3 天 | 📋 中 | ⏳ 待开始 |
| Step 9 | AI 决策逻辑实现 | 3-4 天 | 📋 中 | ⏳ 待开始 |
| Step 10 | 功能完善 | 2-3 天 | 📝 低 | ⏳ 待开始 |
| Step 11 | 测试与优化 | 2-3 天 | 📝 低 | ⏳ 待开始 |
| Step 12 | 论文撰写 | 5-7 天 | 🔥 高 | ⏳ 待开始 |
| Step 13 | 答辩准备 | 2-3 天 | 🔥 高 | ⏳ 待开始 |

---

## 里程碑

| 里程碑 | 截止日期 | 目标 | 状态 |
|--------|----------|------|------|
| 🎯 **M1** | 2026-03-17 | 基础功能完成 (用户+房间+WebSocket) | ✅ 已完成 |
| 🎯 **M2** | 2026-04-07 | 游戏核心完成 (逻辑完善+可对局) | ✅ 基本完成 |
| 🎯 **M3** | 2026-04-28 | AI/RAG 集成完成 | ⏳ 待开始 |
| 🎯 **M4** | 2026-05-19 | 论文与答辩准备完成 | ⏳ 待开始 |

---

## 每日记录

### 2026-03-10
- ✅ 完成 Step 1: 项目初始化
- ✅ 完成 Step 2: 用户认证系统 (含微信登录)
- ✅ 完成 Step 3: 房间管理系统
- ✅ 完成 Step 4: WebSocket 通信框架
- 🔄 开始 Step 5: 游戏逻辑引擎 (完成框架骨架)
- ✅ 完成 Step 6: 前端游戏界面
- 🔄 开始 Step 7: AI 服务 (完成 FastAPI 骨架)

### 2026-03-30
- 📝 代码审查: 确认 Step 1~4、6 已完成，Step 5、7 为骨架状态
- ✅ 完成 Step 5 续: 游戏核心逻辑完善
  - [x] NightActionStore — 夜晚行动内存存储(狼人投票/预言家/女巫/守卫)
  - [x] VoteManager — 白天投票管理器(收集/平票/结算)
  - [x] GameLog 实体 + GameLogRepository
  - [x] GameActionRequest/VoteRequest/StartGameRequest DTO
  - [x] GameService 完整重写 (createPlayers/夜晚结算/投票处决/胜负判定/日志/WebSocket广播)
  - [x] PhaseScheduler 重写 (与GameService联动/阶段调度/自动结算)
  - [x] GameController (7个API)
  - [x] RoomWebSocketHandler 增加 sendToUser 单播
  - [x] WebSocketMessage 增加 8 种消息类型
  - [x] Game.GamePhase 增加 GUARD 阶段

---

*最后更新: 2026-03-30*
