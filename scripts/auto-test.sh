#!/bin/bash
# ================================================================
# 🐺 狼人杀自动化测试脚本 — 全 AI 6人局
#
# 用法: ./scripts/auto-test.sh
#
# 流程:
#   1. 注册/登录测试用户
#   2. 创建房间 + 添加5个AI → 6人满员
#   3. 开始游戏 (standard_6 模式)
#   4. 轮询游戏状态直到结束，实时输出阶段变化
#   5. 打印完整游戏日志 + 最终结果
#
# 前置条件: 后端(8080) + AI服务(8000) 已启动
# ================================================================

set -e

BASE_URL="http://localhost:8080/api"
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "${CYAN}╔══════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║    🐺 狼人杀自动化测试 — 全AI 6人局         ║${NC}"
echo -e "${CYAN}╚══════════════════════════════════════════════╝${NC}"
echo ""

# ======================== 1. 登录 ========================
echo -e "${YELLOW}[1/5] 登录测试用户...${NC}"

# 尝试注册（已存在则忽略）
curl -s -X POST "$BASE_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"username":"autotest","password":"123456","email":"autotest@test.com"}' > /dev/null 2>&1 || true

# 登录获取 token
LOGIN_RES=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"autotest","password":"123456"}')

TOKEN=$(echo "$LOGIN_RES" | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    # 适配不同响应格式
    if 'data' in d and isinstance(d['data'], dict):
        print(d['data'].get('token', ''))
    elif 'token' in d:
        print(d['token'])
    else:
        print(d.get('data', ''))
except: pass
" 2>/dev/null)

if [ -z "$TOKEN" ]; then
  echo -e "${RED}登录失败！响应: $LOGIN_RES${NC}"
  exit 1
fi
echo -e "${GREEN}  ✅ 登录成功, token: ${TOKEN:0:20}...${NC}"

AUTH="Authorization: Bearer $TOKEN"

# ======================== 2. 创建房间 ========================
echo -e "${YELLOW}[2/5] 创建房间 + 添加AI...${NC}"

ROOM_RES=$(curl -s -X POST "$BASE_URL/rooms" \
  -H "Content-Type: application/json" \
  -H "$AUTH" \
  -d '{"roomName":"自动测试局","maxPlayers":6}')

ROOM_CODE=$(echo "$ROOM_RES" | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    data = d.get('data', d)
    print(data.get('roomCode', ''))
except: pass
" 2>/dev/null)

ROOM_ID=$(echo "$ROOM_RES" | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    data = d.get('data', d)
    print(data.get('id', ''))
except: pass
" 2>/dev/null)

if [ -z "$ROOM_CODE" ]; then
  echo -e "${RED}创建房间失败！响应: $ROOM_RES${NC}"
  exit 1
fi
echo -e "${GREEN}  ✅ 房间创建成功: $ROOM_CODE (ID: $ROOM_ID)${NC}"

# 添加 5 个 AI
for i in $(seq 1 5); do
  AI_RES=$(curl -s -X POST "$BASE_URL/rooms/$ROOM_CODE/add-ai" -H "$AUTH")
  echo -e "  🤖 添加AI $i/5"
  sleep 0.3
done

# 确认人数
DETAIL=$(curl -s "$BASE_URL/rooms/$ROOM_CODE" -H "$AUTH")
PLAYER_COUNT=$(echo "$DETAIL" | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    data = d.get('data', d)
    print(data.get('currentPlayers', 0))
except: print(0)
" 2>/dev/null)
echo -e "${GREEN}  ✅ 房间人数: $PLAYER_COUNT/6${NC}"

if [ "$PLAYER_COUNT" -lt 6 ]; then
  echo -e "${RED}人数不足，无法开始游戏${NC}"
  exit 1
fi

# ======================== 3. 开始游戏 ========================
echo -e "${YELLOW}[3/5] 开始游戏...${NC}"

START_RES=$(curl -s -X POST "$BASE_URL/games/room/$ROOM_ID/start" \
  -H "Content-Type: application/json" \
  -H "$AUTH" \
  -d '{"gameModeId":"standard_6"}')

GAME_ID=$(echo "$START_RES" | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    data = d.get('data', d)
    print(data.get('gameId', ''))
except: pass
" 2>/dev/null)

if [ -z "$GAME_ID" ]; then
  echo -e "${RED}开始游戏失败！响应: $START_RES${NC}"
  exit 1
fi
echo -e "${GREEN}  ✅ 游戏开始! ID: $GAME_ID${NC}"

# ======================== 4. 轮询游戏状态 ========================
echo -e "${YELLOW}[4/5] 监控游戏进程...${NC}"
echo ""

LAST_PHASE=""
LAST_ROUND=0
POLL_COUNT=0
MAX_POLLS=600  # 最多等 10 分钟 (600 × 1s)
AUTO_ACTED=""  # 记录已自动行动的阶段

while [ $POLL_COUNT -lt $MAX_POLLS ]; do
  STATUS_RES=$(curl -s "$BASE_URL/games/$GAME_ID" -H "$AUTH" 2>/dev/null)
  
  GAME_STATUS=$(echo "$STATUS_RES" | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    data = d.get('data', d)
    print(data.get('status', 'UNKNOWN'))
except: print('ERROR')
" 2>/dev/null)

  PHASE=$(echo "$STATUS_RES" | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    data = d.get('data', d)
    print(data.get('phase', ''))
except: pass
" 2>/dev/null)

  ROUND=$(echo "$STATUS_RES" | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    data = d.get('data', d)
    print(data.get('round', 0))
except: print(0)
" 2>/dev/null)

  WINNER=$(echo "$STATUS_RES" | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    data = d.get('data', d)
    print(data.get('winner', 'NONE'))
except: print('NONE')
" 2>/dev/null)

  # 阶段变化时输出
  if [ "$PHASE" != "$LAST_PHASE" ] || [ "$ROUND" != "$LAST_ROUND" ]; then
    TIMESTAMP=$(date '+%H:%M:%S')
    
    case "$PHASE" in
      NIGHT_START)  ICON="🌙" ;;
      GUARD)        ICON="🛡️" ;;
      WEREWOLF)     ICON="🐺" ;;
      SEER)         ICON="🔮" ;;
      WITCH)        ICON="🧙" ;;
      DAY_START)    ICON="☀️" ;;
      DISCUSSION)   ICON="💬" ;;
      VOTING)       ICON="🗳️" ;;
      EXECUTION)    ICON="⚖️" ;;
      *)            ICON="📋" ;;
    esac
    
    echo -e "  [$TIMESTAMP] ${ICON} 第${ROUND}轮 ${PHASE}"
    LAST_PHASE="$PHASE"
    LAST_ROUND="$ROUND"
  fi

  # ✨ 自动为真人玩家行动（避免卡住）
  PHASE_KEY="${ROUND}_${PHASE}"
  if [ "$PHASE_KEY" != "$AUTO_ACTED" ]; then
    case "$PHASE" in
      WEREWOLF|SEER|WITCH|GUARD)
        # 夜间阶段：延迟2秒后 skip
        sleep 2
        curl -s -X POST "$BASE_URL/games/$GAME_ID/action" \
          -H "Content-Type: application/json" -H "$AUTH" \
          -d '{"action":"skip"}' > /dev/null 2>&1
        AUTO_ACTED="$PHASE_KEY"
        ;;
      VOTING)
        # 投票阶段：延迟2秒后弃票
        sleep 2
        curl -s -X POST "$BASE_URL/games/$GAME_ID/action" \
          -H "Content-Type: application/json" -H "$AUTH" \
          -d '{"action":"vote","targetId":0}' > /dev/null 2>&1
        AUTO_ACTED="$PHASE_KEY"
        ;;
    esac
  fi

  # 游戏结束
  if [ "$GAME_STATUS" = "FINISHED" ]; then
    echo ""
    echo -e "${GREEN}  🎉 游戏结束！${NC}"
    
    case "$WINNER" in
      VILLAGER)  echo -e "${GREEN}  🏆 获胜方: 好人阵营${NC}" ;;
      WEREWOLF)  echo -e "${RED}  🏆 获胜方: 狼人阵营${NC}" ;;
      *)         echo -e "${YELLOW}  🏆 获胜方: $WINNER${NC}" ;;
    esac
    break
  fi

  # 异常检测
  if [ "$GAME_STATUS" = "ERROR" ]; then
    echo -e "${RED}  ❌ 游戏状态异常！${NC}"
    break
  fi

  POLL_COUNT=$((POLL_COUNT + 1))
  sleep 1
