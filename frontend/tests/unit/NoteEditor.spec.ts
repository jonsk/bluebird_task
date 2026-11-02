import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

/**
 * NoteEditor 消费后端下发的 `fieldPermissions` 决定正文可编辑性：
 * 1) owner/ADMIN（content=true）→ IR 可编辑，不显示只读提示
 * 2) 只读可见者（content=false）→ preview 模式 + 只读提示
 * 3) 未下发 fieldPermissions → 视为可改（能进编辑态的就是 owner/ADMIN）
 * 4) 新建态：可编辑；且没有日期选择（日志日期由后端固定为当天）
 * 5) 提交时不携带 logDate / tagIds（契约已下线）
 *
 * 编辑器已换成 Vditor（IR 模式），这里 stub 掉 `@/utils/vditor` 的加载器：
 * 只断言构造参数与提交载荷，不依赖真实 DOM / lute 引擎。
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

/** 编辑器实例桩：记录构造参数，暴露 Vditor 实例方法。 */
const instances: FakeVditor[] = []
vi.mock('@/utils/vditor', () => {
  class FakeVditor {
    opts: Record<string, any>
    insertValue = vi.fn()
    destroy = vi.fn()
    disabled = vi.fn()
    setTheme = vi.fn()
    constructor(_el: HTMLElement, opts: Record<string, any>) {
      this.opts = opts
      instances.push(this)
    }
  }
  return {
    VDITOR_CDN: '/vditor',
    loadVditor: async () => FakeVditor,
    vditorTheme: (dark: boolean) => (dark ? 'dark' : 'classic'),
    vditorContentTheme: (dark: boolean) => (dark ? 'dark' : 'light'),
    vditorHljs: (dark: boolean) => ({ style: dark ? 'monokai' : 'github', lineNumber: false }),
    renderVditorPreview: vi.fn(async () => undefined),
  }
})

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
}))

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
  await flushPromises()
  await vi.dynamicImportSettled()
  await flushPromises()
}

/** 最后一个 Vditor 实例（切换可编辑性会重建）。 */
function editorOf(): FakeVditor {
  return instances[instances.length - 1]
}

/** 只读态 = 工具栏隐藏 + 实例 disabled（Vditor 没有 preview 编辑模式）。 */
const isReadonly = () =>
  editorOf().opts.toolbarConfig.hide === true && editorOf().disabled.mock.calls.length > 0

/** 模拟 Vditor 的 `input` 回调（真实环境里由编辑器在输入时触发）。 */
async function type(value: string): Promise<void> {
  editorOf().opts.input(value)
  await flushPromises()
}

async function mountEdit(payload: Record<string, unknown>): Promise<VueWrapper> {
  getNote.mockResolvedValue(payload)
  const w = mount(NoteEditor, { props: { noteId: 1001 }, global: { components } })
  await settle()
  return w
}

async function submit(w: VueWrapper): Promise<void> {
  await w.findAllComponents(ElButton)[0].trigger('click')
  await flushPromises()
}

beforeEach(() => {
  setActivePinia(createPinia())
  instances.length = 0
  ;[getNote, updateNote, createNote, bindAttachment, uploadFile].forEach((f) => f.mockReset())
})

describe('NoteEditor × fieldPermissions', () => {
  it('owner（content=true）：IR 模式可编辑，无只读提示', async () => {
    const w = await mountEdit(detail())
    expect(isReadonly()).toBe(false)
    expect(editorOf().opts.mode).toBe('ir')
    expect(w.find('[data-test="note-editor-readonly-hint"]').exists()).toBe(false)
  })

  it('只读可见者（content=false）：preview 模式 + 只读提示，且展示不可改的日志日期', async () => {
    const w = await mountEdit(detail({ fieldPermissions: { content: false } }))
    expect(isReadonly()).toBe(true)
    expect(editorOf().opts.toolbarConfig.hide).toBe(true)
    expect(w.find('[data-test="note-editor-readonly-hint"]').text()).toContain('只读视图')
    expect(w.find('[data-test="note-editor-logdate"]').text()).toContain('2026-09-28')
  })

  it('未下发 fieldPermissions：视为可改', async () => {
    const payload = detail() as Record<string, unknown>
    delete payload.fieldPermissions
    await mountEdit(payload)
    expect(isReadonly()).toBe(false)
  })

  it('新建态：无日期选择控件，正文可编辑', async () => {
    const w = mount(NoteEditor, { global: { components } })
    await settle()
    expect(getNote).not.toHaveBeenCalled()
    expect(w.findComponent(ElDatePicker).exists()).toBe(false)
    expect(isReadonly()).toBe(false)
  })

  it('编辑保存不带 logDate / tagIds', async () => {
    const w = await mountEdit(detail())
    updateNote.mockResolvedValue(detail())
    await type('改过的正文')
    await submit(w)
    expect(updateNote).toHaveBeenCalledWith(1001, { content: '改过的正文', version: 1 })
  })

  it('新建不带 logDate / tagIds', async () => {
    const w = mount(NoteEditor, { global: { components } })
    await settle()
    createNote.mockResolvedValue(detail())
    await type('今天的工作')
    await submit(w)
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

  it('先上传后绑定，插入 note 预览地址（不用 /files/{id}）', async () => {
    const w = await mountEdit(detail())
    uploadFile.mockResolvedValue({ id: 9001 })
    bindAttachment.mockResolvedValue({ previewUrl: '/notes/1001/attachments/9001/preview' })

    const result = await editorOf().opts.upload.handler([png()])

    expect(result).toBe('')
    expect(uploadFile).toHaveBeenCalledTimes(1)
    expect(bindAttachment).toHaveBeenCalledWith(1001, { attachmentId: 9001 })
    expect(editorOf().insertValue).toHaveBeenCalledWith(
      '![](/notes/1001/attachments/9001/preview)',
      true,
    )
    expect(w.emitted('attachments-changed')).toHaveLength(1)
  })

  it('新建态（无 noteId）：拒绝上传并提示先保存', async () => {
    mount(NoteEditor, { global: { components } })
    await settle()
    const result = await editorOf().opts.upload.handler([png()])

    expect(result).toBe('请先保存日志，再插入图片')
    expect(uploadFile).not.toHaveBeenCalled()
  })

  it('非图片文件直接拒绝，不发请求', async () => {
    await mountEdit(detail())
    const txt = new File(['x'], 'a.txt', { type: 'text/plain' })
    const result = await editorOf().opts.upload.handler([txt])

    expect(result).toBe('图片上传失败')
    expect(uploadFile).not.toHaveBeenCalled()
  })
})

interface FakeVditor {
  opts: Record<string, any>
  insertValue: ReturnType<typeof vi.fn>
  destroy: ReturnType<typeof vi.fn>
  disabled: ReturnType<typeof vi.fn>
  setTheme: ReturnType<typeof vi.fn>
}
