<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { EditorContent, useEditor } from '@tiptap/vue-3'
import { buildNoteExtensions } from '@/utils/noteEditorExtensions'
import { watchMarkdownImages, type ImageHydrator } from '@/utils/markdownImages'
import { toEditorHtml } from '@/utils/richText'
import '@/styles/note-editor.css'

/**
 * 日志正文只读渲染器。
 *
 * 与 NoteEditor 使用同一套 Tiptap schema 和 `.note-prose` 样式：
 * - 正文一律按 HTML 处理，只做消毒后交给编辑器解析；
 * - 折叠块可点击展开、待办只读展示、笔记图片继续走 Bearer blob 水合。
 */
const props = defineProps<{ content?: string | null }>()

const rootRef = ref<HTMLElement | null>(null)
let hydrator: ImageHydrator | null = null

const editor = useEditor({
  content: '<p></p>',
  editable: false,
  extensions: buildNoteExtensions({ mode: 'render' }),
  editorProps: {
    attributes: { class: 'note-prose note-renderer__prose' },
  },
})

function apply(): void {
  const ed = editor.value
  if (!ed) return
  hydrator?.stop()
  hydrator = null
  ed.commands.setContent(toEditorHtml(props.content), { emitUpdate: false })
  void nextTick(() => {
    hydrator = watchMarkdownImages(() => rootRef.value)
  })
}

onMounted(apply)
watch(() => props.content, apply)
onBeforeUnmount(() => {
  hydrator?.stop()
  hydrator = null
})
</script>

<template>
  <div ref="rootRef" class="note-renderer">
    <EditorContent :editor="editor" />
  </div>
</template>
