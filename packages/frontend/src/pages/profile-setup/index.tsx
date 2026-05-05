import { useState } from 'react'
import Taro from '@tarojs/taro'
import { View, Text, Input, Button, Image } from '@tarojs/components'
import { uploadAvatar, updateProfile } from '../../api/user'
import { getUserInfo, getToken } from '../../api/auth'
import { getResourceUrl } from '../../utils/request'
import './index.scss'

/**
 * 完善资料页
 * 微信官方唯一允许拿真实头像/昵称的方式：
 *  - 头像：<button open-type="chooseAvatar">
 *  - 昵称：<input type="nickname">
 */
export default function ProfileSetup() {
  const existing = getUserInfo() || {}
  // 本地临时头像路径（chooseAvatar 返回）或已保存的服务器 URL
  const [avatarTempPath, setAvatarTempPath] = useState<string>('')
  const [avatarUrl, setAvatarUrl] = useState<string>(existing.avatarUrl || '')
  const [nickname, setNickname] = useState<string>(existing.nickname || '')
  const [saving, setSaving] = useState(false)

  // 选了本地头像，只暂存路径，保存时统一上传
  const handleChooseAvatar = (e: any) => {
    const { avatarUrl: tmp } = e.detail || {}
    if (tmp) {
      setAvatarTempPath(tmp)
      setAvatarUrl('') // 显示用 temp
    }
  }

  const handleSave = async () => {
    if (!nickname.trim()) {
      Taro.showToast({ title: '请填写昵称', icon: 'none' })
      return
    }
    if (!avatarTempPath && !avatarUrl) {
      Taro.showToast({ title: '请选择头像', icon: 'none' })
      return
    }
    // 登录态兜底
    if (!getToken()) {
      Taro.showToast({ title: '请先登录', icon: 'none' })
      return
    }

    setSaving(true)
    try {
      // 1. 有新选的头像就先上传
      let finalAvatarUrl = avatarUrl
      if (avatarTempPath) {
        const uploadRes = await uploadAvatar(avatarTempPath)
        finalAvatarUrl = uploadRes.url
      }

      // 2. 更新资料
      const res: any = await updateProfile(nickname.trim(), finalAvatarUrl)

      // 3. 刷新本地缓存
      const info = getUserInfo() || {}
      Taro.setStorageSync('userInfo', {
        ...info,
        nickname: res.nickname,
        avatarUrl: res.avatarUrl
      })

      Taro.showToast({ title: '保存成功', icon: 'success' })
      setTimeout(() => {
        Taro.switchTab({ url: '/pages/index/index' })
      }, 800)
    } catch (err: any) {
      // request/uploadFile 内部已 toast，这里兜底
      if (err && err.message) {
        Taro.showToast({ title: err.message, icon: 'none' })
      }
    } finally {
      setSaving(false)
    }
  }

  const handleSkip = () => {
    Taro.showModal({
      title: '跳过完善资料？',
      content: '跳过后你在房间里会以默认头像显示，可随时在"我的"页面补上',
      success: (r) => {
        if (r.confirm) {
          Taro.switchTab({ url: '/pages/index/index' })
        }
      }
    })
  }

  // 展示优先：本地临时 > 服务器已保存 > 空
  const displayAvatar = avatarTempPath
    || (avatarUrl ? getResourceUrl(avatarUrl) : '')

  return (
    <View className='setup-container'>
      <View className='setup-header'>
        <Text className='setup-title'>完善个人资料</Text>
        <Text className='setup-subtitle'>
          设置头像和昵称，其他玩家将看到你
        </Text>
      </View>

      {/* 头像选择器：必须用 open-type="chooseAvatar" */}
      <View className='avatar-section'>
        <Button
          className='avatar-chooser'
          openType='chooseAvatar'
          onChooseAvatar={handleChooseAvatar}
        >
          {displayAvatar ? (
            <Image className='avatar-img' src={displayAvatar} mode='aspectFill' />
          ) : (
            <View className='avatar-placeholder'>
              <Text className='avatar-placeholder-icon'>+</Text>
              <Text className='avatar-placeholder-text'>选择头像</Text>
            </View>
          )}
        </Button>
        <Text className='avatar-hint'>点击选择头像</Text>
      </View>

      {/* 昵称输入：必须用 type="nickname" 才能弹"使用微信昵称"快捷填充 */}
      <View className='nickname-section'>
        <Text className='nickname-label'>昵称</Text>
        <Input
          className='nickname-input'
          type='nickname'
          placeholder='请输入昵称'
          value={nickname}
          maxlength={20}
          onInput={(e) => setNickname(e.detail.value)}
        />
      </View>

      <Button
        className={`save-btn ${saving ? 'loading' : ''}`}
        onClick={handleSave}
        disabled={saving}
      >
        {saving ? '保存中...' : '保存并进入游戏'}
      </Button>

      <Button className='skip-btn' onClick={handleSkip} disabled={saving}>
        跳过
      </Button>
    </View>
  )
}
