import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * NoteEditor 编辑态消费后端 `fieldPermissions` 的正文可编辑性：
 * 1) owner/ADMIN（content=true）→ 正文可编辑，不显示只读提示
 * 2) 只读可见者（content=false）→ 正文只读 + 只读提示
 * 3) 未下发 fieldPermissions → 视为可改（能进编辑态的就是 owner/ADMIN）
 * 4) 新建态 → 可编辑；且没有日期选择（日志日期由后端固定为当天）
 * 5) 提交时不再携带 logDate / tagIds（契约已下线）
 *
 * 组件用 props、stub 读取 disabled / readonly，不依赖真实 DOM 细节。
 */

const getNote = vi.fn()
const updateNote = vi.fn()
const createNote = vi.fn()
vi.mock('@/api/note', () => ({
  getNote: (...a: unknown[]) => getNote(...a),
  updateNote: (...a: unknown[]) => updateNote(...a),
  createNote: (...a: unknown[]) => createNote(...a),
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
}))

import NoteEditor from '@/components/note/NoteEditor.vue'

const ElInput = {
  name: 'ElInput',
  props: { modelValue: String, type: String, rows: Number, maxlength: [String, Number], readonly: Boolean },
  template: '<div class="stub-input"><slot /></div>',
}
const ElDatePicker = {
  name: 'ElDatePicker',
  props: { modelValue: String, disabled: Boolean, type: String, valueFormat: String },
  template: '<div class="stub-date" />',
}
const ElButton = {
  name: 'ElButton',
  props: { disabled: Boolean, loading: Boolean, type: String },
  template: '<button><slot /></button>',
}

const components = { ElInput, ElDatePicker, ElButton }

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

async function mountEdit(payload: Record<string, unknown>) {
  getNote.mockResolvedValue(payload)
  const w = mount(NoteEditor, {
    props: { noteId: 1001 },
    global: { components },
  })
  await flushPromises()
  return w
}

const flags = (w: { findComponent: (c: unknown) => { props: (k: string) => unknown } }) => ({
  contentReadonly: w.findComponent(ElInput).props('readonly') === true,
})

beforeEach(() => {
  ;[getNote, updateNote, createNote].forEach((f) => f.mockReset())
})

describe('NoteEditor × fieldPermissions', () => {
  it('owner（content=true）：正文可编辑，无只读提示', async () => {
    const w = await mountEdit(detail())
    expect(flags(w)).toEqual({ contentReadonly: false })
    expect(w.find('[data-test="note-editor-readonly-hint"]').exists()).toBe(false)
  })

  it('只读可见者（content=false）：正文只读 + 只读提示，且展示不可改的日志日期', async () => {
    const w = await mountEdit(detail({ fieldPermissions: { content: false } }))
    expect(flags(w)).toEqual({ contentReadonly: true })
    expect(w.find('[data-test="note-editor-readonly-hint"]').text()).toContain('只读视图')
    expect(w.find('[data-test="note-editor-logdate"]').text()).toContain('2026-09-28')
  })

  it('未下发 fieldPermissions：视为可改', async () => {
    const payload = detail() as Record<string, unknown>
    delete payload.fieldPermissions
    const w = await mountEdit(payload)
    expect(flags(w)).toEqual({ contentReadonly: false })
  })

  it('新建态：无日期选择控件，正文可编辑', async () => {
    const w = mount(NoteEditor, { global: { components } })
    await flushPromises()
    expect(getNote).not.toHaveBeenCalled()
    expect(w.findComponent(ElDatePicker).exists()).toBe(false)
    expect(flags(w)).toEqual({ contentReadonly: false })
  })

  it('编辑保存不带 logDate / tagIds', async () => {
    const w = await mountEdit(detail())
    updateNote.mockResolvedValue(detail())
    await w.findComponent(ElInput).vm.$emit('update:modelValue', '改过的正文')
    await flushPromises()
    const buttons = w.findAllComponents(ElButton)
    await buttons[0].trigger('click')
    await flushPromises()
    expect(updateNote).toHaveBeenCalledWith(1001, { content: '改过的正文', version: 1 })
  })

  it('新建不带 logDate / tagIds', async () => {
    const w = mount(NoteEditor, { global: { components } })
    createNote.mockResolvedValue(detail())
    await w.findComponent(ElInput).vm.$emit('update:modelValue', '今天的工作')
    await flushPromises()
    const buttons = w.findAllComponents(ElButton)
    await buttons[0].trigger('click')
    await flushPromises()
    expect(createNote).toHaveBeenCalledWith({ content: '今天的工作' })
  })

  it('编辑态详情请求失败不长时间渲染 loading 骨架', async () => {
    getNote.mockRejectedValueOnce(new Error('请求失败'))
    const w = mount(NoteEditor, { props: { noteId: 1001 }, global: { components } })
    await flushPromises()
    expect(w.find('.note-editor__loading').exists()).toBe(false)
  })
})
