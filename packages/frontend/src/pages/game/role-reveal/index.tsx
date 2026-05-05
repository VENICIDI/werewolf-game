import { useEffect, useState } from 'react'
import Taro from '@tarojs/taro'
import { View, Text, Button, Image } from '@tarojs/components'
import { Role } from '../../../api/game'
import './index.scss'

// 角色立绘图片
import imgWerewolf from '../../../assets/images/roles/werewolf.png'
import imgSeer from '../../../assets/images/roles/seer.png'
import imgWitch from '../../../assets/images/roles/witch.png'
import imgHunter from '../../../assets/images/roles/hunter.png'
import imgGuard from '../../../assets/images/roles/guard.png'
import imgVillager from '../../../assets/images/roles/villager.png'
import imgIdiot from '../../../assets/images/roles/idiot.png'

interface RoleInfo {
  id: string
  name: string
  nameEn: string
  camp: string
  campName: string
  description: string
  shortDesc: string
  image: string
  color: string
  tips: string[]
}

const roleData: Record<string, RoleInfo> = {
  [Role.VILLAGER]: {
    id: 'villager',
    name: '村民',
    nameEn: 'Villager',
    camp: 'villager',
    campName: '好人阵营',
    description: '没有特殊技能，通过分析发言和投票找出狼人',
    shortDesc: '平民，无特殊技能',
    image: imgVillager,
    color: '#4CAF50',
    tips: ['认真听发言，找出逻辑漏洞', '不要轻易暴露自己的身份', '学会分析票型']
  },
  [Role.WEREWOLF]: {
    id: 'werewolf',
    name: '狼人',
    nameEn: 'Werewolf',
    camp: 'werewolf',
    campName: '狼人阵营',
    description: '夜晚可以睁眼杀人，白天需要隐藏身份并误导好人',
    shortDesc: '夜晚杀人，白天隐藏',
    image: imgWerewolf,
    color: '#F44336',
    tips: ['学会悍跳预言家', '注意狼队友的配合', '选择合适的刀法']
  },
  [Role.SEER]: {
    id: 'seer',
    name: '预言家',
    nameEn: 'Seer',
    camp: 'villager',
    campName: '好人阵营',
    description: '每晚可以查验一名玩家的身份，是好人阵营的信息核心',
    shortDesc: '每晚查验一名玩家身份',
    image: imgSeer,
    color: '#9C27B0',
    tips: ['选择合适的时机跳身份', '留好警徽流', '注意狼人的悍跳']
  },
  [Role.WITCH]: {
    id: 'witch',
    name: '女巫',
    nameEn: 'Witch',
    camp: 'villager',
    campName: '好人阵营',
    description: '有一瓶解药和一瓶毒药，解药可以救人，毒药可以毒人',
    shortDesc: '解药救人，毒药杀人',
    image: imgWitch,
    color: '#FF9800',
    tips: ['第一晚建议救人', '毒药留给确定的狼人', '注意狼人自刀']
  },
  [Role.HUNTER]: {
    id: 'hunter',
    name: '猎人',
    nameEn: 'Hunter',
    camp: 'villager',
    campName: '好人阵营',
    description: '死亡时可以开枪带走一名玩家，被毒死不能开枪',
    shortDesc: '死亡时可以开枪',
    image: imgHunter,
    color: '#795548',
    tips: ['不要轻易暴露身份', '被票死可以带人', '被毒死不能开枪']
  },
  [Role.GUARD]: {
    id: 'guard',
    name: '守卫',
    nameEn: 'Guard',
    camp: 'villager',
    campName: '好人阵营',
    description: '每晚可以守护一名玩家，被守护的玩家不会被狼人杀死',
    shortDesc: '每晚守护一名玩家',
    image: imgGuard,
    color: '#2196F3',
    tips: ['不要连续守同一个人', '可以守女巫', '注意和女巫的配合']
  },
  [Role.IDIOT]: {
    id: 'idiot',
    name: '白痴',
    nameEn: 'Idiot',
    camp: 'villager',
    campName: '好人阵营',
    description: '被投票出局时不会死亡，但会失去投票权',
    shortDesc: '被票不死，失去投票权',
    image: imgIdiot,
    color: '#FFEB3B',
    tips: ['可以故意表现得像狼', '被票出后帮好人分析', '不要浪费免疫机会']
  }
}

export default function RoleReveal() {
  const [role, setRole] = useState<Role>(Role.UNKNOWN)
  const [showRole, setShowRole] = useState(false)
  const [countdown, setCountdown] = useState(3)

  useEffect(() => {
    // 从路由参数获取角色
    const { router } = Taro.getCurrentInstance()
    const roleParam = router?.params?.role as Role
    if (roleParam) {
      setRole(roleParam)
    }

    // 倒计时
    const timer = setInterval(() => {
      setCountdown(prev => {
        if (prev <= 1) {
          clearInterval(timer)
          setShowRole(true)
          return 0
        }
        return prev - 1
      })
    }, 1000)

    return () => clearInterval(timer)
  }, [])

  const roleInfo = roleData[role] || roleData[Role.VILLAGER]
  const isWerewolf = roleInfo.camp === 'werewolf'

  const goToGame = () => {
    const gameId = Taro.getStorageSync('currentGameId') || ''
    const roomCode = Taro.getStorageSync('currentRoomCode') || ''
    Taro.redirectTo({ url: `/pages/game/play/index?gameId=${gameId}&roomCode=${roomCode}` })
  }

  return (
    <View className='role-reveal-container'>
      {/* 背景 */}
      <View className={`background ${isWerewolf ? 'werewolf' : 'villager'}`} />

      {/* 标题 */}
      <View className='header'>
        <Text className='title'>游戏开始</Text>
        <Text className='subtitle'>你的身份是...</Text>
      </View>

      {/* 倒计时 */}
      {!showRole && (
        <View className='countdown-section'>
          <Text className='countdown-number'>{countdown}</Text>
          <Text className='countdown-text'>即将揭晓</Text>
        </View>
      )}

      {/* 角色展示 */}
      {showRole && (
        <View className='role-section'>
          {/* 阵营标识 */}
          <View className={`camp-badge ${roleInfo.camp}`}>
            <Text className='camp-name'>{roleInfo.campName}</Text>
          </View>

          {/* 角色卡片 - 使用立绘 */}
          <View className='role-card' style={{ borderColor: roleInfo.color }}>
            <Image className='role-image' src={roleInfo.image} mode='aspectFit' />
            <View className='role-info'>
              <Text className='role-name'>{roleInfo.name}</Text>
              <Text className='role-name-en'>{roleInfo.nameEn}</Text>
              <Text className='role-short'>{roleInfo.shortDesc}</Text>
            </View>
          </View>

          {/* 详细描述 */}
          <View className='description-section'>
            <Text className='description-title'>角色说明</Text>
            <Text className='description-text'>{roleInfo.description}</Text>
          </View>

          {/* 技巧提示 */}
          <View className='tips-section'>
            <Text className='tips-title'>游戏技巧</Text>
            {roleInfo.tips.map((tip, index) => (
              <View key={index} className='tip-item'>
                <Text className='tip-dot'>•</Text>
                <Text className='tip-text'>{tip}</Text>
              </View>
            ))}
          </View>

          {/* 进入游戏按钮 */}
          <Button className='enter-game-btn' onClick={goToGame}>
            进入游戏
          </Button>
        </View>
      )}
    </View>
  )
}
