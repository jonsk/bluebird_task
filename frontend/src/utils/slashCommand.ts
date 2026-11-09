import { Extension, type Editor } from '@tiptap/core'
import { VueRenderer } from '@tiptap/vue-3'
import {
  Suggestion,
  type SuggestionKeyDownProps,
  type SuggestionOptions,
  type SuggestionProps,
} from '@tiptap/suggestion'
import tippy, { type Instance } from 'tippy.js'
import SlashMenu from '@/components/note/editor/SlashMenu.vue'
import type { NoteIconName } from '@/utils/noteIcons'

export interface SlashItem {
  title: string
  description: string
  icon: NoteIconName
  group: string
  tone?: string
  keywords?: string[]
  command: (props: { editor: Editor; range: { from: number; to: number } }) => void
}

const items: SlashItem[] = [
  {
    title: '正文',
    description: '普通段落文字',
    icon: 'paragraph',
    group: '基础',
    tone: 'neutral',
    keywords: ['paragraph', 'text', 'p'],
    command: ({ editor, range }) =>
      editor.chain().focus().deleteRange(range).setParagraph().run(),
  },
  {
    title: '标题 1',
    description: '大号标题，用于文档主标题',
    icon: 'heading1',
    group: '基础',
    tone: 'heading',
    keywords: ['h1', 'heading', 'title'],
    command: ({ editor, range }) =>
      editor.chain().focus().deleteRange(range).setNode('heading', { level: 1 }).run(),
  },
  {
    title: '标题 2',
    description: '中号标题，用于章节',
    icon: 'heading2',
    group: '基础',
    tone: 'heading',
    keywords: ['h2', 'heading', 'section'],
    command: ({ editor, range }) =>
      editor.chain().focus().deleteRange(range).setNode('heading', { level: 2 }).run(),
  },
  {
    title: '标题 3',
    description: '小号标题，用于小节',
    icon: 'heading3',
    group: '基础',
    tone: 'heading',
    keywords: ['h3', 'heading'],
    command: ({ editor, range }) =>
      editor.chain().focus().deleteRange(range).setNode('heading', { level: 3 }).run(),
  },
  {
    title: '引用',
    description: '突出引用或结论',
    icon: 'quote',
    group: '基础',
    tone: 'quote',
    keywords: ['quote', 'blockquote', '引用'],
    command: ({ editor, range }) =>
      editor.chain().focus().deleteRange(range).toggleBlockquote().run(),
  },
  {
    title: '代码块',
    description: '保留换行的代码片段',
    icon: 'codeBlock',
    group: '基础',
    tone: 'code',
    keywords: ['code', 'codeblock', '代码'],
    command: ({ editor, range }) =>
      editor.chain().focus().deleteRange(range).setCodeBlock().run(),
  },
  {
    title: '无序列表',
    description: '用圆点组织要点',
    icon: 'bulletList',
    group: '列表',
    tone: 'list',
    keywords: ['bullet', 'ul', 'list', '列表'],
    command: ({ editor, range }) =>
      editor.chain().focus().deleteRange(range).toggleBulletList().run(),
  },
  {
    title: '有序列表',
    description: '用数字组织步骤',
    icon: 'orderedList',
    group: '列表',
    tone: 'list',
    keywords: ['ordered', 'ol', 'list', '列表'],
    command: ({ editor, range }) =>
      editor.chain().focus().deleteRange(range).toggleOrderedList().run(),
  },
  {
    title: '待办清单',
    description: '可勾选的任务列表',
    icon: 'taskList',
    group: '列表',
    tone: 'task',
    keywords: ['todo', 'task', 'check', '待办'],
    command: ({ editor, range }) =>
      editor.chain().focus().deleteRange(range).toggleTaskList().run(),
  },
  {
    title: '分割线',
    description: '在内容之间加一条分线',
    icon: 'horizontalRule',
    group: '插入',
    tone: 'neutral',
    keywords: ['hr', 'divider', 'line', '分割线'],
    command: ({ editor, range }) =>
      editor.chain().focus().deleteRange(range).setHorizontalRule().run(),
  },
  {
    title: '折叠块',
    description: '可展开/收起的详情区域',
    icon: 'details',
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
 * 「行首或空格后输入 /」的块命令菜单。
 *
 * 必须作为编辑器扩展在创建期注册，而不是在 `onCreate` 里调用
 * `editor.registerPlugin`：Suggestion 的插件视图、命令和退出生命周期
 * 依赖创建期的 plugin 配置，后挂载会出现“菜单能出现、选中后不生效”。
 */
export const SlashCommand = Extension.create({
  name: 'slashCommand',

  addOptions() {
    return {
      suggestion: {
        char: '/',
        allowSpaces: true,
        startOfLine: false,
        command: ({ editor, range, props }: { editor: Editor; range: { from: number; to: number }; props: SlashItem }) =>
          props.command({ editor, range }),
        items: ({ query }: { query: string }) => filterItems(query),
        render: createMenu,
      } as Omit<SuggestionOptions<SlashItem>, 'editor'>,
    }
  },

  addProseMirrorPlugins() {
    return [
      Suggestion<SlashItem>({
        editor: this.editor,
        ...this.options.suggestion,
      }),
    ]
  },
})

