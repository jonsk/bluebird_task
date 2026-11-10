<script setup lang="ts">
import { ref, watch } from 'vue'
import { EditorContent, useEditor } from '@tiptap/vue-3'
import { BubbleMenu } from '@tiptap/vue-3/menus'
import DragHandle from '@tiptap/extension-drag-handle-vue-3'
import { buildWeeklyReportExtensions } from '@/utils/weeklyReportExtensions'
import NoteToolbar from '@/components/note/editor/NoteToolbar.vue'
import NoteBubbleMenu from '@/components/note/editor/NoteBubbleMenu.vue'
import NoteOutline from '@/components/note/editor/NoteOutline.vue'
import '@/styles/note-editor.css'

/**
 * 周报正文编辑器（Tiptap，受控组件）。
 *
 * 外壳与「编辑日志」（NoteEditor）完全一致：同一套工具栏（去掉插入图片/附件）、
 * 左侧目录大纲、选中浮动菜单、块拖拽手柄、底部保存状态行。
 * 区别只有两点：周报不绑定 noteId，所以没有附件/提及/斜杠命令；保存由宿主统一做自动保存。
 */
const props = withDefaults(
  defineProps<{
    modelValue: string
    readonly?: boolean
    /** 宿主自动保存的状态，用于底部状态行 */
    saveStatus?: 'idle' | 'saving' | 'saved' | 'error'
    savedAt?: string
    errorMessage?: string
  }>(),
  { readonly: false, saveStatus: 'idle', savedAt: '', errorMessage: '' },
)

const emit = defineEmits<{
  (e: 'update:modelValue', html: string): void
  (e: 'retry'): void
}>()

const tick = ref(0)
let hydrating = false

const editor = useEditor({
  content: props.modelValue || '<p></p>',
  editable: !props.readonly,
  extensions: buildWeeklyReportExtensions(),
  editorProps: {
    attributes: { class: 'note-prose weekly-prose', spellcheck: 'false' },
  },
  onUpdate: ({ editor: ed }) => {
    if (hydrating) return
    emit('update:modelValue', ed.getHTML())
  },
  onTransaction: () => {
    tick.value += 1
  },
})

watch(
  () => props.modelValue,
  (html) => {
    const ed = editor.value
    if (!ed) return
    const next = html || '<p></p>'
    if (ed.getHTML() === next) return
    hydrating = true
    try {
      ed.commands.setContent(next, { emitUpdate: false })
    } finally {
      hydrating = false
    }
  },
)

watch(
  () => props.readonly,
  (readonly) => editor.value?.setEditable(!readonly),
)

/* ==================== 块拖拽手柄 ==================== */

const hoveredBlock = ref<{ pos: number; size: number } | null>(null)

function onNodeChange({ node, pos }: { node: { nodeSize: number } | null; pos: number }): void {
  hoveredBlock.value = node ? { pos, size: node.nodeSize } : null
}

function insertBelow(): void {
  const ed = editor.value
  if (!ed) return
  const insertPos = hoveredBlock.value
    ? hoveredBlock.value.pos + hoveredBlock.value.size
    : ed.state.selection.to

  ed.chain()
    .focus()
    .insertContentAt(insertPos, { type: 'paragraph' })
    .setTextSelection(insertPos + 1)
    .run()
}
</script>

<template>
  <div class="note-editor" data-test="weekly-editor">
    <div class="note-editor__content" :class="{ 'is-readonly': readonly }">
      <NoteToolbar
        v-if="editor && !readonly"
        :editor="editor"
        :tick="tick"
        :attachments="false"
        data-test="weekly-editor-toolbar"
      />

      <div class="note-editor__main">
        <aside v-if="editor && !readonly" class="note-editor__outline" data-test="weekly-editor-outline">
          <NoteOutline :editor="editor" :tick="tick" persistent />
        </aside>

        <div class="note-editor__body">
          <DragHandle
            v-if="editor && !readonly"
            :editor="editor"
            class="drag-handle"
            :compute-position-config="{ placement: 'left-start' }"
            :on-node-change="onNodeChange"
          >
            <div class="drag-handle__inner">
              <button
                class="dh-btn"
                type="button"
                title="在下方插入段落"
                draggable="false"
                @mousedown.stop
                @click.stop="insertBelow"
              >
                +
              </button>
              <button class="dh-btn dh-grip" type="button" title="拖拽排序" draggable="false">⠿</button>
            </div>
          </DragHandle>

          <EditorContent :editor="editor" class="note-editor__editor" data-test="weekly-editor-content" />

          <BubbleMenu v-if="editor && !readonly" :editor="editor" :options="{ placement: 'top' }">
            <NoteBubbleMenu :editor="editor" :tick="tick" />
          </BubbleMenu>
        </div>
      </div>
    </div>

    <div
      v-if="!readonly"
      class="note-editor__status"
      :class="`is-${saveStatus}`"
      data-test="weekly-editor-status"
    >
      <template v-if="saveStatus === 'saving'">
        保存中…<span v-if="savedAt"> · 已保存 {{ savedAt }}</span>
      </template>
      <button
        v-else-if="saveStatus === 'error'"
        type="button"
        class="note-editor__retry"
        data-test="weekly-editor-retry"
        @click="emit('retry')"
      >
        {{ errorMessage || '保存失败' }}，点击重试<span v-if="savedAt"> · 已保存 {{ savedAt }}</span>
      </button>
      <template v-else-if="savedAt">已保存 {{ savedAt }}</template>
      <template v-else>修改后自动保存 · 无需手动定稿</template>
    </div>
  </div>
</template>

<style scoped>
.weekly-prose {
  min-height: 420px;
  outline: none;
}
</style>
