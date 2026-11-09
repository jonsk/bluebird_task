import { describe, expect, it } from 'vitest'
import {
  isBlankHtml,
  isHtmlContent,
  markdownToHtml,
  plainTextFromHtml,
  toEditorHtml,
  visibleLength,
} from '@/utils/richText'
import { stripMarkdown } from '@/utils/markdown'

/** 历史 Markdown 与新 HTML 共存的格式判别 / 转换。 */
describe('richText', () => {
  it('区分 Tiptap HTML 与历史 Markdown', () => {
    expect(isHtmlContent('<h1>标题</h1>')).toBe(true)
    expect(isHtmlContent('<p>正文</p>')).toBe(true)
    expect(isHtmlContent('# 标题')).toBe(false)
    expect(isHtmlContent('普通正文')).toBe(false)
    expect(isHtmlContent('')).toBe(false)
  })

  it('历史 Markdown 转成 Tiptap 可解析的 HTML', () => {
    const html = markdownToHtml('## 今日进展\n\n- 完成接口\n- 补充测试')
    expect(html).toContain('<h2>今日进展</h2>')
    expect(html).toContain('<li>完成接口</li>')
    expect(toEditorHtml('**重点**')).toContain('<strong>重点</strong>')
    expect(toEditorHtml('')).toBe('<p></p>')
  })

  it('旧任务列表转成 TaskItem 结构', () => {
    const html = markdownToHtml('- [x] 已完成\n- [ ] 待处理')
    expect(html).toContain('data-type="taskList"')
    expect(html).toContain('data-type="taskItem" data-checked="true"')
    expect(html).toContain('data-type="taskItem" data-checked="false"')
  })

  it('HTML 转纯文本保留块级换行并解码实体', () => {
    expect(plainTextFromHtml('<h1>标题</h1><p>A&amp;B&nbsp;C</p>')).toBe('标题\nA&B C\n')
    expect(stripMarkdown('<h2>今日进展</h2><p><strong>完成</strong>接口</p>')).toBe(
      '今日进展 完成接口',
    )
  })

  it('空文档 / 可见字数按纯文本计算', () => {
    expect(isBlankHtml('<p></p><p> </p>')).toBe(true)
    expect(isBlankHtml('<p>内容</p>')).toBe(false)
    expect(visibleLength('<p>你好 <strong>世界</strong></p>')).toBe(5)
  })
})
