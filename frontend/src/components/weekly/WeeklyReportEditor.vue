<script setup lang="ts">
import { watch } from 'vue'
import { ElMessage } from 'element-plus'
import { EditorContent, useEditor } from '@tiptap/vue-3'
import { BubbleMenu } from '@tiptap/vue-3/menus'
import DragHandle from '@tiptap/extension-drag-handle-vue-3'
import { buildWeeklyReportExtensions } from '@/utils/weeklyReportExtensions'
import { sanitizeRichTextHtml, containsDangerousRichText } from '@/utils/sanitize'
import { useBlockDragHandle } from '@/composables/useBlockDragHandle'
import BbSaveStatus from '@/components/bb/BbSaveStatus.vue'
import { tableFromClipboard } from '@/utils/excelTablePaste'
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

let hydrating = false
let warnedDanger = false
const DANGER_WARN = '本文含已被过滤的嵌入式内容（视频/脚本等），编辑保存后将永久移除'
// 载入即检查：含被过滤危险标签的历史周报，在用户不知情下编辑保存会静默丢失嵌入内容。
if (containsDangerousRichText(props.modelValue || '')) {
  warnedDanger = true
  ElMessage.warning(DANGER_WARN)
}

const editor = useEditor({
  content: sanitizeRichTextHtml(props.modelValue || '<p></p>'),
  editable: !props.readonly,
  extensions: buildWeeklyReportExtensions(),
  editorProps: {
    attributes: { class: 'note-prose weekly-prose', spellcheck: 'false' },
    handlePaste: (_view, event) => {
      // 与日志编辑器保持一致：Excel 粘贴转成真正的表格节点，而不是制表符文本
      const table = tableFromClipboard(event.clipboardData)
      const ed = editor.value
      if (!table || !ed) return false
      event.preventDefault()
      ed.chain().focus().insertContent(table).run()
      return true
    },
  },
  onUpdate: ({ editor: ed }) => {
    if (hydrating) return
    emit('update:modelValue', ed.getHTML())
  },
})

watch(
  () => props.modelValue,
  (html) => {
    const ed = editor.value
    if (!ed) return
    const raw = html || '<p></p>'
    if (!warnedDanger && containsDangerousRichText(raw)) {
      warnedDanger = true
      ElMessage.warning(DANGER_WARN)
    }
    if (ed.getHTML() === raw) return
    hydrating = true
    try {
      // 周报 contentHtml 可由 API 直接写入（绕过编辑器），回显前统一消毒。
      ed.commands.setContent(sanitizeRichTextHtml(raw), { emitUpdate: false })
    } finally {
      hydrating = false
    }
  },
)

watch(
  () => props.readonly,
  (readonly) => editor.value?.setEditable(!readonly),
)

/* ==================== 块拖拽手柄（与日志编辑器共用同一套逻辑） ==================== */

const { onNodeChange, insertBelow } = useBlockDragHandle(() => editor.value)
</script>

<template>
  <div class="note-editor" data-test="weekly-editor">
    <div class="note-editor__content" :class="{ 'is-readonly': readonly }">
      <NoteToolbar
        v-if="editor && !readonly"
        :editor="editor"
        :attachments="false"
        data-test="weekly-editor-toolbar"
      />

      <div class="note-editor__main">
        <aside v-if="editor && !readonly" class="note-editor__outline" data-test="weekly-editor-outline">
          <NoteOutline :editor="editor" persistent />
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
            <NoteBubbleMenu :editor="editor" />
          </BubbleMenu>
        </div>
      </div>
    </div>

    <BbSaveStatus
      v-if="!readonly"
      :status="saveStatus"
      :saved-at="savedAt"
      :error-message="errorMessage"
      test-id="weekly-editor"
      idle-text="修改后自动保存 · 无需手动定稿"
      @retry="emit('retry')"
    />
  </div>
</template>

<style scoped>
.weekly-prose {
  min-height: 420px;
  outline: none;
}
</style>
