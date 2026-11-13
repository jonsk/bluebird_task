import { describe, expect, it } from 'vitest'
import { sanitizeRichTextHtml } from '@/utils/sanitize'
import { toEditorHtml } from '@/utils/richText'
import { formatFileSize, parseContentDisposition, safeRedirect, toErrorMessage } from '@/utils/format'

describe('sanitizeRichTextHtml', () => {
  it('剥离脚本标签、事件属性与危险协议', () => {
    const dirty =
      '<p onclick="x">正文</p><script>alert(1)</script>' +
      '<img src="x" onerror="alert(1)"><a href="javascript:alert(1)">点我</a>' +
      '<svg onload="alert(1)"></svg><iframe src="https://evil.com"></iframe>'
    const clean = sanitizeRichTextHtml(dirty)
    expect(clean).toContain('正文')
    expect(clean).not.toMatch(/<script|<svg|<iframe|onerror|onload|onclick|javascript:/i)
    expect(clean).not.toContain('href="javascript')
  })

  it('保留 Tiptap 依赖的 details / 任务列表 / 表格 / 高亮 / 图片 / 外链', () => {
    const legal =
      '<details data-type="details"><summary>明细</summary><div data-type="detailsContent"><p>内容</p></div></details>' +
      '<ul data-type="taskList"><li data-type="taskItem" data-checked="false">' +
      '<label><input type="checkbox"><span></span></label><div><p>待办</p></div></li></ul>' +
      '<table class="note-table"><tbody><tr><td style="color: rgb(255, 0, 0)"><p>高亮</p></td></tr></tbody></table>' +
      '<p><a href="https://example.com" target="_blank">外链</a></p>' +
      '<img src="/notes/1/attachments/2/preview" class="note-image">'
    const out = sanitizeRichTextHtml(legal)
    expect(out).toContain('data-type="details"')
    expect(out).toContain('data-checked="false"')
    expect(out).toContain('<table')
    expect(out).toContain('color:')
    expect(out).toContain('class="note-image"')
    expect(out).toContain('rel="noopener noreferrer"')
    expect(out).toContain('type="checkbox"')
  })

  it('拒绝 SVG / HTML 之类的高危 data URL，但保留位图 data URL', () => {
    expect(sanitizeRichTextHtml('<img src="data:text/html,<script>x</script>">')).not.toContain('src=')
    expect(sanitizeRichTextHtml('<img src="data:image/png;base64,iVBORw0KGgo=">')).toContain(
      'data:image/png;base64',
    )
  })

  it('toEditorHtml 对 HTML 正文过消毒', () => {
    expect(toEditorHtml('<p>hi</p><script>alert(1)</script>')).not.toContain('<script')
    expect(toEditorHtml('## 标题\n\n<script>alert(1)</script>')).not.toContain('<script')
    expect(toEditorHtml('')).toBe('<p></p>')
  })
})

describe('safeRedirect', () => {
  it('放行站内相对路径', () => {
    expect(safeRedirect('/index?a=1')).toBe('/index?a=1')
    expect(safeRedirect('/notes/12#anchor')).toBe('/notes/12#anchor')
  })

  it('拒绝协议相对 URL、外链、编码绕过与控制字符', () => {
    expect(safeRedirect('//evil.com')).toBe('/index')
    expect(safeRedirect('/\\evil.com')).toBe('/index')
    expect(safeRedirect('https://evil.com')).toBe('/index')
    expect(safeRedirect('javascript:alert(1)')).toBe('/index')
    expect(safeRedirect('/%2f%2fevil.com')).toBe('/index')
    expect(safeRedirect('/\n//evil.com')).toBe('/index')
    expect(safeRedirect(null)).toBe('/index')
    expect(safeRedirect(42, '/login')).toBe('/login')
  })
})

describe('format 小工具', () => {
  it('formatFileSize 按 B / KB / MB 分档', () => {
    expect(formatFileSize(512)).toBe('512 B')
    expect(formatFileSize(2048)).toBe('2.0 KB')
    expect(formatFileSize(5 * 1024 * 1024)).toBe('5.0 MB')
    expect(formatFileSize(0)).toBe('')
  })

  it('parseContentDisposition 优先 RFC5987 并回退普通 filename', () => {
    expect(parseContentDisposition("attachment; filename*=UTF-8''%E6%8A%A5%E5%91%8A.pdf")).toBe('报告.pdf')
    expect(parseContentDisposition('attachment; filename="a.txt"')).toBe('a.txt')
    expect(parseContentDisposition(undefined)).toBe('download')
  })

  it('toErrorMessage 不回显字符串类型的取消动作', () => {
    expect(toErrorMessage(new Error('出错了'))).toBe('出错了')
    expect(toErrorMessage('cancel', '删除失败')).toBe('删除失败')
    expect(toErrorMessage(undefined)).toBe('操作失败')
  })
})
