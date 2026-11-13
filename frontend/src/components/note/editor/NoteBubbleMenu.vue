<script setup lang="ts">
import type { Editor } from '@tiptap/vue-3'
import NoteIcon from './NoteIcon.vue'

const props = defineProps<{
  editor: Editor
}>()

const editor = props.editor

// 内容色固定，深色观感由 note-editor.css 的 --bb-hl-* 映射决定
const highlightColors = [
  { name: '黄', color: '#fff3a3', cls: 'hl-dot--yellow' },
  { name: '绿', color: '#c9f7d4', cls: 'hl-dot--green' },
  { name: '蓝', color: '#cfe4ff', cls: 'hl-dot--blue' },
  { name: '粉', color: '#ffd6e7', cls: 'hl-dot--pink' },
]

function setLink(): void {
  const previous = editor.getAttributes('link').href as string | undefined
  const url = window.prompt('输入链接地址（留空则移除）', previous || 'https://')
  if (url === null) return
  if (!url.trim()) {
    editor.chain().focus().extendMarkRange('link').unsetLink().run()
    return
  }
  editor.chain().focus().extendMarkRange('link').setLink({ href: url.trim() }).run()
}
</script>

<template>
  <div class="bubble">
    <button
      type="button"
      class="bubble-btn bubble-btn--text"
      :class="{ 'is-active': editor.isActive('bold') }"
      title="粗体"
      @click="editor.chain().focus().toggleBold().run()"
    >
      <NoteIcon name="bold" :size="16" />
    </button>
    <button
      type="button"
      class="bubble-btn bubble-btn--italic"
      :class="{ 'is-active': editor.isActive('italic') }"
      title="斜体"
      @click="editor.chain().focus().toggleItalic().run()"
    >
      <NoteIcon name="italic" :size="16" />
    </button>
    <button
      type="button"
      class="bubble-btn bubble-btn--underline"
      :class="{ 'is-active': editor.isActive('underline') }"
      title="下划线"
      @click="editor.chain().focus().toggleUnderline().run()"
    >
      <NoteIcon name="underline" :size="16" />
    </button>
    <button
      type="button"
      class="bubble-btn bubble-btn--strike"
      :class="{ 'is-active': editor.isActive('strike') }"
      title="删除线"
      @click="editor.chain().focus().toggleStrike().run()"
    >
      <NoteIcon name="strike" :size="16" />
    </button>
    <span class="bubble-sep" />

    <button
      v-for="c in highlightColors"
      :key="c.color"
      type="button"
      class="bubble-dot"
      :class="[c.cls, { 'is-active': editor.isActive('highlight', { color: c.color }) }]"
      :title="`${c.name}色高亮`"
      @click="editor.chain().focus().toggleHighlight({ color: c.color }).run()"
    />

    <span class="bubble-sep" />

    <button type="button" class="bubble-btn" title="链接（Ctrl/Cmd+单击打开）" @click="setLink">
      <NoteIcon name="link" :size="16" />
    </button>
    <button
      type="button"
      class="bubble-btn"
      title="清除格式"
      @click="editor.chain().focus().unsetAllMarks().clearNodes().run()"
    >
      <NoteIcon name="clear" :size="16" />
    </button>
  </div>
</template>

