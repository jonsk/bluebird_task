import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAppStore } from '@/stores/app'

describe('app store 主题功能（前端主题功能方案）', () => {
  beforeEach(() => {
    localStorage.clear()
    document.documentElement.removeAttribute('data-theme')
    setActivePinia(createPinia())
  })

  it('无持久化记录时默认 light-classic，且同步到 <html data-theme>', () => {
    const store = useAppStore()
    expect(store.theme).toBe('light-classic')
    expect(document.documentElement.dataset.theme).toBe('light-classic')
  })

  it('持久化的合法主题会在初始化时被读取并应用到 DOM', () => {
    localStorage.setItem('bb-theme', 'teal')
    const store = useAppStore()
    expect(store.theme).toBe('teal')
    expect(document.documentElement.dataset.theme).toBe('teal')
  })

  it('持久化的非法值回退到 light-classic（isThemeKey 校验）', () => {
    localStorage.setItem('bb-theme', 'hacky-theme')
    const store = useAppStore()
    expect(store.theme).toBe('light-classic')
  })

  it('setTheme 会更新 DOM、写回 localStorage', () => {
    const store = useAppStore()
    store.setTheme('dark')
    expect(store.theme).toBe('dark')
    expect(document.documentElement.dataset.theme).toBe('dark')
    expect(localStorage.getItem('bb-theme')).toBe('dark')

    store.setTheme('light-modern')
    expect(document.documentElement.dataset.theme).toBe('light-modern')
    expect(localStorage.getItem('bb-theme')).toBe('light-modern')
  })

  it('四个内置主题 key 与 tokens.css 的 data-theme 作用域一一对应', () => {
    const store = useAppStore()
    const keys = ['light-classic', 'light-modern', 'teal', 'dark'] as const
    for (const k of keys) {
      store.setTheme(k)
      expect(document.documentElement.dataset.theme).toBe(k)
    }
  })
})
