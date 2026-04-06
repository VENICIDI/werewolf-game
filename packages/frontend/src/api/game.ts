import { get, post } from '../utils/request'

// 开始游戏
export const startGame = (roomId: number, gameModeId: string = 'standard_9') => {
  return post(`/games/room/${roomId}/start`, { gameModeId })
}

// 获取游戏状态
export const getGameStatus = (gameId: number) => {
  return get(`/games/${gameId}/status`)
}

// 执行夜晚行动
export const nightAction = (gameId: number, action: string, targetId?: number) => {
  return post(`/games/${gameId}/night-action`, { action, targetId })
}

// 投票
export const vote = (gameId: number, targetId?: number) => {
  return post(`/games/${gameId}/vote`, { targetId })
}

// 发表遗言
export const lastWords = (gameId: number, content: string) => {
  return post(`/games/${gameId}/last-words`, { content })
}

// 猎人开枪
export const hunterShoot = (gameId: number, targetId: number) => {
  return post(`/games/${gameId}/hunter-shoot`, { targetId })
}

// 获取游戏结果
export const getGameResult = (gameId: number) => {
  return get(`/games/${gameId}/result`)
}

// 游戏阶段类型
export enum GamePhase {
  NONE = 'NONE',
  NIGHT_START = 'NIGHT_START',
  WEREWOLF = 'WEREWOLF',
  SEER = 'SEER',
  WITCH = 'WITCH',
  HUNTER = 'HUNTER',
  DAY_START = 'DAY_START',
  DISCUSSION = 'DISCUSSION',
  VOTING = 'VOTING',
  EXECUTION = 'EXECUTION'
}

// 游戏状态类型
export enum GameStatus {
  PREPARING = 'PREPARING',
  RUNNING = 'RUNNING',
  PAUSED = 'PAUSED',
  FINISHED = 'FINISHED'
}

// 玩家状态类型
export enum PlayerStatus {
  ALIVE = 'ALIVE',
  DEAD = 'DEAD',
  DISCONNECTED = 'DISCONNECTED'
}

// 角色类型
export enum Role {
  UNKNOWN = 'UNKNOWN',
  VILLAGER = 'VILLAGER',
  WEREWOLF = 'WEREWOLF',
  SEER = 'SEER',
  WITCH = 'WITCH',
  HUNTER = 'HUNTER',
  GUARD = 'GUARD',
  IDIOT = 'IDIOT'
}

// 接口定义
export interface Player {
  id: number
  userId: number
  username: string
  avatarUrl?: string
  role: Role
  status: PlayerStatus
  isHost: boolean
  seatNumber: number
}

export interface GameState {
  id: number
  roomCode: string
  status: GameStatus
  currentRound: number
  currentPhase: GamePhase
  players: Player[]
  myRole?: Role
  winner?: string
}

export interface NightAction {
  action: string
  targetId?: number
  result?: any
}
