<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import NoteListView from './NoteListView.vue'
import NoteEditor from '@/components/note/NoteEditor.vue'
import NoteCreatedDraftCard from '@/components/note/NoteCreatedDraftCard.vue'
import NoteMiniList from '@/components/note/NoteMiniList.vue'
import BbMiniCalendar from '@/components/bb/BbMiniCalendar.vue'
import { useNoteScopePage } from '@/composables/useNoteScopePage'
import { useNoteList } from '@/composables/useNoteList'
import { calendar as noteCalendar } from '@/api/calendar'
import { getNote } from '@/api/note'
import { useNoteStore } from '@/stores/note'
import { todayInShanghai } from '@/utils/date'
import type { NoteDetailVO, TagVO } from '@/types/note'

/** 与 BbMiniCalendar.loader 返回结构一致（组件内不导出类型，避免 .vue 类型导出限制）。 */
interface CalendarDayCount {
  date: string
  count: number
}

/**
 * 我的日志（mine）：唯一新建入口 + 唯一行内编辑宿主。
 * 右栏 = BbMiniCalendar(mine) + 当日日志（isolated 列表，点日历只影响右栏，不污染主列表页码/筛选）。
 */
const router = useRouter()
const noteStore = useNoteStore()
const today = todayInShanghai()
const selectedDate = ref<string | null>(today)
const editId = ref<number | null>(null)
/** 临时卡片持有**详情**（附件区依赖 detail.attachments，NoteBrief 没有该字段）。 */
const draftNote = ref<NoteDetailVO | null>(null)
const draftLoading = ref(false)
const draftError = ref('')

const page = useNoteScopePage({
  emptyText: '还没有工作日志，写下今天的第一条吧',
  onOpenEditor: (id) => {
    editId.value = id
  },
})
const {
  notes,
  query,
  total,
  pageNo,
  loading,
  error,
  tagOptions,
  facets,
  errorMode,
  emptyText,
  list,
  onOpen,
  onEdit,
  onPin,
  onUnpin,
  onArchive,
  onUnarchive,
  onRemove,
} = page

/** 编辑器回显用标签（facet 的 id 可空，收窄成 TagVO） */
const editorTags = computed<TagVO[]>(() =>
  facets.tags.value.filter((t) => t.id != null).map((t) => ({ id: Number(t.id), name: t.name ?? '' })),
)

/** 右栏当日日志：独立实例，size=5，无分页 */
const side = useNoteList({
  isolated: true,
  initialQuery: { date: today, page: 1, size: 5 },
})

/** 主列表高亮：只对"写入时的 scope"生效 */
const activeId = computed(() =>
  noteStore.activeScopeId === 'mine' ? noteStore.activeNoteId : null,
)

/** 恢复"刚创建"临时卡片（TTL 内、仍可见）；失败即清理，避免反复尝试。 */
async function restoreDraft(): Promise<void> {
  const pending = noteStore.pendingCreatedNoteId
  if (!pending) return
  draftLoading.value = true
  draftError.value = ''
  try {
    draftNote.value = await getNote(pending.id)
  } catch (e) {
    noteStore.clearPending()
    draftNote.value = null
    draftError.value = (e as Error)?.message || '刚才创建的日志已不存在'
    ElMessage.warning('刚才创建的日志已不存在')
  } finally {
    draftLoading.value = false
  }
}

onMounted(() => {
  void restoreDraft()
  side.setQuery({ date: selectedDate.value ?? today })
})

/** 日历取数：month 由组件按 Date 语义传 0-based，接口要 1-based。 */
async function calendarLoader(year: number, month: number): Promise<CalendarDayCount[]> {
  const days = await noteCalendar(year, month + 1, 'mine')
  return days.map((d) => ({ date: d.date ?? '', count: Number(d.count) || 0 }))
}

function onSelectDate(date: string | null): void {
  selectedDate.value = date
  side.setQuery({ date: date ?? undefined })
}

function onCreated(detail: NoteDetailVO): void {
  draftNote.value = detail
  draftError.value = ''
  noteStore.setPending(Number(detail.id))
  // 新建后回到第一页并清空筛选（"新建后回第一页"是明确预期）
  list.setQuery({ keyword: undefined, tagId: undefined, tagName: undefined, pinned: undefined, date: undefined, visibility: undefined })
  ElMessage.success('已保存并回到第一页')
  void page.loadFacets()
}

