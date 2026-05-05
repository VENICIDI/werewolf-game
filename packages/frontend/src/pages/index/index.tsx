import { useEffect, useState } from 'react'
import Taro, { useDidShow } from '@tarojs/taro'
import { View, Text, Button, Image } from '@tarojs/components'
import { isLoggedIn, getUserInfo, clearAuth } from '../../api/auth'
import { getResourceUrl } from '../../utils/request'
import { bgm } from '../../utils/bgm'
import { IconSwords, IconCrown, IconPlayer } from '../../components/Icons'
import './index.scss'

// 首页海报通过后端静态资源网络加载
const homePoster = getResourceUrl('/images/home-poster.png')

export default function Index() {
  const [loggedIn, setLoggedIn] = useState(false)
  const [userInfo, setUserInfo] = useState<any>(null)

  // 每次页面显示时刷新登录状态（tabBar 页面缓存，switchTab 回来不会重新挂载）
  useDidShow(() => {
    if (isLoggedIn()) {
      setLoggedIn(true)
      setUserInfo(getUserInfo())
    } else {
      setLoggedIn(false)
      setUserInfo(null)
    }
    // 非游戏页面恢复/保持 BGM 播放
    bgm.ensurePlaying()
  })

  const goToRoomList = () => {
    Taro.switchTab({ url: '/pages/room-list/index' })
  }

  const goToLogin = () => {
    Taro.navigateTo({ url: '/pages/login/index' })
  }

  const createRoom = () => {
    if (!loggedIn) {
      Taro.showToast({ title: '请先登录', icon: 'none' })
      return
    }
    Taro.navigateTo({ url: '/pages/room/index?action=create' })
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

  const getRankTitle = (rating: number) => {
    if (rating >= 2000) return '传奇猎魔人'
    if (rating >= 1500) return '高级猎魔人'
    if (rating >= 1200) return '猎魔人'
    if (rating >= 800) return '见习猎手'
    return '新晋村民'
  }

  return (
    <View className='index-page'>
      {/* 全屏背景海报 */}
      <Image className='bg-poster' src={homePoster} mode='aspectFill' />

      {/* 渐变遮罩：顶部微暗 → 中间透明 → 底部深暗 */}
      <View className='bg-gradient' />

      {/* 顶部状态栏 */}
      <View className='top-panel'>
        {loggedIn && userInfo ? (
          <>
            <View className='user-profile' onClick={handleLogout}>
              <View className='user-avatar'>
                {userInfo.avatarUrl ? (
                  <Image className='avatar-img' src={getResourceUrl(userInfo.avatarUrl)} mode='aspectFill' />
                ) : (
                  <IconPlayer size={24} color="#e5c040" />
                )}
              </View>
              <View className='user-meta'>
                <Text className='user-name'>{userInfo.username}</Text>
                <Text className='user-rank'>Lv.{Math.floor((userInfo.rating || 1000) / 100)} {getRankTitle(userInfo.rating || 1000)}</Text>
              </View>
            </View>
            <View className='user-assets'>
              <View className='asset-item'>
                <IconCrown size={14} color="#e5c040" />
                <Text className='asset-value'>{userInfo.rating || 1000}</Text>
              </View>
            </View>
          </>
        ) : (
          <View className='login-profile' onClick={goToLogin}>
            <View className='user-avatar'>
              <IconPlayer size={24} color="#e5c040" />
            </View>
            <View className='user-meta'>
              <Text className='user-name'>点击登录</Text>
              <Text className='user-rank'>开始你的狩猎之旅</Text>
            </View>
          </View>
        )}
      </View>

      {/* 底部操作面板 */}
      <View className='bottom-panel'>
        <View className='panel-title'>
          <Text className='game-title'>狼 人 杀</Text>
          <Text className='game-sub'>WEREWOLF</Text>
        </View>

        <Button className='action-card primary' onClick={goToRoomList} hoverClass='button-hover'>
          <View className='card-inner'>
            <View className='card-icon-wrap'>
              <IconSwords size={20} color="#fff" />
            </View>
            <View className='card-text'>
              <Text className='card-title'>快速加入</Text>
              <Text className='card-desc'>匹配开局</Text>
            </View>
          </View>
        </Button>

        <Button className='action-card secondary' onClick={createRoom} hoverClass='button-hover'>
          <View className='card-inner'>
            <View className='card-icon-wrap'>
              <IconCrown size={20} color="#e5c040" />
            </View>
            <View className='card-text'>
              <Text className='card-title'>创建房间</Text>
              <Text className='card-desc'>邀请好友</Text>
            </View>
          </View>
        </Button>
      </View>
    </View>
  )
}
