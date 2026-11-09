import StarterKit from '@tiptap/starter-kit'
import Placeholder from '@tiptap/extension-placeholder'
import Highlight from '@tiptap/extension-highlight'
import { TextStyle } from '@tiptap/extension-text-style'
import { Color } from '@tiptap/extension-color'
import TaskList from '@tiptap/extension-task-list'
import TaskItem from '@tiptap/extension-task-item'
import Details, { DetailsContent, DetailsSummary } from '@tiptap/extension-details'
import Image from '@tiptap/extension-image'

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
 * Slash 菜单是运行期通过 `registerSlashCommand` 注册的插件，不在这里；
 * 这样可避免直接依赖 `@tiptap/core` 的 `Extension.create`。
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
  ]

  if (mode === 'editor') {
    return [
      ...extensions,
      Placeholder.configure({
        placeholder: options.placeholder ?? '记录今日工作…（Ctrl+Enter 保存）',
      }),
    ]
  }

  return extensions
}

