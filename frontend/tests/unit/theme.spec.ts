import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { isDarkTheme, useAppStore } from '@/stores/app'

describe('app store 主题功能（浅色 / 深色·柔和 / 深色·鲜明）', () => {
  beforeEach(() => {
    localStorage.clear()
    document.documentElement.removeAttribute('data-theme')
    document.documentElement.classList.remove('dark')
    setActivePinia(createPinia())
  })

  it('无持久化记录时默认 light-classic，且同步到 <html data-theme>', () => {
    const store = useAppStore()
    expect(store.theme).toBe('light-classic')
    expect(document.documentElement.dataset.theme).toBe('light-classic')
    expect(document.documentElement.classList.contains('dark')).toBe(false)
  })

  it('持久化的合法主题会在初始化时被读取并应用到 DOM（含 Element Plus 的 dark 类）', () => {
    for (const key of ['dark-soft', 'dark-vivid'] as const) {
      localStorage.clear()
      setActivePinia(createPinia())
      localStorage.setItem('bb-theme', key)
      const store = useAppStore()
      expect(store.theme).toBe(key)
      expect(document.documentElement.dataset.theme).toBe(key)
      expect(document.documentElement.classList.contains('dark')).toBe(true)
    }
  })

  it('旧版 dark 主题迁移为 dark-soft 并写回存储', () => {
    localStorage.setItem('bb-theme', 'dark')
    const store = useAppStore()
    expect(store.theme).toBe('dark-soft')
    expect(document.documentElement.dataset.theme).toBe('dark-soft')
    expect(document.documentElement.classList.contains('dark')).toBe(true)
    expect(localStorage.getItem('bb-theme')).toBe('dark-soft')
  })

  it('持久化的非法值回退到 light-classic（isThemeKey 校验）', () => {
    localStorage.setItem('bb-theme', 'hacky-theme')
    const store = useAppStore()
    expect(store.theme).toBe('light-classic')
  })

  it('已下线主题（light-modern / teal）回退为 light-classic 并清掉残留值', () => {
    for (const retired of ['light-modern', 'teal']) {
      localStorage.clear()
      setActivePinia(createPinia())
      localStorage.setItem('bb-theme', retired)
      const store = useAppStore()
      expect(store.theme).toBe('light-classic')
      expect(document.documentElement.dataset.theme).toBe('light-classic')
      expect(localStorage.getItem('bb-theme')).toBe('light-classic')
    }
  })

  it('setTheme 会更新 DOM、写回 localStorage', () => {
    const store = useAppStore()
    store.setTheme('dark-vivid')
    expect(store.theme).toBe('dark-vivid')
    expect(document.documentElement.dataset.theme).toBe('dark-vivid')
    expect(document.documentElement.classList.contains('dark')).toBe(true)
    expect(localStorage.getItem('bb-theme')).toBe('dark-vivid')

    store.setTheme('light-classic')
    expect(document.documentElement.dataset.theme).toBe('light-classic')
    expect(document.documentElement.classList.contains('dark')).toBe(false)
    expect(localStorage.getItem('bb-theme')).toBe('light-classic')
  })

  it('三个内置主题 key 与 tokens.css 的 data-theme 作用域一一对应', () => {
    const store = useAppStore()
    const keys = ['light-classic', 'dark-soft', 'dark-vivid'] as const
    for (const k of keys) {
      store.setTheme(k)
      expect(document.documentElement.dataset.theme).toBe(k)
    }
  })

  it('isDarkTheme 只把两套深色主题认定为深色', () => {
    expect(isDarkTheme('light-classic')).toBe(false)
    expect(isDarkTheme('dark-soft')).toBe(true)
    expect(isDarkTheme('dark-vivid')).toBe(true)
  })
})
