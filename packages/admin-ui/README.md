# Werewolf RAG 管理后台

基于 **Vue 3 + Element Plus + ECharts** 的 RAG 知识库与检索日志可视化后台，专为毕设答辩演示设计。

## 功能模块

| 页面 | 路径 | 说明 |
|---|---|---|
| 总览仪表盘 | `/admin/dashboard` | 知识库 / 检索 / LLM 关键指标 + 24h 时间序列 + 角色饼图 + 热门源 / 高频 query Top10 |
| 知识库管理 | `/admin/knowledge` | 列出所有源文档，含 chunk 数、字符数、角色/类型/阶段聚合 |
| 文档详情 | `/admin/knowledge/:name` | 单文档所有切片预览 + 原文 Markdown |
| 检索调试器 | `/admin/search` | 在线输入 query，可视化相似度分数（带色条 + 排序徽章） |
| 检索日志 | `/admin/retrieval-logs` | 每次 RAG 调用明细：query / 角色 / 来源 / 耗时 / 命中结果，支持筛选与分页 |
| LLM 日志列表 | `/admin/llm-logs` | 按 game_id 列出所有 LLM 调用日志 |
| LLM 日志详情 | `/admin/llm-logs/:gameId` | 完整 prompt / response 结构化展示 + 原始日志切换 |
| 系统状态 | `/admin/system` | 向量库 / Embedding / 环境变量 / RAG 流程图 |

## 后端 API

所有接口前缀 `/api/admin`：

- `GET  /overview` - 仪表盘聚合数据
- `GET  /knowledge/documents` - 源文档列表
- `GET  /knowledge/documents/{name}/chunks` - 单文档切片
- `GET  /knowledge/raw/{name}` - 源 Markdown 原文
- `POST /knowledge/search` - RAG 检索（带相似度分数，自动写入日志）
- `POST /knowledge/rebuild` - 重建知识库
- `GET  /logs/retrieval` - 检索日志列表（分页/筛选）
- `GET  /logs/retrieval/stats` - 检索日志聚合统计
- `DELETE /logs/retrieval` - 清空检索日志
- `GET  /logs/llm` - LLM 日志游戏列表
- `GET  /logs/llm/{game_id}` - 单局完整 LLM 日志
- `GET  /system/status` - 系统组件状态

## 本地开发

```bash
# 在仓库根目录
npm run admin:install     # 安装依赖
npm run admin:dev         # 启动开发服务器 (默认 5174 端口)
# 开发服务器已配置代理：/api → http://localhost:8000
```

## 构建并集成到 FastAPI

```bash
npm run admin:build
# 产物输出到 packages/ai-service/admin_dist/
# FastAPI 启动后自动挂载在 /admin
# 访问入口：http://localhost:8000/admin
```

`packages/ai-service/main.py` 启动时若检测到 `admin_dist/index.html` 存在，
会自动把 SPA 挂载到 `/admin`（带 history 路由 fallback）。

## 一次性快速启动

```bash
# 1. 安装并构建管理后台
npm run admin:install && npm run admin:build

# 2. 启动 AI 服务（会自动挂载后台）
npm run start:ai

# 3. 浏览器打开
open http://localhost:8000/admin
```

## 设计亮点（毕设答辩话术）

- **可观测性**：每次 RAG 检索完整记录 query / 角色过滤 / 命中切片 / 相似度 / 耗时，JSONL 持久化便于离线分析
- **可解释性**：检索调试器显示每个命中切片的归一化相似度分数与色条可视化，直观对比向量召回质量
- **元数据驱动**：知识库切片自动打上 `role / doc_type / game_phase / difficulty` 多维标签，支持元数据过滤
- **链路可追**：LLM 调用日志结构化解析，按 game_id 索引完整 prompt + response，零成本复盘任意一局博弈
- **零侵入集成**：作为静态 SPA 嵌入 FastAPI，无需额外服务，演示部署只需一行 `npm run admin:build`
