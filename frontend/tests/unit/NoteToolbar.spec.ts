import { describe, expect, it } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import NoteToolbar from '@/components/note/editor/NoteToolbar.vue'

/**
 * 工具栏的字体 / 字号 / 行距下拉框：当前值不在固定选项表里时不能显示空白
 * （老数据或 Word 粘贴会把任意 font-family 写进段落属性）。
 */

function chainApi(): Record<string, unknown> {
  const api: Record<string, unknown> = {}
  for (const m of ['focus', 'updateAttributes', 'setParagraph', 'setHeading', 'setTextAlign', 'toggleBold']) {
    api[m] = () => api
  }
  api.run = () => true
  return api
}

function editorWith(attrs: Record<string, string> = {}) {
  return {
    isActive: () => false,
    getAttributes: () => attrs,
    chain: () => chainApi(),
    can: () => ({ undo: () => false, redo: () => false }),
    state: { selection: { from: 0, to: 0 } },
    on: () => undefined,
    off: () => undefined,
  }
}

function mountToolbar(attrs: Record<string, string> = {}) {
  return shallowMount(NoteToolbar, {
    props: { editor: editorWith(attrs) as never, tick: 0 },
    global: { stubs: { NoteIcon: true, NoteOutline: true, NoteEmojiPicker: true } },
  })
}

function shownText(attrs: Record<string, string>, testId: string): string {
  const select = mountToolbar(attrs).find(`[data-test="${testId}"]`).element as HTMLSelectElement
  return select.selectedOptions[0]?.textContent?.trim() ?? ''
}

describe('NoteToolbar 下拉框显示', () => {
  it('未设置字体时显示占位项「字体」', () => {
    expect(shownText({}, 'note-font-family')).toBe('字体')
  })

  it('字体不在选项表里时显示当前字体名，而不是空白', () => {
    expect(shownText({ fontFamily: '"Microsoft YaHei", 微软雅黑, sans-serif' }, 'note-font-family')).toBe(
      'Microsoft YaHei',
    )
  })

  it('命中预设字体时仍显示预设名称', () => {
    expect(shownText({ fontFamily: '"Songti SC", SimSun, serif' }, 'note-font-family')).toBe('宋体')
  })

  it('字号不在选项表里时显示原始值', () => {
    expect(shownText({ fontSize: '11pt' }, 'note-font-size')).toBe('11pt')
  })

  it('未设置行距时显示默认档位', () => {
    expect(shownText({}, 'note-line-height')).toBe('默认 1.4')
  })
})
