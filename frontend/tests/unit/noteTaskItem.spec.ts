import { Editor } from '@tiptap/core'
import StarterKit from '@tiptap/starter-kit'
import TaskList from '@tiptap/extension-task-list'
import { Fragment, Slice } from '@tiptap/pm/model'
import { NoteTaskItem } from '@/utils/noteTaskItem'

function createEditor(content: string) {
  return new Editor({
    element: document.createElement('div'),
    extensions: [StarterKit, TaskList, NoteTaskItem.configure({ nested: true })],
    content,
  })
}

const TASK_CONTENT =
  '<ul data-type="taskList"><li data-type="taskItem" data-checked="false"><p>任务</p></li></ul>'

const CREATED_AT_LABEL_RE = /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$/

function taskItems(editor: Editor): Element[] {
  return Array.from(editor.view.dom.querySelectorAll('li[data-checked]'))
}

describe('NoteTaskItem', () => {
  it('待办项 NodeView 不创建无障碍隐藏 span，名称保留在 aria-label 上', () => {
    const editor = createEditor(TASK_CONTENT)

    // NodeView 渲染的 li 不带 data-type，用 data-checked 定位。
    const item = editor.view.dom.querySelector('li[data-checked]')
    const input = item?.querySelector('input[type="checkbox"]')

    expect(item).not.toBeNull()
    expect(item?.querySelector('label > span')).toBeNull()
    expect(input?.getAttribute('aria-label')).toContain('任务')

    editor.destroy()
  })

  it('节点更新后也不会重建 span，且不补写历史待办的时间', () => {
    const editor = createEditor(TASK_CONTENT)

    editor.commands.setContent(
      '<ul data-type="taskList"><li data-type="taskItem" data-checked="true"><p>任务二</p></li></ul>',
    )

    const item = editor.view.dom.querySelector('li[data-checked]')
    const input = item?.querySelector('input[type="checkbox"]')

    expect(item).not.toBeNull()
    expect(item?.querySelector('label > span')).toBeNull()
    expect(item?.getAttribute('data-checked')).toBe('true')
    expect(item?.getAttribute('data-created-at')).toBeNull()
    expect(input?.getAttribute('aria-label')).toContain('任务二')

    editor.destroy()
  })

  it('编辑已带时间的待办不会重写原时间', () => {
    const editor = createEditor(
      '<ul data-type="taskList"><li data-type="taskItem" data-checked="false" data-created-at="2026-01-01T00:00:00.000Z" data-created-at-label="2026-01-01 00:00"><p>任务</p></li></ul>',
    )

    editor.commands.insertContentAt(5, '更新')
    expect(taskItems(editor)[0]?.getAttribute('data-created-at-label')).toBe('2026-01-01 00:00')

    editor.destroy()
  })

  it('工具栏/斜杠命令切换待办清单时写入创建时间', () => {
    const editor = createEditor('<p>新任务</p>')

    editor.commands.setTextSelection(1)
    // 与工具栏实际调用一致：focus -> toggleTaskList 在同一个 chain 里。
    expect(editor.chain().focus().toggleTaskList().run()).toBe(true)

    const item = taskItems(editor)[0]
    expect(item?.getAttribute('data-created-at')).toMatch(/^\d{4}-\d{2}-\d{2}T/)
    expect(item?.getAttribute('data-created-at-label')).toMatch(CREATED_AT_LABEL_RE)
    expect(editor.getHTML()).toContain('data-created-at-label=')

    // 保存后重新载入仍能解析出展示值。
    editor.commands.setContent(editor.getHTML())
    expect(taskItems(editor)[0]?.getAttribute('data-created-at-label')).toMatch(
      CREATED_AT_LABEL_RE,
    )

    editor.destroy()
  })

  it('多段选中后切换待办清单，每个新待办都写入时间', () => {
    const editor = createEditor('<p>第一项</p><p>第二项</p>')

    editor.commands.selectAll()
    expect(editor.commands.toggleTaskList()).toBe(true)

    const items = taskItems(editor)
    expect(items).toHaveLength(1)
    expect(items[0]?.querySelectorAll('div > p')).toHaveLength(2)
    expect(items[0]?.getAttribute('data-created-at-label')).toMatch(CREATED_AT_LABEL_RE)

    editor.destroy()
  })

  it('输入 [ ] 自动生成待办时写入时间', () => {
    const editor = createEditor('<p></p>')

    editor.commands.setTextSelection(1)
    const handled = editor.view.someProp('handleTextInput', (handler) =>
      handler(editor.view, 1, 1, '[ ] ', () => editor.state.tr),
    )
    expect(handled).toBe(true)

    const item = taskItems(editor)[0]
    expect(item?.getAttribute('data-created-at-label')).toMatch(CREATED_AT_LABEL_RE)

    editor.destroy()
  })

  it('回车拆出的新待办写入时间，原历史待办保持无时间', () => {
    const editor = createEditor(TASK_CONTENT)

    // 文档结构：taskList > taskItem > paragraph > text，文字结束位置为 5。
    editor.commands.setTextSelection(5)
    const handled = editor.view.someProp('handleKeyDown', (handler) =>
      handler(
        editor.view,
        new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }),
      ),
    )
    expect(handled).toBe(true)

    const items = taskItems(editor)
    expect(items).toHaveLength(2)
    expect(items[0]?.getAttribute('data-created-at-label')).toBeNull()
    expect(items[1]?.getAttribute('data-created-at-label')).toMatch(CREATED_AT_LABEL_RE)

    editor.destroy()
  })

  it('粘贴待办时重置时间，而不是沿用复制源的时间', () => {
    const editor = createEditor(TASK_CONTENT)
    const schema = editor.schema
    const source = schema.nodeFromJSON({
      type: 'taskList',
      content: [
        {
          type: 'taskItem',
          attrs: {
            checked: false,
            createdAt: '2026-01-01T00:00:00.000Z',
            createdAtLabel: '2026-01-01 00:00',
          },
          content: [{ type: 'paragraph', content: [{ type: 'text', text: '复制' }] }],
        },
      ],
    })
    const slice = new Slice(Fragment.from(source), 0, 0)
    const reset = editor.view.someProp('transformPasted', (handler) =>
      handler(slice, editor.view, false),
    )
    const item = reset?.content.firstChild?.firstChild

    expect(item?.attrs.createdAtLabel).not.toBe('2026-01-01 00:00')
    expect(item?.attrs.createdAtLabel).toMatch(CREATED_AT_LABEL_RE)

    editor.destroy()
  })
})
