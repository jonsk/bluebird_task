<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Delete, Download, Paperclip, View } from '@element-plus/icons-vue'
import { bindAttachment, removeAttachment } from '@/api/note'
import { openAttachment, uploadFile } from '@/api/file'
import { useFileConfig } from '@/composables/useFileConfig'
import type { NoteAttachmentVO } from '@/types/note'

/**
 * NoteAttachmentList — 日志附件（taskId=null，走 note 关联 + note 专用下载端点）。
 * - 上传：先 `POST /files` 再 `POST /notes/{id}/attachments`（先保存再上传）。
 * - 下载/预览一律 axios blob（带 Bearer），不使用 `<a href>` 直连。
 * - 删除：`canRemoveRef`（解除关联）；`canDelete`（物理删除）由后端下发，前端不推导。
 */
const props = withDefaults(
  defineProps<{
    noteId: number
    attachments?: NoteAttachmentVO[] | null
    disabled?: boolean
    /** 是否允许上传（详情 canEdit；新建临时卡片恒 true） */
    canUpload?: boolean
    /** 紧凑模式（右栏/临时卡片） */
    compact?: boolean
  }>(),
  { attachments: null, disabled: false, canUpload: true, compact: false },
)

const emit = defineEmits<{ (e: 'refresh'): void; (e: 'changed'): void }>()

const { config, load: loadConfig } = useFileConfig()
const uploading = ref(false)
const progress = ref(0)
const controller = ref<AbortController | null>(null)

const files = computed<NoteAttachmentVO[]>(() => props.attachments ?? [])
const accept = computed(() => (config.value.allowedExt?.length ? config.value.allowedExt.map((e) => `.${e}`).join(',') : undefined))

onMounted(() => {
  void loadConfig()
})

function sizeText(size?: number): string {
  if (!size) return ''
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function extOf(name?: string): string {
  const i = (name ?? '').lastIndexOf('.')
  return i >= 0 ? name!.slice(i + 1).toLowerCase() : ''
}

async function onPick(e: Event): Promise<void> {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (config.value.maxSize && file.size > config.value.maxSize) {
    ElMessage.error(`文件超过上限 ${sizeText(config.value.maxSize)}`)
    return
  }
  const allowed = config.value.allowedExt ?? []
  if (allowed.length && !allowed.map((x) => x.toLowerCase()).includes(extOf(file.name))) {
    ElMessage.error(`不支持的文件类型，仅允许：${allowed.join(' / ')}`)
    return
  }
  uploading.value = true
  progress.value = 0
  controller.value = new AbortController()
  try {
    const vo = await uploadFile(file, {
      signal: controller.value.signal,
      onProgress: (p) => {
        progress.value = p
      },
    })
    await bindAttachment(props.noteId, { attachmentId: Number(vo.id) })
    ElMessage.success('附件已上传')
    emit('refresh')
    emit('changed')
  } catch (err) {
    const msg = (err as Error)?.message || '上传失败'
    ElMessage.error(msg)
  } finally {
    uploading.value = false
    controller.value = null
  }
}

function cancelUpload(): void {
  controller.value?.abort()
  controller.value = null
  uploading.value = false
  ElMessage.info('已取消上传')
}

async function open(mode: 'download' | 'preview', row: NoteAttachmentVO): Promise<void> {
  if (!row.downloadUrl) return
  try {
    await openAttachment({ mode, url: row.downloadUrl, fileName: row.fileName, contentType: row.contentType ?? undefined })
  } catch (err) {
    ElMessage.error((err as Error)?.message || '下载失败')
  }
}

async function remove(row: NoteAttachmentVO): Promise<void> {
  try {
    await removeAttachment(props.noteId, Number(row.attachmentId))
    ElMessage.success('已移除附件')
    emit('refresh')
    emit('changed')
  } catch (err) {
    ElMessage.error((err as Error)?.message || '移除失败')
  }
}
</script>

<template>
  <div class="note-attachments" :class="{ 'is-compact': compact }" data-test="note-attachments">
    <ul v-if="files.length" class="note-attachments__list">
      <li v-for="row in files" :key="row.attachmentId ?? row.fileName" class="note-attachments__item">
        <el-icon class="note-attachments__icon"><Paperclip /></el-icon>
        <span class="note-attachments__name" :title="row.fileName">{{ row.fileName }}</span>
        <span class="note-attachments__size">{{ sizeText(row.size) }}</span>
        <span class="note-attachments__ops">
          <el-button text size="small" :icon="View" title="预览" @click="open('preview', row)" />
          <el-button text size="small" :icon="Download" title="下载" @click="open('download', row)" />
          <el-button
            v-if="row.canRemoveRef !== false"
            text
            size="small"
            type="danger"
            :icon="Delete"
            title="移除"
            :disabled="disabled"
            @click="remove(row)"
          />
        </span>
      </li>
    </ul>
    <div v-else class="note-attachments__empty">暂无附件</div>

    <div v-if="canUpload && !disabled" class="note-attachments__upload">
      <label class="note-attachments__pick">
        <input type="file" :accept="accept" :disabled="uploading" data-test="note-attachment-input" @change="onPick" />
        <el-button size="small" :icon="Paperclip" :loading="uploading">添加附件</el-button>
      </label>
      <template v-if="uploading">
        <el-progress class="note-attachments__progress" :percentage="progress" :stroke-width="6" />
        <el-button text size="small" type="danger" @click="cancelUpload">取消</el-button>
      </template>
    </div>
  </div>
</template>

<style scoped>
.note-attachments__list {
  margin: 0;
  padding: 0;
  list-style: none;
}
.note-attachments__item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
  font-size: 13px;
}
.note-attachments__icon {
  color: var(--bb-text-muted);
}
.note-attachments__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--bb-text-secondary);
}
.note-attachments__size {
  color: var(--bb-text-muted);
  font-size: 12px;
}
.note-attachments__ops {
  display: flex;
  align-items: center;
  gap: 2px;
}
.note-attachments__empty {
  padding: 6px 0;
  font-size: 12px;
  color: var(--bb-text-muted);
}
.note-attachments__upload {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 8px;
}
.note-attachments__pick input {
  display: none;
}
.note-attachments__progress {
  flex: 1;
  min-width: 80px;
}
.is-compact .note-attachments__item {
  padding: 4px 0;
}
</style>
