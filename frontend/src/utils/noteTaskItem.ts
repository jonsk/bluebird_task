import TaskItem from '@tiptap/extension-task-item'
import { wrappingInputRule } from '@tiptap/core'
import { Fragment, Slice, type Node as ProseMirrorNode } from '@tiptap/pm/model'
import { Plugin, type EditorState, type Transaction } from '@tiptap/pm/state'
import type { EditorView } from '@tiptap/pm/view'

const TASK_ITEM_NAME = 'taskItem'
const TASK_ITEM_INPUT_REGEX = /^\s*(\[([( |x])?\])\s$/

/** 待办项创建时间的展示格式：2026-11-16 18:30（浏览器本地时区）。 */
export function formatTaskCreatedAt(date = new Date()): string {
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/** 同时保存机器可读的 ISO 值和展示值；CSS 只读展示值，后续排序可用 ISO。 */
export function createTaskCreatedAtAttrs(date = new Date()) {
  return {
    createdAt: date.toISOString(),
    createdAtLabel: formatTaskCreatedAt(date),
  }
}

function stampMissingTaskItems(tr: Transaction, from: number, to: number): void {
  tr.doc.descendants((node: ProseMirrorNode, pos: number) => {
    if (node.type.name !== TASK_ITEM_NAME || node.attrs.createdAt) {
      return
    }

    const end = pos + node.nodeSize
    if (end <= from || pos >= to) {
      return
    }

    tr.setNodeMarkup(pos, undefined, {
      ...node.attrs,
      ...createTaskCreatedAtAttrs(),
    })
  })
}

function countTaskItems(doc: ProseMirrorNode): number {
  let count = 0
  doc.descendants((node) => {
    if (node.type.name === TASK_ITEM_NAME) {
      count += 1
    }
  })
  return count
}

function stampTaskItemAtSelection(tr: Transaction): boolean {
  const { $from } = tr.selection

  for (let depth = $from.depth; depth > 0; depth -= 1) {
    const node = $from.node(depth)
    if (node.type.name !== TASK_ITEM_NAME) {
      continue
    }

    if (!node.attrs.createdAt) {
      tr.setNodeMarkup($from.before(depth), undefined, {
        ...node.attrs,
        ...createTaskCreatedAtAttrs(),
      })
    }
    return true
  }

  return false
}

function selectedBlockRange(state: EditorState): { from: number; to: number } {
  const { $from, $to } = state.selection
  const from = $from.depth > 0 ? $from.before($from.depth) : $from.pos
  const to = $to.depth > 0 ? $to.after($to.depth) : $to.pos
  return { from, to }
}

function mapFragment(fragment: Fragment): Fragment {
  const children: ProseMirrorNode[] = []

  fragment.forEach((node) => {
    if (node.isText) {
      children.push(node)
      return
    }

    const attrs =
      node.type.name === TASK_ITEM_NAME
        ? { ...node.attrs, ...createTaskCreatedAtAttrs() }
        : node.attrs

    children.push(node.type.create(attrs, mapFragment(node.content), node.marks))
  })

  return Fragment.fromArray(children)
}

/**
 * 粘贴时把待办项视为新条目：剥掉原时间并统一写入粘贴时刻。
 * 内部拖拽不经过这里（transformPasted 会检查 view.dragging），因此移动待办项保留原时间。
 */
function resetTaskItemTimestampsInSlice(slice: Slice): Slice {
  return new Slice(mapFragment(slice.content), slice.openStart, slice.openEnd)
}

/**
 * 待办项扩展：创建时间戳 + NodeView 覆盖。
 *
 * 时间戳只在“新建”时写入：工具栏/斜杠命令切换待办清单、输入 `[ ] `、在待办项里回车、
 * 粘贴。历史待办没有 createdAt 属性时保持原样，不做回填。
 */
export const NoteTaskItem = TaskItem.extend({
  addAttributes() {
    return {
      ...(this.parent?.() ?? {}),
      createdAt: {
        default: null,
        keepOnSplit: false,
        parseHTML: (element: HTMLElement) => element.getAttribute('data-created-at') || null,
        renderHTML: (attributes: Record<string, unknown>) =>
          attributes.createdAt ? { 'data-created-at': attributes.createdAt } : {},
      },
      createdAtLabel: {
        default: null,
        keepOnSplit: false,
        parseHTML: (element: HTMLElement) =>
          element.getAttribute('data-created-at-label') || null,
        renderHTML: (attributes: Record<string, unknown>) =>
          attributes.createdAt && attributes.createdAtLabel
            ? { 'data-created-at-label': attributes.createdAtLabel }
            : {},
      },
    }
  },

  addCommands() {
    return {
      ...(this.parent?.() ?? {}),
      toggleTaskList:
        () =>
        ({ commands, state, tr }) => {
          const { from, to } = selectedBlockRange(state)
          const applied = commands.toggleList(this.name, this.options.taskListTypeName)
          if (!applied) {
            return false
          }

          stampMissingTaskItems(tr, tr.mapping.map(from, -1), tr.mapping.map(to, 1))
          return true
        },
    }
  },

  addKeyboardShortcuts() {
    return {
      ...(this.parent?.() ?? {}),
      Enter: () => {
        const beforeCount = countTaskItems(this.editor.state.doc)
        return this.editor
          .chain()
          .splitListItem(this.name)
          .command(({ tr }) => {
            // splitListItem 在空项末尾等场景会失败；只有真的多出一项才写入时间。
            if (countTaskItems(tr.doc) > beforeCount) {
              stampTaskItemAtSelection(tr)
            }
            return true
          })
          .run()
      },
    }
  },

  addInputRules() {
    return [
      wrappingInputRule({
        find: TASK_ITEM_INPUT_REGEX,
        type: this.type,
        getAttributes: (match: RegExpMatchArray) => ({
          checked: match[match.length - 1] === 'x',
          ...createTaskCreatedAtAttrs(),
        }),
      }),
    ]
  },

  addProseMirrorPlugins() {
    return [
      new Plugin({
        props: {
          transformPasted: (slice, view: EditorView) => {
            // 内部拖拽移动保留原时间；只有粘贴/外部拖入才重置。
            if ((view as unknown as { dragging?: unknown }).dragging) {
              return slice
            }
            return resetTaskItemTimestampsInSlice(slice)
          },
        },
      }),
    ]
  },

  /**
   * 待办项 NodeView 覆盖：不保留无障碍隐藏 span。
   *
   * Tiptap 3.33 的 TaskItem NodeView 每次 update 都会执行
   * `checkboxStyler.textContent = label`。这不是单纯改文本——它在编辑器 DOM 内部
   * 先删掉旧的文本节点再插入新的，Chromium 在该结构变更后恢复选区会退回一格。
   * 组合输入（中文标点，如「。」）上屏时最明显，表现为打完标点光标左移一格，
   * 连续出现会累积成落后 2、4、6 格。普通段落与有序/无序列表没有这段代码，故不受影响。
   *
   * 这里在 NodeView 创建、dom 尚未插入文档的阶段摘掉 span：此时 ProseMirror 的
   * DOMObserver 观察不到该变更，不会触发重绘（在文档内直接删除会被重绘还原，
   * 甚至因反复增删卡死）。无障碍名称由 checkbox 自身的 aria-label 提供，
   * 上游的 updateA11Y 仍在维护它，屏幕阅读器不受影响。
   */
  addNodeView() {
    const parentFactory = this.parent?.()
    if (!parentFactory) {
      return null
    }

    return (props) => {
      const nodeView = parentFactory(props)
      nodeView.dom?.querySelector('label > span')?.remove()
      return nodeView
    }
  },
})
