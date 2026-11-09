<script setup lang="ts">
import { computed, ref } from 'vue'
import { NOTE_EMOJIS } from '@/utils/noteEmojis'

const emit = defineEmits<{ (e: 'select', name: string): void }>()
const keyword = ref('')
const activeGroup = ref('all')

const groupTabs = [
  { value: 'all', label: '全部' },
  { value: 'people', label: '表情手势' },
  { value: 'nature', label: '动物自然' },
  { value: 'food', label: '食物' },
  { value: 'activity', label: '活动' },
  { value: 'travel', label: '地点' },
  { value: 'objects', label: '物品' },
  { value: 'symbols', label: '符号' },
]

const items = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  if (!q) {
    return activeGroup.value === 'all'
      ? NOTE_EMOJIS
      : NOTE_EMOJIS.filter((item) => item.group === activeGroup.value)
  }
  return NOTE_EMOJIS.filter((item) =>
    [item.name, item.label, ...(item.shortcodes ?? []), ...(item.tags ?? [])]
      .join(' ')
      .toLowerCase()
      .includes(q),
  )
})

function pick(name: string): void {
  emit('select', name)
}
</script>

<template>
  <div class="emoji-picker" data-test="note-emoji-picker">
    <input v-model="keyword" class="emoji-picker__search" placeholder="搜索表情或中文" autofocus />
    <div v-if="!keyword.trim()" class="emoji-picker__tabs">
      <button
        v-for="tab in groupTabs"
        :key="tab.value"
        type="button"
        class="emoji-picker__tab"
        :class="{ 'is-active': activeGroup === tab.value }"
        :data-test="`note-emoji-tab-${tab.value}`"
        @click="activeGroup = tab.value"
      >
        {{ tab.label }}
      </button>
    </div>
    <div class="emoji-picker__grid">
      <button
        v-for="item in items"
        :key="item.name"
        type="button"
        class="emoji-picker__item"
        :title="`${item.label || item.name}  :${item.shortcodes?.[0] ?? item.name}:`"
        @click="pick(item.name)"
      >
        {{ item.emoji }}
      </button>
    </div>
    <div v-if="!items.length" class="emoji-picker__empty">没有匹配的表情</div>
  </div>
</template>
