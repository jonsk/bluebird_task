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
    expect(tsvToTableJson('姓名\t数量')).toBeNull()
    expect(tsvToTableJson('第一行\n第二行')).toBeNull()
    expect(tsvToTableJson('姓名\t数量\n张三')).toBeNull()
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
