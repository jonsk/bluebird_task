<script setup lang="ts">
import { StarFilled, Star, Folder, FolderOpened, Delete, Edit } from '@element-plus/icons-vue'
import type { NoteBrief } from '@/types/note'

/**
 * NoteCard — 列表项卡片（纯展示，不请求）。
 * 点击卡片 → open；按 canEdit / canManage 显隐操作按钮。
 */
const props = withDefaults(defineProps<{ note: NoteBrief; active?: boolean }>(), { active: false })

const emit = defineEmits<{
  (e: 'open', id: number): void
  (e: 'edit', id: number): void
  (e: 'pin', id: number): void
  (e: 'unpin', id: number): void
  (e: 'archive', id: number): void
  (e: 'unarchive', id: number): void
  (e: 'remove', id: number): void
}>()

function onClick(): void {
  if (props.note.id != null) emit('open', props.note.id)
}
function stop(e: Event): void {
  e.stopPropagation()
}
</script>

<template>
  <div
    class="note-card"
    :class="{ 'is-active': active }"
    :data-test="`note-card-${note.id}`"
    @click="onClick"
  >
    <div class="note-card__head">
      <span class="note-card__date">{{ note.logDate }}</span>
      <span class="note-card__owner">{{ note.ownerName }}</span>
      <span v-if="note.pinned" class="note-card__badge note-card__badge--pin">
        <el-icon><StarFilled /></el-icon> 置顶
      </span>
      <span v-if="note.archived" class="note-card__badge note-card__badge--arch">已归档</span>
    </div>

    <p class="note-card__summary">{{ note.contentSummary || '（无内容）' }}</p>

    <div v-if="note.tags && note.tags.length" class="note-card__tags">
      <el-tag v-for="t in note.tags" :key="t.id ?? t.name" size="small" type="info" effect="plain">
        {{ t.name }}
      </el-tag>
    </div>

    <div v-if="note.canEdit || note.canManage" class="note-card__ops" @click="stop">
      <el-button
        v-if="note.canEdit"
        text
        size="small"
        :icon="note.pinned ? StarFilled : Star"
        :class="{ 'is-on': note.pinned }"
        :title="note.pinned ? '取消置顶' : '置顶'"
        data-test="note-card-pin"
        @click="note.pinned ? emit('unpin', note.id!) : emit('pin', note.id!)"
      />
      <el-button
        v-if="note.canEdit"
        text
        size="small"
        :icon="note.archived ? FolderOpened : Folder"
        :title="note.archived ? '取消归档' : '归档'"
        data-test="note-card-archive"
        @click="note.archived ? emit('unarchive', note.id!) : emit('archive', note.id!)"
      />
      <el-button
        v-if="note.canEdit"
        text
        size="small"
        :icon="Edit"
        title="编辑"
        data-test="note-card-edit"
        @click="emit('edit', note.id!)"
      />
      <el-button
        v-if="note.canManage"
        text
        size="small"
        type="danger"
        :icon="Delete"
        title="删除"
        data-test="note-card-delete"
        @click="emit('remove', note.id!)"
      />
    </div>
  </div>
</template>

<style scoped>
.note-card {
  position: relative;
  padding: 12px 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--bb-radius-sm);
  background: var(--bg-primary);
  cursor: pointer;
  transition: box-shadow 0.15s, border-color 0.15s;
}
.note-card:hover {
  box-shadow: var(--bb-shadow-flat);
}
.note-card.is-active {
  border-color: var(--bg-accent);
}
.note-card__head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--bb-text-muted);
}
.note-card__owner {
  margin-left: auto;
}
.note-card__badge {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 0 6px;
  border-radius: 10px;
  font-size: 11px;
}
.note-card__badge--pin {
  color: #b88230;
  background: #fdf6ec;
}
.note-card__badge--arch {
  color: var(--bb-text-muted);
  background: var(--bg-hover);
}
.note-card__summary {
  margin: 8px 0;
  font-size: 14px;
  line-height: 1.6;
  color: var(--bb-text-secondary);
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 6.4em;
  overflow: hidden;
}
.note-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.note-card__ops {
  display: flex;
  justify-content: flex-end;
  gap: 2px;
  margin-top: 6px;
}
/* 已置顶：星标高亮（与任务模块一致，避免"置顶反而显示空心星"的歧义） */
.note-card__ops .is-on {
  color: #b88230;
}
</style>
