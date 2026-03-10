import { useEffect, useState, useCallback } from 'react'
import Taro from '@tarojs/taro'
import { View, Text, Button, ScrollView } from '@tarojs/components'
import { GamePhase, GameStatus, Player, Role } from '../../../api/game'
import { wsManager } from '../../../utils/websocket'
import './index.scss'

interface GameState {
  id: number
  roomCode: string
  status: GameStatus
  currentRound: number
  currentPhase: GamePhase
  players: Player[]
  myRole: Role
  myPlayerId: number
}

interface ChatMessage {
  senderId: number
  senderName: string
  content: string
  timestamp: string
}

export default function GamePlay() {
  const [game, setGame] = useState<GameState | null>(null)
  const [loading, setLoading] = useState(true)
  const [chatMessages, setChatMessages] = useState<ChatMessage[]>([])
  const [selectedTarget, setSelectedTarget] = useState<number | null>(null)
  const [phaseTimeLeft, setPhaseTimeLeft] = useState(30)
  const [showRoleModal, setShowRoleModal] = useState(false)

  // 初始化 WebSocket
  useEffect(() => {
    const roomCode = Taro.getStorageSync('currentRoomCode')
    if (roomCode) {
      connectGameWebSocket(roomCode)
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

  const connectGameWebSocket = async (roomCode: string) => {
    try {
      wsManager.on('GAME_START', handleGameStart)
      wsManager.on('PHASE_CHANGE', handlePhaseChange)
      wsManager.on('PLAYER_ACTION', handlePlayerAction)
      wsManager.on('PLAYER_CHAT', handlePlayerChat)
      wsManager.on('GAME_OVER', handleGameOver)

      await wsManager.connect(roomCode)
    } catch (error) {
      console.error('WebSocket 连接失败:', error)
    }
  }

  const handleGameStart = useCallback((message: any) => {
    setGame(message.data)
    setLoading(false)
  }, [])

  const handlePhaseChange = useCallback((message: any) => {
    setGame(prev => prev ? { ...prev, currentPhase: message.data.phase } : null)
    setPhaseTimeLeft(message.data.duration / 1000)
    setSelectedTarget(null)
  }, [])

  const handlePlayerAction = useCallback((message: any) => {
    // 处理玩家行动
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
    Taro.showModal({
      title: '游戏结束',
      content: message.data,
      showCancel: false,
      success: () => {
        Taro.redirectTo({ url: '/pages/room-list/index' })
      }
    })
  }, [])

  const getPhaseDisplay = () => {
    switch (game?.currentPhase) {
      case GamePhase.NIGHT_START:
        return { title: '天黑了', subtitle: '所有人请闭眼', icon: '🌙', bg: 'night' }
      case GamePhase.WEREWOLF:
        return { title: '狼人行动', subtitle: '请选择要击杀的目标', icon: '🐺', bg: 'night' }
      case GamePhase.SEER:
        return { title: '预言家行动', subtitle: '请选择要查验的目标', icon: '🔮', bg: 'night' }
      case GamePhase.WITCH:
        return { title: '女巫行动', subtitle: '是否使用解药/毒药', icon: '🧙‍♀️', bg: 'night' }
      case GamePhase.DAY_START:
        return { title: '天亮了', subtitle: '第 ' + game?.currentRound + ' 天', icon: '☀️', bg: 'day' }
      case GamePhase.DISCUSSION:
        return { title: '讨论阶段', subtitle: '请发表你的看法', icon: '💬', bg: 'day' }
      case GamePhase.VOTING:
        return { title: '投票阶段', subtitle: '请选择要放逐的玩家', icon: '🗳️', bg: 'day' }
      case GamePhase.EXECUTION:
        return { title: '处决阶段', subtitle: '公布投票结果', icon: '⚖️', bg: 'day' }
      default:
        return { title: '等待中', subtitle: '游戏即将开始', icon: '⏳', bg: 'day' }
    }
  }

  const canAct = () => {
    if (!game) return false
    const myPlayer = game.players.find(p => p.id === game.myPlayerId)
    if (!myPlayer || myPlayer.status !== 'ALIVE') return false

    switch (game.currentPhase) {
      case GamePhase.WEREWOLF:
        return game.myRole === Role.WEREWOLF
      case GamePhase.SEER:
        return game.myRole === Role.SEER
      case GamePhase.WITCH:
        return game.myRole === Role.WITCH
      case GamePhase.VOTING:
        return true
      default:
        return false
    }
  }

  const handleAction = () => {
    if (!selectedTarget) {
      Taro.showToast({ title: '请选择目标', icon: 'none' })
      return
    }
    // TODO: 发送行动请求
    wsManager.send('PLAYER_ACTION', { targetId: selectedTarget })
  }

  const phaseDisplay = getPhaseDisplay()
  const isNight = phaseDisplay.bg === 'night'

  if (loading) {
    return (
      <View className='game-play-container'>
        <View className='loading'>游戏加载中...</View>
      </View>
    )
  }

  return (
    <View className={`game-play-container ${phaseDisplay.bg}`}>
      {/* 顶部信息栏 */}
      <View className='top-bar'>
        <View className='round-info'>
          <Text className='round-text'>第 {game?.currentRound} 天</Text>
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
              key={player.id}
              className={`player-card ${player.status} ${selectedTarget === player.id ? 'selected' : ''}`}
              onClick={() => canAct() && setSelectedTarget(player.id)}
            >
              <View className='player-avatar'>
                {player.avatarUrl ? (
                  <image src={player.avatarUrl} className='avatar-img' />
                ) : (
                  <Text className='avatar-default'>
                    {player.status === 'DEAD' ? '💀' : '👤'}
                  </Text>
                )}
              </View>
              <Text className='player-name'>{player.username}</Text>
              {player.isHost && <Text className='host-badge'>房主</Text>}
              <Text className='seat-num'>{player.seatNumber}号</Text>
              {player.status === 'DEAD' && <Text className='dead-badge'>已死亡</Text>}
            </View>
          ))}
        </View>
      </View>

      {/* 行动按钮 */}
      {canAct() && (
        <View className='action-section'>
          <Text className='action-hint'>
            {selectedTarget ? `已选择 ${game?.players.find(p => p.id === selectedTarget)?.username}` : '请点击选择目标'}
          </Text>
          <Button
            className='action-btn'
            onClick={handleAction}
            disabled={!selectedTarget}
          >
            确认行动
          </Button>
        </View>
      )}

      {/* 聊天区域 */}
      <View className='chat-section'>
        <ScrollView className='chat-messages' scrollY>
          {chatMessages.map((msg, index) => (
            <View key={index} className='chat-item'>
              <Text className='chat-sender'>{msg.senderName}:</Text>
              <Text className='chat-content'>{msg.content}</Text>
            </View>
          ))}
        </ScrollView>
      </View>

      {/* 角色信息弹窗 */}
      {showRoleModal && (
        <View className='modal-overlay' onClick={() => setShowRoleModal(false)}>
          <View className='modal-content'>
            <Text className='modal-title'>你的身份</Text>
            <Text className='modal-role'>{game?.myRole}</Text>
            <Button className='modal-close' onClick={() => setShowRoleModal(false)}>
              关闭
            </Button>
          </View>
        </View>
      )}
    </View>
  )
}
