<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import NoteListView from './NoteListView.vue'
import NoteEditor from '@/components/note/NoteEditor.vue'
import NoteMiniList from '@/components/note/NoteMiniList.vue'
import BbMiniCalendar from '@/components/bb/BbMiniCalendar.vue'
import { useNoteScopePage } from '@/composables/useNoteScopePage'
import { useNoteList } from '@/composables/useNoteList'
import { calendar as noteCalendar } from '@/api/calendar'
import { createWeeklyNote, listNotes } from '@/api/note'
import { BizError, HttpError } from '@/api/http'
import { ElMessage } from 'element-plus'
import { navSelect } from '@/composables/useNavReload'
import { useNoteStore } from '@/stores/note'
import {
  shiftDay,
  todayInShanghai,
  todayWeekStart,
  weekRangeLabel,
  weekStartOf,
} from '@/utils/date'
import type { NotePeriod } from '@/types/note'

/** 与 BbMiniCalendar.loader 返回结构一致（组件内不导出类型，避免 .vue 类型导出限制）。 */
interface CalendarDayCount {
  date: string
  count: number
}

/**
 * 每日工作记录（mine）：唯一新建入口 + 唯一行内编辑宿主。
 * 卡片和右栏当日日志点击都直接进入行内编辑，不再跳详情页。
 * 右栏 = BbMiniCalendar(mine) + 当日日志（isolated 列表，点日历只影响右栏，不污染主列表页码/筛选）。
 */
const router = useRouter()
const route = useRoute()
const noteStore = useNoteStore()
/**
 * 同一套页面组件服务两个菜单：`route.meta.period` 决定日/周口径。
 * DAY = 工作日志（一天可多条）；WEEK = 每周工作记录（一周一条，选中项一律归到周一）。
 */
const period = computed<NotePeriod>(() => (route.meta.period === 'WEEK' ? 'WEEK' : 'DAY'))
const isWeekly = computed(() => period.value === 'WEEK')
const today = todayInShanghai()
const weekStart = ref(todayWeekStart())
const selectedDate = ref<string | null>(isWeekly.value ? weekStart.value : today)
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
  emptyText: isWeekly.value ? '本周还没有工作记录，写下这一周吧' : '还没有工作日志，写下今天的第一条吧',
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
  onEdit,
  onRemove,
} = page

/** 右栏当日日志：独立实例，size=5，无分页 */
const side = useNoteList({
  isolated: true,
  initialQuery: { date: selectedDate.value ?? undefined, page: 1, size: 5 },
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
  if (!hide.size) return notes.value
  return notes.value.filter((n) => n.id == null || !hide.has(n.id))
})

/**
 * 点「每周工作记录」菜单：即使已经停在这个页面（路由重复不会重新挂载组件），
 * 也重新校验一次当周记录——有就加载，没有就新建。
 */
watch(navSelect, (sig) => {
  if (!sig || !isWeekly.value || sig.name !== 'WeeklyNoteHome') return
  void openCurrentWeekRecord()
})

onMounted(() => {
  side.setQuery({ date: selectedDate.value ?? today })
  if (isWeekly.value) void openCurrentWeekRecord()
})

/**
 * 周记录一周一条，由系统自动生成：
 * 打开周页面就向后端取当周记录（不存在则按模板新建，已存在原样返回），
 * 拿到后直接进编辑态，页面上不再有「新建」入口。
 */
async function openCurrentWeekRecord(): Promise<void> {
  try {
    const detail = await createWeeklyNote()
    if (detail.id != null) editId.value = Number(detail.id)
    // 列表/右栏在 ensure 之前就发过请求，这里补一次确保当周卡片可见。
    list.refresh()
    side.refresh()
  } catch (e) {
    const msg = e instanceof BizError || e instanceof HttpError ? e.message : (e as Error).message
    ElMessage.error(msg || '本周工作记录加载失败')
  }
}

/**
 * 日历取数。
 * - DAY：当月每天条数（后端 aggregate）。
 * - WEEK：一周一条，把记录标在该周落在本月的第一天（通常是周一；月初不完整时标在 1 号），
 *   点任意一天都会归到该周周一。
 */
async function calendarLoader(year: number, month: number): Promise<CalendarDayCount[]> {
  if (!isWeekly.value) {
    const days = await noteCalendar(year, month + 1, 'mine')
    return days.map((d) => ({ date: d.date ?? '', count: Number(d.count) || 0 }))
  }
  const pad = (n: number): string => (n < 10 ? `0${n}` : String(n))
  const monthStart = `${year}-${pad(month + 1)}-01`
  const daysInMonth = new Date(Date.UTC(year, month + 1, 0)).getUTCDate()
  const monthEnd = `${year}-${pad(month + 1)}-${pad(daysInMonth)}`
  const data = await listNotes({
    scope: 'mine',
    period: 'WEEK',
    start: weekStartOf(monthStart),
    end: weekStartOf(monthEnd),
    page: 1,
    size: 100,
  })
  const byDay: Record<string, number> = {}
  for (const note of data.list ?? []) {
    const start = note.logDate
    if (!start) continue
    const badgeDay = start < monthStart ? shiftDay(start, 6) : start
    if (badgeDay > monthEnd) continue
    byDay[badgeDay] = (byDay[badgeDay] ?? 0) + 1
  }
  return Object.entries(byDay).map(([date, count]) => ({ date, count }))
}

