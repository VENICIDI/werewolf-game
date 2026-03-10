# API 接口文档

## 基础信息
- 基础URL: `http://localhost:8080/api`
- 认证方式: JWT Token (Header: `Authorization: Bearer {token}`)

## 认证接口

### 注册
```
POST /auth/register
Content-Type: application/json

{
  "username": "string",
  "password": "string",
  "email": "string"
}

Response: 200 OK
{
  "id": 1,
  "username": "string",
  "email": "string"
}
```

### 登录
```
POST /auth/login
Content-Type: application/json

{
  "username": "string",
  "password": "string"
}

Response: 200 OK
{
  "id": 1,
  "username": "string",
  "email": "string",
  "token": "jwt_token"
}
```

## 房间接口

### 获取房间列表
```
GET /rooms?status=WAITING

Response: 200 OK
{
  "rooms": [
    {
      "id": 1,
      "roomCode": "ABC123",
      "roomName": "新手房",
      "hostName": "user1",
      "maxPlayers": 12,
      "currentPlayers": 5,
      "hasPassword": false
    }
  ]
}
```

### 创建房间
```
POST /rooms
Authorization: Bearer {token}
Content-Type: application/json

{
  "roomName": "string",
  "maxPlayers": 12,
  "password": "string" (可选)
}

Response: 200 OK
{
  "id": 1,
  "roomCode": "ABC123",
  "roomName": "string",
  "maxPlayers": 12,
  "status": "WAITING"
}
```

### 加入房间
```
POST /rooms/{roomCode}/join
Authorization: Bearer {token}
Content-Type: application/json

{
  "password": "string" (如果需要)
}

Response: 200 OK
{
  "success": true,
  "message": "加入成功"
}
```

### 离开房间
```
POST /rooms/{roomCode}/leave
Authorization: Bearer {token}

Response: 200 OK
{
  "success": true
}
```

## 游戏接口

### 开始游戏
```
POST /games/{roomId}/start
Authorization: Bearer {token}

Response: 200 OK
{
  "gameId": 1,
  "status": "RUNNING",
  "players": [
    {
      "id": 1,
      "seatNumber": 1,
      "role": "VILLAGER",
      "isAi": false
    }
  ]
}
```

### 获取游戏状态
```
GET /games/{gameId}/status
Authorization: Bearer {token}

Response: 200 OK
{
  "gameId": 1,
  "status": "RUNNING",
  "currentRound": 1,
  "currentPhase": "NIGHT_START",
  "players": [...],
  "logs": [...]
}
```

### 执行操作
```
POST /games/{gameId}/action
Authorization: Bearer {token}
Content-Type: application/json

{
  "actionType": "VOTE",
  "targetId": 2
}

Response: 200 OK
{
  "success": true,
  "message": "投票成功"
}
```

## WebSocket 接口

### 连接
```
ws://localhost:8080/ws/game/{gameId}
Headers: Authorization: Bearer {token}
```

### 消息类型

#### 客户端发送
```json
{
  "type": "CHAT",
  "content": "我是好人"
}
```

```json
{
  "type": "ACTION",
  "action": "VOTE",
  "targetId": 2
}
```

#### 服务端推送
```json
{
  "type": "PHASE_CHANGE",
  "phase": "DAY_START",
  "round": 1,
  "message": "天亮了"
}
```

```json
{
  "type": "PLAYER_SPEAK",
  "playerId": 1,
  "playerName": "AI-1",
  "content": "我觉得2号是狼人"
}
```

```json
{
  "type": "GAME_OVER",
  "winner": "VILLAGER",
  "message": "村民阵营获胜"
}
```
