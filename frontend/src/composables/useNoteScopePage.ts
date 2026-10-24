import { computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useNoteList } from '@/composables/useNoteList'
import { useNoteFacets } from '@/composables/useNoteFacets'
import { useNoteActions } from '@/composables/useNoteActions'
import { HttpError } from '@/api/http'
import type { NoteBrief, NoteScope } from '@/types/note'
import type { TagOption } from '@/types/tag'

export interface UseNoteScopePageOptions {
  /** 空态文案 */
  emptyText?: string
  /** true = 403 渲染空态而非错误卡片（/notes/all、/notes/archived/all） */
  adminOnly?: boolean | (() => boolean)
  /** 非管理员访问时 403 也渲染空态（下属页：开关关闭） */
  emptyOn403?: boolean
  /** 403 空态文案 */
  forbiddenText?: string
  /** 行内编辑宿主：注入则 edit 交给宿主，不跳详情（仅 NoteHome） */
  onOpenEditor?: (id: number) => void
}

/**
 * scope 页共用逻辑：列表 / 标签 facet / 动作 + 403 空态 + 详情跳转。
 * 7 个列表页只写"scope 配置 + 标题 + 权限分支"，不复制列表逻辑。
 */
export function useNoteScopePage(options: UseNoteScopePageOptions = {}) {
  const route = useRoute()
  const router = useRouter()
  const scope = computed<NoteScope>(() => (route.meta.scope as NoteScope) || 'mine')
  const archived = computed(() => Boolean(route.meta.archived))

  const list = useNoteList()
  const facets = useNoteFacets()
  const actions = useNoteActions()

  const forbidden = computed(() => list.error.value instanceof HttpError && list.error.value.status === 403)
  const adminOnlyNow = (): boolean =>
    typeof options.adminOnly === 'function' ? options.adminOnly() : Boolean(options.adminOnly)
  const emptyModeOn403 = computed(() => Boolean(adminOnlyNow() || options.emptyOn403))
  const errorMode = computed<'card' | 'empty'>(() => (emptyModeOn403.value && forbidden.value ? 'empty' : 'card'))
  const emptyText = computed(() => {
    if (emptyModeOn403.value && forbidden.value) {
      return options.forbiddenText ?? (adminOnlyNow() ? '仅管理员可查看' : '无权查看')
    }
    return options.emptyText ?? '暂无工作日志'
  })

  const tagOptions = computed<TagOption[]>(() =>
    facets.tags.value.map((t) => ({ id: t.id, name: t.name ?? '' })).filter((t) => t.id != null) as TagOption[],
  )

  function loadFacets(): void {
    void facets.load(scope.value)
  }

  onMounted(loadFacets)
  watch([scope, archived], loadFacets)

  /** 列表行一定有 id（NoteBrief.id 在 schema 里是可选，这里收窄）。 */
  type NoteRow = NoteBrief & { id: number }

  function noteOf(id: number): NoteRow | undefined {
    const found = list.notes.value.find((n) => Number(n.id) === Number(id))
    return found ? (found as NoteRow) : undefined
  }

  function openDetail(id: number): void {
    void router.push({ name: 'NoteDetail', params: { id: String(id) } })
  }

  function onOpen(id: number): void {
    openDetail(id)
  }

  function onEdit(id: number): void {
    if (options.onOpenEditor) {
      options.onOpenEditor(id)
      return
    }
    openDetail(id)
  }

  async function withNote(id: number, fn: (note: NoteRow) => Promise<void>): Promise<void> {
    const note = noteOf(id)
    if (!note) return
    await fn(note)
    list.refresh()
  }

  async function onPin(id: number): Promise<void> {
    await withNote(id, actions.togglePin)
  }
  async function onUnpin(id: number): Promise<void> {
    await withNote(id, actions.togglePin)
  }
  async function onArchive(id: number): Promise<void> {
    await withNote(id, actions.toggleArchive)
  }
  async function onUnarchive(id: number): Promise<void> {
    await withNote(id, actions.toggleArchive)
  }
  async function onRemove(id: number): Promise<void> {
    const note = noteOf(id)
    if (!note) return
    try {
      await ElMessageBox.confirm('删除后不可恢复，确认删除这条工作日志？', '删除日志', {
        type: 'warning',
        confirmButtonText: '删除',
      })
    } catch {
      return
    }
    await actions.remove(note)
    list.refresh()
  }

  return {
    scope,
    archived,
    list,
    facets,
    actions,
    tagOptions,
    forbidden,
    errorMode,
    emptyText,
    // 展平的列表状态（模板里作为顶层绑定可自动解包）
    notes: list.notes,
    total: list.total,
    pageNo: list.page,
    loading: list.loading,
    error: list.error,
    query: list.query,
    onOpen,
    onEdit,
    onPin,
    onUnpin,
    onArchive,
    onUnarchive,
    onRemove,
    loadFacets,
  }
}
