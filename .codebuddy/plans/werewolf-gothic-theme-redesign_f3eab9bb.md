---
name: werewolf-gothic-theme-redesign
overview: 将狼人杀前端从简约科技风格改造为哥特式"血色月夜"羊皮纸风格,包含全局主题、所有页面组件的视觉升级,实现与预览HTML一致的沉浸式游戏体验。
design:
  architecture:
    framework: react
  styleKeywords:
    - 哥特式
    - 暗黑风格
    - 血色月夜
    - 羊皮纸质感
    - 封印印章
    - 中世纪
    - 史诗感
    - 神秘氛围
  fontSystem:
    fontFamily: Noto Serif SC, Cinzel, Crimson Text
    heading:
      size: 48rpx-96rpx
      weight: 700
    subheading:
      size: 32rpx-40rpx
      weight: 600
    body:
      size: 26rpx-30rpx
      weight: 400
  colorSystem:
    primary:
      - "#8b0000"
      - "#4a0000"
      - "#d4af37"
      - "#8a7020"
    background:
      - "#0a0806"
      - "#1a1510"
      - "#2a2018"
    text:
      - "#e8dcc8"
      - "#a0a0a0"
    functional:
      - "#3d3025"
      - "#c4c4c4"
      - "#f5f5f5"
todos:
  - id: global-theme
    content: 重写 app.scss 和 app.tsx，建立全局色彩系统、字体加载、通用组件样式和背景装饰层
    status: completed
  - id: index-page
    content: 改造首页样式，实现英雄区、用户徽章、封印按钮和功能卡片的哥特式设计
    status: completed
    dependencies:
      - global-theme
  - id: auth-pages
    content: 改造登录和注册页样式，实现狼徽章 Logo、羊皮纸表单和装饰元素
    status: completed
    dependencies:
      - global-theme
  - id: room-list-page
    content: 改造房间列表页样式，实现通缉令卡片、玩家点阵和血红印章装饰
    status: completed
    dependencies:
      - global-theme
  - id: room-detail-page
    content: 改造房间详情页样式，实现圆桌布局、环形座位排列和底部操作栏
    status: completed
    dependencies:
      - global-theme
  - id: profile-game-pages
    content: 改造个人资料和游戏页面样式，保持整体风格一致性和游戏可用性
    status: completed
    dependencies:
      - global-theme
---

## 用户需求

将当前基于 Taro + React 的狼人杀游戏前端从简约现代科技风改造为沉浸式哥特式暗黑风格，参考 preview/index-new.html 中的精美设计。

## 产品概述

对狼人杀游戏平台的全部前端页面进行视觉升级，从现有的深蓝色科技风转变为血色月夜主题的哥特式风格，营造神秘、紧张的游戏氛围。

## 核心功能

1. **全局主题改造**：将配色方案从现代蓝红色系改为血红+古金+羊皮纸色系，引入 Noto Serif SC 衬线字体
2. **背景氛围层**：添加月亮、迷雾、噪点纹理等装饰性背景元素
3. **羊皮纸卡片系统**：将所有卡片组件改造为具有渐变背景、金色边框、装饰线条的羊皮纸质感
4. **封印按钮样式**：实现带立体感、金色边框、装饰符号的按钮，支持按压动效
5. **通缉令卡片**：房间列表采用通缉令样式，带血红印章装饰
6. **圆桌座位布局**：房间详情页使用环形座位布局，中心显示房间信息
7. **装饰符号系统**：在各个组件中合理使用 ◆ ✦ ❖ 👑 等装饰符号
8. **动画效果**：添加迷雾飘动、封印旋转、悬停发光、按钮按压等微动效

## 技术栈

- **前端框架**: Taro 3.x + React 18 + TypeScript
- **样式方案**: SCSS 预处理器
- **组件库**: Taro 内置组件 (View, Text, Button)
- **字体方案**: Google Fonts (Noto Serif SC, Cinzel, Crimson Text)
- **动画**: CSS3 Animation + Transition

## 实现方案

### 1. 设计风格迁移策略

