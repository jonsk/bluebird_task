import { http, HttpResponse } from 'msw'
import { dataOf } from './fixtures'

/**
 * Note（工作日志）MSW Mock —— 内存实现，覆盖 `docs/api/openapi.yaml` 的 note 相关端点。
 *
 * 为什么单独成文件而不塞进 `handlers.ts`：note 有一整套独立的作用域/授权/引用语义
 * （7 个 scope + 4 类 grant Tab + 附件 + 引用/被引用），与任务侧数据完全隔离，
 * 混在一起会出现「任务 fixture 变更顺手改坏日志 mock」的耦合。
 *
 * 数据基线对齐 E2E 冻结时间 `2026-09-28T10:00+08:00`（见 `e2e/helpers.ts`）：
 * 日志日期落 2026-09-20 ~ 2026-09-28，日历断言与视觉基线因此可复现。
 *
 * 角色：当前用户 = fixture `users/GET.me`（admin / ADMIN / 研发部 10）。
 * 他人 = 李四(12) / 张三(11)（研发部，admin 的下属）/ 王五(8)（市场部）。
 */

const AUTH = '/api/v1'

/* ---------------- 类型（与 openapi 对齐的子集） ---------------- */

interface TagVO {
  id: number
  name: string
}
interface NoteRow {
  id: number
  content: string
  logDate: string
  visibility: 'PRIVATE' | 'INTERNAL' | 'PUBLIC'
  pinned: boolean
  archived: boolean
  ownerId: number
  ownerName: string
  deptId: number | null
  tags: TagVO[]
  version: number
  deleted: boolean
}
interface NoteAttachmentRow {
  id: number
  noteId: number
  attachmentId: number
  fileName: string
  size: number
  contentType: string
  createdBy: number
  createdByName: string
  canRemoveRef: boolean
  canDelete: boolean
}
interface RelationRow {
  id: number
  noteId: number
  targetType: 'NOTE' | 'TASK'
  targetId: number
  targetSummary: string
  createdAt: string
  createdBy: number
  createdByName: string
}
interface GrantRow {
  id: number
  grantorId: number
  grantorName: string
  granteeId: number
  granteeName: string
  role: 'VIEWER' | 'EDITOR'
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'REVOKED'
  mode: 'OWNER' | 'SUPERIOR' | 'ADMIN'
  createdBy: number
  createdByName: string
  createdAt: string
  approvedBy: number | null
  approvedAt: string | null
  revokedAt: string | null
  revokedBy: number | null
}

const me = dataOf<{ id: number; name: string; username: string; roleCode: string; deptId?: number | null }>('users/GET.me')
const ME = Number(me.id)
const ME_NAME = me.name ?? me.username
const ME_DEPT = me.deptId ?? 10
const IS_ADMIN = me.roleCode === 'ADMIN'

const USERS: Record<number, { name: string; deptId: number | null; leader: number | null }> = {
  1: { name: '管理员', deptId: 10, leader: null },
  11: { name: '张三', deptId: 10, leader: 1 },
  12: { name: '李四', deptId: 10, leader: 1 },
  8: { name: '王五', deptId: 20, leader: null },
}
const TAG_NAMES: Record<number, string> = { 1: '重要', 2: '紧急', 3: '日常' }

const names = (ids: number[]): TagVO[] => ids.map((id) => ({ id, name: TAG_NAMES[id] ?? `标签${id}` }))

