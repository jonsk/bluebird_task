<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'

interface SlashItem {
  title: string
  description: string
  icon: string
  group: string
  tone?: string
  keywords?: string[]
}

const props = defineProps<{
  items: SlashItem[]
  command: (item: SlashItem) => void
  query?: string
}>()

defineOptions({ inheritAttrs: false })

const selected = ref(0)
const listRef = ref<HTMLElement | null>(null)

watch(
  () => props.items,
  () => {
    selected.value = 0
  },
  { deep: true },
)

function showGroup(index: number): boolean {
  if (index === 0) return true
  return props.items[index - 1]?.group !== props.items[index]?.group
}

function select(item: SlashItem): void {
  if (!item) return
  props.command(item)
}

function scrollIntoView(): void {
  void nextTick(() => {
    listRef.value
      ?.querySelector<HTMLElement>('.slash-item.is-selected')
      ?.scrollIntoView({ block: 'nearest' })
  })
}

function move(step: number): void {
  const total = props.items.length
  if (!total) return
  selected.value = (selected.value + step + total) % total
  scrollIntoView()
}

function onKeyDown({ event }: { event: KeyboardEvent }): boolean {
  if (event.key === 'ArrowUp') {
    move(-1)
    return true
  }
  if (event.key === 'ArrowDown') {
    move(1)
    return true
  }
  if (event.key === 'Enter') {
    select(props.items[selected.value])
    return true
  }
  return false
}

defineExpose({ onKeyDown })
</script>

<template>
  <div ref="listRef" class="slash-menu">
    <template v-if="items.length">
      <template v-for="(item, index) in items" :key="item.title">
        <div v-if="showGroup(index)" class="slash-group">{{ item.group }}</div>
        <button
          type="button"
          class="slash-item"
          :class="{ 'is-selected': index === selected }"
          @mousedown.prevent="select(item)"
          @mouseenter="selected = index"
        >
          <span class="slash-icon" :class="`is-${item.tone || 'default'}`">{{ item.icon }}</span>
          <span class="slash-text">
            <span class="slash-title">{{ item.title }}</span>
            <span class="slash-desc">{{ item.description }}</span>
          </span>
        </button>
      </template>
    </template>
    <div v-else class="slash-empty">没有匹配的命令</div>
  </div>
</template>
