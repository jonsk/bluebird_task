<script setup lang="ts">
import { computed } from 'vue'
import { noteIconPaths, type NoteIconName } from '@/utils/noteIcons'

const props = withDefaults(
  defineProps<{
    name: NoteIconName
    size?: number
    strokeWidth?: number
  }>(),
  {
    size: 18,
    strokeWidth: 1.8,
  },
)

const paths = computed(() => noteIconPaths[props.name] ?? [])
</script>

<template>
  <svg
    class="note-icon"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    :stroke-width="strokeWidth"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    focusable="false"
  >
    <path v-for="(d, index) in paths" :key="index" :d="d" />
  </svg>
</template>
