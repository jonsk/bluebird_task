import { get, post, put, del } from './http'
import type {
  NoteDetailVO,
  NoteListData,
  NoteCreateReq,
  NoteUpdateReq,
  NoteRelationCreateReq,
  NoteRelationVO,
  NoteBacklinkVO,
  NoteAttachmentBindReq,
  NoteAttachmentVO,
  NoteTagFacetItem,
  NoteCalendarDay,
  NoteUserGrantReq,
  NoteUserGrantVO,
  NoteGrantListData,
  NoteScope,
  NoteCalendarScope,
  NoteQuery,
} from '@/types/note'

export function listNotes(query: NoteQuery): Promise<NoteListData> {
  return get<NoteListData>('/notes', { ...query })
}

export function getNote(id: number): Promise<NoteDetailVO> {
  return get<NoteDetailVO>(`/notes/${id}`)
}

export function createNote(payload: NoteCreateReq): Promise<NoteDetailVO> {
  return post<NoteDetailVO>('/notes', payload)
}

export function updateNote(id: number, payload: NoteUpdateReq): Promise<NoteDetailVO> {
  return put<NoteDetailVO>(`/notes/${id}`, payload)
}

export function deleteNote(id: number): Promise<void> {
  return del<void>(`/notes/${id}`)
}

export function pinNote(id: number): Promise<void> {
  return post<void>(`/notes/${id}/pin`)
}

export function unpinNote(id: number): Promise<void> {
  return del<void>(`/notes/${id}/pin`)
}

export function archiveNote(id: number): Promise<void> {
  return post<void>(`/notes/${id}/archive`)
}

export function unarchiveNote(id: number): Promise<void> {
  return post<void>(`/notes/${id}/unarchive`)
}

// ---- 附件 ----
export function listAttachments(id: number): Promise<NoteAttachmentVO[]> {
  return get<NoteAttachmentVO[]>(`/notes/${id}/attachments`)
}

export function bindAttachment(id: number, payload: NoteAttachmentBindReq): Promise<NoteAttachmentVO> {
  return post<NoteAttachmentVO>(`/notes/${id}/attachments`, payload)
}

export function removeAttachment(id: number, attachmentId: number): Promise<void> {
  return del<void>(`/notes/${id}/attachments/${attachmentId}`)
}

// ---- 引用 ----
export function listRelations(id: number): Promise<NoteRelationVO[]> {
  return get<NoteRelationVO[]>(`/notes/${id}/relations`)
}

export function addRelation(id: number, payload: NoteRelationCreateReq): Promise<NoteRelationVO[]> {
  return post<NoteRelationVO[]>(`/notes/${id}/relations`, payload)
}

export function removeRelation(id: number, relationId: number): Promise<void> {
  return del<void>(`/notes/${id}/relations/${relationId}`)
}

export function listBacklinks(id: number): Promise<NoteBacklinkVO[]> {
  return get<NoteBacklinkVO[]>(`/notes/${id}/backlinks`)
}

// ---- facets / calendar ----
export function listNoteTags(scope: NoteScope, keyword?: string): Promise<NoteTagFacetItem[]> {
  return get<NoteTagFacetItem[]>('/notes/tags', { scope, keyword })
}

export function calendar(
  year: number,
  month: number,
  scope: NoteCalendarScope = 'mine',
): Promise<NoteCalendarDay[]> {
  return get<NoteCalendarDay[]>('/notes/calendar', { year, month, scope })
}

// ---- 授权 ----
export function listGrants(tab: string, status?: string, role?: string, mode?: string): Promise<NoteUserGrantVO[]> {
  return get<NoteUserGrantVO[]>('/note-grants', { tab, status, role, mode })
}

export function getGrant(id: number): Promise<NoteUserGrantVO> {
  return get<NoteUserGrantVO>(`/note-grants/${id}`)
}

export function createGrant(payload: NoteUserGrantReq): Promise<NoteUserGrantVO> {
  return post<NoteUserGrantVO>('/note-grants', payload)
}

export function approveGrant(id: number): Promise<NoteUserGrantVO> {
  return post<NoteUserGrantVO>(`/note-grants/${id}/approve`)
}

export function rejectGrant(id: number): Promise<NoteUserGrantVO> {
  return post<NoteUserGrantVO>(`/note-grants/${id}/reject`)
}

export function upgradeGrant(id: number): Promise<NoteUserGrantVO> {
  return post<NoteUserGrantVO>(`/note-grants/${id}/upgrade`)
}

export function revokeGrant(id: number): Promise<void> {
  return del<void>(`/note-grants/${id}`)
}

// 授权列表分页（若后端返回数组，本地分页封装）
export function listGrantsPaged(tab: string, page: number, size: number, filters?: { status?: string; role?: string; mode?: string }): Promise<NoteGrantListData> {
  return listGrants(tab, filters?.status, filters?.role, filters?.mode).then((list) => {
    const total = list.length
    const start = (page - 1) * size
    return {
      list: list.slice(start, start + size),
      total,
      page,
      size,
    } as NoteGrantListData
  })
}
