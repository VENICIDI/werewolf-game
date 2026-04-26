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
  private socketTask: Taro.SocketTask | null = null
  private state: WebSocketState = WebSocketState.CLOSED
  private reconnectAttempts: number = 0
  private maxReconnectAttempts: number = 5
  private reconnectInterval: number = 3000
  private heartbeatInterval: number = 30000
  private heartbeatTimer: any = null
  private messageHandlers: Map<string, MessageHandler[]> = new Map()
  private onOpenCallback: (() => void) | null = null
  private onCloseCallback: (() => void) | null = null
  private onErrorCallback: ((error: any) => void) | null = null

  // 连接 WebSocket（兼容微信小程序和 H5）
  connect(roomCode: string): Promise<void> {
    // 先关闭已有连接，防止连到旧房间
    if (this.socketTask) {
      try {
        this.socketTask.close({})
      } catch (e) { /* ignore */ }
      this.socketTask = null as any
    }
    this.reconnectAttempts = 0

    return new Promise((resolve, reject) => {
      const token = getToken()
      if (!token) {
        reject(new Error('未登录'))
        return
      }

      const wsUrl = `ws://localhost:8080/ws/room/${roomCode}?token=${token}`
      console.log('[WS] 连接:', wsUrl)
      this.state = WebSocketState.CONNECTING

      try {
        Taro.connectSocket({
          url: wsUrl,
          success: () => {
            console.log('[WS] connectSocket 调用成功')
          },
          fail: (err) => {
            console.error('[WS] connectSocket 调用失败:', err)
            this.state = WebSocketState.CLOSED
            reject(err)
          }
        }).then((task: Taro.SocketTask) => {
          this.socketTask = task

          task.onOpen(() => {
            console.log('[WS] 连接已建立')
            this.state = WebSocketState.OPEN
            this.reconnectAttempts = 0
            this.startHeartbeat()
            if (this.onOpenCallback) this.onOpenCallback()
            resolve()
          })

          task.onMessage((res) => {
            this.handleMessage(res.data as string)
          })

          task.onClose(() => {
            console.log('[WS] 连接关闭')
            this.state = WebSocketState.CLOSED
            this.stopHeartbeat()
            if (this.onCloseCallback) this.onCloseCallback()
            this.attemptReconnect(roomCode)
          })

          task.onError((error) => {
            console.error('[WS] 连接错误:', error)
            this.state = WebSocketState.CLOSED
            if (this.onErrorCallback) this.onErrorCallback(error)
            reject(error)
          })
        }).catch((err) => {
          console.error('[WS] SocketTask 创建失败:', err)
          this.state = WebSocketState.CLOSED
          reject(err)
        })
      } catch (error) {
        console.error('[WS] 连接异常:', error)
        this.state = WebSocketState.CLOSED
        reject(error)
      }
    })
  }

  // 断开连接
  disconnect(): void {
    this.stopHeartbeat()
    this.reconnectAttempts = this.maxReconnectAttempts // 阻止重连
    if (this.socketTask) {
      this.socketTask.close({})
      this.socketTask = null
    }
    this.state = WebSocketState.CLOSED
  }

  // 发送消息
  send(type: string, data: any): void {
    if (this.socketTask && this.state === WebSocketState.OPEN) {
      const message = {
        type,
        data,
        timestamp: new Date().toISOString()
      }
      this.socketTask.send({
        data: JSON.stringify(message),
        fail: (err) => {
          console.error('[WS] 发送失败:', err)
        }
      })
    } else {
      console.warn('[WS] 未连接，无法发送')
    }
  }

  // 发送聊天消息
  sendChat(content: string): void {
    this.send('PLAYER_CHAT', { content })
  }

  // 结束当前讨论阶段发言 (跳过剩余时间)
  sendSkipSpeech(): void {
    this.send('SKIP_SPEECH', {})
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
    return this.state
  }

  // 是否已连接
  isConnected(): boolean {
    return this.state === WebSocketState.OPEN
  }

  // 处理收到的消息
  private handleMessage(data: string): void {
    try {
      const message = JSON.parse(data)
      const type = message.type

      const handlers = this.messageHandlers.get(type)
      if (handlers) {
        handlers.forEach(handler => handler(message))
      }

      const generalHandlers = this.messageHandlers.get('*')
      if (generalHandlers) {
        generalHandlers.forEach(handler => handler(message))
      }
    } catch (error) {
      console.error('[WS] 解析消息失败:', error)
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
      console.log(`[WS] 尝试重连... (${this.reconnectAttempts}/${this.maxReconnectAttempts})`)
      
      setTimeout(() => {
        this.connect(roomCode).catch(() => {})
      }, this.reconnectInterval)
    } else {
      console.error('[WS] 重连次数已达上限')
    }
  }
}

// 导出单例
export const wsManager = new WebSocketManager()
export default wsManager