/** 初始日志：覆盖 mine / explore / shared / internal / subordinate / archived 的可见性组合。 */
let noteSeq = 1000
let refSeq = 5000
let grantSeq = 7000
const notes: NoteRow[] = [
  {
    id: 1001,
    content: '今天把工作日志的前端骨架搭起来了：列表壳、筛选条、右栏当日日志。',
    logDate: '2026-09-28',
    visibility: 'INTERNAL',
    pinned: true,
    archived: false,
    ownerId: ME,
    ownerName: ME_NAME,
    deptId: ME_DEPT,
    tags: names([1]),
    version: 1,
    deleted: false,
  },
  {
    id: 1002,
    content: '私有草稿：明天再补日志的移动端适配方案。',
    logDate: '2026-09-27',
    visibility: 'PRIVATE',
    pinned: false,
    archived: false,
    ownerId: ME,
    ownerName: ME_NAME,
    deptId: ME_DEPT,
    tags: names([3]),
    version: 1,
    deleted: false,
  },
  {
    id: 1003,
    content: '归档：旧的日志接口草案，已被新契约取代。',
    logDate: '2026-09-25',
    visibility: 'INTERNAL',
    pinned: false,
    archived: true,
    ownerId: ME,
    ownerName: ME_NAME,
    deptId: ME_DEPT,
    tags: [],
    version: 1,
    deleted: false,
  },
  {
    id: 1004,
    content: '李四的日志：完成日志列表的懒加载与缓存淘汰。',
    logDate: '2026-09-28',
    visibility: 'INTERNAL',
    pinned: false,
    archived: false,
    ownerId: 12,
    ownerName: '李四',
    deptId: 10,
    tags: names([2]),
    version: 1,
    deleted: false,
  },
  {
    id: 1005,
    content: '张三的公开日志：部门树递归查询的几个坑。',
    logDate: '2026-09-26',
    visibility: 'PUBLIC',
    pinned: false,
    archived: false,
    ownerId: 11,
    ownerName: '张三',
    deptId: 10,
    tags: [],
    version: 1,
    deleted: false,
  },
  {
    id: 1006,
    content: '王五的公开日志：市场部 Q3 复盘方法论。',
    logDate: '2026-09-24',
    visibility: 'PUBLIC',
    pinned: false,
    archived: false,
    ownerId: 8,
    ownerName: '王五',
    deptId: 20,
    tags: [],
    version: 1,
    deleted: false,
  },
  {
    id: 1007,
    content: '李四的日志：被授权给管理员查看的联调记录。',
    logDate: '2026-09-20',
    visibility: 'INTERNAL',
    pinned: false,
    archived: false,
    ownerId: 12,
    ownerName: '李四',
    deptId: 10,
    tags: names([3]),
    version: 1,
    deleted: false,
  },
]

/** OWNER 授权：李四把日志以 VIEWER 授权给管理员（已生效）。 */
const grants: GrantRow[] = [
  {
    id: 7001,
    grantorId: 12,
    grantorName: '李四',
    granteeId: ME,
    granteeName: ME_NAME,
    role: 'VIEWER',
    status: 'APPROVED',
    mode: 'OWNER',
    createdBy: 12,
    createdByName: '李四',
    createdAt: '2026-09-21T09:00:00+08:00',
    approvedBy: ME,
    approvedAt: '2026-09-21T09:05:00+08:00',
    revokedAt: null,
    revokedBy: null,
  },
  {
    id: 7002,
    grantorId: 12,
    grantorName: '李四',
    granteeId: 11,
    granteeName: '张三',
    role: 'EDITOR',
    status: 'PENDING',
    mode: 'OWNER',
    createdBy: 12,
    createdByName: '李四',
    createdAt: '2026-09-22T10:00:00+08:00',
    approvedBy: null,
    approvedAt: null,
    revokedAt: null,
    revokedBy: null,
  },
  {
    id: 7003,
    grantorId: ME,
    grantorName: ME_NAME,
    granteeId: 8,
    granteeName: '王五',
    role: 'VIEWER',
    status: 'APPROVED',
    mode: 'OWNER',
    createdBy: ME,
    createdByName: ME_NAME,
    createdAt: '2026-09-23T10:00:00+08:00',
    approvedBy: 8,
    approvedAt: '2026-09-23T10:05:00+08:00',
    revokedAt: null,
    revokedBy: null,
  },
]

