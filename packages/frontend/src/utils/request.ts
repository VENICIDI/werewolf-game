import Taro from '@tarojs/taro'
import { checkLogin } from './auth-guard'

// API 基础地址 - H5 环境使用相对路径走 devServer 代理，避免跨域问题
const BASE_URL = process.env.TARO_ENV === 'h5' ? '/api' : 'http://localhost:8080/api'

// 服务器基础地址（用于静态资源如头像）
const SERVER_BASE = process.env.TARO_ENV === 'h5' ? '' : 'http://localhost:8080'

/**
 * 将后端返回的相对路径资源 URL 转为完整可访问的 URL
 * 例如: /avatars/01-shadow-hunter.png -> http://localhost:8080/avatars/01-shadow-hunter.png
 */
export const getResourceUrl = (path: string | undefined | null): string => {
  if (!path) return ''
  if (path.startsWith('http://') || path.startsWith('https://')) return path
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
