# Werewolf AI Service

基于 RAG 增强的 AI 狼人杀玩家服务

## 功能特性

- 🤖 **AI 玩家** - 模拟真人玩家进行游戏
- 🧠 **RAG 增强** - 基于知识库的决策增强
- 💬 **自然语言** - 生成自然流畅的发言
- 🎯 **多角色支持** - 支持狼人、预言家、女巫等多种角色
- 🔌 **易于集成** - 通过 REST API 与 Java 后端通信

## 技术栈

- **FastAPI** - Web 框架
- **ChromaDB** - 向量数据库
- **LangChain** - RAG 框架
- **OpenAI API** - 大语言模型

## 快速开始

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
# 编辑 .env 文件，设置 OPENAI_API_KEY
```

### 3. 启动服务

```bash
python main.py
```

服务将在 http://localhost:8000 启动

### 4. 查看 API 文档

- Swagger UI: http://localhost:8000/docs
- ReDoc: http://localhost:8000/redoc

## API 接口

### 健康检查

```bash
GET /api/health
```

### 知识库查询

```bash
POST /api/knowledge/query
{
  "query": "预言家应该怎么发言？",
  "top_k": 3
}
```

### AI 发言生成

```bash
POST /api/game/speak
{
  "context": {
    "game_id": "game-001",
    "player_id": "player-1",
    "role": "PROPHET",
    "day": 1,
    "phase": "DAY_DISCUSSION",
    ...
  },
  "speak_type": "NORMAL"
}
```

### AI 行动决策

```bash
POST /api/game/action
{
  "context": {...},
  "action_type": "CHECK"
}
```

## 项目结构

```
ai-service/
├── main.py              # FastAPI 应用入口
├── requirements.txt     # Python 依赖
├── .env.example        # 环境变量示例
├── routers/            # API 路由
│   ├── health.py       # 健康检查
│   ├── knowledge.py    # 知识库接口
│   └── game.py         # 游戏 AI 接口
├── services/           # 业务服务
│   ├── rag_service.py  # RAG 服务
│   └── llm_service.py  # LLM 服务
└── models/             # 数据模型
```

## 知识库

知识库文档位于 `docs/knowledge/`：

1. 01-游戏规则.md - 游戏基本规则
2. 02-角色技能.md - 角色技能详解
3. 03-发言技巧.md - 发言策略技巧
4. 04-常见板子.md - 常见板子配置
5. 05-AI策略指南.md - AI 专用策略

## 开发计划

- [x] FastAPI 服务框架
- [x] 基础 API 接口
- [ ] ChromaDB 向量数据库集成
- [ ] 知识库文档向量化
- [ ] RAG 检索逻辑
- [ ] LLM Prompt 工程
- [ ] AI 决策逻辑
- [ ] 与 Java 后端对接

## License

MIT