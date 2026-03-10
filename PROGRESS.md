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
| 第一阶段：基础架构 | 25% | 🔄 进行中 |
| 第二阶段：游戏核心 | 0% | ⏳ 待开始 |
| 第三阶段：AI 系统 | 0% | ⏳ 待开始 |
| 第四阶段：完善测试 | 0% | ⏳ 待开始 |
| 第五阶段：论文答辩 | 0% | ⏳ 待开始 |

---

## 当前步骤

### Step 5: 游戏逻辑引擎 🔄
**开始时间**: 2026-03-10  
**预计完成**: 2026-03-20

**任务清单**:
- [ ] 游戏状态机设计
- [ ] 角色分配算法
- [ ] 夜晚阶段逻辑 (狼人/预言家/女巫/猎人)
- [ ] 白天阶段逻辑 (讨论/投票/处决)
- [ ] 胜负判定
- [ ] 游戏日志记录
- [ ] 前端游戏界面

---

## 已完成步骤

### Step 1: 项目初始化 ✅
**完成时间**: 2026-03-10

- [x] 创建 Monorepo 结构
- [x] 配置 Spring Boot 后端
- [x] 配置 Taro 前端
- [x] 配置 Docker 环境 (MySQL + Redis)
- [x] 设计数据库实体 (User, Room, Game, Player)
- [x] 创建 Repository 层
- [x] 编写数据库文档
- [x] 编写 API 文档

### Step 2: 用户认证系统 ✅
**完成时间**: 2026-03-10

**后端**:
- [x] JWT 工具类 (生成/解析/验证)
- [x] Spring Security 配置
- [x] JWT 认证过滤器
- [x] UserDetailsService
- [x] DTO 封装 (ApiResponse/LoginResponse/RegisterRequest)
- [x] AuthController (注册/登录/获取用户信息)
- [x] 全局异常处理
- [x] 用户上下文拦截器

**前端**:
- [x] HTTP 请求封装 (request.ts)
- [x] Auth API 封装 (login/register/getCurrentUser)
- [x] 登录页面 (含表单验证)
- [x] 注册页面 (含表单验证)
- [x] 首页登录状态显示

### Step 3: 房间管理系统 ✅
**完成时间**: 2026-03-10

**后端**:
- [x] RoomMember 实体
- [x] Room DTO (CreateRoomRequest/JoinRoomRequest/RoomResponse)
- [x] RoomService 完善 (创建/加入/离开/密码验证)
- [x] RoomController (6个API)

**前端**:
- [x] Room API 封装
- [x] 房间列表页面 (含密码弹窗)
- [x] 房间详情页面 (创建/详情/玩家列表)

### Step 4: WebSocket 通信框架 ✅
**完成时间**: 2026-03-10

**后端**:
- [x] WebSocketConfig 配置类
- [x] WebSocketHandshakeInterceptor (Token验证)
- [x] WebSocketMessage 消息DTO
- [x] RoomWebSocketHandler (连接管理/消息处理/广播)

**前端**:
- [x] WebSocketManager 封装 (连接/心跳/重连/订阅)
- [x] 房间页面集成 WebSocket
- [x] 实时聊天功能

---

## 待办步骤

| 步骤 | 名称 | 预计时间 | 优先级 |
|------|------|----------|--------|
| Step 3 | 房间管理系统 | 2-3 天 | 🔥 高 |
| Step 4 | WebSocket 通信 | 2-3 天 | 🔥 高 |
| Step 5 | 游戏逻辑引擎 | 3-4 天 | 🔥 高 |
| Step 6 | 游戏界面开发 | 3-4 天 | 🔥 高 |
| Step 7 | AI 服务搭建 | 2-3 天 | 📋 中 |
| Step 8 | RAG 知识库 | 2-3 天 | 📋 中 |
| Step 9 | AI 决策逻辑 | 3-4 天 | 📋 中 |
| Step 10 | 功能完善 | 2-3 天 | 📝 低 |
| Step 11 | 测试与优化 | 2-3 天 | 📝 低 |
| Step 12 | 论文撰写 | 5-7 天 | 🔥 高 |
| Step 13 | 答辩准备 | 2-3 天 | 🔥 高 |

---

## 里程碑

| 里程碑 | 截止日期 | 目标 | 状态 |
|--------|----------|------|------|
| 🎯 **M1** | 2026-03-17 | 基础功能完成 (用户+房间+WebSocket) | 🔄 进行中 |
| 🎯 **M2** | 2026-04-07 | 游戏核心完成 (逻辑+界面) | ⏳ 待开始 |
| 🎯 **M3** | 2026-04-28 | AI/RAG 集成完成 | ⏳ 待开始 |
| 🎯 **M4** | 2026-05-19 | 论文与答辩准备完成 | ⏳ 待开始 |

---

## 每日记录

### 2026-03-10
- ✅ 完成 Step 1: 项目初始化
- ✅ 完成 Step 2: 用户认证系统
- ✅ 完成 Step 3: 房间管理系统
- 创建项目 Monorepo 结构
- 搭建 Spring Boot 后端骨架
- 设计数据库实体
- 搭建 Taro 前端项目
- 编写首页 UI
- 创建数据库初始化脚本
- 编写 API 文档和数据库文档
- 实现 JWT 认证 (生成/解析/验证)
- 配置 Spring Security + BCrypt
- 实现登录/注册 API
- 实现全局异常处理
- 编写前端登录/注册页面
- 封装 HTTP 请求和 Auth API
- 更新首页登录状态显示
- 实现 RoomMember 实体和 Repository
- 实现房间管理 API (6个接口)
- 编写房间列表页面
- 编写房间详情页面
- 实现创建房间功能

---

*最后更新: 2026-03-10*
