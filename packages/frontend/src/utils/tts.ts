import Taro from '@tarojs/taro'

/**
 * TTS 播放器 - AI 发言文字 → 语音播放
 *
 * 后端：packages/backend  POST /api/tts/synthesize/json
 *       内部代理转发到 Fish Audio https://api.fish.audio/v1/tts
 *       请求：{ text, speaking_rate? }
 *       响应：{ success, data: { audio_base64, content_type, size } }
 *
 * 历史背景：
 *   - 2026-05 之前由 packages/ai-speech (FastAPI + edge-tts) 跑在 8001
 *   - 2026-05-13 改为 Spring Boot 后端代理 Fish Audio API，ai-speech 服务下线
 *
 * 实现：
 *   - 串行队列：同时多条发言入队，按序播放，不会抢声道
 *   - 小程序：base64 写入 FileSystemManager，再用 InnerAudioContext 播本地文件
 *   - H5：直接走 blob URL
 */

const TTS_PATH = '/api/tts/synthesize/json'

function getTtsBaseUrl(): string {
  // 直接复用主后端（Spring Boot），端口 8088
  return process.env.TARO_ENV === 'h5'
    ? 'http://localhost:8088'
    : 'http://10.0.0.240:8088'
}

interface QueueItem {
  text: string
  speakerId?: number
}

class TtsPlayer {
  private queue: QueueItem[] = []
  private playing: boolean = false
  private audioCtx: Taro.InnerAudioContext | null = null
  private enabled: boolean = true

  /** 入队一条文本。多条并发调用会按序播放。 */
  enqueue(text: string, speakerId?: number) {
    if (!this.enabled) return
    if (!text || !text.trim()) return
    this.queue.push({ text: text.trim(), speakerId })
    if (!this.playing) {
      this.playNext()
    }
  }

  /** 清空队列并停止当前播放（如阶段切换、游戏结束） */
  stop() {
    this.queue = []
    this.playing = false
    if (this.audioCtx) {
      try { this.audioCtx.stop() } catch (e) { /* ignore */ }
      try { this.audioCtx.destroy() } catch (e) { /* ignore */ }
      this.audioCtx = null
    }
  }

  /** 运行时开关 */
  setEnabled(enabled: boolean) {
    this.enabled = enabled
    if (!enabled) this.stop()
  }

  private async playNext() {
    const item = this.queue.shift()
    if (!item) {
      this.playing = false
      return
    }
    this.playing = true

    try {
      const audioPath = await this.synthesize(item.text)
      if (!audioPath) {
        this.playing = false
        this.playNext()
        return
      }
      await this.playFile(audioPath)
    } catch (e) {
      console.warn('[TTS] 播放失败:', e)
    } finally {
      this.playing = false
      if (this.queue.length > 0) {
        this.playNext()
      }
    }
  }

  /**
   * 调用后端 TTS，保存到本地文件，返回可播放的路径/URL
   */
  private async synthesize(text: string): Promise<string | null> {
    const url = getTtsBaseUrl() + TTS_PATH
    // 超过 500 字截断（后端上限）
    const payload = text.length > 500 ? text.slice(0, 500) : text

    try {
      const res = await Taro.request({
        url,
        method: 'POST',
        data: { text: payload },
        header: { 'Content-Type': 'application/json' },
        timeout: 15000,
      })

      if (res.statusCode !== 200) {
        console.warn('[TTS] HTTP', res.statusCode, res.data)
        return null
      }
      const data = (res.data as any)?.data
      const b64 = data?.audio_base64
      if (!b64) {
        console.warn('[TTS] 响应缺少 audio_base64')
        return null
      }

      // 小程序：写入本地文件
      if (process.env.TARO_ENV === 'weapp') {
        const fs = Taro.getFileSystemManager()
        const filePath = `${Taro.env.USER_DATA_PATH}/tts_${Date.now()}_${Math.random().toString(36).slice(2, 8)}.mp3`
        await new Promise<void>((resolve, reject) => {
          fs.writeFile({
            filePath,
            data: b64,
            encoding: 'base64',
            success: () => resolve(),
            fail: (err) => reject(err),
          })
        })
        return filePath
      }

      // H5：构造 blob URL
      const binary = atob(b64)
      const len = binary.length
      const bytes = new Uint8Array(len)
      for (let i = 0; i < len; i++) bytes[i] = binary.charCodeAt(i)
      const blob = new Blob([bytes], { type: 'audio/mpeg' })
      return URL.createObjectURL(blob)
    } catch (e) {
      console.warn('[TTS] 合成失败:', e)
      return null
    }
  }

  private playFile(src: string): Promise<void> {
    return new Promise((resolve) => {
      // 每条独立的 InnerAudioContext，避免 iOS 上重用导致切歌失败
      const ctx = Taro.createInnerAudioContext()
      this.audioCtx = ctx
      ctx.src = src
      ctx.obeyMuteSwitch = false
      ctx.autoplay = false

      const cleanup = () => {
        try { ctx.destroy() } catch (e) { /* ignore */ }
        // H5 blob 清理
        if (process.env.TARO_ENV === 'h5' && src.startsWith('blob:')) {
          try { URL.revokeObjectURL(src) } catch (e) { /* ignore */ }
        }
        // 小程序本地文件：保留几秒后由系统清理 USER_DATA_PATH；一次性文件不必主动删
        if (this.audioCtx === ctx) this.audioCtx = null
      }

      ctx.onEnded(() => { cleanup(); resolve() })
      ctx.onError((err) => {
        console.warn('[TTS] 播放出错:', err)
        cleanup()
        resolve()
      })
      ctx.onStop(() => { cleanup(); resolve() })

      ctx.play()
    })
  }
}

export const ttsPlayer = new TtsPlayer()
