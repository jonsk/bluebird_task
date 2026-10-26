import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * NoteEditor：编辑态按后端 `fieldPermissions` 锁定字段
 * 1) owner（四项 true）→ 控件全部可用、无提示
 * 2) EDITOR（visibility/tagIds false）→ 可见性与标签禁用 + 协作者提示
 * 3) VIEWER（content false）→ 正文只读 + 只读提示
 * 4) 旧后端未下发 fieldPermissions → 回退 grantRole=EDITOR 的锁定规则
 * 5) 新建态 → 全字段可改
 *
 * 断言走组件 props（stub 上取 disabled / readonly），避免依赖 stub 的 DOM 细节。
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
const ElSelect = {
  name: 'ElSelect',
  props: { modelValue: String, disabled: Boolean },
  template: '<div class="stub-select"><slot /></div>',
}
const ElOption = { name: 'ElOption', props: { label: String, value: String }, template: '<div />' }
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
const BbTagPicker = {
  name: 'BbTagPicker',
  props: { modelValue: Array, options: Array, disabled: Boolean },
  template: '<div class="stub-tags" />',
}

const components = { ElInput, ElSelect, ElOption, ElDatePicker, ElButton, BbTagPicker }

function detail(over: Record<string, unknown> = {}) {
  return {
    id: 1001,
    content: '正文',
    logDate: '2026-09-28',
    visibility: 'PRIVATE',
    version: 1,
    tags: [],
    grantRole: 'OWNER',
    fieldPermissions: { content: true, logDate: true, visibility: true, tagIds: true },
    ...over,
  }
}

async function mountEdit(payload: Record<string, unknown>) {
  getNote.mockResolvedValue(payload)
  const w = mount(NoteEditor, {
    props: { noteId: 1001, ownerTags: [] },
    global: { components },
  })
  await flushPromises()
  return w
}

const flags = (w: ReturnType<typeof mountEdit> extends Promise<infer T> ? T : never) => ({
  contentReadonly: w.findComponent(ElInput).props('readonly') === true,
  dateDisabled: w.findComponent(ElDatePicker).props('disabled') === true,
  visibilityDisabled: w.findComponent(ElSelect).props('disabled') === true,
  tagsDisabled: w.findComponent(BbTagPicker).props('disabled') === true,
})

beforeEach(() => {
  ;[getNote, updateNote, createNote].forEach((f) => f.mockReset())
})

describe('NoteEditor · fieldPermissions', () => {
  it('owner：四个控件均可用，无权限提示', async () => {
    const w = await mountEdit(detail())
    expect(flags(w)).toEqual({
      contentReadonly: false,
      dateDisabled: false,
      visibilityDisabled: false,
      tagsDisabled: false,
    })
    expect(w.find('[data-test="note-editor-field-hint"]').exists()).toBe(false)
    expect(w.find('[data-test="note-editor-readonly-hint"]').exists()).toBe(false)
  })

  it('EDITOR：锁定可见性与标签，正文/日期可改，显示协作者提示', async () => {
    const w = await mountEdit(
      detail({
        grantRole: 'EDITOR',
        fieldPermissions: { content: true, logDate: true, visibility: false, tagIds: false },
      }),
    )
    expect(flags(w)).toEqual({
      contentReadonly: false,
      dateDisabled: false,
      visibilityDisabled: true,
      tagsDisabled: true,
    })
    expect(w.find('[data-test="note-editor-field-hint"]').text()).toContain('作为协作者')
    expect(w.find('[data-test="note-editor-readonly-hint"]').exists()).toBe(false)
  })

  it('VIEWER：正文只读、四字段全禁，显示只读提示', async () => {
    const w = await mountEdit(
      detail({
        grantRole: 'VIEWER',
        fieldPermissions: { content: false, logDate: false, visibility: false, tagIds: false },
      }),
    )
    expect(flags(w)).toEqual({
      contentReadonly: true,
      dateDisabled: true,
      visibilityDisabled: true,
      tagsDisabled: true,
    })
    expect(w.find('[data-test="note-editor-readonly-hint"]').text()).toContain('只读视图')
    expect(w.find('[data-test="note-editor-field-hint"]').exists()).toBe(false)
  })

  it('旧后端未下发 fieldPermissions：回退到 grantRole=EDITOR 的锁定规则', async () => {
    const payload = detail({ grantRole: 'EDITOR' }) as Record<string, unknown>
    delete payload.fieldPermissions
    const w = await mountEdit(payload)
    expect(flags(w)).toEqual({
      contentReadonly: false,
      dateDisabled: false,
      visibilityDisabled: true,
      tagsDisabled: true,
    })
  })

  it('新建态：无需 detail，全字段可改', async () => {
    const w = mount(NoteEditor, { props: { ownerTags: [] }, global: { components } })
    await flushPromises()
    expect(getNote).not.toHaveBeenCalled()
    expect(flags(w)).toEqual({
      contentReadonly: false,
      dateDisabled: false,
      visibilityDisabled: false,
      tagsDisabled: false,
    })
  })

  it('编辑态详情请求失败不阻塞渲染（loading 归零）', async () => {
    getNote.mockRejectedValueOnce(new Error('网络错误'))
    const w = mount(NoteEditor, { props: { noteId: 1001, ownerTags: [] }, global: { components } })
    await flushPromises()
    expect(w.find('.note-editor__loading').exists()).toBe(false)
  })
})
