import Taro from '@tarojs/taro'

/**
 * 背景音乐管理器 - 使用 InnerAudioContext 播放游戏 BGM
 * 
 * 在小程序中 InnerAudioContext 不支持本地文件路径，
 * 所以 BGM 通过后端静态资源服务提供。
 */

const BGM_PATH = '/audio/bgm.mp3'

class BgmManager {
  private audioCtx: Taro.InnerAudioContext | null = null
  private volume: number = 0.3 // 默认音量 30%（背景音乐不宜太响）
  private playing: boolean = false

  /** 获取 BGM 的完整 URL */
  private getUrl(): string {
    const serverBase = process.env.TARO_ENV === 'h5' ? '' : 'http://10.0.0.240:8088'
    return `${serverBase}${BGM_PATH}`
  }

  /** 初始化音频上下文（懒加载） */
  private init() {
    if (this.audioCtx) return
    this.audioCtx = Taro.createInnerAudioContext()
    this.audioCtx.src = this.getUrl()
    this.audioCtx.loop = true
    this.audioCtx.volume = this.volume
    this.audioCtx.obeyMuteSwitch = false // iOS 静音模式下仍播放

    this.audioCtx.onError((err) => {
      console.warn('[BGM] 播放出错:', err)
    })

    this.audioCtx.onPlay(() => {
      console.log('[BGM] 开始播放')
      this.playing = true
    })

    this.audioCtx.onPause(() => {
      console.log('[BGM] 暂停')
      this.playing = false
    })

    this.audioCtx.onStop(() => {
      console.log('[BGM] 停止')
      this.playing = false
    })
  }

  /** 播放 BGM（如已在播放则忽略） */
  play() {
    this.init()
    if (this.playing) return
    this.audioCtx!.play()
  }

  /**
   * 确保 BGM 正在播放（幂等）。
   * 若尚未初始化则初始化，若暂停中则恢复，若已在播则不动。
   * 供"非游戏页面"的 useDidShow 调用。
   */
  ensurePlaying() {
    this.init()
    if (this.playing) return
    this.audioCtx!.play()
  }

  /** 暂停 BGM（保留上下文，可用 play() 恢复）。供"游戏页面"离开其他页进入时调用。 */
  pause() {
    if (!this.audioCtx || !this.playing) return
    this.audioCtx.pause()
  }

  /** 停止 BGM 并销毁上下文 */
  stop() {
    if (!this.audioCtx) return
    this.audioCtx.stop()
    this.audioCtx.destroy()
    this.audioCtx = null
    this.playing = false
  }

  /** 设置音量 (0~1) */
  setVolume(vol: number) {
    this.volume = Math.max(0, Math.min(1, vol))
    if (this.audioCtx) {
      this.audioCtx.volume = this.volume
    }
  }

  /** 获取当前播放状态 */
  isPlaying(): boolean {
    return this.playing
  }
}

// 单例导出
export const bgm = new BgmManager()
