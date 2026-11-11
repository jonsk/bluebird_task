import { describe, expect, it } from 'vitest'
import { isNearDue, isOverdue, toOffsetIso } from '@/utils/date'

describe('date utils', () => {
  it('过判断：过去且未完成 → true', () => {
    expect(isOverdue('2000-01-01T00:00:00+08:00', false)).toBe(true)
    expect(isOverdue('2000-01-01T00:00:00+08:00', true)).toBe(false)
    expect(isOverdue(null)).toBe(false)
  })

  it('临期：24h 内 → true', () => {
    const soon = new Date(Date.now() + 3600_000).toISOString()
    expect(isNearDue(soon, false)).toBe(true)
    const far = new Date(Date.now() + 48 * 3600_000).toISOString()
    expect(isNearDue(far, false)).toBe(false)
  })

  it('toOffsetIso 精确值：墙钟串在任意时区都锚定 +08:00（不随运行环境时区漂移）', () => {
    // el-date-picker 选 18:00 产出的墙钟串，应稳定存为 18:00+08:00，不被时区换算偏移成 02:00
    expect(toOffsetIso('2026-09-20 18:00:00')).toBe('2026-09-20T18:00:00+08:00')
  })
})
