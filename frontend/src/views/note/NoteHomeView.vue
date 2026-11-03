<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import NoteListView from './NoteListView.vue'
import NoteEditor from '@/components/note/NoteEditor.vue'
import NoteMiniList from '@/components/note/NoteMiniList.vue'
import BbMiniCalendar from '@/components/bb/BbMiniCalendar.vue'
import { useNoteScopePage } from '@/composables/useNoteScopePage'
import { useNoteList } from '@/composables/useNoteList'
import { calendar as noteCalendar } from '@/api/calendar'
import { useNoteStore } from '@/stores/note'
import { todayInShanghai } from '@/utils/date'

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
  errorMode,
  emptyText,
  list,
  onOpen,
  onEdit,
  onRemove,
} = page

/** 右栏当日日志：独立实例，size=5，无分页 */
const side = useNoteList({
  isolated: true,
  initialQuery: { date: today, page: 1, size: 5 },
})

/** 主列表高亮：只对"写入时的 scope"生效 */
const activeId = computed(() =>
  noteStore.activeScopeId === 'mine' ? noteStore.activeNoteId : null,
)

/**
 * 主列表展示数据：行内编辑某条日志时，把正在编辑的那条从列表中隐藏
 * （只显示"未在编辑"的日志），避免编辑内容与列表卡片并存造成困惑。
 */
const displayNotes = computed(() =>
  editId.value == null ? notes.value : notes.value.filter((n) => n.id !== editId.value),
)

/** 正在编辑的日志（用于标题栏提示），列表里被隐藏但仍可在此取日期做标识。 */
const editingNote = computed(() =>
  editId.value == null ? undefined : notes.value.find((n) => n.id === editId.value),
)

onMounted(() => {
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

/**
 * 新建框自动保存：首次落库（created）时清掉筛选并刷新，让新日志立刻出现在列表里；
 * 后续的自动更新不打扰列表（避免每敲一会儿列表就跳一次）。
 */
function onComposerSaved(_detail: unknown, meta: { created: boolean }): void {
  if (!meta.created) return
  list.setQuery({ keyword: undefined, date: undefined })
  list.refresh()
}

/** 行内编辑自动保存：只刷新卡片摘要，面板保持打开（Esc 收起）。 */
function onInlineSaved(): void {
  list.refresh()
}

function closeInlineEditor(): void {
  editId.value = null
  list.refresh()
}

function openDetail(id: number): void {
  void router.push({ name: 'NoteDetail', params: { id: String(id) } })
}
</script>

<template>
  <NoteListView
    :notes="displayNotes"
    :query="query"
    :page="pageNo"
    :total="total"
    :loading="loading"
    :error="error"
    :active-id="activeId"
    :error-mode="errorMode"
    :empty-text="emptyText"
    :show-composer="editId == null"
    @change="list.setQuery"
    @page-change="list.setPage"
    @retry="list.refresh"
    @open="onOpen"
    @edit="onEdit"
    @remove="onRemove"
  >
    <template #composer>
      <NoteEditor @saved="onComposerSaved" />
    </template>

    <template #editor>
      <div v-if="editId != null" class="note-inline-editor" data-test="note-inline-editor">
        <div class="note-inline-editor__bar">
          <span class="note-inline-editor__label">
            正在编辑<template v-if="editingNote?.logDate"> · {{ editingNote.logDate }}</template>
          </span>
          <el-button
            size="small"
            type="primary"
            plain
            data-test="note-inline-done"
            @click="closeInlineEditor"
          >
            完成编辑
          </el-button>
        </div>
        <NoteEditor
          :key="`edit:${editId}`"
          :note-id="editId"
          closable
          @saved="onInlineSaved"
          @cancel="closeInlineEditor"
        />
        <p class="note-inline-editor__hint">内容自动保存；完成编辑后收起，上方可新建新日志</p>
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
.note-inline-editor__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}
.note-inline-editor__label {
  font-size: 13px;
  font-weight: 500;
  color: var(--bb-text-secondary);
}
.note-inline-editor__hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--bb-text-secondary);
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
