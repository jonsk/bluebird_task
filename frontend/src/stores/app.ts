import { defineStore } from 'pinia'
import { ref } from 'vue'

/** 内置主题 key（务必与 tokens.css 的 :root[data-theme=…] 作用域一一对应）。 */
export type ThemeKey = 'light-classic' | 'dark-soft' | 'dark-vivid'

const THEME_STORAGE_KEY = 'bb-theme'
const THEMES: readonly ThemeKey[] = ['light-classic', 'dark-soft', 'dark-vivid']

/** 旧版本只有一个 dark 主题，存量用户在 localStorage 里可能还是 'dark'。 */
const LEGACY_THEME_ALIASES: Record<string, ThemeKey> = { dark: 'dark-soft' }

export function isDarkTheme(t: ThemeKey): boolean {
  return t !== 'light-classic'
}

function isThemeKey(v: unknown): v is ThemeKey {
  return typeof v === 'string' && (THEMES as readonly string[]).includes(v)
}

function normalizeTheme(v: unknown): ThemeKey | null {
  if (isThemeKey(v)) return v
  if (typeof v === 'string' && LEGACY_THEME_ALIASES[v]) return LEGACY_THEME_ALIASES[v]
  return null
}

/**
 * 把主题应用到 <html>：`data-theme` 驱动自有令牌，`dark` 类驱动 Element Plus 官方暗色变量
 * （element-plus/theme-chalk/dark/css-vars.css）。theme-init.js 首帧做同样的事。
 * 登录/回调页固定浅色，由 App.vue 在路由变化时用 'light-classic' 覆盖调用。
 */
export function applyThemeToDom(t: ThemeKey): void {
  const root = document.documentElement
  root.dataset.theme = t
  root.classList.toggle('dark', isDarkTheme(t))
}

/** 布局状态（03 §4.2）：左栏折叠、右栏抽屉、主题。 */
export const useAppStore = defineStore('app', () => {
  const leftCollapsed = ref(false)
  const detailVisible = ref(false)

  // 读取持久化主题；localStorage 不可用或非法值（含已下线的 light-modern / teal）→ 回退浅色。
  let stored: unknown = null
  try {
    stored = localStorage.getItem(THEME_STORAGE_KEY)
  } catch {
    /* ignore */
  }
  const restored = normalizeTheme(stored)
  const theme = ref<ThemeKey>(restored ?? 'light-classic')

  /** 把主题应用到 <html>（data-theme + dark 类）。 */
  function applyTheme(t: ThemeKey): void {
    applyThemeToDom(t)
  }

  function setTheme(t: ThemeKey): void {
    theme.value = t
    applyTheme(t)
    try {
      localStorage.setItem(THEME_STORAGE_KEY, t)
    } catch {
      /* ignore */
    }
  }

  // store 首次构建即同步 DOM，保证与持久化一致；顺手把已下线 / 旧别名的残留值改写成当前 key。
  if (stored !== theme.value) {
    try {
      localStorage.setItem(THEME_STORAGE_KEY, theme.value)
    } catch {
      /* ignore */
    }
  }
  applyTheme(theme.value)

  function toggleLeft(): void {
    leftCollapsed.value = !leftCollapsed.value
  }

  function openDetail(): void {
    detailVisible.value = true
  }

  function closeDetail(): void {
    detailVisible.value = false
  }

  return { leftCollapsed, detailVisible, theme, setTheme, toggleLeft, openDetail, closeDetail }
})
