import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, shallowMount, type VueWrapper } from '@vue/test-utils'

/**
 * NoteEditor 消费后端下发的 `fieldPermissions` 决定正文可编辑性，并负责自动保存。
 *
 * 真实 Tiptap 在 jsdom 里依赖 DOM 选区/布局，单测只验证组件逻辑：
 * 用 `useEditor` 桩替换编辑实例，记录 setContent / onUpdate / setEditable，
 * 断言回显、权限、自动保存载荷与“只有日期标题不落库”。
 */

const state = vi.hoisted(() => ({
  html: '<p></p>',
  editable: true,
  options: null as Record<string, (...args: never[]) => unknown> | null,
}))

const chain = vi.hoisted(() => {
  const api: Record<string, unknown> = {}
  const methods = [
    'focus',
    'deleteRange',
    'setParagraph',
    'setNode',
    'toggleBlockquote',
    'setCodeBlock',
    'toggleBulletList',
    'toggleOrderedList',
    'toggleTaskList',
    'setHorizontalRule',
    'insertContent',
    'insertContentAt',
    'setTextSelection',
    'setImage',
    'toggleBold',
    'toggleItalic',
    'toggleUnderline',
    'toggleStrike',
    'toggleCode',
    'setColor',
    'toggleHighlight',
    'toggleLink',
    'extendMarkRange',
    'unsetLink',
    'unsetAllMarks',
    'clearNodes',
    'undo',
    'redo',
  ]
  for (const method of methods) api[method] = () => api
  api.run = () => true
  return api
})

const fakeEditor = vi.hoisted(() => ({
  getHTML: () => state.html,
  getText: () => state.html.replace(/<[^>]+>/g, ' '),
  commands: {
    setContent: (content: string) => {
      state.html = content
    },
  },
  setEditable: (editable: boolean) => {
    state.editable = editable
  },
  registerPlugin: () => undefined,
  chain: () => chain,
  can: () => ({ undo: () => true, redo: () => true }),
  isActive: () => false,
  getAttributes: () => ({}),
  state: { selection: { from: 0, to: 0 } },
  destroy: () => undefined,
}))

vi.mock('@tiptap/vue-3', async () => {
  const { shallowRef } = await import('vue')
  return {
    useEditor: (options: Record<string, (...args: never[]) => unknown>) => {
      state.options = options
      state.editable = Boolean(options.editable)
      return shallowRef(fakeEditor)
    },
    EditorContent: { name: 'EditorContent', template: '<div class="stub-editor-content" />' },
  }
})

vi.mock('@tiptap/vue-3/menus', () => ({
  BubbleMenu: { name: 'BubbleMenu', template: '<div><slot /></div>' },
}))
vi.mock('@tiptap/extension-drag-handle-vue-3', () => ({
  default: { name: 'DragHandle', template: '<div><slot /></div>' },
}))
vi.mock('@/utils/slashCommand', () => ({ registerSlashCommand: vi.fn() }))

const getNote = vi.fn()
const updateNote = vi.fn()
const createNote = vi.fn()
const bindAttachment = vi.fn()
const uploadFile = vi.fn()
vi.mock('@/api/note', () => ({
  getNote: (...a: unknown[]) => getNote(...a),
  updateNote: (...a: unknown[]) => updateNote(...a),
  createNote: (...a: unknown[]) => createNote(...a),
  bindAttachment: (...a: unknown[]) => bindAttachment(...a),
}))
vi.mock('@/api/file', () => ({
  uploadFile: (...a: unknown[]) => uploadFile(...a),
}))
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
}))

import NoteEditor from '@/components/note/NoteEditor.vue'

function detail(over: Record<string, unknown> = {}) {
  return {
    id: 1001,
    content: '正文',
    logDate: '2026-09-28',
    version: 1,
    role: 'OWNER',
    fieldPermissions: { content: true },
    ...over,
  }
}

async function settle(): Promise<void> {
  await flushPromises()
  await vi.dynamicImportSettled()
  await flushPromises()
}

async function mountEdit(payload: Record<string, unknown>): Promise<VueWrapper> {
  getNote.mockResolvedValue(payload)
  const w = shallowMount(NoteEditor, {
    props: { noteId: 1001 },
    global: { directives: { loading: { mounted: () => undefined } } },
  })
  await settle()
  return w
}

function mountNew(): VueWrapper {
  return shallowMount(NoteEditor, {
    global: { directives: { loading: { mounted: () => undefined } } },
  })
}

/** 模拟 Tiptap 的 onUpdate（真实环境里由用户输入触发）。 */
async function type(html: string): Promise<void> {
  state.html = html
  state.options?.onUpdate?.({ editor: fakeEditor } as never)
  await flushPromises()
}

async function submit(w: VueWrapper): Promise<void> {
  await w.find('[data-test="note-editor"]').trigger('keydown', { key: 'Enter', ctrlKey: true })
  await flushPromises()
}

