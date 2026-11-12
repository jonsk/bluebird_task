<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, MagicStick, Setting } from '@element-plus/icons-vue'
import WeeklyReportEditor from '@/components/weekly/WeeklyReportEditor.vue'
import BbFormDialog from '@/components/bb/BbFormDialog.vue'
import { formatClock } from '@/utils/format'
import {
  deleteWeeklyReport,
  generateWeeklyReport,
  getWeeklyReport,
  getWeeklyReportPreference,
  listWeeklyReportWeeks,
  saveWeeklyReportPreference,
  updateWeeklyReport,
} from '@/api/weeklyReport'
import type {
  WeeklyReportPreference,
  WeeklyReportStatus,
  WeeklyReportStyle,
  WeeklyReportVO,
  WeeklyReportWeek,
} from '@/types/weeklyReport'

/**
 * AI周工作总结：左侧按周列表，右侧编辑器。
 *
 * - 左侧只列「当前周及之前、且至少 1 条日志」的周（后端 /weekly-reports/weeks 口径）；
 *   选中某周即可生成，没生成的周也会出现在列表里。
 * - 「AI 生成」异步执行：后端先落 GENERATING，前端轮询到 DONE/FAILED 为止；
 * - **生成即定稿**（没有草稿态、没有「保存定稿」按钮），正文修改后自动保存；
 * - 同一周只保留一份，已存在时再次生成需要确认覆盖。
 */
const statusMeta: Record<WeeklyReportStatus, { label: string; type: 'success' | 'warning' | 'info' | 'danger' }> = {
  GENERATING: { label: '生成中', type: 'warning' },
  DONE: { label: '已定稿', type: 'success' },
  FAILED: { label: '生成失败', type: 'danger' },
}

/** 停止输入多久后自动保存（毫秒），与编辑日志保持一致 */
const AUTO_SAVE_DELAY = 1200

const styles: Array<{ value: WeeklyReportStyle; label: string }> = [
  { value: 'concise', label: '简洁' },
  { value: 'detailed', label: '详细' },
  { value: 'result', label: '结果导向' },
  { value: 'plan', label: '计划导向' },
]

const loadingWeeks = ref(false)
const loadingDetail = ref(false)
const generating = ref(false)
const weeks = ref<WeeklyReportWeek[]>([])
const selectedWeek = ref('')
const detail = ref<WeeklyReportVO | null>(null)
const content = ref('')
const prefVisible = ref(false)
const prefSaving = ref(false)
const preference = ref<WeeklyReportPreference>({ style: 'concise' })

/* ==================== 自动保存状态 ==================== */

const saveStatus = ref<'idle' | 'saving' | 'saved' | 'error'>('idle')
const savedAt = ref('')
const saveError = ref('')

const lastSaved = ref('')
/** 回显内容期间忽略自动保存，避免刚打开就触发一次保存。 */
let hydrating = false
let saveTimer: ReturnType<typeof setTimeout> | undefined
let savingPromise: Promise<void> | null = null
let pollTimer: ReturnType<typeof setTimeout> | null = null

const currentWeek = computed(() => mondayOf(new Date()))
const selected = computed(() => weeks.value.find((w) => w.weekStart === selectedWeek.value) ?? null)

function pad(n: number): string {
  return String(n).padStart(2, '0')
}

function toDateString(d: Date): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** 归一到一个自然周的周一（本地时区；服务端会再按 Asia/Shanghai 归一一次）。 */
function mondayOf(date: Date): string {
  const d = new Date(date.getFullYear(), date.getMonth(), date.getDate())
  const day = d.getDay() === 0 ? 7 : d.getDay()
  d.setDate(d.getDate() - day + 1)
  return toDateString(d)
}

function statusOf(status?: WeeklyReportStatus | null): { label: string; type: 'success' | 'warning' | 'info' | 'danger' } | null {
  if (!status) return null
  return statusMeta[status] ?? { label: status, type: 'info' }
}

/* ==================== 左侧周次列表 ==================== */

async function loadWeeks(): Promise<void> {
  loadingWeeks.value = true
  try {
    weeks.value = await listWeeklyReportWeeks()
    if (!weeks.value.length) {
      selectedWeek.value = ''
      detail.value = null
      content.value = ''
      lastSaved.value = ''
      return
    }
    const exists = weeks.value.some((w) => w.weekStart === selectedWeek.value)
    if (!exists) {
      // 默认落在本周（有日志时），否则落在最近有日志的一周
      const thisWeek = weeks.value.find((w) => w.weekStart === currentWeek.value)
      selectedWeek.value = (thisWeek ?? weeks.value[0]).weekStart
    }
    await openSelectedWeek()
  } catch (e) {
    ElMessage.error((e as Error).message || '周次列表加载失败')
  } finally {
    loadingWeeks.value = false
  }
}

