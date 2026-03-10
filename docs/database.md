# 数据库设计文档

## 实体关系图

```
┌─────────────┐       ┌─────────────┐       ┌─────────────┐
│    users    │       │    rooms    │       │    games    │
├─────────────┤       ├─────────────┤       ├─────────────┤
│ id (PK)     │       │ id (PK)     │◄──────┤ id (PK)     │
│ username    │◄──────┤ host_id(FK) │       │ room_id(FK) │
│ password    │       │ room_code   │       │ status      │
│ email       │       │ room_name   │       │ current_round│
│ avatar_url  │       │ max_players │       │ current_phase│
│ total_games │       │ current_players│    │ winner      │
│ win_games   │       │ status      │       └─────────────┘
│ rating      │       │ has_password│              │
└─────────────┘       └─────────────┘              │
                                                   │
                          ┌────────────────────────┘
                          │
                   ┌─────────────┐
                   │   players   │
                   ├─────────────┤
                   │ id (PK)     │
                   │ game_id(FK) │
                   │ user_id(FK) │
                   │ is_ai       │
                   │ ai_name     │
                   │ seat_number │
                   │ role        │
                   │ status      │
                   └─────────────┘
```

## 表结构说明

### users (用户表)
存储用户基本信息和游戏统计数据

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键，自增 |
| username | VARCHAR(50) | 用户名，唯一 |
| password | VARCHAR(255) | 加密后的密码 |
| email | VARCHAR(100) | 邮箱，唯一 |
| avatar_url | VARCHAR(255) | 头像URL |
| total_games | INT | 总游戏场次 |
| win_games | INT | 胜利场次 |
| rating | INT | 积分 |

### rooms (房间表)
存储游戏房间信息

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键，自增 |
| room_code | VARCHAR(10) | 房间号，唯一，6位随机字符 |
| room_name | VARCHAR(50) | 房间名称 |
| host_id | BIGINT | 房主用户ID |
| max_players | INT | 最大玩家数，默认12 |
| current_players | INT | 当前玩家数 |
| status | ENUM | 状态：WAITING/PLAYING/FINISHED |
| has_password | BOOLEAN | 是否有密码 |
| password | VARCHAR(50) | 房间密码 |

### games (游戏表)
存储游戏对局信息

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键，自增 |
| room_id | BIGINT | 房间ID |
| status | ENUM | 状态：PREPARING/RUNNING/PAUSED/FINISHED |
| current_round | INT | 当前回合数 |
| current_phase | ENUM | 当前阶段 |
| winner | ENUM | 获胜方：NONE/WEREWOLF/VILLAGER/THIRD_PARTY |

### players (玩家表)
存储每局游戏的玩家信息

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键，自增 |
| game_id | BIGINT | 游戏ID |
| user_id | BIGINT | 用户ID（AI玩家为NULL） |
| is_ai | BOOLEAN | 是否为AI玩家 |
| ai_name | VARCHAR(50) | AI玩家名称 |
| seat_number | INT | 座位号 |
| role | ENUM | 角色 |
| status | ENUM | 状态：ALIVE/DEAD/DISCONNECTED |
| is_captain | BOOLEAN | 是否为警长 |

### game_logs (游戏日志表)
存储游戏过程中的操作日志

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键，自增 |
| game_id | BIGINT | 游戏ID |
| round | INT | 回合数 |
| phase | VARCHAR(50) | 阶段 |
| player_id | BIGINT | 玩家ID |
| action_type | VARCHAR(50) | 操作类型 |
| action_data | JSON | 操作数据 |

## 角色配置

### 标准12人局配置
- 狼人 × 4
- 村民 × 4
- 预言家 × 1
- 女巫 × 1
- 猎人 × 1
- 守卫 × 1

### 9人局配置
- 狼人 × 3
- 村民 × 3
- 预言家 × 1
- 女巫 × 1
- 猎人 × 1
