# Werewolf Game Frontend - Project Structure Overview

## 1. Framework & Build Tool

### Framework: **Taro 3.6.23** with **React 18.2.0**
- **Language:** TypeScript 5.2.0
- **Build Tool:** Webpack 5 (via @tarojs/webpack5-runner)
- **Styling:** SASS/SCSS
- **Environment:** H5 (HTML5 web) and WeApp (WeChat mini-app) support

### Key Dependencies:
- `@tarojs/taro` - Core Taro framework
- `@tarojs/react` - React plugin for Taro
- `@tarojs/components` - Cross-platform UI components
- `react` - UI library
- `react-dom` - React DOM renderer
- `typescript` - Type checking

### Build Configuration:
- **Webpack5 runner:** @tarojs/webpack5-runner
- **Design width:** 375px (mobile-first design)
- **Output:** `dist/` directory
- **Source:** `src/` directory
- **Babel:** 7.23.0 with TypeScript preset

---

## 2. Main Game Page/Component

### Primary Game Component: `/pages/game/play/index.tsx`
**File Path:** `/Users/veennyyang/program/werewolf-game/packages/frontend/src/pages/game/play/index.tsx`

This is the **main interactive game component** where players participate during rounds. It handles:
- Game state management (players, roles, phases)
- Real-time WebSocket communication
- Player actions (voting, killing, checking, saving, poisoning)
- Chat/discussion interface
- Phase management (night actions, discussion, voting, execution)
- Countdown timers for each phase and speaking turn
- Vote tracking and results display
- Role-specific action UI (Witch potion selection, Hunter shooting, etc.)

### Related Game Pages:
1. **Game Entry:** `/pages/game/index.tsx`
   - Redirect page to ensure proper gameId handling

2. **Role Reveal:** `/pages/game/role-reveal/index.tsx`
   - Shows role assignment with 3-second countdown
   - Displays role description, abilities, and tips
   - Path: `/Users/veennyyang/program/werewolf-game/packages/frontend/src/pages/game/role-reveal/index.tsx`

### Game Phases Handled:
- NIGHT_START - Night begins
- WEREWOLF - Werewolves choose kill target
- SEER - Seer chooses player to check
- WITCH - Witch chooses to save/poison
- GUARD - Guard chooses player to protect
- HUNTER - Hunter shoots after death
- DAY_START - Day begins
- DISCUSSION - Players discuss (turn-based speaking)
- VOTING - Players vote to eliminate someone
- EXECUTION - Vote results announced

---

## 3. Audio/Sound Related Code

### Current Audio Setup:

**Audio File:**
- Path: `/Users/veennyyang/program/werewolf-game/packages/frontend/src/assets/audio/bgm.mp3`
- File size: ~4.1 MB
- Format: MP3 (background music)

**Current Status:**
- **NO active audio implementation found** in the codebase
- Audio file exists but is **not imported or used anywhere** in the source code
- No audio players, sound effects, or BGM implementation in React components
- No audio utility functions in utils directory

**Search Results:**
Searching for audio-related keywords (`audio`, `sound`, `mp3`, `wav`, `ogg`) in TypeScript/JSX files returned only authentication-related matches (`isLoggedIn`), indicating **no audio/sound implementation**.

**To Add Audio Support:**
The audio file is ready at `assets/audio/bgm.mp3` but needs to be:
1. Imported in components (using `require()` or ES6 `import`)
2. Wrapped in Taro's Audio component or HTML5 Audio API
3. Integrated with game phase events (e.g., play during night phases, pause during day)

---

## 4. Router Configuration

### Routes Definition: `/app.config.ts`
**File Path:** `/Users/veennyyang/program/werewolf-game/packages/frontend/src/app.config.ts`

