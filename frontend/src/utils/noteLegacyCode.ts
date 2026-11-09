import { Mark, Node, mergeAttributes } from '@tiptap/core'

/**
 * 旧日志里可能已经存在行内代码 / 代码块。
 *
 * 工具栏和斜杠菜单不再提供创建入口，但 schema 必须保留，否则重新保存旧日志时
 * `<code>`、`<pre>` 会被当成未知标签丢掉。这里只保留解析与渲染能力，不注册快捷键、
 * 输入规则或创建命令，避免功能从其他入口重新出现。
 */
export const LegacyCode = Mark.create({
  name: 'code',
  excludes: '_',
  code: true,
  exitable: true,

  parseHTML() {
    return [{ tag: 'code' }]
  },

  renderHTML({ HTMLAttributes }) {
    return ['code', mergeAttributes(HTMLAttributes), 0]
  },
})

export const LegacyCodeBlock = Node.create({
  name: 'codeBlock',
  content: 'text*',
  marks: '',
  group: 'block',
  code: true,
  defining: true,

  addAttributes() {
    return {
      language: {
        default: null,
        parseHTML: (element) => {
          const className = element.firstElementChild?.getAttribute('class') ?? ''
          return className.match(/(?:^|\s)language-([^\s]+)/)?.[1] ?? null
        },
        rendered: false,
      },
    }
  },

  parseHTML() {
    return [{ tag: 'pre', preserveWhitespace: 'full' }]
  },

  renderHTML({ node, HTMLAttributes }) {
    return [
      'pre',
      mergeAttributes(HTMLAttributes),
      [
        'code',
        { class: node.attrs.language ? `language-${node.attrs.language}` : null },
        0,
      ],
    ]
  },
})
