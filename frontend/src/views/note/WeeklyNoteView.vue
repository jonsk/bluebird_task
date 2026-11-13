<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Calendar, Plus } from '@element-plus/icons-vue'
import BbPageHeader from '@/components/bb/BbPageHeader.vue'
import BbMiniCalendar from '@/components/bb/BbMiniCalendar.vue'
import NoteEditor from '@/components/note/NoteEditor.vue'
import NoteMiniList from '@/components/note/NoteMiniList.vue'
import { createWeeklyNote, listNotes } from '@/api/note'
import { BizError, HttpError } from '@/api/http'
import { useNoteList } from '@/composables/useNoteList'
import { navSelect } from '@/composables/useNavReload'
import {
  formatHeaderDate,
  shiftDay,
  todayWeekStart,
  toOffsetIso,
  weekRangeLabel,
  weekStartOf,
} from '@/utils/date'
import { getLastViewedWeekStart, rememberViewedWeek } from '@/utils/weeklyNoteSession'
import type { NoteBrief, NoteDetailVO } from '@/types/note'

interface CalendarDayCount {
  date: string
  count: number
}

type InlineEditorExpose = {
  status: 'idle' | 'saving' | 'saved' | 'error'
  savedAt: string
  errorMessage: string
  saveNow: () => Promise<void>
}

const route = useRoute()
const title = computed(() => (route.meta.title as string) || '每周工作记录')
const headerDate = formatHeaderDate()

const CURRENT_MONDAY = todayWeekStart()
const NEXT_MONDAY = shiftDay(CURRENT_MONDAY, 7)
const RECENT_START = shiftDay(CURRENT_MONDAY, -84)
const FUTURE_PAGE_SIZE = 20

const rememberedWeek = getLastViewedWeekStart()
const editId = ref<number | null>(null)
const activeWeekStart = ref(rememberedWeek ?? CURRENT_MONDAY)
const selectedDate = ref<string>(rememberedWeek ?? CURRENT_MONDAY)
const bootLoading = ref(true)
const editorRef = ref<InlineEditorExpose | null>(null)

const activeWeekLabel = computed(() => weekRangeLabel(activeWeekStart.value))

/** 最近三个月：当前周 + 往前 12 周，共 13 条，一次取完。 */
const recent = useNoteList({
  isolated: true,
  initialQuery: {
    start: RECENT_START,
    end: CURRENT_MONDAY,
    sort: 'log_date_desc',
    page: 1,
    size: 100,
  },
})

/** 未来周：单独分页，避免未来记录无上限时被 size=100 截断。 */
const futureNotes = ref<NoteBrief[]>([])
const futureTotal = ref(0)
const futurePage = ref(1)
const futureLoading = ref(false)
let futureSeq = 0

const futureHasMore = computed(() => futureNotes.value.length < futureTotal.value)

async function loadFuture(reset = false): Promise<void> {
  const target = reset ? 1 : futurePage.value + 1
  if (!reset && !futureHasMore.value) return
  const seq = ++futureSeq
  futureLoading.value = true
  try {
    const data = await listNotes({
      scope: 'mine',
      period: 'WEEK',
      start: NEXT_MONDAY,
      sort: 'log_date_asc',
      page: target,
      size: FUTURE_PAGE_SIZE,
    })
    if (seq !== futureSeq) return
    const rows = data.list ?? []
    futureNotes.value = reset ? rows : [...futureNotes.value, ...rows]
    futureTotal.value = data.total ?? 0
    futurePage.value = target
  } catch (e) {
    if (seq === futureSeq) ElMessage.error(errorText(e, '未来周记录加载失败'))
  } finally {
    if (seq === futureSeq) futureLoading.value = false
  }
}

function errorText(e: unknown, fallback: string): string {
  if (e instanceof BizError || e instanceof HttpError) return e.message
  if (e instanceof Error && e.message) return e.message
  return fallback
}

function cachedNote(weekStart: string): NoteBrief | null {
  return (
    [...recent.notes.value, ...futureNotes.value].find((n) => n.logDate === weekStart) ?? null
  )
}

