import { useEffect, useState } from 'react'
import Taro from '@tarojs/taro'
import { View, Text, Button, Input, ScrollView } from '@tarojs/components'
import { getRoomList, joinRoom } from '../../api/room'
import { isLoggedIn } from '../../api/auth'
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
    if (!isLoggedIn()) {
      Taro.showToast({ title: '请先登录', icon: 'none' })
      return
    }
    Taro.navigateTo({ url: '/pages/room/index?action=create' })
  }

  const handleJoinRoom = (room: Room) => {
    if (!isLoggedIn()) {
      Taro.showToast({ title: '请先登录', icon: 'none' })
      return
    }

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
      Taro.showToast({ title: '加入成功', icon: 'success' })
      // 跳转到房间详情
      setTimeout(() => {
        Taro.navigateTo({ url: `/pages/room/index?code=${roomCode}` })
      }, 500)
    } catch (error: any) {
      Taro.hideLoading()
      Taro.showToast({ title: error.message || '加入失败', icon: 'none' })
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
    if (!isLoggedIn()) {
      Taro.showToast({ title: '请先登录', icon: 'none' })
      return
    }
    if (!joinCode.trim()) {
      Taro.showToast({ title: '请输入房间号', icon: 'none' })
      return
    }
    Taro.navigateTo({ url: `/pages/room/index?code=${joinCode.toUpperCase()}` })
  }

  return (
    <View className='room-list-container'>
      {/* 头部 */}
      <View className='header'>
        <Text className='title'>房间列表</Text>
        <Text className='subtitle'>选择房间加入或创建新房间</Text>
      </View>

      {/* 操作区 */}
      <View className='action-area'>
        <View className='join-by-code'>
          <Input
            className='code-input'
            placeholder='输入房间号加入'
            value={joinCode}
            onInput={(e) => setJoinCode(e.detail.value.toUpperCase())}
            maxLength={6}
          />
          <Button className='join-btn' onClick={handleJoinByCode}>
            加入
          </Button>
        </View>
        <Button className='create-btn' onClick={handleCreateRoom}>
          + 创建房间
        </Button>
      </View>

      {/* 房间列表 */}
      <ScrollView className='room-list' scrollY>
        {loading ? (
          <View className='loading'>加载中...</View>
        ) : rooms.length === 0 ? (
          <View className='empty'>
            <Text className='empty-icon'>🏠</Text>
            <Text className='empty-text'>暂无房间</Text>
            <Text className='empty-tip'>创建一个房间开始游戏吧</Text>
          </View>
        ) : (
          rooms.map((room) => (
            <View key={room.id} className='room-card'>
              <View className='room-header'>
                <Text className='room-name'>{room.roomName}</Text>
                {room.hasPassword && <Text className='lock-icon'>🔒</Text>}
              </View>
              <View className='room-info'>
                <Text className='room-code'>房间号: {room.roomCode}</Text>
                <Text className='host-name'>房主: {room.hostName}</Text>
              </View>
              <View className='room-footer'>
                <View className='player-count'>
                  <Text className='count-icon'>👥</Text>
                  <Text className='count-text'>
                    {room.currentPlayers}/{room.maxPlayers}
                  </Text>
                </View>
                <Button
                  className='join-room-btn'
                  onClick={() => handleJoinRoom(room)}
                  disabled={room.currentPlayers >= room.maxPlayers}
                >
                  {room.currentPlayers >= room.maxPlayers ? '已满' : '加入'}
                </Button>
              </View>
            </View>
          ))
        )}
      </ScrollView>

      {/* 密码输入弹窗 */}
      {showPasswordModal && (
        <View className='modal-overlay'>
          <View className='modal-content'>
            <Text className='modal-title'>输入房间密码</Text>
            <Text className='modal-room'>房间: {selectedRoom?.roomName}</Text>
            <Input
              className='modal-input'
              type='password'
              placeholder='请输入密码'
              value={joinPassword}
              onInput={(e) => setJoinPassword(e.detail.value)}
            />
            <View className='modal-buttons'>
              <Button className='modal-btn cancel' onClick={cancelJoin}>
                取消
              </Button>
              <Button className='modal-btn confirm' onClick={confirmJoinWithPassword}>
                确认
              </Button>
            </View>
          </View>
        </View>
      )}
    </View>
  )
}