/** 取消在途轮询：切周/切换报告前必须停掉，否则旧轮询会把 A 周内容写进 B 周编辑器。 */
function stopPoll(): void {
  if (pollTimer) clearTimeout(pollTimer)
  pollTimer = null
}

/** 按当前选中周加载已有周报（没有周报则清空编辑器）。 */
async function openSelectedWeek(): Promise<void> {
  stopPoll()
  const row = selected.value
  saveStatus.value = 'idle'
  savedAt.value = ''
  saveError.value = ''
  if (!row?.reportId) {
    detail.value = null
    content.value = ''
    lastSaved.value = ''
    return
  }
  await loadDetail(row.reportId)
}

async function selectWeek(row: WeeklyReportWeek): Promise<void> {
  if (row.weekStart === selectedWeek.value) return
  await saveNow()
  stopPoll()
  selectedWeek.value = row.weekStart
  await openSelectedWeek()
}

/** 详情请求序号：连续切周时只让最后一次响应写回。 */
let detailSeq = 0

async function loadDetail(id: number): Promise<void> {
  const seq = ++detailSeq
  loadingDetail.value = true
  try {
    const d = await getWeeklyReport(id)
    if (seq !== detailSeq) return
    applyDetail(d)
  } catch (e) {
    if (seq !== detailSeq) return
    ElMessage.error((e as Error).message || 'AI 周工作总结加载失败')
  } finally {
    if (seq === detailSeq) loadingDetail.value = false
  }
}

function applyDetail(d: WeeklyReportVO): void {
  detail.value = d
  hydrating = true
  try {
    content.value = d.contentHtml ?? ''
  } finally {
    hydrating = false
  }
  lastSaved.value = content.value
  if (d.status === 'GENERATING') schedulePoll()
}

function refreshWeekRow(d: WeeklyReportVO): void {
  const row = weeks.value.find((w) => w.weekStart === d.weekStart)
  if (!row) return
  row.reportId = d.id
  row.status = d.status
  row.errorMessage = d.errorMessage ?? null
}

/* ==================== 生成 ==================== */

function schedulePoll(): void {
  stopPoll()
  const id = detail.value?.id
  const weekStart = detail.value?.weekStart ?? selectedWeek.value
  if (id == null) return
  /** 轮询是否仍属于当前周与当前报告（切周后一律丢弃旧结果）。 */
  const isCurrent = (): boolean => selectedWeek.value === weekStart && detail.value?.id === id
  pollTimer = setTimeout(async () => {
    pollTimer = null
    if (!isCurrent()) return
    try {
      const d = await getWeeklyReport(id)
      if (!isCurrent()) return
      const wasGenerating = detail.value?.status === 'GENERATING'
      applyDetail(d)
      refreshWeekRow(d)
      if (wasGenerating && d.status !== 'GENERATING') {
        if (d.status === 'FAILED') {
          ElMessage.error(d.errorMessage || 'AI 周工作总结生成失败')
        } else {
          ElMessage.success('AI 周工作总结已生成')
        }
      }
    } catch {
      if (isCurrent()) schedulePoll()
    }
  }, 3000)
}

async function startGenerate(): Promise<void> {
  if (generating.value) return
  const weekStart = selectedWeek.value
  if (!weekStart) {
    ElMessage.warning('请先在左侧选择要生成 AI 周工作总结的周')
    return
  }
  const existing = selected.value
  if (existing?.reportId) {
    try {
      await ElMessageBox.confirm(
        `「${weekStart} ~ ${existing.weekEnd}」已有 AI 周工作总结，重新生成会覆盖当前正文。`,
        '重新生成',
        { type: 'warning', confirmButtonText: '覆盖生成' },
      )
    } catch {
      return
    }
    await saveNow()
  }
  generating.value = true
  try {
    const d = await generateWeeklyReport({ weekStart, overwrite: Boolean(existing?.reportId) })
    refreshWeekRow(d)
    // 生成期间用户可能切到别的周：只刷新周列表，不把 A 周内容写进 B 周编辑器。
    if (selectedWeek.value !== weekStart) return
    applyDetail(d)
    if (d.status === 'GENERATING') {
      ElMessage.info('已开始生成，请稍候…')
    } else if (d.status !== 'FAILED') {
      ElMessage.success('AI 周工作总结已生成')
    }
  } catch (e) {
    ElMessage.error((e as Error).message || '生成失败')
  } finally {
    generating.value = false
  }
}

/* ==================== 自动保存 ==================== */

