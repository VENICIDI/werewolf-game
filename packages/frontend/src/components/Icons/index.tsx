/**
 * 狼人杀 SVG 图标集 — 暗黑哥特风
 * 使用 Image + data:image/svg+xml 方案兼容小程序
 */
import { Image } from '@tarojs/components'

interface IconProps {
  size?: number
  color?: string
  className?: string
}

// 工具函数：将 SVG 字符串编码为 data URI
function svgToDataUri(svg: string): string {
  return `data:image/svg+xml,${encodeURIComponent(svg)}`
}

// ========== SVG 原始字符串 ==========

const svgs = {
  wolf: (color: string) => `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"><path d="M4 4L7 8L5 12L7 16L12 20L17 16L19 12L17 8L20 4L16 7L12 5L8 7L4 4Z" fill="${color}" opacity="0.2"/><path d="M12 5L8 7L4 4L7 8L5 12L7 16L12 20L17 16L19 12L17 8L20 4L16 7L12 5Z" stroke="${color}" stroke-width="1.5" stroke-linejoin="round"/><circle cx="9" cy="11" r="1.2" fill="${color}"/><circle cx="15" cy="11" r="1.2" fill="${color}"/><path d="M10 14.5C10 14.5 11 15.5 12 15.5C13 15.5 14 14.5 14 14.5" stroke="${color}" stroke-width="1" stroke-linecap="round"/></svg>`,

  player: (color: string) => `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"><circle cx="12" cy="8" r="4" stroke="${color}" stroke-width="1.5"/><path d="M4 20C4 16.69 7.58 14 12 14C16.42 14 20 16.69 20 20" stroke="${color}" stroke-width="1.5" stroke-linecap="round"/></svg>`,

  robot: (color: string) => `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"><rect x="5" y="8" width="14" height="12" rx="3" stroke="${color}" stroke-width="1.5"/><circle cx="9" cy="14" r="1.5" fill="${color}"/><circle cx="15" cy="14" r="1.5" fill="${color}"/><path d="M12 4V8" stroke="${color}" stroke-width="1.5" stroke-linecap="round"/><circle cx="12" cy="3" r="1.5" fill="${color}"/><path d="M10 17H14" stroke="${color}" stroke-width="1.5" stroke-linecap="round"/></svg>`,

  skull: (color: string) => `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"><path d="M12 3C7.58 3 4 6.58 4 11C4 14.5 6 17 8 18V20C8 20.55 8.45 21 9 21H15C15.55 21 16 20.55 16 20V18C18 17 20 14.5 20 11C20 6.58 16.42 3 12 3Z" stroke="${color}" stroke-width="1.5"/><circle cx="9" cy="11" r="2" fill="${color}"/><circle cx="15" cy="11" r="2" fill="${color}"/><path d="M10 16V19M12 16V19M14 16V19" stroke="${color}" stroke-width="1" stroke-linecap="round"/></svg>`,

  crown: (color: string) => `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"><path d="M3 18L5 8L9 12L12 6L15 12L19 8L21 18H3Z" fill="${color}" opacity="0.2"/><path d="M3 18L5 8L9 12L12 6L15 12L19 8L21 18H3Z" stroke="${color}" stroke-width="1.5" stroke-linejoin="round"/><path d="M4 20H20" stroke="${color}" stroke-width="1.5" stroke-linecap="round"/></svg>`,

  swords: (color: string) => `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"><path d="M6 18L18 6M18 6H14M18 6V10" stroke="${color}" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/><path d="M18 18L6 6M6 6H10M6 6V10" stroke="${color}" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>`,

  moon: (color: string) => `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"><path d="M21 12.79A9 9 0 1 1 11.21 3A7 7 0 0 0 21 12.79Z" fill="${color}" opacity="0.15"/><path d="M21 12.79A9 9 0 1 1 11.21 3A7 7 0 0 0 21 12.79Z" stroke="${color}" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg>`,

  sun: (color: string) => `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"><circle cx="12" cy="12" r="5" fill="${color}" opacity="0.2"/><circle cx="12" cy="12" r="5" stroke="${color}" stroke-width="1.5"/><path d="M12 2V4M12 20V22M4 12H2M22 12H20M5.64 5.64L7.05 7.05M16.95 16.95L18.36 18.36M5.64 18.36L7.05 16.95M16.95 7.05L18.36 5.64" stroke="${color}" stroke-width="1.5" stroke-linecap="round"/></svg>`,

  crystal: (color: string) => `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"><circle cx="12" cy="11" r="7" fill="${color}" opacity="0.15"/><circle cx="12" cy="11" r="7" stroke="${color}" stroke-width="1.5"/><path d="M8 20H16" stroke="${color}" stroke-width="1.5" stroke-linecap="round"/><path d="M9 18H15" stroke="${color}" stroke-width="1.5" stroke-linecap="round"/></svg>`,

  potion: (color: string) => `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"><path d="M9 3H15V7L18 12V19C18 20.1 17.1 21 16 21H8C6.9 21 6 20.1 6 19V12L9 7V3Z" fill="${color}" opacity="0.15"/><path d="M9 3H15V7L18 12V19C18 20.1 17.1 21 16 21H8C6.9 21 6 20.1 6 19V12L9 7V3Z" stroke="${color}" stroke-width="1.5" stroke-linejoin="round"/><path d="M8 3H16" stroke="${color}" stroke-width="1.5" stroke-linecap="round"/><circle cx="10" cy="15" r="1" fill="${color}"/><circle cx="14" cy="13" r="0.8" fill="${color}"/></svg>`,

  shield: (color: string) => `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"><path d="M12 3L4 7V12C4 16.42 7.4 20.54 12 21.5C16.6 20.54 20 16.42 20 12V7L12 3Z" fill="${color}" opacity="0.15"/><path d="M12 3L4 7V12C4 16.42 7.4 20.54 12 21.5C16.6 20.54 20 16.42 20 12V7L12 3Z" stroke="${color}" stroke-width="1.5" stroke-linejoin="round"/><path d="M9 12L11 14L15 10" stroke="${color}" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg>`,

  crosshair: (color: string) => `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"><circle cx="12" cy="12" r="8" stroke="${color}" stroke-width="1.5"/><circle cx="12" cy="12" r="3" stroke="${color}" stroke-width="1.5"/><path d="M12 2V6M12 18V22M2 12H6M18 12H22" stroke="${color}" stroke-width="1.5" stroke-linecap="round"/></svg>`,

  chat: (color: string) => `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"><path d="M21 15C21 15.55 20.55 16 20 16H6L2 20V4C2 3.45 2.45 3 3 3H20C20.55 3 21 3.45 21 4V15Z" fill="${color}" opacity="0.15"/><path d="M21 15C21 15.55 20.55 16 20 16H6L2 20V4C2 3.45 2.45 3 3 3H20C20.55 3 21 3.45 21 4V15Z" stroke="${color}" stroke-width="1.5" stroke-linejoin="round"/><path d="M7 9H17M7 12H13" stroke="${color}" stroke-width="1.2" stroke-linecap="round"/></svg>`,

  vote: (color: string) => `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"><rect x="4" y="10" width="16" height="11" rx="2" stroke="${color}" stroke-width="1.5"/><path d="M4 14H20" stroke="${color}" stroke-width="1.5"/><path d="M9 3H15L17 10H7L9 3Z" stroke="${color}" stroke-width="1.5" stroke-linejoin="round"/></svg>`,

  scale: (color: string) => `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"><path d="M12 3V19" stroke="${color}" stroke-width="1.5" stroke-linecap="round"/><path d="M5 7L12 5L19 7" stroke="${color}" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/><path d="M3 13L5 7L7 13C7 14.1 6.1 15 5 15C3.9 15 3 14.1 3 13Z" stroke="${color}" stroke-width="1.5" stroke-linejoin="round"/><path d="M17 13L19 7L21 13C21 14.1 20.1 15 19 15C17.9 15 17 14.1 17 13Z" stroke="${color}" stroke-width="1.5" stroke-linejoin="round"/><path d="M8 21H16" stroke="${color}" stroke-width="1.5" stroke-linecap="round"/></svg>`,

  timer: (color: string) => `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"><circle cx="12" cy="13" r="8" stroke="${color}" stroke-width="1.5"/><path d="M12 9V13L15 15" stroke="${color}" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/><path d="M10 2H14" stroke="${color}" stroke-width="1.5" stroke-linecap="round"/></svg>`,

  lock: (color: string) => `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"><rect x="5" y="11" width="14" height="10" rx="2" stroke="${color}" stroke-width="1.5"/><path d="M8 11V7C8 4.79 9.79 3 12 3C14.21 3 16 4.79 16 7V11" stroke="${color}" stroke-width="1.5" stroke-linecap="round"/><circle cx="12" cy="16" r="1.5" fill="${color}"/></svg>`,

  back: (color: string) => `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"><path d="M15 19L8 12L15 5" stroke="${color}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>`,

  plus: (color: string) => `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"><path d="M12 5V19M5 12H19" stroke="${color}" stroke-width="2" stroke-linecap="round"/></svg>`,

  check: (color: string) => `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"><path d="M5 13L9 17L19 7" stroke="${color}" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/></svg>`,
}

