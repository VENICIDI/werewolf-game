import { useState } from 'react'
import Taro, { useDidShow } from '@tarojs/taro'
import { View, Text, Input, Button, Form } from '@tarojs/components'
import { login, saveAuth } from '../../api/auth'
import { bgm } from '../../utils/bgm'
import './index.scss'

export default function Login() {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)

  // 非游戏页面：保持 BGM 播放
  useDidShow(() => {
    bgm.ensurePlaying()
  })

  const handleLogin = async () => {
    // 表单验证
    if (!username.trim()) {
      Taro.showToast({ title: '请输入用户名', icon: 'none' })
      return
    }
    if (!password.trim()) {
      Taro.showToast({ title: '请输入密码', icon: 'none' })
      return
    }
    if (password.length < 6) {
      Taro.showToast({ title: '密码至少6位', icon: 'none' })
      return
    }

    setLoading(true)
    try {
      const res: any = await login(username, password)
      // 保存登录信息
      saveAuth(res)
      Taro.showToast({ title: '登录成功', icon: 'success' })
      // 返回首页
      setTimeout(() => {
        Taro.switchTab({ url: '/pages/index/index' })
      }, 1000)
    } catch (error: any) {
      Taro.showToast({ title: error.message || '登录失败', icon: 'none' })
    } finally {
      setLoading(false)
    }
  }

  const goToRegister = () => {
    Taro.navigateTo({ url: '/pages/register/index' })
  }

  const goBack = () => {
    Taro.navigateBack()
  }

  return (
    <View className='login-container'>
      {/* 返回按钮 */}
      <View className='back-btn' onClick={goBack}>
        <Text className='back-icon'>←</Text>
      </View>

      {/* Logo */}
      <View className='logo-section'>
        
        <Text className='logo-title'>狼人杀</Text>
        <Text className='logo-subtitle'>AI 对战平台</Text>
      </View>

      {/* 登录表单 */}
      <View className='form-section'>
        <View className='form-item'>
          <Text className='form-label'>用户名</Text>
          <Input
            className='form-input'
            type='text'
            placeholder='请输入用户名'
            value={username}
            onInput={(e) => setUsername(e.detail.value)}
          />
        </View>

        <View className='form-item'>
          <Text className='form-label'>密码</Text>
          <Input
            className='form-input'
            type='password'
            placeholder='请输入密码'
            value={password}
            onInput={(e) => setPassword(e.detail.value)}
          />
        </View>

        <Button
          className={`login-btn ${loading ? 'loading' : ''}`}
          onClick={handleLogin}
          disabled={loading}
        >
          {loading ? '登录中...' : '登 录'}
        </Button>
      </View>

      {/* 其他选项 */}
      <View className='options-section'>
        <Text className='option-text' onClick={goToRegister}>
          还没有账号？<Text className='highlight'>立即注册</Text>
        </Text>
      </View>

      {/* 测试账号提示 */}
      <View className='tips-section'>
        <Text className='tips-text'>测试账号: admin / admin</Text>
      </View>
    </View>
  )
}
