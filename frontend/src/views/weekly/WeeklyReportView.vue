<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, MagicStick, Setting } from '@element-plus/icons-vue'
import WeeklyReportEditor from '@/components/weekly/WeeklyReportEditor.vue'
import {
  deleteWeeklyReport,
  generateWeeklyReport,
  getWeeklyReport,
  getWeeklyReportPreference,
  listWeeklyReports,
  saveWeeklyReportPreference,
  updateWeeklyReport,
} from '@/api/weeklyReport'
import type {
  WeeklyReportBrief,
  WeeklyReportPreference,
  WeeklyReportStatus,
  WeeklyReportStyle,
  WeeklyReportVO,
} from '@/types/weeklyReport'

/**
 * 工作周报：左侧历史周报列表，右侧编辑器。
 *
 * - 「AI 生成」异步执行：后端先落 GENERATING，前端轮询到 DRAFT/FAILED 为止；
 * - 生成的是草稿，用户可直接编辑，点「保存定稿」后状态变为 DONE；
 * - 同一周只保留一份，已存在时再次生成需要确认覆盖。
 */
const statusMeta: Record<WeeklyReportStatus, { label: string; type: 'success' | 'warning' | 'info' | 'danger' }> = {
  GENERATING: { label: '生成中', type: 'warning' },
  DRAFT: { label: '草稿', type: 'info' },
  DONE: { label: '已定稿', type: 'success' },
  FAILED: { label: '生成失败', type: 'danger' },
}

const styles: Array<{ value: WeeklyReportStyle; label: string }> = [
  { value: 'concise', label: '简洁' },
  { value: 'detailed', label: '详细' },
  { value: 'result', label: '结果导向' },
  { value: 'plan', label: '计划导向' },
]

const loadingList = ref(false)
const loadingDetail = ref(false)
const generating = ref(false)
const saving = ref(false)
const reports = ref<WeeklyReportBrief[]>([])
const detail = ref<WeeklyReportVO | null>(null)
const content = ref('')
const lastSavedHtml = ref('')
const weekDate = ref<Date>(new Date())
const prefVisible = ref(false)
const prefSaving = ref(false)
const preference = ref<WeeklyReportPreference>({ style: 'concise' })

let pollTimer: ReturnType<typeof setTimeout> | null = null

const dirty = computed(() => content.value !== lastSavedHtml.value)
const currentWeek = computed(() => mondayOf(weekDate.value))

const statusInfo = computed(() => (detail.value ? statusMeta[detail.value.status] : null))

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

function weekEndOf(start: string): string {
  const [y, m, d] = start.split('-').map(Number)
  const end = new Date(y, m - 1, d + 6)
  return toDateString(end)
}

async function loadList(): Promise<void> {
  loadingList.value = true
  try {
    const data = await listWeeklyReports(1, 100)
    reports.value = data.list
  } catch (e) {
    ElMessage.error((e as Error).message || '周报列表加载失败')
  } finally {
    loadingList.value = false
  }
}

async function loadDetail(id: number): Promise<void> {
  loadingDetail.value = true
  try {
    const d = await getWeeklyReport(id)
    applyDetail(d)
  } catch (e) {
    ElMessage.error((e as Error).message || '周报加载失败')
  } finally {
    loadingDetail.value = false
  }
}

function applyDetail(d: WeeklyReportVO): void {
  detail.value = d
  content.value = d.contentHtml ?? ''
  lastSavedHtml.value = content.value
  if (d.status === 'GENERATING') schedulePoll()
}

function schedulePoll(): void {
  if (pollTimer) clearTimeout(pollTimer)
  const id = detail.value?.id
  if (id == null) return
  pollTimer = setTimeout(async () => {
    pollTimer = null
    try {
      const d = await getWeeklyReport(id)
      const wasGenerating = detail.value?.status === 'GENERATING'
      applyDetail(d)
      if (wasGenerating && d.status !== 'GENERATING') {
        if (d.status === 'FAILED') {
          ElMessage.error(d.errorMessage || '周报生成失败')
        } else {
          ElMessage.success('周报已生成')
        }
        await refreshBrief(d)
      }
    } catch {
      schedulePoll()
    }
  }, 3000)
}

