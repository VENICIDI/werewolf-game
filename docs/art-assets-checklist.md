# 美术资源需求清单

> 风格基准：**手绘墨线卡通动漫风（Hand-drawn Ink Anime）**
> 特征：粗犷墨线 + 纸张质感 + 不完美手绘细节 + 有限色彩填充
> 参考已有：AI 人机头像（01~12）、玩家默认头像（4张）

---

## 一、角色卡面插画（角色揭示页）

当前状态：role-reveal 页面用 **Emoji** 展示角色（🐺🔮🧙‍♀️等），非常 web 感。
需要替换为手绘风格的角色半身立绘。

| 编号 | 角色 | 尺寸 | 提示词方向 |
|------|------|------|-----------|
| R-01 | 狼人 | 512×768 | 暗夜中露出獠牙的狼人战士，撕裂的斗篷，血红色眼，月光下轮廓 |
| R-02 | 预言家 | 512×768 | 手持水晶球的老者或灵媒，第三眼微光，紫色魔法光晕 |
| R-03 | 女巫 | 512×768 | 持药瓶的黑袍女性，一手绿（毒）一手金（解药），烟雾缭绕 |
| R-04 | 猎人 | 512×768 | 皮衣弩弓猎人，单膝跪地瞄准姿势，地面弹壳，硝烟 |
| R-05 | 守卫 | 512×768 | 持巨盾铁甲骑士，坚毅眼神，盾牌反射月光 |
| R-06 | 村民 | 512×768 | 朴素衣着的农夫/农妇，手持火把，警惕四顾 |
| R-07 | 白痴 | 512×768 | 戴歪帽子的笑脸角色，夸张表情，无害感 |

**通用风格约束**：
```
Hand-drawn anime style character illustration, full portrait from waist up, bold ink lines with varying thickness, sketchy outlines with visible brushstrokes, [角色色系] watercolor wash, textured parchment paper background, imperfect hand-drawn details, vertical composition 512x768, dark fantasy card art style
```

---

## 二、场景背景插画（游戏阶段氛围）

当前状态：所有背景为 **CSS 渐变色**，缺乏游戏氛围感。
需要手绘风格背景大图，通过透明度叠加在现有渐变上。

| 编号 | 场景 | 尺寸 | 描述 |
|------|------|------|------|
| BG-01 | 夜晚·狼人行动 | 750×1334 | 暗夜村庄剪影，血月悬空，雾气弥漫的街道，远处有狼影 |
| BG-02 | 夜晚·神职行动 | 750×1334 | 烛光照亮的密室，水晶球/药架/盾牌模糊轮廓，星空窗外 |
| BG-03 | 白天·讨论阶段 | 750×1334 | 圆桌会议俯视，空椅子围绕，晨光从侧窗投入，纸张散落 |
| BG-04 | 白天·投票处决 | 750×1334 | 广场绞刑架剪影，乌鸦栖息，夕阳染红天空 |

**通用风格约束**：
```
Hand-drawn ink wash style background illustration, atmospheric scene, loose brushwork with ink splatter texture, muted [场景色系] palette, dark fantasy village setting, slightly desaturated, paper texture overlay, mobile game background 750x1334, low opacity suitable for text overlay
```

---

## 三、游戏结算页素材

当前状态：游戏结束后 **无专门结算页/视觉**，只是弹窗提示。
需要胜利/失败的全屏视觉。

| 编号 | 类型 | 尺寸 | 描述 |
|------|------|------|------|
| END-01 | 好人阵营胜利 | 750×400 | 朝阳升起，村庄恢复和平，鲜花与麦穗，手绘横幅标题区 |
| END-02 | 狼人阵营胜利 | 750×400 | 血月笼罩废墟村庄，狼群嚎叫剪影，暗红墨水飞溅 |
| END-03 | 个人胜利徽章 | 256×256 | 金色奖杯/桂冠，手绘描边，胜利光效 |
| END-04 | 个人失败徽章 | 256×256 | 碎裂盾牌/凋零花，灰暗色调，裂纹墨线 |

---

## 四、阶段转场动画帧

当前状态：阶段切换只是文字+图标闪现，缺乏仪式感。
需要关键转场的动画帧（可用帧序列或 Lottie）。

| 编号 | 动画 | 帧数 | 描述 |
|------|------|------|------|
| ANI-01 | 天黑了 | 8~12帧 | 月亮升起 → 村庄灯火熄灭 → 暗幕落下 |
| ANI-02 | 天亮了 | 8~12帧 | 公鸡剪影 → 朝阳射线展开 → 雾气消散 |
| ANI-03 | 玩家死亡 | 6~8帧 | 头像碎裂/灰化效果 → 骷髅浮现 → 墓碑落下 |
| ANI-04 | 投票倒计时 | 循环 | 沙漏流沙 / 蜡烛燃尽，可循环播放 |

