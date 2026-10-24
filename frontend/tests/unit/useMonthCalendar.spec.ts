import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'

/* ---------- 路由 / 日历接口 mock ---------- */
const route = { path: '/notes/calendar', query: {} as Record<string, string>, meta: {} }
const router = { replace: vi.fn(() => Promise.resolve()) }
vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => router,
}))

const calendarApi = vi.fn()
vi.mock('@/api/calendar', () => ({ calendar: (...a: unknown[]) => calendarApi(...a) }))

import { useMonthCalendar } from '@/composables/useMonthCalendar'

/** 在组件生命周期内使用 composable（useMonthCalendar 依赖 onScopeDispose 与 watcher）。 */
function mountCalendar(opts?: Parameters<typeof useMonthCalendar>[1]) {
  let api!: ReturnType<typeof useMonthCalendar>
  const Comp = defineComponent({
    setup() {
      api = useMonthCalendar(undefined, opts)
      return () => h('div')
    },
  })
  const wrapper = mount(Comp, { global: { plugins: [ElementPlus] } })
  return { api, wrapper }
}

async function flush(times = 5): Promise<void> {
  for (let i = 0; i < times; i += 1) {
    await Promise.resolve()
    await new Promise((r) => setTimeout(r, 0))
  }
}

beforeEach(() => {
  setActivePinia(createPinia())
  calendarApi.mockReset()
  router.replace.mockClear()
  route.query = {}
})

afterEach(() => {
  vi.useRealTimers()
})

describe('useMonthCalendar · 月份与 scope', () => {
  it('prevMonth/nextMonth 跨年正确', async () => {
    calendarApi.mockResolvedValue([])
    const { api, wrapper } = mountCalendar({ initialYear: 2026, initialMonth: 1, syncRoute: false })
    await flush()
    api.prevMonth()
    expect(api.year.value).toBe(2025)
    expect(api.month.value).toBe(12)
    api.nextMonth()
    api.nextMonth()
    expect(api.year.value).toBe(2026)
    expect(api.month.value).toBe(2)
    wrapper.unmount()
  })

  it('setScope 触发重载并把 scope 写回 URL', async () => {
    calendarApi.mockResolvedValue([{ date: '2026-09-28', count: 2 }])
    const { api, wrapper } = mountCalendar({ initialYear: 2026, initialMonth: 9, initialScope: 'mine' })
    await flush()
    api.setScope('internal')
    await flush()
    expect(calendarApi).toHaveBeenLastCalledWith(2026, 9, 'internal')
    expect(router.replace).toHaveBeenLastCalledWith(
      expect.objectContaining({ query: expect.objectContaining({ scope: 'internal' }) }),
    )
    wrapper.unmount()
  })

  it('不允许 visible 作为日历 scope（保持原值）', async () => {
    calendarApi.mockResolvedValue([])
    const { api, wrapper } = mountCalendar({ initialYear: 2026, initialMonth: 9, syncRoute: false })
    await flush()
    api.setScope('visible' as never)
    expect(api.scope.value).toBe('mine')
    wrapper.unmount()
  })

  it('403 → forbidden 置位（页面优先渲染"不可用"空态而非重试）', async () => {
    const { HttpError } = await import('@/api/http')
    calendarApi.mockRejectedValue(new HttpError(403, 10003, '无权限'))
    const { api, wrapper } = mountCalendar({ initialYear: 2026, initialMonth: 9, syncRoute: false })
    await flush()
    expect(api.forbidden.value).toBe(true)
    expect(api.days.value).toEqual([])
    wrapper.unmount()
  })
})