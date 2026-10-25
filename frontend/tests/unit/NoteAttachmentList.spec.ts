import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * NoteAttachmentList：附件区行为
 * 1) 多选并发上传：全部成功 → 统一 success 提示 + emit refresh/changed
 * 2) 部分失败：成功的仍然绑定并提示，失败的逐个 error，不阻断其他文件
 * 3) 类型/大小预校验：不合法文件不发请求
 * 4) 移除附件走 attachmentId（不是 ref.id）
 */

const bindAttachment = vi.fn()
const removeAttachment = vi.fn()
vi.mock('@/api/note', () => ({
  bindAttachment: (...a: unknown[]) => bindAttachment(...a),
  removeAttachment: (...a: unknown[]) => removeAttachment(...a),
}))

const uploadFile = vi.fn()
const openAttachment = vi.fn()
vi.mock('@/api/file', () => ({
  uploadFile: (...a: unknown[]) => uploadFile(...a),
  openAttachment: (...a: unknown[]) => openAttachment(...a),
  fetchFileConfig: () => Promise.resolve({ maxSize: 1024 * 1024, allowedExt: ['txt', 'png'] }),
}))

const success = vi.fn()
const error = vi.fn()
const info = vi.fn()
vi.mock('element-plus', () => ({
  ElMessage: {
    success: (...a: unknown[]) => success(...a),
    error: (...a: unknown[]) => error(...a),
    info: (...a: unknown[]) => info(...a),
  },
}))

vi.mock('@element-plus/icons-vue', () => {
  const icon = { template: '<i />' }
  return { Delete: icon, Download: icon, Paperclip: icon, View: icon }
})

import NoteAttachmentList from '@/components/note/NoteAttachmentList.vue'

const ElButton = {
  name: 'ElButton',
  props: ['text', 'size', 'type', 'icon', 'disabled', 'loading'],
  emits: ['click'],
  template: '<button v-bind="$attrs" @click="$emit(\'click\', $event)"><slot /></button>',
}
const ElIcon = { name: 'ElIcon', template: '<i><slot /></i>' }
const ElProgress = { name: 'ElProgress', props: ['percentage', 'strokeWidth'], template: '<div class="progress" />' }

function mountList(props: Record<string, unknown> = {}) {
  return mount(NoteAttachmentList, {
    props: { noteId: 1001, ...props },
    global: { components: { ElButton, ElIcon, ElProgress } },
  })
}

function fakeFile(name: string, size = 10): File {
  const f = new File(['x'], name, { type: 'text/plain' })
  Object.defineProperty(f, 'size', { value: size })
  return f
}

/** 把 File[] 塞进 input 并派发 change */
async function pick(w: ReturnType<typeof mountList>, files: File[]): Promise<void> {
  const input = w.find('[data-test="note-attachment-input"]')
  Object.defineProperty(input.element, 'files', { value: files, configurable: true })
  await input.trigger('change')
  await flushPromises()
}

beforeEach(() => {
  ;[bindAttachment, removeAttachment, uploadFile, openAttachment, success, error, info].forEach((f) => f.mockReset())
})

describe('NoteAttachmentList', () => {
  it('空列表显示「暂无附件」，input 支持 multiple', async () => {
    const w = mountList()
    await flushPromises()
    expect(w.text()).toContain('暂无附件')
    expect(w.find('[data-test="note-attachment-input"]').attributes('multiple')).toBeDefined()
  })

  it('多选并发上传：逐个上传+绑定，汇总一次成功提示', async () => {
    uploadFile.mockImplementation((f: File) => Promise.resolve({ id: f.name === 'a.txt' ? 11 : 12 }))
    bindAttachment.mockResolvedValue(undefined)

    const w = mountList()
    await flushPromises()
    await pick(w, [fakeFile('a.txt'), fakeFile('b.txt')])

    expect(uploadFile).toHaveBeenCalledTimes(2)
    expect(bindAttachment).toHaveBeenCalledTimes(2)
    expect(bindAttachment).toHaveBeenCalledWith(1001, { attachmentId: 11 })
    expect(bindAttachment).toHaveBeenCalledWith(1001, { attachmentId: 12 })
    expect(success).toHaveBeenCalledWith('2 个附件已上传')
    expect(w.emitted('refresh')).toHaveLength(1)
    expect(w.emitted('changed')).toHaveLength(1)
  })

  it('单文件仍提示「附件已上传」', async () => {
    uploadFile.mockResolvedValue({ id: 21 })
    const w = mountList()
    await flushPromises()
    await pick(w, [fakeFile('one.txt')])
    expect(success).toHaveBeenCalledWith('附件已上传')
  })

  it('部分失败：成功的照常绑定并提示，失败逐个报错', async () => {
    uploadFile.mockImplementation((f: File) =>
      f.name === 'bad.txt' ? Promise.reject(new Error('网络中断')) : Promise.resolve({ id: 31 }),
    )
    const w = mountList()
    await flushPromises()
    await pick(w, [fakeFile('ok.txt'), fakeFile('bad.txt')])

    expect(bindAttachment).toHaveBeenCalledTimes(1)
    expect(success).toHaveBeenCalledWith('附件已上传')
    expect(error).toHaveBeenCalledWith('bad.txt：网络中断')
    expect(w.emitted('changed')).toHaveLength(1)
  })

  it('预校验：超限与非法类型不发上传请求', async () => {
    const w = mountList()
    await flushPromises()
    await pick(w, [fakeFile('big.txt', 5 * 1024 * 1024), fakeFile('x.exe')])
    expect(uploadFile).not.toHaveBeenCalled()
    expect(error).toHaveBeenCalledWith('big.txt 超过上限 1.0 MB')
    expect(error).toHaveBeenCalledWith('x.exe 类型不支持，仅允许 txt / png')
  })

  it('移除附件用 attachmentId', async () => {
    removeAttachment.mockResolvedValue(undefined)
    const w = mountList({
      attachments: [{ attachmentId: 77, refId: 999, fileName: 'r.txt', size: 3, canRemoveRef: true }],
    })
    await flushPromises()
    await w.find('[title="移除"]').trigger('click')
    await flushPromises()
    expect(removeAttachment).toHaveBeenCalledWith(1001, 77)
    expect(success).toHaveBeenCalledWith('已移除附件')
    expect(w.emitted('refresh')).toHaveLength(1)
  })

  it('canRemoveRef=false 时不渲染移除按钮；canUpload=false 时不渲染上传入口', async () => {
    const w = mountList({
      canUpload: false,
      attachments: [{ attachmentId: 1, fileName: 'a.txt', canRemoveRef: false }],
    })
    await flushPromises()
    expect(w.find('[title="移除"]').exists()).toBe(false)
    expect(w.find('[data-test="note-attachment-input"]').exists()).toBe(false)
  })
})
