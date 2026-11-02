import { get, post, put, del } from './http'
import type {
  NoteDetailVO,
  NoteListData,
  NoteCreateReq,
  NoteUpdateReq,
  NoteAttachmentBindReq,
  NoteAttachmentVO,
  NoteCalendarDay,
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

// ---- calendar ----
export function calendar(
  year: number,
  month: number,
  scope: NoteCalendarScope = 'mine',
): Promise<NoteCalendarDay[]> {
  return get<NoteCalendarDay[]>('/notes/calendar', { year, month, scope })
}
