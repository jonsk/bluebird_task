import { describe, it, expect, vi, beforeEach } from 'vitest'
import { defineComponent, h, reactive, nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

/**
 * useNoteScopePage：7 个 scope 页共用逻辑
 * 1) 403 + adminOnly → errorMode='empty' 且文案「仅管理员可查看」
 * 2) 403 但非 adminOnly/emptyOn403 → errorMode='card'（真实错误卡片）
 * 3) onEdit：注入 onOpenEditor 时交给宿主，否则跳详情
 * 4) onRemove：确认框取消则不删除；确认后调 remove 并 refresh
 */

const route = reactive({
  path: '/notes',
  query: {} as Record<string, string>,
  meta: { scope: 'mine', archived: false } as Record<string, unknown>,
})
const router = { push: vi.fn(() => Promise.resolve()), replace: vi.fn(() => Promise.resolve()) }
vi.mock('vue-router', () => ({ useRoute: () => route, useRouter: () => router }))

const listNotes = vi.fn()
const listNoteTags = vi.fn()
const deleteNote = vi.fn()
const pinNote = vi.fn()
vi.mock('@/api/note', () => ({
  listNotes: (...a: unknown[]) => listNotes(...a),
  listNoteTags: (...a: unknown[]) => listNoteTags(...a),
  deleteNote: (...a: unknown[]) => deleteNote(...a),
  pinNote: (...a: unknown[]) => pinNote(...a),
  unpinNote: vi.fn(),
  archiveNote: vi.fn(),
  unarchiveNote: vi.fn(),
}))

const confirm = vi.fn()
const success = vi.fn()
const errorMsg = vi.fn()
vi.mock('element-plus', () => ({
  ElMessageBox: { confirm: (...a: unknown[]) => confirm(...a) },
  ElMessage: { success: (...a: unknown[]) => success(...a), error: (...a: unknown[]) => errorMsg(...a) },
}))

import { useNoteScopePage, type UseNoteScopePageOptions } from '@/composables/useNoteScopePage'
import { HttpError } from '@/api/http'

async function flush(times = 5): Promise<void> {
  for (let i = 0; i < times; i += 1) {
    await Promise.resolve()
    await nextTick()
  }
}

/** 在真实组件上下文中运行，保证 onMounted/watch 生效 */
function mountPage(options: UseNoteScopePageOptions = {}) {
  let api!: ReturnType<typeof useNoteScopePage>
  const Comp = defineComponent({
    setup() {
      api = useNoteScopePage(options)
      return () => h('div')
    },
  })
  const w = mount(Comp)
  return { w, api }
}

beforeEach(() => {
  setActivePinia(createPinia())
  route.meta = { scope: 'mine', archived: false }
  route.query = {}
  ;[listNotes, listNoteTags, deleteNote, pinNote, confirm, success, errorMsg].forEach((f) => f.mockReset())
  router.push.mockClear()
  listNoteTags.mockResolvedValue([])
})

describe('useNoteScopePage', () => {
  it('scope 来自 route.meta，403 且 adminOnly → 空态文案', async () => {
    route.meta = { scope: 'all', archived: false }
    listNotes.mockRejectedValue(new HttpError(403, undefined, '无权限'))
    const { api } = mountPage({ adminOnly: true })
    api.list.reloadFirst()
    await flush()
    expect(api.scope.value).toBe('all')
    expect(api.forbidden.value).toBe(true)
    expect(api.errorMode.value).toBe('empty')
    expect(api.emptyText.value).toBe('仅管理员可查看')
  })

  it('403 且 emptyOn403（下属页开关关闭）→ 空态「无权查看」', async () => {
    listNotes.mockRejectedValue(new HttpError(403, undefined, '无权限'))
    const { api } = mountPage({ emptyOn403: true })
    api.list.reloadFirst()
    await flush()
    expect(api.errorMode.value).toBe('empty')
    expect(api.emptyText.value).toBe('无权查看')
  })

  it('非 403 错误仍是错误卡片；无错误时用普通空态文案', async () => {
    listNotes.mockRejectedValue(new HttpError(500, undefined, '服务异常'))
    const { api } = mountPage({ adminOnly: true })
    api.list.reloadFirst()
    await flush()
    expect(api.errorMode.value).toBe('card')
    expect(api.emptyText.value).toBe('暂无工作日志')
  })

  it('onEdit：注入宿主回调走宿主，否则跳详情', async () => {
    const host = vi.fn()
    const a = mountPage({ onOpenEditor: host })
    a.api.onEdit(1001)
    expect(host).toHaveBeenCalledWith(1001)
    expect(router.push).not.toHaveBeenCalled()

    const b = mountPage()
    b.api.onEdit(1002)
    expect(router.push).toHaveBeenCalledWith({ name: 'NoteDetail', params: { id: '1002' } })
  })

  it('onRemove 取消确认：不删除', async () => {
    listNotes.mockResolvedValue({ list: [{ id: 1001 }], total: 1, page: 1, size: 20 })
    const { api } = mountPage()
    api.list.reloadFirst()
    await flush()
    confirm.mockRejectedValueOnce(new Error('cancel'))
    await api.onRemove(1001)
    expect(confirm).toHaveBeenCalledWith('删除后不可恢复，确认删除这条工作日志？', '删除日志', expect.any(Object))
    expect(deleteNote).not.toHaveBeenCalled()
  })

  it('onRemove 确认：删除并刷新列表', async () => {
    listNotes.mockResolvedValue({ list: [{ id: 1001 }], total: 1, page: 1, size: 20 })
    const { api } = mountPage()
    api.list.reloadFirst()
    await flush()
    const before = listNotes.mock.calls.length
    confirm.mockResolvedValueOnce('confirm')
    deleteNote.mockResolvedValueOnce(undefined)
    await api.onRemove(1001)
    await flush()
    expect(deleteNote).toHaveBeenCalledWith(1001)
    expect(listNotes.mock.calls.length).toBeGreaterThan(before)
    expect(success).toHaveBeenCalledWith('已删除')
  })

  it('onPin 找不到行时静默返回', async () => {
    listNotes.mockResolvedValue({ list: [], total: 0, page: 1, size: 20 })
    const { api } = mountPage()
    api.list.reloadFirst()
    await flush()
    await api.onPin(999)
    expect(pinNote).not.toHaveBeenCalled()
  })
})