// ========== 图标组件（使用 Image + data URI） ==========

export function IconWolf({ size = 24, color = '#ff4444', className = '' }: IconProps) {
  return <Image className={className} style={{ width: `${size}px`, height: `${size}px` }} src={svgToDataUri(svgs.wolf(color))} />
}

export function IconPlayer({ size = 24, color = '#b0a090', className = '' }: IconProps) {
  return <Image className={className} style={{ width: `${size}px`, height: `${size}px` }} src={svgToDataUri(svgs.player(color))} />
}

export function IconRobot({ size = 24, color = '#60a0e0', className = '' }: IconProps) {
  return <Image className={className} style={{ width: `${size}px`, height: `${size}px` }} src={svgToDataUri(svgs.robot(color))} />
}

export function IconSkull({ size = 24, color = '#8a7a68', className = '' }: IconProps) {
  return <Image className={className} style={{ width: `${size}px`, height: `${size}px` }} src={svgToDataUri(svgs.skull(color))} />
}

export function IconCrown({ size = 24, color = '#e5c040', className = '' }: IconProps) {
  return <Image className={className} style={{ width: `${size}px`, height: `${size}px` }} src={svgToDataUri(svgs.crown(color))} />
}

export function IconSwords({ size = 24, color = '#c41a1a', className = '' }: IconProps) {
  return <Image className={className} style={{ width: `${size}px`, height: `${size}px` }} src={svgToDataUri(svgs.swords(color))} />
}

