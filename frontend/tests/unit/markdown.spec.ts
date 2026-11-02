import { describe, expect, it } from 'vitest'
import { stripMarkdown } from '@/utils/markdown'

/**
 * 摘要清洗：后端 contentSummary 是原文前 200 码位（不做解析），
 * 展示层需要把 Markdown 语法符号去掉，只留可读文字。
 */
describe('stripMarkdown', () => {
  it('去掉标题、加粗、斜体、删除线标记', () => {
    expect(stripMarkdown('## 今日进展')).toBe('今日进展')
    expect(stripMarkdown('**阻塞**：等测试环境')).toBe('阻塞：等测试环境')
    expect(stripMarkdown('这是 *斜体* 与 ~~删除~~ 文本')).toBe('这是 斜体 与 删除 文本')
  })

  it('去掉列表、引用、分隔线标记', () => {
    expect(stripMarkdown('- 修复登录超时\n- 补充单测')).toBe('修复登录超时 补充单测')
    expect(stripMarkdown('1. 第一条\n2. 第二条')).toBe('第一条 第二条')
    expect(stripMarkdown('> 引用内容')).toBe('引用内容')
    expect(stripMarkdown('---')).toBe('')
  })

  it('围栏代码块整体移除，行内代码保留文字', () => {
    expect(stripMarkdown('前\n```java\nint a = 1;\n```\n后')).toBe('前 后')
    expect(stripMarkdown('执行 `pnpm test` 校验')).toBe('执行 pnpm test 校验')
  })

  it('链接与图片只保留可读文字', () => {
    expect(stripMarkdown('见 [需求文档](https://example.com/a)')).toBe('见 需求文档')
    expect(stripMarkdown('![截图](/notes/1/attachments/2/download)')).toBe('截图')
  })

  it('多行压缩成单行', () => {
    expect(stripMarkdown('# 标题\n\n第一段\n\n第二段')).toBe('标题 第一段 第二段')
  })

  it('空值与纯空白返回空串', () => {
    expect(stripMarkdown('')).toBe('')
    expect(stripMarkdown(null)).toBe('')
    expect(stripMarkdown(undefined)).toBe('')
    expect(stripMarkdown('   \n  ')).toBe('')
  })
})