watch(content, () => {
  if (hydrating) return
  const d = detail.value
  if (!d || d.status === 'GENERATING') return
  if (content.value === lastSaved.value) return
  if (saveStatus.value !== 'error') saveStatus.value = 'idle'
  scheduleSave()
})

function scheduleSave(): void {
  if (saveTimer) clearTimeout(saveTimer)
  saveTimer = setTimeout(() => {
    saveTimer = undefined
    void saveNow()
  }, AUTO_SAVE_DELAY)
}

async function saveNow(): Promise<void> {
  if (saveTimer) {
    clearTimeout(saveTimer)
    saveTimer = undefined
  }
  const d = detail.value
  if (!d || d.status === 'GENERATING') return
  if (savingPromise) await savingPromise
  if (content.value === lastSaved.value) return

  const value = content.value
  const version = d.version
  saveStatus.value = 'saving'
  saveError.value = ''
  savingPromise = (async () => {
    try {
      const updated = await updateWeeklyReport(d.id, { contentHtml: value, version })
      applyDetail(updated)
      refreshWeekRow(updated)
      saveStatus.value = 'saved'
      savedAt.value = formatClock(updated.updatedAt) || formatClock()
    } catch (e) {
      saveStatus.value = 'error'
      saveError.value = (e as Error)?.message || '保存失败'
    }
  })()
  try {
    await savingPromise
  } finally {
    savingPromise = null
  }
  if (content.value !== lastSaved.value) scheduleSave()
}

/* ==================== 删除 ==================== */

async function removeReport(): Promise<void> {
  const d = detail.value
  if (!d) return
  try {
    await ElMessageBox.confirm('删除后不可恢复，确认删除这份 AI 周工作总结？', '删除 AI 周工作总结', {
      type: 'warning',
      confirmButtonText: '删除',
    })
  } catch {
    return
  }
  try {
    await deleteWeeklyReport(d.id)
    const row = weeks.value.find((w) => w.weekStart === d.weekStart)
    if (row) {
      row.reportId = null
      row.status = null
      row.errorMessage = null
    }
    detail.value = null
    content.value = ''
    lastSaved.value = ''
    saveStatus.value = 'idle'
    savedAt.value = ''
    ElMessage.success('已删除')
  } catch (e) {
    ElMessage.error((e as Error).message || '删除失败')
  }
}

/* ==================== 个人规则 ==================== */

async function openPreference(): Promise<void> {
  try {
    preference.value = await getWeeklyReportPreference()
  } catch {
    preference.value = { style: 'concise' }
  }
  prefVisible.value = true
}

async function savePreference(): Promise<void> {
  prefSaving.value = true
  try {
    preference.value = await saveWeeklyReportPreference(preference.value)
    prefVisible.value = false
    ElMessage.success('已保存个人规则')
  } catch (e) {
    ElMessage.error((e as Error).message || '保存失败')
  } finally {
    prefSaving.value = false
  }
}

onMounted(async () => {
  await loadWeeks()
})

onBeforeUnmount(() => {
  if (pollTimer) clearTimeout(pollTimer)
  pollTimer = null
  if (saveTimer) clearTimeout(saveTimer)
  saveTimer = undefined
  if (content.value !== lastSaved.value) void saveNow()
})
</script>