采用渐进式改造方案，保持现有 TSX 组件结构和业务逻辑不变，仅对 SCSS 样式文件进行深度重写。参考 index-new.html 的设计规范，将哥特式暗黑风格适配到 Taro 框架的组件体系中。

**核心设计原则**：

- 使用 CSS 变量统一管理色彩系统
- 通过伪元素 (::before, ::after) 实现装饰效果
- 使用渐变 (linear-gradient, radial-gradient) 营造质感
- 合理运用 box-shadow 和 text-shadow 增强层次感

### 2. 色彩系统设计

在 `app.scss` 中定义全局 CSS 变量：

```
:root {
  --blood-red: #8b0000;
  --blood-dark: #4a0000;
  --gold: #d4af37;
  --gold-dim: #8a7020;
  --parchment: #1a1510;
  --parchment-light: #2a2018;
  --shadow: #0a0806;
  --moon: #c4c4c4;
  --text: #e8dcc8;
}
```

### 3. 字体系统设计

引入 Google Fonts，在 `app.tsx` 中动态加载：

- **Noto Serif SC**: 中文衬线字体，用于正文
- **Cinzel**: 英文装饰字体，用于标题
- **Crimson Text**: 英文衬线字体，用于辅助文本

通过 Taro.loadFontFace API 或在 SCSS 中使用 @import 引入。

### 4. 组件样式改造方案

#### 4.1 全局背景层 (app.scss)

```
page {
  background: var(--shadow);
  position: relative;
  
  &::before { // 月亮
    content: '';
    position: fixed;
    top: 5%; right: 10%;
    width: 120px; height: 120px;
    background: radial-gradient(circle at 30% 30%, #f5f5f5, #c4c4c4, #888);
    border-radius: 50%;
    box-shadow: 0 0 60px rgba(196, 196, 196, 0.3);
  }
  
  &::after { // 迷雾
    content: '';
    position: fixed;
    bottom: 0; left: 0; right: 0;
    height: 200px;
    background: linear-gradient(180deg, transparent, rgba(74, 0, 0, 0.6));
    animation: fogMove 8s ease-in-out infinite;
  }
}
```

#### 4.2 羊皮纸卡片 (.card 类)

```
.parchment-card {
  background: linear-gradient(135deg, var(--parchment-light), var(--parchment), #15100c);
  border: 1px solid #3d3025;
  border-radius: 2px 12px 2px 12px;
  box-shadow: 0 8px 32px rgba(0,0,0,0.6), inset 0 1px 0 rgba(255,255,255,0.05);
  
  &::before { // 顶部装饰线
    content: '';
    position: absolute;
    top: -1px; left: 20px; right: 20px;
    height: 2px;
    background: linear-gradient(90deg, transparent, var(--blood-dark), transparent);
  }
}
```

#### 4.3 封印按钮 (.btn-primary 类)

```
.seal-btn {
  background: linear-gradient(180deg, rgba(139,0,0,0.9), rgba(74,0,0,0.95));
  border: 2px solid var(--gold);
  border-radius: 4px;
  color: var(--text);
  font-family: 'Noto Serif SC', serif;
  letter-spacing: 4px;
  box-shadow: 0 6px 0 var(--blood-dark), 0 8px 20px rgba(0,0,0,0.5);
  transition: all 0.1s;
  
  &::before { content: '◈'; margin-right: 8px; color: var(--gold); }
  &::after { content: '◈'; margin-left: 8px; color: var(--gold); }
  
  &:active {
    transform: translateY(6px);
    box-shadow: 0 0 0 var(--blood-dark), 0 2px 10px rgba(0,0,0,0.5);
  }
}
```

#### 4.4 通缉令卡片 (room-list)

```
.wanted-card {
  background: linear-gradient(135deg, rgba(26,21,16,0.95), rgba(20,15,10,0.98));
  border: 1px solid #4a3c32;
  border-radius: 4px 16px 4px 16px;
  
  &::before { // 血红印章
    content: '';
    position: absolute;
    top: 8px; right: 8px;
    width: 40px; height: 40px;
    border: 2px solid var(--blood-red);
    border-radius: 50%;
    opacity: 0.3;
    transform: rotate(15deg);
  }
}
```

