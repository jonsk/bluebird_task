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
export type NoteTagFacetItem = components['schemas']['NoteTagFacetItem']
export type NoteCalendarDay = components['schemas']['NoteCalendarDay']
export type NoteUserGrantReq = components['schemas']['NoteUserGrantReq']
export type NoteUserGrantVO = components['schemas']['NoteUserGrantVO']
export type TagVO = components['schemas']['TagVO']
export type TaskSearchData = components['schemas']['TaskSearchData']
export type TaskSearchItem = components['schemas']['TaskSearchItem']
export type FileVO = components['schemas']['FileVO']

export type NoteVisibility = 'PRIVATE' | 'INTERNAL' | 'PUBLIC'
export type NoteGrantRole = 'VIEWER' | 'EDITOR'
export type NoteGrantStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'REVOKED'
export type NoteGrantMode = 'OWNER' | 'SUPERIOR' | 'ADMIN'

/** 7 个列表作用域（visible 仅用于引用选择器搜索与查询值，无独立列表页）。 */
export type NoteScope = 'mine' | 'shared' | 'explore' | 'internal' | 'subordinate' | 'visible' | 'all'
/** 日历作用域（不含 visible）。 */
export type NoteCalendarScope = Exclude<NoteScope, 'visible'>

/** 排序（蛇形，与后端 NoteQueryService.applySort 对齐；pinned 恒为第一排序键）。 */
export type NoteSort = 'log_date_desc' | 'log_date_asc' | 'created_desc' | 'updated_desc'

export interface NoteQuery {
  scope?: NoteScope
  tagId?: number
  tagName?: string
  keyword?: string
  visibility?: NoteVisibility
  pinned?: boolean
  archived?: boolean
  start?: string
  end?: string
  date?: string
  sort?: NoteSort
  page?: number
  size?: number
}

/** setQuery 入参：剔除 scope/page/size（防绕过 route.meta 与分页单一入口）。 */
export type NoteQueryPatch = Partial<Omit<NoteQuery, 'scope' | 'page' | 'size'>>

export interface NoteGrantListData {
  list: NoteUserGrantVO[]
  total: number
  page: number
  size: number
}

/** 新建后临时卡片（与时间戳一起存，TTL 30 分钟）。 */
export interface NoteDraft {
  id: number
  ts: number
}

export type NoteTargetRef = { targetType: 'NOTE' | 'TASK'; targetId: number }

/** 品牌类型（落到 attachmentId 而非 fileId，类型系统阻止误传 id）。 */
export type AttachmentRefId = number
export type FileId = number
