import { VueRenderer } from '@tiptap/vue-3'
import { Suggestion, type SuggestionKeyDownProps, type SuggestionProps } from '@tiptap/suggestion'
import tippy, { type Instance } from 'tippy.js'
import SlashMenu from '@/components/note/editor/SlashMenu.vue'

type SuggestionEditor = Parameters<typeof Suggestion>[0]['editor']

export interface SlashItem {
  title: string
  description: string
  icon: string
  group: string
  tone?: string
  keywords?: string[]
  command: (props: { editor: SuggestionEditor; range: { from: number; to: number } }) => void
}

const items: SlashItem[] = [
  {
    title: '正文',
    description: '普通段落文字',
    icon: 'T',
    group: '基础',
    tone: 'neutral',
    keywords: ['paragraph', 'text', 'p'],
    command: ({ editor, range }) =>
      editor.chain().focus().deleteRange(range).setParagraph().run(),
  },
  {
    title: '标题 1',
    description: '大号标题，用于文档主标题',
    icon: 'H1',
    group: '基础',
    tone: 'heading',
    keywords: ['h1', 'heading', 'title'],
    command: ({ editor, range }) =>
      editor.chain().focus().deleteRange(range).setNode('heading', { level: 1 }).run(),
  },
  {
    title: '标题 2',
    description: '中号标题，用于章节',
    icon: 'H2',
    group: '基础',
    tone: 'heading',
    keywords: ['h2', 'heading', 'section'],
    command: ({ editor, range }) =>
      editor.chain().focus().deleteRange(range).setNode('heading', { level: 2 }).run(),
  },
  {
    title: '标题 3',
    description: '小号标题，用于小节',
    icon: 'H3',
    group: '基础',
    tone: 'heading',
    keywords: ['h3', 'heading'],
    command: ({ editor, range }) =>
      editor.chain().focus().deleteRange(range).setNode('heading', { level: 3 }).run(),
  },
  {
    title: '引用',
    description: '突出引用或结论',
    icon: '❝',
    group: '基础',
    tone: 'quote',
    keywords: ['quote', 'blockquote', '引用'],
    command: ({ editor, range }) =>
      editor.chain().focus().deleteRange(range).toggleBlockquote().run(),
  },
  {
    title: '代码块',
    description: '保留换行的代码片段',
    icon: '</>',
    group: '基础',
    tone: 'code',
    keywords: ['code', 'codeblock', '代码'],
    command: ({ editor, range }) =>
      editor.chain().focus().deleteRange(range).setCodeBlock().run(),
  },
  {
    title: '无序列表',
    description: '用圆点组织要点',
    icon: '•',
    group: '列表',
    tone: 'list',
    keywords: ['bullet', 'ul', 'list', '列表'],
    command: ({ editor, range }) =>
      editor.chain().focus().deleteRange(range).toggleBulletList().run(),
  },
  {
    title: '有序列表',
    description: '用数字组织步骤',
    icon: '1.',
    group: '列表',
    tone: 'list',
    keywords: ['ordered', 'ol', 'list', '列表'],
    command: ({ editor, range }) =>
      editor.chain().focus().deleteRange(range).toggleOrderedList().run(),
  },
  {
    title: '待办清单',
    description: '可勾选的任务列表',
    icon: '☑',
    group: '列表',
    tone: 'task',
    keywords: ['todo', 'task', 'check', '待办'],
    command: ({ editor, range }) =>
      editor.chain().focus().deleteRange(range).toggleTaskList().run(),
  },
  {
    title: '分割线',
    description: '在内容之间加一条分线',
    icon: '—',
    group: '插入',
    tone: 'neutral',
    keywords: ['hr', 'divider', 'line', '分割线'],
    command: ({ editor, range }) =>
      editor.chain().focus().deleteRange(range).setHorizontalRule().run(),
  },
  {
    title: '折叠块',
    description: '可展开/收起的详情区域',
    icon: '▸',
    group: '插入',
    tone: 'details',
    keywords: ['details', 'toggle', '折叠', '收起'],
    command: ({ editor, range }) =>
      editor
        .chain()
        .focus()
        .deleteRange(range)
        .insertContent({
          type: 'details',
          attrs: { open: true },
          content: [
            {
              type: 'detailsSummary',
              content: [{ type: 'text', text: '折叠块标题' }],
            },
            {
              type: 'detailsContent',
              content: [
                {
                  type: 'paragraph',
                  content: [{ type: 'text', text: '点击左侧箭头展开或收起。' }],
                },
              ],
            },
          ],
        })
        .run(),
  },
]

function filterItems(query: string): SlashItem[] {
  const q = query.trim().toLowerCase()
  if (!q) return items
  return items.filter((item) => {
    const haystack = [item.title, item.description, item.group, ...(item.keywords || [])]
      .join(' ')
      .toLowerCase()
    return haystack.includes(q)
  })
}

function createMenu() {
  let component: VueRenderer | null = null
  let popup: Instance[] | null = null

  return {
    onStart: (props: SuggestionProps<SlashItem>) => {
      component = new VueRenderer(SlashMenu as never, {
        props,
        editor: props.editor,
      })
      if (!props.clientRect || !component.element) return

      popup = tippy('body', {
        getReferenceClientRect: props.clientRect as () => DOMRect,
        appendTo: () => document.body,
        content: component.element as Element,
        showOnCreate: true,
        interactive: true,
        trigger: 'manual',
        placement: 'bottom-start',
        offset: [0, 10],
        maxWidth: 'none',
        theme: 'slash',
        animation: false,
      })
    },
    onUpdate: (props: SuggestionProps<SlashItem>) => {
      component?.updateProps(props)
      if (!props.clientRect || !popup?.[0]) return
      popup[0].setProps({ getReferenceClientRect: props.clientRect as () => DOMRect })
    },
    onKeyDown: (props: SuggestionKeyDownProps) => {
      if (props.event.key === 'Escape') {
        popup?.[0]?.hide()
        return true
      }
      return Boolean(
        (component?.ref as { onKeyDown?: (p: SuggestionKeyDownProps) => boolean } | null)?.onKeyDown?.(
          props,
        ),
      )
    },
    onExit: () => {
      popup?.[0]?.destroy()
      popup = null
      component?.destroy()
      component = null
    },
  }
}

/**
 * 挂载「行首或空格后输入 /」的块命令菜单。
 *
 * 直接 `editor.registerPlugin(Suggestion(...))`，不额外依赖 `@tiptap/core` 的
 * `Extension.create`；这样也与项目 pnpm 的 peer 依赖布局解耦。
 */
export function registerSlashCommand(editor: SuggestionEditor): void {
  const plugin = Suggestion<SlashItem>({
    editor,
    char: '/',
    allowSpaces: true,
    startOfLine: false,
    command: ({ editor: ed, range, props }) => props.command({ editor: ed, range }),
    items: ({ query }) => filterItems(query),
    render: createMenu,
  })
  editor.registerPlugin(plugin)
}

