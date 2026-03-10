import Taro from '@tarojs/taro'
import { getToken } from '../api/auth'

// WebSocket 连接状态
export enum WebSocketState {
  CONNECTING = 0,
  OPEN = 1,
  CLOSING = 2,
  CLOSED = 3
}

// 消息处理器类型
type MessageHandler = (message: any) => void

class WebSocketManager {
  private ws: WebSocket | null = null
  private url: string = ''
  private reconnectAttempts: number = 0
  private maxReconnectAttempts: number = 5
  private reconnectInterval: number = 3000
  private heartbeatInterval: number = 30000
  private heartbeatTimer: any = null
  private messageHandlers: Map<string, MessageHandler[]> = new Map()
  private onOpenCallback: (() => void) | null = null
  private onCloseCallback: (() => void) | null = null
  private onErrorCallback: ((error: any) => void) | null = null

  // 连接 WebSocket
  connect(roomCode: string): Promise<void> {
    return new Promise((resolve, reject) => {
      const token = getToken()
      if (!token) {
        reject(new Error('未登录'))
        return
      }

      // 构建 WebSocket URL
      const wsUrl = `ws://localhost:8080/ws/room/${roomCode}?token=${token}`
      this.url = wsUrl

      try {
        this.ws = new WebSocket(wsUrl)

        this.ws.onopen = () => {
          console.log('WebSocket 连接成功')
          this.reconnectAttempts = 0
          this.startHeartbeat()
          if (this.onOpenCallback) this.onOpenCallback()
          resolve()
        }

        this.ws.onmessage = (event) => {
          this.handleMessage(event.data)
        }

        this.ws.onclose = () => {
          console.log('WebSocket 连接关闭')
          this.stopHeartbeat()
          if (this.onCloseCallback) this.onCloseCallback()
          this.attemptReconnect(roomCode)
        }

        this.ws.onerror = (error) => {
          console.error('WebSocket 错误:', error)
          if (this.onErrorCallback) this.onErrorCallback(error)
          reject(error)
        }
      } catch (error) {
        reject(error)
      }
    })
  }

  // 断开连接
  disconnect(): void {
    this.stopHeartbeat()
    if (this.ws) {
      this.ws.close()
      this.ws = null
    }
  }

  // 发送消息
  send(type: string, data: any): void {
    if (this.ws && this.ws.readyState === WebSocketState.OPEN) {
      const message = {
        type,
        data,
        timestamp: new Date().toISOString()
      }
      this.ws.send(JSON.stringify(message))
    } else {
      console.warn('WebSocket 未连接')
    }
  }

  // 发送聊天消息
  sendChat(content: string): void {
    this.send('PLAYER_CHAT', { content })
  }

  // 发送准备状态
  sendReady(ready: boolean): void {
    this.send('PLAYER_READY', ready)
  }

  // 发送心跳
  sendHeartbeat(): void {
    this.send('HEARTBEAT', 'ping')
  }

  // 订阅消息类型
  on(type: string, handler: MessageHandler): void {
    if (!this.messageHandlers.has(type)) {
      this.messageHandlers.set(type, [])
    }
    this.messageHandlers.get(type)!.push(handler)
  }

  // 取消订阅
  off(type: string, handler: MessageHandler): void {
    const handlers = this.messageHandlers.get(type)
    if (handlers) {
      const index = handlers.indexOf(handler)
      if (index > -1) {
        handlers.splice(index, 1)
      }
    }
  }

  // 设置连接成功回调
  setOnOpen(callback: () => void): void {
    this.onOpenCallback = callback
  }

  // 设置连接关闭回调
  setOnClose(callback: () => void): void {
    this.onCloseCallback = callback
  }

  // 设置错误回调
  setOnError(callback: (error: any) => void): void {
    this.onErrorCallback = callback
  }

  // 获取连接状态
  getState(): WebSocketState {
    return this.ws ? this.ws.readyState : WebSocketState.CLOSED
  }

  // 是否已连接
  isConnected(): boolean {
    return this.ws !== null && this.ws.readyState === WebSocketState.OPEN
  }

  // 处理收到的消息
  private handleMessage(data: string): void {
    try {
      const message = JSON.parse(data)
      const type = message.type

      // 触发对应类型的处理器
      const handlers = this.messageHandlers.get(type)
      if (handlers) {
        handlers.forEach(handler => handler(message))
      }

      // 触发通用处理器
      const generalHandlers = this.messageHandlers.get('*')
      if (generalHandlers) {
        generalHandlers.forEach(handler => handler(message))
      }
    } catch (error) {
      console.error('解析消息失败:', error)
    }
  }

  // 开始心跳
  private startHeartbeat(): void {
    this.heartbeatTimer = setInterval(() => {
      this.sendHeartbeat()
    }, this.heartbeatInterval)
  }

  // 停止心跳
  private stopHeartbeat(): void {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer)
      this.heartbeatTimer = null
    }
  }

  // 尝试重连
  private attemptReconnect(roomCode: string): void {
    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      this.reconnectAttempts++
      console.log(`尝试重连... (${this.reconnectAttempts}/${this.maxReconnectAttempts})`)
      
      setTimeout(() => {
        this.connect(roomCode).catch(() => {
          // 重连失败，继续尝试
        })
      }, this.reconnectInterval)
    } else {
      console.error('重连次数已达上限')
      Taro.showToast({ title: '连接已断开', icon: 'none' })
    }
  }
}

// 导出单例
export const wsManager = new WebSocketManager()
export default wsManager
