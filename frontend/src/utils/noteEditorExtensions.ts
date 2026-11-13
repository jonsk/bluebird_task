import StarterKit from '@tiptap/starter-kit'
import Placeholder from '@tiptap/extension-placeholder'
import Highlight from '@tiptap/extension-highlight'
import { TextStyle } from '@tiptap/extension-text-style'
import { Color } from '@tiptap/extension-color'
import TaskList from '@tiptap/extension-task-list'
import Details, { DetailsContent, DetailsSummary } from '@tiptap/extension-details'
import Image from '@tiptap/extension-image'
import { TableKit } from '@tiptap/extension-table'
import TextAlign from '@tiptap/extension-text-align'
import { TableOfContents } from '@tiptap/extension-table-of-contents'
import { SlashCommand } from '@/utils/slashCommand'
import { NoteAttachment } from '@/utils/noteAttachment'
import { NoteEmoji } from '@/utils/noteEmoji'
import { NoteIndent } from '@/utils/noteIndent'
import { NoteLinkClick } from '@/utils/noteLinkClick'
import { NoteTaskItem } from '@/utils/noteTaskItem'
import { NoteTypography } from '@/utils/noteTypography'

export interface NoteExtensionOptions {
  /** editor = 可编辑（带占位符）；render = 只读详情渲染。 */
  mode?: 'editor' | 'render'
  placeholder?: string
  /** weekly = 周报编辑器：不注册依赖 noteId 的节点（附件/图片/斜杠菜单/链接点击）。 */
  variant?: 'note' | 'weekly'
}

export const NOTE_PLACEHOLDER = '记录今日工作…（Ctrl+Enter 保存）'
export const WEEKLY_PLACEHOLDER = '点击「AI 生成」自动生成 AI 周工作总结，或直接手动撰写…'

/**
 * 日志编辑器 / 只读渲染共用的 Tiptap 扩展集合。
 *
 * 两边必须使用同一套 schema：否则详情页会把编辑器里合法的节点（折叠块、待办、颜色）
 * 当成未知标签丢掉，同一篇日志在编辑态与详情态排版不一致。
 *
 * Slash 菜单只在编辑态启用；它必须在扩展创建期注册，不能等编辑器创建后再挂插件。
 */
export function buildNoteExtensions(options: NoteExtensionOptions = {}) {
  const mode = options.mode ?? 'editor'
  const isWeekly = options.variant === 'weekly'
  const extensions = [
    StarterKit.configure({
      heading: { levels: [1, 2, 3] },
      // 不提供行内代码 / 代码块：旧数据里的 <code>、<pre> 会按普通文本解析。
      code: false,
      codeBlock: false,
      link: {
        openOnClick: mode === 'render' && !isWeekly,
        autolink: true,
        linkOnPaste: true,
      },
    }),
    Highlight.configure({ multicolor: true }),
    TextStyle,
    Color,
    TaskList,
    NoteTaskItem.configure({ nested: true }),
    Details.configure({
      persist: true,
      openClassName: 'is-open',
      renderToggleButton: ({ element, isOpen }) => {
        element.classList.add('details-toggle')
        element.textContent = ''
        element.setAttribute('aria-label', isOpen ? '收起' : '展开')
      },
    }),
    DetailsSummary,
    DetailsContent,
    ...(isWeekly
      ? []
      : [
          Image.configure({
            inline: false,
            allowBase64: false,
            HTMLAttributes: { class: 'note-image' },
          }),
        ]),
    TableKit.configure({
      table: {
        resizable: true,
        renderWrapper: true,
        HTMLAttributes: { class: 'note-table' },
      },
    }),
    TextAlign.configure({ types: ['heading', 'paragraph'] }),
    NoteTypography,
    NoteIndent,
    ...(isWeekly ? [] : [NoteAttachment]),
    NoteEmoji,
    TableOfContents.configure({
      anchorTypes: ['heading'],
      getId: (text) => {
        const slug = text
          .trim()
          .toLowerCase()
          .replace(/\s+/g, '-')
          .replace(/[^\w\u4e00-\u9fa5-]/g, '')
        return slug ? `toc-${slug}` : 'toc-heading'
      },
    }),
  ]

  if (mode === 'editor') {
    return [
      ...extensions,
      ...(isWeekly ? [] : [NoteLinkClick, SlashCommand]),
      Placeholder.configure({
        placeholder: options.placeholder ?? (isWeekly ? WEEKLY_PLACEHOLDER : NOTE_PLACEHOLDER),
      }),
    ]
  }

  return extensions
}

