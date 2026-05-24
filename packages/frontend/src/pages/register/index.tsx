import { useState } from 'react'
import Taro from '@tarojs/taro'
import { View, Text, Input, Button } from '@tarojs/components'
import { register, saveAuth } from '../../api/auth'
import './index.scss'

export default function Register() {
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [loading, setLoading] = useState(false)

  const validateForm = () => {
    if (!username.trim()) {
      Taro.showToast({ title: '请输入用户名', icon: 'none' })
      return false
    }
    if (username.length < 3) {
      Taro.showToast({ title: '用户名至少3位', icon: 'none' })
      return false
    }
    if (!email.trim()) {
      Taro.showToast({ title: '请输入邮箱', icon: 'none' })
      return false
    }
    // 简单邮箱验证
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
    if (!emailRegex.test(email)) {
      Taro.showToast({ title: '邮箱格式不正确', icon: 'none' })
      return false
    }
    if (!password.trim()) {
      Taro.showToast({ title: '请输入密码', icon: 'none' })
      return false
    }
    if (password.length < 6) {
      Taro.showToast({ title: '密码至少6位', icon: 'none' })
      return false
    }
    if (password !== confirmPassword) {
      Taro.showToast({ title: '两次密码不一致', icon: 'none' })
      return false
    }
    return true
  }

  const handleRegister = async () => {
    if (!validateForm()) return

    setLoading(true)
    try {
      const res: any = await register(username, password, email)
      // 保存登录信息 (注册成功后自动登录)
      saveAuth(res)
      Taro.showToast({ title: '注册成功', icon: 'success' })
      // 返回首页
      setTimeout(() => {
        Taro.switchTab({ url: '/pages/index/index' })
      }, 1000)
    } catch (error: any) {
      Taro.showToast({ title: error.message || '注册失败', icon: 'none' })
    } finally {
      setLoading(false)
    }
  }

  const goToLogin = () => {
    Taro.navigateBack()
  }

  return (
    <View className='register-container'>
      {/* 返回按钮 */}
      <View className='back-btn' onClick={goToLogin}>
        <Text className='back-icon'>←</Text>
      </View>

      {/* 标题 */}
      <View className='header-section'>
        <Text className='header-title'>创建账号</Text>
        <Text className='header-subtitle'>加入狼人杀 AI 对战</Text>
      </View>

      {/* 注册表单 */}
      <View className='form-section'>
        <View className='form-item'>
          <Text className='form-label'>用户名</Text>
          <Input
            className='form-input'
            type='text'
            placeholder='3-20位字符'
            value={username}
            onInput={(e) => setUsername(e.detail.value)}
          />
        </View>

        <View className='form-item'>
          <Text className='form-label'>邮箱</Text>
          <Input
            className='form-input'
            type='text'
            placeholder='example@email.com'
            value={email}
            onInput={(e) => setEmail(e.detail.value)}
          />
        </View>

        <View className='form-item'>
          <Text className='form-label'>密码</Text>
          <Input
            className='form-input'
            type='password'
            placeholder='6-20位字符'
            value={password}
            onInput={(e) => setPassword(e.detail.value)}
          />
        </View>

        <View className='form-item'>
          <Text className='form-label'>确认密码</Text>
          <Input
            className='form-input'
            type='password'
            placeholder='再次输入密码'
            value={confirmPassword}
            onInput={(e) => setConfirmPassword(e.detail.value)}
          />
        </View>

        <Button
          className={`register-btn ${loading ? 'loading' : ''}`}
          onClick={handleRegister}
          disabled={loading}
        >
          {loading ? '注册中...' : '注 册'}
        </Button>
      </View>

      {/* 其他选项 */}
      <View className='options-section'>
        <Text className='option-text' onClick={goToLogin}>
          已有账号？<Text className='highlight'>立即登录</Text>
        </Text>
      </View>
    </View>
  )
}
