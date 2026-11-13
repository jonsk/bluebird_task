import { beforeEach, describe, expect, it } from 'vitest'
import {
  getLastViewedWeekStart,
  rememberViewedWeek,
  resetWeeklyNoteSession,
} from '@/utils/weeklyNoteSession'

describe('weeklyNoteSession', () => {
  beforeEach(() => resetWeeklyNoteSession())

  it('首次进入没有记忆', () => {
    expect(getLastViewedWeekStart()).toBeNull()
  })

  it('记住最后查看的周，并可重置', () => {
    rememberViewedWeek('2026-11-16')
    expect(getLastViewedWeekStart()).toBe('2026-11-16')

    rememberViewedWeek('2026-11-23')
    expect(getLastViewedWeekStart()).toBe('2026-11-23')

    resetWeeklyNoteSession()
    expect(getLastViewedWeekStart()).toBeNull()
  })
})
