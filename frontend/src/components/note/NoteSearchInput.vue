<script setup lang="ts">
import { ref, watch } from 'vue'
import { Search } from '@element-plus/icons-vue'
import type { NoteQuery, NoteQueryPatch } from '@/types/note'

/**
 * NoteSearchInput — 日志列表的关键词搜索（放在页面标题栏右侧，与「我的一天」一致）。
 *
 * 输入做 300ms 防抖再 emit('change', { keyword })：外壳（useNoteList）统一写 URL / 发请求，
 * 本组件不发请求。清空时立即同步（不等防抖），避免「点了 × 还要等一会儿」。
 */
const props = withDefaults(defineProps<{ query: NoteQuery; loading?: boolean }>(), {
  loading: false,
})

const emit = defineEmits<{
  (e: 'change', patch: NoteQueryPatch): void
}>()

const keyword = ref(props.query.keyword ?? '')
let timer: ReturnType<typeof setTimeout> | undefined

function onKeyword(val: string): void {
  keyword.value = val
  if (timer) clearTimeout(timer)
  const patch: NoteQueryPatch = { keyword: val.trim() || undefined }
  if (!val.trim()) {
    emit('change', patch)
    return
  }
  timer = setTimeout(() => emit('change', patch), 300)
}

/** 回车立即搜索（不等防抖）。 */
function flush(): void {
  if (timer) clearTimeout(timer)
  emit('change', { keyword: keyword.value.trim() || undefined })
}

// 外部（URL 回填、清空筛选）改关键词时同步到输入框
watch(
  () => props.query.keyword,
  (v) => {
    if (v !== keyword.value) keyword.value = v ?? ''
  },
)
</script>

<template>
  <div class="note-search" data-test="note-search">
    <el-input
      :model-value="keyword"
      :prefix-icon="Search"
      clearable
      placeholder="搜索关键词"
      @input="onKeyword"
      @clear="onKeyword('')"
      @keyup.enter="flush"
    />
  </div>
</template>

<style scoped>
.note-search {
  width: 100%;
}
</style>
