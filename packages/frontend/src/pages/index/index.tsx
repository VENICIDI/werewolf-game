import { useEffect, useState } from 'react'
import Taro from '@tarojs/taro'
import { View, Text, Button, Image } from '@tarojs/components'
import { isLoggedIn, getUserInfo, clearAuth } from '../../api/auth'
import { IconSwords, IconCrown, IconCrystalBall, IconPotion, IconCrosshair, IconShield, IconPlayer, IconWolf } from '../../components/Icons'
import wolfLogo from '../../assets/icons/wolf-logo.png'
import './index.scss'

export default function Index() {
  const [loggedIn, setLoggedIn] = useState(false)
  const [userInfo, setUserInfo] = useState<any>(null)

  useEffect(() => {
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
    Taro.navigateTo({ url: '/pages/room/index?action=create' })
  }

  const getRankTitle = (rating: number) => {
    if (rating >= 2000) return '传奇猎魔人'
    if (rating >= 1500) return '高级猎魔人'
    if (rating >= 1200) return '猎魔人'
    if (rating >= 800) return '见习猎手'
    return '新晋村民'
  }

  return (
    <View className='index-page'>
      {/* 顶部英雄区：大狼头 + 标题 */}
      <View className='hero'>
        <Image className='hero-logo' src={wolfLogo} mode='aspectFit' />
        <Text className='hero-title'>狼 人 杀</Text>
        <Text className='hero-sub'>Werewolf · 血色月夜</Text>
      </View>

      {/* 核心操作按钮 */}
      <View className='actions'>
        <Button className='action-btn primary' onClick={goToRoomList}>
          <View className='action-icon'><IconSwords size={22} color="#fff" /></View>
          <View className='action-text'>
            <Text className='action-title'>快速加入</Text>
            <Text className='action-desc'>寻找猎场</Text>
          </View>
        </Button>
        <Button className='action-btn secondary' onClick={createRoom}>
          <View className='action-icon'><IconCrown size={22} color="#e5c040" /></View>
          <View className='action-text'>
            <Text className='action-title'>创建房间</Text>
            <Text className='action-desc'>召集猎人</Text>
          </View>
        </Button>
      </View>

      {/* 玩家卡片 */}
      {loggedIn && userInfo ? (
        <View className='user-card'>
          <View className='user-left'>
            <View className='user-avatar'><IconPlayer size={24} color="#e5c040" /></View>
            <View className='user-info'>
              <Text className='user-name'>{userInfo.username}</Text>
              <Text className='user-rank'>{getRankTitle(userInfo.rating || 1000)}</Text>
            </View>
          </View>
          <View className='user-stats'>
            <View className='stat'>
              <Text className='stat-val'>{userInfo.rating || 1000}</Text>
              <Text className='stat-lbl'>积分</Text>
            </View>
            <View className='stat'>
              <Text className='stat-val'>{userInfo.wins || 0}</Text>
              <Text className='stat-lbl'>胜场</Text>
            </View>
            <View className='stat'>
              <Text className='stat-val'>{userInfo.games || 0}</Text>
              <Text className='stat-lbl'>场次</Text>
            </View>
          </View>
        </View>
      ) : (
        <View className='login-card' onClick={goToLogin}>
          <Text className='login-text'>登录以开始狩猎</Text>
          <Text className='login-arrow'>{'>'}</Text>
        </View>
      )}

      {/* 角色速览 */}
      <View className='roles'>
        <Text className='section-label'>角色一览</Text>
        <View className='roles-grid'>
          {[
            { icon: <IconWolf size={20} />, name: '狼人', color: '#c41a1a' },
            { icon: <IconCrystalBall size={20} />, name: '预言家', color: '#8a7dff' },
            { icon: <IconPotion size={20} />, name: '女巫', color: '#b84cff' },
            { icon: <IconCrosshair size={20} />, name: '猎人', color: '#ff9800' },
            { icon: <IconShield size={20} />, name: '守卫', color: '#e5c040' },
            { icon: <IconPlayer size={20} />, name: '村民', color: '#b0a090' },
          ].map(r => (
            <View className='role-chip' key={r.name}>
              <View className='role-chip-icon'>{r.icon}</View>
              <Text className='role-chip-name'>{r.name}</Text>
            </View>
          ))}
        </View>
      </View>
    </View>
  )
}
