<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Delete, Edit, Folder, FolderOpened, Plus, Star, StarFilled } from '@element-plus/icons-vue'
import NoteEditor from '@/components/note/NoteEditor.vue'
import NoteAttachmentList from '@/components/note/NoteAttachmentList.vue'
import NoteRelationPicker from '@/components/note/NoteRelationPicker.vue'
import NoteNotFound from '@/components/note/NoteNotFound.vue'
import { useNoteDetail } from '@/composables/useNoteDetail'
import { useNoteActions } from '@/composables/useNoteActions'
import { addRelation, removeRelation } from '@/api/note'
import { useNoteStore } from '@/stores/note'
import { formatHeaderDate } from '@/utils/date'
import type { NoteTargetRef } from '@/types/note'

/**
 * 日志详情：查看 + 行内编辑 + 附件 + 引用 + 被引用。
 * - 一切改动经 `useNoteDetail.reload()` 整体收敛（子组件不局部 append/merge）。
 * - 404/50001 → NoteNotFound（不显示重试、不改标题）。
 * - 按钮显隐一律用后端下发的 canEdit / canManage。
 */
const route = useRoute()
const router = useRouter()
const store = useNoteStore()
const detailApi = useNoteDetail()
const actions = useNoteActions()

const id = computed(() => Number(route.params.id))
const detail = detailApi.detail
const editing = ref(false)
const relationPickerOpen = ref(false)

function load(): void {
  void detailApi.load(id.value)
}

onMounted(() => {
  load()
  store.setActiveNote(id.value)
})
watch(id, () => {
  load()
  store.setActiveNote(id.value)
})
/** 详情页标题带日志摘要；离开时恢复，否则后续页面标题会残留上一条日志。 */
const BASE_TITLE = document.title
watch(detail, (d) => {
  if (!d) return
  const flat = (d.content ?? '').replace(/\s+/g, ' ').trim()
  document.title = `日志详情 · ${flat.slice(0, 12)}`
})
onUnmounted(() => {
  document.title = BASE_TITLE || '蓝鸟任务管理系统'
})

const tagList = computed(() => detail.value?.tags ?? [])
const relations = computed(() => detail.value?.relations ?? [])
const backlinks = computed(() => detail.value?.backlinks ?? [])
const existingRefs = computed<NoteTargetRef[]>(() =>
  relations.value
    .filter((r) => r.targetType === 'NOTE' || r.targetType === 'TASK')
    .map((r) => ({ targetType: r.targetType as 'NOTE' | 'TASK', targetId: Number(r.targetId) })),
)

const VISIBILITY_LABEL: Record<string, string> = { PRIVATE: '私有', INTERNAL: '部门内', PUBLIC: '公开' }

function back(): void {
  void router.push({ name: 'NoteHome' })
}

async function togglePin(): Promise<void> {
  const d = detail.value
  if (!d) return
  await actions.togglePin({ id: Number(d.id), pinned: d.pinned })
  detailApi.reload()
}

async function toggleArchive(): Promise<void> {
  const d = detail.value
  if (!d) return
  await actions.toggleArchive({ id: Number(d.id), archived: d.archived, visibility: d.visibility })
  detailApi.reload()
}

async function removeNote(): Promise<void> {
  const d = detail.value
  if (!d) return
  try {
    await ElMessageBox.confirm('删除后不可恢复，确认删除这条工作日志？', '删除日志', {
      type: 'warning',
      confirmButtonText: '删除',
    })
  } catch {
    return
  }
  await actions.remove({ id: Number(d.id), visibility: d.visibility })
  void router.push({ name: 'NoteHome' })
}

function onSaved(): void {
  editing.value = false
  detailApi.reload()
}

async function addRef(payload: NoteTargetRef & { summary?: string }): Promise<void> {
  const d = detail.value
  if (!d) return
  try {
    await addRelation(Number(d.id), { targetType: payload.targetType, targetId: payload.targetId })
    ElMessage.success('已添加引用')
    detailApi.reload()
  } catch (e) {
    ElMessage.error((e as Error)?.message || '添加引用失败')
  }
}

async function removeRef(relationId?: number): Promise<void> {
  const d = detail.value
  if (!d || relationId == null) return
  try {
    await removeRelation(Number(d.id), relationId)
    ElMessage.success('已移除引用')
    detailApi.reload()
  } catch (e) {
    ElMessage.error((e as Error)?.message || '移除引用失败')
  }
}

function openRelation(targetType?: string, targetId?: number): void {
  if (targetType === 'NOTE' && targetId != null) {
    void router.push({ name: 'NoteDetail', params: { id: String(targetId) } })
  }
}
function openBacklink(sourceNoteId?: number): void {
  if (sourceNoteId != null) void router.push({ name: 'NoteDetail', params: { id: String(sourceNoteId) } })
}
</script>

