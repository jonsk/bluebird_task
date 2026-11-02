import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

/**
 * NoteEditor 编辑态消费后端 `fieldPermissions` 的正文可编辑性：
 * 1) owner/ADMIN（content=true）→ 正文可编辑，不显示只读提示
 * 2) 只读可见者（content=false）→ 正文只读 + 只读提示
 * 3) 未下发 fieldPermissions → 视为可改（能进编辑态的就是 owner/ADMIN）
 * 4) 新建态 → 可编辑；且没有日期选择（日志日期由后端固定为当天）
 * 5) 提交时不再携带 logDate / tagIds（契约已下线）
 *
 * 编辑器已换成 md-editor-v3（异步组件），此处用 stub 替换，
 * 只断言 props 与提交载荷，不依赖 CodeMirror 的真实 DOM。
 */

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

/**
 * 插件在真实环境里会动态 import prettier/mermaid/echarts（体积大、jsdom 跑不动），
 * 这里直接短路——本用例关心的是权限 prop 与提交载荷，不是扩展装配。
 */
vi.mock('@/plugins/markdown', () => ({
  setupMarkdown: vi.fn(),
  setupMarkdownEditor: vi.fn(async () => undefined),
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
}))

vi.mock('md-editor-v3', () => {
  const MdEditor = {
    name: 'MdEditor',
    props: [
      'modelValue',
      'theme',
      'language',
      'preview',
      'maxLength',
      'readOnly',
      'toolbarsExclude',
      'noMermaid',
      'noKatex',
      'noEcharts',
      'noPrettier',
      'noUploadImg',
      'onUploadImg',
      'placeholder',
    ],
    emits: ['update:modelValue', 'onSave'],
    template: '<div class="stub-md" />',
  }
  const MdPreview = {
    name: 'MdPreview',
    props: ['modelValue', 'theme', 'language'],
    template: '<div class="stub-preview" />',
  }
  return { MdEditor, MdPreview, config: vi.fn() }
})

import NoteEditor from '@/components/note/NoteEditor.vue'

const ElButton = {
  name: 'ElButton',
  props: { disabled: Boolean, loading: Boolean, type: String },
  template: '<button><slot /></button>',
}
const ElDatePicker = {
  name: 'ElDatePicker',
  props: { modelValue: String, disabled: Boolean, type: String, valueFormat: String },
  template: '<div class="stub-date" />',
}

const components = { ElButton, ElDatePicker }

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
  // 异步组件（defineAsyncComponent）+ 动态 import，需要等导入队列落地
  await flushPromises()
  await vi.dynamicImportSettled()
  await flushPromises()
}

function editorOf(w: VueWrapper) {
  return w.findComponent({ name: 'MdEditor' })
}

const contentReadonly = (w: VueWrapper) => editorOf(w).props('readOnly') === true

async function mountEdit(payload: Record<string, unknown>): Promise<VueWrapper> {
  getNote.mockResolvedValue(payload)
  const w = mount(NoteEditor, { props: { noteId: 1001 }, global: { components } })
  await settle()
  return w
}

beforeEach(() => {
  setActivePinia(createPinia())
  ;[getNote, updateNote, createNote, bindAttachment, uploadFile].forEach((f) => f.mockReset())
})

describe('NoteEditor × fieldPermissions', () => {
  it('owner（content=true）：正文可编辑，无只读提示', async () => {
    const w = await mountEdit(detail())
    expect(contentReadonly(w)).toBe(false)
    expect(w.find('[data-test="note-editor-readonly-hint"]').exists()).toBe(false)
  })

  it('只读可见者（content=false）：正文只读 + 只读提示，且展示不可改的日志日期', async () => {
    const w = await mountEdit(detail({ fieldPermissions: { content: false } }))
    expect(contentReadonly(w)).toBe(true)
    expect(w.find('[data-test="note-editor-readonly-hint"]').text()).toContain('只读视图')
    expect(w.find('[data-test="note-editor-logdate"]').text()).toContain('2026-09-28')
  })

  it('未下发 fieldPermissions：视为可改', async () => {
    const payload = detail() as Record<string, unknown>
    delete payload.fieldPermissions
    const w = await mountEdit(payload)
    expect(contentReadonly(w)).toBe(false)
  })

  it('新建态：无日期选择控件，正文可编辑', async () => {
    const w = mount(NoteEditor, { global: { components } })
    await settle()
    expect(getNote).not.toHaveBeenCalled()
    expect(w.findComponent(ElDatePicker).exists()).toBe(false)
    expect(contentReadonly(w)).toBe(false)
  })

  it('编辑保存不带 logDate / tagIds', async () => {
    const w = await mountEdit(detail())
    updateNote.mockResolvedValue(detail())
    editorOf(w).vm.$emit('update:modelValue', '改过的正文')
    await flushPromises()
    const buttons = w.findAllComponents(ElButton)
    await buttons[0].trigger('click')
    await flushPromises()
    expect(updateNote).toHaveBeenCalledWith(1001, { content: '改过的正文', version: 1 })
  })

  it('新建不带 logDate / tagIds', async () => {
    const w = mount(NoteEditor, { global: { components } })
    await settle()
    createNote.mockResolvedValue(detail())
    editorOf(w).vm.$emit('update:modelValue', '今天的工作')
    await flushPromises()
    const buttons = w.findAllComponents(ElButton)
    await buttons[0].trigger('click')
    await flushPromises()
    expect(createNote).toHaveBeenCalledWith({ content: '今天的工作' })
  })

  it('编辑态详情请求失败不长时间渲染 loading 骨架', async () => {
    getNote.mockRejectedValueOnce(new Error('请求失败'))
    const w = mount(NoteEditor, { props: { noteId: 1001 }, global: { components } })
    await settle()
    expect(w.find('.note-editor__loading').exists()).toBe(false)
  })
})

describe('NoteEditor × 图片上传', () => {
  const png = () => new File([new Uint8Array([1, 2, 3])], 'shot.png', { type: 'image/png' })

  it('先上传后绑定，回调传入 note 预览地址（不是 /files/{id}）', async () => {
    const w = await mountEdit(detail())
    uploadFile.mockResolvedValue({ id: 9001 })
    bindAttachment.mockResolvedValue({ previewUrl: '/notes/1001/attachments/9001/preview' })

    const cb = vi.fn()
    await editorOf(w).props('onUploadImg')([png()], cb)

    expect(uploadFile).toHaveBeenCalledTimes(1)
    expect(bindAttachment).toHaveBeenCalledWith(1001, { attachmentId: 9001 })
    expect(cb).toHaveBeenCalledWith(['/notes/1001/attachments/9001/preview'])
    expect(w.emitted('attachments-changed')).toHaveLength(1)
  })

  it('新建态（无 noteId）：拒绝上传并提示先保存', async () => {
    const w = mount(NoteEditor, { global: { components } })
    await settle()
    const cb = vi.fn()
    await editorOf(w).props('onUploadImg')([png()], cb)

    expect(uploadFile).not.toHaveBeenCalled()
    expect(cb).toHaveBeenCalledWith([])
  })

  it('非图片文件直接拒绝，不发请求', async () => {
    const w = await mountEdit(detail())
    const cb = vi.fn()
    const txt = new File(['x'], 'a.txt', { type: 'text/plain' })
    await editorOf(w).props('onUploadImg')([txt], cb)

    expect(uploadFile).not.toHaveBeenCalled()
    expect(cb).toHaveBeenCalledWith([])
  })
})
