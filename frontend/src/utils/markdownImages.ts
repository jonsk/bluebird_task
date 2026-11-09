/**
 * 日志正文图片的「带鉴权水合」。
 *
 * 背景：附件端点一律要 `Authorization: Bearer`，而 `<img src>` 不会带请求头。
 * 所以正文里写的是 `/notes/{id}/attachments/{aid}/preview`，渲染后由这里用 axios
 * 取 blob，再把 `src` 换成 object URL —— 与附件下载/预览走同一条鉴权链路，
 * 不引入「签名 URL」「匿名白名单」这类后端放行口子。
 *
 * 只处理日志附件的预览地址；外链图片原样保留。
 */

import { getBlob } from '@/api/http'

const NOTE_PREVIEW = /^(?:\/api\/v1)?\/notes\/\d+\/attachments\/\d+\/preview(?:[?#].*)?$/

/** 是否本站日志附件预览地址（相对路径，避免把用户粘贴的外链也拦下来）。 */
export function isNotePreviewSrc(src: string): boolean {
  return NOTE_PREVIEW.test(src)
}

export interface ImageHydrator {
  stop: () => void
}

/**
 * 监听容器内新增的 `<img>`（含 Markdown 转换后异步插入的正文图），逐个补图。
 *
 * @param getRoot 返回渲染容器；容器本身可能稍后才挂载，故用函数而非元素
 */
export function watchMarkdownImages(getRoot: () => HTMLElement | null | undefined): ImageHydrator {
  const objectUrls = new Set<string>()
  let stopped = false
  let observer: MutationObserver | null = null

  async function hydrate(img: HTMLImageElement): Promise<void> {
    const src = img.getAttribute('src') ?? ''
    if (!isNotePreviewSrc(src) || img.dataset.bbImg) return
    img.dataset.bbImg = 'loading'
    try {
      const blob = await getBlob(src)
      if (stopped) return
      const url = URL.createObjectURL(blob)
      objectUrls.add(url)
      img.src = url
      img.dataset.bbImg = 'done'
    } catch {
      if (stopped) return
      img.dataset.bbImg = 'error'
      img.alt = img.alt || '图片加载失败'
    }
  }

  function scan(): void {
    const root = getRoot()
    if (!root) return
    root.querySelectorAll('img').forEach((el) => void hydrate(el as HTMLImageElement))
  }

  function attach(): void {
    const root = getRoot()
    if (!root) return
    scan()
    if (!observer) {
      observer = new MutationObserver(() => scan())
      observer.observe(root, { childList: true, subtree: true })
    }
  }

  // 容器可能在首帧之后才渲染（异步组件 + 数据请求），用 rAF 兜住这一次延迟。
  const raf = requestAnimationFrame(attach)

  return {
    stop: () => {
      stopped = true
      cancelAnimationFrame(raf)
      observer?.disconnect()
      observer = null
      objectUrls.forEach((url) => URL.revokeObjectURL(url))
      objectUrls.clear()
    },
  }
}
