import { useEffect, useState } from 'react'
import Taro from '@tarojs/taro'
import { View, Text, Button, ScrollView } from '@tarojs/components'
import { isLoggedIn, getUserInfo, clearAuth } from '../../api/auth'
import { checkLogin } from '../../utils/auth-guard'
import './index.scss'

// 角色图鉴数据
const ROLES = [
  { icon: '🐺', name: '狼人', camp: '狼阵营', desc: '夜晚猎杀一名玩家' },
  { icon: '🔮', name: '预言家', camp: '好人阵营', desc: '每晚查验一人身份' },
  { icon: '🧙', name: '女巫', camp: '好人阵营', desc: '持有一瓶解药和毒药' },
  { icon: '🏹', name: '猎人', camp: '好人阵营', desc: '死亡时可带走一人' },
  { icon: '💂', name: '守卫', camp: '好人阵营', desc: '每晚守护一名玩家' },
  { icon: '👤', name: '村民', camp: '好人阵营', desc: '用智慧找出狼人' },
]

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

  // 根据积分返回称号
  const getRankTitle = (rating: number) => {
    if (rating >= 2000) return '传奇猎魔人'
    if (rating >= 1500) return '高级猎魔人'
    if (rating >= 1200) return '猎魔人'
    if (rating >= 800) return '见习猎手'
    return '新晋村民'
  }

  return (
    <View className='index-page'>
      {/* 主标题区 */}
      <View className='hero-section'>
        <View className='hero-wolf'>🐺</View>
        <Text className='hero-title'>狼人杀</Text>
        <Text className='hero-en'>Werewolf · 血色月夜</Text>
        <View className='hero-divider'>
          <View className='divider-line' />
          <Text className='divider-icon'>✦</Text>
          <View className='divider-line' />
        </View>
        <Text className='hero-slogan'>血月之下，谁是暗夜的猎手？</Text>
      </View>

      {/* 玩家身份卡 / 登录邀请 */}
      {loggedIn && userInfo ? (
        <View className='player-card'>
          <View className='player-avatar'>
            <Text className='avatar-emoji'>🦊</Text>
          </View>
          <View className='player-info'>
            <Text className='player-name'>{userInfo.username}</Text>
            <Text className='player-rank'>
              {getRankTitle(userInfo.rating || 1000)}
            </Text>
            <View className='player-stats'>
              <View className='stat-item'>
                <Text className='stat-value'>{userInfo.rating || 1000}</Text>
                <Text className='stat-label'>积分</Text>
              </View>
              <View className='stat-divider' />
              <View className='stat-item'>
                <Text className='stat-value'>{userInfo.wins || 0}</Text>
                <Text className='stat-label'>胜场</Text>
              </View>
              <View className='stat-divider' />
              <View className='stat-item'>
                <Text className='stat-value'>{userInfo.games || 0}</Text>
                <Text className='stat-label'>总场次</Text>
              </View>
            </View>
          </View>
          <Text className='logout-btn' onClick={handleLogout}>离开</Text>
        </View>
      ) : (
        <View className='invite-card' onClick={goToLogin}>
          <View className='invite-content'>
            <Text className='invite-icon'>🌙</Text>
            <View className='invite-text'>
              <Text className='invite-title'>月夜将至，猎人尚未现身</Text>
              <Text className='invite-desc'>点击此处，揭示你的身份</Text>
            </View>
          </View>
          <Text className='invite-arrow'>›</Text>
        </View>
      )}

      {/* 核心操作 */}
      <View className='action-area'>
        <Button className='action-btn primary' onClick={goToRoomList}>
          <Text className='action-btn-icon'>⚔️</Text>
          <View className='action-btn-text'>
            <Text className='action-btn-title'>快速加入</Text>
            <Text className='action-btn-desc'>寻找正在等待的猎场</Text>
          </View>
        </Button>
        <Button className='action-btn secondary' onClick={createRoom}>
          <Text className='action-btn-icon'>🏰</Text>
          <View className='action-btn-text'>
            <Text className='action-btn-title'>创建房间</Text>
            <Text className='action-btn-desc'>召集猎人开始狩猎</Text>
          </View>
        </Button>
      </View>

      {/* 血夜战报 */}
      <View className='tonight-section'>
        <View className='tonight-header'>
          <Text className='tonight-title'>◆ 血夜战报</Text>
        </View>
        <View className='tonight-stats'>
          <View className='tonight-item'>
            <Text className='tonight-num'>--</Text>
            <Text className='tonight-label'>在线猎人</Text>
          </View>
          <View className='tonight-sep'>|</View>
          <View className='tonight-item'>
            <Text className='tonight-num'>--</Text>
            <Text className='tonight-label'>进行中</Text>
          </View>
          <View className='tonight-sep'>|</View>
          <View className='tonight-item'>
            <Text className='tonight-num'>--</Text>
            <Text className='tonight-label'>今日对局</Text>
          </View>
        </View>
      </View>

      {/* 角色图鉴 */}
      <View className='roles-section'>
        <View className='roles-header'>
          <Text className='roles-title'>◆ 角色图鉴</Text>
        </View>
        <ScrollView className='roles-scroll' scrollX enableFlex>
          {ROLES.map((role) => (
            <View className='role-card' key={role.name}>
              <Text className='role-icon'>{role.icon}</Text>
              <Text className='role-name'>{role.name}</Text>
              <Text className='role-camp'>{role.camp}</Text>
              <Text className='role-desc'>{role.desc}</Text>
            </View>
          ))}
        </ScrollView>
      </View>

      {/* 底部装饰 */}
      <View className='footer-deco'>
        <Text className='footer-text'>—— 谨慎选择你的盟友 ——</Text>
      </View>
    </View>
  )
}
