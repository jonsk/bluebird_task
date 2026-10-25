import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

/**
 * useNoteActions：visibility → 受影响 scope 的缓存失效、pin/archive 的互斥端点、
 * 成功/失败 toast 文案（错误统一走 errorCode.messageOf 产生的 message）。
 */

const pinNote = vi.fn()
const unpinNote = vi.fn()
const archiveNote = vi.fn()
const unarchiveNote = vi.fn()
const deleteNote = vi.fn()
vi.mock('@/api/note', () => ({
  pinNote: (...a: unknown[]) => pinNote(...a),
  unpinNote: (...a: unknown[]) => unpinNote(...a),
  archiveNote: (...a: unknown[]) => archiveNote(...a),
  unarchiveNote: (...a: unknown[]) => unarchiveNote(...a),
  deleteNote: (...a: unknown[]) => deleteNote(...a),
}))

const success = vi.fn()
const error = vi.fn()
vi.mock('element-plus', () => ({
  ElMessage: { success: (...a: unknown[]) => success(...a), error: (...a: unknown[]) => error(...a) },
}))

import { useNoteActions, scopesForVisibility } from '@/composables/useNoteActions'
import { useNoteStore } from '@/stores/note'
import { BizError, HttpError } from '@/api/http'

beforeEach(() => {
  setActivePinia(createPinia())
  ;[pinNote, unpinNote, archiveNote, unarchiveNote, deleteNote, success, error].forEach((f) => f.mockReset())
})

describe('scopesForVisibility', () => {
  it('PRIVATE → mine/all', () => {
    expect(scopesForVisibility('PRIVATE')).toEqual(['mine', 'all'])
  })
  it('INTERNAL → mine/internal/all', () => {
    expect(scopesForVisibility('INTERNAL')).toEqual(['mine', 'internal', 'all'])
  })
  it('PUBLIC → 含 explore/shared', () => {
    expect(scopesForVisibility('PUBLIC')).toEqual(['mine', 'explore', 'shared', 'all'])
  })
  it('未知/缺省 → mine/all', () => {
    expect(scopesForVisibility(undefined)).toEqual(['mine', 'all'])
  })
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
    expect(spy).toHaveBeenCalledWith(['mine', 'all'])
    expect(success).toHaveBeenCalledWith('操作成功')

    unpinNote.mockResolvedValueOnce(undefined)
    await actions.togglePin({ id: 1, pinned: true })
    expect(unpinNote).toHaveBeenCalledWith(1)
  })

  it('toggleArchive：额外失效 mine/all（归档查询）', async () => {
    const store = useNoteStore()
    const spy = vi.spyOn(store, 'invalidate')
    const actions = useNoteActions()

    archiveNote.mockResolvedValueOnce(undefined)
    await actions.toggleArchive({ id: 2, archived: false, visibility: 'INTERNAL' })
    expect(archiveNote).toHaveBeenCalledWith(2)
    expect(spy).toHaveBeenCalledWith(['mine', 'internal', 'all'])

    unarchiveNote.mockResolvedValueOnce(undefined)
    await actions.toggleArchive({ id: 2, archived: true, visibility: 'PUBLIC' })
    expect(unarchiveNote).toHaveBeenCalledWith(2)
    expect(spy).toHaveBeenLastCalledWith(['mine', 'explore', 'shared', 'all'])
  })

  it('remove：成功后失效缓存并提示「已删除」', async () => {
    const store = useNoteStore()
    const spy = vi.spyOn(store, 'invalidate')
    const actions = useNoteActions()
    deleteNote.mockResolvedValueOnce(undefined)
    await actions.remove({ id: 3, visibility: 'PUBLIC' })
    expect(deleteNote).toHaveBeenCalledWith(3)
    expect(spy).toHaveBeenCalledWith(['mine', 'explore', 'shared', 'all'])
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
