import { describe, expect, it } from 'vitest'
import { NOTE_EMOJIS } from '@/utils/noteEmojis'

describe('NOTE_EMOJIS', () => {
  it('提供足够多的常用表情且名称唯一', () => {
    expect(NOTE_EMOJIS.length).toBeGreaterThanOrEqual(200)
    const names = NOTE_EMOJIS.map((item) => item.name)
    expect(new Set(names).size).toBe(names.length)
  })

  it('带有中文名称 / 标签，支持中文搜索', () => {
    const hasChineseText = NOTE_EMOJIS.some(
      (item) =>
        Boolean(item.label?.trim()) ||
        (item.tags ?? []).some((tag) => /[\u4e00-\u9fa5]/.test(tag)),
    )
    expect(hasChineseText).toBe(true)
    expect(NOTE_EMOJIS.some((item) => item.tags?.includes('开心'))).toBe(true)
  })
})
