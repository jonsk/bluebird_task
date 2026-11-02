<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
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

function onCreated(): void {
  // 创建后直接刷新列表，不再弹出附件上传临时卡片
  list.setQuery({ keyword: undefined, date: undefined })
  ElMessage.success('已保存并回到第一页')
  list.refresh()
}

function onEditorSaved(): void {
  editId.value = null
  ElMessage.success('已保存')
  list.refresh()
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
    :error-mode="errorMode"
    :empty-text="emptyText"
    show-composer
    @change="list.setQuery"
    @page-change="list.setPage"
    @retry="list.refresh"
    @open="onOpen"
    @edit="onEdit"
    @remove="onRemove"
  >
    <template #composer>
      <NoteEditor @saved="onCreated" />
    </template>

    <template #editor>
      <div v-if="editId != null" class="note-inline-editor" data-test="note-inline-editor">
        <NoteEditor
          :key="`edit:${editId}`"
          :note-id="editId"
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
