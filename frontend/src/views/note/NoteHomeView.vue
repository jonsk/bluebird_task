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

/**
 * 新建框（composer）状态：
 * - `composerOpen`：新建框是否展开。「完成编辑」后收起，底部显示「新建日志」按钮重新展开。
 * - `composerId`：本次草稿首次落库后的日志 id。保存后台自动把这条生成了，但按需求
 *   它不应立刻出现在列表里——只有用户点「完成编辑」退出这条草稿时才显示。
 */
const composerOpen = ref(true)
const composerId = ref<number | null>(null)

/** 行内编辑器的保存状态（compact 模式下状态行被隐藏，改到标题栏展示）。 */
type InlineEditorExpose = {
  status: 'idle' | 'saving' | 'saved' | 'error'
  savedAt: string
  errorMessage: string
  saveNow: () => Promise<void>
}
const inlineEditorRef = ref<InlineEditorExpose | null>(null)
/** 新建框编辑器引用（完成编辑时先强制保存草稿）。 */
const composerEditorRef = ref<InlineEditorExpose | null>(null)

const page = useNoteScopePage({
  emptyText: '还没有工作日志，写下今天的第一条吧',
  onOpenEditor: (id) => {
    // 切去编辑既有日志时，新建框会被隐藏（卸载）；若草稿已由自动保存落库，
    // 重置隐藏 id 让其放行显示，避免残留导致该草稿一直从列表里消失。
    composerId.value = null
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
 * 主列表展示数据：把「正在被编辑/正在新建且未完成」的日志从列表中隐藏，
 * 避免编辑/撰写内容与列表卡片并存造成困惑。这两类只有退出（关掉宿主）后才出现在列表里。
 */
const displayNotes = computed(() => {
  const hide = new Set<number>()
  if (editId.value != null) hide.add(editId.value)
  if (composerOpen.value && composerId.value != null) hide.add(composerId.value)
  return hide.size ? notes.value.filter((n) => !hide.has(n.id)) : notes.value
})

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
 * 新建框自动保存：首次落库（created）时记下草稿 id（列表先把它隐藏），
 * 并清掉筛选/刷新让后台数据就绪——但列表里的显示由 displayNotes 管控，退出编辑才真正出现。
 */
function onComposerSaved(detail: { id: number }, meta: { created: boolean }): void {
  if (meta.created) {
    composerId.value = Number(detail.id)
    list.setQuery({ keyword: undefined, date: undefined })
    list.refresh()
  }
}

/**
 * 「完成编辑」：先把未落库的改动强制保存掉，再收起新建框并解除对草稿的隐藏，
 * 让这条新日志在列表中显示。下次要新建再点「新建日志」重新展开。
 */
async function finishComposer(): Promise<void> {
  try {
    await composerEditorRef.value?.saveNow()
  } finally {
    composerId.value = null
    composerOpen.value = false
    list.refresh()
  }
}

/** 展开一个全新的新建框（重置已隐藏的草稿 id）。 */
function openComposer(): void {
  composerId.value = null
  composerOpen.value = true
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

/** 标题栏上的「保存失败，点击重试」：把重试动作转发给行内编辑器。 */
function retryInlineSave(): void {
  void inlineEditorRef.value?.saveNow()
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
      <div v-if="composerOpen" class="note-inline-editor note-composer" data-test="note-composer">
        <div class="note-inline-editor__bar">
          <span class="note-inline-editor__label">新建日志 · {{ today }}</span>
          <el-button
            size="small"
            type="primary"
            plain
            data-test="note-composer-done"
            @click="finishComposer"
          >
            完成编辑
          </el-button>
        </div>
        <NoteEditor
          ref="composerEditorRef"
          compact
          @saved="onComposerSaved"
        />
      </div>
      <el-button
        v-else
        class="note-composer__new"
        data-test="note-composer-reopen"
        @click="openComposer"
      >
        新建日志
      </el-button>
    </template>

    <template #editor>
      <div v-if="editId != null" class="note-inline-editor" data-test="note-inline-editor">
        <div class="note-inline-editor__bar">
          <span class="note-inline-editor__label">
            正在编辑<template v-if="editingNote?.logDate"> · {{ editingNote.logDate }}</template>
            <template v-if="inlineEditorRef?.status === 'saving'">
              <span class="note-inline-editor__save is-saving">保存中…</span>
            </template>
            <template v-else-if="inlineEditorRef?.status === 'saved'">
              <span class="note-inline-editor__save is-saved">已保存 {{ inlineEditorRef.savedAt }}</span>
            </template>
            <template v-else-if="inlineEditorRef?.status === 'error'">
              <button
                class="note-inline-editor__save note-inline-editor__retry"
                type="button"
                @click="retryInlineSave"
              >
                {{ inlineEditorRef.errorMessage }}，点击重试
              </button>
            </template>
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
          ref="inlineEditorRef"
          :key="`edit:${editId}`"
          :note-id="editId"
          compact
          closable
          @saved="onInlineSaved"
          @cancel="closeInlineEditor"
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
  display: flex;
  align-items: center;
  gap: 8px;
}
.note-inline-editor__save {
  font-size: 12px;
  font-weight: 400;
}
.note-inline-editor__save.is-saving {
  color: var(--bb-text-secondary);
}
.note-inline-editor__save.is-saved {
  color: var(--bb-success, #67c23a);
}
.note-inline-editor__save.note-inline-editor__retry {
  padding: 0;
  border: none;
  background: none;
  cursor: pointer;
  color: var(--bb-danger, #f56c6c);
  text-decoration: underline;
}
/* 新建框收起后的「新建日志」入口 */
.note-composer__new {
  width: 100%;
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
