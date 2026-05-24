import { useState } from 'react'
import Taro from '@tarojs/taro'
import { View, Text, Input, Button } from '@tarojs/components'
import { login, wxLogin, saveAuth } from '../../api/auth'
import './index.scss'

export default function Login() {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [wxLoading, setWxLoading] = useState(false)

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
      saveAuth(res)
      Taro.showToast({ title: '登录成功', icon: 'success' })
      setTimeout(() => {
        Taro.switchTab({ url: '/pages/index/index' })
      }, 1000)
    } catch (error: any) {
      Taro.showToast({ title: error.message || '登录失败', icon: 'none' })
    } finally {
      setLoading(false)
    }
  }

  /**
   * 微信手机号一键登录
   * 参考流程图：
   *  1. getPhoneNumber 拿 phoneCode
   *  2. wx.login 拿 loginCode
   *  3. 两个 code 一起发后端 /auth/wx-login
   *  4. 后端用 phoneCode 调微信拿手机号
   *  5. 后端用 loginCode 调微信拿 openid
   *  6. 登录&绑定
   */
  const handleGetPhoneNumber = async (e: any) => {
    if (wxLoading) return
    const detail = e?.detail || {}
    // 用户拒绝授权
    if (detail.errMsg && detail.errMsg.indexOf('fail') !== -1) {
      if (detail.errMsg.indexOf('cancel') !== -1 || detail.errMsg.indexOf('deny') !== -1) {
        Taro.showToast({ title: '已取消授权', icon: 'none' })
      } else {
        Taro.showToast({ title: '授权失败，请重试', icon: 'none' })
      }
      return
    }
    const phoneCode = detail.code
    if (!phoneCode) {
      Taro.showToast({ title: '未获得手机号授权', icon: 'none' })
      return
    }

    setWxLoading(true)
    try {
      // 1. 拿 loginCode
      const loginRes = await Taro.login()
      if (!loginRes.code) {
        throw new Error('获取微信登录凭证失败')
      }

      // 2. 发后端（loginCode + phoneCode）
      const res: any = await wxLogin(loginRes.code, phoneCode)

      // 3. 保存登录态
      saveAuth(res)
      Taro.showToast({
        title: res.isNewUser ? '欢迎新用户' : '登录成功',
        icon: 'success'
      })

      // 4. 根据是否需要完善资料决定跳转
      setTimeout(() => {
        if (res.needProfileSetup) {
          Taro.redirectTo({ url: '/pages/profile-setup/index' })
        } else {
          Taro.switchTab({ url: '/pages/index/index' })
        }
      }, 800)
    } catch (error: any) {
      Taro.showToast({ title: error.message || '微信登录失败', icon: 'none' })
    } finally {
      setWxLoading(false)
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

        {/* 分割线 */}
        <View className='divider'>
          <Text className='divider-text'>或</Text>
        </View>

        {/* 微信手机号一键登录 —— 必须用 open-type，原生组件触发授权 */}
        <Button
          className={`wx-login-btn ${wxLoading ? 'loading' : ''}`}
          openType='getPhoneNumber'
          onGetPhoneNumber={handleGetPhoneNumber}
          disabled={wxLoading}
        >
          {wxLoading ? '登录中...' : '微信手机号一键登录'}
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