async function refreshBrief(d: WeeklyReportVO): Promise<void> {
  const i = reports.value.findIndex((r) => r.id === d.id)
  const brief: WeeklyReportBrief = {
    id: d.id,
    weekStart: d.weekStart,
    weekEnd: d.weekEnd,
    status: d.status,
    errorMessage: d.errorMessage,
    generatedAt: d.generatedAt,
    updatedAt: d.updatedAt,
  }
  if (i >= 0) reports.value.splice(i, 1, brief)
  else reports.value.unshift(brief)
}

async function selectReport(row: WeeklyReportBrief): Promise<void> {
  if (detail.value?.id === row.id) return
  if (dirty.value) {
    try {
      await ElMessageBox.confirm('当前周报有未保存的修改，切换后将丢失。', '未保存的修改', {
        type: 'warning',
        confirmButtonText: '仍要切换',
      })
    } catch {
      return
    }
  }
  await loadDetail(row.id)
}

async function startGenerate(): Promise<void> {
  if (generating.value) return
  if (dirty.value) {
    try {
      await ElMessageBox.confirm('当前周报有未保存的修改，重新生成会丢失这些修改。', '未保存的修改', {
        type: 'warning',
        confirmButtonText: '继续生成',
      })
    } catch {
      return
    }
  }
  const weekStart = currentWeek.value
  const existing = reports.value.find((r) => r.weekStart === weekStart)
  if (existing) {
    try {
      await ElMessageBox.confirm(
        `「${weekStart} ~ ${weekEndOf(weekStart)}」已有周报，重新生成会覆盖当前正文。`,
        '重新生成',
        { type: 'warning', confirmButtonText: '覆盖生成' },
      )
    } catch {
      return
    }
  }
  generating.value = true
  try {
    const d = await generateWeeklyReport({ weekStart, overwrite: Boolean(existing) })
    applyDetail(d)
    if (d.status !== 'GENERATING') await refreshBrief(d)
    else {
      const brief: WeeklyReportBrief = {
        id: d.id,
        weekStart: d.weekStart,
        weekEnd: d.weekEnd,
        status: d.status,
      }
      const i = reports.value.findIndex((r) => r.id === d.id)
      if (i >= 0) reports.value.splice(i, 1, brief)
      else reports.value.unshift(brief)
      ElMessage.info('已开始生成，请稍候…')
    }
  } catch (e) {
    ElMessage.error((e as Error).message || '生成失败')
  } finally {
    generating.value = false
  }
}

