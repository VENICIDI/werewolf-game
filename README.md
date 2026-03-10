# 基于 RAG 增强的 AI 狼人杀游戏平台

## 项目结构

```
werewolf-game/
├── packages/
│   ├── backend/          # Spring Boot 后端
│   ├── frontend/         # Taro + React 前端
│   ├── ai-service/       # AI 服务 (Python/RAG)
│   └── shared/           # 共享类型定义
├── docs/                 # 文档
├── docker-compose.yml    # 本地开发环境
└── README.md
```

## 快速开始

### 1. 启动基础设施
```bash
docker-compose up -d
```

### 2. 启动后端
```bash
cd packages/backend
./mvnw spring-boot:run
```

### 3. 启动前端
```bash
cd packages/frontend
npm install
npm run dev
```

## 技术栈

- **前端**: Taro 3.x + React 18 + TypeScript
- **后端**: Spring Boot 3.x + Java 17
- **数据库**: MySQL 8.0 + Redis 7.x
- **AI**: Python + LangChain + 向量数据库

## 开发文档

- [后端 API 设计](docs/backend-api.md)
- [数据库设计](docs/database.md)
- [AI 系统设计](docs/ai-system.md)