async function findWeekNote(weekStart: string): Promise<NoteBrief | null> {
  const cached = cachedNote(weekStart)
  if (cached) return cached
  const data = await listNotes({
    scope: 'mine',
    period: 'WEEK',
    date: weekStart,
    page: 1,
    size: 1,
  })
  return data.list?.[0] ?? null
}

/**
 * 打开指定周：先保存当前编辑内容，再切到该周。
 * `selectedDay` 用于日历跨月徽标：徽标可能落在周日，但记录本身仍归到周一。
 */
async function openWeek(
  weekStart: string,
  options: { silentMissing?: boolean; selectedDay?: string; toast?: string } = {},
): Promise<boolean> {
  try {
    await editorRef.value?.saveNow()
    const note = await findWeekNote(weekStart)
    if (!note?.id) {
      if (!options.silentMissing) ElMessage.info('该周还没有工作记录')
      return false
    }
    editId.value = Number(note.id)
    activeWeekStart.value = note.logDate || weekStart
    selectedDate.value = options.selectedDay ?? weekStart
    rememberViewedWeek(weekStart)
    if (options.toast) ElMessage.success(options.toast)
    return true
  } catch (e) {
    ElMessage.error(errorText(e, '周记录加载失败'))
    return false
  }
}

async function openCurrentWeek(): Promise<void> {
  bootLoading.value = true
  try {
    const detail = await createWeeklyNote()
    if (detail.id != null) {
      editId.value = Number(detail.id)
      activeWeekStart.value = detail.logDate || CURRENT_MONDAY
      selectedDate.value = CURRENT_MONDAY
      rememberViewedWeek(CURRENT_MONDAY)
    }
    recent.refresh()
    await loadFuture(true)
  } catch (e) {
    ElMessage.error(errorText(e, '本周工作记录加载失败'))
  } finally {
    bootLoading.value = false
  }
}

/** 首次进入当前周；之后恢复本次会话最后查看周，记录已删除则回退当前周。 */
async function openInitialWeek(): Promise<void> {
  const remembered = getLastViewedWeekStart()
  if (remembered) {
    bootLoading.value = true
    const opened = await openWeek(remembered, { silentMissing: true })
    bootLoading.value = false
    if (opened) return
  }
  await openCurrentWeek()
}

function openByNoteId(id: number): void {
  const note = [...futureNotes.value, ...recent.notes.value].find((n) => Number(n.id) === Number(id))
  if (note?.logDate) void openWeek(note.logDate)
}

watch(navSelect, (sig) => {
  if (!sig || sig.name !== 'WeeklyNoteHome') return
  void openInitialWeek()
})

onMounted(() => {
  void loadFuture(true)
  void openInitialWeek()
})

/* ==================== 日历 ==================== */

/** 当前月份有记录的日期；没有点的日期点击不响应。 */
const calendarDots = ref<Set<string>>(new Set())

async function calendarLoader(year: number, month: number): Promise<CalendarDayCount[]> {
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
  const dots = new Set<string>()
  for (const note of data.list ?? []) {
    const start = note.logDate
    if (!start) continue
    // 跨月周标在落在本月的最后一天；该日期仍属于同一 ISO 周，点击时归一化回周一。
    const badgeDay = start < monthStart ? shiftDay(start, 6) : start
    if (badgeDay > monthEnd) continue
    byDay[badgeDay] = (byDay[badgeDay] ?? 0) + 1
    dots.add(badgeDay)
  }
  calendarDots.value = dots
  return Object.entries(byDay).map(([date, count]) => ({ date, count }))
}

function onSelectDate(date: string | null): void {
  if (!date || !calendarDots.value.has(date)) return
  selectedDate.value = date
  void openWeek(weekStartOf(date), { silentMissing: true, selectedDay: date })
}

/* ==================== 新建未来周 ==================== */

const createOpen = ref(false)
const draftDate = ref<string | null>(NEXT_MONDAY)
const creating = ref(false)
const draftMonday = computed(() => (draftDate.value ? weekStartOf(draftDate.value) : ''))
const canCreate = computed(
  () => Boolean(draftMonday.value) && draftMonday.value > CURRENT_MONDAY,
)