const relations: RelationRow[] = [
  {
    id: 5001,
    noteId: 1001,
    targetType: 'NOTE',
    targetId: 1004,
    targetSummary: '李四的日志：完成日志列表的懒加载与缓存淘汰。',
    createdAt: '2026-09-28T11:00:00+08:00',
    createdBy: ME,
    createdByName: ME_NAME,
  },
]

const attachments: NoteAttachmentRow[] = []
const files: Record<number, { fileName: string; size: number; contentType: string; body: Uint8Array }> = {}
let fileSeq = 8800

/* ---------------- helpers ---------------- */

function ok<T>(data: T): Response {
  return HttpResponse.json({ code: 0, message: 'ok', data, traceId: 'mock-note' })
}
function fail(code: number, message: string, status = 200): Response {
  return HttpResponse.json({ code, message, data: null, traceId: 'mock-note' }, { status })
}

const alive = (n: NoteRow): boolean => !n.deleted

function isSubordinateOf(ownerId: number): boolean {
  return USERS[ownerId]?.leader === ME
}

function approvedGrantsFor(granteeId: number): GrantRow[] {
  return grants.filter((g) => g.granteeId === granteeId && g.status === 'APPROVED')
}

/** 当前用户对某条日志的权限（作者 / 编辑授权 / 只读授权 / 管理员）。 */
function permsOf(n: NoteRow): { canEdit: boolean; canManage: boolean } {
  if (n.ownerId === ME) return { canEdit: true, canManage: true }
  if (IS_ADMIN) return { canEdit: true, canManage: true }
  const granted = approvedGrantsFor(ME).filter((g) => g.grantorId === n.ownerId)
  if (granted.some((g) => g.role === 'EDITOR')) return { canEdit: true, canManage: false }
  return { canEdit: false, canManage: false }
}

/** 可见性判定：与后端 NoteQueryService 的 scope 语义保持一致（够前端联调即可）。 */
function inScope(n: NoteRow, scope: string, archived: boolean): boolean {
  if (n.archived !== archived) return false
  switch (scope) {
    case 'mine':
      return n.ownerId === ME
    case 'explore':
      return n.visibility === 'PUBLIC' && n.ownerId !== ME
    case 'shared':
      return n.ownerId !== ME && approvedGrantsFor(ME).some((g) => g.grantorId === n.ownerId)
    case 'internal':
      return n.visibility !== 'PRIVATE' && n.deptId === ME_DEPT
    case 'subordinate':
      return (n.ownerId === ME || isSubordinateOf(n.ownerId)) && n.visibility !== 'PRIVATE'
    case 'all':
      return IS_ADMIN
    case 'visible':
    default: {
      if (n.ownerId === ME) return true
      if (n.visibility === 'PUBLIC') return true
      if (n.visibility === 'INTERNAL' && n.deptId === ME_DEPT) return true
      return approvedGrantsFor(ME).some((g) => g.grantorId === n.ownerId)
    }
  }
}

function summaryOf(content: string): string {
  return content.replace(/\s+/g, ' ').trim().slice(0, 200)
}

function brief(n: NoteRow) {
  const p = permsOf(n)
  return {
    id: n.id,
    contentSummary: summaryOf(n.content),
    logDate: n.logDate,
    visibility: n.visibility,
    pinned: n.pinned,
    archived: n.archived,
    ownerId: n.ownerId,
    ownerName: n.ownerName,
    tags: n.tags,
    canEdit: p.canEdit,
    canManage: p.canManage,
    version: n.version,
  }
}

