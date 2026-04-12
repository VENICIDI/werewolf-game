import Taro from '@tarojs/taro'
import { isLoggedIn } from '../api/auth'

// 登录弹窗事件总线
type LoginModalCallback = (options?: LoginGuardOptions) => void
let showLoginModalCallback: LoginModalCallback | null = null

export interface LoginGuardOptions {
  /** 提示标题，默认 "需要登录" */
  title?: string
  /** 提示描述文案 */
  message?: string
  /** 确认按钮文案，默认 "去登录" */
  confirmText?: string
  /** 取消按钮文案，默认 "稍后再说" */
  cancelText?: string
  /** 登录成功后的回调页面路径（可选） */
  redirectUrl?: string
}

/**
 * 注册登录弹窗的显示回调（由 LoginModal 组件调用）
 */
export const registerLoginModal = (callback: LoginModalCallback) => {
  showLoginModalCallback = callback
}

/**
 * 注销登录弹窗回调
 */
export const unregisterLoginModal = () => {
  showLoginModalCallback = null
}

/**
 * 核心方法：检查登录状态
 * - 已登录：返回 true
 * - 未登录：弹出登录提示弹窗，返回 false
 *
 * @example
 * ```tsx
 * const handleCreateRoom = () => {
 *   if (!checkLogin({ message: '创建房间需要先登录哦' })) return
 *   // 已登录，继续执行操作...
 * }
 * ```
 */
export const checkLogin = (options?: LoginGuardOptions): boolean => {
  if (isLoggedIn()) {
    return true
  }

  // 小程序环境使用原生弹窗（自定义浮层在 App 组件中无法正确覆盖页面）
  if (process.env.TARO_ENV !== 'h5') {
    Taro.showModal({
      title: options?.title || '需要登录',
      content: options?.message || '该操作需要登录后才能使用，是否前往登录？',
      confirmText: options?.confirmText || '去登录',
      cancelText: options?.cancelText || '取消',
      success: (res) => {
        if (res.confirm) {
          navigateToLogin(options?.redirectUrl)
        }
      }
    })
  } else if (showLoginModalCallback) {
    showLoginModalCallback(options)
  } else {
    Taro.showModal({
      title: options?.title || '需要登录',
      content: options?.message || '该操作需要登录后才能使用，是否前往登录？',
      confirmText: options?.confirmText || '去登录',
      cancelText: options?.cancelText || '取消',
      success: (res) => {
        if (res.confirm) {
          navigateToLogin(options?.redirectUrl)
        }
      }
    })
  }

  return false
}

/**
 * 跳转到登录页
 * @param redirectUrl 登录成功后要跳转回的页面路径
 */
export const navigateToLogin = (redirectUrl?: string) => {
  const url = redirectUrl
    ? `/pages/login/index?redirect=${encodeURIComponent(redirectUrl)}`
    : '/pages/login/index'
  Taro.navigateTo({ url })
}
