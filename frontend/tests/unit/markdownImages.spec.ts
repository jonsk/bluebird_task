import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { watchMarkdownImages, isNotePreviewSrc } from '@/utils/markdownImages'

const PREVIEW = '/notes/1/attachments/2/preview'
const OK_BLOB = new Blob(['x'], { type: 'image/png' })

const getBlob = vi.hoisted(() => vi.fn())
vi.mock('@/api/http', () => ({ getBlob }))

/** 等 rAF + MutationObserver + promise 链全部跑完（一个 tick 不够）。 */
const flush = async (): Promise<void> => {
  for (let i = 0; i < 4; i++) await new Promise<void>((r) => setTimeout(r, 0))
}

describe('isNotePreviewSrc', () => {
  it('只认本站日志附件预览地址', () => {
    expect(isNotePreviewSrc(PREVIEW)).toBe(true)
    expect(isNotePreviewSrc(`/api/v1${PREVIEW}`)).toBe(true)
    expect(isNotePreviewSrc(`${PREVIEW}?v=2`)).toBe(true)
    expect(isNotePreviewSrc('https://evil.com/notes/1/attachments/2/preview')).toBe(false)
    expect(isNotePreviewSrc('/notes/1/attachments/2/download')).toBe(false)
  })
})

describe('watchMarkdownImages', () => {
  let root: HTMLDivElement
  let created: string[]
  let revoked: string[]

  beforeEach(() => {
    getBlob.mockReset()
    getBlob.mockResolvedValue(OK_BLOB)
    created = []
    revoked = []
    let seq = 0
    // jsdom 没有 URL.createObjectURL，直接装桩（vi.spyOn 会因为属性不存在而失败）
    Object.defineProperty(URL, 'createObjectURL', {
      configurable: true,
      writable: true,
      value: (() => {
        const url = `blob:mock/${++seq}`
        created.push(url)
        return url
      }) as unknown as typeof URL.createObjectURL,
    })
    Object.defineProperty(URL, 'revokeObjectURL', {
      configurable: true,
      writable: true,
      value: ((url: string) => {
        revoked.push(url)
      }) as unknown as typeof URL.revokeObjectURL,
    })
    root = document.createElement('div')
    document.body.appendChild(root)
  })

  afterEach(() => {
    document.body.innerHTML = ''
    vi.restoreAllMocks()
  })

  function addImg(src = PREVIEW): HTMLImageElement {
    const img = document.createElement('img')
    img.setAttribute('src', src)
    root.appendChild(img)
    return img
  }

  it('把预览地址换成 objectURL（含异步插入的图片）', async () => {
    const hydrator = watchMarkdownImages(() => root)
    await flush()

    const img = addImg()
    await flush()
    await flush()

    expect(getBlob).toHaveBeenCalledWith(PREVIEW)
    expect(img.getAttribute('src')).toBe('blob:mock/1')
    expect(img.dataset.bbImg).toBe('done')
    hydrator.stop()
  })

  it('容器晚于首帧才挂载时，图片仍会被水合', async () => {
    let late: HTMLElement | null = null
    const hydrator = watchMarkdownImages(() => late)
    await flush()

    late = root
    const img = addImg()
    await flush()
    await new Promise<void>((r) => setTimeout(r, 40))
    await flush()

    expect(getBlob).toHaveBeenCalledWith(PREVIEW)
    expect(img.getAttribute('src')).toBe('blob:mock/1')
    hydrator.stop()
  })

  it('外链图片不动，也不发请求', async () => {
    const hydrator = watchMarkdownImages(() => root)
    await flush()

    const img = addImg('https://example.com/a.png')
    await flush()
    await flush()

    expect(getBlob).not.toHaveBeenCalled()
    expect(img.getAttribute('src')).toBe('https://example.com/a.png')
    hydrator.stop()
  })

  it('取图失败时标记 error 并给出 alt 文案', async () => {
    getBlob.mockRejectedValue(new Error('401'))
    const hydrator = watchMarkdownImages(() => root)
    await flush()

    const img = addImg()
    await flush()
    await flush()

    expect(img.dataset.bbImg).toBe('error')
    expect(img.alt).toBe('图片加载失败')
    hydrator.stop()
  })

  describe('缺口 1：stop() 之后不留失效的 blob 地址', () => {
    it('stop() 释放 objectURL，并把 img.src 还原成原预览地址', async () => {
      const hydrator = watchMarkdownImages(() => root)
      await flush()

      const img = addImg()
      await flush()
      await flush()
      expect(img.getAttribute('src')).toBe('blob:mock/1')

      hydrator.stop()
      await flush()

      expect(revoked).toContain('blob:mock/1')
      // 关键：不能再指向已 revoke 的 blob URL，否则容器被复用/缓存的图片永远裂
      expect(img.getAttribute('src')).toBe(PREVIEW)
      expect(img.dataset.bbImg).toBeUndefined()
    })

    it('stop() 之后重新挂载能再次水合（blob 地址不残留）', async () => {
      const first = watchMarkdownImages(() => root)
      await flush()
      const img = addImg()
      await flush()
      await flush()
      first.stop()
      await flush()

      const second = watchMarkdownImages(() => root)
      await flush()
      await flush()
      expect(img.getAttribute('src')).toBe('blob:mock/2')
      second.stop()
    })
  })

  describe('缺口 2：src 被父级重渲染改回预览地址后要重新水合', () => {
    it('监听 src 属性变化，重新取图并替换', async () => {
      const hydrator = watchMarkdownImages(() => root)
      await flush()

      const img = addImg()
      await flush()
      await flush()
      expect(img.getAttribute('src')).toBe('blob:mock/1')

      // 模拟 v-html / Tiptap 重绘：src 被写回原始预览地址
      img.setAttribute('src', PREVIEW)
      await flush()
      await flush()

      expect(getBlob).toHaveBeenCalledTimes(2)
      expect(img.getAttribute('src')).toBe('blob:mock/2')
      hydrator.stop()
    })

    it('自身写回 blob 地址不会触发二次取图（避免自激循环）', async () => {
      const hydrator = watchMarkdownImages(() => root)
      await flush()

      addImg()
      await flush()
      await flush()
      await flush()

      expect(getBlob).toHaveBeenCalledTimes(1)
      hydrator.stop()
    })
  })
})
