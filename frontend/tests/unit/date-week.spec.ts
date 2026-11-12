import { describe, it, expect } from 'vitest'
import { shiftDay, weekHeadings, weekRangeLabel, weekStartOf } from '@/utils/date'

/**
 * 周口径（ISO 周：周一 ~ 周日）。周记录的所有日期计算都走这些函数：
 * 点周内任意一天归到周一、周标签跨年不省略年份、默认正文 7 个标题逐日取年月。
 */
describe('周口径工具', () => {
  it('周日归本周一，周一不动', () => {
    expect(weekStartOf('2026-11-08')).toBe('2026-11-02') // 周日
    expect(weekStartOf('2026-11-09')).toBe('2026-11-09') // 周一
    expect(weekStartOf('2026-11-12')).toBe('2026-11-09')
  })

  it('周标签两端都写完整日期，跨年周首尾年份不同', () => {
    expect(weekRangeLabel('2026-11-02')).toBe('2026-11-02 ~ 2026-11-08')
    expect(weekRangeLabel('2026-12-28')).toBe('2026-12-28 ~ 2027-01-03')
  })

  it('7 个日期标题逐日取自己的年月（跨年不会写错）', () => {
    expect(weekHeadings('2026-12-28')).toEqual([
      '2026年12月28日 周一',
      '2026年12月29日 周二',
      '2026年12月30日 周三',
      '2026年12月31日 周四',
      '2027年1月1日 周五',
      '2027年1月2日 周六',
      '2027年1月3日 周日',
    ])
  })

  it('shiftDay 按日历日加减（跨月/跨年）', () => {
    expect(shiftDay('2026-11-30', 1)).toBe('2026-12-01')
    expect(shiftDay('2027-01-01', -1)).toBe('2026-12-31')
    expect(shiftDay('2026-02-28', 1)).toBe('2026-03-01')
  })
})
