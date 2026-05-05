# Werewolf Frontend - Quick Reference

## Overview

| Aspect | Details |
|--------|---------|
| **Framework** | Taro 3.6.23 + React 18.2.0 + TypeScript |
| **Build Tool** | Webpack 5 |
| **Styling** | SASS/SCSS |
| **Language** | TypeScript 5.2.0 |
| **Platforms** | H5 (Web) & WeApp (WeChat mini-app) |

---

## Key File Paths

### Main Game Component (WHERE PLAYERS PLAY)
```
/Users/veennyyang/program/werewolf-game/packages/frontend/src/pages/game/play/index.tsx
```
- 1680 lines of React/TypeScript
- Handles all game phases, voting, player actions
- Real-time WebSocket communication
- Circular player seating UI
- Vote tracking and results display

### Other Important Game Pages
```
/pages/game/index.tsx              - Game entry point
/pages/game/role-reveal/index.tsx  - Role reveal with 3s countdown
```

### Core Utilities
```
/src/utils/request.ts      - HTTP client + resource URL resolver
/src/utils/websocket.ts    - WebSocket manager
/src/utils/auth-guard.ts   - Authentication guard
/src/api/game.ts           - Game API functions
/src/api/auth.ts           - Auth API functions
/src/api/room.ts           - Room API functions
```

### Configuration Files
```
/src/app.config.ts         - Routes & tab bar config
/config/index.js           - Taro/Webpack config
/tsconfig.json             - TypeScript config
/babel.config.js           - Babel config
```

---

## Routes Overview

```
Home              pages/index/index
Login             pages/login/index
Register          pages/register/index
Room List         pages/room-list/index
Room Details      pages/room/index
Game Entry        pages/game/index
GAME PLAY         pages/game/play/index          <-- MAIN GAME PAGE
Role Reveal       pages/game/role-reveal/index
Profile           pages/profile/index
```

---

## Audio Setup

**File Location:** `/src/assets/audio/bgm.mp3` (4.1 MB)

**Current Status:** NOT IMPORTED OR USED

**To Use It:**
```typescript
// Create /src/utils/audio.ts
import bgmPath from '../assets/audio/bgm.mp3'

const audioContext = Taro.createInnerAudioContext()
audioContext.src = bgmPath
audioContext.play()
```

---

## Asset Import Pattern

### Static Images
```typescript
import imgBackground from '../../../assets/images/backgrounds/bg-day.png'
<Image src={imgBackground} mode='aspectFill' />
```

### Dynamic Images (e.g., roles)
```typescript
const getRoleImage = (role: string) => {
  const map: Record<string, string> = {
    WEREWOLF: require('../../../assets/images/roles/werewolf.png'),
    SEER: require('../../../assets/images/roles/seer.png'),
    // ...
  }
  return map[role] || map['VILLAGER']
}
```

### Backend Resource URLs
```typescript
import { getResourceUrl } from '../utils/request'

// Converts '/avatars/user-1.png' to 'http://localhost:8080/avatars/user-1.png'
<Image src={getResourceUrl(player.avatarUrl)} />
```

---

## Game Phases

| Phase | Actors | Action |
|-------|--------|--------|
| NIGHT_START | Everyone | Prepare for night actions |
| WEREWOLF | Werewolves | Choose kill target |
| SEER | Seer | Choose player to check |
| WITCH | Witch | Use save/poison potion |
| GUARD | Guard | Choose player to protect |
| HUNTER | Hunter | Shoot (if dead) |
| DAY_START | Everyone | Prepare for discussion |
| DISCUSSION | All alive | Turn-based speaking |
| VOTING | All alive | Vote to eliminate |
| EXECUTION | Everyone | Results announced |

---

## Component Structure

```
Pages/
  ├── game/
  │   ├── play/index.tsx           [MAIN GAME - 1680 lines]
  │   ├── role-reveal/index.tsx    [Role animation]
  │   └── index.tsx                [Entry redirect]
  ├── room/index.tsx               [Room details]
  ├── room-list/index.tsx          [Browse rooms]
  ├── index/index.tsx              [Home]
  ├── login/index.tsx              [Login]
  ├── register/index.tsx           [Register]
  └── profile/index.tsx            [User profile]

Components/
  ├── Icons/index.tsx              [SVG icons]
  └── LoginModal/index.tsx         [Login modal]
```

---

## Tab Bar (Bottom Navigation)

| Icon | Label | Route |
|------|-------|-------|
| home.png | 首页 (Home) | pages/index/index |
| room.png | 房间 (Rooms) | pages/room-list/index |
| profile.png | 我的 (Profile) | pages/profile/index |

---

## Development

### Commands
```bash
npm run dev:h5       # Dev H5 (web) on port 10086
npm run build:h5     # Build for H5
npm run dev:weapp    # Dev WeChat mini-app
npm run build:weapp  # Build for WeChat
```

### API Base URL
- **H5 (web):** `/api` (proxied to http://localhost:8080/api)
- **WeApp:** `http://localhost:8080/api`

### Dev Server
- **Port:** 10086
- **Proxy:** `/api` → `http://localhost:8080`

---

## Theme Colors

```
Primary Background:  #080604 (very dark brown)
Text Primary:        #f0e6d6 (cream/beige)
Text Secondary:      #b0a090 (tan)
Gold/Accent:         #e5c040
Active Red:          #c41a1a
Green (success):     #4caf50
Red (danger):        #d32f2f
Purple:              #9c27b0
```

---

## Game State Management

- **HTTP:** `Taro.request()` wrapper in `/utils/request.ts`
- **WebSocket:** Event-driven updates in `/utils/websocket.ts`
- **Storage:** Taro local storage for token, userInfo, currentGameId, currentRoomCode
- **Component State:** React hooks (useState, useCallback, useEffect, useRef)

---

## Important: Audio Not Implemented Yet

The BGM file exists at `/src/assets/audio/bgm.mp3` but:
- Not imported anywhere
- No audio player component
- No phase-based audio triggers
- Ready to implement when needed

---

## Players & Roles

### Roles
- VILLAGER (村民) - Common role
- WEREWOLF (狼人) - Killer role
- SEER (预言家) - Information role
- WITCH (女巫) - Support role (save/poison)
- HUNTER (猎人) - Tactical role (shoot on death)
- GUARD (守卫) - Protection role
- IDIOT (白痴) - Special role

### Player States
- ALIVE - Active in game
- DEAD - Eliminated
- DISCONNECTED - Lost connection

---

## TypeScript Paths

### Path Alias
```
@/ → ./src/
```

Example: `import { get } from '@/utils/request'`

---

## File Sizes

- Main game component: ~54KB (1680 lines)
- Audio file: ~4.1 MB (bgm.mp3)
- Total source files: ~19 TypeScript/TSX files
- Total assets: 50+ images + 1 audio file

---

## Key Libraries

```json
{
  "@tarojs/taro": "3.6.23",
  "@tarojs/react": "3.6.23",
  "@tarojs/components": "3.6.23",
  "@tarojs/webpack5-runner": "3.6.23",
  "react": "18.2.0",
  "react-dom": "18.2.0",
  "typescript": "5.2.0",
  "webpack": "5.89.0"
}
```

