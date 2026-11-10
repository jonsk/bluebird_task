import type { JSONContent } from '@tiptap/core'

/**
 * Excel / WPS 复制表格后的剪贴板转换。
 *
 * 浏览器通常同时提供 `text/html`（带 <table>）和 `text/plain`（制表符分隔）。
 * 这里统一转成 Tiptap 的 JSON 结构，避免 Excel 的 colgroup / 内联样式 / 命名空间
 * 让 ProseMirror 的默认 HTML 解析失败；没有 HTML 表格时再退回 TSV。
 */

function paragraph(text: string): JSONContent {
  const value = text.trim()
  return value
    ? { type: 'paragraph', content: [{ type: 'text', text: value }] }
    : { type: 'paragraph' }
}

function cellContent(cell: HTMLTableCellElement): JSONContent[] {
  const clone = cell.cloneNode(true) as HTMLElement
  clone.querySelectorAll('br').forEach((br) => br.replaceWith('\n'))
  const lines = (clone.textContent ?? '')
    .replace(/\u00a0/g, ' ')
    .replace(/\r\n?/g, '\n')
    .split('\n')

  while (lines.length && !lines[0].trim()) lines.shift()
  while (lines.length && !lines[lines.length - 1].trim()) lines.pop()
  if (!lines.length) return [{ type: 'paragraph' }]
  return lines.map(paragraph)
}

function cellAttrs(cell: HTMLTableCellElement): Partial<JSONContent> {
  const attrs: Record<string, number> = {}
  if (cell.colSpan > 1) attrs.colspan = cell.colSpan
  if (cell.rowSpan > 1) attrs.rowspan = cell.rowSpan
  return Object.keys(attrs).length ? { attrs } : {}
}

/** 把一段 HTML 里的第一个 <table> 转成 Tiptap table JSON；没有表格返回 null。 */
export function htmlTableToJson(html: string): JSONContent | null {
  if (!html || !/<table[\s>]/i.test(html)) return null

  const doc = new DOMParser().parseFromString(html, 'text/html')
  const table = doc.querySelector('table')
  if (!table) return null

  const rows: JSONContent[] = []
  for (const row of Array.from(table.rows)) {
    const cells: JSONContent[] = []
    for (const cell of Array.from(row.cells)) {
      const type = cell.tagName === 'TH' ? 'tableHeader' : 'tableCell'
      const content =
        !cell.textContent?.trim() && !cell.querySelector('br')
          ? [{ type: 'paragraph' }]
          : cellContent(cell)
      cells.push({ type, ...cellAttrs(cell), content })
    }
    if (cells.length) rows.push({ type: 'tableRow', content: cells })
  }

  return rows.length ? { type: 'table', content: rows } : null
}

/** 把 Excel 复制出的制表符文本转成表格；不像表格时返回 null。 */
export function tsvToTableJson(text: string): JSONContent | null {
  if (!text || !text.includes('\t')) return null

  const lines = text.replace(/\r\n?/g, '\n').split('\n')
  while (lines.length && !lines[lines.length - 1].trim()) lines.pop()
  if (lines.length < 2) return null

  const rows = lines.map((line) => line.split('\t'))
  if (rows.some((row) => row.length < 2)) return null
  if (!rows.some((row) => row.some((cell) => cell.trim()))) return null

  const width = Math.max(...rows.map((row) => row.length))
  return {
    type: 'table',
    content: rows.map((row) => ({
      type: 'tableRow',
      content: Array.from({ length: width }, (_, index) => ({
        type: 'tableCell',
        content: [paragraph(row[index] ?? '')],
      })),
    })),
  }
}

/** 按优先级读取剪贴板中的 HTML 表格 / TSV 表格。 */
export function tableFromClipboard(data: DataTransfer | null): JSONContent | null {
  if (!data) return null
  return htmlTableToJson(data.getData('text/html')) ?? tsvToTableJson(data.getData('text/plain'))
}
