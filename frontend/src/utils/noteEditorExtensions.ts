import StarterKit from '@tiptap/starter-kit'
import Placeholder from '@tiptap/extension-placeholder'
import Highlight from '@tiptap/extension-highlight'
import { TextStyle } from '@tiptap/extension-text-style'
import { Color } from '@tiptap/extension-color'
import TaskList from '@tiptap/extension-task-list'
import TaskItem from '@tiptap/extension-task-item'
import Details, { DetailsContent, DetailsSummary } from '@tiptap/extension-details'
import Image from '@tiptap/extension-image'
import { TableKit } from '@tiptap/extension-table'
import TextAlign from '@tiptap/extension-text-align'
import { TableOfContents } from '@tiptap/extension-table-of-contents'
import { FindAndReplace } from '@tiptap/extension-find-and-replace'
import { SlashCommand } from '@/utils/slashCommand'
import { NoteAttachment } from '@/utils/noteAttachment'
import { NoteEmoji } from '@/utils/noteEmoji'
import { NoteIndent } from '@/utils/noteIndent'
import { NoteMention } from '@/utils/noteMention'
import { NoteTypography } from '@/utils/noteTypography'

export interface NoteExtensionOptions {
  /** editor = 可编辑（带占位符）；render = 只读详情渲染。 */
  mode?: 'editor' | 'render'
  placeholder?: string
}

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
  const extensions = [
    StarterKit.configure({
      heading: { levels: [1, 2, 3] },
      link: {
        openOnClick: mode === 'render',
        autolink: true,
        linkOnPaste: true,
      },
    }),
    Highlight.configure({ multicolor: true }),
    TextStyle,
    Color,
    TaskList,
    TaskItem.configure({ nested: true }),
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
    Image.configure({
      inline: false,
      allowBase64: false,
      HTMLAttributes: { class: 'note-image' },
    }),
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
    NoteAttachment,
    NoteMention,
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
      SlashCommand,
      FindAndReplace.configure({ searchDebounceMs: 0 }),
      Placeholder.configure({
        placeholder: options.placeholder ?? '记录今日工作…（Ctrl+Enter 保存）',
      }),
    ]
  }

  return extensions
}