<template>
  <div class="note-detail">
    <div class="note-detail__bar">
      <el-button text :icon="ArrowLeft" data-test="note-detail-back" @click="back">返回</el-button>
      <span class="note-detail__date">{{ formatHeaderDate() }}</span>
    </div>

    <div v-if="detailApi.loading.value" class="note-detail__loading">
      <el-skeleton :rows="6" animated />
    </div>

    <NoteNotFound v-else-if="detailApi.notFound.value" />

    <el-result
      v-else-if="detailApi.error.value"
      icon="error"
      title="加载失败"
      :sub-title="detailApi.error.value.message"
    >
      <template #extra>
        <el-button @click="load">重试</el-button>
      </template>
    </el-result>

    <template v-else-if="detail">
      <section class="note-detail__head">
        <div class="note-detail__meta">
          <span class="note-detail__logdate">{{ detail.logDate }}</span>
          <el-tag size="small" type="info" effect="plain">{{ VISIBILITY_LABEL[detail.visibility ?? 'PRIVATE'] }}</el-tag>
          <span class="note-detail__owner">{{ detail.ownerName }}</span>
          <el-tag v-if="detail.pinned" size="small" type="warning" effect="plain">置顶</el-tag>
          <el-tag v-if="detail.archived" size="small" type="info">已归档</el-tag>
          <el-tag v-for="t in tagList" :key="t.id ?? t.name" size="small" effect="plain">{{ t.name }}</el-tag>
        </div>
        <div class="note-detail__ops">
          <el-button v-if="detail.canEdit" size="small" :icon="detail.pinned ? StarFilled : Star" @click="togglePin">
            {{ detail.pinned ? '取消置顶' : '置顶' }}
          </el-button>
          <el-button v-if="detail.canEdit" size="small" :icon="detail.archived ? FolderOpened : Folder" @click="toggleArchive">
            {{ detail.archived ? '取消归档' : '归档' }}
          </el-button>
          <el-button v-if="detail.canEdit" size="small" type="primary" :icon="Edit" data-test="note-detail-edit" @click="editing = true">
            编辑
          </el-button>
          <el-button v-if="detail.canManage" size="small" type="danger" :icon="Delete" data-test="note-detail-delete" @click="removeNote">
            删除
          </el-button>
        </div>
      </section>

      <section v-if="editing" class="note-detail__editor">
        <NoteEditor :note-id="Number(detail.id)" :owner-tags="detail.ownerTags ?? undefined" @saved="onSaved" @cancel="editing = false" />
      </section>
      <section v-else class="note-detail__content" data-test="note-detail-content">{{ detail.content }}</section>

      <section class="note-detail__block">
        <h3 class="note-detail__block-title">附件</h3>
        <NoteAttachmentList
          :note-id="Number(detail.id)"
          :attachments="detail.attachments"
          :can-upload="Boolean(detail.canEdit)"
          @refresh="detailApi.reload()"
        />
      </section>

      <section class="note-detail__block">
        <div class="note-detail__block-head">
          <h3 class="note-detail__block-title">引用（{{ relations.length }}）</h3>
          <el-button v-if="detail.canEdit" text size="small" :icon="Plus" @click="relationPickerOpen = true">添加引用</el-button>
        </div>
        <ul v-if="relations.length" class="note-detail__refs">
          <li v-for="r in relations" :key="r.id" class="note-detail__ref">
            <el-tag size="small" effect="plain">{{ r.targetType === 'TASK' ? '任务' : '日志' }}</el-tag>
            <span class="note-detail__ref-summary" @click="openRelation(r.targetType, r.targetId)">
              {{ r.targetSummary || `#${r.targetId}` }}
            </span>
            <span class="note-detail__ref-by">{{ r.createdByName }}</span>
            <el-button v-if="detail.canEdit" text size="small" type="danger" @click="removeRef(r.id)">移除</el-button>
          </li>
        </ul>
        <div v-else class="note-detail__empty-line">暂无引用</div>
      </section>

      <section class="note-detail__block">
        <h3 class="note-detail__block-title">被引用（{{ backlinks.length }}）</h3>
        <ul v-if="backlinks.length" class="note-detail__refs">
          <li v-for="b in backlinks" :key="b.relationId" class="note-detail__ref">
            <span class="note-detail__ref-summary" @click="openBacklink(b.sourceNoteId)">
              {{ b.sourceSummary || `#${b.sourceNoteId}` }}
            </span>
            <span class="note-detail__ref-by">{{ b.sourceOwnerName }} · {{ b.createdByName }}</span>
          </li>
        </ul>
        <div v-else class="note-detail__empty-line">还没有其他日志引用这条</div>
      </section>

      <NoteRelationPicker
        v-model="relationPickerOpen"
        :exclude-note-id="Number(detail.id)"
        :disabled-refs="existingRefs"
        @pick="addRef"
      />
    </template>
  </div>
</template>

<style scoped>
.note-detail {
  max-width: 900px;
  padding: 20px var(--bb-page-padding) 48px;
  box-sizing: border-box;
}
.note-detail__bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.note-detail__date {
  font-size: 12px;
  color: var(--bb-text-muted);
}
.note-detail__loading {
  padding: 12px 0;
}
.note-detail__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.note-detail__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--bb-text-muted);
}
.note-detail__logdate {
  font-size: 15px;
  color: var(--bb-text-secondary);
}
.note-detail__owner {
  margin-left: 4px;
}
.note-detail__ops {
  display: flex;
  flex: 0 0 auto;
  gap: 6px;
}
.note-detail__content {
  padding: 18px 0;
  font-size: 15px;
  line-height: 1.8;
  color: var(--bb-text-secondary);
  white-space: pre-wrap;
  word-break: break-word;
}
.note-detail__editor {
  padding: 12px 0;
}
.note-detail__block {
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.note-detail__block-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.note-detail__block-title {
  margin: 0 0 8px;
  font-size: 14px;
  font-weight: 500;
  color: var(--bb-text-secondary);
}
.note-detail__refs {
  margin: 0;
  padding: 0;
  list-style: none;
}
.note-detail__ref {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
  font-size: 13px;
}
.note-detail__ref-summary {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
  color: var(--bb-text-secondary);
}
.note-detail__ref-summary:hover {
  color: var(--bg-accent);
}
.note-detail__ref-by {
  font-size: 12px;
  color: var(--bb-text-muted);
}
.note-detail__empty-line {
  font-size: 12px;
  color: var(--bb-text-muted);
}
</style>
