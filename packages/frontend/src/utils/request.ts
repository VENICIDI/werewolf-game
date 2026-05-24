import Taro from '@tarojs/taro'
import { checkLogin } from './auth-guard'

// 后端服务器地址
// H5 环境使用相对路径走 devServer 代理，避免跨域问题
// 小程序环境使用局域网 IP（真机调试需要手机和电脑在同一 WiFi 下）
const SERVER_HOST = process.env.NODE_ENV === 'development' ? '' : 'http://39.96.170.159:8088'
const BASE_URL = process.env.NODE_ENV === 'development' ? '/api' : `${SERVER_HOST}/api`

// 服务器基础地址（仅用于用户上传的动态资源如头像）
const SERVER_BASE = SERVER_HOST

/**
 * 将后端返回的相对路径资源 URL 转为完整可访问的 URL
 * - 预设资源（/avatars/*.png）走前端本地打包
 * - 用户上传资源（/uploads/*）走服务器地址
 */
// 预设头像静态映射（Taro 不支持完全动态 require，需要静态列出）
const avatarMap: Record<string, string> = {
  '01-shadow-hunter.png': require('../assets/avatars/01-shadow-hunter.png'),
  '02-moonwalker.png': require('../assets/avatars/02-moonwalker.png'),
  '03-silver-wolf.png': require('../assets/avatars/03-silver-wolf.png'),
  '04-bloodclaw.png': require('../assets/avatars/04-bloodclaw.png'),
  '05-night-owl.png': require('../assets/avatars/05-night-owl.png'),
  '06-mist-oracle.png': require('../assets/avatars/06-mist-oracle.png'),
  '07-iron-guard.png': require('../assets/avatars/07-iron-guard.png'),
  '08-viper.png': require('../assets/avatars/08-viper.png'),
  '09-lone-wolf.png': require('../assets/avatars/09-lone-wolf.png'),
  '10-night-watchman.png': require('../assets/avatars/10-night-watchman.png'),
  '11-specter.png': require('../assets/avatars/11-specter.png'),
  '12-black-raven.png': require('../assets/avatars/12-black-raven.png'),
  'default-female-cool.png': require('../assets/avatars/default-female-cool.png'),
  'default-female-warm.png': require('../assets/avatars/default-female-warm.png'),
  'default-male-cool.png': require('../assets/avatars/default-male-cool.png'),
  'default-male-warm.png': require('../assets/avatars/default-male-warm.png'),
}

export const getResourceUrl = (path: string | undefined | null): string => {
  if (!path) return ''
  if (path.startsWith('http://') || path.startsWith('https://')) return path
  // 预设头像：使用前端打包的本地资源
  if (path.startsWith('/avatars/')) {
    const filename = path.split('/').pop() || ''
    const local = avatarMap[filename]
    if (local) return local
  }
  return `${SERVER_BASE}${path}`
}

// 请求拦截
const request = (options: any) => {
  // 获取 Token
  const token = Taro.getStorageSync('token')
  const fullUrl = `${BASE_URL}${options.url}`
  console.log(`[Request] ${options.method || 'GET'} ${fullUrl}`)
  
  return new Promise((resolve, reject) => {
    Taro.request({
      url: `${BASE_URL}${options.url}`,
      method: options.method || 'GET',
      data: options.data,
      timeout: 10000,
      header: {
        'Content-Type': 'application/json',
        ...(token && { 'Authorization': `Bearer ${token}` }),
        ...options.header
      },
      success: (res) => {
        console.log(`[Request] ← ${options.method || 'GET'} ${options.url} status=${res.statusCode}`, res.data)
        if (res.statusCode >= 200 && res.statusCode < 300) {
          const data = res.data
          if (data.code === 200) {
            resolve(data.data)
          } else {
            Taro.showToast({
              title: data.message || '请求失败',
              icon: 'none'
            })
            reject(data)
          }
        } else if (res.statusCode === 401 || res.statusCode === 403) {
          Taro.removeStorageSync('token')
          Taro.removeStorageSync('userInfo')
          checkLogin({
            title: res.statusCode === 401 ? '登录已过期' : '请先登录',
            message: res.statusCode === 401
              ? '你的身份令牌已失效，需要重新登录以继续操作'
              : '该操作需要登录，请先登录后再试'
          })
          reject(res)
        } else if (res.statusCode >= 400 && res.statusCode < 500) {
          // 业务错误：优先展示后端的具体 message
          const bizMsg = (res.data && (res.data.message || res.data.msg)) || '请求失败'
          console.warn(`[Request] 业务错误 ${res.statusCode}: ${bizMsg}`, res.data)
          Taro.showToast({ title: bizMsg, icon: 'none' })
          // reject 时把 message 挂到错误对象上，调用方也能拿到
          reject({ ...(res.data || {}), statusCode: res.statusCode, message: bizMsg })
        } else {
          Taro.showToast({
            title: '网络错误',
            icon: 'none'
          })
          reject(res)
        }
      },
      fail: (err) => {
        console.warn(`[Request] ✗ ${options.method || 'GET'} ${options.url} fail`, err)
        Taro.showToast({
          title: '网络请求失败',
          icon: 'none'
        })
        reject(err)
      }
    })
  })
}

// 封装常用方法
export const get = (url: string, params?: any) => {
  return request({ url, method: 'GET', data: params })
}

export const post = (url: string, data?: any) => {
  return request({ url, method: 'POST', data })
}

export const put = (url: string, data?: any) => {
  return request({ url, method: 'PUT', data })
}

export const del = (url: string, data?: any) => {
  return request({ url, method: 'DELETE', data })
}

export default request
