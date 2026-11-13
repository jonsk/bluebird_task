import { Extension } from '@tiptap/core'

/**
 * 段落 / 标题缩进，以及列表的升 / 降级。
 *
 * 列表用 StarterKit 的 sink/liftListItem；段落和标题写 `data-indent`，
 * 由 CSS 统一换算缩进宽度，避免和 text-align / font-size 的 style 打架。
 */
export interface NoteIndentOptions {
  types: string[]
  max: number
}

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    noteIndent: {
      /** 增加一级缩进；列表项内为降级（嵌套）。 */
      indentBlock: () => ReturnType
      /** 减少一级缩进；列表项内为升级。 */
      outdentBlock: () => ReturnType
    }
  }
}

export const NoteIndent = Extension.create<NoteIndentOptions>({
  name: 'noteIndent',

  addOptions() {
    return { types: ['paragraph', 'heading'], max: 6 }
  },

  addGlobalAttributes() {
    return [
      {
        types: this.options.types,
        attributes: {
          indent: {
            default: 0,
            parseHTML: (element) => {
              const raw = Number(element.getAttribute('data-indent'))
              return Number.isFinite(raw) ? Math.min(Math.max(raw, 0), this.options.max) : 0
            },
            renderHTML: (attributes) => {
              const value = Number(attributes.indent) || 0
              return value > 0 ? { 'data-indent': String(value) } : {}
            },
          },
        },
      },
    ]
  },

  addCommands() {
    return {
      indentBlock:
        () =>
        ({ editor, commands }) => {
          const listType = editor.isActive('taskItem') ? 'taskItem' : editor.isActive('listItem') ? 'listItem' : null
          if (listType) return commands.sinkListItem(listType)

          const type = editor.isActive('heading') ? 'heading' : 'paragraph'
          const current = Number(editor.getAttributes(type).indent) || 0
          return commands.updateAttributes(type, {
            indent: Math.min(current + 1, this.options.max),
          })
        },
      outdentBlock:
        () =>
        ({ editor, commands }) => {
          const listType = editor.isActive('taskItem') ? 'taskItem' : editor.isActive('listItem') ? 'listItem' : null
          if (listType) return commands.liftListItem(listType)

          const type = editor.isActive('heading') ? 'heading' : 'paragraph'
          const current = Number(editor.getAttributes(type).indent) || 0
          return commands.updateAttributes(type, {
            indent: Math.max(current - 1, 0),
          })
        },
    }
  },

  addKeyboardShortcuts() {
    /**
     * Tab / Shift-Tab 的缩进入口。只接管段落与标题：
     * - 表格单元格里 Tab 是「跳到下一个单元格」，列表项里是 sink/lift，均交给上游处理；
     * - 其它节点（图片、附件、折叠块标题等）不拦截，保留浏览器/上游默认行为；
     * - 到达 0 / max 边界时仍然拦截，避免焦点被 Tab 带出编辑器。
     */
    const indentByTab = (direction: 'in' | 'out') => (): boolean => {
      const { editor } = this
      if (editor.isActive('table')) return false
      if (editor.isActive('taskItem') || editor.isActive('listItem')) return false
      if (!editor.isActive('paragraph') && !editor.isActive('heading')) return false

      if (direction === 'in') editor.commands.indentBlock()
      else editor.commands.outdentBlock()
      return true
    }

    return {
      Tab: indentByTab('in'),
      'Shift-Tab': indentByTab('out'),
    }
  },
})
