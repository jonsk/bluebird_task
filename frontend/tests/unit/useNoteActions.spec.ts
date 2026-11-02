import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

/**
 * useNoteActions：remove 端点、受影响 scope 的缓存失效、
 * 成功/失败 toast 文案（错误统一走 errorCode.messageOf 产生的 message）。
 * 置顶能力已下线，不再有 togglePin。
 */

const deleteNote = vi.fn()
vi.mock('@/api/note', () => ({
  deleteNote: (...a: unknown[]) => deleteNote(...a),
}))

const success = vi.fn()
const error = vi.fn()
vi.mock('element-plus', () => ({
  ElMessage: { success: (...a: unknown[]) => success(...a), error: (...a: unknown[]) => error(...a) },
}))

import { useNoteActions } from '@/composables/useNoteActions'
import { useNoteStore } from '@/stores/note'
import { HttpError } from '@/api/http'

beforeEach(() => {
  setActivePinia(createPinia())
  ;[deleteNote, success, error].forEach((f) => f.mockReset())
})

describe('useNoteActions', () => {
  it('remove：成功后失效缓存并提示「已删除」', async () => {
    const store = useNoteStore()
    const spy = vi.spyOn(store, 'invalidate')
    const actions = useNoteActions()
    deleteNote.mockResolvedValueOnce(undefined)
    await actions.remove({ id: 3 })
    expect(deleteNote).toHaveBeenCalledWith(3)
    expect(spy).toHaveBeenCalledWith(['mine', 'subordinate', 'all'])
    expect(success).toHaveBeenCalledWith('已删除')
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
