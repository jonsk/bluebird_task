import { Node, mergeAttributes, nodeInputRule } from '@tiptap/core'
import { PluginKey } from '@tiptap/pm/state'
import { VueRenderer } from '@tiptap/vue-3'
import {
  Suggestion,
  type SuggestionKeyDownProps,
  type SuggestionOptions,
  type SuggestionProps,
} from '@tiptap/suggestion'
import tippy, { type Instance } from 'tippy.js'
import NoteEmojiMenu from '@/components/note/editor/NoteEmojiMenu.vue'
import { NOTE_EMOJIS, type NoteEmojiItem } from '@/utils/noteEmojis'

export interface NoteEmojiOptions {
  suggestion: Omit<SuggestionOptions<NoteEmojiItem>, 'editor'>
}

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    noteEmoji: {
      /** 按 shortcode 插入一个 Emoji 节点。 */
      setEmoji: (name: string) => ReturnType
    }
  }
}

function findEmoji(name: string | null | undefined): NoteEmojiItem | undefined {
  if (!name) return undefined
  return NOTE_EMOJIS.find((item) => item.name === name || item.shortcodes?.includes(name))
}

function filterEmojis(query: string): NoteEmojiItem[] {
  const q = query.trim().toLowerCase()
  if (!q) return NOTE_EMOJIS.slice(0, 24)
  return NOTE_EMOJIS.filter((item) =>
    [item.name, item.label, ...(item.shortcodes ?? []), ...(item.tags ?? [])]
      .join(' ')
      .toLowerCase()
      .includes(q),
  ).slice(0, 24)
}

function createEmojiMenu() {
  let component: VueRenderer | null = null
  let popup: Instance[] | null = null

  return {
    onStart: (props: SuggestionProps<NoteEmojiItem>) => {
      component = new VueRenderer(NoteEmojiMenu as never, {
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
        offset: [0, 8],
        theme: 'slash',
        animation: false,
      })
    },
    onUpdate: (props: SuggestionProps<NoteEmojiItem>) => {
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
        (component?.ref as { onKeyDown?: (p: SuggestionKeyDownProps) => boolean } | null)?.onKeyDown?.(props),
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
 * 轻量 Emoji 节点。
 *
 * 只维护常用子集，避免把官方扩展的全量表情数据打进日志编辑器 chunk；
 * 支持 `:shortcode:` 输入规则、`:` 触发的建议菜单和工具栏选择。
 */
export const NoteEmoji = Node.create<NoteEmojiOptions>({
  name: 'emoji',
  inline: true,
  group: 'inline',
  atom: true,

  addOptions() {
    return {
      suggestion: {
        char: ':',
        pluginKey: new PluginKey('noteEmojiSuggestion'),
        items: ({ query }: { query: string }) => filterEmojis(query),
        render: createEmojiMenu,
        command: ({ editor, range, props }) =>
          editor
            .chain()
            .focus()
            .insertContentAt(range, [{ type: 'emoji', attrs: { name: props.name } }])
            .run(),
      } as Omit<SuggestionOptions<NoteEmojiItem>, 'editor'>,
    }
  },

  addAttributes() {
    return {
      name: {
        default: null,
        parseHTML: (element) => element.getAttribute('data-name') || element.textContent,
        renderHTML: (attributes) => ({ 'data-name': attributes.name }),
      },
    }
  },

  parseHTML() {
    return [{ tag: 'span[data-type="emoji"]' }]
  },

  renderHTML({ HTMLAttributes, node }) {
    const item = findEmoji(node.attrs.name)
    return [
      'span',
      mergeAttributes(HTMLAttributes, { 'data-type': 'emoji', class: 'note-emoji' }),
      item?.emoji || `:${node.attrs.name}:`,
    ]
  },

  renderText({ node }) {
    return findEmoji(node.attrs.name)?.emoji || `:${node.attrs.name}:`
  },

  addCommands() {
    return {
      setEmoji:
        (name) =>
        ({ chain }) => {
          if (!findEmoji(name)) return false
          return chain().insertContent({ type: this.name, attrs: { name } }).run()
        },
    }
  },

  addInputRules() {
    return [
      nodeInputRule({
        find: /:([a-zA-Z0-9_+-]+):$/,
        type: this.type,
        getAttributes: (match) => (findEmoji(match[1]) ? { name: match[1] } : null),
      }),
    ]
  },

  addProseMirrorPlugins() {
    return [
      Suggestion<NoteEmojiItem>({
        editor: this.editor,
        ...this.options.suggestion,
      }),
    ]
  },
})
