import { describe, it, expect, vi, beforeEach } from 'vitest'
import { effectScope, nextTick, type EffectScope } from 'vue'

/** useFileConfig：默认回退值、成功覆盖、失败回退且不阻塞上传。 */

const fetchFileConfig = vi.fn()
vi.mock('@/api/file', () => ({
  fetchFileConfig: (...args: unknown[]) => fetchFileConfig(...args),
}))

import { useFileConfig } from '@/composables/useFileConfig'

const FALLBACK = { maxSize: 50 * 1024 * 1024, allowedExt: [] }

let scopes: EffectScope[] = []

function mount() {
  const scope = effectScope()
  const r = scope.run(() => useFileConfig())!
  scopes.push(scope)
  return r
}

async function flush(times = 4): Promise<void> {
  for (let i = 0; i < times; i += 1) {
    await Promise.resolve()
    await nextTick()
  }
}

beforeEach(() => {
  fetchFileConfig.mockReset()
  scopes = []
})

describe('useFileConfig', () => {
  it('初始即为回退配置（上传入口不被阻塞）', () => {
    const r = mount()
    expect(r.config.value).toEqual(FALLBACK)
    expect(r.loading.value).toBe(false)
  })

  it('成功后用服务端配置覆盖', async () => {
    const r = mount()
    fetchFileConfig.mockResolvedValue({ maxSize: 10 * 1024 * 1024, allowedExt: ['txt', 'png'] })
    await r.load()
    expect(r.config.value).toEqual({ maxSize: 10 * 1024 * 1024, allowedExt: ['txt', 'png'] })
    expect(r.error.value).toBeNull()
  })

  it('失败回退常量并记录 error', async () => {
    const r = mount()
    fetchFileConfig.mockResolvedValue({ maxSize: 1, allowedExt: [] })
    await r.load()
    fetchFileConfig.mockRejectedValueOnce(new Error('配置接口 500'))
    await r.load()
    expect(r.config.value).toEqual(FALLBACK)
    expect(r.error.value).toBeInstanceOf(Error)
    expect(r.loading.value).toBe(false)
  })

  it('竞态：旧响应不覆盖新响应', async () => {
    const r = mount()
    let slow: (v: unknown) => void = () => {}
    fetchFileConfig.mockImplementationOnce(() => new Promise((res) => (slow = res)))
    const p1 = r.load()
    fetchFileConfig.mockResolvedValueOnce({ maxSize: 2, allowedExt: ['md'] })
    await r.load()
    slow({ maxSize: 3, allowedExt: ['old'] })
    await p1
    await flush()
    expect(r.config.value.maxSize).toBe(2)
  })
})
