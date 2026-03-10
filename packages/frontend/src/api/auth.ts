import { post, get } from '../utils/request'
import Taro from '@tarojs/taro'

// 登录
export const login = (username: string, password: string) => {
  return post('/auth/login', { username, password })
}

// 注册
export const register = (username: string, password: string, email: string) => {
  return post('/auth/register', { username, password, email })
}

// 获取当前用户信息
export const getCurrentUser = () => {
  return get('/auth/me')
}

// 保存登录状态
export const saveAuth = (data: any) => {
  Taro.setStorageSync('token', data.token)
  Taro.setStorageSync('userInfo', {
    id: data.id,
    username: data.username,
    email: data.email,
    avatarUrl: data.avatarUrl,
    rating: data.rating
  })
}

// 清除登录状态
export const clearAuth = () => {
  Taro.removeStorageSync('token')
  Taro.removeStorageSync('userInfo')
}

// 获取 Token
export const getToken = () => {
  return Taro.getStorageSync('token')
}

// 获取用户信息
export const getUserInfo = () => {
  return Taro.getStorageSync('userInfo')
}

// 检查是否登录
export const isLoggedIn = () => {
  return !!getToken()
}
