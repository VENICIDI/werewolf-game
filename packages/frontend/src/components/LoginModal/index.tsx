import { useState, useEffect, useCallback } from 'react'
import { View, Text, Button } from '@tarojs/components'
import {
  registerLoginModal,
  unregisterLoginModal,
  navigateToLogin,
  LoginGuardOptions
} from '../../utils/auth-guard'
import './index.scss'

export default function LoginModal() {
  const [visible, setVisible] = useState(false)
  const [options, setOptions] = useState<LoginGuardOptions>({})

  const showModal = useCallback((opts?: LoginGuardOptions) => {
    setOptions(opts || {})
    setVisible(true)
  }, [])

  useEffect(() => {
    registerLoginModal(showModal)
    return () => {
      unregisterLoginModal()
    }
  }, [showModal])

  const handleConfirm = () => {
    setVisible(false)
    // 延迟一帧再跳转，确保弹窗关闭动画流畅
    setTimeout(() => {
      navigateToLogin(options.redirectUrl)
    }, 200)
  }

  const handleCancel = () => {
    setVisible(false)
  }

  const handleOverlayClick = () => {
    setVisible(false)
  }

  if (!visible) return null

  return (
    <View className='login-modal-overlay' onClick={handleOverlayClick}>
      <View className='login-modal' onClick={(e) => e.stopPropagation()}>
        {/* 顶部装饰 */}
        <View className='modal-top-deco'>
          <View className='deco-line'></View>
          <Text className='deco-diamond'>◆</Text>
          <View className='deco-line'></View>
        </View>

        {/* 图标区 */}
        <View className='modal-icon-area'>
          <View className='icon-glow'></View>
          <Text className='modal-icon'>🌙</Text>
        </View>

        {/* 标题 */}
        <Text className='modal-title'>
          {options.title || '需要登录'}
        </Text>

        {/* 描述 */}
        <Text className='modal-message'>
          {options.message || '月夜降临，猎人需先亮明身份方可行动'}
        </Text>

        {/* 分割线 */}
        <View className='modal-divider'>
          <View className='divider-line'></View>
          <Text className='divider-dot'>✦</Text>
          <View className='divider-line'></View>
        </View>

        {/* 按钮区 */}
        <View className='modal-buttons'>
          <Button className='modal-btn cancel-btn' onClick={handleCancel}>
            <Text className='btn-text'>{options.cancelText || '稍后再说'}</Text>
          </Button>
          <Button className='modal-btn confirm-btn' onClick={handleConfirm}>
            <Text className='btn-text'>{options.confirmText || '去登录'}</Text>
          </Button>
        </View>

        {/* 底部装饰 */}
        <View className='modal-bottom-deco'>
          <View className='deco-line'></View>
          <Text className='deco-diamond'>◆</Text>
          <View className='deco-line'></View>
        </View>
      </View>
    </View>
  )
}
