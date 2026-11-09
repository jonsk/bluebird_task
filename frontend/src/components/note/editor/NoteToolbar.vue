<script setup lang="ts">
import { computed, ref } from 'vue'
import type { Editor } from '@tiptap/vue-3'

const props = defineProps<{
  editor: Editor
  /** 每次编辑器事务递增，用于强制重算 isActive / undo 状态。 */
  tick?: number
}>()

const emit = defineEmits<{
  (e: 'upload-image', files: File[]): void
}>()

const editor = props.editor
const fileInput = ref<HTMLInputElement | null>(null)

const highlightColors = [
  { name: '黄', color: '#fff3a3' },
  { name: '绿', color: '#c9f7d4' },
  { name: '蓝', color: '#cfe4ff' },
  { name: '粉', color: '#ffd6e7' },
]

const currentBlock = computed(() => {
  // 引用 tick，让事务后重新计算块级格式
  void props.tick
  for (const level of [1, 2, 3] as const) {
    if (editor.isActive('heading', { level })) return `h${level}`
  }
  return 'paragraph'
})

function onBlockChange(event: Event): void {
  const value = (event.target as HTMLSelectElement).value
  if (value === 'paragraph') {
    editor.chain().focus().setParagraph().run()
    return
  }
  const level = Number(value.slice(1)) as 1 | 2 | 3
  editor.chain().focus().setHeading({ level }).run()
}

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

function setTextColor(event: Event): void {
  const color = (event.target as HTMLInputElement).value
  editor.chain().focus().setColor(color).run()
}

function setHighlightColor(event: Event): void {
  const color = (event.target as HTMLInputElement).value
  editor.chain().focus().setHighlight({ color }).run()
}

function pickImages(): void {
  fileInput.value?.click()
}

function onFiles(event: Event): void {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  if (files.length) emit('upload-image', files)
  input.value = ''
}
</script>

<template>
  <div class="note-toolbar" role="toolbar" aria-label="编辑器工具栏" :data-tick="tick">
    <select class="tb-select" :value="currentBlock" title="段落格式" @change="onBlockChange">
      <option value="paragraph">正文</option>
      <option value="h1">标题 1</option>
      <option value="h2">标题 2</option>
      <option value="h3">标题 3</option>
    </select>

    <span class="tb-sep" />

    <button
      type="button"
      class="tb-btn tb-btn--bold"
      :class="{ 'is-active': editor.isActive('bold') }"
      title="粗体 (Ctrl+B)"
      @click="editor.chain().focus().toggleBold().run()"
    >
      B
    </button>
    <button
      type="button"
      class="tb-btn tb-btn--italic"
      :class="{ 'is-active': editor.isActive('italic') }"
      title="斜体 (Ctrl+I)"
      @click="editor.chain().focus().toggleItalic().run()"
    >
      I
    </button>
    <button
      type="button"
      class="tb-btn tb-btn--underline"
      :class="{ 'is-active': editor.isActive('underline') }"
      title="下划线 (Ctrl+U)"
      @click="editor.chain().focus().toggleUnderline().run()"
    >
      U
    </button>
    <button
      type="button"
      class="tb-btn tb-btn--strike"
      :class="{ 'is-active': editor.isActive('strike') }"
      title="删除线"
      @click="editor.chain().focus().toggleStrike().run()"
    >
      S
    </button>
    <button
      type="button"
      class="tb-btn tb-btn--code"
      :class="{ 'is-active': editor.isActive('code') }"
      title="行内代码"
      @click="editor.chain().focus().toggleCode().run()"
    >
      &lt;/&gt;
    </button>

    <span class="tb-sep" />

    <label class="tb-color" title="文字颜色">
      <span class="tb-color__letter">A</span>
      <input
        class="tb-color__input"
        type="color"
        :value="editor.getAttributes('textStyle').color || '#5b5bd6'"
        @input="setTextColor"
      />
    </label>

    <span class="tb-dots" title="高亮颜色">
      <button
        v-for="c in highlightColors"
        :key="c.color"
        type="button"
        class="tb-dot"
        :class="{ 'is-active': editor.isActive('highlight', { color: c.color }) }"
        :style="{ background: c.color }"
        :title="`${c.name}色高亮`"
        @click="editor.chain().focus().toggleHighlight({ color: c.color }).run()"
      />
      <label class="tb-dot tb-dot--custom" title="自定义高亮颜色">
        <input type="color" @input="setHighlightColor" />
      </label>
    </span>

    <span class="tb-sep" />

    <button
      type="button"
      class="tb-btn"
      :class="{ 'is-active': editor.isActive('bulletList') }"
      title="无序列表"
      @click="editor.chain().focus().toggleBulletList().run()"
    >
      •
    </button>
    <button
      type="button"
      class="tb-btn tb-btn--wide"
      :class="{ 'is-active': editor.isActive('orderedList') }"
      title="有序列表"
      @click="editor.chain().focus().toggleOrderedList().run()"
    >
      1.
    </button>
    <button
      type="button"
      class="tb-btn"
      :class="{ 'is-active': editor.isActive('taskList') }"
      title="待办清单"
      @click="editor.chain().focus().toggleTaskList().run()"
    >
      ☑
    </button>

    <span class="tb-sep" />

    <button
      type="button"
      class="tb-btn tb-btn--wide"
      :class="{ 'is-active': editor.isActive('link') }"
      title="链接"
      @click="setLink"
    >
      链接
    </button>
    <button
      type="button"
      class="tb-btn"
      :class="{ 'is-active': editor.isActive('blockquote') }"
      title="引用"
      @click="editor.chain().focus().toggleBlockquote().run()"
    >
      ❝
    </button>
    <button
      type="button"
      class="tb-btn tb-btn--wide"
      :class="{ 'is-active': editor.isActive('codeBlock') }"
      title="代码块"
      @click="editor.chain().focus().toggleCodeBlock().run()"
    >
      { }
    </button>
    <button
      type="button"
      class="tb-btn"
      title="分割线"
      @click="editor.chain().focus().setHorizontalRule().run()"
    >
      —
    </button>
    <button
      type="button"
      class="tb-btn"
      title="插入折叠块"
      @click="editor.chain().focus().setDetails().run()"
    >
      ▸
    </button>
    <button type="button" class="tb-btn tb-btn--wide" title="插入图片" @click="pickImages">
      图片
    </button>

    <span class="tb-sep" />

    <button
      type="button"
      class="tb-btn"
      title="撤销 (Ctrl+Z)"
      :disabled="!editor.can().undo()"
      @click="editor.chain().focus().undo().run()"
    >
      ↶
    </button>
    <button
      type="button"
      class="tb-btn"
      title="重做 (Ctrl+Shift+Z)"
      :disabled="!editor.can().redo()"
      @click="editor.chain().focus().redo().run()"
    >
      ↷
    </button>

    <input ref="fileInput" class="tb-file" type="file" accept="image/png,image/jpeg,image/gif,image/webp,image/bmp" multiple @change="onFiles" />
  </div>
</template>

