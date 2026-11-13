/**
 * 日志正文图片的「带鉴权水合」。
 *
 * 背景：附件端点一律要 `Authorization: Bearer`，而 `<img src>` 不会带请求头。
 * 所以正文里写的是 `/notes/{id}/attachments/{aid}/preview`，渲染后由这里用 axios
 * 取 blob，再把 `src` 换成 object URL —— 与附件下载/预览走同一条鉴权链路，
 * 不引入「签名 URL」「匿名白名单」这类后端放行口子。
 *
 * 只处理日志附件的预览地址；外链图片原样保留。
 *
 * 两个必须守住的不变量：
 * 1. `stop()` 之后，页面上不能留下指向已 revoke 的 blob URL 的 `<img>`——否则容器被
 *    KeepAlive/v-show 复用时图片永远是裂的，且控制台只报 net error，很难归因；
 * 2. 父级重渲染把 `src` 写回预览地址时（v-html 重绘、Tiptap setContent），必须重新水合，
 *    所以观察器要监听 `src` 属性，而不只是子节点变化。
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

/** 单张图的水合状态：blob 地址 + 原始预览地址（stop 时还原用）。 */
interface Hydrated {
  url: string
  original: string
}

/**
 * 监听容器内新增的 `<img>`（含粘贴/上传后异步插入的正文图）与 `src` 变化，逐个补图。
 *
 * @param getRoot 返回渲染容器；容器本身可能稍后才挂载，故用函数而非元素
 */
export function watchMarkdownImages(getRoot: () => HTMLElement | null | undefined): ImageHydrator {
  const objectUrls = new Set<string>()
  // 用 Map 而非 WeakMap：stop() 需要遍历还原 src
  const hydrated = new Map<HTMLImageElement, Hydrated>()
  let stopped = false
  let observer: MutationObserver | null = null

  function release(img: HTMLImageElement): void {
    const state = hydrated.get(img)
    if (!state) return
    hydrated.delete(img)
    objectUrls.delete(state.url)
    URL.revokeObjectURL(state.url)
  }

  async function hydrate(img: HTMLImageElement): Promise<void> {
    const src = img.getAttribute('src') ?? ''
    // 自己刚写回的 blob 地址：属性变更回调会看到它，直接跳过，避免自激循环
    if (hydrated.get(img)?.url === src) return
    if (!isNotePreviewSrc(src)) {
      // 被父级改成了别的地址（或节点已复用）：释放旧的 blob，允许下次重新水合
      if (hydrated.has(img)) {
        release(img)
        delete img.dataset.bbImg
      }
      return
    }
    // 同一张图已在处理中（src 没变），不重复发请求
    if (img.dataset.bbImg === 'loading' && hydrated.get(img)?.original === src) return

    img.dataset.bbImg = 'loading'
    try {
      const blob = await getBlob(src)
      if (stopped) return
      release(img)
      const url = URL.createObjectURL(blob)
      objectUrls.add(url)
      // 先登记再改 src：观察器回调是异步的，但顺序写反会多触发一轮 hydrate
      hydrated.set(img, { url, original: src })
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
      observer.observe(root, {
        childList: true,
        subtree: true,
        // src 被外部改写（重渲染）时要重新水合
        attributes: true,
        attributeFilter: ['src'],
      })
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
      // 先还原 src 再 revoke：顺序反过来的话，中间态是「指向已失效 blob」的 img
      hydrated.forEach((state, img) => {
        if (img.getAttribute('src') === state.url) {
          img.setAttribute('src', state.original)
        }
        delete img.dataset.bbImg
      })
      hydrated.clear()
      objectUrls.forEach((url) => URL.revokeObjectURL(url))
      objectUrls.clear()
    },
  }
}
