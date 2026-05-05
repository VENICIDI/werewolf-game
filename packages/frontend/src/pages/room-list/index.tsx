import { useEffect, useState } from 'react'
import Taro from '@tarojs/taro'
import { View, Text, Button, Input, ScrollView } from '@tarojs/components'
import { getRoomList, joinRoom } from '../../api/room'
import { checkLogin } from '../../utils/auth-guard'
import { IconSwords, IconLock, IconPlayer } from '../../components/Icons'
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
    if (!checkLogin({ message: '创建房间需要先登录' })) return
    Taro.navigateTo({ url: '/pages/room/index?action=create' })
  }

  const handleJoinRoom = (room: Room) => {
    if (!checkLogin({ message: '加入房间需要先登录' })) return
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
      setTimeout(() => Taro.showToast({ title: msg, icon: 'none' }), 100)
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

  const handleJoinByCode = async () => {
    if (!checkLogin({ message: '加入房间需要先登录' })) return
    const code = joinCode.trim().toUpperCase()
    if (!code) {
      Taro.showToast({ title: '请输入房间号', icon: 'none' })
      return
    }
    const ROOM_CODE_REGEX = /^[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}$/
    if (!ROOM_CODE_REGEX.test(code)) {
      Taro.showToast({ title: '房间号格式错误', icon: 'none', duration: 2500 })
      return
    }
    try {
      Taro.showLoading({ title: '加入中...' })
      await joinRoom(code)
      Taro.hideLoading()
      Taro.navigateTo({ url: `/pages/room/index?code=${code}` })
      setJoinCode('')
    } catch (error: any) {
      Taro.hideLoading()
      const msg = error?.data?.message || error?.message || '加入失败'
      setTimeout(() => Taro.showToast({ title: msg, icon: 'none', duration: 2500 }), 100)
    }
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
      default: return 'waiting'
    }
  }

  return (
    <View className='room-list-page'>
      {/* 顶部操作区 */}
      <View className='top-area'>
        <View className='join-row'>
          <Input
            className='code-input'
            placeholder='输入6位房间号'
            value={joinCode}
            onInput={(e) => setJoinCode(e.detail.value.toUpperCase())}
            maxLength={6}
          />
          <Button className='join-btn' onClick={handleJoinByCode}>加入</Button>
        </View>
        <Button className='create-btn' onClick={handleCreateRoom}>
          <View className='create-icon'><IconSwords size={18} color="#fff" /></View>
          <Text className='create-text'>创建房间</Text>
        </Button>
      </View>

      {/* 房间列表标题 */}
      <View className='list-header'>
        <Text className='list-title'>当前房间</Text>
        <View className='list-right'>
          <Text className='room-count'>{rooms.length}个</Text>
          <Text className='refresh-btn' onClick={fetchRooms}>{loading ? '...' : '刷新'}</Text>
        </View>
      </View>

      {/* 房间列表 */}
      <ScrollView className='room-list' scrollY>
        {loading ? (
          <View className='empty-state'>
            <Text className='empty-text'>加载中...</Text>
          </View>
        ) : rooms.length === 0 ? (
          <View className='empty-state'>
            <Text className='empty-text'>暂无房间，创建一个吧</Text>
          </View>
        ) : (
          rooms.map((room) => (
            <View
              key={room.id}
              className={`room-card ${getStatusClass(room.status)}`}
              onClick={() => handleJoinRoom(room)}
            >
              <View className='card-main'>
                <View className='card-top'>
                  <Text className='room-name'>{room.roomName}</Text>
                  <View className={`status-tag ${getStatusClass(room.status)}`}>
                    <Text className='status-text'>{getStatusLabel(room.status)}</Text>
                  </View>
                </View>
                <View className='card-info'>
                  <Text className='info-code'>{room.roomCode}</Text>
                  <Text className='info-host'>{room.hostName}</Text>
                  {room.hasPassword && <View className='lock-icon'><IconLock size={12} color="#b89830" /></View>}
                </View>
              </View>
              <View className='card-right'>
                <Text className='player-count'>{room.currentPlayers}/{room.maxPlayers}</Text>
                <View className='progress-bar'>
                  <View className='progress-fill' style={{ width: `${(room.currentPlayers / room.maxPlayers) * 100}%` }} />
                </View>
              </View>
            </View>
          ))
        )}
      </ScrollView>

      {/* 密码弹窗 */}
      {showPasswordModal && (
        <View className='modal-overlay' onClick={cancelJoin}>
          <View className='modal-content' onClick={(e) => e.stopPropagation()}>
            <Text className='modal-title'>输入房间密码</Text>
            <Text className='modal-room-name'>{selectedRoom?.roomName}</Text>
            <Input
              className='modal-input'
              type='password'
              placeholder='请输入密码'
              value={joinPassword}
              onInput={(e) => setJoinPassword(e.detail.value)}
              focus
            />
            <View className='modal-buttons'>
              <Button className='modal-btn cancel' onClick={cancelJoin}>取消</Button>
              <Button className='modal-btn confirm' onClick={confirmJoinWithPassword}>确认</Button>
            </View>
          </View>
        </View>
      )}
    </View>
  )
}
