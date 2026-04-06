import { useEffect, useState, useCallback, useRef } from 'react'
import Taro, { getCurrentInstance } from '@tarojs/taro'
import { View, Text, Button, Input, ScrollView } from '@tarojs/components'
import { getRoomDetail, createRoom, leaveRoom, setReady, addAiPlayer, removeAiPlayer } from '../../api/room'
import { startGame } from '../../api/game'
import { getUserInfo } from '../../api/auth'
import { checkLogin } from '../../utils/auth-guard'
import { isLoggedIn } from '../../api/auth'
import { wsManager, WebSocketState } from '../../utils/websocket'
import './index.scss'

interface Player {
  userId: number
  username: string
  avatarUrl: string
  isHost: boolean
  isReady?: boolean
  isAi?: boolean
}

interface RoomData {
  id: number
  roomCode: string
  roomName: string
  hostId: number
  hostName: string
  maxPlayers: number
  currentPlayers: number
  hasPassword: boolean
  status: string
  players: Player[]
}

interface ChatMessage {
  senderId: number
  senderName: string
  content: string
  timestamp: string
}

export default function Room() {
  const [room, setRoom] = useState<RoomData | null>(null)
  const roomRef = useRef<RoomData | null>(null)
  const [loading, setLoading] = useState(false)
  const [isCreating, setIsCreating] = useState(false)
  const [isReady, setIsReady] = useState(false)
  const [chatMessages, setChatMessages] = useState<ChatMessage[]>([])
  const [chatInput, setChatInput] = useState('')
  const [wsConnected, setWsConnected] = useState(false)

  // 创建房间表单
  const [roomName, setRoomName] = useState('')
  const [maxPlayers, setMaxPlayers] = useState(9)
  const [password, setPassword] = useState('')

  const { router } = getCurrentInstance()
  const roomCode = router?.params?.code
  const action = router?.params?.action

  useEffect(() => {
    console.log('[Room] useEffect 触发, action:', action, 'roomCode:', roomCode, 'isLoggedIn:', isLoggedIn())
    if (!isLoggedIn()) {
      Taro.showToast({ title: '请先登录', icon: 'none' })
      setTimeout(() => {
        Taro.redirectTo({ url: '/pages/login/index' })
      }, 1500)
      return
    }

    if (action === 'create') {
      setIsCreating(true)
    } else if (roomCode) {
      fetchRoomDetail(roomCode)
      connectWebSocket(roomCode)
      
      // 超时保护：10秒未加载完成则自动返回
      const timeout = setTimeout(() => {
        if (!roomRef.current) {
          Taro.showToast({ title: '加载超时，请重试', icon: 'none' })
          setTimeout(() => Taro.navigateBack(), 1500)
        }
      }, 10000)
      
      return () => {
        clearTimeout(timeout)
        wsManager.disconnect()
      }
    } else {
      // 兜底：参数缺失，返回
      console.warn('[Room] 缺少 action 或 roomCode 参数，返回上一页')
      Taro.showToast({ title: '参数错误', icon: 'none' })
      setTimeout(() => Taro.navigateBack(), 1000)
    }
  }, [])

  const connectWebSocket = async (code: string) => {
    try {
      wsManager.on('JOIN_ROOM', handlePlayerJoin)
      wsManager.on('LEAVE_ROOM', handlePlayerLeave)
      wsManager.on('ROOM_UPDATE', handleRoomUpdate)
      wsManager.on('PLAYER_READY', handlePlayerReady)
      wsManager.on('PLAYER_CHAT', handlePlayerChat)
      wsManager.on('HEARTBEAT_ACK', handleHeartbeatAck)
      wsManager.on('GAME_START', handleGameStart)

      wsManager.setOnOpen(() => {
        setWsConnected(true)
        console.log('WebSocket 已连接')
      })

      wsManager.setOnClose(() => {
        setWsConnected(false)
        console.log('WebSocket 已断开')
      })

      wsManager.setOnError((error) => {
        console.error('WebSocket 错误:', error)
      })

      await wsManager.connect(code)
    } catch (error: any) {
      console.error('WebSocket 连接失败:', error)
      Taro.showToast({ title: '实时连接失败', icon: 'none' })
    }
  }

  const handlePlayerJoin = useCallback((message: any) => {
    Taro.showToast({ title: `${message.senderName} 加入了房间`, icon: 'none' })
    if (roomCode) fetchRoomDetail(roomCode)
  }, [roomCode])

  const handlePlayerLeave = useCallback((message: any) => {
    Taro.showToast({ title: `${message.senderName} 离开了房间`, icon: 'none' })
    if (roomCode) fetchRoomDetail(roomCode)
  }, [roomCode])

  const handleRoomUpdate = useCallback((message: any) => {
    setRoom(message.data)
  }, [])

  const handlePlayerReady = useCallback((message: any) => {
    const userInfo = getUserInfo()
    if (message.senderId === userInfo?.id) {
      setIsReady(message.data)
    }
    Taro.showToast({
      title: `${message.senderName} ${message.data ? '已准备' : '取消准备'}`,
      icon: 'none'
    })
    if (roomCode) fetchRoomDetail(roomCode)
  }, [roomCode])

  const handlePlayerChat = useCallback((message: any) => {
    const newMessage: ChatMessage = {
      senderId: message.senderId,
      senderName: message.senderName,
      content: message.data?.content || '',
      timestamp: message.timestamp
    }
    setChatMessages(prev => [...prev, newMessage])
  }, [])

  const handleHeartbeatAck = useCallback(() => {}, [])

  const handleGameStart = useCallback((message: any) => {
    const gameId = message.data?.gameId
    console.log('[Room] 收到 GAME_START, gameId:', gameId)
    if (gameId && roomCode) {
      Taro.setStorageSync('currentRoomCode', roomCode)
      Taro.setStorageSync('currentGameId', gameId)
      Taro.redirectTo({ url: `/pages/game/play/index?gameId=${gameId}&roomCode=${roomCode}` })
    }
  }, [roomCode])

  const fetchRoomDetail = async (code: string) => {
    setLoading(true)
    console.log('[Room] fetchRoomDetail 开始, code:', code)
    try {
      const res: any = await getRoomDetail(code)
      console.log('[Room] fetchRoomDetail 成功:', JSON.stringify(res))
      setRoom(res)
      roomRef.current = res
      const userInfo = getUserInfo()
      const me = res.players?.find((p: Player) => p.userId === userInfo?.id)
      if (me?.isReady) setIsReady(true)
    } catch (error: any) {
      console.error('[Room] fetchRoomDetail 失败:', error)
      Taro.showToast({ title: error.message || '获取房间信息失败', icon: 'none' })
      setTimeout(() => {
        Taro.switchTab({ url: '/pages/room-list/index' })
      }, 1500)
    } finally {
      setLoading(false)
    }
  }

  const handleCreateRoom = async () => {
    console.log('[Room] handleCreateRoom 点击')
    if (!roomName.trim()) {
      Taro.showToast({ title: '请输入房间名称', icon: 'none' })
      return
    }

    try {
      Taro.showLoading({ title: '创建中...' })
      console.log('[Room] 调用 createRoom API...')
      const res: any = await createRoom({
        roomName,
        maxPlayers,
        password: password || undefined
      })
      console.log('[Room] createRoom 返回:', JSON.stringify(res))
      Taro.hideLoading()
      Taro.showToast({ title: '创建成功', icon: 'success' })
      const targetUrl = `/pages/room/index?code=${res.roomCode}`
      console.log('[Room] 准备跳转到:', targetUrl)
      setTimeout(() => {
        console.log('[Room] 执行 redirectTo')
        Taro.redirectTo({ url: targetUrl })
      }, 500)
    } catch (error: any) {
      Taro.hideLoading()
      Taro.showToast({ title: error.message || '创建失败', icon: 'none' })
    }
  }

  const handleLeaveRoom = async () => {
    if (!room) return
    Taro.showModal({
      title: '提示',
      content: '确定要离开房间吗？',
      success: async (res) => {
        if (res.confirm) {
          try {
            await leaveRoom(room.roomCode)
            wsManager.disconnect()
            Taro.showToast({ title: '已离开房间', icon: 'success' })
            setTimeout(() => {
              Taro.switchTab({ url: '/pages/room-list/index' })
            }, 500)
          } catch (error: any) {
            Taro.showToast({ title: error.message || '操作失败', icon: 'none' })
          }
        }
      }
    })
  }

  const handleReady = async () => {
    if (!room) return
    try {
      wsManager.sendReady(!isReady)
      await setReady(room.roomCode, !isReady)
      setIsReady(!isReady)
      Taro.showToast({ title: isReady ? '已取消准备' : '已准备', icon: 'success' })
    } catch (error: any) {
      Taro.showToast({ title: error.message || '操作失败', icon: 'none' })
    }
  }

  const handleSendChat = () => {
    if (!chatInput.trim()) return
    wsManager.sendChat(chatInput)
    setChatInput('')
  }

  const handleStartGame = async () => {
    if (!room) return
    // 根据实际人数选择游戏模式
    const playerCount = room.currentPlayers
    let gameModeId = 'standard_9'
    if (playerCount <= 6) gameModeId = 'standard_6'
    else if (playerCount <= 9) gameModeId = 'standard_9'
    else gameModeId = 'standard_12'

    try {
      Taro.showLoading({ title: '血月升起中...' })
      const res: any = await startGame(room.id, gameModeId)
      Taro.hideLoading()
      console.log('[Room] 游戏开始:', JSON.stringify(res))
      Taro.setStorageSync('currentRoomCode', room.roomCode)
      Taro.setStorageSync('currentGameId', res.gameId)
      Taro.redirectTo({ url: `/pages/game/play/index?gameId=${res.gameId}&roomCode=${room.roomCode}` })
    } catch (error: any) {
      Taro.hideLoading()
      Taro.showToast({ title: error.message || '开始游戏失败', icon: 'none' })
    }
  }

  const handleAddAi = async () => {
    if (!room) return
    try {
      Taro.showLoading({ title: '召唤中...' })
      await addAiPlayer(room.roomCode)
      Taro.hideLoading()
      Taro.showToast({ title: '人机猎手已加入', icon: 'success' })
      fetchRoomDetail(room.roomCode)
    } catch (error: any) {
      Taro.hideLoading()
      Taro.showToast({ title: error.message || '添加人机失败', icon: 'none' })
    }
  }

  const handleRemoveAi = async () => {
    if (!room) return
    try {
      await removeAiPlayer(room.roomCode)
      Taro.showToast({ title: '已移除一个人机', icon: 'success' })
      fetchRoomDetail(room.roomCode)
    } catch (error: any) {
      Taro.showToast({ title: error.message || '移除人机失败', icon: 'none' })
    }
  }

  const getRoleDesc = (num: number) => {
    switch (num) {
      case 6: return '4村民 + 2狼人'
      case 9: return '3村民 + 3神职 + 3狼人'
      case 12: return '4村民 + 4神职 + 4狼人'
      default: return ''
    }
  }

  // ====== 创建房间界面 ======
  if (isCreating) {
    return (
      <View className='room-container'>
        <View className='nav-header'>
          <View className='back-btn' onClick={() => Taro.navigateBack()}>
            <Text className='back-icon'>←</Text>
          </View>
          <Text className='nav-title'>创建房间</Text>
          <View className='nav-placeholder'></View>
        </View>

        <View className='create-form'>
          {/* 顶部装饰 */}
          <View className='form-deco-top'>
            <Text className='form-deco-icon'>⚔</Text>
            <Text className='form-deco-text'>设立你的猎场</Text>
          </View>

          {/* 房间名称 */}
          <View className='form-group'>
            <View className='form-label-row'>
              <Text className='form-label-icon'>📝</Text>
              <Text className='form-label'>房间名称</Text>
            </View>
            <Input
              className='form-input'
              placeholder='给你的房间起个名字'
              value={roomName}
              onInput={(e) => setRoomName(e.detail.value)}
              maxLength={20}
            />
          </View>

          {/* 人数设置 */}
          <View className='form-group'>
            <View className='form-label-row'>
              <Text className='form-label-icon'>👥</Text>
              <Text className='form-label'>人数设置</Text>
            </View>
            <View className='player-options'>
              {[6, 9, 12].map((num) => (
                <View
                  key={num}
                  className={`player-option ${maxPlayers === num ? 'active' : ''}`}
                  onClick={() => setMaxPlayers(num)}
                >
                  <Text className='option-num'>{num}</Text>
                  <Text className='option-unit'>人</Text>
                  <Text className='option-desc'>{getRoleDesc(num)}</Text>
                  {maxPlayers === num && <View className='option-check'>✓</View>}
                </View>
              ))}
            </View>
          </View>

          {/* 房间密码 */}
          <View className='form-group'>
            <View className='form-label-row'>
              <Text className='form-label-icon'>🔒</Text>
              <Text className='form-label'>房间密码</Text>
              <Text className='form-label-hint'>可选</Text>
            </View>
            <Input
              className='form-input'
              type='password'
              placeholder='不设置则为公开房间'
              value={password}
              onInput={(e) => setPassword(e.detail.value)}
              maxLength={20}
            />
          </View>

          {/* 创建按钮 */}
          <Button className='form-submit-btn' onClick={handleCreateRoom}>
            <Text className='btn-deco'>◈</Text>
            <Text className='btn-text'>创建房间</Text>
            <Text className='btn-deco'>◈</Text>
          </Button>
        </View>
      </View>
    )
  }

  // ====== 加载中 ======
  if (!room) {
    return (
      <View className='room-container'>
        <View className='loading-state'>
          <Text className='loading-icon'>🌙</Text>
          <Text className='loading-text'>穿越暗夜迷雾...</Text>
        </View>
      </View>
    )
  }

  const userInfo = getUserInfo()
  const isHost = userInfo?.id === room.hostId
  const readyCount = room.players?.filter(p => p.isReady || p.isHost).length || 0

  return (
    <View className='room-container'>
      {/* 顶部导航 */}
      <View className='nav-header'>
        <View className='back-btn' onClick={() => Taro.navigateBack()}>
          <Text className='back-icon'>←</Text>
        </View>
        <View className='nav-center'>
          <Text className='nav-room-name'>{room.roomName}</Text>
          <View className='nav-meta'>
            <Text className='nav-room-code'>{room.roomCode}</Text>
            <View className={`ws-indicator ${wsConnected ? 'on' : 'off'}`}>
              <Text className='ws-dot'></Text>
              <Text className='ws-label'>{wsConnected ? '已连接' : '未连接'}</Text>
            </View>
          </View>
        </View>
        <View className='nav-placeholder'></View>
      </View>

      <ScrollView className='room-body' scrollY>
        {/* 房间信息卡片 */}
        <View className='room-info-card'>
          <View className='info-row'>
            <View className='info-cell'>
              <Text className='info-cell-label'>房主</Text>
              <Text className='info-cell-value'>👤 {room.hostName}</Text>
            </View>
            <View className='info-cell-divider'></View>
            <View className='info-cell'>
              <Text className='info-cell-label'>人数</Text>
              <Text className='info-cell-value'>{room.currentPlayers}/{room.maxPlayers}</Text>
            </View>
            <View className='info-cell-divider'></View>
            <View className='info-cell'>
              <Text className='info-cell-label'>已准备</Text>
              <Text className='info-cell-value ready-value'>{readyCount}/{room.currentPlayers}</Text>
            </View>
          </View>
          {/* 进度条 */}
          <View className='info-progress'>
            <View className='info-progress-bar'>
              <View
                className='info-progress-fill'
                style={{ width: `${(room.currentPlayers / room.maxPlayers) * 100}%` }}
              ></View>
            </View>
          </View>
        </View>

        {/* 玩家列表 */}
        <View className='players-section'>
          <View className='section-header'>
            <Text className='section-icon'>⚔</Text>
            <Text className='section-title'>猎人集结</Text>
            <View className='section-line'></View>
          </View>

          {/* 人机操作区（仅房主可见） */}
          {isHost && room.currentPlayers < room.maxPlayers && (
            <View className='ai-actions'>
              <Button className='ai-add-btn' onClick={handleAddAi}>
                <Text className='ai-btn-icon'>🤖</Text>
                <Text className='ai-btn-text'>添加人机</Text>
              </Button>
              {room.players?.some(p => p.isAi) && (
                <Button className='ai-remove-btn' onClick={handleRemoveAi}>
                  <Text className='ai-btn-text'>移除人机</Text>
                </Button>
              )}
            </View>
          )}

          <View className='players-grid'>
            {room.players?.map((player, index) => (
              <View
                key={player.userId}
                className={`player-card ${player.isReady ? 'ready' : ''} ${player.isHost ? 'host' : ''}`}
              >
                <View className='player-avatar'>
                  {player.avatarUrl ? (
                    <image src={player.avatarUrl} className='avatar-img' />
                  ) : (
                    <Text className='avatar-emoji'>
                      {player.isAi ? '🤖' : player.isHost ? '👑' : '🐺'}
                    </Text>
                  )}
                  {player.isReady && (
                    <View className='ready-mark'>
                      <Text className='ready-mark-text'>✓</Text>
                    </View>
                  )}
                </View>
                <Text className='player-name'>{player.username}</Text>
                <View className='player-badges'>
                  {player.isHost && <Text className='badge host-badge'>房主</Text>}
                  {player.isAi && <Text className='badge ai-badge'>人机</Text>}
                  {player.isReady && !player.isHost && !player.isAi && <Text className='badge ready-badge'>已准备</Text>}
                </View>
                <Text className='seat-number'>{index + 1}号位</Text>
              </View>
            ))}

            {/* 空位 */}
            {Array.from({ length: room.maxPlayers - (room.players?.length || 0) }).map((_, i) => (
              <View key={`empty-${i}`} className='player-card empty'>
                <View className='player-avatar empty'>
                  <Text className='avatar-emoji'>+</Text>
                </View>
                <Text className='player-name empty-name'>等待加入</Text>
                <Text className='seat-number'>{(room.players?.length || 0) + i + 1}号位</Text>
              </View>
            ))}
          </View>
        </View>

        {/* 聊天区域 */}
        <View className='chat-section'>
          <View className='section-header'>
            <Text className='section-icon'>💬</Text>
            <Text className='section-title'>聊天</Text>
            <View className='section-line'></View>
          </View>

          <View className='chat-box'>
            <ScrollView className='chat-messages' scrollY scrollWithAnimation>
              {chatMessages.length === 0 ? (
                <View className='chat-empty'>
                  <Text className='chat-empty-text'>暂无消息，来打个招呼吧 👋</Text>
                </View>
              ) : (
                chatMessages.map((msg, index) => {
                  const isMe = msg.senderId === userInfo?.id
                  return (
                    <View key={index} className={`chat-item ${isMe ? 'me' : ''}`}>
                      <Text className='chat-sender'>{msg.senderName}</Text>
                      <Text className='chat-content'>{msg.content}</Text>
                    </View>
                  )
                })
              )}
            </ScrollView>

            <View className='chat-input-row'>
              <Input
                className='chat-input'
                placeholder='说点什么...'
                value={chatInput}
                onInput={(e) => setChatInput(e.detail.value)}
                onConfirm={handleSendChat}
              />
              <Button className='chat-send-btn' onClick={handleSendChat}>
                发送
              </Button>
            </View>
          </View>
        </View>
      </ScrollView>

      {/* 底部操作栏 */}
      <View className='bottom-bar'>
        <Button className='bar-btn leave-btn' onClick={handleLeaveRoom}>
          退出猎场
        </Button>
        {isHost ? (
          <Button
            className='bar-btn start-btn'
            onClick={handleStartGame}
            disabled={room.currentPlayers < 6}
          >
            {room.currentPlayers < 6
              ? `还需 ${6 - room.currentPlayers} 人`
              : '血月升起'}
          </Button>
        ) : (
          <Button
            className={`bar-btn ${isReady ? 'cancel-btn' : 'ready-btn'}`}
            onClick={handleReady}
          >
            {isReady ? '收刃入鞘' : '执刃待命'}
          </Button>
        )}
      </View>
    </View>
  )
}
