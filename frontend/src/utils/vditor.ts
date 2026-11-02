/**
 * Vditor 运行期加载与主题参数的单一出口。
 *
 * 为什么需要这里：
 * - Vditor 的 lute 引擎、工具栏图标、i18n、KaTeX、mermaid 都是按 `cdn` 前缀**动态插 script/link**
 *   加载的，bundler 管不到；构建期由 `scripts/vendor-vditor.mjs` 拷到 `public/vditor`，
 *   `cdn: '/vditor'` 走同源，内网零外网请求。
 * - 编辑（IR）与只读预览（`Vditor.preview`）必须用**同一套**主题 / 高亮 / 公式参数，
 *   否则同一篇日志在列表详情与编辑态排版不一致。
 *
 * 本模块只被异步 import（编辑器、详情页），不要放进 `main.ts`，否则 Vditor 会进首屏 chunk。
 */
import type VditorType from 'vditor'

/** 同源静态资源根（对应 `frontend/public/vditor`，由 predev/prebuild 生成）。 */
export const VDITOR_CDN = '/vditor'

/** 编辑器主题名（Vditor 的 `theme` 选项）。 */
export function vditorTheme(dark: boolean): 'dark' | 'classic' {
  return dark ? 'dark' : 'classic'
}

/** 内容区主题名（`preview.theme.current` / `IPreviewOptions.theme.current`）。 */
export function vditorContentTheme(dark: boolean): 'dark' | 'light' {
  return dark ? 'dark' : 'light'
}

/** 代码高亮配置（编辑器 `preview.hljs` 与 `Vditor.preview` 共用）。 */
export function vditorHljs(dark: boolean): { style: string; lineNumber: boolean } {
  return { style: dark ? 'monokai' : 'github', lineNumber: false }
}

let loading: Promise<typeof VditorType> | null = null

/** 异步加载 Vditor 类与样式（幂等，失败后允许重试）。 */
export function loadVditor(): Promise<typeof VditorType> {
  loading ??= Promise.all([import('vditor'), import('vditor/dist/index.css')])
    .then(([mod]) => mod.default)
    .catch((e) => {
      loading = null
      throw e
    })
  return loading
}

export interface PreviewParams {
  dark: boolean
  /** 渲染完成后的回调（图片水合、滚动定位等）。 */
  after?: () => void
}

/**
 * 把 Markdown 渲染进指定容器（只读）。
 *
 * 与 `MdPreview` 的区别：Vditor 的预览是**命令式**的（自己插 DOM），因此调用方需在
 * 内容 / 主题变化时重新调用；返回的 Promise 在 lute 加载并渲染完成后 resolve。
 */
export async function renderVditorPreview(
  el: HTMLDivElement | null | undefined,
  markdown: string,
  { dark, after }: PreviewParams,
): Promise<void> {
  if (!el) return
  const Vditor = await loadVditor()
  if (!el.isConnected) return
  el.innerHTML = ''
  await Vditor.preview(el, markdown, {
    cdn: VDITOR_CDN,
    lang: 'zh_CN',
    mode: vditorContentTheme(dark),
    theme: { current: vditorContentTheme(dark) },
    hljs: vditorHljs(dark),
    math: { engine: 'KaTeX' },
    anchor: 0,
  })
  after?.()
}