---

## 五、UI 装饰元素

当前状态：界面纯 CSS 实现，缺乏手绘质感的装饰细节。

| 编号 | 元素 | 尺寸 | 用途 |
|------|------|------|------|
| UI-01 | 圆桌桌面纹理 | 512×512 | 游戏主界面中央桌面底图（替代纯色圆） |
| UI-02 | 卷轴边框 | 9-slice | 聊天消息/公告框的装饰边框 |
| UI-03 | 血迹分隔线 | 750×20 | 替代当前 CSS 渐变分隔线 |
| UI-04 | 座位号徽章底图 | 64×64 | 手绘风格圆形/六角形底座，放号码 |
| UI-05 | 按钮底纹 | 300×80 | 手绘风格按钮框（替代 CSS gradient 按钮） |
| UI-06 | 标题装饰 | 600×60 | 章节标题两侧的手绘藤蔓/花纹 |

---

## 六、底部 Tab 栏图标

当前状态：约 200 bytes 的极简占位图标。
需要与整体手绘风格统一的 Tab 图标。

| 编号 | 图标 | 尺寸 | 描述 |
|------|------|------|------|
| TAB-01 | 首页 | 48×48 | 手绘风格的村庄/小屋图标 |
| TAB-02 | 首页（选中） | 48×48 | 同上但带暖光/血红高亮 |
| TAB-03 | 房间 | 48×48 | 手绘风格的门/大厅入口 |
| TAB-04 | 房间（选中） | 48×48 | 同上但门开启/高亮 |
| TAB-05 | 我的 | 48×48 | 手绘风格的人物剪影 |
| TAB-06 | 我的（选中） | 48×48 | 同上高亮 |

---

## 七、音效与配乐（加分项）

| 编号 | 类型 | 时长 | 描述 |
|------|------|------|------|
| SFX-01 | 夜幕降临 | 2~3s | 猫头鹰叫 + 风声渐起 |
| SFX-02 | 天亮鸡鸣 | 2s | 公鸡打鸣 + 清晨鸟啼 |
| SFX-03 | 投票落锤 | 1s | 木槌敲击审判台 |
| SFX-04 | 死亡音效 | 1.5s | 心跳停止 + 灵魂飘散 |
| SFX-05 | 胜利号角 | 3s | 凯旋号角 + 欢呼 |
| SFX-06 | 失败丧钟 | 3s | 教堂丧钟 + 乌鸦 |
| BGM-01 | 夜间配乐 | 循环 | 低沉弦乐 + 诡异音盒，紧张氛围 |
| BGM-02 | 白天配乐 | 循环 | 轻快弦乐 + 适度紧张感 |

---

## 优先级排序

| 优先级 | 资源 | 理由 |
|--------|------|------|
| 🔴 P0 | 角色卡面 (R-01~07) | 替换 Emoji，游戏感提升最大 |
| 🔴 P0 | 圆桌桌面纹理 (UI-01) | 核心游戏界面视觉焦点 |
| 🟡 P1 | 场景背景 (BG-01~04) | 氛围感大幅提升 |
| 🟡 P1 | 游戏结算页 (END-01~04) | 游戏完整性 |
| 🟡 P1 | Tab 图标 (TAB-01~06) | 整体精致度 |
| 🟢 P2 | 阶段转场动画 (ANI-01~04) | 锦上添花，仪式感 |
| 🟢 P2 | UI 装饰元素 (UI-02~06) | 精细打磨 |
| ⚪ P3 | 音效配乐 (SFX/BGM) | 沉浸感加成，非视觉优先 |

---

## 生图提示词模板（直接可用）

### 角色卡面模板
```
Hand-drawn anime style character illustration, [角色描述], bold ink lines with varying thickness, sketchy outlines with visible brushstrokes, [色系] watercolor wash accents, textured parchment paper background, imperfect hand-drawn details, dark fantasy card art, vertical portrait composition, no text, no watermark
```

### 背景场景模板
```
Hand-drawn ink wash style background, [场景描述], atmospheric loose brushwork with ink splatter, muted [色系] palette, dark fantasy village setting, slightly desaturated, paper grain texture overlay, low detail for text readability, mobile game background, no characters, no text
```

### UI 元素模板
```
Hand-drawn ink style game UI element, [元素描述], isolated on transparent background, bold rough ink outlines, [色系] accents, parchment texture, imperfect handmade feel, game asset, PNG with alpha
```

---

*生成于 2026-05-05 | 基于当前项目美术现状分析*