function openCreate(): void {
  draftDate.value = NEXT_MONDAY
  createOpen.value = true
}

function disabledDate(date: Date): boolean {
  return weekStartOf(toOffsetIso(date).slice(0, 10)) <= CURRENT_MONDAY
}

async function confirmCreate(): Promise<void> {
  const monday = draftMonday.value
  if (!monday) {
    ElMessage.warning('请选择周')
    return
  }
  if (monday <= CURRENT_MONDAY) {
    ElMessage.warning('只能创建本周之后的周记录')
    return
  }
  creating.value = true
  try {
    await editorRef.value?.saveNow()
    const existing = await findWeekNote(monday)
    if (existing?.id) {
      createOpen.value = false
      await openWeek(monday, { toast: '该周已有记录，已打开' })
      return
    }
    const detail = await createWeeklyNote({ weekStart: monday })
    if (detail.id == null) throw new Error('创建周记录失败')
    createOpen.value = false
    editId.value = Number(detail.id)
    activeWeekStart.value = detail.logDate || monday
    selectedDate.value = monday
    rememberViewedWeek(monday)
    await loadFuture(true)
    ElMessage.success(`已创建 ${weekRangeLabel(monday)} 的周记录`)
  } catch (e) {
    ElMessage.error(errorText(e, '创建周记录失败'))
  } finally {
    creating.value = false
  }
}

/** 自动保存后只更新列表摘要，不重拉列表，避免骨架屏闪烁。 */
function onEditorSaved(detail: NoteDetailVO): void {
  const id = Number(detail.id)
  const summary = detail.contentSummary ?? ''
  for (const list of [recent.notes.value, futureNotes.value]) {
    const note = list.find((n) => Number(n.id) === id)
    if (note) note.contentSummary = summary
  }
  if (detail.logDate) activeWeekStart.value = detail.logDate
}

function retryInlineSave(): void {
  void editorRef.value?.saveNow()
}
</script>

