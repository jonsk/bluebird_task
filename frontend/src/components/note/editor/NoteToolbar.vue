<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import type { Editor } from '@tiptap/vue-3'
import NoteIcon from './NoteIcon.vue'
import NoteOutline from './NoteOutline.vue'
import NoteEmojiPicker from './NoteEmojiPicker.vue'

const props = defineProps<{
  editor: Editor
  /** 每次编辑器事务递增，用于强制重算 isActive / undo 状态。 */
  tick?: number
}>()

const emit = defineEmits<{
  (e: 'upload-image', files: File[]): void
  (e: 'upload-attachment', files: File[]): void
}>()

const editor = props.editor
const fileInput = ref<HTMLInputElement | null>(null)
const attachmentInput = ref<HTMLInputElement | null>(null)
const outlineOpen = ref(false)
const emojiOpen = ref(false)
const alignOpen = ref(false)
const alignRef = ref<HTMLElement | null>(null)

const alignOptions = [
  { value: 'left', label: '左对齐', icon: 'alignLeft' },
  { value: 'center', label: '居中', icon: 'alignCenter' },
  { value: 'right', label: '右对齐', icon: 'alignRight' },
  { value: 'justify', label: '两端对齐', icon: 'alignJustify' },
] as const

const highlightColors = [
  { name: '黄', color: '#fff3a3' },
  { name: '绿', color: '#c9f7d4' },
  { name: '蓝', color: '#cfe4ff' },
  { name: '粉', color: '#ffd6e7' },
]

const fontFamilies = [
  { label: '字体', value: '' },
  { label: '宋体', value: '"Songti SC", SimSun, serif' },
  { label: '黑体', value: '"Heiti SC", SimHei, sans-serif' },
  { label: '楷体', value: '"Kaiti SC", KaiTi, serif' },
  { label: '等宽', value: '"JetBrains Mono", Consolas, monospace' },
]

const fontSizes = [
  { label: '字号', value: '' },
  { label: '12', value: '12px' },
  { label: '14', value: '14px' },
  { label: '15', value: '15px' },
  { label: '16', value: '16px' },
  { label: '18', value: '18px' },
  { label: '20', value: '20px' },
  { label: '24', value: '24px' },
  { label: '28', value: '28px' },
  { label: '32', value: '32px' },
]

const lineHeights = [
  { label: '默认 1.4', value: '' },
  { label: '紧凑 1.3', value: '1.3' },
  { label: '舒适 1.6', value: '1.6' },
  { label: '标准 1.8', value: '1.8' },
  { label: '宽松 2.0', value: '2' },
  { label: '超宽 2.4', value: '2.4' },
]

const currentBlock = computed(() => {
  // 引用 tick，让事务后重新计算块级格式
  void props.tick
  for (const level of [1, 2, 3] as const) {
    if (editor.isActive('heading', { level })) return `h${level}`
  }
  return 'paragraph'
})

const blockType = computed(() => (currentBlock.value === 'paragraph' ? 'paragraph' : 'heading'))

const blockAttrs = computed(() => {
  void props.tick
  return editor.getAttributes(blockType.value)
})

const currentFontFamily = computed(() => String(blockAttrs.value.fontFamily || ''))
const currentFontSize = computed(() => String(blockAttrs.value.fontSize || ''))
const currentLineHeight = computed(() => String(blockAttrs.value.lineHeight || ''))

function onBlockChange(event: Event): void {
  const value = (event.target as HTMLSelectElement).value
  if (value === 'paragraph') {
    editor.chain().focus().setParagraph().run()
    return
  }
  const level = Number(value.slice(1)) as 1 | 2 | 3
  editor.chain().focus().setHeading({ level }).run()
}

function setBlockAttr(attr: 'fontFamily' | 'fontSize' | 'lineHeight', event: Event): void {
  const value = (event.target as HTMLSelectElement).value
  editor.chain().focus().updateAttributes(blockType.value, { [attr]: value || null }).run()
}

function setTextAlign(align: 'left' | 'center' | 'right' | 'justify'): void {
  editor.chain().focus().setTextAlign(align).run()
}

function isAlign(align: string): boolean {
  void props.tick
  return editor.isActive({ textAlign: align })
}

const currentAlign = computed(() => {
  void props.tick
  return alignOptions.find((item) => editor.isActive({ textAlign: item.value })) ?? alignOptions[0]
})

function applyAlign(align: 'left' | 'center' | 'right' | 'justify'): void {
  setTextAlign(align)
  alignOpen.value = false
}

function closeAlignOnOutside(event: PointerEvent): void {
  if (!alignRef.value?.contains(event.target as Node)) alignOpen.value = false
}

onMounted(() => document.addEventListener('pointerdown', closeAlignOnOutside))
onBeforeUnmount(() => document.removeEventListener('pointerdown', closeAlignOnOutside))

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

function pickAttachments(): void {
  attachmentInput.value?.click()
}

function onAttachmentFiles(event: Event): void {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  if (files.length) emit('upload-attachment', files)
  input.value = ''
}

