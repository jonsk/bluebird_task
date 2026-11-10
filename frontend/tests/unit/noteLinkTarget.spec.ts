import { describe, expect, it } from 'vitest'
import { noteLinkHref } from '@/utils/noteLinkTarget'

function dom(html: string): HTMLElement {
  const root = document.createElement('div')
  root.innerHTML = html
  document.body.appendChild(root)
  return root
}

describe('noteLinkHref', () => {
  it('识别站内日志链接，点到链接内的文字也能返回地址', () => {
    const root = dom('<p>完成联调（<a href="/notes/369165902139392">#369</a>）</p>')
    const anchor = root.querySelector('a') as HTMLAnchorElement
    expect(noteLinkHref(anchor.firstChild, root)).toBe('/notes/369165902139392')
  })

  it('外部链接不接管', () => {
    const root = dom('<a href="https://example.com/x">x</a>')
    expect(noteLinkHref(root.querySelector('a'), root)).toBeNull()
  })

  it('非数字的 notes 路径不接管', () => {
    const root = dom('<a href="/notes/calendar">日历</a>')
    expect(noteLinkHref(root.querySelector('a'), root)).toBeNull()
  })

  it('点击普通文字返回 null，不影响光标定位', () => {
    const root = dom('<p>普通文字</p>')
    expect(noteLinkHref(root.querySelector('p'), root)).toBeNull()
  })

  it('链接不在编辑器容器内时忽略', () => {
    const outside = dom('<a href="/notes/1">#1</a>')
    const container = dom('<p>正文</p>')
    expect(noteLinkHref(outside.querySelector('a'), container)).toBeNull()
  })
})
