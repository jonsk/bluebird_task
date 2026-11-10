import StarterKit from '@tiptap/starter-kit'
import Placeholder from '@tiptap/extension-placeholder'
import Highlight from '@tiptap/extension-highlight'
import { TextStyle } from '@tiptap/extension-text-style'
import { Color } from '@tiptap/extension-color'
import TaskList from '@tiptap/extension-task-list'
import TaskItem from '@tiptap/extension-task-item'
import { TableKit } from '@tiptap/extension-table'
import TextAlign from '@tiptap/extension-text-align'
import { NoteIndent } from '@/utils/noteIndent'
import { NoteTypography } from '@/utils/noteTypography'
import { LegacyCode, LegacyCodeBlock } from '@/utils/noteLegacyCode'

/**
 * 周报编辑器扩展集合。
 *
 * 与工作日志共用排版插件（缩进、行距、表格、对齐），但不注册附件、提及、Emoji、
 * 斜杠命令等依赖 noteId 的节点——周报不绑定附件，避免编辑器里插入无法解析的引用。
 */
export function buildWeeklyReportExtensions(placeholder = '点击「AI 生成」自动生成周报，或直接手动撰写…') {
  return [
    StarterKit.configure({
      heading: { levels: [1, 2, 3] },
      code: false,
      codeBlock: false,
      link: { openOnClick: false, autolink: true, linkOnPaste: true },
    }),
    LegacyCode,
    LegacyCodeBlock,
    Highlight.configure({ multicolor: true }),
    TextStyle,
    Color,
    TaskList,
    TaskItem.configure({ nested: true }),
    TableKit.configure({
      table: { resizable: true, renderWrapper: true, HTMLAttributes: { class: 'note-table' } },
    }),
    TextAlign.configure({ types: ['heading', 'paragraph'] }),
    NoteTypography,
    NoteIndent,
    Placeholder.configure({ placeholder }),
  ]
}
