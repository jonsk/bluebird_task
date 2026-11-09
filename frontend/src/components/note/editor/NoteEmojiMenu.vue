<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import type { NoteEmojiItem } from '@/utils/noteEmojis'

interface EmojiProps {
  items: NoteEmojiItem[]
  command: (item: NoteEmojiItem) => void
}

const props = defineProps<EmojiProps>()
const selected = ref(0)
const root = ref<HTMLElement | null>(null)

watch(
  () => props.items,
  () => {
    selected.value = 0
    void nextTick(() => root.value?.querySelector<HTMLElement>('.emoji-item.is-selected')?.scrollIntoView({ block: 'nearest' }))
  },
)

function select(item: NoteEmojiItem): void {
  props.command(item)
}

function move(step: number): void {
  if (!props.items.length) return
  selected.value = (selected.value + step + props.items.length) % props.items.length
  void nextTick(() => root.value?.querySelector<HTMLElement>('.emoji-item.is-selected')?.scrollIntoView({ block: 'nearest' }))
}

function onKeyDown({ event }: { event: KeyboardEvent }): boolean {
  if (event.isComposing) return false
  if (event.key === 'ArrowUp' || event.key === 'ArrowLeft') {
    move(-1)
    return true
  }
  if (event.key === 'ArrowDown' || event.key === 'ArrowRight') {
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
  <div ref="root" class="emoji-menu" data-test="note-emoji-menu">
    <template v-if="items.length">
      <button
        v-for="(item, index) in items"
        :key="item.name"
        type="button"
        class="emoji-item"
        :class="{ 'is-selected': index === selected }"
        :title="`:${item.shortcodes?.[0] ?? item.name}:`"
        @mousedown.prevent="select(item)"
        @mouseenter="selected = index"
      >
        {{ item.emoji || `:${item.name}:` }}
      </button>
    </template>
    <div v-else class="emoji-empty">没有匹配的表情</div>
  </div>
</template>