function detailOf(n: NoteRow) {
  const p = permsOf(n)
  return {
    ...brief(n),
    content: n.content,
    relations: relations.filter((r) => r.noteId === n.id),
    backlinks: relations
      .filter((r) => r.targetType === 'NOTE' && Number(r.targetId) === n.id)
      .map((r) => {
        const src = notes.find((x) => x.id === r.noteId)
        return {
          relationId: r.id,
          sourceNoteId: r.noteId,
          sourceSummary: src ? summaryOf(src.content) : '',
          sourceOwnerId: src?.ownerId ?? 0,
          sourceOwnerName: src?.ownerName ?? '',
          createdAt: r.createdAt,
          createdBy: r.createdBy,
          createdByName: r.createdByName,
        }
      }),
    attachments: attachments.filter((a) => a.noteId === n.id),
    ownerTags: p.canEdit || p.canManage ? Object.keys(TAG_NAMES).map((id) => ({ id: Number(id), name: TAG_NAMES[Number(id)] })) : null,
  }
}

function sortNotes(list: NoteRow[], sort: string): NoteRow[] {
  const byDate = (a: NoteRow, b: NoteRow): number => (a.logDate < b.logDate ? 1 : a.logDate > b.logDate ? -1 : 0)
  const sorted = [...list].sort((a, b) => {
    if (a.pinned !== b.pinned) return a.pinned ? -1 : 1
    if (sort === 'log_date_asc') return -byDate(a, b)
    if (sort === 'created_desc' || sort === 'updated_desc') return b.id - a.id
    return byDate(a, b)
  })
  return sorted
}

const nowIso = (): string => new Date().toISOString()

/* ---------------- handlers ---------------- */

/** 任务附件下载端点（task 专用，note 侧复用同一套「下载 URL」语义）。 */
function taskAttachmentHandlers() {
  return [
    http.post(`${AUTH}/tasks/:taskId/attachments`, async ({ request, params }) => {
      const form = await request.formData()
      const file = form.get('file')
      const fileName = file instanceof File ? file.name : '未命名文件'
      const size = file instanceof File ? file.size : 0
      const id = fileSeq++
      files[id] = {
        fileName,
        size,
        contentType: file instanceof File ? file.type || 'application/octet-stream' : 'application/octet-stream',
        body: file instanceof File ? new Uint8Array(await file.arrayBuffer()) : new Uint8Array(),
      }
      return ok({
        id,
        fileName,
        size,
        contentType: files[id].contentType,
        downloadUrl: `/tasks/${params.taskId}/attachments/${id}/download`,
        canDelete: true,
      })
    }),
    http.delete(`${AUTH}/tasks/:taskId/attachments/:aid`, ({ params }) => {
      delete files[Number(params.aid)]
      return ok(null)
    }),
    http.get(`${AUTH}/tasks/:taskId/attachments/:aid/download`, ({ params }) => {
      const f = files[Number(params.aid)]
      if (!f) return fail(40001, '文件不存在', 404)
      return new HttpResponse(f.body, {
        headers: {
          'Content-Type': f.contentType,
          'Content-Disposition': `attachment; filename*=UTF-8''${encodeURIComponent(f.fileName)}`,
        },
      })
    }),
    http.get(`${AUTH}/tasks/:taskId/attachments/:aid/preview`, ({ params }) => {
      const f = files[Number(params.aid)]
      if (!f) return fail(40001, '文件不存在', 404)
      return new HttpResponse(f.body, { headers: { 'Content-Type': f.contentType } })
    }),
    http.get(`${AUTH}/files/:id`, ({ params }) => {
      const f = files[Number(params.id)]
      if (!f) return fail(40001, '文件不存在', 404)
      return new HttpResponse(f.body, {
        headers: {
          'Content-Type': f.contentType,
          'Content-Disposition': `attachment; filename*=UTF-8''${encodeURIComponent(f.fileName)}`,
        },
      })
    }),
  ]
}

