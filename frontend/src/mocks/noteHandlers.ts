import { http, HttpResponse } from 'msw'
import { dataOf } from './fixtures'
import { canReadSubordinate, isAdminRequest } from './identity'

/**
 * Note（工作日志）MSW Mock —— 内存实现，覆盖 `docs/api/openapi.yaml` 的 note 相关端点。
 *
 * 为什么单独成文件而不塞进 `handlers.ts`：note 有一整套独立的作用域/授权/引用语义
 * （4 个 scope + 附件 + 引用/被引用），与任务侧数据完全隔离，
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

interface NoteRow {
  id: number
  content: string
  logDate: string
  ownerId: number
  ownerName: string
  deptId: number | null
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
/** 初始日志：覆盖 mine（1001/1002）/ subordinate（李四、张三）/ visible / all（后台）。 */
// 起始值需大于 fixture 最大 id（1007），否则新建日志会与既有卡片撞 id
let noteSeq = 1100
let refSeq = 5000
const notes: NoteRow[] = [
  {
    id: 1001,
    content: '今天把工作日志的前端骨架搭起来了：列表壳、筛选条、右栏当日日志。',
    logDate: '2026-09-28',
    ownerId: ME,
    ownerName: ME_NAME,
    deptId: ME_DEPT,
    version: 1,
    deleted: false,
  },
  {
    id: 1002,
    content: '私有草稿：明天再补日志的移动端适配方案。',
    logDate: '2026-09-27',
    ownerId: ME,
    ownerName: ME_NAME,
    deptId: ME_DEPT,
    version: 1,
    deleted: false,
  },
  {
    id: 1004,
    content: '李四的日志：完成日志列表的懒加载与缓存淘汰。',
    logDate: '2026-09-28',
    ownerId: 12,
    ownerName: '李四',
    deptId: 10,
    version: 1,
    deleted: false,
  },
  {
    id: 1005,
    content: '张三的日志：部门树递归查询的几个坑。',
    logDate: '2026-09-26',
    ownerId: 11,
    ownerName: '张三',
    deptId: 10,
    version: 1,
    deleted: false,
  },
  {
    id: 1006,
    content: '王五的日志：市场部 Q3 复盘方法论。',
    logDate: '2026-09-24',
    ownerId: 8,
    ownerName: '王五',
    deptId: 20,
    version: 1,
    deleted: false,
  },
  {
    id: 1007,
    content: '李四的日志：被授权给管理员查看的联调记录。',
    logDate: '2026-09-20',
    ownerId: 12,
    ownerName: '李四',
    deptId: 10,
    version: 1,
    deleted: false,
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

/** 当前用户对某条日志的权限（作者 / 编辑授权 / 只读授权 / 管理员）。 */
function permsOf(n: NoteRow): { canEdit: boolean; canManage: boolean } {
  if (n.ownerId === ME) return { canEdit: true, canManage: true }
  if (IS_ADMIN) return { canEdit: true, canManage: true }
  return { canEdit: false, canManage: false }
}

/**
 * 可见性判定：与后端 `NoteVisibilitySqlBuilder.scopePredicate` 对齐
 * （owner / 已授权 / 下属，下属受 `managerCanReadSubordinate` 门控）。
 */
function inScope(n: NoteRow, scope: string): boolean {
  switch (scope) {
    case 'mine':
      return n.ownerId === ME
    case 'subordinate':
      return isSubordinateOf(n.ownerId)
    case 'all':
      return IS_ADMIN
    case 'visible':
    default:
      if (n.ownerId === ME) return true
      return isSubordinateOf(n.ownerId)
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
    ownerId: n.ownerId,
    ownerName: n.ownerName,
    canEdit: p.canEdit,
    canManage: p.canManage,
    version: n.version,
  }
}

/**
 * 字段级可编辑性：与后端 `NoteAuthService.fieldPermissions` 同规则
 * （owner/ADMIN 全 true；其余全 false）。
 */
function fieldPermsOf(n: NoteRow): { content: boolean } {
  const p = permsOf(n)
  return { content: p.canEdit }
}

function detailOf(n: NoteRow) {
  const perms = fieldPermsOf(n)
  return {
    ...brief(n),
    content: n.content,
    fieldPermissions: perms,
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
  }
}

function sortNotes(list: NoteRow[], sort: string): NoteRow[] {
  const byDate = (a: NoteRow, b: NoteRow): number => (a.logDate < b.logDate ? 1 : a.logDate > b.logDate ? -1 : 0)
  const sorted = [...list].sort((a, b) => {
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

  // ---- 列表 / 详情 / 增删改（:id 必须注册在 calendar 之后） ----
  http.get(`${AUTH}/notes/calendar`, ({ request }) => {
    const url = new URL(request.url)
    const year = Number(url.searchParams.get('year'))
    const month = Number(url.searchParams.get('month'))
    const scope = url.searchParams.get('scope') ?? 'mine'
    if (!year || !month) return fail(10001, 'year/month 必填', 400)
    if (scope === 'all' && !isAdminRequest(request)) return fail(10003, '无权限', 403)
    if (scope === 'subordinate' && !canReadSubordinate(request)) return fail(10003, '无权限', 403)
    const counter = new Map<string, number>()
    for (const n of notes) {
      if (!alive(n) || !inScope(n, scope)) continue
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
    const page = Number(q.get('page') ?? 1)
    const size = Number(q.get('size') ?? 20)
    const keyword = (q.get('keyword') ?? '').trim().toLowerCase()
    const date = q.get('date') ?? ''

    // 权限门控（对齐后端 NoteQueryService）：all 仅 ADMIN；subordinate 需 MANAGER_CAN_READ_SUBORDINATE
    if (scope === 'all' && !isAdminRequest(request)) return fail(10003, '无权限', 403)
    if (scope === 'subordinate' && !canReadSubordinate(request)) return fail(10003, '无权限', 403)

    let list = notes.filter((n) => alive(n) && inScope(n, scope))
    if (keyword) list = list.filter((n) => n.content.toLowerCase().includes(keyword))
    if (date) list = list.filter((n) => n.logDate === date)

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
    const body = (await request.json()) as { content?: string }
    if (!body.content?.trim()) return fail(10001, '内容不能为空', 400)
    const row: NoteRow = {
      id: ++noteSeq,
      content: body.content,
      // 与后端一致：日志日期固定为创建当天（Asia/Shanghai）
      logDate: new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Shanghai' }),
      ownerId: ME,
      ownerName: ME_NAME,
      deptId: ME_DEPT,
      version: 1,
      deleted: false,
    }
    notes.unshift(row)
    return ok(detailOf(row))
  }),

  http.put(`${AUTH}/notes/:id`, async ({ request, params }) => {
    const n = notes.find((x) => String(x.id) === params.id)
    if (!n || n.deleted) return fail(10004, '资源不存在', 404)
    // e2e 钩子：单页内无法并行制造真实版本落后，故允许一次性强制冲突（用后即焚）
    if (localStorage.getItem('bb_mock_force_conflict') === '1') {
      localStorage.removeItem('bb_mock_force_conflict')
      return fail(10006, '内容已被他人修改，请刷新后重试')
    }
    const body = (await request.json()) as {
      content?: string
      logDate?: string
      version?: number
    }
    if (!permsOf(n).canEdit) return fail(10003, '无权限', 403)
    if (body.logDate !== undefined) return fail(10001, 'logDate 不可修改（日志日期由创建时间决定）', 400)
    if (typeof body.version === 'number' && body.version !== n.version) {
      return fail(10006, '内容已被他人修改，请刷新后重试')
    }
    if (body.content !== undefined) n.content = body.content
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

  ...taskAttachmentHandlers(),
]
