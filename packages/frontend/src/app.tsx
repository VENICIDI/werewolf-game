import { PropsWithChildren, useEffect } from 'react'
import { View } from '@tarojs/components'
import Taro from '@tarojs/taro'
import LoginModal from './components/LoginModal'
import './app.scss'

// 全局配置：H5 走 dev server proxy 用相对路径；小程序必须用局域网 IP（localhost 在手机上指向手机自己）
const API_BASE_URL = process.env.TARO_ENV === 'h5' ? 'http://localhost:8088' : 'http://10.0.0.240:8088'
Taro.setStorageSync('api_base_url', API_BASE_URL)

function App({ children }: PropsWithChildren<any>) {
  useEffect(() => {
    if (typeof document !== 'undefined') {
      const fontLink = document.createElement('link')
      fontLink.href = 'https://fonts.googleapis.com/css2?family=Cinzel:wght@400;700;900&family=Crimson+Text:ital,wght@0,400;0,600;1,400&family=Noto+Serif+SC:wght@400;700;900&display=swap'
      fontLink.rel = 'stylesheet'
      document.head.appendChild(fontLink)
    }
  }, [])

  return (
    <View>
      {children}
      <LoginModal />
    </View>
  )
}

export default App