function onSelectDate(date: string | null): void {
  // 周视图：点周内任意一天都归到该周周一，右栏与列表按周取数
  const next = date == null ? null : isWeekly.value ? weekStartOf(date) : date
  selectedDate.value = next
  side.setQuery({ date: next ?? undefined })
}

/** 右栏标题：日视图显示具体日期，周视图显示周区间。 */
const sideTitle = computed(() => {
  if (isWeekly.value) {
    const start = selectedDate.value ?? weekStart.value
    return start === weekStart.value ? '本周记录' : `周记录 · ${weekRangeLabel(start)}`
  }
  return selectedDate.value === today ? '今日日志' : `日志 · ${selectedDate.value}`
})

/**
 * 新建框自动保存：首次落库（created）时记下草稿 id（列表先把它隐藏）。
 *
 * 这里同样不刷新列表，避免自动保存瞬间切换骨架屏导致高度/光标抖动；
 * 列表里的显示由 displayNotes 管控，点「完成编辑」时由 finishComposer() 统一刷新。
 */
function onComposerSaved(detail: { id?: number | null }, meta: { created: boolean }): void {
  if (meta.created && detail.id != null) {
    composerId.value = Number(detail.id)
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
    // 退出新建时才刷新：清掉筛选并回到第一页，确保刚创建的日志可见。
    list.setQuery({ keyword: undefined, date: undefined })
    // setQuery 可能命中旧缓存，强制再拉一次；此时编辑框已关闭，不会影响输入。
    list.refresh()
  }
}

/** 展开一个全新的新建框（重置已隐藏的草稿 id）。 */
function openComposer(): void {
  composerId.value = null
  composerOpen.value = true
  list.refresh()
}

/**
 * 行内编辑自动保存：不刷新列表。
 *
 * 正在编辑的日志已从 `displayNotes` 隐藏，此时刷新列表只会让 `loading`
 * 短暂切换成骨架屏、改变列表高度，导致编辑区滚动位置和光标跳动。
 * 卡片摘要等退出编辑时由 `closeInlineEditor()` 统一刷新。
 */
function onInlineSaved(): void {
  // no-op：等 Esc / 完成编辑时再刷新列表。
}

function closeInlineEditor(): void {
  editId.value = null
  list.refresh()
}

/** 标题栏上的「保存失败，点击重试」：把重试动作转发给行内编辑器。 */
function retryInlineSave(): void {
  void inlineEditorRef.value?.saveNow()
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
    :show-composer="!isWeekly && editId == null"
    :hide-empty="editId != null"
    @change="list.setQuery"
    @page-change="list.setPage"
    @retry="list.refresh"
    @open="onEdit"
    @edit="onEdit"
    @remove="onRemove"
  >
    <template #composer>
      <div v-if="composerOpen" class="note-inline-editor note-composer" data-test="note-composer">
        <NoteEditor
          ref="composerEditorRef"
          compact
          :period="period"
          @saved="onComposerSaved"
        />
        <div class="note-inline-editor__actions note-inline-editor__actions--end">
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
      </div>
      <el-button
        v-else-if="!isWeekly"
        class="note-composer__new"
        data-test="note-composer-reopen"
        @click="openComposer"
      >
        新建日志
      </el-button>
    </template>

    <template #editor>
      <div
        v-if="editId != null"
        class="note-inline-editor note-editor-panel"
        data-test="note-inline-editor"
      >
        <NoteEditor
          ref="inlineEditorRef"
          :key="`edit:${editId}`"
          :note-id="editId"
          compact
          closable
          @saved="onInlineSaved"
          @cancel="closeInlineEditor"
        />
        <div class="note-inline-editor__actions">
          <span
            class="note-inline-editor__status"
            data-test="note-inline-status"
            :data-status="inlineEditorRef?.status ?? 'idle'"
          >
            <template v-if="inlineEditorRef?.status === 'saving'">
              <span class="note-inline-editor__save is-saving">保存中…</span>
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
            <span v-if="inlineEditorRef?.savedAt" class="note-inline-editor__save is-saved">
              <template
                v-if="
                  inlineEditorRef?.status === 'saving' || inlineEditorRef?.status === 'error'
                "
              >
                ·
              </template>
              已保存 {{ inlineEditorRef.savedAt }}
            </span>
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
          <h3 class="note-side__title">{{ sideTitle }}</h3>
          <NoteMiniList
            :notes="side.notes.value"
            :loading="side.loading.value"
            :active-id="activeId"
            :period="period"
            @open="onEdit"
            @view-all="router.push({ name: isWeekly ? 'WeeklyNoteHome' : 'NoteHome' })"
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
.note-inline-editor__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 8px;
}
.note-inline-editor__actions--end {
  justify-content: flex-end;
}
.note-inline-editor__status {
  min-height: 20px;
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
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
/* 新建 / 编辑统一：去掉外层标题行和包裹留白，只保留编辑器自身的边框 */
.note-composer,
.note-editor-panel {
  padding: 0;
  border: 0;
  background: transparent;
}
.note-composer :deep(.note-editor),
.note-editor-panel :deep(.note-editor) {
  padding: 0;
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
