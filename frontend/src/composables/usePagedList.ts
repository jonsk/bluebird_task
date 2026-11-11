import { shallowReactive } from 'vue'

/** 分页响应（与后端 PageResult 对齐，只取列表与总数）。 */
export interface PageData<T> {
  list: T[]
  total: number
}

export interface PagedList<T> {
  rows: T[]
  total: number
  page: number
  size: number
  loading: boolean
  load: () => Promise<void>
  setPage: (page: number) => Promise<void>
}

/**
 * 分页列表状态机：收敛 rows/total/page/size/loading 与竞态取消。
 *
 * 只让最后一次请求写回，避免快速翻页或切换筛选时旧结果覆盖新条件。
 * 返回 shallowReactive 对象，模板里 `list.page` 可直接读写（无需 .value）。
 */
export function usePagedList<T>(
  fetcher: (page: number, size: number) => Promise<PageData<T>>,
  options: { size?: number; initialPage?: number } = {},
): PagedList<T> {
  const state = shallowReactive({
    rows: [] as T[],
    total: 0,
    page: options.initialPage ?? 1,
    size: options.size ?? 20,
    loading: false,
  })
  let seq = 0

  async function load(): Promise<void> {
    const current = ++seq
    state.loading = true
    try {
      const data = await fetcher(state.page, state.size)
      if (current !== seq) return
      state.rows = data.list
      state.total = data.total
    } finally {
      if (current === seq) state.loading = false
    }
  }

  async function setPage(p: number): Promise<void> {
    state.page = p
    await load()
  }

  return Object.assign(state, { load, setPage })
}
