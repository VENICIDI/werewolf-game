import { useEffect } from 'react'
import Taro, { useRouter } from '@tarojs/taro'
import { View, Text, Button } from '@tarojs/components'

export default function GameEntry() {
  const router = useRouter()

  useEffect(() => {
    // 检查是否有游戏 ID，如果有则跳转到对应的游戏页面
    const gameId = router.params.gameId
    if (gameId) {
      Taro.redirectTo({ url: `/pages/game/play/index?gameId=${gameId}` })
    }
  }, [])

  return (
    <View style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100vh', backgroundColor: '#0f0f1e', color: '#fff' }}>
      <Text style={{ fontSize: '48px', marginBottom: '20px' }}>🐺</Text>
      <Text style={{ fontSize: '24px', fontWeight: 'bold', marginBottom: '10px' }}>游戏大厅</Text>
      <Text style={{ fontSize: '14px', color: '#aaa', marginBottom: '30px' }}>请从房间中开始游戏</Text>
      <Button
        style={{ backgroundColor: '#e94560', color: '#fff', borderRadius: '8px', border: 'none', padding: '10px 40px' }}
        onClick={() => Taro.switchTab({ url: '/pages/room-list/index' })}
      >
        前往房间列表
      </Button>
    </View>
  )
}
