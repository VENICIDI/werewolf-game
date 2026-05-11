# 微信云托管部署指南

> 适用于：狼人杀 monorepo → 微信云托管（WeChat CloudRun）

---

## 1. 服务拓扑

| 云托管服务名 | 目标目录 | 端口 | 说明 |
|--------------|----------|------|------|
| `werewolf-backend` | `packages/backend` | 8088 | Spring Boot 主后端 |
| `werewolf-ai` | `packages/ai-service` | 8000 | FastAPI AI 推理 + RAG（仅供 backend 内网调用，不对外） |
| `werewolf-speech` | `packages/ai-speech` | 8001 | FastAPI STT + TTS（小程序直连） |

- 小程序端通过 `wx.cloud.callContainer` 调用 `werewolf-backend` 和 `werewolf-speech`。
- `werewolf-backend` 通过服务名内网调用 `werewolf-ai`（地址 `http://werewolf-ai`）。
- `werewolf-ai` 不开放公网，降低 Key 泄漏风险。

## 2. 创建前置资源（一次性）

微信云托管本身不提供 MySQL / Redis，需要自行准备：
- **MySQL 8.x**：腾讯云 TencentDB for MySQL（同地域 VPC）
- **Redis 6.x**：腾讯云 Redis（同地域 VPC）
- **对象存储（可选）**：头像/音频如果要永久存储，建议走 COS。云托管容器本地磁盘重启会丢

创建完成后，把连接串/密码记下来，下一步填到环境变量。

## 3. 部署步骤（每个服务重复）

在微信云托管控制台「新建服务」：

### 3.1 选择代码
- **方式**：绑定 GitHub 仓库
- **仓库**：`VENICIDI/werewolf-game`
- **分支**：`master`
- **端口**：按上表填（8088 / 8000 / 8001）

### 3.2 高级设置
- **目标目录**：按上表填（`packages/backend` 等）
- **Dockerfile 文件**：有
- **Dockerfile 名称**：`Dockerfile`

### 3.3 环境变量

#### `werewolf-backend`

| Key | Value |
|-----|-------|
| `SERVER_PORT` | `8088` |
| `MYSQL_HOST` | 腾讯云 MySQL 内网地址 |
| `MYSQL_PORT` | `3306` |
| `MYSQL_DATABASE` | `werewolf` |
| `MYSQL_USERNAME` | 数据库账号 |
| `MYSQL_PASSWORD` | 数据库密码 |
| `REDIS_HOST` | 腾讯云 Redis 内网地址 |
| `REDIS_PORT` | `6379` |
| `REDIS_PASSWORD` | Redis 密码（无则留空） |
| `REDIS_DATABASE` | `0` |
| `AI_SERVICE_URL` | `http://werewolf-ai` |
| `WECHAT_APPID` | `wxb0e543c0df784e35` |
| `WECHAT_SECRET` | 微信小程序 AppSecret |
| `JWT_SECRET` | 生产环境换一个强随机串 |

#### `werewolf-ai`

| Key | Value |
|-----|-------|
| `SERVICE_HOST` | `0.0.0.0` |
| `SERVICE_PORT` | `8000` |
| `OPENAI_API_KEY` | DeepSeek/OpenAI Key |
| `OPENAI_BASE_URL` | `https://api.deepseek.com` |
| `OPENAI_MODEL` | `deepseek-v4-flash` |
| `JAVA_BACKEND_URL` | `http://werewolf-backend:8088` |
| `CHROMA_PERSIST_DIR` | `/app/chroma_db` |
| `USE_LOCAL_EMBEDDING` | `true` |
| `LOCAL_EMBEDDING_MODEL` | `BAAI/bge-small-zh-v1.5` |

#### `werewolf-speech`

| Key | Value |
|-----|-------|
| `SERVICE_HOST` | `0.0.0.0` |
| `SERVICE_PORT` | `8001` |
| `LOG_LEVEL` | `INFO` |

### 3.4 首次部署注意
- **Whisper 模型下载**：`werewolf-speech` 首次启动会拉 ~480MB 模型，构建超时的话可以给 Dockerfile 加一层 `RUN python -c "import whisper; whisper.load_model('small')"` 预下载进镜像。
- **edge-tts 网络**：需要容器出公网访问微软。云托管默认可以，海外备份可以换 `minimax` / `azure` TTS。

## 4. 小程序端改造（必须）

当前前端 4 处硬编码的 `http://10.0.0.240:8088` / `:8001`，上线前改成 `wx.cloud.callContainer`：

```ts
// 示例：替代 packages/frontend/src/utils/request.ts 的 Taro.request
wx.cloud.callContainer({
  config: { env: 'prod-xxxxxx' },     // 云托管环境 ID
  path: '/api/xxx',
  method: 'POST',
  header: { 'X-WX-SERVICE': 'werewolf-backend' },
  data: {...}
})
```

WebSocket：
```ts
wx.cloud.connectContainer({
  config: { env: 'prod-xxxxxx' },
  service: 'werewolf-backend',
  path: '/ws'
})
```

小程序合法域名：**无需配置**，callContainer 自动走云托管通道。

## 5. 验证

部署完后在云托管控制台「服务详情 → 调用日志」能看到：
- backend 的 `HTTP 200`
- ai-service 的 LangChain 初始化
- ai-speech 的 `edge-tts service ready`

也可以用云托管调试面板对服务直接发请求：
- `GET werewolf-backend/actuator/health` → `{"status":"UP"}`
- `GET werewolf-ai/health` → `{"status":"ok"}`
- `GET werewolf-speech/health` → `{"status":"ok"}`

## 6. 回滚 / 日志

- 每次部署产出一个 `版本号`，在控制台可一键回滚旧版本。
- 日志保留 7 天，长期日志建议接 CLS。

---

维护：每次改动代码 `git push master` 后去控制台手动点「部署」或开启「自动部署」。
