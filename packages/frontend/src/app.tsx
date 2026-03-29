import { PropsWithChildren, useEffect } from 'react'
import Taro from '@tarojs/taro'
import './app.scss'

// 全局配置
Taro.setStorageSync('api_base_url', 'http://localhost:8080')

function App({ children }: PropsWithChildren<any>) {
  useEffect(() => {
    // 加载 Google Fonts
    const fontLink = document.createElement('link')
    fontLink.href = 'https://fonts.googleapis.com/css2?family=Cinzel:wght@400;700;900&family=Crimson+Text:ital,wght@0,400;0,600;1,400&family=Noto+Serif+SC:wght@400;700;900&display=swap'
    fontLink.rel = 'stylesheet'
    document.head.appendChild(fontLink)
  }, [])

  return children
}

export default App
