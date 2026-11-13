import type { JSONContent } from '@tiptap/core'

/**
 * Excel / WPS 复制表格后的剪贴板转换。
 *
 * 浏览器通常同时提供 `text/html`（带 <table>）和 `text/plain`（制表符分隔）；
 * Excel 还会额外塞一张该区域的 PNG 快照到剪贴板的 files 里——所以调用方必须
 * **先判表格、再判图片**，否则表格永远被当成图片粘进来。
 *
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

/** 带合并的单元格在 Tiptap 里必须靠 colspan / rowspan 表达，缺一个都会被 schema 拒绝。 */
function cellJson(cell: HTMLTableCellElement): JSONContent {
  const colspan = Math.max(1, cell.colSpan || 1)
  const rowspan = Math.max(1, cell.rowSpan || 1)
  const attrs: Record<string, number> = {}
  if (colspan > 1) attrs.colspan = colspan
  if (rowspan > 1) attrs.rowspan = rowspan
  const content =
    !cell.textContent?.trim() && !cell.querySelector('br')
      ? [{ type: 'paragraph' }]
      : cellContent(cell)
  return {
    type: cell.tagName === 'TH' ? 'tableHeader' : 'tableCell',
    ...(Object.keys(attrs).length ? { attrs } : {}),
    content,
  }
}

interface PlacedCell {
  json: JSONContent
  col: number
  colspan: number
}

const slotKey = (row: number, col: number): string => `${row}:${col}`

/**
 * 把 <table> 摊平成网格再还原成 Tiptap 表格。
 *
 * Excel 的合并区域会让后续行少若干个 <td>（被 rowspan 占掉的格子不重复输出），
 * 直接按 `<tr>` 里的单元格数量生成 JSON 会让每行列数不一致，插入时被 schema 拒绝、
 * 表现就是「粘贴完全没反应」。这里按占用矩阵补齐被 rowspan 覆盖的空位。
 */
function gridToJson(table: HTMLTableElement): JSONContent[] {
  const rowEls = Array.from(table.rows)
  const occupied = new Set<string>()
  const placedRows: PlacedCell[][] = []

  for (let r = 0; r < rowEls.length; r++) {
    let col = 0
    const placed: PlacedCell[] = []
    for (const cell of Array.from(rowEls[r].cells)) {
      while (occupied.has(slotKey(r, col))) col += 1
      const colspan = Math.max(1, cell.colSpan || 1)
      const rowspan = Math.max(1, cell.rowSpan || 1)
      for (let dr = 1; dr < rowspan; dr += 1) {
        for (let dc = 0; dc < colspan; dc += 1) occupied.add(slotKey(r + dr, col + dc))
      }
      placed.push({ json: cellJson(cell), col, colspan })
      col += colspan
    }
    placedRows.push(placed)
  }

  // 表格宽度 = 所有行里被占用到的最大列号 + 1（含 rowspan 向下延伸出来的格子）
  let width = 0
  for (let r = 0; r < placedRows.length; r += 1) {
    for (const item of placedRows[r]) width = Math.max(width, item.col + item.colspan)
    for (let c = 0; c < width + 1; c += 1) {
      if (occupied.has(slotKey(r, c))) width = Math.max(width, c + 1)
    }
  }

  return placedRows.map((row, r) => {
    const cells = row.map((item) => item.json)
    let col = row.reduce((max, item) => Math.max(max, item.col + item.colspan), 0)
    while (col < width) {
      // 被上方 rowspan 覆盖的列不能再补格子，否则列数会翻倍
      if (!occupied.has(slotKey(r, col))) {
        cells.push({ type: 'tableCell', content: [{ type: 'paragraph' }] })
      }
      col += 1
    }
    return { type: 'tableRow', content: cells }
  })
}

/** 把一段 HTML 里的第一个 <table> 转成 Tiptap table JSON；没有表格返回 null。 */
export function htmlTableToJson(html: string): JSONContent | null {
  if (!html || !/<table[\s>]/i.test(html)) return null

  const doc = new DOMParser().parseFromString(html, 'text/html')
  const table = doc.querySelector('table')
  if (!table) return null

  const rows = gridToJson(table)
  return rows.length ? { type: 'table', content: rows } : null
}

/** 把 Excel 复制出的制表符文本转成表格；不像表格时返回 null。 */
export function tsvToTableJson(text: string): JSONContent | null {
  if (!text || !text.includes('\t')) return null

  const lines = text.replace(/\r\n?/g, '\n').split('\n')
  while (lines.length && !lines[lines.length - 1].trim()) lines.pop()
  if (!lines.length) return null

  const rows = lines.map((line) => line.split('\t'))
  // 每行至少两列才像「从表格复制的」；单行单列的普通文本仍然按纯文本处理
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

/**
 * 表格之外的「可见内容」判定：文本、图片、分割线等算内容；
 * 空的包裹层（如 Tiptap 的 `div.tableWrapper`）不算，避免误伤。
 */
function hasVisibleContent(node: Node): boolean {
  if (node.nodeType === Node.TEXT_NODE) return Boolean(node.textContent?.trim())
  if (node.nodeType !== Node.ELEMENT_NODE) return false

  const element = node as HTMLElement
  if (element.tagName === 'TABLE') return false
  if (element.tagName === 'BR') return false
  if (element.tagName === 'IMG' || element.tagName === 'HR') return true

  return Array.from(element.childNodes).some(hasVisibleContent)
}

/**
 * 剪贴板 HTML 是否是「整段内容就是一张表」——即 Excel / WPS 复制单元格区域的形态。
 *
 * 必须区分两种粘贴：
 * - Excel 区域：`body` 里只有一张 `<table>`，其余是 `<meta>`、样式、Office 命名空间和注释；
 * - 文档：表格只是正文的一部分，还有段落、列表、图片等。
 * 后者必须交回 ProseMirror 原生解析，否则转换器只取第一张表，整篇内容会只剩那张表。
 */
export function isHtmlOnlyTable(html: string): boolean {
  if (!html || !/<table[\s>]/i.test(html)) return false

  const doc = new DOMParser().parseFromString(html, 'text/html')
  const tables = Array.from(doc.body.querySelectorAll('table'))
  if (tables.length !== 1) return false
  if (!tables[0].rows.length) return false

  return !Array.from(doc.body.childNodes).some(hasVisibleContent)
}

/** 剪贴板 HTML 里是否含表格：用于阻止「正文 + 表格」被 Excel 的区域快照抢走。 */
export function clipboardHasHtmlTable(data: DataTransfer | null): boolean {
  return /<table[\s>]/i.test(data?.getData('text/html') ?? '')
}

/**
 * 按优先级读取剪贴板中的表格，**仅在「内容就是一张表」时接管**。
 *
 * - HTML 里还夹着正文、列表或图片 → 返回 `null`，交给 ProseMirror 原生解析；
 * - 没有 `text/html` 时才用 TSV 兜底，避免把带制表符的代码误判成表格。
 */
export function tableFromClipboard(data: DataTransfer | null): JSONContent | null {
  if (!data) return null

  const html = data.getData('text/html')
  if (html) {
    return isHtmlOnlyTable(html) ? htmlTableToJson(html) : null
  }

  return tsvToTableJson(data.getData('text/plain'))
}
