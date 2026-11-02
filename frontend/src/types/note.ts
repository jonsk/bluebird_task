import type { components } from '@/types/api'

/** 从 OpenAPI 生成的 note 类型（禁止手写 any 兜底）。 */
export type NoteBrief = components['schemas']['NoteBrief']
export type NoteVO = components['schemas']['NoteVO']
export type NoteDetailVO = components['schemas']['NoteDetailVO']
export type NoteListData = components['schemas']['NoteListData']
export type NoteCreateReq = components['schemas']['NoteCreateReq']
export type NoteUpdateReq = components['schemas']['NoteUpdateReq']
export type NoteRelationCreateReq = components['schemas']['NoteRelationCreateReq']
export type NoteRelationVO = components['schemas']['NoteRelationVO']
export type NoteBacklinkVO = components['schemas']['NoteBacklinkVO']
export type NoteAttachmentBindReq = components['schemas']['NoteAttachmentBindReq']
export type NoteAttachmentVO = components['schemas']['NoteAttachmentVO']
export type NoteCalendarDay = components['schemas']['NoteCalendarDay']
export type TagVO = components['schemas']['TagVO']
export type TaskSearchData = components['schemas']['TaskSearchData']
export type TaskSearchItem = components['schemas']['TaskSearchItem']
export type FileVO = components['schemas']['FileVO']

/** 4 个列表作用域（visible 仅用于内部可见性等价，无独立列表页）。 */
export type NoteScope = 'mine' | 'subordinate' | 'visible' | 'all'
/** 日历作用域（不含 visible）。 */
export type NoteCalendarScope = Exclude<NoteScope, 'visible' | 'all'>

/** 排序（蛇形，与后端 NoteQueryService.applySort 对齐）。 */
export type NoteSort = 'log_date_desc' | 'log_date_asc' | 'created_desc' | 'updated_desc'

export interface NoteQuery {
  scope?: NoteScope
  keyword?: string
  start?: string
  end?: string
  date?: string
  sort?: NoteSort
  page?: number
  size?: number
}

/** setQuery 入参：剔除 scope/page/size（防绕过 route.meta 与分页单一入口）。 */
export type NoteQueryPatch = Partial<Omit<NoteQuery, 'scope' | 'page' | 'size'>>

/** 新建后临时卡片（与时间戳一起存，TTL 30 分钟）。 */
export interface NoteDraft {
  id: number
  ts: number
}

export type NoteTargetRef = { targetType: 'NOTE' | 'TASK'; targetId: number }

/** 品牌类型（落到 attachmentId 而非 fileId，类型系统阻止误传 id）。 */
export type AttachmentRefId = number
export type FileId = number
