import { useEffect, useState, useCallback, useRef } from 'react'
import Taro, { useRouter } from '@tarojs/taro'
import { View, Text, Button, ScrollView, Input } from '@tarojs/components'
import { getGameStatus, getGameSnapshot, GamePhase, GameStatus, Role } from '../../../api/game'
import { get, post } from '../../../utils/request'
import { wsManager } from '../../../utils/websocket'
import './index.scss'

interface PlayerInfo {
  playerId: number
  seatNumber: number
  username: string
  avatarUrl?: string
  isAi?: boolean
  status: string
  role?: string
  isCaptain?: boolean
  canSpeak?: boolean
}

interface GameData {
  gameId: number
  roomCode: string
  status: string
  round: number
  phase: string
  players: PlayerInfo[]
  myPlayerId?: number
  myRole?: string
  mySeat?: number
  teammates?: number[]  // 狼人队友 ID 列表
}

interface ChatMessage {
  senderId: number
  senderName: string
  content: string
  timestamp: string
}

export default function GamePlay() {
  const router = useRouter()
  const [game, setGame] = useState<GameData | null>(null)
  const [loading, setLoading] = useState(true)
  const [chatMessages, setChatMessages] = useState<ChatMessage[]>([])
  const [chatInput, setChatInput] = useState('')
  const [speakingPlayerIds, setSpeakingPlayerIds] = useState<Set<number>>(new Set())
  const [currentSpeakerId, setCurrentSpeakerId] = useState<number | null>(null)
  // 当前发言人的剩余秒数 (仅 DISCUSSION 阶段有意义)
  const [speakTimeLeft, setSpeakTimeLeft] = useState(0)
  const [selectedTarget, setSelectedTarget] = useState<number | null>(null)
  const [phaseTimeLeft, setPhaseTimeLeft] = useState(0)
  // ✨ 阶段 / 发言 的绝对结束时间戳(epoch ms,服务端时间)
  const [phaseEndsAt, setPhaseEndsAt] = useState<number>(0)
  const [speakEndsAt, setSpeakEndsAt] = useState<number>(0)
  // ✨ 服务器 - 本地时钟偏移(ms) = serverNow - Date.now() (收到消息时算)
  // 计算剩余时间:Math.max(0, (endsAt - (Date.now() + serverClockOffset)) / 1000)
  const serverClockOffsetRef = useRef<number>(0)
  const [showRoleModal, setShowRoleModal] = useState(false)
  // ✨ 女巫信息
  const [witchInfo, setWitchInfo] = useState<{
    hasSave: boolean, hasPoison: boolean,
    killTargetId?: number, killTargetSeat?: number, killTargetName?: string
  } | null>(null)
  const [witchAction, setWitchAction] = useState<'none' | 'save' | 'poison'>('none')
  // ✨ 猎人开枪
  const [canHunterShoot, setCanHunterShoot] = useState(false)
  // ✨ 投票实时快照: voterId -> targetId（0=弃票）
  const [voteRecords, setVoteRecords] = useState<Record<number, number>>({})
  const [voteProgress, setVoteProgress] = useState<{ voted: number, total: number }>({ voted: 0, total: 0 })
  // ✨ 本阶段是否已提交行动（按 "phase:round" 维度，阶段切换后自动失效）
  const [submittedPhaseKey, setSubmittedPhaseKey] = useState<string | null>(null)
  const [submittedSummary, setSubmittedSummary] = useState<string>('')
  // ✨ 同步锁：防止 React setState 异步导致的短时间连续点击提交多次
  const submittedPhaseKeyRef = useRef<string | null>(null)
  const submittingRef = useRef<boolean>(false)
  // ✨ 投票最终结果（处决面板）
  const [voteResult, setVoteResult] = useState<{
    votesByVoter: Record<number, number>   // voterId -> targetId
    voteDetails: Record<number, number>    // targetId -> count
    isTie: boolean
    eliminatedPlayerId: number | null
    eliminatedSeat: number | null
    eliminatedName?: string
    message: string
  } | null>(null)

  // 初始化：通过 API 获取游戏状态
  useEffect(() => {
    console.log('%c[VERSION] GamePlay v2 - 锁定+全员推进', 'color:#4caf50;font-weight:bold;font-size:14px')
    const gameId = router.params.gameId || Taro.getStorageSync('currentGameId')
    const roomCode = router.params.roomCode || Taro.getStorageSync('currentRoomCode')

    if (!gameId) {
      Taro.showToast({ title: '游戏参数缺失', icon: 'none' })
      setTimeout(() => Taro.switchTab({ url: '/pages/room-list/index' }), 1500)
      return
    }

    // 主动拉取游戏状态
    fetchGameData(Number(gameId))
    // 连接 WebSocket 接收增量更新
    if (roomCode) {
      connectWebSocket(String(roomCode))
    }

    return () => {
      wsManager.disconnect()
    }
  }, [])

  // ✨ 阶段倒计时(绝对时间戳驱动,自愈漂移)
  // 每 500ms 用 (phaseEndsAt - serverNow) 重算 phaseTimeLeft,服务端-本地时钟差自动补偿
  useEffect(() => {
    if (!phaseEndsAt) {
      setPhaseTimeLeft(0)
      return
    }
    const tick = () => {
      const serverNow = Date.now() + serverClockOffsetRef.current
      const remainMs = Math.max(0, phaseEndsAt - serverNow)
      setPhaseTimeLeft(Math.ceil(remainMs / 1000))
    }
    tick()
    const timer = setInterval(tick, 500)
    return () => clearInterval(timer)
  }, [phaseEndsAt])

  // ✨ 当前发言人倒计时(绝对时间戳驱动)
  useEffect(() => {
    if (!speakEndsAt) {
      setSpeakTimeLeft(0)
      return
    }
    const tick = () => {
      const serverNow = Date.now() + serverClockOffsetRef.current
      const remainMs = Math.max(0, speakEndsAt - serverNow)
      setSpeakTimeLeft(Math.ceil(remainMs / 1000))
    }
    tick()
    const timer = setInterval(tick, 500)
    return () => clearInterval(timer)
  }, [speakEndsAt])

  // ✨ 调试：监控关键状态变化
  useEffect(() => {
    if (!game) return
    console.log('[STATE] 游戏状态更新', {
      phase: game.phase,
      round: game.round,
      myRole: game.myRole,
      canAct: canAct(),
      isActionLocked: isActionLocked(),
      submittedPhaseKey,
      submittedPhaseKeyRef: submittedPhaseKeyRef.current,
      selectedTarget,
      witchAction,
    })
  }, [game?.phase, game?.round, submittedPhaseKey, selectedTarget, witchAction])

  const fetchGameData = async (gameId: number) => {
    try {
      // 获取游戏基础状态
      const gameRes: any = await get(`/games/${gameId}`)
      // 获取玩家列表（含自己的角色）
      const playersRes: any = await get(`/games/${gameId}/players`)

      const gameData: GameData = {
        gameId: gameRes.gameId,
        roomCode: Taro.getStorageSync('currentRoomCode') || '',
        status: gameRes.status,
        round: gameRes.round,
        phase: gameRes.phase,
        players: playersRes.players || [],
        myPlayerId: playersRes.myPlayerId,
        myRole: playersRes.myRole,
        mySeat: playersRes.mySeat,
        teammates: playersRes.teammates || [],
      }

      setGame(gameData)
      const initialSpeaker = (gameData.players || []).find(p => p.canSpeak && p.status === 'ALIVE')
      setCurrentSpeakerId(initialSpeaker?.playerId || null)
      setLoading(false)
      console.log('[Game] 游戏数据加载完成:', gameData.phase, '我的角色:', gameData.myRole)

      // 弹出角色提示
      if (gameData.myRole && gameData.myRole !== 'UNKNOWN') {
        setTimeout(() => setShowRoleModal(true), 500)
      }
    } catch (error: any) {
      console.error('[Game] 获取游戏数据失败:', error)
      Taro.showToast({ title: '获取游戏数据失败', icon: 'none' })
      setTimeout(() => Taro.switchTab({ url: '/pages/room-list/index' }), 2000)
    }
  }

  // ✨ 应用后端返回的完整快照到本地状态 (WS 重连/首次进入时调用)
  // 作为权威数据源对齐:阶段、倒计时、玩家状态、投票进度、发言人、行动锁
  const applySnapshot = useCallback(async (gameId: number) => {
    try {
      const snap = await getGameSnapshot(gameId)
      console.log('[Snapshot] 应用快照', snap)

      // ① 校准时钟偏移
      if (snap.serverNow) {
        serverClockOffsetRef.current = Number(snap.serverNow) - Date.now()
      }

      // ② 游戏基础状态 + 玩家列表
      setGame(prev => ({
        ...(prev || {} as GameData),
        gameId: snap.gameId,
        roomCode: Taro.getStorageSync('currentRoomCode') || '',
        status: snap.status,
        round: snap.round,
        phase: snap.phase,
        players: snap.players.map(p => ({
          playerId: p.playerId,
          seatNumber: p.seatNumber,
          username: p.username,
          avatarUrl: p.avatarUrl,
          isAi: p.isAi,
          status: p.status,
          canSpeak: p.canSpeak,
          role: p.role,
        })),
        myPlayerId: snap.myPlayerId,
        myRole: snap.myRole,
        mySeat: snap.mySeat,
        teammates: snap.teammates || [],
      }))

      // ③ 阶段时间戳
      setPhaseEndsAt(snap.phaseEndsAt || 0)

      // ④ 行动锁:用后端 mySubmitted 重建本地锁状态
      const key = `${snap.phase}:${snap.round}`
      if (snap.mySubmitted) {
        submittedPhaseKeyRef.current = key
        setSubmittedPhaseKey(key)
        setSubmittedSummary('✅ 已提交(从服务端同步)')
      } else {
        submittedPhaseKeyRef.current = null
        setSubmittedPhaseKey(null)
        setSubmittedSummary('')
      }

      // ⑤ 女巫信息 / 猎人开枪
      if (snap.witchInfo) setWitchInfo(snap.witchInfo)
      if (snap.canHunterShoot) setCanHunterShoot(true)

      // ⑥ 讨论阶段发言人 + 发言倒计时
      if (snap.discussion) {
        setCurrentSpeakerId(snap.discussion.currentSpeakerId)
        setSpeakEndsAt(snap.discussion.speakEndsAt || 0)
      } else if (snap.phase !== 'DISCUSSION') {
        setCurrentSpeakerId(null)
        setSpeakEndsAt(0)
      }

      // ⑦ 投票进度
      if (snap.vote) {
        const normalized: Record<number, number> = {}
        Object.keys(snap.vote.votesByVoter).forEach(k => {
          normalized[Number(k)] = Number(snap.vote!.votesByVoter[k])
        })
        setVoteRecords(normalized)
        setVoteProgress({ voted: snap.vote.votedCount, total: snap.vote.eligibleCount })
      }

      setLoading(false)
    } catch (error: any) {
      console.error('[Snapshot] 拉取失败', error)
    }
  }, [])

  const connectWebSocket = async (roomCode: string) => {
    try {
      wsManager.on('PHASE_CHANGE', handlePhaseChange)
      wsManager.on('PHASE_ADVANCE', handlePhaseAdvance)
      wsManager.on('PLAYER_ACTION', handlePlayerAction)
      wsManager.on('PLAYER_CHAT', handlePlayerChat)
      wsManager.on('GAME_OVER', handleGameOver)
      wsManager.on('DEATH_ANNOUNCE', handleDeathAnnounce)
      wsManager.on('ROLE_ASSIGN', handleRoleAssign)
      wsManager.on('VOTE_START', handleVoteStart)
      wsManager.on('VOTE_UPDATE', handleVoteUpdate)
      wsManager.on('VOTE_RESULT', handleVoteResult)
      wsManager.on('ACTION_CONFIRM', handleActionConfirm)
      wsManager.on('SEER_RESULT', handleSeerResult)
      wsManager.on('WITCH_INFO', handleWitchInfo)
      wsManager.on('HUNTER_SHOOT', handleHunterShoot)
      wsManager.on('SPEAKER_CHANGE', handleSpeakerChange)
      wsManager.on('SYSTEM', handleSystemMessage)
      wsManager.on('ERROR', handleWsError)

      // ✨ 重连成功后 — 主动拉取完整快照对齐本地状态
      wsManager.setOnReconnected(() => {
        const gid = Number(router.params.gameId || Taro.getStorageSync('currentGameId'))
        if (gid) {
          console.log('[WS] 重连成功,拉取游戏快照同步状态')
          applySnapshot(gid)
        }
      })

      await wsManager.connect(roomCode)
      console.log('[Game] WebSocket 已连接')
    } catch (error) {
      console.error('[Game] WebSocket 连接失败:', error)
    }
  }

  // ✨ 用 ref 存上一次已处理的 (phase, round),防止重复 PHASE_CHANGE 把已锁定状态误清
  const lastPhaseKeyRef = useRef<string | null>(null)

  const handlePhaseChange = useCallback((message: any) => {
    const data = message.data
    const incomingKey = `${data.phase}:${data.round}`
    // ✨ 重复消息去重:同一 (phase, round) 已处理过则只刷新时间戳,不做副作用
    if (lastPhaseKeyRef.current === incomingKey) {
      console.log('[WS] PHASE_CHANGE 重复消息,仅刷新时间戳 →', incomingKey)
      if (data.serverNow) {
        serverClockOffsetRef.current = Number(data.serverNow) - Date.now()
      }
      if (data.endsAt) setPhaseEndsAt(Number(data.endsAt))
      return
    }
    lastPhaseKeyRef.current = incomingKey
    console.log('[WS] PHASE_CHANGE →', { phase: data.phase, round: data.round, endsAt: data.endsAt, duration: data.duration })
    setGame(prev => prev ? {
      ...prev,
      phase: data.phase,
      round: data.round || prev.round,
    } : null)
    // ✨ 优先用绝对时间戳;校准时钟偏移
    if (data.serverNow) {
      serverClockOffsetRef.current = Number(data.serverNow) - Date.now()
    }
    if (data.endsAt) {
      setPhaseEndsAt(Number(data.endsAt))
    } else if (data.duration) {
      // 向后兼容:没有 endsAt 时,用本地时间 + duration 估算
      setPhaseEndsAt(Date.now() + Number(data.duration))
    } else {
      setPhaseEndsAt(0)
    }
    setSelectedTarget(null)
    setSpeakingPlayerIds(new Set())
    setWitchAction('none')
    // 阶段切换:清除上一阶段的"已提交"锁定
    setSubmittedPhaseKey(null)
    setSubmittedSummary('')
    submittedPhaseKeyRef.current = null
    submittingRef.current = false
    console.log('[LOCK] 🔓 阶段切换,清除锁定')
    if (data.phase !== 'DISCUSSION') {
      setCurrentSpeakerId(null)
      setSpeakEndsAt(0)
    }
    // 进入女巫阶段时不清除 witchInfo(WITCH_INFO 消息可能先到)
    if (data.phase !== 'WITCH') {
      setWitchInfo(null)
    }
    // 投票/处决状态仅在这两个阶段保留
    if (data.phase !== 'VOTING' && data.phase !== 'EXECUTION') {
      setVoteRecords({})
      setVoteProgress({ voted: 0, total: 0 })
      setVoteResult(null)
    }

    // 显示阶段提示
    if (data.message) {
      Taro.showToast({ title: data.message, icon: 'none', duration: 2000 })
    }
  }, [])

  // ✨ 后端"全员提交即推进"的广播
  const handlePhaseAdvance = useCallback((message: any) => {
    const data = message?.data || {}
    console.log('[WS] PHASE_ADVANCE →', data)
    Taro.showToast({ title: '全员已行动，进入下一阶段', icon: 'none', duration: 1200 })
  }, [])

  const handlePlayerAction = useCallback((message: any) => {
    console.log('[Game] 玩家行动:', message.data)
  }, [])

  // ✨ currentSpeakerId 的 ref 镜像,供 handlePlayerChat 等 useCallback([],...) 中无依赖访问最新值
  const currentSpeakerIdRef = useRef<number | null>(null)
  useEffect(() => {
    currentSpeakerIdRef.current = currentSpeakerId
  }, [currentSpeakerId])

  const handlePlayerChat = useCallback((message: any) => {
    const senderId = message.senderId
    setChatMessages(prev => [...prev, {
      senderId,
      senderName: message.senderName,
      content: message.data?.content || '',
      timestamp: message.timestamp
    }])
    // ✨ 仅当前发言人的聊天才触发"刚说话"高亮,避免延迟/异常消息让非当前发言人变绿
    if (senderId !== currentSpeakerIdRef.current) {
      return
    }
    setSpeakingPlayerIds(prev => new Set(prev).add(senderId))
    // 3秒后取消高亮
    setTimeout(() => {
      setSpeakingPlayerIds(prev => {
        const next = new Set(prev)
        next.delete(senderId)
        return next
      })
    }, 3000)
  }, [])

  const handleGameOver = useCallback((message: any) => {
    const data = message.data
    Taro.showModal({
      title: '游戏结束',
      content: data.message || '游戏已结束',
      showCancel: false,
      success: () => {
        Taro.switchTab({ url: '/pages/room-list/index' })
      }
    })
  }, [])

  const handleDeathAnnounce = useCallback((message: any) => {
    const data = message.data
    if (data.message) {
      Taro.showToast({ title: data.message, icon: 'none', duration: 3000 })
    }
    // 更新玩家状态
    if (data.deaths) {
      setGame(prev => {
        if (!prev) return null
        const updatedPlayers = prev.players.map(p => {
          const dead = data.deaths.find((d: any) => d.playerId === p.playerId)
          return dead ? { ...p, status: 'DEAD' } : p
        })
        return { ...prev, players: updatedPlayers }
      })
    }
  }, [])

  const handleRoleAssign = useCallback((message: any) => {
    const data = message.data
    console.log('[Game] 角色分配:', data.role, '队友:', data.teammates)
    setGame(prev => prev ? {
      ...prev,
      myRole: data.role,
      myPlayerId: data.playerId,
      teammates: data.teammates || [],
    } : null)
    setShowRoleModal(true)
  }, [])

  const handleVoteStart = useCallback((message: any) => {
    const data = message?.data || {}
    console.log('[WS] VOTE_START →', data)
    // 进入新一轮投票 → 清空上一轮的快照、结果面板以及"已提交"锁定
    setVoteRecords({})
    setVoteResult(null)
    setVoteProgress({ voted: 0, total: Number(data.eligibleCount) || 0 })
    setSubmittedPhaseKey(null)
    setSubmittedSummary('')
    submittedPhaseKeyRef.current = null
    submittingRef.current = false
    setSelectedTarget(null)
    console.log('[LOCK] 🔓 VOTE_START 清除锁定')
    Taro.showToast({ title: '投票阶段开始', icon: 'none' })
  }, [])

  // 实时投票更新：每次有人投票，后端广播 VOTE_UPDATE
  const handleVoteUpdate = useCallback((message: any) => {
    const data = message?.data || {}
    const raw = (data.votesByVoter || {}) as Record<string, number>
    const normalized: Record<number, number> = {}
    Object.keys(raw).forEach(k => {
      const v = raw[k]
      normalized[Number(k)] = typeof v === 'number' ? v : Number(v)
    })
    setVoteRecords(normalized)
    setVoteProgress({
      voted: Number(data.votedCount) || Object.keys(normalized).length,
      total: Number(data.eligibleCount) || 0,
    })
  }, [])

  const handleVoteResult = useCallback((message: any) => {
    const data = message?.data || {}
    if (data.message) {
      Taro.showToast({ title: data.message, icon: 'none', duration: 3000 })
    }
    // 更新被放逐玩家状态为 DEAD
    if (data.eliminatedPlayerId) {
      setGame(prev => {
        if (!prev) return null
        const updatedPlayers = prev.players.map(p =>
          p.playerId === data.eliminatedPlayerId ? { ...p, status: 'DEAD' } : p
        )
        return { ...prev, players: updatedPlayers }
      })
    }

    // 归一化 map (JSON 里 key 是 string)
    const rawVotes = (data.votesByVoter || {}) as Record<string, number>
    const votesByVoter: Record<number, number> = {}
    Object.keys(rawVotes).forEach(k => {
      const v = rawVotes[k]
      votesByVoter[Number(k)] = typeof v === 'number' ? v : Number(v)
    })
    const rawDetails = (data.voteDetails || {}) as Record<string, number>
    const voteDetails: Record<number, number> = {}
    Object.keys(rawDetails).forEach(k => {
      const v = rawDetails[k]
      voteDetails[Number(k)] = typeof v === 'number' ? v : Number(v)
    })

    setVoteRecords(votesByVoter)
    setVoteResult({
      votesByVoter,
      voteDetails,
      isTie: Boolean(data.isTie),
      eliminatedPlayerId: data.eliminatedPlayerId ?? null,
      eliminatedSeat: data.eliminatedSeat ?? null,
      eliminatedName: data.eliminatedName,
      message: data.message || '',
    })
  }, [])

  const handleActionConfirm = useCallback((message: any) => {
    const data = message?.data || {}
    const phase = data.phase
    const round = data.round
    console.log('[WS] ACTION_CONFIRM →', { phase, round, action: data.action, data })
    if (phase && round != null) {
      const key = `${phase}:${round}`
      submittedPhaseKeyRef.current = key
      setSubmittedPhaseKey(key)
      console.log('[LOCK] 🔒 WS 触发锁定 key=', key)
    }
    Taro.showToast({ title: '行动已确认', icon: 'success' })
  }, [])

  const handleSeerResult = useCallback((message: any) => {
    const data = message.data
    const isWolf = data?.isWerewolf
    Taro.showModal({
      title: '🔮 查验结果',
      content: data?.message || `${data?.targetSeat}号玩家是${isWolf ? '🐺 狼人' : '👤 好人'}`,
      showCancel: false,
    })
  }, [])

  const handleWitchInfo = useCallback((message: any) => {
    const data = message.data
    setWitchInfo({
      hasSave: data?.hasSave ?? false,
      hasPoison: data?.hasPoison ?? false,
      killTargetId: data?.killTargetId,
      killTargetSeat: data?.killTargetSeat,
      killTargetName: data?.killTargetName,
    })
  }, [])

  const handleHunterShoot = useCallback((message: any) => {
    const data = message.data
    if (data?.canShoot) {
      // 通知猎人可以开枪
      setCanHunterShoot(true)
      Taro.showToast({ title: '你已死亡，请选择开枪目标！', icon: 'none', duration: 3000 })
    } else {
      // 广播猎人开枪结果
      if (data?.message) {
        Taro.showToast({ title: data.message, icon: 'none', duration: 3000 })
      }
      // 更新被射杀玩家状态
      if (data?.targetSeat) {
        setGame(prev => {
          if (!prev) return null
          const updatedPlayers = prev.players.map(p =>
            p.seatNumber === data.targetSeat ? { ...p, status: 'DEAD' } : p
          )
          return { ...prev, players: updatedPlayers }
        })
      }
    }
  }, [])

  const handleSystemMessage = useCallback((message: any) => {
    if (message.data?.message) {
      Taro.showToast({ title: message.data.message, icon: 'none' })
    }
  }, [])

  const handleSpeakerChange = useCallback((message: any) => {
    const data = message.data || {}
    const nextSpeakerId = Number(data.playerId)
    if (!nextSpeakerId) return
    // ✨ 切换发言人时立即清空"刚说话"高亮 Set,避免上一位的残影与新发言人的 active-speaker 同时高亮
    setSpeakingPlayerIds(new Set())
    setCurrentSpeakerId(prev => {
      // 幂等去重:相同发言人重复广播只刷新时间戳,不重渲染
      if (prev === nextSpeakerId) return prev
      return nextSpeakerId
    })
    // ✨ 校准时钟偏移 + 驱动绝对时间戳倒计时
    if (data.serverNow) {
      serverClockOffsetRef.current = Number(data.serverNow) - Date.now()
    }
    if (data.endsAt) {
      setSpeakEndsAt(Number(data.endsAt))
    } else if (data.speakTimeMs) {
      setSpeakEndsAt(Date.now() + Number(data.speakTimeMs))
    }
    setGame(prev => {
      if (!prev) return null
      return {
        ...prev,
        players: prev.players.map(p => ({
          ...p,
          canSpeak: p.playerId === nextSpeakerId && p.status === 'ALIVE',
        })),
      }
    })
    if (data.message) {
      Taro.showToast({ title: data.message, icon: 'none', duration: 1200 })
    }
  }, [])

  const handleWsError = useCallback((message: any) => {
    const text = typeof message?.data === 'string' ? message.data : ''
    if (text) {
      Taro.showToast({ title: text, icon: 'none' })
    }
  }, [])

  // 获取阶段展示信息
  const getPhaseDisplay = () => {
    switch (game?.phase) {
      case 'NIGHT_START':
        return { title: '天黑了', subtitle: '所有人请闭眼', icon: '🌙', bg: 'night' }
      case 'GUARD':
        return { title: '守卫行动', subtitle: '请选择要守护的目标', icon: '🛡️', bg: 'night' }
      case 'WEREWOLF':
        return { title: '狼人行动', subtitle: '请选择要击杀的目标', icon: '🐺', bg: 'night' }
      case 'SEER':
        return { title: '预言家行动', subtitle: '请选择要查验的目标', icon: '🔮', bg: 'night' }
      case 'WITCH':
        return { title: '女巫行动', subtitle: '是否使用解药/毒药', icon: '🧙', bg: 'night' }
      case 'DAY_START':
        return { title: '天亮了', subtitle: `第 ${game?.round} 天`, icon: '☀️', bg: 'day' }
      case 'DISCUSSION':
        return { title: '讨论阶段', subtitle: '请发表你的看法', icon: '💬', bg: 'day' }
      case 'VOTING':
        return { title: '投票阶段', subtitle: '请选择要放逐的玩家', icon: '🗳️', bg: 'day' }
      case 'EXECUTION':
        return { title: '处决阶段', subtitle: '公布投票结果', icon: '⚖️', bg: 'day' }
      default:
        return { title: '游戏进行中', subtitle: game?.phase || '', icon: '🐺', bg: 'night' }
    }
  }

  // 判断当前阶段我是否可以行动
  const canAct = () => {
    if (!game || !game.myRole) return false

    // ✨ 猎人开枪（死亡后可行动）
    if (canHunterShoot && game.myRole === 'HUNTER') return true

    const myPlayer = game.players.find(p => p.playerId === game.myPlayerId)
    if (!myPlayer || myPlayer.status !== 'ALIVE') return false

    // ✨ 本阶段已提交 → 锁定，不可再行动
    if (isActionLocked()) return false

    switch (game.phase) {
      case 'WEREWOLF': return game.myRole === 'WEREWOLF'
      case 'SEER': return game.myRole === 'SEER'
      case 'WITCH': return game.myRole === 'WITCH'
      case 'GUARD': return game.myRole === 'GUARD'
      case 'VOTING': return true
      default: return false
    }
  }

  // 获取行动类型
  const getActionType = () => {
    // 猎人开枪
    if (canHunterShoot && game?.myRole === 'HUNTER') return 'shoot'

    switch (game?.phase) {
      case 'WEREWOLF': return 'kill'
      case 'SEER': return 'check'
      case 'WITCH': return witchAction === 'save' ? 'save' : witchAction === 'poison' ? 'poison' : 'skip'
      case 'GUARD': return 'guard'
      case 'VOTING': return 'vote'
      default: return 'skip'
    }
  }

  // 本阶段是否已提交（阶段 + 回合维度，切阶段或下一轮自动失效）
  const isActionLocked = (): boolean => {
    if (!game) return false
    const expected = `${game.phase}:${game.round}`
    // 优先读 ref（同步）；setState 的值用于 UI，ref 用于事件处理
    const lockedKey = submittedPhaseKeyRef.current || submittedPhaseKey
    return lockedKey === expected
  }

  // 标记已提交（HTTP 成功后立刻调用，不等 WebSocket 兜底）
  const markSubmitted = (summary: string) => {
    if (!game) return
    const key = `${game.phase}:${game.round}`
    submittedPhaseKeyRef.current = key
    setSubmittedPhaseKey(key)
    setSubmittedSummary(summary)
    console.log('[LOCK] 🔒 markSubmitted key=', key, 'summary=', summary)
  }

  // 女巫救人：先二次确认
  const handleWitchSave = async () => {
    console.log('[BTN] 💊 使用解药按钮触发', {
      phase: game?.phase, round: game?.round,
      isActionLocked: isActionLocked(),
      submitting: submittingRef.current,
      submittedPhaseKeyRef: submittedPhaseKeyRef.current,
      witchInfo,
    })
    if (!game) return
    if (isActionLocked() || submittingRef.current) {
      console.log('[BTN] ❌ 解药被拦截：已锁定或正在提交')
      return
    }
    const targetSeat = witchInfo?.killTargetSeat
    const targetName = witchInfo?.killTargetName
    const confirmText = targetSeat
      ? `确定要使用【解药】救活 ${targetSeat}号 ${targetName || ''} 吗？\n\n⚠️ 解药整局仅一次，使用后不可撤销。`
      : `确定要使用【解药】吗？\n\n⚠️ 解药整局仅一次，使用后不可撤销。`
    const res = await Taro.showModal({
      title: '💊 使用解药确认',
      content: confirmText,
      confirmText: '确认救人',
      confirmColor: '#2e7d32',
      cancelText: '再想想',
    })
    console.log('[BTN] 解药 Modal 结果:', res.confirm)
    if (!res.confirm) return
    if (isActionLocked() || submittingRef.current) {
      console.log('[BTN] ❌ 解药 Modal 确认后再次检查被拦截')
      return
    }
    submittingRef.current = true
    console.log('[BTN] 🔒 开始发送解药请求')
    try {
      await post(`/games/${game.gameId}/action`, { action: 'save' })
      markSubmitted(`💊 已使用解药救 ${targetSeat || ''}号`)
      setWitchAction('none')
      Taro.showToast({ title: '💊 已使用解药', icon: 'none' })
      console.log('[BTN] ✅ 解药请求成功')
    } catch (error: any) {
      console.warn('[Game] 解药行动失败:', error?.message, error)
    } finally {
      submittingRef.current = false
      console.log('[BTN] 🔓 解药请求结束，释放 submittingRef')
    }
  }

  // 女巫毒人：先二次确认
  const handleWitchPoison = async () => {
    console.log('[BTN] ☠️ 使用毒药按钮触发', {
      phase: game?.phase, round: game?.round,
      isActionLocked: isActionLocked(),
      submitting: submittingRef.current,
      selectedTarget,
    })
    if (!game) return
    if (isActionLocked() || submittingRef.current) {
      console.log('[BTN] ❌ 毒药被拦截：已锁定或正在提交')
      return
    }
    if (!selectedTarget) {
      Taro.showToast({ title: '请选择毒杀目标', icon: 'none' })
      console.log('[BTN] ❌ 毒药未选中目标')
      return
    }
    const target = game.players.find(p => p.playerId === selectedTarget)
    const confirmText = target
      ? `确定要使用【毒药】毒杀 ${target.seatNumber}号 ${target.username || ''} 吗？\n\n⚠️ 毒药整局仅一次，使用后不可撤销。\n⚠️ 误毒好人将大幅增加狼人胜率。`
      : `确定要使用【毒药】吗？\n\n⚠️ 毒药整局仅一次，使用后不可撤销。`
    const res = await Taro.showModal({
      title: '☠️ 使用毒药确认',
      content: confirmText,
      confirmText: '确认毒杀',
      confirmColor: '#d32f2f',
      cancelText: '再想想',
    })
    console.log('[BTN] 毒药 Modal 结果:', res.confirm)
    if (!res.confirm) return
    if (isActionLocked() || submittingRef.current) {
      console.log('[BTN] ❌ 毒药 Modal 确认后再次检查被拦截')
      return
    }
    submittingRef.current = true
    console.log('[BTN] 🔒 开始发送毒药请求 target=', selectedTarget)
    try {
      await post(`/games/${game.gameId}/action`, {
        action: 'poison',
        targetId: selectedTarget,
      })
      markSubmitted(`☠️ 已毒杀 ${target?.seatNumber || ''}号 ${target?.username || ''}`)
      setWitchAction('none')
      Taro.showToast({ title: '☠️ 已使用毒药', icon: 'none' })
      console.log('[BTN] ✅ 毒药请求成功')
    } catch (error: any) {
      console.warn('[Game] 毒药行动失败:', error?.message, error)
    } finally {
      submittingRef.current = false
      console.log('[BTN] 🔓 毒药请求结束')
    }
  }

  const handleAction = async () => {
    console.log('[BTN] 🎯 确认行动按钮触发', {
      phase: game?.phase, round: game?.round,
      myRole: game?.myRole,
      actionType: getActionType(),
      selectedTarget,
      isActionLocked: isActionLocked(),
      submitting: submittingRef.current,
      submittedPhaseKeyRef: submittedPhaseKeyRef.current,
      submittedPhaseKey,
    })
    if (!game) return
    if (isActionLocked() || submittingRef.current) {
      console.log('[BTN] ❌ 确认行动被拦截：已锁定或正在提交')
      return
    }
    const actionType = getActionType()

    // 女巫救人：走专用确认流程
    if (actionType === 'save') {
      await handleWitchSave()
      return
    }
    // 女巫毒人：走专用确认流程
    if (actionType === 'poison') {
      await handleWitchPoison()
      return
    }

    if (!selectedTarget) {
      Taro.showToast({ title: '请选择目标', icon: 'none' })
      console.log('[BTN] ❌ 确认行动未选中目标')
      return
    }
    submittingRef.current = true
    console.log(`[BTN] 🔒 开始发送 ${actionType} 请求 target=${selectedTarget}`)
    try {
      const res: any = await post(`/games/${game.gameId}/action`, {
        action: actionType,
        targetId: selectedTarget
      })
      const target = game.players.find(p => p.playerId === selectedTarget)
      const actionLabel: Record<string, string> = {
        kill: '🐺 已选择击杀',
        check: '🔮 已查验',
        guard: '🛡️ 已守护',
        vote: '🗳️ 已投票给',
        shoot: '🎯 已开枪击毙',
      }
      markSubmitted(`${actionLabel[actionType] || '✅ 已行动'} ${target?.seatNumber || ''}号 ${target?.username || ''}`)
      console.log(`[BTN] ✅ ${actionType} 请求成功，锁定 key=${game.phase}:${game.round}`)
      // 预言家查验：直接从 HTTP 响应弹出结果（不依赖 WebSocket SEER_RESULT）
      if (actionType === 'check' && res) {
        const isWolf = res.isWerewolf
        Taro.showModal({
          title: '🔮 查验结果',
          content: res.message || `${res.targetSeat}号玩家是${isWolf ? '🐺 狼人' : '👤 好人'}`,
          showCancel: false,
        })
      }
      // 猎人开枪后清除状态
      if (actionType === 'shoot') {
        setCanHunterShoot(false)
      }
    } catch (error: any) {
      console.warn(`[Game] ${actionType} 行动失败:`, error?.message, error)
    } finally {
      submittingRef.current = false
      console.log('[BTN] 🔓 行动请求结束')
    }
  }

  // 女巫"不使用"的二次确认
  const handleWitchSkip = async () => {
    console.log('[BTN] 🌙 女巫不使用按钮触发', {
      isActionLocked: isActionLocked(),
      submitting: submittingRef.current,
    })
    if (!game) return
    if (isActionLocked() || submittingRef.current) {
      console.log('[BTN] ❌ 女巫跳过被拦截')
      return
    }
    const res = await Taro.showModal({
      title: '🌙 确认放弃用药',
      content: '确定本轮不使用解药和毒药吗？\n\n⚠️ 本轮放弃后，今晚被狼人刀到的玩家将死亡。',
      confirmText: '确认放弃',
      confirmColor: '#8a7a68',
      cancelText: '再想想',
    })
    if (!res.confirm) return
    if (isActionLocked() || submittingRef.current) return
    submittingRef.current = true
    try {
      await post(`/games/${game.gameId}/action`, { action: 'skip' })
      markSubmitted('🌙 本轮已放弃用药')
      Taro.showToast({ title: '已跳过', icon: 'none' })
      console.log('[BTN] ✅ 女巫跳过成功')
    } catch (error: any) {
      console.warn('[Game] 女巫跳过失败:', error?.message, error)
    } finally {
      submittingRef.current = false
    }
  }

  const handleSkip = async () => {
    console.log('[BTN] ⏭️ 跳过按钮触发', {
      phase: game?.phase, round: game?.round,
      isActionLocked: isActionLocked(),
      submitting: submittingRef.current,
    })
    if (!game) return
    if (isActionLocked() || submittingRef.current) {
      console.log('[BTN] ❌ 跳过被拦截')
      return
    }
    submittingRef.current = true
    try {
      await post(`/games/${game.gameId}/action`, { action: 'skip' })
      markSubmitted('🌙 已跳过本轮行动')
      Taro.showToast({ title: '已跳过', icon: 'none' })
      console.log('[BTN] ✅ 跳过成功')
    } catch (error: any) {
      console.warn('[Game] 跳过失败:', error?.message, error)
    } finally {
      submittingRef.current = false
    }
  }

  // 角色名中文映射
  const getRoleName = (role?: string) => {
    const map: Record<string, string> = {
      VILLAGER: '村民', WEREWOLF: '狼人', SEER: '预言家',
      WITCH: '女巫', HUNTER: '猎人', GUARD: '守卫', IDIOT: '白痴',
      UNKNOWN: '未知'
    }
    return map[role || ''] || role || '未知'
  }

  // 判断当前阶段是否可以发送聊天消息（讨论阶段轮流发言）
  const canChat = () => {
    if (!game) return false
    const myPlayer = game.players.find(p => p.playerId === game.myPlayerId)
    if (!myPlayer || myPlayer.status !== 'ALIVE') return false
    return game.phase === 'DISCUSSION' && currentSpeakerId === game.myPlayerId
  }

  const getActingRoleForPhase = (phase?: string) => {
    const roleByPhase: Record<string, string> = {
      WEREWOLF: 'WEREWOLF',
      SEER: 'SEER',
      WITCH: 'WITCH',
      GUARD: 'GUARD',
      HUNTER: 'HUNTER',
    }
    return roleByPhase[phase || '']
  }

  const getPlayerHighlight = (player: PlayerInfo) => {
    if (!game || player.status !== 'ALIVE') {
      return { active: false, className: '', label: '' }
    }

    if (game.phase === 'DISCUSSION') {
      const isCurrentSpeaker = player.playerId === currentSpeakerId
      return { active: isCurrentSpeaker, className: isCurrentSpeaker ? 'active-speaker' : '', label: isCurrentSpeaker ? '发言中' : '' }
    }

    const actingRole = getActingRoleForPhase(game.phase)
    if (!actingRole) {
      return { active: false, className: '', label: '' }
    }

    const isMe = player.playerId === game.myPlayerId
    const isWerewolfTeammate = actingRole === 'WEREWOLF' && game.teammates?.includes(player.playerId)
    const roleVisibleToMe = isMe ? game.myRole : player.role
    const isActingPlayer = roleVisibleToMe === actingRole || isWerewolfTeammate

    return {
      active: isActingPlayer,
      className: isActingPlayer ? `active-turn active-${actingRole.toLowerCase()}` : '',
      label: isActingPlayer ? '行动中' : '',
    }
  }

  // 发送聊天消息
  const handleSendChat = () => {
    if (!canChat()) {
      Taro.showToast({ title: '还没轮到你发言', icon: 'none' })
      return
    }
    if (!chatInput.trim()) return
    wsManager.sendChat(chatInput)
    setChatInput('')
  }

  // ✨ 结束发言 — 主动跳过剩余时间
  const handleSkipSpeech = () => {
    if (!canChat()) {
      Taro.showToast({ title: '当前不是你的发言时段', icon: 'none' })
      return
    }
    wsManager.sendSkipSpeech()
    setSpeakTimeLeft(0)
  }

  const currentSpeaker = game?.players.find(p => p.playerId === currentSpeakerId)

  const phaseDisplay = getPhaseDisplay()

  if (loading) {
    return (
      <View className='game-play-container'>
        <View className='loading'>穿越血月迷雾...</View>
      </View>
    )
  }

  return (
    <View className={`game-play-container ${phaseDisplay.bg}`}>
      {/* 顶部信息栏 */}
      <View className='top-bar'>
        <View className='round-info'>
          <Text className='round-text'>第 {game?.round} 天</Text>
          <Text className='phase-text'>{phaseDisplay.title}</Text>
        </View>
        <View className='timer'>
          <Text className='timer-icon'>⏱️</Text>
          {/* ✨ 讨论阶段:优先显示当前发言人的剩余发言时间(每人 60s);
              其他阶段显示阶段总倒计时 */}
          {game?.phase === 'DISCUSSION' && speakEndsAt > 0 ? (
            <Text className='timer-text'>{speakTimeLeft}s</Text>
          ) : (
            <Text className='timer-text'>{phaseTimeLeft}s</Text>
          )}
        </View>
        <View className='role-btn' onClick={() => setShowRoleModal(true)}>
          <Text className='role-icon'>👤</Text>
        </View>
      </View>

      {/* 阶段提示 */}
      <View className='phase-banner'>
        <Text className='phase-icon'>{phaseDisplay.icon}</Text>
        <Text className='phase-title'>{phaseDisplay.title}</Text>
        <Text className='phase-subtitle'>{phaseDisplay.subtitle}</Text>
      </View>

      {/* ✨ 投票阶段进度条 */}
      {(game?.phase === 'VOTING' || game?.phase === 'EXECUTION') && voteProgress.total > 0 && (
        <View style={{
          margin: '8px 12px 0', padding: '8px 12px',
          background: 'rgba(20,16,12,0.75)',
          border: '1px solid rgba(212,175,55,0.3)',
          borderRadius: '8px'
        }}>
          <View style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '6px' }}>
            <Text style={{ fontSize: '13px', color: '#d4af37', fontWeight: 'bold' }}>
              🗳️ 投票进度
            </Text>
            <Text style={{ fontSize: '12px', color: '#c9a86a' }}>
              {voteProgress.voted} / {voteProgress.total}
            </Text>
          </View>
          <View style={{
            height: '6px', background: 'rgba(0,0,0,0.4)',
            borderRadius: '3px', overflow: 'hidden'
          }}>
            <View style={{
              height: '100%',
              width: `${voteProgress.total > 0 ? (voteProgress.voted / voteProgress.total) * 100 : 0}%`,
              background: 'linear-gradient(90deg, #d4af37, #ff9800)',
              transition: 'width 0.4s ease'
            }} />
          </View>
        </View>
      )}

      {/* 圆桌式玩家列表 */}
      <View className='round-table-section'>
        {/* 圆桌桌面 */}
        <View className='table-surface'>
          <View className='table-center'>
            <Text className='table-phase-icon'>{phaseDisplay.icon}</Text>
            {game?.myRole === 'WEREWOLF' && game?.teammates && game.teammates.length > 0 && (
              <Text className='table-wolf-hint'>
                队友: {game.teammates.map(tid => {
                  const p = game.players.find(pl => pl.playerId === tid)
                  return p ? `${p.seatNumber}号` : ''
                }).join(' ')}
              </Text>
            )}
          </View>
        </View>

        {/* 玩家座位 - 圆形排列 */}
        <View className='seats-ring'>
          {game?.players.map((player, index) => {
            const isTeammate = game?.myRole === 'WEREWOLF' && game?.teammates?.includes(player.playerId)
            const isMe = player.playerId === game?.myPlayerId
            const highlight = getPlayerHighlight(player)
            const showVoteUI = game?.phase === 'EXECUTION'
            const finalVotes: Record<number, number> = (showVoteUI && voteResult?.votesByVoter) || {}
            const receivedVotes = showVoteUI
              ? Object.values(finalVotes).filter(t => Number(t) === player.playerId).length
              : 0
            const myVoteTarget = (showVoteUI && game?.myPlayerId != null)
              ? finalVotes[game.myPlayerId]
              : undefined
            const iVotedHim = showVoteUI && myVoteTarget != null && Number(myVoteTarget) === player.playerId
            const isEliminated = game?.phase === 'EXECUTION'
              && voteResult?.eliminatedPlayerId === player.playerId

            // 圆桌位置计算：以 CSS 变量注入角度
            const total = game?.players.length || 9
            const angle = (index / total) * 360 - 90 // 从顶部开始
            const seatStyle = {
              '--seat-angle': `${angle}deg`,
              '--seat-index': `${index}`,
            } as React.CSSProperties

            return (
              <View
                key={player.playerId}
                className={`seat-node ${player.status} ${selectedTarget === player.playerId ? 'selected' : ''} ${speakingPlayerIds.has(player.playerId) ? 'speaking' : ''} ${isTeammate ? 'werewolf-teammate' : ''} ${highlight.className} ${isEliminated ? 'eliminated' : ''} ${isMe ? 'is-me' : ''}`}
                style={seatStyle}
                onClick={() => {
                  if (!canAct() || player.status !== 'ALIVE') return
                  if (game?.phase === 'WEREWOLF' && (isTeammate || isMe)) return
                  setSelectedTarget(player.playerId)
                }}
              >
                <View className='seat-avatar'>
                  <Text className='avatar-icon'>
                    {player.status === 'DEAD' ? '💀'
                      : (isTeammate || (isMe && game?.myRole === 'WEREWOLF')) ? '🐺'
                      : player.isAi ? '🤖' : '👤'}
                  </Text>
                  {/* 投票得票气泡 */}
                  {showVoteUI && receivedVotes > 0 && (
                    <View className='vote-bubble'>
                      <Text className='vote-count'>{receivedVotes}</Text>
                    </View>
                  )}
                </View>
                <Text className='seat-name'>{isMe ? '我' : player.username}</Text>
                <Text className='seat-number'>{player.seatNumber}号</Text>
                {highlight.active && <View className='seat-active-ring' />}
                {iVotedHim && <Text className='my-vote-mark'>✦</Text>}
                {isEliminated && <Text className='eliminated-mark'>✗</Text>}
              </View>
            )
          })}
        </View>
      </View>

      {/* ✨ 投票明细面板（VOTING 阶段仅显示进度 / EXECUTION 显示最终明细） */}
      {(game?.phase === 'VOTING' || game?.phase === 'EXECUTION') && (() => {
        // VOTING 阶段:后端不下发投票明细,只显示进度提示,避免提前暴露"谁投了谁";
        // EXECUTION 阶段:展示最终投票结果(voteResult)。
        if (game?.phase === 'VOTING') {
          const voted = voteProgress.voted
          const total = voteProgress.total
          const allVoted = total > 0 && voted >= total
          return (
            <View style={{
              margin: '8px 12px', padding: '14px',
              background: 'rgba(20,16,12,0.8)',
              border: '1px solid rgba(212,175,55,0.3)',
              borderRadius: '10px', textAlign: 'center'
            }}>
              <Text style={{
                display: 'block', fontSize: '14px', color: '#d4af37',
                fontWeight: 'bold', marginBottom: '6px'
              }}>
                🗳 投票进行中
              </Text>
              <Text style={{ fontSize: '13px', color: '#c9a86a' }}>
                {allVoted
                  ? '全员已投票，等待阶段结束后公布结果…'
                  : `已有 ${voted}/${total} 人完成投票，结果将在阶段结束后公布`}
              </Text>
            </View>
          )
        }

        // 以下为 EXECUTION 阶段分支
        const records: Record<number, number> = (voteResult && voteResult.votesByVoter) || {}
        const entries = Object.entries(records).map(([vid, tid]) => ({
          voterId: Number(vid),
          targetId: Number(tid),
        }))
        // 排序：按投票人座位号
        entries.sort((a, b) => {
          const sa = game?.players.find(p => p.playerId === a.voterId)?.seatNumber ?? 99
          const sb = game?.players.find(p => p.playerId === b.voterId)?.seatNumber ?? 99
          return sa - sb
        })
        // 汇总得票数（用于 EXECUTION 阶段柱状）
        const countMap: Record<number, number> = {}
        entries.forEach(e => {
          if (e.targetId && e.targetId > 0) {
            countMap[e.targetId] = (countMap[e.targetId] || 0) + 1
          }
        })
        const maxCount = Math.max(1, ...Object.values(countMap))
        const countEntries = Object.entries(countMap)
          .map(([tid, c]) => ({ targetId: Number(tid), count: c }))
          .sort((a, b) => b.count - a.count)
        const abstainCount = entries.filter(e => !e.targetId || e.targetId <= 0).length

        const isFinal = game?.phase === 'EXECUTION' && !!voteResult

        return (
          <View style={{
            margin: '8px 12px', padding: '12px',
            background: isFinal
              ? 'linear-gradient(180deg, rgba(50,20,20,0.85), rgba(20,16,12,0.9))'
              : 'rgba(20,16,12,0.8)',
            border: isFinal
              ? '1.5px solid rgba(198,40,40,0.55)'
              : '1px solid rgba(212,175,55,0.3)',
            borderRadius: '10px',
            boxShadow: isFinal ? '0 4px 18px rgba(198,40,40,0.3)' : 'none'
          }}>
            <Text style={{
              display: 'block', fontSize: '14px',
              color: isFinal ? '#ff8a80' : '#d4af37',
              fontWeight: 'bold', marginBottom: '8px',
              textAlign: 'center'
            }}>
              {isFinal ? '⚖ 放逐结果' : '🗳 实时投票'}
            </Text>

            {/* EXECUTION 阶段：最终处决结论 */}
            {isFinal && voteResult && (
              <View style={{
                marginBottom: '10px', padding: '10px',
                background: voteResult.eliminatedPlayerId
                  ? 'rgba(198,40,40,0.22)'
                  : 'rgba(80,80,80,0.22)',
                borderRadius: '6px',
                border: `1px solid ${voteResult.eliminatedPlayerId ? 'rgba(198,40,40,0.6)' : 'rgba(140,140,140,0.4)'}`,
                textAlign: 'center'
              }}>
                <Text style={{
                  fontSize: '15px', fontWeight: 'bold',
                  color: voteResult.eliminatedPlayerId ? '#ff6b6b' : '#c9a86a'
                }}>
                  {voteResult.eliminatedPlayerId
                    ? `⚔ ${voteResult.eliminatedSeat}号 ${voteResult.eliminatedName || ''} 被放逐`
                    : (voteResult.isTie ? '⚖ 平票，无人被放逐' : '🕊 无人被放逐')}
                </Text>
              </View>
            )}

            {/* 每个目标的得票柱状 */}
            {countEntries.length > 0 && (
              <View style={{ marginBottom: '8px' }}>
                {countEntries.map(({ targetId, count }) => {
                  const target = game?.players.find(p => p.playerId === targetId)
                  const isEliminated = isFinal && voteResult?.eliminatedPlayerId === targetId
                  return (
                    <View key={targetId} style={{ marginBottom: '6px' }}>
                      <View style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2px' }}>
                        <Text style={{ fontSize: '12px', color: isEliminated ? '#ff8a80' : '#c9a86a' }}>
                          {target ? `${target.seatNumber}号 ${target.username}` : `ID ${targetId}`}
                          {isEliminated && ' ⚔'}
                        </Text>
                        <Text style={{ fontSize: '12px', color: isEliminated ? '#ff8a80' : '#c9a86a', fontWeight: 'bold' }}>
                          {count}票
                        </Text>
                      </View>
                      <View style={{
                        height: '6px', background: 'rgba(0,0,0,0.4)',
                        borderRadius: '3px', overflow: 'hidden'
                      }}>
                        <View style={{
                          height: '100%',
                          width: `${(count / maxCount) * 100}%`,
                          background: isEliminated
                            ? 'linear-gradient(90deg, #ff6b6b, #c62828)'
                            : 'linear-gradient(90deg, #d4af37, #ff9800)',
                          transition: 'width 0.4s ease'
                        }} />
                      </View>
                    </View>
                  )
                })}
                {abstainCount > 0 && (
                  <View style={{ marginTop: '4px' }}>
                    <Text style={{ fontSize: '11px', color: '#8a7a68' }}>
                      弃票: {abstainCount}
                    </Text>
                  </View>
                )}
              </View>
            )}

            {/* 投票明细："X号 → Y号" */}
            <View style={{
              paddingTop: '8px',
              borderTop: '1px dashed rgba(212,175,55,0.25)'
            }}>
              <Text style={{ display: 'block', fontSize: '11px', color: '#8a7a68', marginBottom: '6px' }}>
                投票明细
              </Text>
              <View style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
                {entries.map(({ voterId, targetId }) => {
                  const voter = game?.players.find(p => p.playerId === voterId)
                  const target = targetId > 0 ? game?.players.find(p => p.playerId === targetId) : null
                  const abstain = !targetId || targetId <= 0
                  return (
                    <View key={voterId} style={{
                      padding: '3px 8px',
                      background: abstain ? 'rgba(80,80,80,0.25)' : 'rgba(212,175,55,0.12)',
                      border: `1px solid ${abstain ? 'rgba(140,140,140,0.3)' : 'rgba(212,175,55,0.3)'}`,
                      borderRadius: '10px'
                    }}>
                      <Text style={{ fontSize: '11px', color: abstain ? '#8a7a68' : '#c9a86a' }}>
                        {voter ? `${voter.seatNumber}号` : `ID${voterId}`}
                        {abstain ? ' 弃票' : ` → ${target ? `${target.seatNumber}号` : `ID${targetId}`}`}
                      </Text>
                    </View>
                  )
                })}
              </View>
            </View>
          </View>
        )
      })()}

      {/* ✨ 已提交行动：锁定提示（仅当前阶段我本应能行动时展示） */}
      {(() => {
        if (!game || !isActionLocked()) return null
        // 仅当角色/阶段匹配时展示"已锁定"，避免旁观者看到
        const myPlayer = game.players.find(p => p.playerId === game.myPlayerId)
        if (!myPlayer || myPlayer.status !== 'ALIVE') return null
        const roleActivePhases: Record<string, string> = {
          WEREWOLF: 'WEREWOLF', SEER: 'SEER', WITCH: 'WITCH', GUARD: 'GUARD',
        }
        const isMyActingPhase =
          roleActivePhases[game.myRole || ''] === game.phase || game.phase === 'VOTING'
        if (!isMyActingPhase) return null
        return (
          <View className='action-section' style={{ opacity: 0.92 }}>
            <View style={{
              padding: '14px 12px',
              background: 'linear-gradient(180deg, rgba(76,175,80,0.16) 0%, rgba(46,125,50,0.10) 100%)',
              borderRadius: '10px',
              border: '2px solid rgba(76,175,80,0.55)',
              textAlign: 'center',
            }}>
              <Text style={{ fontSize: '24px', display: 'block', marginBottom: '4px' }}>🔒</Text>
              <Text style={{ color: '#a5d6a7', fontSize: '12px', display: 'block', marginBottom: '4px' }}>
                — 本阶段行动已锁定 —
              </Text>
              <Text style={{ color: '#e8f5e9', fontSize: '14px', fontWeight: 'bold', display: 'block' }}>
                {submittedSummary || '已提交'}
              </Text>
              <Text style={{ color: '#81c784', fontSize: '11px', display: 'block', marginTop: '6px' }}>
                等待其他玩家完成行动…
              </Text>
            </View>
          </View>
        )
      })()}

      {/* 行动按钮 */}
      {canAct() && (
        <View className='action-section'>
          {/* ✨ 女巫专用 UI */}
          {game?.phase === 'WITCH' && game?.myRole === 'WITCH' ? (
            <View>
              {/* 药水状态 */}
              <View style={{ display: 'flex', justifyContent: 'center', gap: '16px', marginBottom: '10px' }}>
                <Text style={{ fontSize: '12px', color: witchInfo?.hasSave ? '#4caf50' : '#666' }}>
                  💊 解药{witchInfo?.hasSave ? '✓' : '✗ 已用'}
                </Text>
                <Text style={{ fontSize: '12px', color: witchInfo?.hasPoison ? '#9c27b0' : '#666' }}>
                  ☠️ 毒药{witchInfo?.hasPoison ? '✓' : '✗ 已用'}
                </Text>
              </View>

              {/* 被杀者信息 - 醒目红色 */}
              {witchInfo?.killTargetSeat ? (
                <View style={{ padding: '10px 14px', marginBottom: '12px', background: 'rgba(255,60,60,0.12)', borderRadius: '8px', border: '1.5px solid rgba(255,60,60,0.4)', textAlign: 'center' }}>
                  <Text style={{ fontSize: '16px', color: '#ff4444', fontWeight: 'bold', display: 'block' }}>
                    💀 今晚 {witchInfo.killTargetSeat}号 {witchInfo.killTargetName} 被狼人杀害
                  </Text>
                  {witchInfo?.hasSave && (
                    <Text style={{ fontSize: '12px', color: '#4caf50', display: 'block', marginTop: '4px' }}>
                      你可以使用解药救活TA
                    </Text>
                  )}
                </View>
              ) : (
                <View style={{ padding: '8px 14px', marginBottom: '12px', background: 'rgba(100,100,100,0.1)', borderRadius: '8px', textAlign: 'center' }}>
                  <Text style={{ fontSize: '13px', color: '#8a7a68' }}>
                    🌙 今晚是平安夜，无人被杀
                  </Text>
                </View>
              )}

              {/* 行动按钮组 */}
              <View style={{ display: 'flex', gap: '10px', justifyContent: 'center', flexWrap: 'wrap' }}>
                {/* 解药按钮 - 只有有人死且有解药时才显示 */}
                {witchInfo?.hasSave && witchInfo?.killTargetSeat && (
                  <Button
                    className='action-btn'
                    style={{
                      background: witchAction === 'save'
                        ? 'linear-gradient(180deg, rgba(76,175,80,1) 0%, rgba(27,94,32,1) 100%)'
                        : 'linear-gradient(180deg, rgba(76,175,80,0.25) 0%, rgba(46,125,50,0.25) 100%)',
                      border: witchAction === 'save' ? '3px solid #4caf50' : '2px solid rgba(76,175,80,0.5)',
                      color: witchAction === 'save' ? '#fff' : '#a5d6a7',
                      fontWeight: 'bold',
                      flex: '1', minWidth: '100px', maxWidth: '150px',
                      boxShadow: witchAction === 'save' ? '0 0 12px rgba(76,175,80,0.6)' : 'none',
                    }}
                    onClick={() => { setWitchAction('save'); setSelectedTarget(null) }}
                  >
                    💊 解药·救人
                  </Button>
                )}
                {/* 中间分隔 */}
                {witchInfo?.hasSave && witchInfo?.killTargetSeat && witchInfo?.hasPoison && (
                  <View style={{ width: '2px', background: 'rgba(138,122,104,0.3)', margin: '0 2px' }} />
                )}
                {/* 毒药按钮 */}
                {witchInfo?.hasPoison && (
                  <Button
                    className='action-btn'
                    style={{
                      background: witchAction === 'poison'
                        ? 'linear-gradient(180deg, rgba(211,47,47,1) 0%, rgba(106,27,154,1) 100%)'
                        : 'linear-gradient(180deg, rgba(156,39,176,0.25) 0%, rgba(106,27,154,0.25) 100%)',
                      border: witchAction === 'poison' ? '3px solid #d32f2f' : '2px solid rgba(156,39,176,0.5)',
                      color: witchAction === 'poison' ? '#fff' : '#ce93d8',
                      fontWeight: 'bold',
                      flex: '1', minWidth: '100px', maxWidth: '150px',
                      boxShadow: witchAction === 'poison' ? '0 0 12px rgba(211,47,47,0.6)' : 'none',
                    }}
                    onClick={() => { setWitchAction('poison'); setSelectedTarget(null) }}
                  >
                    ☠️ 毒药·毒人
                  </Button>
                )}
                {/* 跳过 */}
                <Button
                  className='action-btn'
                  style={{ background: 'rgba(30,26,22,0.9)', border: '1.5px solid rgba(74,61,48,0.4)', boxShadow: 'none', flex: '1', minWidth: '100px', maxWidth: '150px', color: '#8a7a68' }}
                  onClick={handleWitchSkip}
                >
                  🌙 不使用
                </Button>
              </View>

              {/* 解药确认面板 - 整体绿色强视觉 */}
              {witchAction === 'save' && (
                <View style={{
                  marginTop: '14px',
                  textAlign: 'center',
                  padding: '14px 12px',
                  background: 'linear-gradient(180deg, rgba(76,175,80,0.18) 0%, rgba(46,125,50,0.12) 100%)',
                  borderRadius: '10px',
                  border: '2.5px solid #4caf50',
                  boxShadow: '0 0 16px rgba(76,175,80,0.35)',
                }}>
                  <Text style={{ fontSize: '28px', display: 'block', marginBottom: '4px' }}>💊</Text>
                  <Text style={{ color: '#a5d6a7', fontSize: '12px', display: 'block', marginBottom: '4px' }}>
                    — 解药行动 —
                  </Text>
                  <Text style={{ color: '#e8f5e9', fontSize: '15px', fontWeight: 'bold', display: 'block' }}>
                    救活 {witchInfo?.killTargetSeat}号 {witchInfo?.killTargetName}
                  </Text>
                  <Text style={{ color: '#81c784', fontSize: '11px', display: 'block', marginTop: '4px' }}>
                    整局仅一次 · 点击下方按钮二次确认
                  </Text>
                  <Button
                    className='action-btn'
                    style={{
                      marginTop: '10px',
                      background: 'linear-gradient(180deg, #4caf50 0%, #2e7d32 100%)',
                      color: '#fff',
                      fontWeight: 'bold',
                      border: '2px solid #a5d6a7',
                      fontSize: '15px',
                    }}
                    onClick={handleAction}
                  >
                    ✅ 确认救 {witchInfo?.killTargetSeat}号
                  </Button>
                </View>
              )}

              {/* 毒药确认面板 - 整体红紫色强视觉 */}
              {witchAction === 'poison' && (
                <View style={{
                  marginTop: '14px',
                  textAlign: 'center',
                  padding: '14px 12px',
                  background: 'linear-gradient(180deg, rgba(211,47,47,0.18) 0%, rgba(106,27,154,0.14) 100%)',
                  borderRadius: '10px',
                  border: '2.5px solid #d32f2f',
                  boxShadow: '0 0 16px rgba(211,47,47,0.35)',
                }}>
                  <Text style={{ fontSize: '28px', display: 'block', marginBottom: '4px' }}>☠️</Text>
                  <Text style={{ color: '#ef9a9a', fontSize: '12px', display: 'block', marginBottom: '4px' }}>
                    — 毒药行动 · 危险 —
                  </Text>
                  {selectedTarget ? (
                    <Text style={{ color: '#ffebee', fontSize: '15px', fontWeight: 'bold', display: 'block' }}>
                      毒杀 {game?.players.find(p => p.playerId === selectedTarget)?.seatNumber}号 {game?.players.find(p => p.playerId === selectedTarget)?.username}
                    </Text>
                  ) : (
                    <Text style={{ color: '#ffcdd2', fontSize: '14px', fontWeight: 'bold', display: 'block' }}>
                      ☝️ 请在上方玩家列表中选择毒杀目标
                    </Text>
                  )}
                  <Text style={{ color: '#ef9a9a', fontSize: '11px', display: 'block', marginTop: '4px' }}>
                    整局仅一次 · 误毒好人会让狼人获胜
                  </Text>
                  <Button
                    className='action-btn'
                    style={{
                      marginTop: '10px',
                      background: selectedTarget
                        ? 'linear-gradient(180deg, #d32f2f 0%, #6a1b9a 100%)'
                        : 'rgba(90,60,90,0.5)',
                      color: '#fff',
                      fontWeight: 'bold',
                      border: '2px solid #ef9a9a',
                      fontSize: '15px',
                    }}
                    onClick={handleAction}
                    disabled={!selectedTarget}
                  >
                    {selectedTarget
                      ? `☠️ 确认毒 ${game?.players.find(p => p.playerId === selectedTarget)?.seatNumber}号`
                      : '请先选择目标'}
                  </Button>
                </View>
              )}

              {/* 未选择时的提示 */}
              {witchAction === 'none' && (
                <Text style={{ display: 'block', textAlign: 'center', marginTop: '8px', fontSize: '12px', color: '#8a7a68' }}>
                  选择使用解药、毒药或跳过本轮
                </Text>
              )}
            </View>
          ) : (
            /* 通用行动 UI */
            <View>
              {canHunterShoot && game?.myRole === 'HUNTER' && (
                <Text style={{ display: 'block', textAlign: 'center', marginBottom: '6px', color: '#ff6b6b', fontSize: '13px', fontWeight: 'bold' }}>
                  🔫 你已死亡！请选择开枪带走一个人
                </Text>
              )}
              <Text className='action-hint'>
                {selectedTarget
                  ? `⚔ 目标：${game?.players.find(p => p.playerId === selectedTarget)?.username}`
                  : '☞ 请点击上方选择目标'}
              </Text>
              <View style={{ display: 'flex', gap: '10px' }}>
                <Button
                  className='action-btn'
                  onClick={handleAction}
                  disabled={!selectedTarget}
                >
                  ◈ 确认行动 ◈
                </Button>
                {!canHunterShoot && (
                  <Button
                    className='action-btn'
                    onClick={handleSkip}
                    style={{ background: 'rgba(30,26,22,0.9)', border: '1.5px solid rgba(74,61,48,0.4)', boxShadow: 'none' }}
                  >
                    跳过
                  </Button>
                )}
              </View>
            </View>
          )}
        </View>
      )}

      {/* 聊天区域 */}
      <View className='chat-section'>
        <View className='chat-header'>
          <Text className='chat-title'>💬 消息</Text>
          {game?.phase === 'DISCUSSION' && currentSpeaker && (
            <Text className='chat-hint'>
              轮到 {currentSpeaker.seatNumber}号 {currentSpeaker.username} 发言
            </Text>
          )}
        </View>
        <ScrollView className='chat-messages' scrollY scrollWithAnimation>
          {chatMessages.length === 0 ? (
            <View style={{ textAlign: 'center', padding: '20px', color: '#8a7a68' }}>
              暗夜寂静...
            </View>
          ) : (
            chatMessages.map((msg, index) => (
              <View key={index} className={`chat-item ${msg.senderId === game?.myPlayerId ? 'me' : ''}`}>
                <Text className='chat-sender'>{msg.senderName}:</Text>
                <Text className='chat-content'>{msg.content}</Text>
              </View>
            ))
          )}
        </ScrollView>

        {/* 聊天输入框 - 讨论阶段可用 */}
        {canChat() ? (
          <View className='chat-input-row'>
            <Input
              className='chat-input'
              placeholder='发表你的看法...'
              value={chatInput}
              onInput={(e) => setChatInput(e.detail.value)}
              onConfirm={handleSendChat}
            />
            <Button className='chat-send-btn' onClick={handleSendChat}>
              发送
            </Button>
            <Button className='chat-skip-btn' onClick={handleSkipSpeech}>
              结束发言
            </Button>
          </View>
        ) : (
          <View className='chat-input-disabled'>
            <Text className='chat-disabled-text'>
              {game?.phase === 'DISCUSSION' ? `当前发言人：${currentSpeaker ? `${currentSpeaker.seatNumber}号 ${currentSpeaker.username}` : '等待切换'}` :
               game?.phase === 'VOTING' ? '投票阶段，禁止发言' :
               game?.phase === 'EXECUTION' ? '处决阶段，静默等待' :
               ['NIGHT_START', 'WEREWOLF', 'SEER', 'WITCH', 'GUARD', 'HUNTER'].includes(game?.phase || '') ? '夜晚降临，保持沉默' :
               '当前阶段无法发言'}
            </Text>
          </View>
        )}
      </View>

      {/* 角色信息弹窗 */}
      {showRoleModal && (
        <View className='modal-overlay' onClick={() => setShowRoleModal(false)}>
          <View className='modal-content' onClick={(e) => e.stopPropagation()}>
            <Text className='modal-title'>—— 你的身份 ——</Text>
            <Text style={{ display: 'block', textAlign: 'center', fontSize: '40px', marginBottom: '8px', filter: 'drop-shadow(0 4px 12px rgba(196, 26, 26, 0.4))' }}>
              {game?.myRole === 'WEREWOLF' ? '🐺' : game?.myRole === 'SEER' ? '🔮' : game?.myRole === 'WITCH' ? '🧙' : game?.myRole === 'HUNTER' ? '🔫' : game?.myRole === 'GUARD' ? '🛡️' : game?.myRole === 'IDIOT' ? '🤡' : '👤'}
            </Text>
            <Text className='modal-role'>{getRoleName(game?.myRole)}</Text>
            <Text style={{ display: 'block', textAlign: 'center', fontSize: '11px', color: game?.myRole === 'WEREWOLF' ? '#ff4444' : '#4caf50', letterSpacing: '2px', marginBottom: '4px' }}>
              {game?.myRole === 'WEREWOLF' ? '狼人阵营' : '好人阵营'}
            </Text>
            <Text style={{ display: 'block', textAlign: 'center', fontSize: '12px', color: '#8a7a68', marginBottom: '8px', fontFamily: 'monospace' }}>
              {game?.mySeat}号位
            </Text>
            {/* 狼人队友 */}
            {game?.myRole === 'WEREWOLF' && game?.teammates && game.teammates.length > 0 && (
              <View style={{ padding: '8px 12px', marginBottom: '12px', background: 'rgba(180,40,40,0.15)', borderRadius: '6px', border: '1px solid rgba(180,40,40,0.3)' }}>
                <Text style={{ display: 'block', fontSize: '12px', color: '#ff4444', textAlign: 'center', fontWeight: 'bold' }}>
                  🐺 你的狼队友
                </Text>
                <Text style={{ display: 'block', fontSize: '13px', color: '#ff6666', textAlign: 'center', marginTop: '4px' }}>
                  {game.teammates.map(tid => {
                    const p = game.players.find(pl => pl.playerId === tid)
                    return p ? `${p.seatNumber}号 ${p.username}` : `ID${tid}`
                  }).join('、')}
                </Text>
              </View>
            )}
            <Button className='modal-close' onClick={() => setShowRoleModal(false)}>
              ◈ 知道了 ◈
            </Button>
          </View>
        </View>
      )}
    </View>
  )
}