export function IconMoon({ size = 24, color = '#d4d4d4', className = '' }: IconProps) {
  return <Image className={className} style={{ width: `${size}px`, height: `${size}px` }} src={svgToDataUri(svgs.moon(color))} />
}

export function IconSun({ size = 24, color = '#e5c040', className = '' }: IconProps) {
  return <Image className={className} style={{ width: `${size}px`, height: `${size}px` }} src={svgToDataUri(svgs.sun(color))} />
}

export function IconCrystalBall({ size = 24, color = '#8a7dff', className = '' }: IconProps) {
  return <Image className={className} style={{ width: `${size}px`, height: `${size}px` }} src={svgToDataUri(svgs.crystal(color))} />
}

export function IconPotion({ size = 24, color = '#b84cff', className = '' }: IconProps) {
  return <Image className={className} style={{ width: `${size}px`, height: `${size}px` }} src={svgToDataUri(svgs.potion(color))} />
}

export function IconShield({ size = 24, color = '#e5c040', className = '' }: IconProps) {
  return <Image className={className} style={{ width: `${size}px`, height: `${size}px` }} src={svgToDataUri(svgs.shield(color))} />
}

export function IconCrosshair({ size = 24, color = '#ff9800', className = '' }: IconProps) {
  return <Image className={className} style={{ width: `${size}px`, height: `${size}px` }} src={svgToDataUri(svgs.crosshair(color))} />
}

export function IconChat({ size = 24, color = '#4caf50', className = '' }: IconProps) {
  return <Image className={className} style={{ width: `${size}px`, height: `${size}px` }} src={svgToDataUri(svgs.chat(color))} />
}

export function IconVote({ size = 24, color = '#e5c040', className = '' }: IconProps) {
  return <Image className={className} style={{ width: `${size}px`, height: `${size}px` }} src={svgToDataUri(svgs.vote(color))} />
}

export function IconScale({ size = 24, color = '#c41a1a', className = '' }: IconProps) {
  return <Image className={className} style={{ width: `${size}px`, height: `${size}px` }} src={svgToDataUri(svgs.scale(color))} />
}

export function IconTimer({ size = 24, color = '#ff2d2d', className = '' }: IconProps) {
  return <Image className={className} style={{ width: `${size}px`, height: `${size}px` }} src={svgToDataUri(svgs.timer(color))} />
}

export function IconLock({ size = 24, color = '#4caf50', className = '' }: IconProps) {
  return <Image className={className} style={{ width: `${size}px`, height: `${size}px` }} src={svgToDataUri(svgs.lock(color))} />
}

export function IconBack({ size = 24, color = '#e5c040', className = '' }: IconProps) {
  return <Image className={className} style={{ width: `${size}px`, height: `${size}px` }} src={svgToDataUri(svgs.back(color))} />
}

export function IconPlus({ size = 24, color = '#8a7a68', className = '' }: IconProps) {
  return <Image className={className} style={{ width: `${size}px`, height: `${size}px` }} src={svgToDataUri(svgs.plus(color))} />
}

export function IconCheck({ size = 24, color = '#4caf50', className = '' }: IconProps) {
  return <Image className={className} style={{ width: `${size}px`, height: `${size}px` }} src={svgToDataUri(svgs.check(color))} />
}
