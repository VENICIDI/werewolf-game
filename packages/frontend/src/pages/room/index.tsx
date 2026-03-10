import { useEffect, useState, useCallback } from 'react'
import Taro, { getCurrentInstance } from '@tarojs/taro'
import { View, Text, Button, Input, ScrollView } from '@tarojs/components'
import { getRoomDetail, createRoom, leaveRoom, setReady } from '../../api/room'
import { getUserInfo, isLoggedIn } from '../../api/auth'
import { wsManager, WebSocketState } from '../../utils/websocket'
import './index.scss'

interface Player {
  userId: number
  username: string
  avatarUrl: string
  isHost: boolean
  isReady?: boolean
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

  // 初始化 WebSocket 监听
  useEffect(() => {
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
    }

    // 组件卸载时断开 WebSocket
    return () => {
      wsManager.disconnect()
    }
  }, [])

  // 连接 WebSocket
  const connectWebSocket = async (code: string) => {
    try {
      // 设置消息处理器
      wsManager.on('JOIN_ROOM', handlePlayerJoin)
      wsManager.on('LEAVE_ROOM', handlePlayerLeave)
      wsManager.on('ROOM_UPDATE', handleRoomUpdate)
      wsManager.on('PLAYER_READY', handlePlayerReady)
      wsManager.on('PLAYER_CHAT', handlePlayerChat)
      wsManager.on('HEARTBEAT_ACK', handleHeartbeatAck)
      
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

  // WebSocket 消息处理器
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

  const handleHeartbeatAck = useCallback(() => {
    // 心跳响应，无需处理
  }, [])

  const fetchRoomDetail = async (code: string) => {
    setLoading(true)
    try {
      const res: any = await getRoomDetail(code)
      setRoom(res)
      // 检查自己是否已准备
      const userInfo = getUserInfo()
      const me = res.players?.find((p: Player) => p.userId === userInfo?.id)
      // 这里简化处理，实际应该从后端获取准备状态
    } catch (error: any) {
      Taro.showToast({ title: error.message || '获取房间信息失败', icon: 'none' })
      setTimeout(() => {
        Taro.navigateBack()
      }, 1500)
    } finally {
      setLoading(false)
    }
  }

  const handleCreateRoom = async () => {
    if (!roomName.trim()) {
      Taro.showToast({ title: '请输入房间名称', icon: 'none' })
      return
    }

    try {
      Taro.showLoading({ title: '创建中...' })
      const res: any = await createRoom({
        roomName,
        maxPlayers,
        password: password || undefined
      })
      Taro.hideLoading()
      Taro.showToast({ title: '创建成功', icon: 'success' })
      
      // 跳转到房间详情
      setTimeout(() => {
        Taro.redirectTo({ url: `/pages/room/index?code=${res.roomCode}` })
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
      // 通过 WebSocket 发送准备状态
      wsManager.sendReady(!isReady)
      // 同时调用 HTTP API
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

  const handleStartGame = () => {
    Taro.showToast({ title: '游戏开始功能开发中...', icon: 'none' })
  }

  // 创建房间界面
  if (isCreating) {
    return (
      <View className='room-container'>
        <View className='header'>
          <Text className='back-btn' onClick={() => Taro.navigateBack()}>←</Text>
          <Text className='title'>创建房间</Text>
          <View className='placeholder'></View>
        </View>

        <View className='create-form'>
          <View className='form-item'>
            <Text className='label'>房间名称</Text>
            <Input
              className='input'
              placeholder='请输入房间名称'
              value={roomName}
              onInput={(e) => setRoomName(e.detail.value)}
              maxLength={20}
            />
          </View>

          <View className='form-item'>
            <Text className='label'>人数设置</Text>
            <View className='player-options'>
              {[6, 9, 12].map((num) => (
                <View
                  key={num}
                  className={`option ${maxPlayers === num ? 'active' : ''}`}
                  onClick={() => setMaxPlayers(num)}
                >
                  <Text className='option-num'>{num}人</Text>
                </View>
              ))}
            </View>
          </View>

          <View className='form-item'>
            <Text className='label'>房间密码 (可选)</Text>
            <Input
              className='input'
              type='password'
              placeholder='不设置则为公开房间'
              value={password}
              onInput={(e) => setPassword(e.detail.value)}
              maxLength={20}
            />
          </View>

          <Button className='create-btn' onClick={handleCreateRoom}>
            创建房间
          </Button>
        </View>
      </View>
    )
  }

  // 房间详情界面
  if (!room) {
    return (
      <View className='room-container'>
        <View className='loading'>加载中...</View>
      </View>
    )
  }

  const userInfo = getUserInfo()
  const isHost = userInfo?.id === room.hostId

  return (
    <View className='room-container'>
      {/* 头部 */}
      <View className='header'>
        <Text className='back-btn' onClick={() => Taro.navigateBack()}>←</Text>
        <View className='room-info-header'>
          <Text className='room-name'>{room.roomName}</Text>
          <Text className='room-code'>房间号: {room.roomCode}</Text>
          <View className='ws-status'>
            <Text className={`status-dot ${wsConnected ? 'connected' : 'disconnected'}`}></Text>
            <Text className='status-text'>{wsConnected ? '实时连接中' : '未连接'}</Text>
          </View>
        </View>
        <View className='placeholder'></View>
      </View>

      {/* 玩家列表 */}
      <View className='players-section'>
        <Text className='section-title'>
          玩家列表 ({room.currentPlayers}/{room.maxPlayers})
        </Text>
        <View className='players-grid'>
          {room.players?.map((player, index) => (
            <View key={player.userId} className={`player-card ${player.isReady ? 'ready' : ''}`}>
              <View className='player-avatar'>
                {player.avatarUrl ? (
                  <image src={player.avatarUrl} className='avatar-img' />
                ) : (
                  <Text className='avatar-default'>👤</Text>
                )}
              </View>
              <Text className='player-name'>{player.username}</Text>
              {player.isHost && <Text className='host-badge'>房主</Text>}
              {player.isReady && <Text className='ready-badge'>已准备</Text>}
              <Text className='seat-num'>{index + 1}号</Text>
            </View>
          ))}
          {/* 空位 */}
          {Array.from({ length: room.maxPlayers - (room.players?.length || 0) }).map((_, i) => (
            <View key={`empty-${i}`} className='player-card empty'>
              <View className='player-avatar empty'>
                <Text className='avatar-default'>+</Text>
              </View>
              <Text className='player-name'>等待加入</Text>
              <Text className='seat-num'>{(room.players?.length || 0) + i + 1}号</Text>
            </View>
          ))}
        </View>
      </View>

      {/* 聊天区域 */}
      <View className='chat-section'>
        <Text className='section-title'>聊天</Text>
        <ScrollView className='chat-messages' scrollY>
          {chatMessages.length === 0 ? (
            <Text className='chat-empty'>暂无消息</Text>
          ) : (
            chatMessages.map((msg, index) => (
              <View key={index} className='chat-item'>
                <Text className='chat-sender'>{msg.senderName}:</Text>
                <Text className='chat-content'>{msg.content}</Text>
              </View>
            ))
          )}
        </ScrollView>
        <View className='chat-input-area'>
          <Input
            className='chat-input'
            placeholder='输入消息...'
            value={chatInput}
            onInput={(e) => setChatInput(e.detail.value)}
            onConfirm={handleSendChat}
          />
          <Button className='chat-send' onClick={handleSendChat}>
            发送
          </Button>
        </View>
      </View>

      {/* 底部操作栏 */}
      <View className='action-bar'>
        <Button className='action-btn leave' onClick={handleLeaveRoom}>
          离开房间
        </Button>
        {isHost ? (
          <Button 
            className='action-btn start' 
            onClick={handleStartGame}
            disabled={room.currentPlayers < 6}
          >
            开始游戏
          </Button>
        ) : (
          <Button 
            className={`action-btn ${isReady ? 'cancel' : 'ready'}`}
            onClick={handleReady}
          >
            {isReady ? '取消准备' : '准备'}
          </Button>
        )}
      </View>
    </View>
  )
}
