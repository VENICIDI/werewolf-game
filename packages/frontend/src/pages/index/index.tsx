import { useEffect, useState } from 'react'
import Taro from '@tarojs/taro'
import { View, Text, Button } from '@tarojs/components'
import { isLoggedIn, getUserInfo, clearAuth } from '../../api/auth'
import './index.scss'

export default function Index() {
  const [loggedIn, setLoggedIn] = useState(false)
  const [userInfo, setUserInfo] = useState<any>(null)

  useEffect(() => {
    // 检查登录状态
    if (isLoggedIn()) {
      setLoggedIn(true)
      setUserInfo(getUserInfo())
    }
  }, [])

  const goToRoomList = () => {
    Taro.switchTab({ url: '/pages/room-list/index' })
  }

  const goToLogin = () => {
    Taro.navigateTo({ url: '/pages/login/index' })
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

  const createRoom = () => {
    if (!loggedIn) {
      Taro.showToast({ title: '请先登录', icon: 'none' })
      return
    }
    // TODO: 创建房间
    Taro.navigateTo({ url: '/pages/room/index' })
  }

  return (
    <View className='index-container'>
      {/* 头部 */}
      <View className='header'>
        <Text className='title'>🐺 狼人杀</Text>
        <Text className='subtitle'>基于 RAG 增强的 AI 对战平台</Text>
      </View>

      {/* 用户信息 */}
      {loggedIn && userInfo ? (
        <View className='user-card'>
          <View className='user-info-row'>
            <Text className='welcome'>欢迎，{userInfo.username}</Text>
            <Text className='rating'>积分: {userInfo.rating || 1000}</Text>
          </View>
          <Text className='logout-text' onClick={handleLogout}>退出</Text>
        </View>
      ) : (
        <View className='login-card'>
          <Text className='tip'>登录后可与 AI 对战</Text>
          <Button className='btn-login' onClick={goToLogin}>立即登录</Button>
        </View>
      )}

      {/* 快速开始 */}
      <View className='action-section'>
        <Text className='section-title'>快速开始</Text>
        <View className='action-buttons'>
          <Button className='btn-primary btn-large' onClick={goToRoomList}>
            加入房间
          </Button>
          <Button className='btn-secondary btn-large' onClick={createRoom}>
            创建房间
          </Button>
        </View>
      </View>

      {/* 游戏特色 */}
      <View className='features-section'>
        <Text className='section-title'>游戏特色</Text>
        <View className='feature-list'>
          <View className='feature-item'>
            <Text className='feature-icon'>🤖</Text>
            <Text className='feature-title'>AI 对战</Text>
            <Text className='feature-desc'>与智能 AI 玩家一决高下</Text>
          </View>
          <View className='feature-item'>
            <Text className='feature-icon'>🧠</Text>
            <Text className='feature-title'>RAG 增强</Text>
            <Text className='feature-desc'>AI 具备狼人杀专业知识</Text>
          </View>
          <View className='feature-item'>
            <Text className='feature-icon'>👥</Text>
            <Text className='feature-title'>多人联机</Text>
            <Text className='feature-desc'>支持 6-12 人对战</Text>
          </View>
        </View>
      </View>
    </View>
  )
}
