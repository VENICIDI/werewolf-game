import { useEffect, useState, useCallback } from 'react'
import Taro, { useRouter } from '@tarojs/taro'
import { View, Text, Button, ScrollView, Input } from '@tarojs/components'
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
  teammates?: number[]  // 狼人队友 ID 列表
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
  const [chatInput, setChatInput] = useState('')
  const [speakingPlayerIds, setSpeakingPlayerIds] = useState<Set<number>>(new Set())
  const [selectedTarget, setSelectedTarget] = useState<number | null>(null)
  const [phaseTimeLeft, setPhaseTimeLeft] = useState(0)
  const [showRoleModal, setShowRoleModal] = useState(false)
  // ✨ 女巫信息
  const [witchInfo, setWitchInfo] = useState<{
    hasSave: boolean, hasPoison: boolean,
    killTargetId?: number, killTargetSeat?: number, killTargetName?: string
  } | null>(null)
  const [witchAction, setWitchAction] = useState<'none' | 'save' | 'poison'>('none')
  // ✨ 猎人开枪
  const [canHunterShoot, setCanHunterShoot] = useState(false)

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
        teammates: playersRes.teammates || [],
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
      wsManager.on('SEER_RESULT', handleSeerResult)
      wsManager.on('WITCH_INFO', handleWitchInfo)
      wsManager.on('HUNTER_SHOOT', handleHunterShoot)
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
    setSpeakingPlayerIds(new Set())
    setWitchAction('none')
    // 进入女巫阶段时不清除 witchInfo（WITCH_INFO 消息可能先到）
    if (data.phase !== 'WITCH') {
      setWitchInfo(null)
    }

    // 显示阶段提示
    if (data.message) {
      Taro.showToast({ title: data.message, icon: 'none', duration: 2000 })
    }
  }, [])

  const handlePlayerAction = useCallback((message: any) => {
    console.log('[Game] 玩家行动:', message.data)
  }, [])

  const handlePlayerChat = useCallback((message: any) => {
    const senderId = message.senderId
    setChatMessages(prev => [...prev, {
      senderId,
      senderName: message.senderName,
      content: message.data?.content || '',
      timestamp: message.timestamp
    }])
    // 标记发言者高亮
    setSpeakingPlayerIds(prev => new Set(prev).add(senderId))
    // 3秒后取消高亮
    setTimeout(() => {
      setSpeakingPlayerIds(prev => {
        const next = new Set(prev)
        next.delete(senderId)
        return next
      })
    }, 3000)
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
    console.log('[Game] 角色分配:', data.role, '队友:', data.teammates)
    setGame(prev => prev ? {
      ...prev,
      myRole: data.role,
      myPlayerId: data.playerId,
      teammates: data.teammates || [],
    } : null)
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
    // 更新被处决玩家状态为 DEAD
    if (data.eliminatedPlayerId) {
      setGame(prev => {
        if (!prev) return null
        const updatedPlayers = prev.players.map(p =>
          p.playerId === data.eliminatedPlayerId ? { ...p, status: 'DEAD' } : p
        )
        return { ...prev, players: updatedPlayers }
      })
    }
  }, [])

  const handleActionConfirm = useCallback((message: any) => {
    Taro.showToast({ title: '行动已确认', icon: 'success' })
  }, [])

  const handleSeerResult = useCallback((message: any) => {
    const data = message.data
    const isWolf = data?.isWerewolf
    Taro.showModal({
      title: '🔮 查验结果',
      content: data?.message || `${data?.targetSeat}号玩家是${isWolf ? '🐺 狼人' : '👤 好人'}`,
      showCancel: false,
    })
  }, [])

  const handleWitchInfo = useCallback((message: any) => {
    const data = message.data
    setWitchInfo({
      hasSave: data?.hasSave ?? false,
      hasPoison: data?.hasPoison ?? false,
      killTargetId: data?.killTargetId,
      killTargetSeat: data?.killTargetSeat,
      killTargetName: data?.killTargetName,
    })
  }, [])

  const handleHunterShoot = useCallback((message: any) => {
    const data = message.data
    if (data?.canShoot) {
      // 通知猎人可以开枪
      setCanHunterShoot(true)
      Taro.showToast({ title: '你已死亡，请选择开枪目标！', icon: 'none', duration: 3000 })
    } else {
      // 广播猎人开枪结果
      if (data?.message) {
        Taro.showToast({ title: data.message, icon: 'none', duration: 3000 })
      }
      // 更新被射杀玩家状态
      if (data?.targetSeat) {
        setGame(prev => {
          if (!prev) return null
          const updatedPlayers = prev.players.map(p =>
            p.seatNumber === data.targetSeat ? { ...p, status: 'DEAD' } : p
          )
          return { ...prev, players: updatedPlayers }
        })
      }
    }
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

    // ✨ 猎人开枪（死亡后可行动）
    if (canHunterShoot && game.myRole === 'HUNTER') return true

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
    // 猎人开枪
    if (canHunterShoot && game?.myRole === 'HUNTER') return 'shoot'

    switch (game?.phase) {
      case 'WEREWOLF': return 'kill'
      case 'SEER': return 'check'
      case 'WITCH': return witchAction === 'save' ? 'save' : witchAction === 'poison' ? 'poison' : 'skip'
      case 'GUARD': return 'guard'
      case 'VOTING': return 'vote'
      default: return 'skip'
    }
  }

  const handleAction = async () => {
    if (!game) return
    const actionType = getActionType()

    // 女巫救人不需要选目标
    if (actionType === 'save') {
      try {
        await post(`/games/${game.gameId}/action`, { action: 'save' })
        setWitchAction('none')
        setWitchInfo(null)
      } catch (error: any) {
        Taro.showToast({ title: error.message || '行动失败', icon: 'none' })
      }
      return
    }

    if (!selectedTarget) {
      Taro.showToast({ title: '请选择目标', icon: 'none' })
      return
    }
    try {
      const res: any = await post(`/games/${game.gameId}/action`, {
        action: actionType,
        targetId: selectedTarget
      })
      // 预言家查验：直接从 HTTP 响应弹出结果（不依赖 WebSocket SEER_RESULT）
      if (actionType === 'check' && res) {
        const isWolf = res.isWerewolf
        Taro.showModal({
          title: '🔮 查验结果',
          content: res.message || `${res.targetSeat}号玩家是${isWolf ? '🐺 狼人' : '👤 好人'}`,
          showCancel: false,
        })
      }
      // 猎人开枪后清除状态
      if (actionType === 'shoot') {
        setCanHunterShoot(false)
      }
      if (actionType === 'poison') {
        setWitchAction('none')
        setWitchInfo(null)
      }
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

  // 判断当前阶段是否可以发送聊天消息（白天讨论阶段存活玩家可发言）
  const canChat = () => {
    if (!game) return false
    const myPlayer = game.players.find(p => p.playerId === game.myPlayerId)
    if (!myPlayer || myPlayer.status !== 'ALIVE') return false
    return ['DISCUSSION', 'DAY_START'].includes(game.phase)
  }

  const getActingRoleForPhase = (phase?: string) => {
    const roleByPhase: Record<string, string> = {
      WEREWOLF: 'WEREWOLF',
      SEER: 'SEER',
      WITCH: 'WITCH',
      GUARD: 'GUARD',
      HUNTER: 'HUNTER',
    }
    return roleByPhase[phase || '']
  }

  const getPlayerHighlight = (player: PlayerInfo) => {
    if (!game || player.status !== 'ALIVE') {
      return { active: false, className: '', label: '' }
    }

    if (['DAY_START', 'DISCUSSION'].includes(game.phase)) {
      return { active: true, className: 'active-speaker', label: '可发言' }
    }

    const actingRole = getActingRoleForPhase(game.phase)
    if (!actingRole) {
      return { active: false, className: '', label: '' }
    }

    const isMe = player.playerId === game.myPlayerId
    const isWerewolfTeammate = actingRole === 'WEREWOLF' && game.teammates?.includes(player.playerId)
    const roleVisibleToMe = isMe ? game.myRole : player.role
    const isActingPlayer = roleVisibleToMe === actingRole || isWerewolfTeammate

    return {
      active: isActingPlayer,
      className: isActingPlayer ? `active-turn active-${actingRole.toLowerCase()}` : '',
      label: isActingPlayer ? '行动中' : '',
    }
  }

  // 发送聊天消息
  const handleSendChat = () => {
    if (!chatInput.trim()) return
    wsManager.sendChat(chatInput)
    setChatInput('')
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
        {/* 狼人队友提示 - 始终显示 */}
        {game?.myRole === 'WEREWOLF' && game?.teammates && game.teammates.length > 0 && (
          <View style={{ padding: '6px 12px', marginBottom: '8px', background: 'rgba(180,40,40,0.2)', borderRadius: '6px', border: '1.5px solid rgba(180,40,40,0.4)' }}>
            <Text style={{ fontSize: '13px', color: '#ff4444', fontWeight: 'bold' }}>
              🐺 你的狼队友: {game.teammates.map(tid => {
                const p = game.players.find(pl => pl.playerId === tid)
                return p ? `${p.seatNumber}号 ${p.username}` : `ID${tid}`
              }).join('、')}
            </Text>
          </View>
        )}
        <View className='players-grid'>
          {game?.players.map((player) => {
            const isTeammate = game?.myRole === 'WEREWOLF' && game?.teammates?.includes(player.playerId)
            const isMe = player.playerId === game?.myPlayerId
            const highlight = getPlayerHighlight(player)
            return (
              <View
                key={player.playerId}
                className={`player-card ${player.status} ${selectedTarget === player.playerId ? 'selected' : ''} ${speakingPlayerIds.has(player.playerId) ? 'speaking' : ''} ${isTeammate ? 'werewolf-teammate' : ''} ${highlight.className}`}
                onClick={() => {
                  if (!canAct() || player.status !== 'ALIVE') return
                  // 狼人阶段不能选自己和队友
                  if (game?.phase === 'WEREWOLF' && (isTeammate || isMe)) return
                  setSelectedTarget(player.playerId)
                }}
              >
                <View className='player-avatar'>
                  <Text className='avatar-default'>
                    {player.status === 'DEAD' ? '💀'
                      : (isTeammate || (isMe && game?.myRole === 'WEREWOLF')) ? '🐺'
                      : player.isAi ? '🤖' : '👤'}
                  </Text>
                </View>
                <Text className='player-name'>{player.username}</Text>
                <Text className='seat-num'>{player.seatNumber}号位</Text>
                {isMe && game?.myRole === 'WEREWOLF' && (
                  <Text style={{ fontSize: '10px', color: '#ff4444', textAlign: 'center', fontWeight: 'bold' }}>🐺 你</Text>
                )}
                {isTeammate && (
                  <Text style={{ fontSize: '10px', color: '#ff4444', textAlign: 'center', fontWeight: 'bold' }}>🐺 队友</Text>
                )}
                {highlight.active && <Text className='active-badge'>{highlight.label}</Text>}
                {player.status === 'DEAD' && <Text className='dead-badge'>已死亡</Text>}
              </View>
            )
          })}
        </View>
      </View>

      {/* 行动按钮 */}
      {canAct() && (
        <View className='action-section'>
          {/* ✨ 女巫专用 UI */}
          {game?.phase === 'WITCH' && game?.myRole === 'WITCH' ? (
            <View>
              {/* 药水状态 */}
              <View style={{ display: 'flex', justifyContent: 'center', gap: '16px', marginBottom: '10px' }}>
                <Text style={{ fontSize: '12px', color: witchInfo?.hasSave ? '#4caf50' : '#666' }}>
                  💊 解药{witchInfo?.hasSave ? '✓' : '✗ 已用'}
                </Text>
                <Text style={{ fontSize: '12px', color: witchInfo?.hasPoison ? '#9c27b0' : '#666' }}>
                  ☠️ 毒药{witchInfo?.hasPoison ? '✓' : '✗ 已用'}
                </Text>
              </View>

              {/* 被杀者信息 - 醒目红色 */}
              {witchInfo?.killTargetSeat ? (
                <View style={{ padding: '10px 14px', marginBottom: '12px', background: 'rgba(255,60,60,0.12)', borderRadius: '8px', border: '1.5px solid rgba(255,60,60,0.4)', textAlign: 'center' }}>
                  <Text style={{ fontSize: '16px', color: '#ff4444', fontWeight: 'bold', display: 'block' }}>
                    💀 今晚 {witchInfo.killTargetSeat}号 {witchInfo.killTargetName} 被狼人杀害
                  </Text>
                  {witchInfo?.hasSave && (
                    <Text style={{ fontSize: '12px', color: '#4caf50', display: 'block', marginTop: '4px' }}>
                      你可以使用解药救活TA
                    </Text>
                  )}
                </View>
              ) : (
                <View style={{ padding: '8px 14px', marginBottom: '12px', background: 'rgba(100,100,100,0.1)', borderRadius: '8px', textAlign: 'center' }}>
                  <Text style={{ fontSize: '13px', color: '#8a7a68' }}>
                    🌙 今晚是平安夜，无人被杀
                  </Text>
                </View>
              )}

              {/* 行动按钮组 */}
              <View style={{ display: 'flex', gap: '8px', justifyContent: 'center', flexWrap: 'wrap' }}>
                {/* 解药按钮 - 只有有人死且有解药时才显示 */}
                {witchInfo?.hasSave && witchInfo?.killTargetSeat && (
                  <Button
                    className='action-btn'
                    style={{
                      background: witchAction === 'save'
                        ? 'linear-gradient(180deg, rgba(76,175,80,0.95) 0%, rgba(46,125,50,0.95) 100%)'
                        : 'rgba(76,175,80,0.4)',
                      border: witchAction === 'save' ? '2px solid #4caf50' : '1.5px solid rgba(76,175,80,0.4)',
                      flex: '1', minWidth: '90px', maxWidth: '130px'
                    }}
                    onClick={() => { setWitchAction('save'); setSelectedTarget(null) }}
                  >
                    💊 使用解药
                  </Button>
                )}
                {/* 毒药按钮 */}
                {witchInfo?.hasPoison && (
                  <Button
                    className='action-btn'
                    style={{
                      background: witchAction === 'poison'
                        ? 'linear-gradient(180deg, rgba(156,39,176,0.95) 0%, rgba(106,27,154,0.95) 100%)'
                        : 'rgba(156,39,176,0.4)',
                      border: witchAction === 'poison' ? '2px solid #9c27b0' : '1.5px solid rgba(156,39,176,0.4)',
                      flex: '1', minWidth: '90px', maxWidth: '130px'
                    }}
                    onClick={() => { setWitchAction('poison'); setSelectedTarget(null) }}
                  >
                    ☠️ 使用毒药
                  </Button>
                )}
                {/* 跳过 */}
                <Button
                  className='action-btn'
                  style={{ background: 'rgba(30,26,22,0.9)', border: '1.5px solid rgba(74,61,48,0.4)', boxShadow: 'none', flex: '1', minWidth: '90px', maxWidth: '130px' }}
                  onClick={handleSkip}
                >
                  🌙 不使用
                </Button>
              </View>

              {/* 解药确认 */}
              {witchAction === 'save' && (
                <View style={{ marginTop: '10px', textAlign: 'center', padding: '8px', background: 'rgba(76,175,80,0.1)', borderRadius: '6px', border: '1px solid rgba(76,175,80,0.3)' }}>
                  <Text style={{ color: '#4caf50', fontSize: '14px', fontWeight: 'bold', display: 'block' }}>
                    💊 将使用解药救活 {witchInfo?.killTargetSeat}号 {witchInfo?.killTargetName}
                  </Text>
                  <Button className='action-btn' style={{ marginTop: '8px', background: 'linear-gradient(180deg, rgba(76,175,80,0.9) 0%, rgba(46,125,50,0.95) 100%)' }} onClick={handleAction}>
                    ◈ 确认救人 ◈
                  </Button>
                </View>
              )}

              {/* 毒药确认 - 需要选择目标 */}
              {witchAction === 'poison' && (
                <View style={{ marginTop: '10px', textAlign: 'center', padding: '8px', background: 'rgba(156,39,176,0.1)', borderRadius: '6px', border: '1px solid rgba(156,39,176,0.3)' }}>
                  <Text style={{ color: '#9c27b0', fontSize: '14px', fontWeight: 'bold', display: 'block' }}>
                    {selectedTarget
                      ? `☠️ 将使用毒药毒杀 ${game?.players.find(p => p.playerId === selectedTarget)?.seatNumber}号 ${game?.players.find(p => p.playerId === selectedTarget)?.username}`
                      : '☝️ 请在上方玩家列表中选择毒杀目标'}
                  </Text>
                  <Button className='action-btn' style={{ marginTop: '8px', background: 'linear-gradient(180deg, rgba(156,39,176,0.9) 0%, rgba(106,27,154,0.95) 100%)' }} onClick={handleAction} disabled={!selectedTarget}>
                    ◈ 确认毒人 ◈
                  </Button>
                </View>
              )}

              {/* 未选择时的提示 */}
              {witchAction === 'none' && (
                <Text style={{ display: 'block', textAlign: 'center', marginTop: '8px', fontSize: '12px', color: '#8a7a68' }}>
                  选择使用解药、毒药或跳过本轮
                </Text>
              )}
            </View>
          ) : (
            /* 通用行动 UI */
            <View>
              {canHunterShoot && game?.myRole === 'HUNTER' && (
                <Text style={{ display: 'block', textAlign: 'center', marginBottom: '6px', color: '#ff6b6b', fontSize: '13px', fontWeight: 'bold' }}>
                  🔫 你已死亡！请选择开枪带走一个人
                </Text>
              )}
              <Text className='action-hint'>
                {selectedTarget
                  ? `⚔ 目标：${game?.players.find(p => p.playerId === selectedTarget)?.username}`
                  : '☞ 请点击上方选择目标'}
              </Text>
              <View style={{ display: 'flex', gap: '10px' }}>
                <Button
                  className='action-btn'
                  onClick={handleAction}
                  disabled={!selectedTarget}
                >
                  ◈ 确认行动 ◈
                </Button>
                {!canHunterShoot && (
                  <Button
                    className='action-btn'
                    onClick={handleSkip}
                    style={{ background: 'rgba(30,26,22,0.9)', border: '1.5px solid rgba(74,61,48,0.4)', boxShadow: 'none' }}
                  >
                    跳过
                  </Button>
                )}
              </View>
            </View>
          )}
        </View>
      )}

      {/* 聊天区域 */}
      <View className='chat-section'>
        <View className='chat-header'>
          <Text className='chat-title'>💬 消息</Text>
          {canChat() && <Text className='chat-hint'>讨论阶段，畅所欲言</Text>}
        </View>
        <ScrollView className='chat-messages' scrollY scrollWithAnimation>
          {chatMessages.length === 0 ? (
            <View style={{ textAlign: 'center', padding: '20px', color: '#8a7a68' }}>
              暗夜寂静...
            </View>
          ) : (
            chatMessages.map((msg, index) => (
              <View key={index} className={`chat-item ${msg.senderId === game?.myPlayerId ? 'me' : ''}`}>
                <Text className='chat-sender'>{msg.senderName}:</Text>
                <Text className='chat-content'>{msg.content}</Text>
              </View>
            ))
          )}
        </ScrollView>

        {/* 聊天输入框 - 讨论阶段可用 */}
        {canChat() ? (
          <View className='chat-input-row'>
            <Input
              className='chat-input'
              placeholder={phaseTimeLeft > 0 ? `发言倒计时 ${phaseTimeLeft}s，发表你的看法...` : '发表你的看法...'}
              value={chatInput}
              onInput={(e) => setChatInput(e.detail.value)}
              onConfirm={handleSendChat}
            />
            <Button className='chat-send-btn' onClick={handleSendChat}>
              发送
            </Button>
          </View>
        ) : (
          <View className='chat-input-disabled'>
            <Text className='chat-disabled-text'>
              {game?.phase === 'VOTING' ? '投票阶段，禁止发言' :
               game?.phase === 'EXECUTION' ? '处决阶段，静默等待' :
               ['NIGHT_START', 'WEREWOLF', 'SEER', 'WITCH', 'GUARD', 'HUNTER'].includes(game?.phase || '') ? '夜晚降临，保持沉默' :
               '当前阶段无法发言'}
            </Text>
          </View>
        )}
      </View>

      {/* 角色信息弹窗 */}
      {showRoleModal && (
        <View className='modal-overlay' onClick={() => setShowRoleModal(false)}>
          <View className='modal-content' onClick={(e) => e.stopPropagation()}>
            <Text className='modal-title'>—— 你的身份 ——</Text>
            <Text style={{ display: 'block', textAlign: 'center', fontSize: '40px', marginBottom: '8px', filter: 'drop-shadow(0 4px 12px rgba(196, 26, 26, 0.4))' }}>
              {game?.myRole === 'WEREWOLF' ? '🐺' : game?.myRole === 'SEER' ? '🔮' : game?.myRole === 'WITCH' ? '🧙' : game?.myRole === 'HUNTER' ? '🔫' : game?.myRole === 'GUARD' ? '🛡️' : game?.myRole === 'IDIOT' ? '🤡' : '👤'}
            </Text>
            <Text className='modal-role'>{getRoleName(game?.myRole)}</Text>
            <Text style={{ display: 'block', textAlign: 'center', fontSize: '11px', color: game?.myRole === 'WEREWOLF' ? '#ff4444' : '#4caf50', letterSpacing: '2px', marginBottom: '4px' }}>
              {game?.myRole === 'WEREWOLF' ? '狼人阵营' : '好人阵营'}
            </Text>
            <Text style={{ display: 'block', textAlign: 'center', fontSize: '12px', color: '#8a7a68', marginBottom: '8px', fontFamily: 'monospace' }}>
              {game?.mySeat}号位
            </Text>
            {/* 狼人队友 */}
            {game?.myRole === 'WEREWOLF' && game?.teammates && game.teammates.length > 0 && (
              <View style={{ padding: '8px 12px', marginBottom: '12px', background: 'rgba(180,40,40,0.15)', borderRadius: '6px', border: '1px solid rgba(180,40,40,0.3)' }}>
                <Text style={{ display: 'block', fontSize: '12px', color: '#ff4444', textAlign: 'center', fontWeight: 'bold' }}>
                  🐺 你的狼队友
                </Text>
                <Text style={{ display: 'block', fontSize: '13px', color: '#ff6666', textAlign: 'center', marginTop: '4px' }}>
                  {game.teammates.map(tid => {
                    const p = game.players.find(pl => pl.playerId === tid)
                    return p ? `${p.seatNumber}号 ${p.username}` : `ID${tid}`
                  }).join('、')}
                </Text>
              </View>
            )}
            <Button className='modal-close' onClick={() => setShowRoleModal(false)}>
              ◈ 知道了 ◈
            </Button>
          </View>
        </View>
      )}
    </View>
  )
}