```typescript
pages: [
  'pages/index/index',           // Home page
  'pages/login/index',           // Login page
  'pages/register/index',        // Registration page
  'pages/room-list/index',       // Room list
  'pages/room/index',            // Room details/waiting room
  'pages/game/index',            // Game entry (redirects to play)
  'pages/game/play/index',       // Main game page (GAME PLAY)
  'pages/game/role-reveal/index', // Role reveal animation
  'pages/profile/index'          // User profile
]
```

### Tab Bar Configuration:
Three main tabs in the bottom navigation:
1. **首页 (Home)** → `pages/index/index`
   - Icons: `assets/images/home.png` / `home-active.png`

2. **房间 (Rooms)** → `pages/room-list/index`
   - Icons: `assets/images/room.png` / `room-active.png`

3. **我的 (Profile)** → `pages/profile/index`
   - Icons: `assets/images/profile.png` / `profile-active.png`

### Theme Colors:
```
- Background: #080604 (dark brown/black)
- Text: #f0e6d6 (beige/cream)
- Gold: #e5c040
- Primary Red: #c41a1a (selected tab)
- Secondary Text: #b0a090
```

### Path Alias:
- `@/` → `./src/` (configured in tsconfig.json)

---

## 5. Asset Import Patterns

### Image Asset Imports (Static Import):

**Pattern 1: Using `require()` - Most Common**
```typescript
// In /pages/game/play/index.tsx
import imgVillagerWin from '../../../assets/images/endings/villager-victory.png'
import imgBgNight from '../../../assets/images/backgrounds/bg-night-village.png'
import imgTable from '../../../assets/images/table-texture.png'

// Usage in component:
<Image className='bg-atmosphere' src={phaseDisplay.bg === 'night' ? imgBgNight : imgBgDay} mode='aspectFill' />
```

**Pattern 2: Using `require()` with dynamic role lookup**
```typescript
// In /pages/game/play/index.tsx
const getRoleImage = (role?: string) => {
  const map: Record<string, string> = {
    VILLAGER: require('../../../assets/images/roles/villager.png'),
    WEREWOLF: require('../../../assets/images/roles/werewolf.png'),
    SEER: require('../../../assets/images/roles/seer.png'),
    WITCH: require('../../../assets/images/roles/witch.png'),
    HUNTER: require('../../../assets/images/roles/hunter.png'),
    GUARD: require('../../../assets/images/roles/guard.png'),
    IDIOT: require('../../../assets/images/roles/idiot.png'),
  }
  return map[role || ''] || map['VILLAGER']
}
```

### Asset Directory Structure:
```
src/assets/
├── audio/
│   └── bgm.mp3                    # Background music (unused)
├── images/
│   ├── backgrounds/
│   │   ├── bg-day.png
│   │   ├── bg-night-village.png
│   │   ├── bg-night-chamber.png
│   │   └── bg-vote.png
│   ├── roles/
│   │   ├── villager.png
│   │   ├── werewolf.png
│   │   ├── seer.png
│   │   ├── witch.png
│   │   ├── hunter.png
│   │   ├── guard.png
│   │   └── idiot.png
│   ├── endings/
│   │   ├── villager-victory.png
│   │   └── werewolf-victory.png
│   ├── home-poster.png
│   ├── table-texture.png
│   ├── home.png / home-active.png
│   ├── room.png / room-active.png
│   ├── profile.png / profile-active.png
│   ├── 村民.png, 狼人.png, 猎人.png, 守卫.png, 预言家.png, 女巫.png
│   └── avatars/                   # (directory exists, for user avatars)
├── icons/
│   └── wolf-logo.png
└── gen_icons.py                   # Script for icon generation
```

### URL Resolution for Backend Resources:

**Request Utility Function:** `/src/utils/request.ts`
```typescript
export const getResourceUrl = (path: string | undefined | null): string => {
  if (!path) return ''
  if (path.startsWith('http://') || path.startsWith('https://')) return path
  return `${SERVER_BASE}${path}`  // Converts relative paths to full URLs
}
// Example: '/avatars/user-1.png' → 'http://localhost:8080/avatars/user-1.png'
```

