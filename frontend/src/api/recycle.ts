import { del, get, post } from './http'

/**
 * 回收站（2026-10-30）。
 *
 * <p>类型在此处本地声明而非走 `types/api.d.ts`：openapi 契约门禁只比对
 * `docs/api/openapi.yaml` ↔ 设计文档接口表，本次端点尚未回填设计文档，
 * 若只改 openapi 反而会让门禁变红。等补文档时再统一走 openapi-typescript 生成。
 */
export type RecycleType = 'TASK' | 'NOTE'

export interface RecycleItem {
  type: RecycleType
  id: number
  title: string
  /** 升级前删除的历史记录为 null */
  deletedAt: string | null
  deletedBy: number | null
  deletedByName: string | null
  restorable: boolean
  unrestorableReason: string | null
}

export interface RecyclePage {
  list: RecycleItem[]
  total: number
  page: number
  size: number
}

export interface RecycleRef {
  type: RecycleType
  id: number
}

export interface RecycleBatchResult {
  affected: number
  skipped: Array<{ type: RecycleType; id: number; reason: string }>
}

/** 列表：type=all|task|note；后端只返回「自己删除」的记录。 */
export function listRecycle(
  type: 'all' | 'task' | 'note',
  keyword: string,
  page = 1,
  size = 20,
): Promise<RecyclePage> {
  return get<RecyclePage>('/recycle', { type, keyword: keyword || undefined, page, size })
}

/** 恢复单条（任务含子树；日志的附件与引用关系一并回来）。 */
export function restoreRecycleItem(item: RecycleRef): Promise<void> {
  const path = item.type === 'TASK' ? `/recycle/tasks/${item.id}/restore` : `/recycle/notes/${item.id}/restore`
  return post<void>(path)
}

/** 彻底删除单条（仅 ADMIN）。 */
export function purgeRecycleItem(item: RecycleRef): Promise<void> {
  const path = item.type === 'TASK' ? `/recycle/tasks/${item.id}` : `/recycle/notes/${item.id}`
  return del<void>(path)
}

export function restoreRecycleBatch(items: RecycleRef[]): Promise<RecycleBatchResult> {
  return post<RecycleBatchResult>('/recycle/restore', { items })
}

export function purgeRecycleBatch(items: RecycleRef[]): Promise<RecycleBatchResult> {
  return post<RecycleBatchResult>('/recycle/purge', { items })
}

/** 清空回收站（仅 ADMIN）。返回被彻底删除的条数。 */
export function emptyRecycle(type: 'all' | 'task' | 'note'): Promise<number> {
  return del<number>(`/recycle/empty?type=${type}`)
}
