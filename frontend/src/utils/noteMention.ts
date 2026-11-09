import { mergeAttributes } from '@tiptap/core'
import Mention from '@tiptap/extension-mention'
import { VueRenderer } from '@tiptap/vue-3'
import {
  type SuggestionKeyDownProps,
  type SuggestionOptions,
  type SuggestionProps,
} from '@tiptap/suggestion'
import tippy, { type Instance } from 'tippy.js'
import NoteMentionMenu from '@/components/note/editor/NoteMentionMenu.vue'
import { listUsers } from '@/api/user'

export interface NoteMentionItem {
  id: string
  label: string
  description?: string
}

async function searchUsers(query: string): Promise<NoteMentionItem[]> {
  try {
    const page = await listUsers({ keyword: query.trim(), size: 8 })
    return (page.list ?? []).map((user) => ({
      id: String(user.id ?? ''),
      label: user.name || user.username || '未命名成员',
      description: [user.deptName, user.username ? `@${user.username}` : ''].filter(Boolean).join(' · '),
    }))
  } catch {
    return []
  }
}

function createMentionMenu() {
  let component: VueRenderer | null = null
  let popup: Instance[] | null = null

  return {
    onStart: (props: SuggestionProps<NoteMentionItem>) => {
      component = new VueRenderer(NoteMentionMenu as never, {
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
    onUpdate: (props: SuggestionProps<NoteMentionItem>) => {
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

export const NoteMention = Mention.configure({
  HTMLAttributes: { class: 'note-mention' },
  renderText: ({ node }) => `@${node.attrs.label ?? node.attrs.id ?? ''}`,
  renderHTML: ({ options, node }) => [
    'span',
    mergeAttributes(options.HTMLAttributes, {
      'data-type': 'mention',
      'data-id': node.attrs.id,
      'data-label': node.attrs.label,
    }),
    `@${node.attrs.label ?? node.attrs.id ?? ''}`,
  ],
  suggestion: {
    char: '@',
    allowSpaces: false,
    items: ({ query }: { query: string }) => searchUsers(query),
    render: createMentionMenu,
    command: ({ editor, range, props }) =>
      editor
        .chain()
        .focus()
        .insertContentAt(range, [
          { type: 'mention', attrs: { id: props.id, label: props.label } },
          { type: 'text', text: ' ' },
        ])
        .run(),
  } as Omit<SuggestionOptions<NoteMentionItem>, 'editor'>,
})
