<script setup lang="ts">
import { computed, onMounted } from 'vue'
import type { Editor } from '@tiptap/vue-3'

const props = defineProps<{
  editor: Editor
  tick?: number
  persistent?: boolean
}>()

const emit = defineEmits<{ (e: 'close'): void }>()

const items = computed(() => {
  void props.tick
  return props.editor.storage.tableOfContents?.content ?? []
})

onMounted(() => {
  props.editor.commands.updateTableOfContents()
})

function jump(pos: number): void {
  props.editor.chain().focus().setTextSelection(pos + 1).run()
  const dom = props.editor.view.nodeDOM(pos)
  if (dom instanceof HTMLElement) dom.scrollIntoView({ behavior: 'smooth', block: 'center' })
  if (!props.persistent) emit('close')
}
</script>

<template>
  <div class="note-outline" :data-test="persistent ? 'note-outline-panel-inner' : 'note-outline'">
    <div class="note-outline__title">文档目录</div>
    <div v-if="items.length" class="note-outline__list">
      <button
        v-for="item in items"
        :key="item.id"
        type="button"
        class="note-outline__item"
        :class="{ 'is-active': item.isActive }"
        :style="{ paddingLeft: `${(item.level - 1) * 12 + 10}px` }"
        @click="jump(item.pos)"
      >
        {{ item.textContent || '未命名标题' }}
      </button>
    </div>
    <div v-else class="note-outline__empty">还没有标题</div>
  </div>
</template>
