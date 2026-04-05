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
    <View style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100vh', background: 'var(--shadow, #080604)', color: 'var(--text, #f0e6d6)' }}>
      <Text style={{ fontSize: '48px', marginBottom: '20px', filter: 'drop-shadow(0 4px 12px rgba(196,26,26,0.5))' }}>🐺</Text>
      <Text style={{ fontSize: '24px', fontWeight: 'bold', marginBottom: '10px', color: 'var(--gold, #e5c040)', letterSpacing: '4px' }}>游戏大厅</Text>
      <Text style={{ fontSize: '14px', color: 'var(--text-secondary, #b0a090)', marginBottom: '30px' }}>请从房间中开始游戏</Text>
      <Button
        style={{ background: 'linear-gradient(180deg, rgba(196,26,26,0.85), rgba(106,0,0,0.95))', color: 'var(--text, #f0e6d6)', borderRadius: '8px', border: '1.5px solid var(--gold, #e5c040)', padding: '10px 40px', fontWeight: '700', letterSpacing: '3px' }}
        onClick={() => Taro.switchTab({ url: '/pages/room-list/index' })}
      >
        前往房间列表
      </Button>
    </View>
  )
}
