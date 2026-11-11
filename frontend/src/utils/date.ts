/**
 * 日期格式化（03 §4.5）。统一 ISO8601 + +08:00，替换旧 fomatDateFun。
 */

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

/**
 * 本地墙钟时间 → 带 +08:00 偏移的 ISO8601（契约统一格式）。
 *
 * 入参按「+08 墙钟」解释：el-date-picker 的 value-format 字符串、`new Date(y, m, d)`
 * 本地构造都产出浏览器本地墙钟分量，而目标用户均在 +08，本地 getter 取到的就是 +08
 * 时区的时/分/秒。这里直接把日历分量拼成 `+08:00` 的 ISO 串，**不做任何跨时区换算**，
 * 因此不会因为 getTimezoneOffset 二次换算产生 ±8h 错位（旧实现在 UTC 下会把 18:00 存成 02:00）。
 *
 * ⚠️ 语义前提：仅当运行环境时区为 +08（即用户所在时区）时，落库值与用户所见一致。
 * 若浏览器处于其他时区，本函数仍按「本地墙钟即 +08 墙钟」处理，不会自动纠正。
 */
export function toOffsetIso(input: Date | string | number): string {
  const d = input instanceof Date ? input : new Date(input)
  const p = (n: number): string => (n < 10 ? `0${n}` : String(n))
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}+08:00`
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

/** 上海时区的当天日期（YYYY-MM-DD），禁止 toISOString().slice（会偏移到 UTC）。 */
export function todayInShanghai(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai' }).format(new Date())
}

/** todayInShanghai 的同义别名。 */
export const todayDate = todayInShanghai

function pad(n: number): string {
  return n < 10 ? `0${n}` : String(n)
}
