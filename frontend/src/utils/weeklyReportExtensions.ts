import { buildNoteExtensions, WEEKLY_PLACEHOLDER } from './noteEditorExtensions'

/**
 * 周报编辑器扩展集合：与工作日志复用同一套 schema（缩进 / 行距 / 表格 / 折叠块 /
 * Emoji / 目录），但不注册附件、提及、斜杠命令等依赖 noteId 的节点——周报不绑定附件。
 */
export function buildWeeklyReportExtensions(placeholder = WEEKLY_PLACEHOLDER) {
  return buildNoteExtensions({ variant: 'weekly', placeholder })
}
