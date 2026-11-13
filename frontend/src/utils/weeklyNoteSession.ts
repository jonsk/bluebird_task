/**
 * 每周工作记录：本次会话内记住最后查看的周（只存内存）。
 *
 * - 首次进入由页面打开当前周；
 * - 之后点击左侧菜单恢复该周；
 * - 刷新页面即清空，回到当前周（符合「本次会话」语义）。
 */
let lastViewedWeekStart: string | null = null

export function getLastViewedWeekStart(): string | null {
  return lastViewedWeekStart
}

export function rememberViewedWeek(weekStart: string): void {
  lastViewedWeekStart = weekStart
}

export function resetWeeklyNoteSession(): void {
  lastViewedWeekStart = null
}
