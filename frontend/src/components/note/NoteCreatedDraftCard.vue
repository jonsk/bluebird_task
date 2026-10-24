<script setup lang="ts">
import { computed } from 'vue'
import { Check, Close } from '@element-plus/icons-vue'
import NoteAttachmentList from './NoteAttachmentList.vue'
import type { NoteDetailVO } from '@/types/note'

/**
 * NoteCreatedDraftCard — 刚创建日志的临时卡片（§7.2 契约）。
 *
 * - 与列表数据完全独立：新建后即使新日志不在第一页，用户仍有明确的补附件入口。
 * - 附件区复用 `NoteAttachmentList`（唯一上传/删除实现）；附件变更后只上抛 `changed`，
 *   父组件重新 `GET /notes/{id}` 取全量详情（不做单条增量合并，避免与 reload 竞态互相覆盖）。
 * - `complete`：移除卡片（日志保留）；`discard`：移除卡片（**不回滚**已创建的日志）。
 * - `loading`/`error` 覆盖「按 noteId 恢复详情」失败的两种中间态，父组件据此不渲染附件入口。
 */
const props = withDefaults(
  defineProps<{
    noteId: number
    detail?: NoteDetailVO | null
    loading?: boolean
    error?: string
  }>(),
  { detail: null, loading: false, error: '' },
)

const emit = defineEmits<{
  (e: 'complete', id: number): void
  (e: 'discard', id: number): void
  (e: 'changed'): void
}>()

const summary = computed(() => {
  const flat = (props.detail?.content ?? '').replace(/\s+/g, ' ').trim()
  return flat || '（无内容）'
})
const canUpload = computed(() => Boolean(props.detail?.canEdit))
</script>

<template>
  <div class="note-draft" data-test="note-draft-card">
    <div class="note-draft__head">
      <span class="note-draft__tag">刚刚创建</span>
      <span v-if="detail" class="note-draft__date">{{ detail.logDate }}</span>
      <span class="note-draft__hint">可直接在下方补充附件</span>
    </div>

    <div v-if="loading" class="note-draft__loading" data-test="note-draft-loading">加载中…</div>
    <p v-else-if="error" class="note-draft__error">{{ error }}</p>

    <template v-else-if="detail">
      <p class="note-draft__summary">{{ summary }}</p>
      <div class="note-draft__attachments">
        <NoteAttachmentList
          :note-id="noteId"
          :attachments="detail.attachments"
          :can-upload="canUpload"
          @refresh="emit('changed')"
          @changed="emit('changed')"
        />
      </div>
    </template>

    <div class="note-draft__ops">
      <el-button size="small" type="primary" :icon="Check" data-test="note-draft-complete" @click="emit('complete', noteId)">
        完成
      </el-button>
      <el-button size="small" :icon="Close" data-test="note-draft-discard" @click="emit('discard', noteId)">放弃</el-button>
    </div>
  </div>
</template>

<style scoped>
.note-draft {
  margin-bottom: 12px;
  padding: 12px 14px;
  border: 1px dashed var(--bg-accent);
  border-radius: var(--bb-radius-sm);
  background: var(--bg-primary);
}
.note-draft__head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--bb-text-muted);
}
.note-draft__tag {
  padding: 0 6px;
  border-radius: 10px;
  background: var(--bg-active);
  color: var(--bg-accent);
}
.note-draft__hint {
  margin-left: auto;
}
.note-draft__summary {
  margin: 8px 0;
  font-size: 14px;
  line-height: 1.6;
  color: var(--bb-text-secondary);
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 6.4em;
  overflow: hidden;
}
.note-draft__attachments {
  margin: 4px 0 8px;
}
.note-draft__loading,
.note-draft__error {
  margin: 8px 0;
  font-size: 12px;
  color: var(--bb-text-muted);
}
.note-draft__error {
  color: var(--el-color-danger);
}
.note-draft__ops {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
