import Taro from '@tarojs/taro'

// API 基础地址
const BASE_URL = 'http://localhost:8080/api'

// 请求拦截
const request = (options: any) => {
  // 获取 Token
  const token = Taro.getStorageSync('token')
  
  return new Promise((resolve, reject) => {
    Taro.request({
      url: `${BASE_URL}${options.url}`,
      method: options.method || 'GET',
      data: options.data,
      header: {
        'Content-Type': 'application/json',
        ...(token && { 'Authorization': `Bearer ${token}` }),
        ...options.header
      },
      success: (res) => {
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
        } else if (res.statusCode === 401) {
          // Token 过期，清除登录状态
          Taro.removeStorageSync('token')
          Taro.removeStorageSync('userInfo')
          Taro.showToast({
            title: '登录已过期，请重新登录',
            icon: 'none'
          })
          // 跳转到登录页
          setTimeout(() => {
            Taro.navigateTo({ url: '/pages/login/index' })
          }, 1500)
          reject(res)
        } else {
          Taro.showToast({
            title: '网络错误',
            icon: 'none'
          })
          reject(res)
        }
      },
      fail: (err) => {
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
