<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'

interface MentionItem {
  id: string
  label: string
  description?: string
}

interface MentionProps {
  items: MentionItem[]
  command: (item: MentionItem) => void
}

const props = defineProps<MentionProps>()
const selected = ref(0)
const root = ref<HTMLElement | null>(null)

watch(
  () => props.items,
  () => {
    selected.value = 0
    void nextTick(() => root.value?.querySelector<HTMLElement>('.mention-item.is-selected')?.scrollIntoView({ block: 'nearest' }))
  },
)

function select(item: MentionItem): void {
  props.command(item)
}

function move(step: number): void {
  if (!props.items.length) return
  selected.value = (selected.value + step + props.items.length) % props.items.length
  void nextTick(() => root.value?.querySelector<HTMLElement>('.mention-item.is-selected')?.scrollIntoView({ block: 'nearest' }))
}

function onKeyDown({ event }: { event: KeyboardEvent }): boolean {
  if (event.isComposing) return false
  if (event.key === 'ArrowUp') {
    move(-1)
    return true
  }
  if (event.key === 'ArrowDown') {
    move(1)
    return true
  }
  if (event.key === 'Enter') {
    const item = props.items[selected.value]
    if (item) select(item)
    return true
  }
  return false
}

defineExpose({ onKeyDown })
</script>

<template>
  <div ref="root" class="mention-menu" data-test="note-mention-menu">
    <template v-if="items.length">
      <button
        v-for="(item, index) in items"
        :key="item.id"
        type="button"
        class="mention-item"
        :class="{ 'is-selected': index === selected }"
        @mousedown.prevent="select(item)"
        @mouseenter="selected = index"
      >
        <span class="mention-avatar">{{ item.label.slice(0, 1) }}</span>
        <span class="mention-text">
          <span class="mention-name">{{ item.label }}</span>
          <span v-if="item.description" class="mention-desc">{{ item.description }}</span>
        </span>
      </button>
    </template>
    <div v-else class="mention-empty">没有匹配的成员</div>
  </div>
</template>