async function saveReport(): Promise<void> {
  const d = detail.value
  if (!d) return
  if (d.status === 'GENERATING') {
    ElMessage.warning('正在生成中，暂不能保存')
    return
  }
  saving.value = true
  try {
    const updated = await updateWeeklyReport(d.id, {
      contentHtml: content.value,
      version: d.version,
    })
    applyDetail(updated)
    await refreshBrief(updated)
    ElMessage.success('已保存定稿')
  } catch (e) {
    ElMessage.error((e as Error).message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function removeReport(): Promise<void> {
  const d = detail.value
  if (!d) return
  try {
    await ElMessageBox.confirm('删除后不可恢复，确认删除这份周报？', '删除周报', {
      type: 'warning',
      confirmButtonText: '删除',
    })
  } catch {
    return
  }
  try {
    await deleteWeeklyReport(d.id)
    reports.value = reports.value.filter((r) => r.id !== d.id)
    detail.value = null
    content.value = ''
    lastSavedHtml.value = ''
    ElMessage.success('已删除')
  } catch (e) {
    ElMessage.error((e as Error).message || '删除失败')
  }
}

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
  await loadList()
  if (reports.value.length) {
    await loadDetail(reports.value[0].id)
  }
})

onBeforeUnmount(() => {
  if (pollTimer) clearTimeout(pollTimer)
  pollTimer = null
})
</script>

<template>
  <div class="weekly">
    <aside class="weekly__side">
      <div class="weekly__side-head">
        <h2 class="weekly__side-title">我的周报</h2>
        <el-button text size="small" :icon="Setting" data-test="weekly-preference" @click="openPreference">
          个人规则
        </el-button>
      </div>

      <div v-loading="loadingList" class="weekly__list">
        <button
          v-for="row in reports"
          :key="row.id"
          type="button"
          class="weekly__item"
          :class="{ 'is-active': detail?.id === row.id }"
          @click="selectReport(row)"
        >
          <div class="weekly__item-top">
            <span class="weekly__item-week">{{ row.weekStart }} ~ {{ row.weekEnd }}</span>
            <el-tag size="small" :type="statusMeta[row.status].type" effect="plain">
              {{ statusMeta[row.status].label }}
            </el-tag>
          </div>
          <p class="weekly__item-summary">
            {{ row.summary || row.errorMessage || '暂无内容' }}
          </p>
        </button>
        <p v-if="!loadingList && !reports.length" class="weekly__empty">还没有周报，选择日期后生成第一份。</p>
      </div>
    </aside>

    <section class="weekly__main">
      <header class="weekly__head">
        <div class="weekly__week">
          <el-date-picker
            v-model="weekDate"
            type="date"
            size="small"
            :clearable="false"
            placeholder="选择周内任意一天"
          />
          <span class="weekly__range">{{ currentWeek }} ~ {{ weekEndOf(currentWeek) }}</span>
          <el-tag v-if="statusInfo" size="small" :type="statusInfo.type" effect="plain">
            {{ statusInfo.label }}
          </el-tag>
        </div>
        <div class="weekly__ops">
          <el-button
            size="small"
            type="primary"
            :icon="MagicStick"
            :loading="generating"
            data-test="weekly-generate"
            @click="startGenerate"
          >
            {{ detail && detail.weekStart === currentWeek ? '重新生成' : 'AI 生成' }}
          </el-button>
          <el-button
            size="small"
            :disabled="!detail || detail.status === 'GENERATING' || (!dirty && detail.status === 'DONE')"
            :loading="saving"
            data-test="weekly-save"
            @click="saveReport"
          >
            保存定稿
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

      <div v-if="detail?.modelName" class="weekly__meta">
        生成模型：{{ detail.modelName }}
        <span v-if="detail.sourceNoteIds?.length"> · 引用 {{ detail.sourceNoteIds.length }} 条日志</span>
        <span v-if="detail.generatedAt"> · {{ new Date(detail.generatedAt).toLocaleString('zh-CN') }}</span>
      </div>

      <div v-loading="loadingDetail" class="weekly__editor">
        <WeeklyReportEditor
          v-if="detail"
          v-model="content"
          :readonly="detail.status === 'GENERATING'"
        />
        <el-empty v-else description="选择日期后点击「AI 生成」，或从左侧打开已有周报">
          <el-button type="primary" :icon="MagicStick" :loading="generating" @click="startGenerate">
            AI 生成本周周报
          </el-button>
        </el-empty>
      </div>
    </section>

    <el-dialog v-model="prefVisible" title="周报个人规则" width="520px">
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
      <template #footer>
        <el-button @click="prefVisible = false">取消</el-button>
        <el-button type="primary" :loading="prefSaving" @click="savePreference">保存</el-button>
      </template>
    </el-dialog>
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
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
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
.weekly__week {
  display: flex;
  align-items: center;
  gap: 10px;
}
.weekly__range {
  font-size: 13px;
  color: var(--bb-text-secondary);
}
.weekly__ops {
  display: flex;
  align-items: center;
  gap: 8px;
}
.weekly__alert {
  margin-bottom: 10px;
}
.weekly__meta {
  margin-bottom: 8px;
  font-size: 12px;
  color: var(--bb-text-muted);
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
