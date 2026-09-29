import { defineStore } from 'pinia'
import { ref } from 'vue'

/** 布局状态（03 §4.2）：左栏折叠、右栏抽屉。 */
export const useAppStore = defineStore('app', () => {
  const leftCollapsed = ref(false)
  const detailVisible = ref(false)

  function toggleLeft(): void {
    leftCollapsed.value = !leftCollapsed.value
  }

  function openDetail(): void {
    detailVisible.value = true
  }

  function closeDetail(): void {
    detailVisible.value = false
  }

  return { leftCollapsed, detailVisible, toggleLeft, openDetail, closeDetail }
})