function insertTable(): void {
  editor.chain().focus().insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run()
}

function insertMention(): void {
  editor.chain().focus().insertContent('@').run()
}

function insertEmoji(name: string): void {
  editor.chain().focus().setEmoji(name).run()
  emojiOpen.value = false
}

function toggleOutline(): void {
  outlineOpen.value = !outlineOpen.value
  emojiOpen.value = false
}

function toggleEmoji(): void {
  emojiOpen.value = !emojiOpen.value
  outlineOpen.value = false
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

    <select
      class="tb-select tb-select--font"
      :value="currentFontFamily"
      title="字体"
      data-test="note-font-family"
      @change="setBlockAttr('fontFamily', $event)"
    >
      <option v-for="item in fontFamilies" :key="item.value" :value="item.value">{{ item.label }}</option>
    </select>

    <select
      class="tb-select tb-select--size"
      :value="currentFontSize"
      title="字号"
      data-test="note-font-size"
      @change="setBlockAttr('fontSize', $event)"
    >
      <option v-for="item in fontSizes" :key="item.value" :value="item.value">{{ item.label }}</option>
    </select>

    <select
      class="tb-select tb-select--size"
      :value="currentLineHeight"
      title="行距"
      data-test="note-line-height"
      @change="setBlockAttr('lineHeight', $event)"
    >
      <option v-for="item in lineHeights" :key="item.value" :value="item.value">{{ item.label }}</option>
    </select>

    <span class="tb-sep" />

    <button
      type="button"
      class="tb-btn tb-btn--bold"
      :class="{ 'is-active': editor.isActive('bold') }"
      title="粗体 (Ctrl+B)"
      @click="editor.chain().focus().toggleBold().run()"
    >
      <NoteIcon name="bold" />
    </button>
    <button
      type="button"
      class="tb-btn tb-btn--italic"
      :class="{ 'is-active': editor.isActive('italic') }"
      title="斜体 (Ctrl+I)"
      @click="editor.chain().focus().toggleItalic().run()"
    >
      <NoteIcon name="italic" />
    </button>
    <button
      type="button"
      class="tb-btn tb-btn--underline"
      :class="{ 'is-active': editor.isActive('underline') }"
      title="下划线 (Ctrl+U)"
      @click="editor.chain().focus().toggleUnderline().run()"
    >
      <NoteIcon name="underline" />
    </button>
    <button
      type="button"
      class="tb-btn tb-btn--strike"
      :class="{ 'is-active': editor.isActive('strike') }"
      title="删除线"
      @click="editor.chain().focus().toggleStrike().run()"
    >
      <NoteIcon name="strike" />
    </button>
    <span class="tb-sep" />

    <label class="tb-color" title="文字颜色">
      <NoteIcon name="textColor" />
      <input
        class="tb-color__input"
        type="color"
        :value="editor.getAttributes('textStyle').color || '#5b5bd6'"
        @input="setTextColor"
      />
    </label>

    <span class="tb-dots" title="高亮">
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

    <button
      type="button"
      class="tb-btn"
      title="清除格式"
      data-test="note-clear-format"
      @click="editor.chain().focus().unsetAllMarks().clearNodes().run()"
    >
      <NoteIcon name="clear" />
    </button>

    <span class="tb-sep" />

    <div ref="alignRef" class="tb-align">
      <button
        type="button"
        class="tb-btn tb-align__trigger"
        title="对齐"
        data-test="note-align-menu"
        :aria-expanded="alignOpen"
        @click="alignOpen = !alignOpen"
      >
        <NoteIcon :name="currentAlign.icon" />
        <span class="tb-caret" aria-hidden="true" />
      </button>
      <div v-if="alignOpen" class="tb-align__menu" role="menu">
        <button
          v-for="item in alignOptions"
          :key="item.value"
          type="button"
          class="tb-align__item"
          :class="{ 'is-active': isAlign(item.value) }"
          role="menuitem"
          :data-test="`note-align-${item.value}`"
          @click="applyAlign(item.value)"
        >
          <NoteIcon :name="item.icon" />
          <span>{{ item.label }}</span>
        </button>
      </div>
    </div>

    <button type="button" class="tb-btn" title="减少缩进" data-test="note-outdent" @click="editor.chain().focus().outdentBlock().run()">
      <NoteIcon name="outdent" />
    </button>
    <button type="button" class="tb-btn" title="增加缩进" data-test="note-indent" @click="editor.chain().focus().indentBlock().run()">
      <NoteIcon name="indent" />
    </button>

    <span class="tb-sep" />

    <button
      type="button"
      class="tb-btn"
      :class="{ 'is-active': editor.isActive('bulletList') }"
      title="无序列表"
      @click="editor.chain().focus().toggleBulletList().run()"
    >
      <NoteIcon name="bulletList" />
    </button>
    <button
      type="button"
      class="tb-btn"
      :class="{ 'is-active': editor.isActive('orderedList') }"
      title="有序列表"
      @click="editor.chain().focus().toggleOrderedList().run()"
    >
      <NoteIcon name="orderedList" />
    </button>
    <button
      type="button"
      class="tb-btn"
      :class="{ 'is-active': editor.isActive('taskList') }"
      title="待办清单"
      @click="editor.chain().focus().toggleTaskList().run()"
    >
      <NoteIcon name="taskList" />
    </button>

    <span class="tb-sep" />

    <button
      type="button"
      class="tb-btn"
      :class="{ 'is-active': editor.isActive('link') }"
      title="链接"
      @click="setLink"
    >
      <NoteIcon name="link" />
    </button>
    <button
      type="button"
      class="tb-btn"
      :class="{ 'is-active': editor.isActive('blockquote') }"
      title="引用"
      @click="editor.chain().focus().toggleBlockquote().run()"
    >
      <NoteIcon name="quote" />
    </button>
    <button
      type="button"
      class="tb-btn"
      title="分割线"
      @click="editor.chain().focus().setHorizontalRule().run()"
    >
      <NoteIcon name="horizontalRule" />
    </button>
    <button
      type="button"
      class="tb-btn"
      title="插入折叠代码"
      data-test="note-details"
      @click="editor.chain().focus().setDetails().run()"
    >
      <NoteIcon name="codeBlock" />
    </button>
    <button type="button" class="tb-btn" title="插入图片" @click="pickImages">
      <NoteIcon name="image" />
    </button>
    <button type="button" class="tb-btn" title="附件" data-test="note-attachment" @click="pickAttachments">
      <NoteIcon name="attachment" />
    </button>
    <button type="button" class="tb-btn" title="表格（3×3）" data-test="note-table" @click="insertTable">
      <NoteIcon name="table" />
    </button>

    <span class="tb-sep" />

    <button type="button" class="tb-btn" title="提及成员（输入 @）" data-test="note-mention" @click="insertMention">
      <NoteIcon name="mention" />
    </button>
    <button type="button" class="tb-btn" title="表情" data-test="note-emoji-toggle" @click="toggleEmoji">
      <NoteIcon name="emoji" />
    </button>
    <button type="button" class="tb-btn tb-toc-btn" :class="{ 'is-active': outlineOpen }" title="目录" data-test="note-toc-toggle" @click="toggleOutline">
      <NoteIcon name="toc" />
    </button>

    <span class="tb-sep" />

    <button
      type="button"
      class="tb-btn"
      title="撤销 (Ctrl+Z)"
      :disabled="!editor.can().undo()"
      @click="editor.chain().focus().undo().run()"
    >
      <NoteIcon name="undo" />
    </button>
    <button
      type="button"
      class="tb-btn"
      title="重做 (Ctrl+Shift+Z)"
      :disabled="!editor.can().redo()"
      @click="editor.chain().focus().redo().run()"
    >
      <NoteIcon name="redo" />
    </button>

    <template v-if="editor.isActive('table')">
      <span class="tb-sep" />
      <span class="tb-table-ops" data-test="note-table-ops">
        <button type="button" class="tb-text-btn" title="上方加行" @click="editor.chain().focus().addRowBefore().run()">↑行</button>
        <button type="button" class="tb-text-btn" title="下方加行" @click="editor.chain().focus().addRowAfter().run()">↓行</button>
        <button type="button" class="tb-text-btn" title="左侧加列" @click="editor.chain().focus().addColumnBefore().run()">←列</button>
        <button type="button" class="tb-text-btn" title="右侧加列" @click="editor.chain().focus().addColumnAfter().run()">→列</button>
        <button type="button" class="tb-text-btn" title="删除行" @click="editor.chain().focus().deleteRow().run()">删行</button>
        <button type="button" class="tb-text-btn" title="删除列" @click="editor.chain().focus().deleteColumn().run()">删列</button>
        <button type="button" class="tb-text-btn" title="合并单元格" @click="editor.chain().focus().mergeCells().run()">合并</button>
        <button type="button" class="tb-text-btn" title="拆分单元格" @click="editor.chain().focus().splitCell().run()">拆分</button>
        <button type="button" class="tb-text-btn" title="切换表头行" @click="editor.chain().focus().toggleHeaderRow().run()">表头</button>
        <button type="button" class="tb-text-btn is-danger" title="删除表格" @click="editor.chain().focus().deleteTable().run()">删表</button>
      </span>
    </template>

    <div v-if="outlineOpen" class="tb-popover tb-popover--outline">
      <NoteOutline :editor="editor" :tick="tick" @close="outlineOpen = false" />
    </div>

    <div v-if="emojiOpen" class="tb-popover tb-popover--emoji">
      <NoteEmojiPicker @select="insertEmoji" />
    </div>

    <input ref="fileInput" class="tb-file" type="file" accept="image/png,image/jpeg,image/gif,image/webp,image/bmp" multiple @change="onFiles" />
    <input ref="attachmentInput" class="tb-file" type="file" multiple data-test="note-attachment-input" @change="onAttachmentFiles" />
  </div>
</template>
