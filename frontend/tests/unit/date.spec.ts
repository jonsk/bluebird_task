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

  it('toOffsetIso 输出 +08:00 偏移', () => {
    expect(toOffsetIso(new Date('2026-10-01T10:00:00Z'))).toMatch(/\+08:00$/)
  })
})
