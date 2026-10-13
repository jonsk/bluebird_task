import { defineStore } from 'pinia'
import { ref } from 'vue'

/** 内置主题 key（务必与 tokens.css 的 :root[data-theme=…] 作用域一一对应）。 */
export type ThemeKey = 'light-classic' | 'light-modern' | 'teal' | 'dark'

const THEME_STORAGE_KEY = 'bb-theme'
const THEMES: readonly ThemeKey[] = ['light-classic', 'light-modern', 'teal', 'dark']

function isThemeKey(v: unknown): v is ThemeKey {
  return typeof v === 'string' && (THEMES as readonly string[]).includes(v)
}

/** 布局状态（03 §4.2）：左栏折叠、右栏抽屉、主题。 */
export const useAppStore = defineStore('app', () => {
  const leftCollapsed = ref(false)
  const detailVisible = ref(false)

  // 读取持久化主题；localStorage 不可用或非法值 → 回退默认经典浅色。
  let stored: unknown = null
  try {
    stored = localStorage.getItem(THEME_STORAGE_KEY)
  } catch {
    /* ignore */
  }
  const theme = ref<ThemeKey>(isThemeKey(stored) ? (stored as ThemeKey) : 'light-classic')

  /** 把主题应用到 <html data-theme>（index.html 内联脚本已预设，此处兜底同步）。 */
  function applyTheme(t: ThemeKey): void {
    document.documentElement.dataset.theme = t
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

  // store 首次构建即同步 DOM，保证与持久化一致。
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