done

if [ $POLL_COUNT -ge $MAX_POLLS ]; then
  echo -e "${RED}  ⏰ 超时！游戏未在5分钟内结束${NC}"
fi

# ======================== 5. 打印结果 ========================
echo ""
echo -e "${YELLOW}[5/5] 游戏复盘...${NC}"
echo ""

# 玩家列表和角色
echo -e "${CYAN}━━━ 玩家角色 & 状态 ━━━${NC}"
PLAYERS_RES=$(curl -s "$BASE_URL/games/$GAME_ID/players" -H "$AUTH")
echo "$PLAYERS_RES" | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    data = d.get('data', d)
    players = data.get('players', [])
    role_map = {
        'VILLAGER': '👤村民', 'WEREWOLF': '🐺狼人', 'SEER': '🔮预言家',
        'WITCH': '🧙女巫', 'HUNTER': '🔫猎人', 'GUARD': '🛡守卫',
        'IDIOT': '🤡白痴', 'UNKNOWN': '❓未知'
    }
    status_map = {'ALIVE': '✅存活', 'DEAD': '💀死亡'}
    for p in sorted(players, key=lambda x: x.get('seatNumber', 0)):
        seat = p.get('seatNumber', '?')
        name = p.get('username', '?')
        role = role_map.get(p.get('role', 'UNKNOWN'), p.get('role', '?'))
        status = status_map.get(p.get('status', ''), p.get('status', ''))
        ai = ' [AI]' if p.get('isAi') else ''
        print(f'  {seat}号 {name:<15} {role:<8} {status}{ai}')
except Exception as e:
    print(f'  解析失败: {e}')
" 2>/dev/null

# 游戏日志
echo ""
echo -e "${CYAN}━━━ 行动日志 ━━━${NC}"
LOGS_RES=$(curl -s "$BASE_URL/games/$GAME_ID/logs" -H "$AUTH")
echo "$LOGS_RES" | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    logs = d.get('data', d)
    if isinstance(logs, list):
        for log in logs:
            r = log.get('round', 0)
            phase = str(log.get('phase', '?'))
            action = str(log.get('actionType', '?'))
            pid = log.get('playerId', '-')
            pid_str = str(pid) if pid else '-'
            print(f'  R{r} | {phase:<14} | P{pid_str:<3} | {action}')
    else:
        print('  无日志')
except Exception as e:
    print(f'  解析失败: {e}')
" 2>/dev/null

echo ""
echo -e "${CYAN}━━━ 测试完成 ━━━${NC}"
echo -e "  游戏ID: $GAME_ID"
echo -e "  房间号: $ROOM_CODE"
echo -e "  轮询次数: $POLL_COUNT"
echo ""
