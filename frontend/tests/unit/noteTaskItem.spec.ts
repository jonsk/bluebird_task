import { Editor } from '@tiptap/core'
import StarterKit from '@tiptap/starter-kit'
import TaskList from '@tiptap/extension-task-list'
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

  it('节点更新后也不会重建 span', () => {
    const editor = createEditor(TASK_CONTENT)

    editor.commands.setContent(
      '<ul data-type="taskList"><li data-type="taskItem" data-checked="true"><p>任务二</p></li></ul>',
    )

    const item = editor.view.dom.querySelector('li[data-checked]')
    const input = item?.querySelector('input[type="checkbox"]')

    expect(item).not.toBeNull()
    expect(item?.querySelector('label > span')).toBeNull()
    expect(item?.getAttribute('data-checked')).toBe('true')
    expect(input?.getAttribute('aria-label')).toContain('任务二')

    editor.destroy()
  })
})