#### 4.5 圆桌座位布局 (room)

使用绝对定位将座位按圆形分布：

```
.round-table {
  position: relative;
  width: 100%;
  aspect-ratio: 1;
  
  .seat {
    position: absolute;
    
    &:nth-child(1) { top: 0; left: 50%; transform: translateX(-50%); }
    &:nth-child(2) { top: 15%; right: 5%; }
    &:nth-child(3) { top: 50%; right: 0; transform: translateY(-50%); }
    // ... 其他座位
  }
}
```

### 5. 动画效果实现

```
@keyframes fogMove {
  0%, 100% { transform: translateX(-10px); opacity: 0.6; }
  50% { transform: translateX(10px); opacity: 0.8; }
}

@keyframes rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
```

### 6. 响应式适配

由于 Taro 主要面向移动端，使用 rpx 单位确保适配不同屏幕：

```
.container {
  padding: 80rpx 32rpx;
  font-size: 28rpx;
}
```

## 实现注意事项

1. **保持组件逻辑不变**：仅修改 SCSS 文件，不改动 TSX 组件的结构和事件处理
2. **性能优化**：

- 背景层使用 fixed 定位避免重排
- 动画使用 transform 和 opacity 触发 GPU 加速
- 避免过多伪元素嵌套导致渲染压力

3. **Taro 兼容性**：

- 使用 Taro 支持的 CSS 属性
- 避免使用不支持的 backdrop-filter
- 测试小程序端的样式表现

4. **渐变层次控制**：合理使用 z-index 确保背景层、内容层、装饰层的正确叠加
5. **可访问性**：确保文本对比度符合 WCAG 标准，按钮点击区域足够大

## 架构设计

### 样式层级结构

```
app.scss (全局)
├── CSS 变量定义
├── 全局背景层
├── 字体引入
├── 通用组件样式
│   ├── .parchment-card
│   ├── .seal-btn
│   ├── .section-title
│   └── 滚动条样式
└── 动画定义

pages/*/index.scss (页面)
├── 页面容器样式
├── 页面特定组件
└── 页面特定动画
```

### 目录结构

所有修改限定在 `packages/frontend/src/` 目录下：

```
packages/frontend/src/
├── app.tsx              # [MODIFY] 添加字体加载逻辑
├── app.scss             # [MODIFY] 重写全局样式，定义色彩系统、通用组件、背景层
├── pages/
│   ├── index/
│   │   └── index.scss   # [MODIFY] 首页样式：英雄区、用户徽章、功能卡片
│   ├── login/
│   │   └── index.scss   # [MODIFY] 登录页样式：狼徽章 Logo、羊皮纸表单
│   ├── register/
│   │   └── index.scss   # [MODIFY] 注册页样式：与登录页保持一致
│   ├── room-list/
│   │   └── index.scss   # [MODIFY] 房间列表样式：通缉令卡片、玩家点阵
│   ├── room/
│   │   └── index.scss   # [MODIFY] 房间详情样式：圆桌布局、座位环形排列、底部操作栏
│   ├── profile/
│   │   └── index.scss   # [MODIFY] 个人资料样式：保持整体风格一致
│   ├── game/
│   │   ├── role-reveal/
│   │   │   └── index.scss  # [MODIFY] 角色揭晓样式：增强氛围感
│   │   └── play/
│   │       └── index.scss  # [MODIFY] 游戏进行样式：保持可用性的前提下增强视觉效果
```

### 关键代码结构

#### CSS 变量系统 (app.scss)

```
:root {
  // 色彩系统
  --blood-red: #8b0000;
  --blood-dark: #4a0000;
  --gold: #d4af37;
  --gold-dim: #8a7020;
  --parchment: #1a1510;
  --parchment-light: #2a2018;
  --shadow: #0a0806;
  --text: #e8dcc8;
  
  // 字体系统
  --font-serif: 'Noto Serif SC', serif;
  --font-display: 'Cinzel', 'Noto Serif SC', serif;
  
  // 间距系统
  --spacing-xs: 8rpx;
  --spacing-sm: 16rpx;
  --spacing-md: 24rpx;
  --spacing-lg: 32rpx;
  --spacing-xl: 40rpx;
}
```

