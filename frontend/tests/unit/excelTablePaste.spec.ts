import { describe, expect, it } from 'vitest'
import { htmlTableToJson, tableFromClipboard, tsvToTableJson } from '@/utils/excelTablePaste'

function cellText(cell: unknown): string {
  const node = cell as { content?: Array<{ content?: Array<{ text?: string }> }> }
  return node.content?.[0]?.content?.[0]?.text ?? ''
}

describe('excelTablePaste', () => {
  it('把 Excel 风格 HTML 表格转成 Tiptap JSON，并忽略 colgroup / 内联样式', () => {
    const table = htmlTableToJson(`
      <meta charset="utf-8">
      <table border="0" cellpadding="0" cellspacing="0" style="border-collapse:collapse">
        <colgroup><col width="64"></colgroup>
        <tr height="20">
          <td class="xl65" style="font-weight:700">姓名</td>
          <td>数量</td>
        </tr>
        <tr>
          <td>张三</td>
          <td>2</td>
        </tr>
      </table>
    `)

    expect(table?.type).toBe('table')
    expect(table?.content).toHaveLength(2)
    expect(cellText(table?.content?.[0]?.content?.[0])).toBe('姓名')
    expect(cellText(table?.content?.[1]?.content?.[1])).toBe('2')
  })

  it('保留单元格内的换行和合并单元格属性', () => {
    const table = htmlTableToJson(
      '<table><tr><td colspan="2" rowspan="2">第一行<br>第二行</td></tr></table>',
    )
    const cell = table?.content?.[0]?.content?.[0] as
      | { attrs?: Record<string, number>; content?: unknown[] }
      | undefined

    expect(cell?.attrs).toEqual({ colspan: 2, rowspan: 2 })
    expect(cell?.content).toHaveLength(2)
    expect(cellText(cell)).toBe('第一行')
  })

  it('没有 HTML 表格时返回 null', () => {
    expect(htmlTableToJson('<p>普通段落</p>')).toBeNull()
    expect(htmlTableToJson('')).toBeNull()
  })

  it('把制表符文本转成表格，并保留尾部空单元格', () => {
    const table = tsvToTableJson('姓名\t数量\n张三\t2\n李四\t')

    expect(table?.type).toBe('table')
    expect(table?.content).toHaveLength(3)
    expect(table?.content?.[1]?.content).toHaveLength(2)
    expect(cellText(table?.content?.[1]?.content?.[0])).toBe('张三')
    expect(cellText(table?.content?.[2]?.content?.[1])).toBe('')
  })

  it('单行、没有制表符或列数不完整的普通文本不会误判成表格', () => {
    expect(tsvToTableJson('第一行\n第二行')).toBeNull()
    expect(tsvToTableJson('姓名\t数量\n张三')).toBeNull()
  })

  it('单行但多列的制表符文本仍按表格处理', () => {
    const table = tsvToTableJson('姓名\t数量')
    expect(table?.type).toBe('table')
    expect(table?.content).toHaveLength(1)
    expect(table?.content?.[0]?.content).toHaveLength(2)
    expect(cellText(table?.content?.[0]?.content?.[1])).toBe('数量')
  })

  it('合并单元格按网格补齐，每行列数一致', () => {
    // 第 1 列被 rowspan=2 占掉，第二行只有 1 个 <td>（Excel 的真实形态）
    const table = htmlTableToJson(`
      <table>
        <tr><td rowspan="2">合并</td><td>A</td><td>B</td></tr>
        <tr><td>C</td><td>D</td></tr>
      </table>
    `)
    const rows = table?.content ?? []
    expect(rows).toHaveLength(2)
    // 第二行不能再补格子：合并列归上方单元格，列数仍为 3
    const secondRow = rows[1]?.content ?? []
    expect(secondRow).toHaveLength(2)
    expect(cellText(secondRow[0])).toBe('C')
    expect(cellText(secondRow[1])).toBe('D')

    const merged = rows[0]?.content?.[0] as { attrs?: Record<string, number> } | undefined
    expect(merged?.attrs).toEqual({ rowspan: 2 })
  })

  it('行尾缺格时补空单元格，保证行宽一致', () => {
    const table = htmlTableToJson(`
      <table>
        <tr><td>A</td><td>B</td></tr>
        <tr><td>C</td></tr>
      </table>
    `)
    const rows = table?.content ?? []
    expect(rows[0]?.content).toHaveLength(2)
    expect(rows[1]?.content).toHaveLength(2)
    expect(cellText(rows[1]?.content?.[1])).toBe('')
  })

  it('剪贴板同时有 HTML 和纯文本时优先使用 HTML 表格', () => {
    const data = {
      getData: (type: string) =>
        type === 'text/html'
          ? '<table><tr><td>来自 HTML</td></tr></table>'
          : '来自纯文本\t2',
    } as DataTransfer

    const table = tableFromClipboard(data)
    expect(cellText(table?.content?.[0]?.content?.[0])).toBe('来自 HTML')
  })
})
