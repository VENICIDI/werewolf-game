import { useEffect, useState, useCallback } from 'react'
import Taro, { useRouter } from '@tarojs/taro'
import { View, Text, Button, ScrollView } from '@tarojs/components'
import { getGameStatus, GamePhase, GameStatus, Role } from '../../../api/game'
import { get, post } from '../../../utils/request'
import { wsManager } from '../../../utils/websocket'
import './index.scss'

interface PlayerInfo {
  playerId: number
  seatNumber: number
  username: string
  avatarUrl?: string
  isAi?: boolean
  status: string
  role?: string
  isCaptain?: boolean
}

interface GameData {
  gameId: number
  roomCode: string
  status: string
  round: number
  phase: string
  players: PlayerInfo[]
  myPlayerId?: number
  myRole?: string
  mySeat?: number
}

interface ChatMessage {
  senderId: number
  senderName: string
  content: string
  timestamp: string
}

export default function GamePlay() {
  const router = useRouter()
  const [game, setGame] = useState<GameData | null>(null)
  const [loading, setLoading] = useState(true)
  const [chatMessages, setChatMessages] = useState<ChatMessage[]>([])
  const [selectedTarget, setSelectedTarget] = useState<number | null>(null)
  const [phaseTimeLeft, setPhaseTimeLeft] = useState(0)
  const [showRoleModal, setShowRoleModal] = useState(false)

  // 初始化：通过 API 获取游戏状态
  useEffect(() => {
    const gameId = router.params.gameId || Taro.getStorageSync('currentGameId')
    const roomCode = router.params.roomCode || Taro.getStorageSync('currentRoomCode')

    if (!gameId) {
      Taro.showToast({ title: '游戏参数缺失', icon: 'none' })
      setTimeout(() => Taro.switchTab({ url: '/pages/room-list/index' }), 1500)
      return
    }

    // 主动拉取游戏状态
    fetchGameData(Number(gameId))
    // 连接 WebSocket 接收增量更新
    if (roomCode) {
      connectWebSocket(String(roomCode))
    }

    return () => {
      wsManager.disconnect()
    }
  }, [])

  // 倒计时
  useEffect(() => {
    if (phaseTimeLeft <= 0) return
    const timer = setInterval(() => {
      setPhaseTimeLeft(prev => Math.max(0, prev - 1))
    }, 1000)
    return () => clearInterval(timer)
  }, [phaseTimeLeft])

  const fetchGameData = async (gameId: number) => {
    try {
      // 获取游戏基础状态
      const gameRes: any = await get(`/games/${gameId}`)
      // 获取玩家列表（含自己的角色）
      const playersRes: any = await get(`/games/${gameId}/players`)

      const gameData: GameData = {
        gameId: gameRes.gameId,
        roomCode: Taro.getStorageSync('currentRoomCode') || '',
        status: gameRes.status,
        round: gameRes.round,
        phase: gameRes.phase,
        players: playersRes.players || [],
        myPlayerId: playersRes.myPlayerId,
        myRole: playersRes.myRole,
        mySeat: playersRes.mySeat,
      }

      setGame(gameData)
      setLoading(false)
      console.log('[Game] 游戏数据加载完成:', gameData.phase, '我的角色:', gameData.myRole)

      // 弹出角色提示
      if (gameData.myRole && gameData.myRole !== 'UNKNOWN') {
        setTimeout(() => setShowRoleModal(true), 500)
      }
    } catch (error: any) {
      console.error('[Game] 获取游戏数据失败:', error)
      Taro.showToast({ title: '获取游戏数据失败', icon: 'none' })
      setTimeout(() => Taro.switchTab({ url: '/pages/room-list/index' }), 2000)
    }
  }

  const connectWebSocket = async (roomCode: string) => {
    try {
      wsManager.on('PHASE_CHANGE', handlePhaseChange)
      wsManager.on('PLAYER_ACTION', handlePlayerAction)
      wsManager.on('PLAYER_CHAT', handlePlayerChat)
      wsManager.on('GAME_OVER', handleGameOver)
      wsManager.on('DEATH_ANNOUNCE', handleDeathAnnounce)
      wsManager.on('ROLE_ASSIGN', handleRoleAssign)
      wsManager.on('VOTE_START', handleVoteStart)
      wsManager.on('VOTE_RESULT', handleVoteResult)
      wsManager.on('ACTION_CONFIRM', handleActionConfirm)
      wsManager.on('SYSTEM', handleSystemMessage)

      await wsManager.connect(roomCode)
      console.log('[Game] WebSocket 已连接')
    } catch (error) {
      console.error('[Game] WebSocket 连接失败:', error)
    }
  }

  const handlePhaseChange = useCallback((message: any) => {
    const data = message.data
    console.log('[Game] 阶段切换:', data.phase)
    setGame(prev => prev ? {
      ...prev,
      phase: data.phase,
      round: data.round || prev.round,
    } : null)
    if (data.duration) {
      setPhaseTimeLeft(Math.floor(data.duration / 1000))
    }
    setSelectedTarget(null)

    // 显示阶段提示
    if (data.message) {
      Taro.showToast({ title: data.message, icon: 'none', duration: 2000 })
    }
  }, [])

  const handlePlayerAction = useCallback((message: any) => {
    console.log('[Game] 玩家行动:', message.data)
  }, [])

  const handlePlayerChat = useCallback((message: any) => {
    setChatMessages(prev => [...prev, {
      senderId: message.senderId,
      senderName: message.senderName,
      content: message.data?.content || '',
      timestamp: message.timestamp
    }])
  }, [])

  const handleGameOver = useCallback((message: any) => {
    const data = message.data
    Taro.showModal({
      title: '游戏结束',
      content: data.message || '游戏已结束',
      showCancel: false,
      success: () => {
        Taro.switchTab({ url: '/pages/room-list/index' })
      }
    })
  }, [])

  const handleDeathAnnounce = useCallback((message: any) => {
    const data = message.data
    if (data.message) {
      Taro.showToast({ title: data.message, icon: 'none', duration: 3000 })
    }
    // 更新玩家状态
    if (data.deaths) {
      setGame(prev => {
        if (!prev) return null
        const updatedPlayers = prev.players.map(p => {
          const dead = data.deaths.find((d: any) => d.playerId === p.playerId)
          return dead ? { ...p, status: 'DEAD' } : p
        })
        return { ...prev, players: updatedPlayers }
      })
    }
  }, [])

  const handleRoleAssign = useCallback((message: any) => {
    const data = message.data
    console.log('[Game] 角色分配:', data.role)
    setGame(prev => prev ? { ...prev, myRole: data.role, myPlayerId: data.playerId } : null)
    setShowRoleModal(true)
  }, [])

  const handleVoteStart = useCallback((message: any) => {
    Taro.showToast({ title: '投票阶段开始', icon: 'none' })
  }, [])

  const handleVoteResult = useCallback((message: any) => {
    const data = message.data
    if (data.message) {
      Taro.showToast({ title: data.message, icon: 'none', duration: 3000 })
    }
  }, [])

  const handleActionConfirm = useCallback((message: any) => {
    Taro.showToast({ title: '行动已确认', icon: 'success' })
  }, [])

  const handleSystemMessage = useCallback((message: any) => {
    if (message.data?.message) {
      Taro.showToast({ title: message.data.message, icon: 'none' })
    }
  }, [])

  // 获取阶段展示信息
  const getPhaseDisplay = () => {
    switch (game?.phase) {
      case 'NIGHT_START':
        return { title: '天黑了', subtitle: '所有人请闭眼', icon: '🌙', bg: 'night' }
      case 'GUARD':
        return { title: '守卫行动', subtitle: '请选择要守护的目标', icon: '🛡️', bg: 'night' }
      case 'WEREWOLF':
        return { title: '狼人行动', subtitle: '请选择要击杀的目标', icon: '🐺', bg: 'night' }
      case 'SEER':
        return { title: '预言家行动', subtitle: '请选择要查验的目标', icon: '🔮', bg: 'night' }
      case 'WITCH':
        return { title: '女巫行动', subtitle: '是否使用解药/毒药', icon: '🧙', bg: 'night' }
      case 'DAY_START':
        return { title: '天亮了', subtitle: `第 ${game?.round} 天`, icon: '☀️', bg: 'day' }
      case 'DISCUSSION':
        return { title: '讨论阶段', subtitle: '请发表你的看法', icon: '💬', bg: 'day' }
      case 'VOTING':
        return { title: '投票阶段', subtitle: '请选择要放逐的玩家', icon: '🗳️', bg: 'day' }
      case 'EXECUTION':
        return { title: '处决阶段', subtitle: '公布投票结果', icon: '⚖️', bg: 'day' }
      default:
        return { title: '游戏进行中', subtitle: game?.phase || '', icon: '🐺', bg: 'night' }
    }
  }

  // 判断当前阶段我是否可以行动
  const canAct = () => {
    if (!game || !game.myRole) return false
    const myPlayer = game.players.find(p => p.playerId === game.myPlayerId)
    if (!myPlayer || myPlayer.status !== 'ALIVE') return false

    switch (game.phase) {
      case 'WEREWOLF': return game.myRole === 'WEREWOLF'
      case 'SEER': return game.myRole === 'SEER'
      case 'WITCH': return game.myRole === 'WITCH'
      case 'GUARD': return game.myRole === 'GUARD'
      case 'VOTING': return true
      default: return false
    }
  }

  // 获取行动类型
  const getActionType = () => {
    switch (game?.phase) {
      case 'WEREWOLF': return 'kill'
      case 'SEER': return 'check'
      case 'WITCH': return 'poison' // 简化：默认毒药
      case 'GUARD': return 'guard'
      case 'VOTING': return 'vote'
      default: return 'skip'
    }
  }

  const handleAction = async () => {
    if (!game || !selectedTarget) {
      Taro.showToast({ title: '请选择目标', icon: 'none' })
      return
    }
    try {
      await post(`/games/${game.gameId}/action`, {
        action: getActionType(),
        targetId: selectedTarget
      })
    } catch (error: any) {
      Taro.showToast({ title: error.message || '行动失败', icon: 'none' })
    }
  }

  const handleSkip = async () => {
    if (!game) return
    try {
      await post(`/games/${game.gameId}/action`, { action: 'skip' })
      Taro.showToast({ title: '已跳过', icon: 'none' })
    } catch (error: any) {
      Taro.showToast({ title: error.message || '操作失败', icon: 'none' })
    }
  }

  // 角色名中文映射
  const getRoleName = (role?: string) => {
    const map: Record<string, string> = {
      VILLAGER: '村民', WEREWOLF: '狼人', SEER: '预言家',
      WITCH: '女巫', HUNTER: '猎人', GUARD: '守卫', IDIOT: '白痴',
      UNKNOWN: '未知'
    }
    return map[role || ''] || role || '未知'
  }

  const phaseDisplay = getPhaseDisplay()

  if (loading) {
    return (
      <View className='game-play-container'>
        <View className='loading'>穿越血月迷雾...</View>
      </View>
    )
  }

  return (
    <View className={`game-play-container ${phaseDisplay.bg}`}>
      {/* 顶部信息栏 */}
      <View className='top-bar'>
        <View className='round-info'>
          <Text className='round-text'>第 {game?.round} 天</Text>
          <Text className='phase-text'>{phaseDisplay.title}</Text>
        </View>
        <View className='timer'>
          <Text className='timer-icon'>⏱️</Text>
          <Text className='timer-text'>{phaseTimeLeft}s</Text>
        </View>
        <View className='role-btn' onClick={() => setShowRoleModal(true)}>
          <Text className='role-icon'>👤</Text>
        </View>
      </View>

      {/* 阶段提示 */}
      <View className='phase-banner'>
        <Text className='phase-icon'>{phaseDisplay.icon}</Text>
        <Text className='phase-title'>{phaseDisplay.title}</Text>
        <Text className='phase-subtitle'>{phaseDisplay.subtitle}</Text>
      </View>

      {/* 玩家列表 */}
      <View className='players-section'>
        <Text className='section-title'>玩家列表</Text>
        <View className='players-grid'>
          {game?.players.map((player) => (
            <View
              key={player.playerId}
              className={`player-card ${player.status} ${selectedTarget === player.playerId ? 'selected' : ''}`}
              onClick={() => canAct() && player.status === 'ALIVE' && setSelectedTarget(player.playerId)}
            >
              <View className='player-avatar'>
                <Text className='avatar-default'>
                  {player.status === 'DEAD' ? '💀' : player.isAi ? '🤖' : '👤'}
                </Text>
              </View>
              <Text className='player-name'>{player.username}</Text>
              <Text className='seat-num'>{player.seatNumber}号位</Text>
              {player.status === 'DEAD' && <Text className='dead-badge'>已死亡</Text>}
            </View>
          ))}
        </View>
      </View>

      {/* 行动按钮 */}
      {canAct() && (
        <View className='action-section'>
          <Text className='action-hint'>
            {selectedTarget
              ? `已选择 ${game?.players.find(p => p.playerId === selectedTarget)?.username}`
              : '请点击选择目标'}
          </Text>
          <View style={{ display: 'flex', gap: '10px' }}>
            <Button
              className='action-btn'
              onClick={handleAction}
              disabled={!selectedTarget}
            >
              确认行动
            </Button>
            <Button
              className='action-btn'
              onClick={handleSkip}
              style={{ background: 'rgba(40,36,32,0.8)', border: '1px solid #4a3d30' }}
            >
              跳过
            </Button>
          </View>
        </View>
      )}

      {/* 聊天区域 */}
      <View className='chat-section'>
        <ScrollView className='chat-messages' scrollY>
          {chatMessages.length === 0 ? (
            <View style={{ textAlign: 'center', padding: '20px', color: '#8a7a68' }}>
              暗夜寂静...
            </View>
          ) : (
            chatMessages.map((msg, index) => (
              <View key={index} className='chat-item'>
                <Text className='chat-sender'>{msg.senderName}:</Text>
                <Text className='chat-content'>{msg.content}</Text>
              </View>
            ))
          )}
        </ScrollView>
      </View>

      {/* 角色信息弹窗 */}
      {showRoleModal && (
        <View className='modal-overlay' onClick={() => setShowRoleModal(false)}>
          <View className='modal-content' onClick={(e) => e.stopPropagation()}>
            <Text className='modal-title'>你的身份</Text>
            <Text className='modal-role'>{getRoleName(game?.myRole)}</Text>
            <Text style={{ display: 'block', textAlign: 'center', fontSize: '13px', color: '#b0a090', marginBottom: '20px' }}>
              {game?.mySeat}号位
            </Text>
            <Button className='modal-close' onClick={() => setShowRoleModal(false)}>
              知道了
            </Button>
          </View>
        </View>
      )}
    </View>
  )
}
