import { PropsWithChildren } from 'react'
import Taro from '@tarojs/taro'
import './app.scss'

// 全局配置
Taro.setStorageSync('api_base_url', 'http://localhost:8080')

function App({ children }: PropsWithChildren<any>) {
  return children
}

export default App
