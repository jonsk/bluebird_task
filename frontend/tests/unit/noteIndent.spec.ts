import { afterEach, describe, expect, it } from 'vitest'
import { Editor } from '@tiptap/core'
import StarterKit from '@tiptap/starter-kit'
import { TableKit } from '@tiptap/extension-table'
import { NoteIndent } from '@/utils/noteIndent'

const editors: Editor[] = []

function createEditor(content: string): Editor {
  const element = document.createElement('div')
  document.body.appendChild(element)
  const editor = new Editor({
    element,
    extensions: [StarterKit, TableKit, NoteIndent],
    content,
  })
  editors.push(editor)
  return editor
}

function pressTab(editor: Editor, shift = false): KeyboardEvent {
  const event = new KeyboardEvent('keydown', {
    key: 'Tab',
    shiftKey: shift,
    bubbles: true,
    cancelable: true,
  })
  editor.view.dom.dispatchEvent(event)
  return event
}

/** 把光标放进第一段指定文字的块内。 */
function selectText(editor: Editor, text: string): number {
  let found = -1
  editor.state.doc.descendants((node, pos) => {
    if (found < 0 && node.isTextblock && node.textContent === text) found = pos + 1
  })
  expect(found).toBeGreaterThan(0)
  editor.commands.setTextSelection(found)
  return found
}

function indentOf(editor: Editor, type = 'paragraph'): number {
  return Number(editor.getAttributes(type).indent) || 0
}

afterEach(() => {
  editors.splice(0).forEach((editor) => editor.destroy())
  document.body.innerHTML = ''
})

describe('NoteIndent 快捷键', () => {
  it('段落 Tab 增加缩进，Shift-Tab 减少缩进', () => {
    const editor = createEditor('<p>正文</p>')
    selectText(editor, '正文')

    pressTab(editor)
    expect(indentOf(editor)).toBe(1)
    expect(editor.getHTML()).toContain('data-indent="1"')

    pressTab(editor)
    expect(indentOf(editor)).toBe(2)

    pressTab(editor, true)
    expect(indentOf(editor)).toBe(1)
  })

  it('标题同样支持 Tab / Shift-Tab', () => {
    const editor = createEditor('<h2>标题</h2>')
    selectText(editor, '标题')

    pressTab(editor)
    expect(indentOf(editor, 'heading')).toBe(1)
  })

  it('已在 0 级时 Shift-Tab 仍拦截默认行为，避免焦点移出编辑器', () => {
    const editor = createEditor('<p>正文</p>')
    selectText(editor, '正文')

    const event = pressTab(editor, true)
    expect(indentOf(editor)).toBe(0)
    expect(event.defaultPrevented).toBe(true)
  })

  it('列表项内 Tab 仍走 sinkListItem，不写段落缩进', () => {
    const editor = createEditor('<ul><li><p>一</p></li><li><p>二</p></li></ul>')
    selectText(editor, '二')

    pressTab(editor)

    expect(editor.getHTML()).not.toContain('data-indent')
    expect((editor.getHTML().match(/<ul/g) ?? []).length).toBe(2)
    expect(indentOf(editor)).toBe(0)
  })

  it('表格单元格内 Tab 仍切换到下一个单元格，不缩进', () => {
    const editor = createEditor(
      '<table><tbody><tr><td><p>A</p></td><td><p>B</p></td></tr></tbody></table>',
    )
    const start = selectText(editor, 'A')

    pressTab(editor)

    expect(editor.getHTML()).not.toContain('data-indent')
    expect(indentOf(editor)).toBe(0)
    expect(editor.state.selection.from).toBeGreaterThan(start)
  })
})
