# 🐺 狼人杀游戏流程问题清单

> 审查时间: 2026-04-13  
> 审查范围: 后端游戏引擎 + AI 桥接 + 前端游戏界面
> 最后更新: 2026-04-13 00:52

---

## P0 — 严重（影响核心游戏逻辑）

### 1. ✅ 夜晚结算后不检查胜负
- **修复**: `PhaseScheduler` 夜晚结算后增加 `checkWinCondition()` 调用

### 2. ✅ 前端女巫无法救人
- **修复**: 前端女巫阶段重构为三选一 UI（💊救人 / ☠️毒人 / 跳过）

### 3. ✅ 前端女巫看不到被杀者
- **修复**: 后端新增 `notifyWitchKillTarget()` 在女巫阶段发送 `WITCH_INFO` 消息，前端显示被杀者信息

### 4. ✅ 前端没有猎人开枪界面
- **修复**: 前端监听 `HUNTER_SHOOT` 消息，死亡猎人可选目标开枪

### 5. ✅ AI 猎人死亡不会自动开枪
- **修复**: `notifyHunterCanShoot()` 中 AI 猎人自动随机选目标开枪

### 6. ✅ 投票结算可能双重触发
- **修复**: `votingResolved` 幂等锁，`resolveVoting` 和 PhaseScheduler 超时互斥

### 7. ✅ 猎人可以多次开枪
- **修复**: `hunterShot` 集合记录已开枪猎人，`executeAction` 中 shoot 前检查

---

## P1 — 高（规则不完整）

### 8. ✅ 女巫同一晚可以同时用药
- **修复**: `WitchSaveHandler` 和 `WitchPoisonHandler` 互相检查，同一晚只能用一瓶

### 9. witchCanSaveSelf 配置未生效
- **描述**: 配置了 `witchCanSaveSelf: false` 但 `WitchSaveHandler` 没有检查
- **状态**: 待修复

### 10. guardCanProtectSelf 配置未生效
- **描述**: 配置了 `guardCanProtectSelf: false` 但代码没有检查
- **状态**: 待修复

### 11. firstNightNoKill 配置未生效
- **描述**: 6人局配置了 `firstNightNoKill: true` 但没有实现
- **状态**: 待修复

### 12. ✅ AI 女巫不知道被杀者
- **修复**: `notifyWitchKillTarget()` 中 AI 女巫自动决策（50%概率救人，30%概率毒人）

### 13. ✅ 预言家查验结果弹两次
- **修复**: 后端 check 行动不再发 ACTION_CONFIRM，前端只监听 SEER_RESULT

### 14. 遗言功能未实现
- **状态**: 待修复

---

## P2 — 中（体验/架构问题）

### 15. game-config.json 完全不被使用 — 待清理
### 16. 12人局守卫行动顺序不符合标准 — 待修复
### 17. 玩家掉线无处理 — 待实现
### 18. guardWitchConflict 硬编码 — 待优化
### 19. tieVote 配置未读取 — 待优化
### 20. 游戏暂停/恢复未实现 — 待实现
### 21. ✅ AI 投票失败无降级
- **修复**: AI 投票/夜间行动/发言全部增加 try-catch 降级（随机投票/skip/默认发言）
### 22. SkipHandler 不标记已提交 — 待修复
### 23. 讨论时间过长 — 待优化

---

## 修复总结

**已修复**: #1 #2 #3 #4 #5 #6 #7 #8 #12 #13 #21 (共 11 个)
**待修复**: #9 #10 #11 #14 ~ #23 (共 12 个)