/** 附件变更（临时卡片内上传/解绑后）：重取详情，保持卡片与后端一致。 */
async function onDraftChanged(): Promise<void> {
  const id = draftNote.value?.id
  if (id == null) return
  try {
    draftNote.value = await getNote(Number(id))
  } catch {
    /* 拉取失败保留旧卡片，下一次操作再试 */
  }
}

function collapseDraft(): void {
  noteStore.clearPending()
  draftNote.value = null
  draftError.value = ''
}

function onCompleteDraft(): void {
  collapseDraft()
  list.refresh()
}

/** 放弃 = 只移除临时卡片，不回滚已创建的日志（§7.2）。 */
function onDiscardDraft(): void {
  collapseDraft()
}

/** 开始输入下一条时先收起上一条的临时卡片，防止附件状态串条。 */
function onComposerFocus(): void {
  if (draftNote.value) collapseDraft()
}

function onEditorSaved(): void {
  editId.value = null
  list.refresh()
  void page.loadFacets()
}

function openDetail(id: number): void {
  void router.push({ name: 'NoteDetail', params: { id: String(id) } })
}
</script>

<template>
  <NoteListView
    :notes="notes"
    :query="query"
    :page="pageNo"
    :total="total"
    :loading="loading"
    :error="error"
    :active-id="activeId"
    :tag-options="tagOptions"
    :tag-loading="facets.loading.value"
    :tag-error="facets.error.value != null"
    :error-mode="errorMode"
    :empty-text="emptyText"
    show-composer
    @change="list.setQuery"
    @retry-tags="facets.retry"
    @page-change="list.setPage"
    @retry="list.refresh"
    @open="onOpen"
    @edit="onEdit"
    @pin="onPin"
    @unpin="onUnpin"
    @archive="onArchive"
    @unarchive="onUnarchive"
    @remove="onRemove"
  >
    <template #composer>
      <div @focusin="onComposerFocus">
        <NoteEditor :owner-tags="editorTags" @saved="onCreated" />
      </div>
    </template>

    <template #above-list>
      <NoteCreatedDraftCard
        v-if="draftNote || draftLoading"
        :note-id="Number(draftNote?.id ?? noteStore.pendingCreatedNoteId?.id ?? 0)"
        :detail="draftNote"
        :loading="draftLoading"
        :error="draftError"
        @complete="onCompleteDraft"
        @discard="onDiscardDraft"
        @changed="onDraftChanged"
      />
    </template>

    <template #editor>
      <div v-if="editId != null" class="note-inline-editor" data-test="note-inline-editor">
        <NoteEditor
          :key="`edit:${editId}`"
          :note-id="editId"
          :owner-tags="editorTags"
          @saved="onEditorSaved"
          @cancel="editId = null"
        />
      </div>
    </template>

    <template #side>
      <div class="note-side">
        <div class="note-side__calendar">
          <BbMiniCalendar
            :selected="selectedDate"
            :loader="calendarLoader"
            scope="mine"
            :height="280"
            @select-date="onSelectDate"
          />
        </div>
        <div class="note-side__list">
          <h3 class="note-side__title">{{ selectedDate === today ? '今日日志' : `日志 · ${selectedDate}` }}</h3>
          <NoteMiniList
            :notes="side.notes.value"
            :loading="side.loading.value"
            :active-id="activeId"
            @open="openDetail"
            @view-all="router.push({ name: 'NoteHome' })"
          />
        </div>
      </div>
    </template>
  </NoteListView>
</template>

<style scoped>
/* 行内编辑宿主：紧贴列表顶部，不遮挡上下文（区别于弹窗） */
.note-inline-editor {
  margin-bottom: 12px;
  padding: 12px 14px;
  border: 1px solid var(--bg-accent);
  border-radius: var(--bb-radius-sm);
  background: var(--bg-primary);
}
.note-side {
  flex: 0 0 var(--bb-calendar-width);
  width: var(--bb-calendar-width);
  min-width: 0;
  height: calc(100vh - 20px);
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.note-side__calendar {
  flex: 0 0 280px;
  height: 280px;
  margin-top: 20px;
  overflow: hidden;
}
.note-side__list {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  padding: 12px 12px 24px;
  box-sizing: border-box;
}
.note-side__title {
  margin: 0 0 8px;
  font-size: 14px;
  font-weight: 500;
  color: var(--bb-text-secondary);
}
</style>
