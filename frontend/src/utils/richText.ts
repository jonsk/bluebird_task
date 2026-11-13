import { sanitizeRichTextHtml } from './sanitize'

/**
 * 富文本工具。
 *
 * 日志正文与周报 `contentHtml` 一律落库为 Tiptap `getHTML()` 产出的 HTML；
 * 历史 Markdown 兼容层已移除（原因与影响见 docs/architecture/tiptap-editor.md §9.4），
 * 这里不再做格式判别，也不再引入 Markdown 转换器。
 */

/**
 * 任意日志正文转成编辑器可解析的 HTML。
 *
 * 数据库里的 HTML 可能来自历史导入、AI 生成或直接调 API 写入，
 * 因此统一过一遍 `sanitizeRichTextHtml`，与后端写入时的 RichTextGuard 组成双防线；
 * 后续即使更换渲染器也不会退化成存储型 XSS。
 */
export function toEditorHtml(content: string | null | undefined): string {
  const src = content ?? ''
  if (!src.trim()) return '<p></p>'
  return sanitizeRichTextHtml(src)
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

/** 把正文压成单行摘要文本，供列表卡片 / 右栏小列表 / 详情页标题使用。 */
export function plainSummary(content?: string | null): string {
  if (!content) return ''
  return plainTextFromHtml(content).replace(/\s+/g, ' ').trim()
}

/** HTML 是否是“空文档”（只有空段落/空白）。 */
export function isBlankHtml(html: string): boolean {
  return plainTextFromHtml(html).trim().length === 0
}

/** 统计用户可见字符数（不含 HTML 标签与首尾空白）。 */
export function visibleLength(html: string): number {
  return [...plainTextFromHtml(html).trim()].length
}
