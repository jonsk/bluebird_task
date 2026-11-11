<script setup lang="ts">
/**
 * 自动保存状态行（NoteEditor / WeeklyReportEditor 共用）。
 *
 * 两个编辑器原先各写一份结构完全相同、仅文案与 data-test 前缀不同的状态行。
 * 样式来自全局 @/styles/note-editor.css（.note-editor__status / .note-editor__retry），
 * 因此移到公共组件后外观与作用域不变。
 */
withDefaults(
  defineProps<{
    status: 'idle' | 'saving' | 'saved' | 'error'
    savedAt?: string
    errorMessage?: string
    /** 生成 `data-test="<testId>-status"` 与 `-retry`；E2E 依赖这些属性。 */
    testId?: string
    idleText?: string
    errorFallback?: string
  }>(),
  {
    savedAt: '',
    errorMessage: '',
    testId: '',
    idleText: '修改后自动保存',
    errorFallback: '保存失败',
  },
)

const emit = defineEmits<{ (e: 'retry'): void }>()
</script>

<template>
  <div
    class="note-editor__status"
    :class="`is-${status}`"
    :data-test="testId ? `${testId}-status` : undefined"
  >
    <template v-if="status === 'saving'">
      保存中…<span v-if="savedAt"> · 已保存 {{ savedAt }}</span>
    </template>
    <button
      v-else-if="status === 'error'"
      type="button"
      class="note-editor__retry"
      :data-test="testId ? `${testId}-retry` : undefined"
      @click="emit('retry')"
    >
      {{ errorMessage || errorFallback }}，点击重试<span v-if="savedAt"> · 已保存 {{ savedAt }}</span>
    </button>
    <template v-else-if="savedAt">已保存 {{ savedAt }}</template>
    <template v-else>{{ idleText }}</template>
  </div>
</template>
