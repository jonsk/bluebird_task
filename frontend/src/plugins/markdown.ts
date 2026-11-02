/**
 * md-editor-v3 全局配置。
 *
 * 组件默认会在运行时从 `https://unpkg.com` 拉取 prettier / cropperjs / screenfull /
 * mermaid / katex / echarts。内网环境即使能出外网，把「编辑器能不能格式化」这类基础能力
 * 挂在第三方 CDN 上也不合适，因此这里改为**注入本地实例**：运行时零 CDN 请求。
 *
 * 三档装配（都幂等，可任意顺序重复调用，配置只增不减）：
 * - `setupMarkdown()`         只装 highlight.js（最轻，纯文本/代码日志够用）
 * - `setupMarkdownRender()`   追加 mermaid / katex / echarts（正文里真有公式或图才需要）
 * - `setupMarkdownEditor()`   追加 prettier / cropperjs / screenfull（写日志才需要）
 *
 * 注意：本模块**不要**在 main.ts 静态导入，否则会把 md-editor-v3 与 CodeMirror
 * 一起塞进首屏 chunk；只在异步加载编辑器/预览的分支里调用。
 */
import { config } from 'md-editor-v3'
import hljs from 'highlight.js/lib/core'
import bash from 'highlight.js/lib/languages/bash'
import css from 'highlight.js/lib/languages/css'
import java from 'highlight.js/lib/languages/java'
import javascript from 'highlight.js/lib/languages/javascript'
import json from 'highlight.js/lib/languages/json'
import markdown from 'highlight.js/lib/languages/markdown'
import python from 'highlight.js/lib/languages/python'
import sql from 'highlight.js/lib/languages/sql'
import typescript from 'highlight.js/lib/languages/typescript'
import xml from 'highlight.js/lib/languages/xml'
import yaml from 'highlight.js/lib/languages/yaml'
import 'highlight.js/styles/github.css'

const languages = {
  bash,
  css,
  java,
  javascript,
  json,
  markdown,
  python,
  sql,
  typescript,
  xml,
  yaml,
}

/**
 * 累积式配置：每次装配都往同一对象里补，再整体交给 `config()`。
 * 这样无论 `config()` 是深合并还是整体替换，都不会把先前装好的扩展顶掉。
 */
const extensions: Record<string, unknown> = {}

function apply(): void {
  config({ editorExtensions: extensions })
}

function setupHighlight(): void {
  if (extensions.highlight) return
  for (const [name, language] of Object.entries(languages)) {
    hljs.registerLanguage(name, language)
  }
  extensions.highlight = { instance: hljs }
  apply()
}

/** 只读预览的基础档：本地代码高亮。 */
export function setupMarkdown(): void {
  setupHighlight()
}

let renderReady: Promise<void> | null = null

/** 渲染档：正文里出现公式 / mermaid / echarts 时才调用（追加对应本地实例）。 */
export function setupMarkdownRender(): Promise<void> {
  setupHighlight()
  renderReady ??= (async () => {
    const [{ default: mermaid }, katex, echarts] = await Promise.all([
      import('mermaid'),
      import('katex'),
      import('echarts'),
      import('katex/dist/katex.min.css'),
    ])
    extensions.mermaid = { instance: mermaid }
    extensions.katex = { instance: katex }
    extensions.echarts = { instance: echarts }
    apply()
  })()
  return renderReady
}

let editorReady: Promise<void> | null = null

/** 编辑档：渲染档 + 格式化 / 图片裁剪 / 全屏。 */
export function setupMarkdownEditor(): Promise<void> {
  editorReady ??= (async () => {
    await setupMarkdownRender()
    const [{ default: prettier }, prettierMarkdown, { default: Cropper }, { default: screenfull }] =
      await Promise.all([
        import('prettier/standalone'),
        import('prettier/plugins/markdown'),
        import('cropperjs'),
        import('screenfull'),
        import('cropperjs/dist/cropper.min.css'),
      ])
    extensions.prettier = { prettierInstance: prettier, parserMarkdownInstance: prettierMarkdown }
    extensions.cropper = { instance: Cropper }
    extensions.screenfull = { instance: screenfull }
    apply()
  })()
  return editorReady
}