beforeEach(() => {
  state.html = '<p></p>'
  state.editable = true
  state.options = null
  ;[getNote, updateNote, createNote, bindAttachment, uploadFile].forEach((f) => f.mockReset())
})

describe('NoteEditor × fieldPermissions', () => {
  it('owner（content=true）：可编辑，无只读提示', async () => {
    const w = await mountEdit(detail())
    expect(state.editable).toBe(true)
    expect(w.find('note-toolbar-stub').exists()).toBe(true)
    expect(w.find('[data-test="note-editor-readonly-hint"]').exists()).toBe(false)
  })

  it('只读可见者（content=false）：关闭编辑并显示只读提示与日志日期', async () => {
    const w = await mountEdit(detail({ fieldPermissions: { content: false } }))
    expect(state.editable).toBe(false)
    expect(w.find('note-toolbar-stub').exists()).toBe(false)
    expect(w.find('[data-test="note-editor-readonly-hint"]').text()).toContain('只读视图')
    expect(w.find('[data-test="note-editor-logdate"]').text()).toContain('2026-09-28')
  })

  it('未下发 fieldPermissions：视为可改', async () => {
    const payload = detail() as Record<string, unknown>
    delete payload.fieldPermissions
    await mountEdit(payload)
    expect(state.editable).toBe(true)
  })

  it('新建态：正文可编辑，首行预置当天日期标题', async () => {
    mountNew()
    await settle()
    const d = new Date()
    const heading = `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日`
    expect(getNote).not.toHaveBeenCalled()
    expect(state.editable).toBe(true)
    expect(state.html).toContain(`<h1>${heading}</h1>`)
  })

  it('编辑态：回显正文，历史 Markdown 自动转 HTML，不插入日期标题', async () => {
    await mountEdit(detail({ content: '# 已有内容\n正文' }))
    expect(state.html).toContain('<h1>已有内容</h1>')
    expect(state.html).toContain('正文')
    expect(state.html).not.toContain('2026年9月28日')
  })

  it('编辑保存不带 logDate / tagIds', async () => {
    const w = await mountEdit(detail())
    updateNote.mockResolvedValue(detail())
    await type('<p>改过的正文</p>')
    await submit(w)
    expect(updateNote).toHaveBeenCalledWith(1001, {
      content: '<p>改过的正文</p>',
      version: 1,
    })
  })

  it('新建不带 logDate / tagIds', async () => {
    const w = mountNew()
    await settle()
    createNote.mockResolvedValue(detail())
    await type('<p>今天的工作</p>')
    await submit(w)
    expect(createNote).toHaveBeenCalledWith({ content: '<p>今天的工作</p>' })
  })

  it('没有保存/取消按钮，只保留自动保存状态行', async () => {
    const w = mountNew()
    await settle()
    expect(w.find('[data-test="note-editor-save"]').exists()).toBe(false)
    expect(w.find('[data-test="note-editor-status"]').text()).toContain('自动保存')
  })

  it('自动保存：停止输入 1.2s 后创建；只有日期标题时不落库', async () => {
    vi.useFakeTimers()
    try {
      mountNew()
      await settle()

      // 只有预置标题 → 不该凭空建一条日志
      await type(state.html)
      await vi.advanceTimersByTimeAsync(2000)
      expect(createNote).not.toHaveBeenCalled()

      createNote.mockResolvedValue(detail())
      state.html = '<h1>2026年11月3日</h1><p>今日进展</p>'
      state.options?.onUpdate?.({ editor: fakeEditor } as never)
      await vi.advanceTimersByTimeAsync(1200)
      await flushPromises()
      expect(createNote).toHaveBeenCalledWith({
        content: '<h1>2026年11月3日</h1><p>今日进展</p>',
      })
    } finally {
      vi.useRealTimers()
    }
  })

  it('新建落库后继续输入：走 update，不会重复创建', async () => {
    const w = mountNew()
    await settle()
    createNote.mockResolvedValue(detail())
    updateNote.mockResolvedValue(detail({ content: '<p>第二段</p>', version: 2 }))

    await type('<p>第一段</p>')
    await submit(w)
    expect(createNote).toHaveBeenCalledTimes(1)

    await type('<p>第二段</p>')
    await submit(w)
    expect(createNote).toHaveBeenCalledTimes(1)
    expect(updateNote).toHaveBeenCalledWith(1001, {
      content: '<p>第二段</p>',
      version: 1,
    })
  })

  it('编辑态详情请求失败不长时间渲染 loading 骨架', async () => {
    getNote.mockRejectedValueOnce(new Error('请求失败'))
    const w = shallowMount(NoteEditor, {
      props: { noteId: 1001 },
      global: { directives: { loading: { mounted: () => undefined } } },
    })
    await settle()
    expect(w.find('.note-editor__loading').exists()).toBe(false)
  })
})
