import { ref } from 'vue'
import type { Editor } from '@tiptap/core'

/**
 * Tiptap 块拖拽手柄的公共逻辑：记录当前 hover 的块位置，并在其下方插入段落。
 *
 * NoteEditor 与 WeeklyReportEditor 的编辑器外壳一致，这段 `hoveredBlock / onNodeChange /
 * insertBelow` 原先整份复制（含边界处理）。抽成 composable 后两个编辑器共用一套行为。
 *
 * 参数用 getter 而非 ref，避免把 `ShallowRef<Editor | undefined>` 的具体类型泄漏到调用方。
 */
export function useBlockDragHandle(getEditor: () => Editor | undefined) {
  const hoveredBlock = ref<{ pos: number; size: number } | null>(null)

  function onNodeChange({ node, pos }: { node: { nodeSize: number } | null; pos: number }): void {
    hoveredBlock.value = node ? { pos, size: node.nodeSize } : null
  }

  function insertBelow(): void {
    const editor = getEditor()
    if (!editor) return
    const insertPos = hoveredBlock.value
      ? hoveredBlock.value.pos + hoveredBlock.value.size
      : editor.state.selection.to

    editor.chain()
      .focus()
      .insertContentAt(insertPos, { type: 'paragraph' })
      .setTextSelection(insertPos + 1)
      .run()
  }

  return { onNodeChange, insertBelow }
}
