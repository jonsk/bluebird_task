import { calendar as noteCalendar } from './note'
import type { NoteCalendarDay, NoteCalendarScope } from '@/types/note'

/** 工作日志日历聚合（按年/月/作用域）。 */
export function calendar(year: number, month: number, scope: NoteCalendarScope = 'mine'): Promise<NoteCalendarDay[]> {
  return noteCalendar(year, month, scope)
}

export type { NoteCalendarDay, NoteCalendarScope }