#### 通用组件样式类 (app.scss)

- `.parchment-card`: 羊皮纸卡片基础样式
- `.seal-btn`: 封印按钮样式
- `.seal-btn.secondary`: 次要按钮样式
- `.section-title`: 章节标题样式
- `.avatar-seal`: 头像封印样式
- `.bg-layer`: 背景装饰层容器

## 设计风格

采用**哥特式暗黑风格**，以血色月夜为主题，营造神秘、紧张、史诗感的游戏氛围。设计灵感源自中世纪羊皮纸卷、封印印章、通缉令等元素。

### 页面规划

#### 1. 首页 (Index)

- **英雄区块**：大标题"狼人杀"配装饰符号 ✦，副标题"血色月夜"斜体
- **用户徽章**：圆形头像封印（金色边框+虚线外圈），用户名+等级+积分
- **快速操作**：两个封印按钮（加入房间、创建房间）
- **功能卡片**：3×1 网格展示游戏特色（AI 对战、RAG 增强、多人联机）

#### 2. 登录/注册页 (Login/Register)

- **Logo 区域**：大型圆形狼徽章（旋转动画），下方标题文字
- **表单卡片**：羊皮纸质感卡片，表单标签带 ◆ 符号
- **输入框**：深色背景，金色聚焦边框，内阴影效果
- **提交按钮**：封印按钮样式，按压动效
- **底部链接**：虚线下划线，血红色强调

#### 3. 房间列表 (Room List)

- **顶部搜索**：输入框+加入按钮组合
- **创建按钮**：大型封印按钮
- **通缉令卡片**：
- 羊皮纸背景，右上角血红圆形印章
- 标题带 ❖ 符号，房间代码采用等宽字体+血红背景
- 玩家数量用点阵图示（已加入为红点发光，空位为暗点）
- 右下角加入按钮

#### 4. 房间详情 (Room)

- **顶部信息**：房间名称+房间代码居中显示
- **圆桌布局**：
- 中心圆形区域显示状态（金色边框，羊皮纸质感）
- 8 个座位沿圆形排列
- 每个座位显示头像（房主带 👑 标记）、昵称、号码
- 空座位为半透明暗色
- **底部操作栏**：渐变背景，离开按钮+准备按钮

#### 5. 个人资料 (Profile)

- **头部卡片**：大型头像封印+用户信息
- **统计数据**：游戏场次、胜率、积分等，采用卡片网格布局
- **历史记录**：列表卡片展示最近战绩

#### 6. 游戏页面 (Game)

- **角色揭晓**：大型角色卡片翻转动画，角色名称+描述
- **游戏进行**：保持简洁可用，添加血红边框和阴影增强氛围

### 设计规范

**布局**：

- 页面内边距：80rpx 32rpx
- 卡片间距：24rpx - 32rpx
- 圆角：2px 12px 2px 12px（羊皮纸卡片）
- 按钮圆角：4px（封印按钮）

**排版**：

- 标题：Cinzel 字体，48rpx - 96rpx，字重 900
- 正文：Noto Serif SC，28rpx - 32rpx，字重 400-600
- 装饰符号：32rpx - 40rpx

**交互**：

- 悬停：金色发光（text-shadow / box-shadow）
- 按压：向下位移 6px，阴影消失
- 过渡：0.3s ease（颜色、边框），0.1s（按压）
- 动画：迷雾 8s 循环，封印旋转 20s 循环

**阴影**：

- 卡片：0 8px 32px rgba(0,0,0,0.6)
- 按钮：0 6px 0（立体感） + 0 8px 20px（环境阴影）
- 发光：0 0 20px rgba(212, 175, 55, 0.3)

**响应式**：

- 使用 rpx 单位自适应屏幕宽度
- 圆桌布局使用 aspect-ratio: 1 保持正方形
- 最大宽度限制避免超宽屏变形