<template>
  <div class="right-box">
    <div class="right-box-div">
      <div class="right-main">
        <BbPageHeader
          class="page-shell--note"
          :title="title"
          :icon="Calendar"
          :date="headerDate"
        >
          <template #title-extra>
            <span class="weekly-header__range" data-test="weekly-range">
              {{ activeWeekLabel }}
            </span>
          </template>
          <template #actions>
            <span
              class="weekly-header__status"
              data-test="weekly-inline-status"
              :data-status="editorRef?.status ?? 'idle'"
            >
              <template v-if="editorRef?.status === 'saving'">
                <span class="weekly-header__save is-saving">保存中…</span>
              </template>
              <template v-else-if="editorRef?.status === 'error'">
                <button
                  class="weekly-header__save weekly-header__retry"
                  type="button"
                  @click="retryInlineSave"
                >
                  {{ editorRef.errorMessage }}，点击重试
                </button>
              </template>
              <span v-if="editorRef?.savedAt" class="weekly-header__save is-saved">
                <template
                  v-if="editorRef?.status === 'saving' || editorRef?.status === 'error'"
                >
                  ·
                </template>
                已保存 {{ editorRef.savedAt }}
              </span>
            </span>
          </template>
        </BbPageHeader>

        <section class="right-box-bottom">
          <div class="note-page weekly-page">
            <div v-if="editId != null" class="weekly-editor" data-test="weekly-inline-editor">
              <NoteEditor
                ref="editorRef"
                :key="`weekly:${editId}`"
                :note-id="editId"
                compact
                period="WEEK"
                @saved="onEditorSaved"
              />
            </div>
            <div v-else v-loading="bootLoading" class="weekly-editor__placeholder">
              <el-empty
                v-if="!bootLoading"
                description="本周工作记录加载失败，请稍后重试"
                :image-size="64"
              >
                <el-button size="small" data-test="weekly-retry" @click="openInitialWeek">
                  重试
                </el-button>
              </el-empty>
            </div>
          </div>
        </section>
      </div>

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
          <div class="note-side__head">
            <h3 class="note-side__title">周记录</h3>
            <el-tooltip content="新建未来周记录" placement="left">
              <el-button
                class="note-side__create"
                circle
                size="small"
                :icon="Plus"
                aria-label="新建未来周记录"
                data-test="weekly-create"
                @click="openCreate"
              />
            </el-tooltip>
          </div>

          <section
            v-if="futureNotes.length"
            class="note-side__group"
            data-test="weekly-future-group"
          >
            <h4 class="note-side__group-title">未来</h4>
            <NoteMiniList
              :notes="futureNotes"
              :active-id="editId"
              period="WEEK"
              :show-footer="false"
              tag-text="未来"
              @open="openByNoteId"
            />
            <div v-if="futureHasMore" class="note-side__more">
              <el-button
                text
                size="small"
                :loading="futureLoading"
                data-test="weekly-future-more"
                @click="loadFuture(false)"
              >
                加载更多
              </el-button>
            </div>
          </section>

          <section class="note-side__group" data-test="weekly-recent-group">
            <h4 class="note-side__group-title">最近三个月</h4>
            <NoteMiniList
              :notes="recent.notes.value"
              :loading="recent.loading.value"
              :active-id="editId"
              period="WEEK"
              :show-footer="false"
              empty-text="最近三个月暂无记录"
              @open="openByNoteId"
            />
          </section>
        </div>
      </div>
    </div>

    <el-dialog v-model="createOpen" title="新建未来周记录" width="400px" append-to-body>
      <div class="weekly-create">
        <el-date-picker
          v-model="draftDate"
          type="week"
          value-format="YYYY-MM-DD"
          format="YYYY-MM-DD"
          :disabled-date="disabledDate"
          :clearable="false"
          placeholder="选择未来周"
          class="weekly-create__picker"
        />
        <p v-if="draftMonday" class="weekly-create__range">
          {{ weekRangeLabel(draftMonday) }}
        </p>
      </div>
      <template #footer>
        <el-button @click="createOpen = false">取消</el-button>
        <el-button
          type="primary"
          :loading="creating"
          :disabled="!canCreate"
          data-test="weekly-create-submit"
          @click="confirmCreate"
        >
          创建并打开
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.right-box {
  min-height: 100vh;
  box-sizing: border-box;
}
.right-box-div {
  display: flex;
  justify-content: space-between;
}
.right-main {
  flex: 1;
  min-width: 0;
}
.right-box-bottom {
  height: calc(100vh - 80px);
  overflow-y: auto;
}
.note-page {
  padding: 0 var(--bb-page-padding) 24px;
  box-sizing: border-box;
}
.weekly-page {
  padding-top: 8px;
}
.weekly-header__range {
  margin-left: 10px;
  font-size: 12px;
  font-weight: 400;
  color: var(--bb-text-muted);
}
.weekly-header__status {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
  flex-wrap: wrap;
}
.weekly-header__save {
  font-size: 12px;
  font-weight: 400;
}
.weekly-header__save.is-saving {
  color: var(--bb-text-secondary);
}
.weekly-header__save.is-saved {
  color: var(--bb-success, #67c23a);
}
.weekly-header__save.weekly-header__retry {
  padding: 0;
  border: none;
  background: none;
  cursor: pointer;
  color: var(--bb-danger, #f56c6c);
  text-decoration: underline;
}
.weekly-editor__placeholder {
  min-height: 240px;
}
.weekly-editor :deep(.note-editor) {
  padding: 0;
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
.note-side__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}
.note-side__title {
  margin: 0;
  font-size: 14px;
  font-weight: 500;
  color: var(--bb-text-secondary);
}
.note-side__group + .note-side__group {
  margin-top: 14px;
}
.note-side__group-title {
  margin: 0 0 4px;
  font-size: 12px;
  font-weight: 500;
  color: var(--bb-text-muted);
}
.note-side__more {
  display: flex;
  justify-content: center;
  margin-top: 4px;
}
.weekly-create__picker {
  width: 100%;
}
.weekly-create__range {
  margin: 10px 0 0;
  font-size: 13px;
  color: var(--bb-text-secondary);
}
</style>

<!-- 页头共享样式：非 scoped，保证规则能作用到 BbPageHeader 组件内部的节点 -->
<style>
@import '../../styles/page-shell.css';
</style>
