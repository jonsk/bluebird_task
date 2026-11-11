import { marked } from 'marked'
import { sanitizeRichTextHtml } from './sanitize'

/**
 * 富文本格式兼容工具。
 *
 * 日志正文从历史 Markdown 迁移到 Tiptap HTML 后，库里会同时存在两种形态：
 * - 新写入：Tiptap `getHTML()` 输出，必以块级标签开头（`<p>`/`<h1>`/`<ul>`…）；
 * - 历史数据：Markdown 源码。
 *
 * 这里用「是否以块级标签开头」做轻量判别，不新增数据库字段：
 * - 编辑/渲染前把历史 Markdown 转成 HTML；
 * - 用户真正编辑后，自动保存会把该篇日志落成 HTML（无需一次性迁移脚本）。
 */
const HTML_BLOCK_START =
  /^\s*<(?:p|h[1-6]|ul|ol|li|blockquote|pre|div|details|summary|table|thead|tbody|tr|td|th|hr|img|figure|section|article)\b/i

/** 是否为 Tiptap 产出的 HTML（或历史数据里直接写的 HTML 片段）。 */
export function isHtmlContent(content: string | null | undefined): boolean {
  if (!content) return false
  return HTML_BLOCK_START.test(content)
}

/**
 * 历史 Markdown → HTML。
 *
 * `marked` 只负责通用 GFM；任务列表输出会被改写成 Tiptap TaskItem 能识别的结构，
 * 避免旧日志里的 `- [ ]` 在迁移后变成带一个不可用 checkbox 的普通列表。
 */
export function markdownToHtml(markdown: string): string {
  const html = marked.parse(markdown, { async: false, gfm: true, breaks: false }) as string
  return normalizeTaskLists(html)
}

function normalizeTaskLists(html: string): string {
  return html
    .replace(/<ul class="contains-task-list">/g, '<ul data-type="taskList">')
    .replace(
      /<li(?:\s+class="task-list-item")?>\s*<input([^>]*)>([\s\S]*?)<\/li>/g,
      (_match, attrs: string, body: string) => {
        const checked = /\bchecked\b/i.test(attrs)
        return (
          `<li data-type="taskItem" data-checked="${checked}">` +
          `<label><input type="checkbox"${checked ? ' checked' : ''}><span></span></label>` +
          `<div><p>${body.trim()}</p></div>` +
          `</li>`
        )
      },
    )
    .replace(/<ul>\s*(<li data-type="taskItem")/g, '<ul data-type="taskList">$1')
}

/**
 * 任意日志正文（HTML 或 Markdown）转成编辑器可解析的 HTML。
 *
 * `isHtmlContent` 只是**格式判别**，不是安全边界：数据库里的 HTML 可能来自历史导入、
 * AI 生成或直接调 API 写入。因此两条分支的产物统一过一次 `sanitizeRichTextHtml`，
 * 与后端写入时的 RichTextGuard 组成双防线；后续即使更换渲染器也不会退化成存储型 XSS。
 */
export function toEditorHtml(content: string | null | undefined): string {
  const src = content ?? ''
  if (!src.trim()) return '<p></p>'
  const html = isHtmlContent(src) ? src : markdownToHtml(src)
  return sanitizeRichTextHtml(html)
}

/**
 * HTML → 纯文本。保留块级换行，供标题/摘要/“是否只有标题”判断使用。
 *
 * 不依赖 `innerText`（jsdom 未完整实现），手工把块级结束标签与 `<br>` 换成换行，
 * 再由浏览器的 DOMParser 解码实体、丢弃标签。
 */
export function plainTextFromHtml(html: string): string {
  if (!html) return ''
  const withBreaks = html
    .replace(/<(script|style)\b[\s\S]*?<\/\1>/gi, ' ')
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<\/(p|div|li|h[1-6]|blockquote|pre|details|summary|tr|td|th|ul|ol)>/gi, '\n')
    .replace(/<hr\s*\/?>/gi, '\n')
  const doc = new DOMParser().parseFromString(withBreaks, 'text/html')
  return (doc.body.textContent ?? '').replace(/\u00a0/g, ' ').replace(/\r\n?/g, '\n')
}

/** HTML 是否是“空文档”（只有空段落/空白）。 */
export function isBlankHtml(html: string): boolean {
  return plainTextFromHtml(html).trim().length === 0
}

/** 统计用户可见字符数（不含 HTML 标签与首尾空白）。 */
export function visibleLength(html: string): number {
  return [...plainTextFromHtml(html).trim()].length
}
