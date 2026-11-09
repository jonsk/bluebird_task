<script setup lang="ts">
import type { Editor } from '@tiptap/vue-3'

const props = defineProps<{
  editor: Editor
  tick?: number
}>()

const editor = props.editor

const highlightColors = [
  { name: '黄', color: '#fff3a3' },
  { name: '绿', color: '#c9f7d4' },
  { name: '蓝', color: '#cfe4ff' },
  { name: '粉', color: '#ffd6e7' },
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
  <div class="bubble" :data-tick="tick">
    <button
      type="button"
      class="bubble-btn bubble-btn--text"
      :class="{ 'is-active': editor.isActive('bold') }"
      title="粗体"
      @click="editor.chain().focus().toggleBold().run()"
    >
      B
    </button>
    <button
      type="button"
      class="bubble-btn bubble-btn--italic"
      :class="{ 'is-active': editor.isActive('italic') }"
      title="斜体"
      @click="editor.chain().focus().toggleItalic().run()"
    >
      I
    </button>
    <button
      type="button"
      class="bubble-btn bubble-btn--underline"
      :class="{ 'is-active': editor.isActive('underline') }"
      title="下划线"
      @click="editor.chain().focus().toggleUnderline().run()"
    >
      U
    </button>
    <button
      type="button"
      class="bubble-btn bubble-btn--strike"
      :class="{ 'is-active': editor.isActive('strike') }"
      title="删除线"
      @click="editor.chain().focus().toggleStrike().run()"
    >
      S
    </button>
    <button
      type="button"
      class="bubble-btn bubble-btn--code"
      :class="{ 'is-active': editor.isActive('code') }"
      title="行内代码"
      @click="editor.chain().focus().toggleCode().run()"
    >
      &lt;/&gt;
    </button>

    <span class="bubble-sep" />

    <button
      v-for="c in highlightColors"
      :key="c.color"
      type="button"
      class="bubble-dot"
      :class="{ 'is-active': editor.isActive('highlight', { color: c.color }) }"
      :style="{ background: c.color }"
      :title="`${c.name}色高亮`"
      @click="editor.chain().focus().toggleHighlight({ color: c.color }).run()"
    />

    <span class="bubble-sep" />

    <button type="button" class="bubble-btn bubble-btn--wide" title="链接" @click="setLink">
      链接
    </button>
    <button
      type="button"
      class="bubble-btn bubble-btn--wide"
      title="清除格式"
      @click="editor.chain().focus().unsetAllMarks().clearNodes().run()"
    >
      清除
    </button>
  </div>
</template>

