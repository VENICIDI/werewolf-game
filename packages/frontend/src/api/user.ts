import Taro from '@tarojs/taro'
import { put } from '../utils/request'

const SERVER_HOST = process.env.TARO_ENV === 'h5' ? '' : 'http://10.0.0.240:8088'
const BASE_URL = process.env.TARO_ENV === 'h5' ? '/api' : `${SERVER_HOST}/api`

/**
 * 上传头像文件（用 Taro.uploadFile 走 multipart）
 * @param filePath 本地临时文件路径（chooseAvatar 返回的 avatarUrl）
 * @returns { url: '/uploads/avatars/xxx.jpg' }
 */
export const uploadAvatar = (filePath: string): Promise<{ url: string }> => {
  const token = Taro.getStorageSync('token')
  return new Promise((resolve, reject) => {
    Taro.uploadFile({
      url: `${BASE_URL}/user/avatar`,
      filePath,
      name: 'file',
      header: token ? { Authorization: `Bearer ${token}` } : {},
      success: (res) => {
        try {
          const data = typeof res.data === 'string' ? JSON.parse(res.data) : res.data
          if (data.code === 200) {
            resolve(data.data)
          } else {
            Taro.showToast({ title: data.message || '上传失败', icon: 'none' })
            reject(data)
          }
        } catch (e) {
          reject(e)
        }
      },
      fail: (err) => {
        Taro.showToast({ title: '上传失败', icon: 'none' })
        reject(err)
      }
    })
  })
}

/**
 * 更新用户资料（昵称 + 头像）
 */
export const updateProfile = (nickname?: string, avatarUrl?: string) => {
  return put('/user/profile', { nickname, avatarUrl })
}