<template>
  <div class="weekly">
    <aside class="weekly__side">
      <div class="weekly__side-head">
        <h2 class="weekly__side-title">我的 AI 周工作总结</h2>
        <el-button text size="small" :icon="Setting" data-test="weekly-preference" @click="openPreference">
          个人规则
        </el-button>
      </div>

      <div v-loading="loadingWeeks" class="weekly__list">
        <button
          v-for="row in weeks"
          :key="row.weekStart"
          type="button"
          class="weekly__item"
          :class="{ 'is-active': selectedWeek === row.weekStart }"
          @click="selectWeek(row)"
        >
          <div class="weekly__item-top">
            <span class="weekly__item-week">{{ row.weekStart }} ~ {{ row.weekEnd }}</span>
            <el-tag v-if="statusOf(row.status)" size="small" :type="statusOf(row.status)!.type" effect="plain">
              {{ statusOf(row.status)!.label }}
            </el-tag>
          </div>
          <p class="weekly__item-summary">{{ row.noteCount }} 条日志</p>
        </button>
        <p v-if="!loadingWeeks && !weeks.length" class="weekly__empty">
          当前周及之前还没有工作日志，先去「日志」写几条再来生成。
        </p>
      </div>
    </aside>

    <section class="weekly__main">
      <header class="weekly__head">
        <div class="weekly__meta">
          <template v-if="detail?.modelName">
            生成模型：{{ detail.modelName }}
            <span v-if="detail.sourceNoteIds?.length"> · 引用 {{ detail.sourceNoteIds.length }} 条日志</span>
            <span v-if="detail.generatedAt"> · {{ new Date(detail.generatedAt).toLocaleString('zh-CN') }}</span>
          </template>
          <template v-else-if="selected">
            {{ selected.weekStart }} ~ {{ selected.weekEnd }} · {{ selected.noteCount }} 条日志
          </template>
        </div>
        <div class="weekly__ops">
          <el-button
            size="small"
            type="primary"
            :icon="MagicStick"
            :loading="generating"
            :disabled="!selectedWeek"
            data-test="weekly-generate"
            @click="startGenerate"
          >
            {{ selected?.reportId ? '重新生成' : 'AI 生成' }}
          </el-button>
          <el-button
            v-if="detail"
            size="small"
            text
            type="danger"
            :icon="Delete"
            data-test="weekly-delete"
            @click="removeReport"
          >
            删除
          </el-button>
        </div>
      </header>

      <el-alert
        v-if="detail?.status === 'FAILED'"
        type="error"
        show-icon
        :closable="false"
        :title="detail.errorMessage || 'AI 生成失败，请稍后重试'"
        class="weekly__alert"
      />

      <div v-loading="loadingDetail" class="weekly__editor">
        <WeeklyReportEditor
          v-if="detail"
          v-model="content"
          :readonly="detail.status === 'GENERATING'"
          :save-status="saveStatus"
          :saved-at="savedAt"
          :error-message="saveError"
          @retry="saveNow"
        />
        <el-empty v-else :description="selected ? '该周还没有 AI 周工作总结' : '先在左侧选择一周'">
          <el-button
            v-if="selectedWeek"
            type="primary"
            :icon="MagicStick"
            :loading="generating"
            @click="startGenerate"
          >
            生成这一周的 AI 周工作总结
          </el-button>
        </el-empty>
      </div>
    </section>

    <BbFormDialog
      v-model:open="prefVisible"
      title="AI 周工作总结个人规则"
      width="520px"
      :loading="prefSaving"
      @submit="savePreference"
    >
      <el-form label-width="90px">
        <el-form-item label="行文风格">
          <el-select v-model="preference.style" class="weekly__pref-control">
            <el-option v-for="s in styles" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="重点关注">
          <el-input v-model="preference.focus" maxlength="200" placeholder="例如：交付进度、跨团队协作" />
        </el-form-item>
        <el-form-item label="补充规则">
          <el-input
            v-model="preference.customRules"
            type="textarea"
            :rows="5"
            maxlength="2000"
            show-word-limit
            placeholder="只影响行文；JSON 结构与引用要求由系统固定，不能被覆盖"
          />
        </el-form-item>
      </el-form>
    </BbFormDialog>
  </div>
</template>

<style scoped>
.weekly {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 16px;
  height: 100%;
  min-height: 0;
  padding: 16px 20px;
  box-sizing: border-box;
}
.weekly__side {
  display: flex;
  flex-direction: column;
  min-height: 0;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--bb-radius-sm);
  background: var(--bg-primary);
  overflow: hidden;
}
.weekly__side-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.weekly__side-title {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  color: var(--bb-text-primary);
}
.weekly__list {
  flex: 1;
  min-height: 0;
  padding: 8px;
  overflow-y: auto;
}
.weekly__item {
  display: block;
  width: 100%;
  margin-bottom: 6px;
  padding: 10px;
  border: 1px solid transparent;
  border-radius: var(--bb-radius);
  background: transparent;
  text-align: left;
  cursor: pointer;
}
.weekly__item:hover {
  background: var(--bg-hover);
}
.weekly__item.is-active {
  border-color: var(--bb-color-primary);
  background: var(--bg-active);
}
.weekly__item-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.weekly__item-week {
  font-size: 12px;
  font-weight: 600;
  color: var(--bb-text-primary);
}
.weekly__item-summary {
  margin: 6px 0 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--bb-text-muted);
}
.weekly__empty {
  margin: 16px 8px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--bb-text-muted);
}
.weekly__main {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
}
.weekly__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
  padding: 4px 0 12px;
}
.weekly__meta {
  font-size: 12px;
  color: var(--bb-text-muted);
}
.weekly__ops {
  display: flex;
  align-items: center;
  gap: 8px;
}
.weekly__alert {
  margin-bottom: 10px;
}
.weekly__editor {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
.weekly__pref-control {
  width: 100%;
}
@media (max-width: 980px) {
  .weekly {
    grid-template-columns: minmax(0, 1fr);
    height: auto;
  }
  .weekly__side {
    max-height: 260px;
  }
}
</style>