export const noteHandlers = [
  // ---- 通用文件上传（note 附件链路的第一跳：POST /files → bindAttachment）----
  http.get(`${AUTH}/files/config`, () =>
    ok({ maxSize: 50 * 1024 * 1024, allowedExt: ['txt', 'md', 'pdf', 'png', 'jpg', 'zip'] }),
  ),
  http.post(`${AUTH}/files`, async ({ request }) => {
    const form = await request.formData()
    const file = form.get('file')
    if (!(file instanceof File)) return fail(10001, '缺少文件', 400)
    const id = fileSeq++
    files[id] = {
      fileName: file.name,
      size: file.size,
      contentType: file.type || 'application/octet-stream',
      body: new Uint8Array(await file.arrayBuffer()),
    }
    return ok({
      id,
      fileName: file.name,
      size: file.size,
      contentType: files[id].contentType,
      downloadUrl: `/files/${id}`,
      createdBy: ME,
      canDelete: true,
    })
  }),

  // ---- 列表 / 详情 / 增删改（:id 必须在 tags|calendar 之后注册） ----
  http.get(`${AUTH}/notes/tags`, ({ request }) => {
    const url = new URL(request.url)
    const scope = url.searchParams.get('scope') ?? 'mine'
    const base = notes.filter((n) => alive(n) && inScope(n, scope, false))
    if (scope === 'mine') {
      return ok(
        Object.keys(TAG_NAMES).map((id) => ({
          id: Number(id),
          name: TAG_NAMES[Number(id)],
          count: base.filter((n) => n.tags.some((t) => t.id === Number(id))).length,
        })),
      )
    }
    const counter = new Map<string, number>()
    for (const n of base) for (const t of n.tags) counter.set(t.name, (counter.get(t.name) ?? 0) + 1)
    return ok([...counter.entries()].map(([name, count]) => ({ name, count })))
  }),

  http.get(`${AUTH}/notes/calendar`, ({ request }) => {
    const url = new URL(request.url)
    const year = Number(url.searchParams.get('year'))
    const month = Number(url.searchParams.get('month'))
    const scope = url.searchParams.get('scope') ?? 'mine'
    if (!year || !month) return fail(10001, 'year/month 必填', 400)
    const counter = new Map<string, number>()
    for (const n of notes) {
      if (!alive(n) || !inScope(n, scope, false)) continue
      const [y, m] = n.logDate.split('-').map(Number)
      if (y !== year || m !== month) continue
      counter.set(n.logDate, (counter.get(n.logDate) ?? 0) + 1)
    }
    return ok([...counter.entries()].map(([date, count]) => ({ date, count })))
  }),

  http.get(`${AUTH}/notes`, ({ request }) => {
    const url = new URL(request.url)
    const q = url.searchParams
    const scope = q.get('scope') ?? 'mine'
    const archived = q.get('archived') === 'true'
    const page = Number(q.get('page') ?? 1)
    const size = Number(q.get('size') ?? 20)
    const keyword = (q.get('keyword') ?? '').trim().toLowerCase()
    const tagId = q.get('tagId') ? Number(q.get('tagId')) : null
    const tagName = q.get('tagName') ?? ''
    const date = q.get('date') ?? ''

    let list = notes.filter((n) => alive(n) && inScope(n, scope, archived))
    if (keyword) list = list.filter((n) => n.content.toLowerCase().includes(keyword))
    if (tagId) list = list.filter((n) => n.tags.some((t) => t.id === tagId))
    if (tagName) list = list.filter((n) => n.tags.some((t) => t.name === tagName))
    if (date) list = list.filter((n) => n.logDate === date)
    if (q.get('pinned') === 'true') list = list.filter((n) => n.pinned)
    if (q.get('visibility')) list = list.filter((n) => n.visibility === q.get('visibility'))

    const sorted = sortNotes(list, q.get('sort') ?? 'log_date_desc')
    const start = (page - 1) * size
    return ok({
      list: sorted.slice(start, start + size).map(brief),
      total: sorted.length,
      page,
      size,
    })
  }),

  http.get(`${AUTH}/notes/:id`, ({ params }) => {
    const n = notes.find((x) => String(x.id) === params.id)
    if (!n || n.deleted) return fail(10004, '资源不存在', 404)
    return ok(detailOf(n))
  }),

  http.post(`${AUTH}/notes`, async ({ request }) => {
    const body = (await request.json()) as {
      content?: string
      logDate?: string
      visibility?: NoteRow['visibility']
      tagIds?: number[]
    }
    if (!body.content?.trim()) return fail(10001, '内容不能为空', 400)
    const row: NoteRow = {
      id: ++noteSeq,
      content: body.content,
      logDate: body.logDate ?? '2026-09-28',
      visibility: body.visibility ?? 'PRIVATE',
      pinned: false,
      archived: false,
      ownerId: ME,
      ownerName: ME_NAME,
      deptId: ME_DEPT,
      tags: names(body.tagIds ?? []),
      version: 1,
      deleted: false,
    }
    notes.unshift(row)
    return ok(detailOf(row))
  }),

  http.put(`${AUTH}/notes/:id`, async ({ request, params }) => {
    const n = notes.find((x) => String(x.id) === params.id)
    if (!n || n.deleted) return fail(10004, '资源不存在', 404)
    const body = (await request.json()) as {
      content?: string
      logDate?: string
      visibility?: NoteRow['visibility']
      tagIds?: number[]
      version?: number
    }
    if (!permsOf(n).canEdit) return fail(10003, '无权限', 403)
    if (typeof body.version === 'number' && body.version !== n.version) {
      return fail(10006, '内容已被他人修改，请刷新后重试')
    }
    if (body.content !== undefined) n.content = body.content
    if (body.logDate !== undefined) n.logDate = body.logDate
    if (body.visibility !== undefined) n.visibility = body.visibility
    if (body.tagIds !== undefined) n.tags = names(body.tagIds)
    n.version += 1
    return ok(detailOf(n))
  }),

  http.delete(`${AUTH}/notes/:id`, ({ params }) => {
    const n = notes.find((x) => String(x.id) === params.id)
    if (!n || n.deleted) return fail(10004, '资源不存在', 404)
    if (!permsOf(n).canManage) return fail(10003, '无权限', 403)
    n.deleted = true
    return ok(null)
  }),

  http.post(`${AUTH}/notes/:id/pin`, ({ params }) => {
    const n = notes.find((x) => String(x.id) === params.id)
    if (!n) return fail(10004, '资源不存在', 404)
    n.pinned = true
    return ok(null)
  }),
  http.delete(`${AUTH}/notes/:id/pin`, ({ params }) => {
    const n = notes.find((x) => String(x.id) === params.id)
    if (!n) return fail(10004, '资源不存在', 404)
    n.pinned = false
    return ok(null)
  }),
  http.post(`${AUTH}/notes/:id/archive`, ({ params }) => {
    const n = notes.find((x) => String(x.id) === params.id)
    if (!n) return fail(10004, '资源不存在', 404)
    n.archived = true
    return ok(null)
  }),
  http.post(`${AUTH}/notes/:id/unarchive`, ({ params }) => {
    const n = notes.find((x) => String(x.id) === params.id)
    if (!n) return fail(10004, '资源不存在', 404)
    n.archived = false
    return ok(null)
  }),

  // ---- 附件 ----
  http.get(`${AUTH}/notes/:id/attachments`, ({ params }) => {
    return ok(attachments.filter((a) => String(a.noteId) === params.id))
  }),
  http.post(`${AUTH}/notes/:id/attachments`, async ({ request, params }) => {
    const note = notes.find((x) => String(x.id) === params.id)
    if (!note) return fail(10004, '资源不存在', 404)
    const body = (await request.json()) as { attachmentId?: number }
    const fid = Number(body.attachmentId)
    if (!files[fid]) return fail(50004, '附件不存在')
    const row: NoteAttachmentRow = {
      id: refSeq++,
      noteId: note.id,
      attachmentId: fid,
      fileName: files[fid].fileName,
      size: files[fid].size,
      contentType: files[fid].contentType,
      createdBy: ME,
      createdByName: ME_NAME,
      canRemoveRef: true,
      canDelete: true,
    }
    attachments.push(row)
    return ok(row)
  }),
  http.delete(`${AUTH}/notes/:id/attachments/:aid`, ({ params }) => {
    const idx = attachments.findIndex((a) => String(a.id) === params.aid)
    if (idx >= 0) attachments.splice(idx, 1)
    return ok(null)
  }),
  http.get(`${AUTH}/notes/:id/attachments/:aid/download`, ({ params }) => {
    const row = attachments.find((a) => String(a.id) === params.aid)
    const f = row ? files[row.attachmentId] : undefined
    if (!row || !f) return fail(40001, '文件不存在', 404)
    return new HttpResponse(f.body, {
      headers: {
        'Content-Type': f.contentType,
        'Content-Disposition': `attachment; filename*=UTF-8''${encodeURIComponent(f.fileName)}`,
      },
    })
  }),
  http.get(`${AUTH}/notes/:id/attachments/:aid/preview`, ({ params }) => {
    const row = attachments.find((a) => String(a.id) === params.aid)
    const f = row ? files[row.attachmentId] : undefined
    if (!row || !f) return fail(40001, '文件不存在', 404)
    return new HttpResponse(f.body, { headers: { 'Content-Type': f.contentType } })
  }),

  // ---- 引用 / 被引用 ----
  http.get(`${AUTH}/notes/:id/relations`, ({ params }) => {
    return ok(relations.filter((r) => String(r.noteId) === params.id))
  }),
  http.post(`${AUTH}/notes/:id/relations`, async ({ request, params }) => {
    const note = notes.find((x) => String(x.id) === params.id)
    if (!note) return fail(10004, '资源不存在', 404)
    const body = (await request.json()) as { targetType?: 'NOTE' | 'TASK'; targetId?: number }
    if (!body.targetType || body.targetId == null) return fail(10001, '参数错误', 400)
    if (relations.some((r) => r.noteId === note.id && r.targetType === body.targetType && Number(r.targetId) === Number(body.targetId))) {
      return fail(50009, '该引用已存在')
    }
    const target = body.targetType === 'NOTE' ? notes.find((n) => n.id === Number(body.targetId)) : undefined
    relations.push({
      id: refSeq++,
      noteId: note.id,
      targetType: body.targetType,
      targetId: Number(body.targetId),
      targetSummary: target ? summaryOf(target.content) : `任务 #${body.targetId}`,
      createdAt: nowIso(),
      createdBy: ME,
      createdByName: ME_NAME,
    })
    return ok(relations.filter((r) => r.noteId === note.id))
  }),
  http.delete(`${AUTH}/notes/:id/relations/:rid`, ({ params }) => {
    const i = relations.findIndex((r) => String(r.id) === params.rid)
    if (i >= 0) relations.splice(i, 1)
    return ok(null)
  }),
  http.get(`${AUTH}/notes/:id/backlinks`, ({ params }) => {
    const id = Number(params.id)
    return ok(
      relations
        .filter((r) => r.targetType === 'NOTE' && Number(r.targetId) === id)
        .map((r) => {
          const src = notes.find((x) => x.id === r.noteId)
          return {
            relationId: r.id,
            sourceNoteId: r.noteId,
            sourceSummary: src ? summaryOf(src.content) : '',
            sourceOwnerId: src?.ownerId ?? 0,
            sourceOwnerName: src?.ownerName ?? '',
            createdAt: r.createdAt,
            createdBy: r.createdBy,
            createdByName: r.createdByName,
          }
        }),
    )
  }),

  // ---- 授权 ----
  http.get(`${AUTH}/note-grants`, ({ request }) => {
    const url = new URL(request.url)
    const tab = url.searchParams.get('tab') ?? 'created'
    const status = url.searchParams.get('status')
    let list = [...grants]
    if (tab === 'created') list = list.filter((g) => g.createdBy === ME)
    else if (tab === 'granted') list = list.filter((g) => g.grantorId === ME)
    else if (tab === 'incoming') list = list.filter((g) => g.granteeId === ME)
    else if (tab === 'all' && !IS_ADMIN) return ok([])
    if (status) list = list.filter((g) => g.status === status)
    return ok(
      list.map((g) => ({
        ...g,
        direction: g.grantorId === ME ? 'outgoing' : g.granteeId === ME ? 'incoming' : 'none',
        canApprove: g.granteeId === ME && g.status === 'PENDING',
        canReject: g.granteeId === ME && g.status === 'PENDING',
        canUpgrade: (g.grantorId === ME || IS_ADMIN) && g.status === 'APPROVED' && g.role === 'VIEWER',
        canRevoke: (g.grantorId === ME || IS_ADMIN) && (g.status === 'APPROVED' || g.status === 'PENDING'),
      })),
    )
  }),
  http.get(`${AUTH}/note-grants/:id`, ({ params }) => {
    const g = grants.find((x) => String(x.id) === params.id)
    if (!g) return fail(10004, '资源不存在', 404)
    return ok(g)
  }),
  http.post(`${AUTH}/note-grants`, async ({ request }) => {
    const body = (await request.json()) as Partial<GrantRow>
    const grantorId = Number(body.grantorId ?? ME)
    const granteeId = Number(body.granteeId ?? 0)
    if (!granteeId) return fail(10001, '被授权人必填', 400)
    if (granteeId === grantorId) return fail(50008, '不能给自己授权')
    const mode = (body.mode ?? 'OWNER') as GrantRow['mode']
    if (mode === 'SUPERIOR' && !isSubordinateOf(grantorId)) return fail(50011, '只能选择自己的下属')
    const row: GrantRow = {
      id: ++grantSeq,
      grantorId,
      grantorName: USERS[grantorId]?.name ?? ME_NAME,
      granteeId,
      granteeName: USERS[granteeId]?.name ?? '未知用户',
      role: (body.role ?? 'VIEWER') as GrantRow['role'],
      status: mode === 'SUPERIOR' ? 'PENDING' : 'APPROVED',
      mode,
      createdBy: ME,
      createdByName: ME_NAME,
      createdAt: nowIso(),
      approvedBy: mode === 'SUPERIOR' ? null : ME,
      approvedAt: mode === 'SUPERIOR' ? null : nowIso(),
      revokedAt: null,
      revokedBy: null,
    }
    grants.push(row)
    return ok(row)
  }),
  http.post(`${AUTH}/note-grants/:id/approve`, ({ params }) => {
    const g = grants.find((x) => String(x.id) === params.id)
    if (!g) return fail(10004, '资源不存在', 404)
    if (g.status !== 'PENDING') return fail(50014, '状态已变更，请刷新')
    g.status = 'APPROVED'
    g.approvedBy = ME
    g.approvedAt = nowIso()
    return ok(g)
  }),
  http.post(`${AUTH}/note-grants/:id/reject`, ({ params }) => {
    const g = grants.find((x) => String(x.id) === params.id)
    if (!g) return fail(10004, '资源不存在', 404)
    if (g.status !== 'PENDING') return fail(50014, '状态已变更，请刷新')
    g.status = 'REJECTED'
    return ok(g)
  }),
  http.post(`${AUTH}/note-grants/:id/upgrade`, ({ params }) => {
    const g = grants.find((x) => String(x.id) === params.id)
    if (!g) return fail(10004, '资源不存在', 404)
    if (g.role !== 'VIEWER') return fail(50014, '权限已升级，请刷新')
    g.role = 'EDITOR'
    return ok(g)
  }),
  http.delete(`${AUTH}/note-grants/:id`, ({ params }) => {
    const g = grants.find((x) => String(x.id) === params.id)
    if (!g) return fail(10004, '资源不存在', 404)
    g.status = 'REVOKED'
    g.revokedAt = nowIso()
    g.revokedBy = ME
    return ok(null)
  }),

  ...taskAttachmentHandlers(),
]
