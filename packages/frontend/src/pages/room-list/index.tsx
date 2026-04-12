import { useEffect, useState } from 'react'
import Taro from '@tarojs/taro'
import { View, Text, Button, Input, ScrollView } from '@tarojs/components'
import { getRoomList, joinRoom } from '../../api/room'
import { checkLogin } from '../../utils/auth-guard'
import './index.scss'

interface Room {
  id: number
  roomCode: string
  roomName: string
  hostName: string
  maxPlayers: number
  currentPlayers: number
  hasPassword: boolean
  status: string
}

export default function RoomList() {
  const [rooms, setRooms] = useState<Room[]>([])
  const [loading, setLoading] = useState(false)
  const [joinCode, setJoinCode] = useState('')
  const [joinPassword, setJoinPassword] = useState('')
  const [showPasswordModal, setShowPasswordModal] = useState(false)
  const [selectedRoom, setSelectedRoom] = useState<Room | null>(null)

  useEffect(() => {
    fetchRooms()
  }, [])

  const fetchRooms = async () => {
    setLoading(true)
    try {
      const res: any = await getRoomList()
      setRooms(res || [])
    } catch (error) {
      console.error('获取房间列表失败:', error)
    } finally {
      setLoading(false)
    }
  }

  const handleCreateRoom = () => {
    if (!checkLogin({ message: '创建房间需要先登录，去亮明你的猎人身份吧' })) return
    Taro.navigateTo({ url: '/pages/room/index?action=create' })
  }

  const handleJoinRoom = (room: Room) => {
    if (!checkLogin({ message: '加入房间需要先登录，去亮明你的猎人身份吧' })) return

    if (room.hasPassword) {
      setSelectedRoom(room)
      setShowPasswordModal(true)
    } else {
      doJoinRoom(room.roomCode, '')
    }
  }

  const doJoinRoom = async (roomCode: string, password: string) => {
    try {
      Taro.showLoading({ title: '加入中...' })
      await joinRoom(roomCode, password)
      Taro.hideLoading()
      Taro.navigateTo({ url: `/pages/room/index?code=${roomCode}` })
    } catch (error: any) {
      Taro.hideLoading()
      const msg = error?.data?.message || error?.message || '加入失败'
      setTimeout(() => {
        Taro.showToast({ title: msg, icon: 'none' })
      }, 100)
    }
  }

  const confirmJoinWithPassword = () => {
    if (!joinPassword.trim()) {
      Taro.showToast({ title: '请输入密码', icon: 'none' })
      return
    }
    if (selectedRoom) {
      doJoinRoom(selectedRoom.roomCode, joinPassword)
      setShowPasswordModal(false)
      setJoinPassword('')
    }
  }

  const cancelJoin = () => {
    setShowPasswordModal(false)
    setJoinPassword('')
    setSelectedRoom(null)
  }

  const handleJoinByCode = () => {
    if (!checkLogin({ message: '加入房间需要先登录，去亮明你的猎人身份吧' })) return
    if (!joinCode.trim()) {
      Taro.showToast({ title: '请输入房间号', icon: 'none' })
      return
    }
    Taro.navigateTo({ url: `/pages/room/index?code=${joinCode.toUpperCase()}` })
  }

  const getStatusLabel = (status: string) => {
    switch (status) {
      case 'WAITING': return '等待中'
      case 'PLAYING': return '游戏中'
      case 'FINISHED': return '已结束'
      default: return '等待中'
    }
  }

  const getStatusClass = (status: string) => {
    switch (status) {
      case 'WAITING': return 'waiting'
      case 'PLAYING': return 'playing'
      case 'FINISHED': return 'finished'
      default: return 'waiting'
    }
  }

  return (
    <View className='room-list-container'>
      {/* 头部标题 */}
      <View className='page-header'>
        <View className='header-title-row'>
          <Text className='header-icon'>📜</Text>
          <Text className='header-title'>狩猎集会</Text>
        </View>
        <Text className='header-subtitle'>选择一个房间加入战局，或创建属于你的猎场</Text>
        <View className='header-divider'>
          <View className='divider-line'></View>
          <Text className='divider-icon'>◈</Text>
          <View className='divider-line'></View>
        </View>
      </View>

      {/* 快捷操作区 */}
      <View className='quick-actions'>
        <View className='join-by-code'>
          <View className='input-wrapper'>
            <Text className='input-prefix'>🔑</Text>
            <Input
              className='code-input'
              placeholder='输入6位房间号'
              value={joinCode}
              onInput={(e) => setJoinCode(e.detail.value.toUpperCase())}
              maxLength={6}
            />
          </View>
          <Button className='join-btn' onClick={handleJoinByCode}>
            <Text className='btn-text'>加入</Text>
          </Button>
        </View>
        <Button className='create-btn' onClick={handleCreateRoom}>
          <Text className='btn-icon'>⚔</Text>
          <Text className='btn-text'>创建房间</Text>
        </Button>
      </View>

      {/* 房间列表区 */}
      <View className='list-header'>
        <View className='list-title-row'>
          <Text className='list-title'>当前房间</Text>
          <Text className='room-count'>{rooms.length} 个房间</Text>
        </View>
        <View className='refresh-btn' onClick={fetchRooms}>
          <Text className={`refresh-icon ${loading ? 'spinning' : ''}`}>↻</Text>
        </View>
      </View>

      <ScrollView className='room-list' scrollY>
        {loading ? (
          <View className='loading-state'>
            <Text className='loading-icon'>🌙</Text>
            <Text className='loading-text'>月光笼罩中...</Text>
          </View>
        ) : rooms.length === 0 ? (
          <View className='empty-state'>
            <Text className='empty-icon'>🐺</Text>
            <Text className='empty-title'>月夜寂静</Text>
            <Text className='empty-desc'>暂无开放的房间，成为第一个召集猎人的人吧</Text>
            <Button className='empty-create-btn' onClick={handleCreateRoom}>
              创建房间
            </Button>
          </View>
        ) : (
          rooms.map((room) => (
            <View
              key={room.id}
              className={`room-card ${getStatusClass(room.status)}`}
              onClick={() => handleJoinRoom(room)}
            >
              {/* 卡片顶部装饰 */}
              <View className='card-top-deco'></View>

              <View className='card-body'>
                {/* 第一行：房间名 + 状态 */}
                <View className='card-row-top'>
                  <View className='room-name-area'>
                    <Text className='room-name'>{room.roomName}</Text>
                    {room.hasPassword && <Text className='lock-badge'>🔒</Text>}
                  </View>
                  <View className={`status-tag ${getStatusClass(room.status)}`}>
                    <Text className='status-dot'></Text>
                    <Text className='status-text'>{getStatusLabel(room.status)}</Text>
                  </View>
                </View>

                {/* 第二行：房间号 + 房主 */}
                <View className='card-row-info'>
                  <View className='info-item'>
                    <Text className='info-label'>房间号</Text>
                    <Text className='info-value code'>{room.roomCode}</Text>
                  </View>
                  <View className='info-divider'></View>
                  <View className='info-item'>
                    <Text className='info-label'>房主</Text>
                    <Text className='info-value'>👤 {room.hostName}</Text>
                  </View>
                </View>

                {/* 第三行：人数进度 + 加入按钮 */}
                <View className='card-row-bottom'>
                  <View className='player-progress'>
                    <View className='progress-header'>
                      <Text className='progress-label'>👥 猎人集结</Text>
                      <Text className='progress-num'>
                        {room.currentPlayers}<Text className='progress-slash'>/</Text>{room.maxPlayers}
                      </Text>
                    </View>
                    <View className='progress-bar'>
                      <View
                        className='progress-fill'
                        style={{ width: `${(room.currentPlayers / room.maxPlayers) * 100}%` }}
                      ></View>
                    </View>
                  </View>
                  <Button
                    className={`card-join-btn ${room.currentPlayers >= room.maxPlayers ? 'full' : ''}`}
                    onClick={(e) => {
                      e.stopPropagation()
                      handleJoinRoom(room)
                    }}
                    disabled={room.currentPlayers >= room.maxPlayers || room.status === 'PLAYING'}
                  >
                    {room.status === 'PLAYING' ? '进行中' : room.currentPlayers >= room.maxPlayers ? '已满员' : '加入'}
                  </Button>
                </View>
              </View>
            </View>
          ))
        )}
      </ScrollView>

      {/* 密码输入弹窗 */}
      {showPasswordModal && (
        <View className='modal-overlay' onClick={cancelJoin}>
          <View className='modal-content' onClick={(e) => e.stopPropagation()}>
            <View className='modal-header'>
              <Text className='modal-icon'>🔐</Text>
              <Text className='modal-title'>输入密码</Text>
            </View>
            <Text className='modal-room-name'>{selectedRoom?.roomName}</Text>
            <View className='modal-input-wrapper'>
              <Input
                className='modal-input'
                type='password'
                placeholder='请输入房间密码'
                value={joinPassword}
                onInput={(e) => setJoinPassword(e.detail.value)}
                focus
              />
            </View>
            <View className='modal-buttons'>
              <Button className='modal-btn cancel' onClick={cancelJoin}>
                取消
              </Button>
              <Button className='modal-btn confirm' onClick={confirmJoinWithPassword}>
                确认加入
              </Button>
            </View>
          </View>
        </View>
      )}
    </View>
  )
}
