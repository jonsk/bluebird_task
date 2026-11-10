<script setup lang="ts">
import { ref, watch } from 'vue'
import { EditorContent, useEditor } from '@tiptap/vue-3'
import { buildWeeklyReportExtensions } from '@/utils/weeklyReportExtensions'
import '@/styles/note-editor.css'

/**
 * 周报正文编辑器（Tiptap，受控组件）。
 *
 * 与工作日志编辑器共用 schema 与 `.note-prose` 样式，但去掉附件/提及/斜杠菜单：
 * 周报是独立文档，不绑定 noteId，插入附件节点只会产生无法解析的坏链接。
 */
const props = withDefaults(
  defineProps<{
    modelValue: string
    readonly?: boolean
  }>(),
  { readonly: false },
)

const emit = defineEmits<{ (e: 'update:modelValue', html: string): void }>()

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

function isActive(name: string, attrs?: Record<string, unknown>): boolean {
  void tick.value
  return editor.value?.isActive(name, attrs) ?? false
}

function toggle(name: 'bold' | 'italic' | 'strike' | 'bulletList' | 'orderedList' | 'taskList' | 'blockquote'): void {
  const ed = editor.value
  if (!ed) return
  const chain = ed.chain().focus()
  switch (name) {
    case 'bold':
      chain.toggleBold().run()
      break
    case 'italic':
      chain.toggleItalic().run()
      break
    case 'strike':
      chain.toggleStrike().run()
      break
    case 'bulletList':
      chain.toggleBulletList().run()
      break
    case 'orderedList':
      chain.toggleOrderedList().run()
      break
    case 'taskList':
      chain.toggleTaskList().run()
      break
    case 'blockquote':
      chain.toggleBlockquote().run()
      break
  }
}

function setHeading(level: 0 | 2 | 3): void {
  const chain = editor.value?.chain().focus()
  if (!chain) return
  if (level === 0) {
    chain.setParagraph().run()
  } else {
    chain.toggleHeading({ level }).run()
  }
}

function setAlign(align: 'left' | 'center' | 'right'): void {
  editor.value?.chain().focus().setTextAlign(align).run()
}

function insertTable(): void {
  editor.value?.chain().focus().insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run()
}

function clearFormat(): void {
  editor.value?.chain().focus().clearNodes().unsetAllMarks().run()
}

function undo(): void {
  editor.value?.chain().focus().undo().run()
}

function redo(): void {
  editor.value?.chain().focus().redo().run()
}
</script>

<template>
  <div class="weekly-editor">
    <div v-if="!readonly" class="weekly-editor__toolbar" data-test="weekly-editor-toolbar">
      <button type="button" class="tb" :class="{ 'is-active': isActive('bold') }" title="加粗" @click="toggle('bold')">
        <strong>B</strong>
      </button>
      <button type="button" class="tb" :class="{ 'is-active': isActive('italic') }" title="斜体" @click="toggle('italic')">
        <em>I</em>
      </button>
      <button type="button" class="tb" :class="{ 'is-active': isActive('strike') }" title="删除线" @click="toggle('strike')">
        <s>S</s>
      </button>
      <span class="tb-sep"></span>
      <button type="button" class="tb tb--text" :class="{ 'is-active': isActive('heading', { level: 2 }) }" @click="setHeading(2)">
        H2
      </button>
      <button type="button" class="tb tb--text" :class="{ 'is-active': isActive('heading', { level: 3 }) }" @click="setHeading(3)">
        H3
      </button>
      <button type="button" class="tb tb--text" @click="setHeading(0)">正文</button>
      <span class="tb-sep"></span>
      <button type="button" class="tb tb--text" :class="{ 'is-active': isActive('bulletList') }" title="无序列表" @click="toggle('bulletList')">
        •
      </button>
      <button type="button" class="tb tb--text" :class="{ 'is-active': isActive('orderedList') }" title="有序列表" @click="toggle('orderedList')">
        1.
      </button>
      <button type="button" class="tb tb--text" :class="{ 'is-active': isActive('taskList') }" title="待办" @click="toggle('taskList')">
        ☑
      </button>
      <button type="button" class="tb tb--text" :class="{ 'is-active': isActive('blockquote') }" title="引用" @click="toggle('blockquote')">
        ❝
      </button>
      <span class="tb-sep"></span>
      <button type="button" class="tb tb--text" title="左对齐" @click="setAlign('left')">左</button>
      <button type="button" class="tb tb--text" title="居中" @click="setAlign('center')">中</button>
      <button type="button" class="tb tb--text" title="右对齐" @click="setAlign('right')">右</button>
      <button type="button" class="tb tb--text" title="插入表格" @click="insertTable">▦</button>
      <span class="tb-sep"></span>
      <button type="button" class="tb tb--text" title="清除格式" @click="clearFormat">清除</button>
      <button type="button" class="tb tb--text" title="撤销" @click="undo">↶</button>
      <button type="button" class="tb tb--text" title="重做" @click="redo">↷</button>
    </div>

    <EditorContent :editor="editor" class="weekly-editor__content" data-test="weekly-editor-content" />
  </div>
</template>

<style scoped>
.weekly-editor {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--bb-radius-sm);
  background: var(--bg-primary);
  overflow: hidden;
}
.weekly-editor__toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 2px;
  padding: 6px 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: var(--bg-secondary);
}
.tb {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 28px;
  height: 28px;
  padding: 0 6px;
  border: 0;
  border-radius: var(--bb-radius);
  background: transparent;
  color: var(--bb-text-secondary);
  font-size: 13px;
  cursor: pointer;
}
.tb:hover {
  background: var(--bg-hover);
}
.tb.is-active {
  background: var(--bg-active);
  color: var(--bb-color-primary);
}
.tb--text {
  font-size: 12px;
}
.tb-sep {
  width: 1px;
  height: 18px;
  margin: 0 4px;
  background: var(--el-border-color-lighter);
}
.weekly-editor__content {
  min-height: 460px;
  padding: 18px 22px 28px;
}
.weekly-editor__content :deep(.tiptap) {
  min-height: 420px;
  outline: none;
}
</style>
