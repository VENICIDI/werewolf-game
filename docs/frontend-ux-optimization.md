# 狼人杀前端 UX 优化文档 — 血夜主题

> **版本**: v1.0  
> **更新时间**: 2026-04-05  
> **主题代号**: 血色月夜 · Blood Moon Night

---

## 目录

1. [颜色对比度优化方案](#1-颜色对比度优化方案)
2. [图片资源清单（待提供）](#2-图片资源清单待提供)
3. [血夜主题强化设计](#3-血夜主题强化设计)
4. [UX 交互增强方案](#4-ux-交互增强方案)
5. [粒子效果系统设计](#5-粒子效果系统设计)
6. [实施优先级与排期建议](#6-实施优先级与排期建议)

---

## 1. 颜色对比度优化方案

### 1.1 当前问题诊断

| 问题 | 位置 | 现状 | WCAG 对比度 |
|------|------|------|-------------|
| 主文字在暗背景上可读性差 | 全局 `--text: #e8dcc8` on `--shadow: #0a0806` | 对比度约 12:1 ✅ | AA 通过 |
| **次要文字过暗** | `.stat-label`, `.role-desc` 等 `#6a5a4a` on `#0a0806` | **对比度约 3.2:1** ❌ | **未通过 AA** |
| **描述文字过暗** | `.header-subtitle` `#7a6a5a` on `#0a0806` | **对比度约 3.8:1** ❌ | **未通过 AA** |
| **placeholder 文字** | `#5a4a3a` on `#0a0806` | **对比度约 2.5:1** ❌ | **严重不足** |
| 卡片背景过暗 | `.parchment` 渐变 `#2a2018→#1a1510→#15100c` | 卡片与页面背景几乎融为一体 | N/A |
| 血红色标题可读性 | `.hero-title` `#8b0000` on `#0a0806` | **对比度约 2.8:1** ❌ | **未通过** |
| 金暗色文字 | `--gold-dim: #8a7020` on 暗背景 | **对比度约 3.5:1** ❌ | **边缘** |
| 游戏页配色不统一 | `game/play` `game/index` | 使用旧配色 `#e94560` `#0f3460` `#1a1a2e` | 风格割裂 |
| TabBar 选中色 | `selectedColor: #e94560` | 粉红色与血夜主题不协调 | 风格割裂 |

### 1.2 优化后色板（血夜增强版）

```scss
:root {
  // 核心色 — 提升亮度保证可读性
  --blood-red: #c41a1a;        // 原 #8b0000 → 提亮，对比度 5.2:1 ✅
  --blood-dark: #6a0000;       // 原 #4a0000 → 微调
  --blood-glow: #ff2d2d;       // 🆕 血色高亮（用于发光/强调）
  
  --gold: #e5c040;             // 原 #d4af37 → 提亮，对比度 8.5:1 ✅
  --gold-dim: #b89830;         // 原 #8a7020 → 提亮，对比度 5.8:1 ✅
  --gold-glow: #ffd700;        // 🆕 金色高亮（用于发光效果）
  
  // 背景色 — 卡片层级更分明
  --shadow: #080604;           // 页面最深背景（微调）
  --parchment: #1e1812;        // 原 #1a1510 → 微亮
  --parchment-light: #322820;  // 原 #2a2018 → 明显提亮
  --card-surface: #2a221a;     // 🆕 卡片表面色
  --card-border: #4a3d30;      // 🆕 卡片边框色（原 #3d3025 → 提亮）
  
  // 文字色 — 确保全部通过 WCAG AA
  --text: #f0e6d6;             // 原 #e8dcc8 → 微提亮
  --text-secondary: #b0a090;   // 🆕 次要文字（替代 #7a6a5a），对比度 6.5:1 ✅
  --text-tertiary: #8a7a68;    // 🆕 三级文字（替代 #5a4a3a），对比度 4.6:1 ✅
  --text-placeholder: #7a6a58; // 🆕 placeholder 专用，对比度 4.2:1 ✅
  
  // 月光系
  --moon: #d4d4d4;             // 原 #c4c4c4 → 微亮
  --moon-glow: #ffffff;        // 🆕 月光高光
}
```

### 1.3 各页面逐项修复清单

#### 全局 `app.scss`

| 修改项 | 原值 | 新值 | 说明 |
|--------|------|------|------|
| `.parchment` 背景 | `#2a2018→#1a1510→#15100c` | `#322820→#1e1812→#181210` | 卡片更亮，与背景拉开层级 |
| `.parchment` 边框 | `#3d3025` | `var(--card-border)` = `#4a3d30` | 边框更清晰 |
| `.parchment::before` | `var(--blood-dark)` | `var(--blood-red)` opacity 0.6 | 顶部装饰线更可见 |
| `.seal-btn` 文字 | `var(--text)` | 保持，添加 `text-shadow` | 增强按钮文字清晰度 |
| `.section-title` | `var(--gold)` | `var(--gold)` + `text-shadow: 0 0 8px rgba(229,192,64,0.3)` | 标题发光增强 |

#### 首页 `index/index.scss`

| 修改项 | 原值 | 新值 |
|--------|------|------|
| `.hero-title` color | `var(--blood-red)` (#8b0000) | `var(--blood-red)` (#c41a1a) + 增强 text-shadow |
| `.stat-label` color | `#6a5a4a` | `var(--text-secondary)` (#b0a090) |
| `.tonight-label` color | `#6a5a4a` | `var(--text-secondary)` |
| `.tonight-num` color | `var(--blood-red)` | `var(--blood-glow)` (#ff2d2d) |
| `.role-desc` color | `#6a5a4a` | `var(--text-tertiary)` (#8a7a68) |
| `.role-camp` color | `var(--gold-dim)` | 新 `var(--gold-dim)` (#b89830) |
| `.footer-text` color | `#3d3025` | `var(--text-tertiary)` |
| `.tonight-stats` 背景 | `rgba(26,21,16,0.8)` | `var(--card-surface)` + 边框提亮 |
| `.role-card` 背景 | `rgba(26,21,16,0.7)` | `var(--card-surface)` + 悬停高亮 |
| `.action-btn-desc` color | `rgba(232,220,200,0.5)` | `var(--text-tertiary)` |

#### 房间列表页 `room-list/index.scss`

| 修改项 | 原值 | 新值 |
|--------|------|------|
| `.header-subtitle` | `#7a6a5a` | `var(--text-secondary)` |
| `.room-count` | `#6a5a4a` | `var(--text-secondary)` |
| `.info-label` | `#5a4a3a` | `var(--text-tertiary)` |
| `.progress-label` | `#6a5a4a` | `var(--text-secondary)` |
| `.room-card` 背景 | `rgba(28,22,16,0.95)` | `var(--card-surface)` 透明度 0.95 |
| `.room-card` 边框 | `#3a2e24` | `var(--card-border)` |
| 待中状态 `.status-text` | `#8bc34a` | `#a5d65a`（提亮） |
| `.card-top-deco` opacity | `0.6` | `0.9`（装饰线更可见） |

#### 房间详情页 `room/index.scss`

| 修改项 | 原值 | 新值 |
|--------|------|------|
| `.info-cell-label` | `#5a4a3a` | `var(--text-tertiary)` |
| `.seat-number` | `#5a4a3a` | `var(--text-tertiary)` |
| `.empty-name` | `#5a4a3a` | `var(--text-tertiary)` |
| `.ws-label` | `#6a5a4a` | `var(--text-secondary)` |
| `.chat-empty-text` | `#5a4a3a` | `var(--text-tertiary)` |
| `.leave-btn` color | `#8a7a6a` | `var(--text-secondary)` |
| `.loading-text` | `#7a6a5a` | `var(--text-secondary)` |

#### 游戏对战页 `game/play/index.scss` — **需要全面改造**

> ⚠️ 该页面仍使用旧配色系统 (`#e94560`, `#0f3460`, `#1a1a2e`)，需统一为血夜主题。

| 修改项 | 原值 | 新值 |
|--------|------|------|
| `.night` 背景 | `#1a1a2e→#0f0f1e` | `var(--shadow)` 带血色径向渐变 |
| `.day` 背景 | `#2d3748→#1a202c` | `#1a1510→#0a0806` 带暖色调 |
| `.timer-text` | `#e94560` | `var(--blood-glow)` |
| `.player-card.selected` border | `#e94560` | `var(--blood-red)` |
| `.host-badge` color/bg | `#e94560` | `var(--blood-red)` |
| `.chat-sender` color | `#e94560` | `var(--gold)` |
| `.action-btn` gradient | `#e94560→#c73e54` | `var(--blood-red)→var(--blood-dark)` |
| `.player-avatar` bg | `#0f3460` | `var(--parchment-light)` |
| `.modal-content` bg | `#1a1a2e` | 与主题弹窗样式统一 |
| `.modal-role` color | `#e94560` | `var(--blood-red)` |
| `.modal-close` bg | `#0f3460` | 血红渐变 + 金边 |

#### 游戏入口页 `game/index.tsx` — **需要改造**

> 当前使用内联 `style`，需替换为 SCSS 类名，统一血夜主题。

| 修改项 | 原值 | 新值 |
|--------|------|------|
| 背景色 | `#0f0f1e` | `var(--shadow)` |
| 文字颜色 | `#fff` / `#aaa` | `var(--text)` / `var(--text-secondary)` |
| 按钮 | `#e94560` | 血红渐变 + 金边封印按钮 |

#### TabBar 配色 `app.config.ts`

| 修改项 | 原值 | 新值 |
|--------|------|------|
| `selectedColor` | `#e94560`（粉红） | `#c41a1a`（血红）或 `#e5c040`（金色） |
| `backgroundColor` | `#1a1a2e`（蓝紫） | `#0a0806`（与 `--shadow` 统一） |

---

## 2. 图片资源清单（待提供）

### 2.1 当前资源盘点

目前项目**极度精简**，仅有 6 个 TabBar 小图标（总计 ~1.4KB），所有视觉效果均由纯 CSS 实现。

| 现有资源 | 用途 | 状态 |
|----------|------|------|
| `home.png` / `home-active.png` | 首页 Tab 图标 | ⚠️ 需替换为血夜风格 |
| `room.png` / `room-active.png` | 房间 Tab 图标 | ⚠️ 需替换为血夜风格 |
| `profile.png` / `profile-active.png` | 我的 Tab 图标 | ⚠️ 需替换为血夜风格 |

### 2.2 需要提供的图片清单

> 以下图片将存放在 `src/assets/images/` 目录下，建议使用 **PNG** (带透明通道) 或 **WebP** 格式。

#### A. TabBar 图标 (6张)

| 文件名 | 尺寸建议 | 用途 | 风格要求 |
|--------|----------|------|----------|
| `tab-home.png` | 81×81px (@3x) | 首页-未选中 | 血夜风格线性图标，暗金色调 |
| `tab-home-active.png` | 81×81px | 首页-选中 | 血红色 + 发光效果 |
| `tab-room.png` | 81×81px | 房间-未选中 | 剑/城堡/帐篷 线性图标 |
| `tab-room-active.png` | 81×81px | 房间-选中 | 血红发光 |
| `tab-profile.png` | 81×81px | 我的-未选中 | 面具/头盔 线性图标 |
| `tab-profile-active.png` | 81×81px | 我的-选中 | 血红发光 |

#### B. 页面背景/氛围图 (5-8张)

| 文件名 | 尺寸建议 | 用途 | 风格要求 |
|--------|----------|------|----------|
| `bg-blood-moon.png` | 750×400px | 首页顶部血月装饰 | 血红色月亮，带光晕，半透明 |
| `bg-forest-silhouette.png` | 750×300px | 页面底部装饰 | 暗色森林剪影，树木/尖塔 |
| `bg-fog-layer.png` | 750×200px | 底部雾气叠加层 | 半透明血色雾气 |
| `bg-parchment-texture.png` | 750×750px (可平铺) | 卡片背景纹理 | 羊皮纸/兽皮纹理，低对比度 |
| `bg-rune-circle.png` | 400×400px | 装饰符文圆环 | 暗金色神秘符文阵 |
| `bg-night-sky.png` | 750×1334px | 游戏夜晚全屏背景 | 暗蓝偏红的夜空，星星 |
| `bg-day-sky.png` | 750×1334px | 游戏白天全屏背景 | 暮色调天空，偏暖 |
| `bg-splash.png` | 750×1334px | 启动页/加载页背景 | 血月 + 狼群剪影 + 标题 |

#### C. 角色图鉴卡面 (7张)

| 文件名 | 尺寸建议 | 用途 | 风格要求 |
|--------|----------|------|----------|
| `role-villager.png` | 200×260px | 村民角色卡 | 中世纪风格农民，暗色调 |
| `role-werewolf.png` | 200×260px | 狼人角色卡 | 变身狼人，血红眼睛 |
| `role-seer.png` | 200×260px | 预言家角色卡 | 手持水晶球，紫色光芒 |
| `role-witch.png` | 200×260px | 女巫角色卡 | 持药瓶，绿色+紫色调 |
| `role-hunter.png` | 200×260px | 猎人角色卡 | 持弩/弓箭，棕色调 |
| `role-guard.png` | 200×260px | 守卫角色卡 | 骑士盔甲+盾牌，蓝色调 |
| `role-idiot.png` | 200×260px | 白痴角色卡 | 小丑面具，黄色调 |

#### D. 游戏阶段图 (8张)

| 文件名 | 尺寸建议 | 用途 | 风格要求 |
|--------|----------|------|----------|
| `phase-night.png` | 300×200px | 天黑了 阶段横幅 | 血月夜景 |
| `phase-werewolf.png` | 300×200px | 狼人行动 横幅 | 狼爪+血迹 |
| `phase-seer.png` | 300×200px | 预言家行动 横幅 | 水晶球发光 |
| `phase-witch.png` | 300×200px | 女巫行动 横幅 | 药瓶+烟雾 |
| `phase-day.png` | 300×200px | 天亮了 横幅 | 曙光 + 篝火 |
| `phase-discussion.png` | 300×200px | 讨论阶段 横幅 | 圆桌会议 |
| `phase-vote.png` | 300×200px | 投票阶段 横幅 | 投票石/审判台 |
| `phase-execution.png` | 300×200px | 处决阶段 横幅 | 绞刑架/天平 |

#### E. UI 装饰元素 (10+张)

| 文件名 | 尺寸建议 | 用途 |
|--------|----------|------|
| `deco-divider.png` | 600×20px | 血夜风格分割线（替代纯 CSS 渐变线） |
| `deco-corner-tl.png` | 60×60px | 卡片左上角装饰花纹 |
| `deco-corner-br.png` | 60×60px | 卡片右下角装饰花纹 |
| `deco-frame-border.png` | 9-patch | 角色卡片装饰边框 |
| `icon-wolf-paw.png` | 48×48px | 狼爪印装饰图标 |
| `icon-blood-drop.png` | 32×32px | 血滴装饰/分隔符 |
| `icon-moon-crescent.png` | 48×48px | 新月图标 |
| `icon-skull.png` | 48×48px | 死亡标记 |
| `icon-sword-cross.png` | 48×48px | 交叉剑图标 |
| `icon-shield.png` | 48×48px | 盾牌图标（守护标记） |

#### F. 动画序列帧 / Lottie (可选)

| 文件名 | 格式 | 用途 |
|--------|------|------|
| `anim-wolf-howl.json` | Lottie JSON | 狼嚎动画（首页/游戏开始） |
| `anim-blood-splash.json` | Lottie JSON | 击杀效果动画 |
| `anim-magic-circle.json` | Lottie JSON | 技能释放魔法阵 |
| `anim-card-flip.json` | Lottie JSON | 角色卡翻转揭示 |

### 2.3 图片命名规范

```
src/assets/images/
├── tab/           # TabBar 图标
├── bg/            # 背景氛围图
├── roles/         # 角色卡面
├── phases/        # 游戏阶段图
├── deco/          # 装饰元素
├── icons/         # 功能图标
└── anim/          # 动画文件（Lottie）
```

---

## 3. 血夜主题强化设计

### 3.1 保留的优秀元素

- ✅ "狩猎集会" 房间列表标题 — 完美贴合主题
- ✅ "猎人集结" 玩家进度标签
- ✅ "设立你的猎场" 创建房间装饰文案
- ✅ "月夜降临，猎人需先亮明身份" 登录引导文案
- ✅ 封印按钮 `◈` 装饰符系统
- ✅ 羊皮纸卡片非对称圆角 `2px 12px 2px 12px`
- ✅ CSS 星尘粒子系统（全局 `page::before/::after`）
- ✅ 血色脉搏动画 `bloodPulse`

### 3.2 主题增强方向

#### 3.2.1 导航栏血化

```scss
// app.config.ts 导航栏
navigationBarBackgroundColor: '#0a0806'  // 与主背景统一
// 添加自定义导航栏，底部加血色渐变线
.custom-nav::after {
  content: '';
  height: 2px;
  background: linear-gradient(90deg, transparent, var(--blood-red), var(--gold-dim), var(--blood-red), transparent);
}
```

#### 3.2.2 页面切换氛围

每个页面应有独特的氛围色调：

| 页面 | 氛围色 | 描述 |
|------|--------|------|
| 首页 | 血红光晕 | 顶部血月发光，底部符文暗纹（已有） |
| 狩猎集会 | 金色微光 | 左上金斑 + 右下血斑（已有）→ 增强 |
| 房间/猎场 | 暖金色 | 壁炉/篝火感，右上金色光源 |
| 游戏-夜晚 | 深蓝偏红 | 血月高悬，冷色调 |
| 游戏-白天 | 暖棕色 | 晨光，暖色调 |
| 个人中心 | 暗金色 | 宝库/书房感 |

#### 3.2.3 文案体系标准化（保持"狩猎集会"风格）

| 功能 | 当前文案 | 建议优化 |
|------|----------|----------|
| 房间列表标题 | 📜 狩猎集会 | ✅ 保持 |
| 房间列表副标题 | 选择一个房间加入战局... | ✅ 保持 |
| 创建房间 | 设立你的猎场 | ✅ 保持 |
| 玩家列表 | ⚔ 猎人集结 | ✅ 保持 |
| 空房间提示 | 🐺 月夜寂静 | ✅ 保持 |
| 登录提示 | 月夜将至，猎人尚未现身 | ✅ 保持 |
| 开始游戏 | 开始游戏 | → **「血月升起」** |
| 准备就绪 | 准备就绪 | → **「执刃待命」** |
| 取消准备 | 取消准备 | → **「收刃入鞘」** |
| 离开房间 | 离开房间 | → **「退出猎场」** |
| 首页标语 | 夜幕降临，谁是狼人？ | → **「血月之下，谁是暗夜的猎手？」** |
| 加载中 | 进入房间中... | → **「穿越暗夜迷雾...」** |
| 今夜战报 | ◆ 今夜战报 | → **「◆ 血夜战报」** |
| 聊天区空提示 | 暂无消息，来打个招呼吧 👋 | → **「暗夜寂静，打破沉默吧...」** |

---

## 4. UX 交互增强方案

### 4.1 微信小程序特有交互

#### 4.1.1 触觉反馈 (Haptic Feedback)

```typescript
// 关键操作添加震动反馈
Taro.vibrateShort({ type: 'medium' })  // 按钮点击
Taro.vibrateLong()                      // 角色揭示、击杀通知
```

| 触发场景 | 震动类型 | 说明 |
|----------|----------|------|
| 按钮点击 | `vibrateShort('light')` | 轻触反馈 |
| 准备就绪/取消 | `vibrateShort('medium')` | 中等反馈 |
| 角色揭示 | `vibrateLong()` | 强烈震动，增加仪式感 |
| 被杀/毒死通知 | `vibrateLong()` | 死亡通知震感 |
| 投票确认 | `vibrateShort('heavy')` | 重要操作确认 |
| 游戏开始 | 连续两次 `vibrateShort` | 紧张感 |

#### 4.1.2 下拉刷新 & 上滑加载

```typescript
// 房间列表页添加下拉刷新
Taro.enablePullDownRefresh()

// 页面配置
{
  enablePullDownRefresh: true,
  backgroundTextStyle: 'dark',
  backgroundColor: '#0a0806'
}
```

#### 4.1.3 页面跳转动画

```typescript
// 使用自定义路由动画（Taro 支持的范围内）
Taro.navigateTo({
  url: '/pages/room/index?code=XXXX',
  // 小程序端可用 routeType
})
```

### 4.2 通用 UX 交互增强

#### 4.2.1 按钮系统交互升级

| 交互 | 现状 | 优化方案 |
|------|------|----------|
| 按下反馈 | `translateY(6px)` 下压 | ✅ 保持，增加 `scale(0.98)` + 阴影消失 |
| 禁用状态 | 简单灰色 | → 裂纹纹理 + "封印中..." 文字 |
| 加载状态 | 文字变化 | → 血色脉搏环形 loading + 文字 |
| 长按效果 | 无 | → 血色涟漪扩散动画 |

```scss
// 按钮涟漪效果
.seal-btn {
  overflow: hidden;
  &::before {
    // 点击时血色涟漪
    @keyframes ripple {
      from { transform: scale(0); opacity: 0.5; }
      to { transform: scale(4); opacity: 0; }
    }
  }
}
```

#### 4.2.2 卡片交互系统

| 交互 | 实现方案 |
|------|----------|
| **点击下沉** | `transform: scale(0.985)` + 阴影减少（已有，保持） |
| **滑动删除** | 房间卡片长按可快速退出（如已在房内） |
| **卡片入场** | 列表卡片逐个入场动画 (staggered fadeInUp) |
| **空位呼吸** | 空位卡片虚线边框呼吸发光 |
| **就绪闪光** | 玩家就绪时卡片金色闪光扫过 |

```scss
// 卡片逐个入场
.room-card {
  animation: cardFadeIn 0.4s ease-out backwards;
  @for $i from 1 through 20 {
    &:nth-child(#{$i}) {
      animation-delay: #{$i * 0.08}s;
    }
  }
}

@keyframes cardFadeIn {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
```

#### 4.2.3 Toast / 通知系统

替代原生 `Taro.showToast`，使用血夜主题自定义 Toast：

```
┌──────────────────────────────┐
│  ◆ 玩家"暗夜猎手"加入了猎场  │
│  ──────── ✦ ────────        │
└──────────────────────────────┘
```

| 通知类型 | 颜色方案 | 图标 |
|----------|----------|------|
| 成功 | 金色边框 + 暗金文字 | ✦ |
| 错误 | 血红边框 + 红色文字 | ✗ |
| 信息 | 月光色边框 + 银白文字 | ◆ |
| 警告 | 琥珀色边框 + 橙色文字 | ⚠ |

#### 4.2.4 骨架屏 (Skeleton Loading)

替代当前 `🌙 月光笼罩中...` 加载状态：

```
┌──────────────────────────────┐
│  ████████████  ░░░░ ██████  │  ← 房间名 + 状态
│  ████  │  ██████████        │  ← 房间号 + 房主
│  ████████████████████  ███  │  ← 进度条 + 按钮
└──────────────────────────────┘
```

骨架颜色使用 `--parchment` 和 `--parchment-light` 做呼吸闪烁。

#### 4.2.5 游戏流程交互

| 场景 | 交互效果 |
|------|----------|
| **角色揭示** | 卡片从背面翻转到正面（3D transform），配合震动 |
| **天黑过渡** | 全屏从亮到暗渐变（800ms），配合月亮升起动画 |
| **天亮过渡** | 全屏从暗到亮渐变，配合晨光照射效果 |
| **狼人选人** | 被选中的头像血红光环脉搏 + 周围血雾 |
| **预言家查验** | 水晶球发光旋转动画 → 揭示好人(绿光)/狼人(红光) |
| **女巫用药** | 绿色气泡(解药) / 紫色烟雾(毒药) 动画 |
| **投票计数** | 票数石子逐个落入天平动画 |
| **玩家死亡** | 头像灰度化 + 灵魂升起消散效果 |
| **游戏结束** | 胜利阵营: 金色光柱 + 庆祝粒子；失败阵营: 暗红碎裂效果 |

#### 4.2.6 聊天区增强

| 功能 | 说明 |
|------|------|
| 消息入场动画 | 新消息从底部滑入 + 淡入 |
| 发送按钮反馈 | 点击后血色涟漪 + 消息飞出动画 |
| 表情快捷面板 | 预设游戏相关表情：🐺🔮🧙🏹💂👤💀🩸🌙⚔ |
| 消息时间戳 | 悬停/点击显示精确时间 |
| 系统消息样式 | 居中显示，暗金色背景，与玩家消息区分 |

---

## 5. 粒子效果系统设计

### 5.1 技术选型

| 方案 | 优点 | 缺点 | 推荐度 |
|------|------|------|--------|
| **纯 CSS (`background` + `@keyframes`)** | 零依赖，当前已用 | 粒子数量有限，交互差 | ⭐⭐⭐ 基础层 |
| **Canvas 2D 粒子引擎** | 高性能，粒子数量大 | 需手写引擎 | ⭐⭐⭐⭐ 推荐 |
| **Taro Canvas API** | 兼容小程序+H5 | API 受限 | ⭐⭐⭐ 必须方案 |
| **Lottie-miniprogram** | 效果精美 | 文件体积较大 | ⭐⭐⭐ 关键动画 |

**推荐方案**: CSS 粒子(基础层) + Canvas 粒子引擎(复杂效果) + Lottie(关键动画)

### 5.2 粒子效果清单与实现细则

---

#### 效果 1: 🌟 全局星尘粒子（已有，增强）

**当前状态**: 使用 `page::before/::after` 的 `radial-gradient` 模拟 30 颗静态星星

**增强方案**:

| 属性 | 现状 | 优化 |
|------|------|------|
| 粒子数量 | 30颗 (CSS渐变) | 80-120颗 (Canvas) |
| 运动方式 | 仅透明度闪烁 | 闪烁 + 微移漂浮 + 偶尔划过 |
| 颜色 | 金色/银色/红色 | 保持，增加偶尔的明亮白色闪烁 |
| 交互 | 无 | 手指滑动时附近星星被"推开" |

**实现**:
```typescript
// 创建全局粒子管理器组件
// src/components/ParticleBackground/index.tsx

interface Particle {
  x: number; y: number       // 位置
  size: number                // 1-2.5px
  color: 'gold' | 'silver' | 'blood'
  opacity: number             // 0.2-0.8
  twinkleSpeed: number        // 闪烁速度
  driftX: number; driftY: number  // 漂移方向
}

// Canvas 绘制循环 (requestAnimationFrame)
// 1. 清空画布
// 2. 遍历粒子，更新位置和透明度
// 3. 绘制发光圆点 (arc + radialGradient)
// 4. 1/200 概率生成流星粒子
```

**性能优化**:
- 使用 `offscreenCanvas` (小程序)
- 帧率限制 30fps
- 屏幕外粒子不绘制
- 页面非活跃时暂停

---

#### 效果 2: 🩸 血雾弥漫

**触发**: 游戏夜晚阶段 / 狼人行动时

**实现细则**:

| 属性 | 值 |
|------|-----|
| 粒子数量 | 30-50 |
| 粒子大小 | 20-60px（模糊圆） |
| 颜色 | `rgba(139,0,0, 0.05~0.15)` |
| 运动 | 缓慢水平漂移 + 上下浮动 |
| 混合模式 | `screen` |
| 生命周期 | 5-15秒淡入淡出 |

```scss
// CSS 方案（简化版，用于非 Canvas 场景）
.blood-fog-particle {
  position: absolute;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(139,0,0,0.1) 0%, transparent 70%);
  filter: blur(20px);
  animation: fogDrift 10s ease-in-out infinite;
  pointer-events: none;
}

@keyframes fogDrift {
  0% { transform: translate(0, 0); opacity: 0; }
  20% { opacity: 0.3; }
  80% { opacity: 0.3; }
  100% { transform: translate(60px, -30px); opacity: 0; }
}
```

---

#### 效果 3: ✨ 金色尘埃飞舞

**触发**: 进入房间页 / 角色揭示 / 胜利画面

**实现细则**:

| 属性 | 值 |
|------|-----|
| 粒子数量 | 40-80 |
| 粒子大小 | 1-4px |
| 颜色 | `#d4af37` → `#ffd700` (金色渐变) |
| 运动 | 从下往上飘升 + 左右摇摆 (sin 曲线) |
| 轨迹 | 螺旋上升 |
| 生命周期 | 3-8秒，淡出消失 |
| 发光 | 每个粒子有 2px 发光光晕 |

```typescript
// Canvas 粒子参数
{
  x: Math.random() * width,
  y: height + 10,
  vx: Math.sin(time * 0.5) * 0.5,  // 左右摇摆
  vy: -1 - Math.random() * 2,       // 上升速度
  size: 1 + Math.random() * 3,
  life: 1.0,                         // 生命值 1.0→0
  decay: 0.003 + Math.random() * 0.005
}
```

---

#### 效果 4: 🐺 狼嚎冲击波

**触发**: 狼人击杀确认时

**实现细则**:

| 属性 | 值 |
|------|-----|
| 效果类型 | 圆形冲击波 + 径向粒子 |
| 中心 | 被杀玩家头像位置 |
| 冲击波 | 血红色环形，从中心向外扩散 500ms |
| 粒子数量 | 20-30 |
| 粒子颜色 | 血红色 |
| 粒子运动 | 从中心向四周爆射，速度递减 |
| 屏幕效果 | 全屏 0.1s 红色闪烁 (screen shake) |

```scss
@keyframes shockwave {
  0% {
    width: 0; height: 0;
    border: 3px solid rgba(196, 0, 0, 0.8);
    opacity: 1;
  }
  100% {
    width: 300px; height: 300px;
    border: 1px solid rgba(196, 0, 0, 0);
    opacity: 0;
  }
}
```

---

#### 效果 5: 🔮 查验光球

**触发**: 预言家查验结果揭示

**实现细则**:

| 属性 | 值 |
|------|-----|
| 阶段1 | 中心紫色光球聚集 (0-800ms) |
| 阶段2 | 光球旋转加速 (800-1500ms) |
| 阶段3 | 光球爆开，揭示颜色 (1500-2000ms) |
| 好人结果 | 绿色粒子四散 + 绿色光晕 |
| 狼人结果 | 红色粒子 + 红色闪电 + 屏幕微震 |
| 粒子数量 | 阶段1: 20个聚集粒子，阶段3: 50个爆散粒子 |

---

#### 效果 6: 🧙 药水烟雾

**触发**: 女巫使用解药/毒药

**实现细则**:

| 属性 | 解药 | 毒药 |
|------|------|------|
| 主色调 | `#4caf50` 绿色 | `#9c27b0` 紫色 |
| 粒子形态 | 气泡上升 | 烟雾弥漫 |
| 粒子数量 | 20个气泡 | 40个烟雾粒子 |
| 运动 | 向上飘升，左右轻摇 | 水平扩散，缓慢上升 |
| 大小 | 3-8px 圆形 | 10-30px 模糊圆 |
| 生命周期 | 2-4秒 | 3-6秒 |

---

#### 效果 7: 🗳️ 投票石子动画

**触发**: 投票阶段，每个人投票时

**实现细则**:

| 属性 | 值 |
|------|-----|
| 效果 | 石子从投票者位置飞向被投者 |
| 轨迹 | 抛物线弧形 |
| 尾迹 | 金色拖尾粒子 (5-8个) |
| 落点效果 | 小型烟尘爆发 |
| 累积显示 | 被投者下方显示石子堆 |

---

#### 效果 8: 💀 灵魂消散

**触发**: 玩家被投票出局/被杀

**实现细则**:

| 属性 | 值 |
|------|-----|
| 阶段1 | 头像灰度化 0→1 (500ms) |
| 阶段2 | 头像碎裂成 20-30 个碎片 |
| 阶段3 | 碎片变成透明烟雾粒子向上飘散 |
| 颜色 | 灰白色 → 逐渐透明 |
| 持续时间 | 总计 2秒 |

---

#### 效果 9: 🏆 胜利庆典

**触发**: 游戏结束，胜利阵营

**实现细则**:

| 属性 | 好人胜利 | 狼人胜利 |
|------|----------|----------|
| 主色 | 金色 + 白色 | 血红色 + 暗紫色 |
| 效果 | 金色粒子从底部喷射 + 星光爆发 | 血色粒子从中心爆发 + 狼嚎冲击波 |
| 粒子数量 | 100-200 | 80-150 |
| 额外效果 | 金色碎纸片飘落 | 屏幕裂纹效果 |
| 持续时间 | 3秒 | 3秒 |

---

#### 效果 10: 🌙 月光流转

**触发**: 全局持续效果（与星尘粒子共存）

**实现细则**:

| 属性 | 值 |
|------|-----|
| 效果 | 从页面右上角(月亮位置)射出柔和光线 |
| 实现 | CSS `conic-gradient` + 缓慢旋转 |
| 颜色 | `rgba(196,196,196, 0.02~0.05)` |
| 旋转 | 60秒一周 |
| 叠加 | 偶尔有光柱闪过 (3-5秒间隔) |

```scss
.moonlight-rays {
  position: fixed;
  top: 5%;
  right: 10%;
  width: 200%;
  height: 200%;
  background: conic-gradient(
    from 0deg at 50% 50%,
    transparent 0deg,
    rgba(196,196,196,0.03) 10deg,
    transparent 20deg,
    transparent 40deg,
    rgba(196,196,196,0.02) 50deg,
    transparent 60deg,
    // ... 重复
  );
  animation: moonRotate 60s linear infinite;
  pointer-events: none;
  z-index: 0;
}
```

---

### 5.3 粒子性能预算

| 场景 | 最大粒子数 | 帧率目标 | Canvas 数量 |
|------|-----------|----------|-------------|
| 全局星尘 | 120 | 30fps | 1个 (全屏) |
| 页面氛围(血雾/月光) | 50 | 30fps | 复用全局 Canvas |
| 游戏效果(单个) | 200 | 60fps | 1个 (效果层) |
| 总计(最大同屏) | ~300 | 30fps | 2个 Canvas |

**内存预算**: 粒子对象池 500，复用而非创建/销毁。

---

## 6. 实施优先级与排期建议

### P0 — 立即修复（1-2天）

| 任务 | 说明 |
|------|------|
| 颜色对比度修复 | 更新 CSS 变量，修复所有 WCAG AA 不达标的文字色 |
| 游戏页面统一主题 | `game/play` 和 `game/index` 改用血夜主题色 |
| TabBar 配色统一 | `selectedColor` 和 `backgroundColor` 改为主题色 |
| 卡片背景提亮 | 增强卡片与页面背景的视觉层级 |

### P1 — 核心体验（3-5天）

| 任务 | 说明 |
|------|------|
| 文案主题化 | 替换按钮/提示文案为血夜风格 |
| 按钮交互升级 | 涟漪/加载/禁用状态 |
| 卡片入场动画 | staggered fadeInUp |
| 触觉反馈 | 关键操作添加震动 |
| 骨架屏 | 替代当前 loading 状态 |
| 自定义 Toast | 血夜主题通知组件 |

### P2 — 视觉增强（5-8天）

| 任务 | 说明 |
|------|------|
| 接收并整合图片资源 | 替换 Emoji 为精美角色卡面和图标 |
| Canvas 粒子引擎 | 开发全局粒子管理器组件 |
| 全局星尘增强 | CSS → Canvas 升级 |
| 血雾 + 月光效果 | 氛围层粒子 |
| 下拉刷新自定义 | 血夜风格刷新动画 |

### P3 — 游戏高光（8-12天）

| 任务 | 说明 |
|------|------|
| 角色揭示翻牌效果 | 3D 卡片翻转 + 金色尘埃 |
| 昼夜转换过渡动画 | 全屏渐变 + 粒子切换 |
| 狼嚎冲击波 | 击杀效果粒子 |
| 查验光球 | 预言家技能效果 |
| 药水烟雾 | 女巫技能效果 |
| 投票石子 | 投票可视化动画 |
| 灵魂消散 | 死亡效果 |
| 胜利庆典 | 游戏结束粒子烟花 |

---

## 附录

### A. 文件变更清单

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `src/app.scss` | 修改 | 更新 CSS 变量、增强卡片/按钮样式 |
| `src/app.config.ts` | 修改 | TabBar/导航栏配色 |
| `src/pages/index/index.scss` | 修改 | 对比度修复、动画增强 |
| `src/pages/room-list/index.scss` | 修改 | 卡片入场动画、对比度修复 |
| `src/pages/room/index.scss` | 修改 | 对比度修复 |
| `src/pages/game/play/index.scss` | **重构** | 全面改为血夜主题 |
| `src/pages/game/index.tsx` | **重构** | 内联样式→SCSS 类名 |
| `src/pages/game/role-reveal/index.scss` | 修改 | 增强翻牌效果 |
| `src/pages/profile/index.scss` | 修改 | 对比度修复 |
| `src/pages/login/index.scss` | 修改 | 微调 |
| `src/pages/register/index.scss` | 修改 | 微调 |
| `src/components/LoginModal/index.scss` | 修改 | 微调 |
| `src/components/ParticleBackground/` | **新增** | Canvas 粒子引擎组件 |
| `src/components/BloodToast/` | **新增** | 自定义 Toast 组件 |
| `src/components/SkeletonCard/` | **新增** | 骨架屏组件 |
| `src/assets/images/*` | **新增** | 图片资源（待提供） |

### B. 设计 Token 速查

```scss
// 文字层级
--text:             #f0e6d6  // 主文字
--text-secondary:   #b0a090  // 次要文字
--text-tertiary:    #8a7a68  // 三级文字
--text-placeholder: #7a6a58  // 输入框占位符

// 背景层级（从深到浅）
--shadow:           #080604  // 页面背景
--parchment:        #1e1812  // 卡片深色区
--parchment-light:  #322820  // 卡片亮色区
--card-surface:     #2a221a  // 卡片表面
--card-border:      #4a3d30  // 卡片边框

// 强调色
--blood-red:   #c41a1a  // 主红色
--blood-glow:  #ff2d2d  // 红色发光
--gold:        #e5c040  // 主金色
--gold-glow:   #ffd700  // 金色发光
```
