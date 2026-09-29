<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { uploadFile, deleteFile, fileDownloadUrl, type FileVO } from '@/api/file'

/**
 * 附件列表（03 §5.3.4）。上传/下载/删除；预览按 id 由后端代理（02 §5.6，修 SSRF）。
 */
const props = withDefaults(defineProps<{ taskId?: number | null; files?: FileVO[]; disabled?: boolean }>(), {
  taskId: null,
  files: () => [],
  disabled: false,
})

const emit = defineEmits<{ (e: 'changed'): void }>()

const uploading = ref(false)
const items = reactive<FileVO[]>([])
const fileInput = ref<HTMLInputElement | null>(null)

watch(
  () => props.files,
  (next) => {
    items.splice(0, items.length, ...(next ?? []))
  },
  { immediate: true, deep: true },
)

const canUpload = computed(() => !props.disabled && props.taskId != null)

async function onPick(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || props.taskId == null) return
  uploading.value = true
  try {
    const stored = await uploadFile(file, props.taskId)
    items.push(stored)
    ElMessage.success('上传成功')
    emit('changed')
  } catch (e) {
    ElMessage.error((e as Error).message || '上传失败')
  } finally {
    uploading.value = false
  }
}

async function onDelete(file: FileVO): Promise<void> {
  try {
    await deleteFile(Number(file.id))
    const idx = items.findIndex((f) => f.id === file.id)
    if (idx >= 0) items.splice(idx, 1)
    ElMessage.success('已删除')
    emit('changed')
  } catch (e) {
    ElMessage.error((e as Error).message || '删除失败')
  }
}
</script>

<template>
  <div class="bb-attachments">
    <div class="bb-attachments__head">
      <span class="bb-attachments__title">附件</span>
      <el-button v-if="canUpload" size="small" :loading="uploading" @click="fileInput?.click()">上传</el-button>
      <input ref="fileInput" type="file" hidden @change="onPick" />
    </div>
    <ul class="bb-attachments__list">
      <li v-for="file in items" :key="file.id" class="bb-attachments__item">
        <a :href="fileDownloadUrl(Number(file.id))" target="_blank" rel="noopener">{{ file.fileName }}</a>
        <span class="bb-attachments__size">{{ Math.ceil(Number(file.size ?? 0) / 1024) }} KB</span>
        <el-button v-if="!disabled" text size="small" @click="onDelete(file)">删除</el-button>
      </li>
      <li v-if="!items.length" class="bb-attachments__empty">暂无附件</li>
    </ul>
  </div>
</template>

<style scoped>
.bb-attachments__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}
.bb-attachments__title {
  font-size: 13px;
  font-weight: 500;
}
.bb-attachments__list {
  margin: 0;
  padding: 0;
  list-style: none;
}
.bb-attachments__item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
  font-size: 13px;
}
.bb-attachments__item a {
  color: var(--bb-color-primary);
  text-decoration: none;
}
.bb-attachments__size {
  color: var(--bb-color-muted);
  font-size: 12px;
}
.bb-attachments__empty {
  color: var(--bb-color-muted);
  font-size: 13px;
  padding: 4px 0;
}
</style>
