import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { isDarkTheme, useAppStore } from '@/stores/app'

describe('app store 主题功能（浅色 3 套 / 深色 2 套）', () => {
  beforeEach(() => {
    localStorage.clear()
    document.documentElement.removeAttribute('data-theme')
    document.documentElement.removeAttribute('data-hl')
    document.documentElement.classList.remove('dark')
    setActivePinia(createPinia())
  })

  it('无持久化记录时默认 light-soft，且同步到 <html data-theme> / data-hl', () => {
    const store = useAppStore()
    expect(store.theme).toBe('light-soft')
    expect(document.documentElement.dataset.theme).toBe('light-soft')
    expect(document.documentElement.dataset.hl).toBe('soft')
    expect(document.documentElement.classList.contains('dark')).toBe(false)
  })

  it('持久化的合法主题会在初始化时被读取并应用到 DOM（深色同时挂 Element Plus 的 dark 类）', () => {
    for (const key of ['light-vivid', 'light-contrast', 'dark-soft', 'dark-vivid'] as const) {
      localStorage.clear()
      setActivePinia(createPinia())
      localStorage.setItem('bb-theme', key)
      const store = useAppStore()
      expect(store.theme).toBe(key)
      expect(document.documentElement.dataset.theme).toBe(key)
      expect(document.documentElement.classList.contains('dark')).toBe(isDarkTheme(key))
    }
  })

  it('旧 key 迁移：light-classic → light-soft，dark → dark-soft，并写回存储', () => {
    const legacy: Array<[string, string]> = [
      ['light-classic', 'light-soft'],
      ['dark', 'dark-soft'],
    ]
    for (const [oldKey, newKey] of legacy) {
      localStorage.clear()
      setActivePinia(createPinia())
      localStorage.setItem('bb-theme', oldKey)
      const store = useAppStore()
      expect(store.theme).toBe(newKey)
      expect(document.documentElement.dataset.theme).toBe(newKey)
      expect(localStorage.getItem('bb-theme')).toBe(newKey)
    }
  })

  it('持久化的非法值回退到 light-soft（isThemeKey 校验）', () => {
    localStorage.setItem('bb-theme', 'hacky-theme')
    const store = useAppStore()
    expect(store.theme).toBe('light-soft')
  })

  it('已下线主题（light-modern / teal）回退为 light-soft 并清掉残留值', () => {
    for (const retired of ['light-modern', 'teal']) {
      localStorage.clear()
      setActivePinia(createPinia())
      localStorage.setItem('bb-theme', retired)
      const store = useAppStore()
      expect(store.theme).toBe('light-soft')
      expect(document.documentElement.dataset.theme).toBe('light-soft')
      expect(localStorage.getItem('bb-theme')).toBe('light-soft')
    }
  })

  it('setTheme 会更新 DOM、写回 localStorage', () => {
    const store = useAppStore()
    store.setTheme('dark-vivid')
    expect(store.theme).toBe('dark-vivid')
    expect(document.documentElement.dataset.theme).toBe('dark-vivid')
    expect(document.documentElement.classList.contains('dark')).toBe(true)
    expect(localStorage.getItem('bb-theme')).toBe('dark-vivid')

    store.setTheme('light-vivid')
    expect(document.documentElement.dataset.theme).toBe('light-vivid')
    expect(document.documentElement.classList.contains('dark')).toBe(false)
    expect(localStorage.getItem('bb-theme')).toBe('light-vivid')
  })

  it('五个内置主题 key 与 tokens.css 的 data-theme 作用域一一对应', () => {
    const store = useAppStore()
    const keys = ['light-soft', 'light-vivid', 'light-contrast', 'dark-soft', 'dark-vivid'] as const
    for (const k of keys) {
      store.setTheme(k)
      expect(document.documentElement.dataset.theme).toBe(k)
    }
  })

  it('data-hl 只区分「柔和 / 鲜明」两族：硬朗与鲜明同族，柔和同族自成一档', () => {
    const store = useAppStore()
    const expectHl: Array<[string, string]> = [
      ['light-soft', 'soft'],
      ['dark-soft', 'soft'],
      ['light-vivid', 'vivid'],
      ['light-contrast', 'vivid'],
      ['dark-vivid', 'vivid'],
    ]
    for (const [theme, hl] of expectHl) {
      store.setTheme(theme as never)
      expect(document.documentElement.dataset.hl).toBe(hl)
    }
  })

  it('isDarkTheme 只把两套深色主题认定为深色', () => {
    expect(isDarkTheme('light-soft')).toBe(false)
    expect(isDarkTheme('light-vivid')).toBe(false)
    expect(isDarkTheme('light-contrast')).toBe(false)
    expect(isDarkTheme('dark-soft')).toBe(true)
    expect(isDarkTheme('dark-vivid')).toBe(true)
  })
})