### Dynamic Avatar Import:
```typescript
// In player components
player.avatarUrl ? 
  <image src={getResourceUrl(player.avatarUrl)} className='avatar-img' /> 
  : <IconRobot size={20} />
```

---

## 6. WebSocket & Real-Time Communication

### WebSocket Manager: `/src/utils/websocket.ts`
- Manages real-time game updates
- Event handlers: PHASE_CHANGE, PHASE_ADVANCE, PLAYER_CHAT, GAME_OVER, VOTE_UPDATE, etc.
- Automatic reconnection with game snapshot sync

### Key Features in Game Play Page:
- Receives real-time phase changes
- Updates player voting progress
- Broadcasts chat messages
- Sends player actions (vote, kill, check, poison, save, shoot)
- Handles player deaths and game endings
- Syncs server time for accurate countdowns

---

## 7. Project Structure Summary

```
src/
├── app.tsx                    # Root app component
├── app.config.ts              # Route & tab configuration
├── app.scss                   # Global styles
├── api/
│   ├── auth.ts               # Authentication API
│   ├── game.ts               # Game API (phases, actions, snapshots)
│   └── room.ts               # Room API
├── components/
│   ├── Icons/
│   │   └── index.tsx          # SVG icon components
│   └── LoginModal/
│       └── index.tsx          # Login modal component
├── pages/
│   ├── index/                 # Home page
│   ├── login/                 # Login page
│   ├── register/              # Registration page
│   ├── room-list/             # Room listing
│   ├── room/                  # Room details
│   ├── profile/               # User profile
│   └── game/
│       ├── index.tsx          # Game entry
│       ├── play/
│       │   ├── index.tsx      # MAIN GAME COMPONENT ⭐
│       │   └── index.scss
│       └── role-reveal/
│           ├── index.tsx      # Role animation
│           └── index.scss
├── utils/
│   ├── request.ts             # HTTP request wrapper
│   ├── websocket.ts           # WebSocket manager
│   └── auth-guard.ts          # Auth route guard
└── assets/
    ├── audio/
    │   └── bgm.mp3
    ├── images/
    └── icons/
```

---

## 8. Key Technologies & Concepts

### State Management:
- React Hooks (useState, useCallback, useEffect, useRef)
- Local component state
- Taro storage (localStorage)

### Communication:
- HTTP requests (via Taro.request)
- WebSocket (real-time events)
- Storage sync (game state persistence)

### UI Components:
- Taro components (View, Text, Button, Image, Input, ScrollView)
- SVG icon components
- CSS Grid for circular player seating
- Modal overlays

### Game Logic:
- Phase-based turn system
- Role-specific actions
- Vote tracking and resolution
- Countdown timers with server-client sync
- Death and elimination management

---

## 9. Important Notes for Audio Integration

1. **Audio file exists:** `/Users/veennyyang/program/werewolf-game/packages/frontend/src/assets/audio/bgm.mp3`
2. **No current usage:** Not imported anywhere in the codebase
3. **Taro Audio Options:**
   - Use `Taro.createInnerAudioContext()` for H5 and mini-app compatibility
   - Or use HTML5 Audio API (works for H5 target)
4. **Recommended approach:** 
   - Create `/src/utils/audio.ts` utility module
   - Implement audio context management
   - Integrate with game phase changes for background music

---

## 10. Development Environment

### Local Dev Server:
- **Port:** 10086 (configured in `/config/index.js`)
- **API Proxy:** `/api` → `http://localhost:8080/api`
- **Running:** `npm run dev` (same as `npm run dev:h5`)

### Build Commands:
- `npm run dev:h5` - Dev H5 (web)
- `npm run build:h5` - Build H5
- `npm run dev:weapp` - Dev WeChat mini-app
- `npm run build:weapp` - Build WeChat mini-app

