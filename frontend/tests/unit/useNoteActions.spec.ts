import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

/**
 * useNoteActions：pin/remove 的互斥端点、受影响 scope 的缓存失效、
 * 成功/失败 toast 文案（错误统一走 errorCode.messageOf 产生的 message）。
 */

const pinNote = vi.fn()
const unpinNote = vi.fn()
const deleteNote = vi.fn()
vi.mock('@/api/note', () => ({
  pinNote: (...a: unknown[]) => pinNote(...a),
  unpinNote: (...a: unknown[]) => unpinNote(...a),
  deleteNote: (...a: unknown[]) => deleteNote(...a),
}))

const success = vi.fn()
const error = vi.fn()
vi.mock('element-plus', () => ({
  ElMessage: { success: (...a: unknown[]) => success(...a), error: (...a: unknown[]) => error(...a) },
}))

import { useNoteActions } from '@/composables/useNoteActions'
import { useNoteStore } from '@/stores/note'
import { BizError, HttpError } from '@/api/http'

beforeEach(() => {
  setActivePinia(createPinia())
  ;[pinNote, unpinNote, deleteNote, success, error].forEach((f) => f.mockReset())
})

describe('useNoteActions', () => {
  it('togglePin：未置顶调 pin，置顶调 unpin，并失效列表缓存', async () => {
    const store = useNoteStore()
    const spy = vi.spyOn(store, 'invalidate')
    const actions = useNoteActions()

    pinNote.mockResolvedValueOnce(undefined)
    await actions.togglePin({ id: 1, pinned: false })
    expect(pinNote).toHaveBeenCalledWith(1)
    expect(unpinNote).not.toHaveBeenCalled()
    expect(spy).toHaveBeenCalledWith(['mine', 'shared', 'subordinate', 'all'])
    expect(success).toHaveBeenCalledWith('操作成功')

    unpinNote.mockResolvedValueOnce(undefined)
    await actions.togglePin({ id: 1, pinned: true })
    expect(unpinNote).toHaveBeenCalledWith(1)
  })

  it('remove：成功后失效缓存并提示「已删除」', async () => {
    const store = useNoteStore()
    const spy = vi.spyOn(store, 'invalidate')
    const actions = useNoteActions()
    deleteNote.mockResolvedValueOnce(undefined)
    await actions.remove({ id: 3 })
    expect(deleteNote).toHaveBeenCalledWith(3)
    expect(spy).toHaveBeenCalledWith(['mine', 'shared', 'subordinate', 'all'])
    expect(success).toHaveBeenCalledWith('已删除')
  })

  it('业务错误：toast 用 BizError.message，且不失效缓存', async () => {
    const store = useNoteStore()
    const spy = vi.spyOn(store, 'invalidate')
    const actions = useNoteActions()
    pinNote.mockRejectedValueOnce(new BizError(50013, '该字段不可修改'))
    await actions.togglePin({ id: 4, pinned: false })
    expect(error).toHaveBeenCalledWith('该字段不可修改')
    expect(spy).not.toHaveBeenCalled()
  })

  it('HTTP 错误（如 403）同样走 message', async () => {
    const actions = useNoteActions()
    deleteNote.mockRejectedValueOnce(new HttpError(403, undefined, '无权限'))
    await actions.remove({ id: 5 })
    expect(error).toHaveBeenCalledWith('无权限')
  })

  it('非自定义错误：抛出 Error.message，空则兜底「操作失败」', async () => {
    const actions = useNoteActions()
    deleteNote.mockRejectedValueOnce(new Error(''))
    await actions.remove({ id: 6 })
    expect(error).toHaveBeenCalledWith('操作失败')
  })
})
