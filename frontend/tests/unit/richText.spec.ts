import { describe, expect, it } from 'vitest'
import {
  isBlankHtml,
  plainSummary,
  plainTextFromHtml,
  toEditorHtml,
  visibleLength,
} from '@/utils/richText'

/** 正文一律是 HTML：只做消毒与纯文本化，不再兼容历史 Markdown。 */
describe('richText', () => {
  it('toEditorHtml 消毒 HTML，空内容落成空段落', () => {
    expect(toEditorHtml('<p>正文</p>')).toContain('<p>正文</p>')
    expect(toEditorHtml('<p>hi</p><script>alert(1)</script>')).not.toContain('<script')
    expect(toEditorHtml('')).toBe('<p></p>')
    expect(toEditorHtml(null)).toBe('<p></p>')
  })

  it('HTML 转纯文本保留块级换行并解码实体', () => {
    expect(plainTextFromHtml('<h1>标题</h1><p>A&amp;B&nbsp;C</p>')).toBe('标题\nA&B C\n')
    expect(plainSummary('<h2>今日进展</h2><p><strong>完成</strong>接口</p>')).toBe(
      '今日进展 完成接口',
    )
  })

  it('空文档 / 可见字数按纯文本计算', () => {
    expect(isBlankHtml('<p></p><p> </p>')).toBe(true)
    expect(isBlankHtml('<p>内容</p>')).toBe(false)
    expect(visibleLength('<p>你好 <strong>世界</strong></p>')).toBe(5)
  })
})
