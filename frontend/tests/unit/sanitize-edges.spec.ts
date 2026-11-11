import { describe, expect, it } from 'vitest'
import { containsDangerousRichText, sanitizeRichTextHtml } from '@/utils/sanitize'

describe('containsDangerousRichText', () => {
  it('命中会被整段删除的标签（用于载入前提示数据丢失风险）', () => {
    expect(containsDangerousRichText('<p>正文</p><iframe src="https://x"></iframe>')).toBe(true)
    expect(containsDangerousRichText('<video><source src="a.mp4"></video>')).toBe(true)
    expect(containsDangerousRichText('<style>p{color:red}</style>')).toBe(true)
    expect(containsDangerousRichText('<p>这里只是讨论 &lt;iframe&gt; 标签</p>')).toBe(false)
  })

  it('空值与合法 Tiptap 内容都返回 false', () => {
    expect(containsDangerousRichText('')).toBe(false)
    expect(
      containsDangerousRichText('<details data-type="details"><summary>s</summary></details>'),
    ).toBe(false)
  })
})

describe('sanitizeRichTextHtml 边界', () => {
  it('危险标签连同内部文本一起删除（数据会丢失，故需要提示）', () => {
    const out = sanitizeRichTextHtml('<p>保留</p><script>var secret = 1</script>')
    expect(out).toContain('保留')
    expect(out).not.toContain('secret')
  })

  it('style 中的 url() / expression() / @import 被剥掉，普通样式保留', () => {
    const out = sanitizeRichTextHtml(
      '<p style="background: url(javascript:alert(1))">a</p>' +
        '<p style="width: expression(alert(1))">b</p>' +
        '<p style="color: rgb(0, 0, 255)">c</p>',
    )
    expect(out).not.toMatch(/url\(|expression\(/i)
    expect(out).toContain('color: rgb(0, 0, 255)')
  })

  it('srcdoc / formaction / ping 等属性无条件移除', () => {
    const out = sanitizeRichTextHtml(
      '<a href="/ok" ping="https://track" formaction="/x">链接</a><p srcdoc="<script>1</script>">x</p>',
    )
    expect(out).not.toContain('ping=')
    expect(out).not.toContain('formaction=')
    expect(out).not.toContain('srcdoc=')
  })

  it('大小写与制表符混淆的 javascript: 协议也被拦下', () => {
    const upper = sanitizeRichTextHtml('<a href="JaVaScRiPt:alert(1)">x</a>')
    const tabbed = sanitizeRichTextHtml('<a href="java\tscript:alert(1)">x</a>')
    expect(upper).not.toContain('href=')
    expect(tabbed).not.toContain('href=')
  })

  it('blob: 与 file: 不在白名单内（正文只允许站内路径 / http(s) / 图片 data URL）', () => {
    expect(sanitizeRichTextHtml('<img src="blob:http://localhost/a">')).not.toContain('src=')
    expect(sanitizeRichTextHtml('<img src="file:///C:/secret.png">')).not.toContain('src=')
  })

  it('data:image/svg+xml 被拒（SVG 可执行脚本）', () => {
    const out = sanitizeRichTextHtml('<img src="data:image/svg+xml;base64,PHN2Zz48L3N2Zz4=">')
    expect(out).not.toContain('data:image/svg')
  })

  it('target=_blank 只补一次 noopener，不重复堆 rel', () => {
    const once = sanitizeRichTextHtml('<a href="https://a.com" target="_blank" rel="noopener">x</a>')
    const twice = sanitizeRichTextHtml(once)
    expect(once).toContain('rel="noopener"')
    expect(twice.match(/noopener/g)?.length).toBe(1)
  })

  it('非 checkbox 的 input 被强制改回 checkbox（Tiptap 任务列表契约）', () => {
    const out = sanitizeRichTextHtml('<input type="password" value="x" name="pwd">')
    expect(out).toContain('type="checkbox"')
    expect(out).not.toContain('value=')
    expect(out).not.toContain('name=')
  })

  it('站内相对路径、锚点与 mailto/tel 链接保留', () => {
    const out = sanitizeRichTextHtml(
      '<a href="/notes/1">站内</a><a href="#top">锚点</a><a href="mailto:a@b.com">邮件</a><a href="tel:123">电话</a>',
    )
    expect(out).toContain('href="/notes/1"')
    expect(out).toContain('href="#top"')
    expect(out).toContain('mailto:a@b.com')
    expect(out).toContain('tel:123')
  })

  it('空输入返回空串且不抛错', () => {
    expect(sanitizeRichTextHtml('')).toBe('')
  })
})
