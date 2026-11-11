import { describe, expect, it, vi } from 'vitest'
import { usePagedList, type PageData } from '@/composables/usePagedList'

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

const pageData = <T>(list: T[], total = list.length): PageData<T> => ({ list, total })

describe('usePagedList', () => {
  it('默认第 1 页、每页 20 条，load 后写入 rows/total 并复位 loading', async () => {
    const fetcher = vi.fn().mockResolvedValue(pageData(['a', 'b'], 42))
    const list = usePagedList<string>(fetcher)

    expect(list.page).toBe(1)
    expect(list.size).toBe(20)

    const pending = list.load()
    expect(list.loading).toBe(true)
    await pending

    expect(list.loading).toBe(false)
    expect(list.rows).toEqual(['a', 'b'])
    expect(list.total).toBe(42)
    expect(fetcher).toHaveBeenCalledWith(1, 20)
  })

  it('支持自定义初始页与每页条数', async () => {
    const fetcher = vi.fn().mockResolvedValue(pageData([]))
    const list = usePagedList<string>(fetcher, { initialPage: 3, size: 50 })

    await list.load()

    expect(fetcher).toHaveBeenCalledWith(3, 50)
  })

  it('setPage 先更新页码再按新页码请求', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(pageData(['p1']))
      .mockResolvedValueOnce(pageData(['p2']))
    const list = usePagedList<string>(fetcher)

    await list.load()
    await list.setPage(2)

    expect(list.page).toBe(2)
    expect(fetcher).toHaveBeenLastCalledWith(2, 20)
    expect(list.rows).toEqual(['p2'])
  })

  it('竞态：先发的慢请求后返回时不覆盖新结果', async () => {
    const first = deferred<PageData<string>>()
    const second = deferred<PageData<string>>()
    const fetcher = vi.fn().mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)
    const list = usePagedList<string>(fetcher)

    const slow = list.load()
    const fast = list.load()

    second.resolve(pageData(['新结果'], 1))
    await fast
    first.resolve(pageData(['旧结果'], 9))
    await slow

    expect(list.rows).toEqual(['新结果'])
    expect(list.total).toBe(1)
    expect(list.loading).toBe(false)
  })

  it('请求失败时 loading 也会复位（不会卡在加载态）', async () => {
    const fetcher = vi.fn().mockRejectedValue(new Error('网络错误'))
    const list = usePagedList<string>(fetcher)

    await expect(list.load()).rejects.toThrow('网络错误')

    expect(list.loading).toBe(false)
    expect(list.rows).toEqual([])
  })

  it('空结果也正常写入（total 为 0）', async () => {
    const list = usePagedList<string>(vi.fn().mockResolvedValue(pageData([], 0)))

    await list.load()

    expect(list.rows).toEqual([])
    expect(list.total).toBe(0)
  })
})
