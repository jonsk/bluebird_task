<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import type { Editor } from '@tiptap/vue-3'

const props = defineProps<{
  editor: Editor
  persistent?: boolean
}>()

const emit = defineEmits<{ (e: 'close'): void }>()

const items = computed(() => props.editor.storage.tableOfContents?.content ?? [])

const listRef = ref<HTMLElement | null>(null)
const activeId = computed<string | null>(() => items.value.find((i) => i.isActive)?.id ?? null)

/**
 * 大纲有自己的滚动区（高度受限时内部滚动）。当前所在标题可能滚出可视区，
 * 这里只调整大纲自身的 scrollTop，不惊动正文/页面的滚动位置。
 */
watch(activeId, async (id) => {
  if (id == null) return
  await nextTick()
  const box = listRef.value
  const el = box?.querySelector<HTMLElement>('[data-outline-active="true"]') ?? null
  if (!box || !el) return
  const b = box.getBoundingClientRect()
  const r = el.getBoundingClientRect()
  if (r.top < b.top) box.scrollTop -= b.top - r.top
  else if (r.bottom > b.bottom) box.scrollTop += r.bottom - b.bottom
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
    <div v-if="items.length" ref="listRef" class="note-outline__list">
      <button
        v-for="item in items"
        :key="item.id"
        type="button"
        class="note-outline__item"
        :class="{ 'is-active': item.isActive }"
        :data-outline-active="item.isActive ? 'true' : undefined"
        :style="{ paddingLeft: `${(item.level - 1) * 12 + 10}px` }"
        @click="jump(item.pos)"
      >
        {{ item.textContent || '未命名标题' }}
      </button>
    </div>
    <div v-else class="note-outline__empty">还没有标题</div>
  </div>
</template>
