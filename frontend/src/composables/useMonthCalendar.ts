import { ref, watch, onScopeDispose } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { calendar } from '@/api/calendar'
import { HttpError, BizError } from '@/api/http'
import type { NoteCalendarDay, NoteCalendarScope } from '@/types/note'

export interface MonthCalendarState {
  year: import('vue').Ref<number>
  month: import('vue').Ref<number>
  scope: import('vue').Ref<NoteCalendarScope>
  selected: import('vue').Ref<string | null>
  days: import('vue').Ref<NoteCalendarDay[]>
  loading: import('vue').Ref<boolean>
  error: import('vue').Ref<Error | null>
  forbidden: import('vue').Ref<boolean>
  setScope: (s: NoteCalendarScope) => void
  prevMonth: () => void
  nextMonth: () => void
  select: (date: string | null) => void
  reload: () => void
}

const VALID_SCOPES: NoteCalendarScope[] = ['mine', 'shared', 'explore', 'internal', 'subordinate', 'all']

/**
 * 全页日历状态（loader 可选；缺省用内置 note loader）。
 * scope 必须为合法日历 scope（不含 visible），非法值在 dev 警告并保持原值。
 */
export function useMonthCalendar(
  loader?: (year: number, month: number, scope?: string) => Promise<NoteCalendarDay[]>,
  opts?: {
    initialScope?: NoteCalendarScope
    initialYear?: number
    initialMonth?: number
    initialDate?: string | null
    syncRoute?: boolean
  },
): MonthCalendarState {
  const route = useRoute()
  const router = useRouter()
  const now = new Date()
  const syncRoute = opts?.syncRoute !== false

  const year = ref<number>(opts?.initialYear ?? (route.query.year ? Number(route.query.year) : now.getFullYear()))
  const month = ref<number>(opts?.initialMonth ?? (route.query.month ? Number(route.query.month) : now.getMonth() + 1))
  const scope = ref<NoteCalendarScope>(opts?.initialScope ?? (route.query.scope as NoteCalendarScope) ?? 'mine')
  const selected = ref<string | null>(opts?.initialDate ?? (typeof route.query.date === 'string' ? route.query.date : null))
  const days = ref<NoteCalendarDay[]>([])
  const loading = ref(false)
  const error = ref<Error | null>(null)
  const forbidden = ref(false)
  let seq = 0
  let applying = false

  async function load(): Promise<void> {
    const s = ++seq
    loading.value = true
    error.value = null
    forbidden.value = false
    try {
      const data = loader
        ? await loader(year.value, month.value, scope.value)
        : await calendar(year.value, month.value, scope.value)
      if (s !== seq) return
      days.value = data
    } catch (e) {
      if (s !== seq) return
      if ((e instanceof HttpError && e.status === 403) || (e instanceof BizError && e.code === 50016)) {
        forbidden.value = true
      }
      error.value = e as Error
    } finally {
      if (s === seq) loading.value = false
    }
  }

  function apply(): void {
    if (applying) return
    applying = true
    if (syncRoute) {
      void router
        .replace({
          path: route.path,
          query: {
            ...route.query,
            scope: scope.value,
            year: String(year.value),
            month: String(month.value),
            date: selected.value || undefined,
          },
        })
        .catch(() => {})
    }
    void load()
    applying = false
  }

  function setScope(s: NoteCalendarScope): void {
    if (!VALID_SCOPES.includes(s)) {
      if (import.meta.env.DEV) console.warn(`[useMonthCalendar] 非法 scope: ${s}，已忽略`)
      return
    }
    scope.value = s
    apply()
  }
  function prevMonth(): void {
    if (month.value === 1) {
      month.value = 12
      year.value -= 1
    } else {
      month.value -= 1
    }
    apply()
  }
  function nextMonth(): void {
    if (month.value === 12) {
      month.value = 1
      year.value += 1
    } else {
      month.value += 1
    }
    apply()
  }
  function select(date: string | null): void {
    selected.value = date
    apply()
  }
  function reload(): void {
    void load()
  }

  if (syncRoute) {
    watch(
      () => route.query,
      () => {
        if (applying) return
        const y = Number(route.query.year)
        const m = Number(route.query.month)
        const sc = route.query.scope as NoteCalendarScope
        const d = typeof route.query.date === 'string' ? route.query.date : null
        if (y) year.value = y
        if (m) month.value = m
        if (sc && VALID_SCOPES.includes(sc)) scope.value = sc
        selected.value = d
        void load()
      },
    )
  }

  void load()
  onScopeDispose(() => {
    seq++
  })

  return { year, month, scope, selected, days, loading, error, forbidden, setScope, prevMonth, nextMonth, select, reload }
}
