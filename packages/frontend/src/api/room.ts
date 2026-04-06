import { get, post } from '../utils/request'

// 获取房间列表
export const getRoomList = () => {
  return get('/rooms')
}

// 创建房间
export const createRoom = (data: {
  roomName: string
  maxPlayers?: number
  password?: string
}) => {
  return post('/rooms', data)
}

// 获取房间详情
export const getRoomDetail = (roomCode: string) => {
  return get(`/rooms/${roomCode}`)
}

// 加入房间
export const joinRoom = (roomCode: string, password?: string) => {
  return post(`/rooms/${roomCode}/join`, { password })
}

// 离开房间
export const leaveRoom = (roomCode: string) => {
  return post(`/rooms/${roomCode}/leave`, {})
}

// 设置准备状态
export const setReady = (roomCode: string, ready: boolean) => {
  return post(`/rooms/${roomCode}/ready?ready=${ready}`, {})
}

// 添加 AI 玩家
export const addAiPlayer = (roomCode: string) => {
  return post(`/rooms/${roomCode}/add-ai`, {})
}

// 移除 AI 玩家
export const removeAiPlayer = (roomCode: string) => {
  return post(`/rooms/${roomCode}/remove-ai`, {})
}
