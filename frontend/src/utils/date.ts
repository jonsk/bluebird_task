/**
 * 日期格式化（03 §4.5）。统一 ISO8601 + +08:00，替换旧 fomatDateFun。
 */

const SHANGHAI_OFFSET_MIN = 8 * 60

/** 将后端 ISO 字符串或 Date 解析为本地显示字符串（yyyy-MM-dd HH:mm）。 */
export function formatDateTime(input?: string | number | Date | null): string {
  if (!input) return ''
  const date = input instanceof Date ? input : new Date(input)
  if (Number.isNaN(date.getTime())) return ''
  const y = date.getFullYear()
  const m = pad(date.getMonth() + 1)
  const d = pad(date.getDate())
  const hh = pad(date.getHours())
  const mm = pad(date.getMinutes())
  return `${y}-${m}-${d} ${hh}:${mm}`
}

export function formatDate(input?: string | number | Date | null): string {
  const dt = formatDateTime(input)
  return dt ? dt.slice(0, 10) : ''
}

/** 是否逾期（含当天之前且未完成）。 */
export function isOverdue(dueAt?: string | null, completed = false): boolean {
  if (!dueAt || completed) return false
  return new Date(dueAt).getTime() < Date.now()
}

/** 是否临期（24 小时内）。 */
export function isNearDue(dueAt?: string | null, completed = false): boolean {
  if (!dueAt || completed) return false
  const diff = new Date(dueAt).getTime() - Date.now()
  return diff >= 0 && diff <= 24 * 3600 * 1000
}

/** 本地时间 → 带 +08:00 偏移的 ISO8601（契约统一格式）。 */
export function toOffsetIso(input: Date | string | number): string {
  const date = input instanceof Date ? input : new Date(input)
  const utcMs = date.getTime() + date.getTimezoneOffset() * 60_000
  const local = new Date(utcMs + SHANGHAI_OFFSET_MIN * 60_000)
  const base = local.toISOString().slice(0, 19)
  return `${base}+08:00`
}

/** 主区标题下的日期行（旧 `.r-b-t-l-l-t-bottom`）：`9月30日 星期三`。 */
export function formatHeaderDate(input?: string | number | Date | null): string {
  const date = input ? (input instanceof Date ? input : new Date(input)) : new Date()
  if (Number.isNaN(date.getTime())) return ''
  const week = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六'][date.getDay()]
  return `${date.getMonth() + 1}月${date.getDate()}日 ${week}`
}

/** 任务卡「创建于」段（旧 `.span-generated-on`）：`09月05日`。 */
export function formatCreatedDate(input?: string | number | Date | null): string {
  if (!input) return ''
  const date = input instanceof Date ? input : new Date(input)
  if (Number.isNaN(date.getTime())) return ''
  return `${pad(date.getMonth() + 1)}月${pad(date.getDate())}日`
}

function pad(n: number): string {
  return n < 10 ? `0${n}` : String(n)
}
