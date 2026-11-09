<script setup lang="ts">
import { computed, ref } from 'vue'
import { NOTE_EMOJIS } from '@/utils/noteEmojis'

const emit = defineEmits<{ (e: 'select', name: string): void }>()
const keyword = ref('')

const items = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  if (!q) return NOTE_EMOJIS
  return NOTE_EMOJIS.filter((item) =>
    [item.name, ...(item.shortcodes ?? []), ...(item.tags ?? [])].join(' ').toLowerCase().includes(q),
  )
})

function pick(name: string): void {
  emit('select', name)
}
</script>

<template>
  <div class="emoji-picker" data-test="note-emoji-picker">
    <input v-model="keyword" class="emoji-picker__search" placeholder="搜索表情" autofocus />
    <div class="emoji-picker__grid">
      <button
        v-for="item in items"
        :key="item.name"
        type="button"
        class="emoji-picker__item"
        :title="`:${item.shortcodes?.[0] ?? item.name}:`"
        @click="pick(item.name)"
      >
        {{ item.emoji }}
      </button>
    </div>
    <div v-if="!items.length" class="emoji-picker__empty">没有匹配的表情</div>
  </div>
</template>
