import { useState } from 'react'
import Taro, { useDidShow } from '@tarojs/taro'
import { View, Text, Button, Image } from '@tarojs/components'
import { isLoggedIn, getUserInfo, clearAuth } from '../../api/auth'
import { getResourceUrl } from '../../utils/request'
import './index.scss'

export default function Profile() {
  const [loggedIn, setLoggedIn] = useState(false)
  const [userInfo, setUserInfo] = useState<any>(null)

  useDidShow(() => {
    checkLoginStatus()
  })

  const checkLoginStatus = () => {
    if (isLoggedIn()) {
      setLoggedIn(true)
      setUserInfo(getUserInfo())
    } else {
      setLoggedIn(false)
      setUserInfo(null)
    }
  }

  const handleLogout = () => {
    Taro.showModal({
      title: '提示',
      content: '确定要退出登录吗？',
      success: (res) => {
        if (res.confirm) {
          clearAuth()
          setLoggedIn(false)
          setUserInfo(null)
          Taro.showToast({ title: '已退出登录', icon: 'success' })
        }
      }
    })
  }

  const goToLogin = () => {
    Taro.navigateTo({ url: '/pages/login/index' })
  }

  const goToEditProfile = () => {
    Taro.navigateTo({ url: '/pages/profile-setup/index' })
  }

  // 手机号打码显示
  const maskedPhone = (p?: string) => {
    if (!p || p.length < 11) return ''
    return p.substring(0, 3) + '****' + p.substring(7)
  }

  if (!loggedIn) {
    return (
      <View className='profile-container'>
        <View className='not-logged-in'>
          <Text className='avatar-placeholder'>◆</Text>
          <Text className='hint'>登录后查看个人信息</Text>
          <Button className='btn-login' onClick={goToLogin}>立即登录</Button>
        </View>
      </View>
    )
  }

  const avatarSrc = userInfo?.avatarUrl ? getResourceUrl(userInfo.avatarUrl) : ''
  const displayName = userInfo?.nickname || userInfo?.username || '未知用户'
  const initial = (displayName[0] || '?').toUpperCase()

  return (
    <View className='profile-container'>
      {/* 用户头像区域 */}
      <View className='profile-header'>
        <View className='avatar'>
          {avatarSrc ? (
            <Image className='avatar-img' src={avatarSrc} mode='aspectFill' />
          ) : (
            <Text className='avatar-text'>{initial}</Text>
          )}
        </View>
        <Text className='username'>{displayName}</Text>
        {userInfo?.phone ? (
          <Text className='email'>{maskedPhone(userInfo.phone)}</Text>
        ) : (
          <Text className='email'>{userInfo?.email || ''}</Text>
        )}
        <View className='edit-profile' onClick={goToEditProfile}>
          <Text className='edit-profile-text'>编辑资料</Text>
        </View>
      </View>

      {/* 统计信息 */}
      <View className='stats-section'>
        <View className='stat-item'>
          <Text className='stat-value'>{userInfo?.rating || 1000}</Text>
          <Text className='stat-label'>积分</Text>
        </View>
        <View className='stat-item'>
          <Text className='stat-value'>{userInfo?.gamesPlayed || 0}</Text>
          <Text className='stat-label'>总场次</Text>
        </View>
        <View className='stat-item'>
          <Text className='stat-value'>{userInfo?.wins || 0}</Text>
          <Text className='stat-label'>胜场</Text>
        </View>
      </View>

      {/* 菜单 */}
      <View className='menu-section'>
        <View className='menu-item'>
          <Text className='menu-icon'>📊</Text>
          <Text className='menu-text'>战绩记录</Text>
          <Text className='menu-arrow'>›</Text>
        </View>
        <View className='menu-item'>
          <Text className='menu-icon'>⚙️</Text>
          <Text className='menu-text'>游戏设置</Text>
          <Text className='menu-arrow'>›</Text>
        </View>
        <View className='menu-item'>
          <Text className='menu-icon'>ℹ️</Text>
          <Text className='menu-text'>关于游戏</Text>
          <Text className='menu-arrow'>›</Text>
        </View>
      </View>

      {/* 退出登录 */}
      <Button className='btn-logout' onClick={handleLogout}>退出登录</Button>
    </View>
  )
}
