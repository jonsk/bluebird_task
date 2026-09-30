<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Bell, Calendar, Flag, FolderOpened, Plus, PriceTag, RefreshRight } from '@element-plus/icons-vue'
import { createTask, type CycleRule, type TaskUpsertPayload } from '@/api/task'
import BbUserPicker from './BbUserPicker.vue'
import { PRIORITY, PRIORITY_LABEL, type Priority } from '@/utils/constants'
import { toOffsetIso } from '@/utils/date'
import type { CategoryNode } from '@/api/category'
import type { TagVO } from '@/api/tag'
import type { UserVO } from '@/api/user'

/**
 * 任务编辑器（旧 `.t-b-input-box`，行高 52）：列表顶部内联的「添加任务」输入框，
 * 聚焦后展开配置行 +「添加」。
 *
 * 交互对齐旧 `addTaskBlock.vue`（item 1）：配置行一律为**「图标（+ 已选值文字）→ 点击弹出选择层」**，
 * **不使用可见的下拉框**；截止/提醒/重复为日期/提醒/周期，优先级/指派/标签/分类为各自的选择层。
 */
const props = withDefaults(
  defineProps<{
    categories?: CategoryNode[]
    tags?: TagVO[]
    users?: UserVO[]
    defaultCategoryId?: number | null
  }>(),
  { categories: () => [], tags: () => [], users: () => [], defaultCategoryId: null },
)

const emit = defineEmits<{ (e: 'saved'): void }>()

const expanded = ref(false)
const submitting = ref(false)

const form = reactive({
  title: '',
  dueAt: '',
  remindAt: '',
  freq: '' as '' | 'DAILY' | 'WEEKLY' | 'MONTHLY',
  priority: 'MEDIUM' as Priority,
  categoryId: null as number | null,
  assigneeIds: [] as number[],
  tagIds: [] as number[],
})

const canSubmit = computed(() => form.title.trim().length > 0 && !submitting.value)

// ── 优先级/指派/标签/分类：只保留图标，点图标弹出选择层（无下拉框） ──
// 旧 addTaskBlock.vue 的配置行即「图标 + 已选值文字」，没有 el-select 框（用户反馈 #3）。
/** 指派：@ 图标打开人员选择弹窗（左机构树 + 右人员表 + 搜索，见 BbUserPicker）。 */
const pickerOpen = ref(false)
const pickedUsers = ref<UserVO[]>([])

/** 单选类弹层（优先级/分类）选中后需主动关闭：菜单项是自定义 div，不适用 el-dropdown 的 hide-on-click。 */
const priorityDd = ref<{ handleClose: () => void }>()
const categoryDd = ref<{ handleClose: () => void }>()

function pickPriority(p: Priority): void {
  form.priority = p
  priorityDd.value?.handleClose()
}
function pickCategory(id: number | null): void {
  form.categoryId = id
  categoryDd.value?.handleClose()
}

/** 指派：@ 图标 → 打开人员选择弹窗；确认后回填。 */
function openUserPicker(): void {
  pickerOpen.value = true
}
function onUsersConfirm(users: UserVO[]): void {
  pickedUsers.value = users
  form.assigneeIds = users.map((u) => Number(u.id))
}

const selectedTags = computed(() => props.tags.filter((t) => form.tagIds.includes(Number(t.id))))
const assigneeLabel = computed(() => pickedUsers.value.map((u) => u.name ?? '').join('、'))
const tagLabel = computed(() => selectedTags.value.map((t) => t.name ?? '').join('、'))

/** 分类树按层级拍平：弹层内做缩进列表，同时用于展示已选分类名。 */
const flatCategories = computed(() => {
  const out: Array<{ id: number; name: string; depth: number }> = []
  const walk = (nodes: CategoryNode[], depth: number): void => {
    for (const n of nodes) {
      out.push({ id: Number(n.id), name: n.name ?? '', depth })
      if (n.children?.length) walk(n.children, depth + 1)
    }
  }
  walk(props.categories ?? [], 0)
  return out
})
const categoryLabel = computed(() => flatCategories.value.find((c) => c.id === form.categoryId)?.name ?? '')

function toggleTag(id: number): void {
  const i = form.tagIds.indexOf(id)
  if (i >= 0) form.tagIds.splice(i, 1)
  else form.tagIds.push(id)
}

// ── 截止/提醒/重复：点图标弹层（旧 dropdownSetDate/dropdownSetTips/dropdownSetEach）──
/** 弹层内是否切到「日历/自定义」面板。 */
const dueShowCalendar = ref(false)
const remindShowCalendar = ref(false)
const eachShowCustom = ref(false)
/** 弹层内日历选中的日期 / 时间。 */
const calDate = ref(new Date())
const calTime = ref('')
/** 重复自定义：周期数 + 单位。 */
const eachValue = ref(1)
const eachUnit = ref('0')
/** 重复自定义：选中的星期（单位=周时）。 */
const eachWeeks = ref<number[]>([])

const WEEK_TEXT = ['日', '一', '二', '三', '四', '五', '六']
const WEEK_VALUE = [1, 2, 3, 4, 5, 6, 0]
const eachUnits = [
  { label: '天', value: '0' },
  { label: '周', value: '1' },
  { label: '月', value: '2' },
]

function todayStr(): string {
  return toOffsetIso(new Date()).slice(0, 10)
}
function daysFromNow(n: number): string {
  const d = new Date()
  d.setDate(d.getDate() + n)
  return toOffsetIso(d).slice(0, 10)
}
/** 当前或下周一。 */
function nextMonday(): string {
  const d = new Date()
  const day = d.getDay()
  const add = day === 1 ? 7 : (8 - day) % 7
  d.setDate(d.getDate() + add)
  return toOffsetIso(d).slice(0, 10)
}

/** 截止时间展示文字（旧 dropdownSetDateText）。 */
const dueLabel = computed(() => {
  if (!form.dueAt) return ''
  const s = form.dueAt.slice(0, 16).replace('T', ' ')
  if (form.dueAt.slice(0, 10) === todayStr()) return `今天 ${s.slice(11)}`
  if (form.dueAt.slice(0, 10) === daysFromNow(1)) return `明天 ${s.slice(11)}`
  return s
})

/** 提醒展示文字（旧 dropdownSetTipsText）。 */
const remindLabel = computed(() => {
  if (!form.remindAt) return ''
  const s = form.remindAt.slice(0, 16).replace('T', ' ')
  if (form.remindAt.slice(0, 10) === todayStr()) return `今天 ${s.slice(11)}`
  if (form.remindAt.slice(0, 10) === daysFromNow(1)) return `明天 ${s.slice(11)}`
  return s
})

/** 重复展示文字（旧 dropdownSetEachText）。 */
const eachLabel = computed(() => {
  switch (form.freq) {
    case 'DAILY':
      return '每天'
    case 'WEEKLY':
      return '每周'
    case 'MONTHLY':
      return '每月'
    default:
      return ''
  }
})

function setDue(v: string): void {
  form.dueAt = v
}
function setRemind(v: string): void {
  form.remindAt = v
}

function confirmDueCalendar(): void {
  const d = toOffsetIso(calDate.value).slice(0, 10)
  setDue(`${d}T${calTime.value || '08:00'}`)
  dueShowCalendar.value = false
}
function confirmRemindCalendar(): void {
  const d = toOffsetIso(calDate.value).slice(0, 10)
  setRemind(`${d}T${calTime.value || '08:00'}`)
  remindShowCalendar.value = false
}
function confirmEach(): void {
  if (eachUnit.value === '1') {
    const weeks = [...eachWeeks.value].sort()
    if (!weeks.length) return
    form.freq = 'WEEKLY'
  } else if (eachUnit.value === '2') {
    form.freq = 'MONTHLY'
  } else {
    form.freq = 'DAILY'
  }
  eachShowCustom.value = false
}
function resetEachPopup(): void {
  eachValue.value = 1
  eachUnit.value = '0'
  eachWeeks.value = []
  eachShowCustom.value = false
}
function toggleWeek(v: number): void {
  const i = eachWeeks.value.indexOf(v)
  if (i >= 0) eachWeeks.value.splice(i, 1)
  else eachWeeks.value.push(v)
}

function reset(): void {
  form.title = ''
  form.dueAt = ''
  form.remindAt = ''
  form.freq = ''
  form.priority = 'MEDIUM'
  form.categoryId = props.defaultCategoryId
  form.assigneeIds = []
  form.tagIds = []
  pickedUsers.value = []
  pickerOpen.value = false
  dueShowCalendar.value = false
  remindShowCalendar.value = false
  eachShowCustom.value = false
}

async function onSubmit(): Promise<void> {
  if (!canSubmit.value) return
  submitting.value = true
  const payload: TaskUpsertPayload = {
    title: form.title.trim(),
    content: null,
    priority: form.priority,
    dueAt: form.dueAt ? toOffsetIso(form.dueAt) : null,
    remindAt: form.remindAt ? toOffsetIso(form.remindAt) : null,
    categoryId: form.categoryId,
    assigneeIds: form.assigneeIds,
    ccIds: [],
    tagIds: form.tagIds,
    cycleRule: form.freq
      ? ({
          freq: form.freq,
          interval: 1,
          dtstart: form.dueAt ? toOffsetIso(form.dueAt) : toOffsetIso(new Date()),
          until: null,
          tz: 'Asia/Shanghai',
          count: null,
          byDay: null,
        } satisfies CycleRule)
      : null,
  }
  try {
    await createTask(payload)
    ElMessage.success('已创建')
    reset()
    expanded.value = false
    emit('saved')
  } catch (e) {
    ElMessage.error((e as Error)?.message || '创建失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="t-b-input-box" :class="{ 'is-expanded': expanded }">
    <div class="t-b-i-box-div">
      <el-icon class="t-b-i-b-d-icon"><Plus /></el-icon>
      <input
        v-model="form.title"
        class="t-b-i-b-d-input"
        type="text"
        placeholder="添加任务"
        @focus="expanded = true"
        @keyup.enter="onSubmit"
      />
    </div>

    <div v-show="expanded" class="t-b-i-box-config">
      <div class="t-b-i-b-c-left-div">
        <!-- 截止：点图标弹出（旧 dropdownSetDate） -->
        <div class="t-b-i-b-c-l-d-item" :class="{ 'is-set': form.dueAt }">
          <el-dropdown trigger="click" popper-class="bb-cfg-popper" :hide-on-click="false" @visible-change="(v: boolean) => { if (!v) dueShowCalendar = false }">
            <div class="bb-cfg-trigger" data-test="composer-due">
              <el-icon class="t-b-i-b-c-l-d-item-icon"><Calendar /></el-icon>
              <span v-if="form.dueAt" class="bb-cfg-text">{{ dueLabel }}</span>
            </div>
            <template #dropdown>
              <div v-show="!dueShowCalendar" class="bb-cfg-menu">
                <div class="bb-cfg-menu__item" @click="setDue(`${daysFromNow(0)}T23:59`)">今天</div>
                <div class="bb-cfg-menu__item" @click="setDue(`${daysFromNow(1)}T23:59`)">明天</div>
                <div class="bb-cfg-menu__item" @click="setDue(`${nextMonday()}T23:59`)">下周</div>
                <div class="bb-cfg-menu__item bb-cfg-menu__item--divider" @click="dueShowCalendar = true">选择日期和时间</div>
                <div v-if="form.dueAt" class="bb-cfg-menu__item bb-cfg-menu__item--danger" @click="setDue('')">删除截止日期</div>
              </div>
              <div v-show="dueShowCalendar" class="bb-cfg-calendar">
                <el-calendar v-model="calDate" class="bb-cfg-calendar__inner" />
                <el-time-select v-model="calTime" start="01:00" step="00:30" end="23:30" placeholder="时间" class="bb-cfg-calendar__time" />
                <el-button type="primary" size="small" class="bb-cfg-calendar__save" @click="confirmDueCalendar">保存</el-button>
              </div>
            </template>
          </el-dropdown>
        </div>

        <!-- 提醒：点图标弹出（旧 dropdownSetTips） -->
        <div class="t-b-i-b-c-l-d-item" :class="{ 'is-set': form.remindAt }">
          <el-dropdown trigger="click" popper-class="bb-cfg-popper" :hide-on-click="false" @visible-change="(v: boolean) => { if (!v) remindShowCalendar = false }">
            <div class="bb-cfg-trigger" data-test="composer-remind">
              <el-icon class="t-b-i-b-c-l-d-item-icon"><Bell /></el-icon>
              <span v-if="form.remindAt" class="bb-cfg-text">{{ remindLabel }}</span>
            </div>
            <template #dropdown>
              <div v-show="!remindShowCalendar" class="bb-cfg-menu">
                <div class="bb-cfg-menu__item" @click="setRemind(`${daysFromNow(0)}T23:59`)">今天</div>
                <div class="bb-cfg-menu__item" @click="setRemind(`${daysFromNow(1)}T09:00`)">明天</div>
                <div class="bb-cfg-menu__item" @click="setRemind(`${nextMonday()}T09:00`)">下周</div>
                <div class="bb-cfg-menu__item bb-cfg-menu__item--divider" @click="remindShowCalendar = true">选择日期和时间</div>
                <div v-if="form.remindAt" class="bb-cfg-menu__item bb-cfg-menu__item--danger" @click="setRemind('')">删除提醒</div>
              </div>
              <div v-show="remindShowCalendar" class="bb-cfg-calendar">
                <el-calendar v-model="calDate" class="bb-cfg-calendar__inner" />
                <el-time-select v-model="calTime" start="01:00" step="00:30" end="23:30" placeholder="时间" class="bb-cfg-calendar__time" />
                <el-button type="primary" size="small" class="bb-cfg-calendar__save" @click="confirmRemindCalendar">保存</el-button>
              </div>
            </template>
          </el-dropdown>
        </div>

        <!-- 重复：点图标弹出（旧 dropdownSetEach） -->
        <div class="t-b-i-b-c-l-d-item" :class="{ 'is-set': form.freq }">
          <el-dropdown trigger="click" popper-class="bb-cfg-popper" :hide-on-click="false" @visible-change="(v: boolean) => { if (!v) resetEachPopup() }">
            <div class="bb-cfg-trigger" data-test="composer-repeat">
              <el-icon class="t-b-i-b-c-l-d-item-icon"><RefreshRight /></el-icon>
              <span v-if="form.freq" class="bb-cfg-text">{{ eachLabel }}</span>
            </div>
            <template #dropdown>
              <div v-show="!eachShowCustom" class="bb-cfg-menu">
                <div class="bb-cfg-menu__item" @click="form.freq = 'DAILY'">每天</div>
                <div class="bb-cfg-menu__item" @click="form.freq = 'WEEKLY'">每周</div>
                <div class="bb-cfg-menu__item" @click="form.freq = 'MONTHLY'">每月</div>
                <div class="bb-cfg-menu__item bb-cfg-menu__item--divider" @click="eachShowCustom = true">自定义</div>
                <div v-if="form.freq" class="bb-cfg-menu__item bb-cfg-menu__item--danger" @click="form.freq = ''">从不重复</div>
              </div>
              <div v-show="eachShowCustom" class="bb-cfg-custom">
                <div class="bb-cfg-custom__head">
                  <el-input-number v-model="eachValue" :min="1" size="small" class="bb-cfg-custom__num" />
                  <el-select v-model="eachUnit" size="small" class="bb-cfg-custom__unit">
                    <el-option v-for="u in eachUnits" :key="u.value" :label="u.label" :value="u.value" />
                  </el-select>
                </div>
                <div v-if="eachUnit === '1'" class="bb-cfg-custom__weeks">
                  <span
                    v-for="(v, i) in WEEK_VALUE"
                    :key="v"
                    class="bb-cfg-custom__week"
                    :class="{ 'is-active': eachWeeks.includes(v) }"
                    @click="toggleWeek(v)"
                  >{{ WEEK_TEXT[i] }}</span>
                </div>
                <el-button type="primary" size="small" class="bb-cfg-custom__save" @click="confirmEach">保存</el-button>
              </div>
            </template>
          </el-dropdown>
        </div>

        <!-- 优先级：点图标弹出（取消下拉框） -->
        <div class="t-b-i-b-c-l-d-item" :class="{ 'is-set': form.priority !== 'MEDIUM' }">
          <el-dropdown ref="priorityDd" trigger="click" popper-class="bb-cfg-popper">
            <div class="bb-cfg-trigger" data-test="composer-priority">
              <el-icon class="t-b-i-b-c-l-d-item-icon"><Flag /></el-icon>
              <span class="bb-cfg-text">{{ PRIORITY_LABEL[form.priority] }}</span>
            </div>
            <template #dropdown>
              <div class="bb-cfg-menu">
                <div
                  v-for="p in PRIORITY"
                  :key="p"
                  class="bb-cfg-menu__item"
                  :class="{ 'is-active': form.priority === p }"
                  @click="pickPriority(p)"
                >
                  {{ PRIORITY_LABEL[p] }}
                </div>
              </div>
            </template>
          </el-dropdown>
        </div>

        <!-- 指派：@ 图标 → 打开「左机构树 + 右人员表 + 搜索」人员选择弹窗（取消下拉框） -->
        <div class="t-b-i-b-c-l-d-item" :class="{ 'is-set': form.assigneeIds.length }">
          <div class="bb-cfg-trigger" data-test="composer-assignee" title="选择人员" @click="openUserPicker">
            <span class="assigning">@</span>
            <span v-if="assigneeLabel" class="bb-cfg-text">{{ assigneeLabel }}</span>
          </div>
        </div>

        <!-- 标签：点图标弹出勾选（取消下拉框） -->
        <div class="t-b-i-b-c-l-d-item" :class="{ 'is-set': form.tagIds.length }">
          <el-dropdown trigger="click" popper-class="bb-cfg-popper" :hide-on-click="false">
            <div class="bb-cfg-trigger" data-test="composer-tags">
              <el-icon class="t-b-i-b-c-l-d-item-icon"><PriceTag /></el-icon>
              <span v-if="tagLabel" class="bb-cfg-text">{{ tagLabel }}</span>
            </div>
            <template #dropdown>
              <div class="bb-cfg-picker">
                <div class="bb-cfg-picker__list">
                  <div
                    v-for="t in tags"
                    :key="t.id"
                    class="bb-cfg-picker__row"
                    @click="toggleTag(Number(t.id))"
                  >
                    <el-checkbox :model-value="form.tagIds.includes(Number(t.id))" />
                    <span class="bb-cfg-picker__name">{{ t.name }}</span>
                  </div>
                  <div v-if="!tags.length" class="bb-cfg-picker__empty">暂无标签</div>
                </div>
              </div>
            </template>
          </el-dropdown>
        </div>

        <!-- 分类：点图标弹出（取消下拉框） -->
        <div class="t-b-i-b-c-l-d-item" :class="{ 'is-set': form.categoryId != null }">
          <el-dropdown ref="categoryDd" trigger="click" popper-class="bb-cfg-popper">
            <div class="bb-cfg-trigger" data-test="composer-category">
              <el-icon class="t-b-i-b-c-l-d-item-icon"><FolderOpened /></el-icon>
              <span v-if="categoryLabel" class="bb-cfg-text">{{ categoryLabel }}</span>
            </div>
            <template #dropdown>
              <div class="bb-cfg-menu bb-cfg-menu--scroll">
                <div
                  v-for="c in flatCategories"
                  :key="c.id"
                  class="bb-cfg-menu__item"
                  :class="{ 'is-active': form.categoryId === c.id }"
                  :style="{ paddingLeft: `${16 + c.depth * 14}px` }"
                  @click="pickCategory(c.id)"
                >
                  {{ c.name }}
                </div>
                <div v-if="!flatCategories.length" class="bb-cfg-menu__item bb-cfg-menu__item--empty">暂无分类</div>
                <div
                  v-if="form.categoryId != null"
                  class="bb-cfg-menu__item bb-cfg-menu__item--divider bb-cfg-menu__item--danger"
                  @click="pickCategory(null)"
                >
                  清除分类
                </div>
              </div>
            </template>
          </el-dropdown>
        </div>
      </div>

      <div class="t-b-i-b-c-right-div">
        <el-button :disabled="!canSubmit" :loading="submitting" @click="onSubmit"><span>添加</span></el-button>
      </div>
    </div>

    <!-- 人员选择弹窗（左机构树 + 右人员表 + 搜索/分页） -->
    <BbUserPicker v-model="pickerOpen" :picked-users="pickedUsers" @confirm="onUsersConfirm" />
  </div>
</template>

<style scoped>
.t-b-input-box {
  width: 100%;
  margin: 20px 0 10px;
  border-radius: var(--bb-radius-sm);
  overflow: hidden;
  box-shadow: var(--bb-shadow-flat);
}
.t-b-i-box-div {
  display: flex;
  align-items: center;
  width: 100%;
  height: 52px;
  background: var(--bg-primary);
}
.t-b-i-b-d-icon {
  margin: 0 8px 0 16px;
  font-size: 16px;
  color: var(--bb-text-secondary);
}
.t-b-i-b-d-input {
  width: 95%;
  height: 100%;
  border: 0;
  outline: none;
  background: transparent;
  font-size: 14px;
  color: var(--bb-text);
}
.t-b-i-box-config {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: var(--bg-primary);
  border-top: 1px solid var(--bg-separator);
}
.t-b-i-b-c-left-div {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 0;
}
.t-b-i-b-c-l-d-item {
  display: flex;
  align-items: center;
  margin-left: 20px;
}
.t-b-i-b-c-l-d-item-icon {
  margin: 3px 5px 0 0;
  color: var(--bb-text-secondary);
  cursor: pointer;
}
.assigning {
  margin-right: 5px;
  color: var(--bb-text-secondary);
  cursor: pointer;
}
.t-b-i-b-c-right-div {
  flex-shrink: 0;
}
.bb-cfg-trigger {
  display: flex;
  align-items: center;
  cursor: pointer;
}
.bb-cfg-text {
  margin-left: 4px;
  font-size: 13px;
  color: var(--bb-text);
}
.t-b-i-b-c-l-d-item.is-set .bb-cfg-trigger {
  color: var(--bg-accent);
}
.t-b-i-b-c-l-d-item.is-set .bb-cfg-text {
  color: var(--bg-accent);
}
.t-b-i-b-c-l-d-item.is-set .assigning {
  color: var(--bg-accent);
}
</style>

<!-- 弹层内容 teleport 到 body，需全局样式（popper-class） -->
<style>
.bb-cfg-popper {
  min-width: 200px;
}
.bb-cfg-menu {
  padding: 4px 0;
}
.bb-cfg-menu__item {
  padding: 6px 16px;
  font-size: 13px;
  color: #292827;
  cursor: pointer;
  white-space: nowrap;
}
.bb-cfg-menu__item:hover {
  background: var(--el-color-primary-light-9);
}
.bb-cfg-menu__item--divider {
  border-top: 1px solid var(--el-border-color-lighter);
  margin-top: 4px;
  padding-top: 8px;
}
.bb-cfg-menu__item--danger {
  color: var(--el-color-danger);
}
.bb-cfg-menu--scroll {
  max-height: 260px;
  overflow-y: auto;
}
.bb-cfg-menu__item.is-active {
  color: var(--el-color-primary);
  font-weight: 600;
}
.bb-cfg-menu__item--empty {
  color: var(--el-text-color-placeholder);
  cursor: default;
}
.bb-cfg-menu__item--empty:hover {
  background: transparent;
}
/* 指派 / 标签：图标点开的勾选层（替代原可见下拉框） */
.bb-cfg-picker {
  width: 220px;
  padding: 6px 0;
}
.bb-cfg-picker__search {
  width: calc(100% - 16px);
  margin: 0 8px 6px;
}
.bb-cfg-picker__list {
  max-height: 240px;
  overflow-y: auto;
}
.bb-cfg-picker__row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  font-size: 13px;
  cursor: pointer;
}
.bb-cfg-picker__row:hover {
  background: var(--el-color-primary-light-9);
}
/* 整行负责切换，复选框只做展示，避免双击/双触发 */
.bb-cfg-picker__row .el-checkbox {
  pointer-events: none;
}
.bb-cfg-picker__name {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.bb-cfg-picker__empty {
  padding: 8px 12px;
  font-size: 13px;
  color: var(--el-text-color-placeholder);
}
.bb-cfg-calendar {
  width: 280px;
  padding: 8px;
}
.bb-cfg-calendar__inner {
  height: 260px;
}
.bb-cfg-calendar__inner .el-calendar__header {
  padding: 8px 12px 0;
}
.bb-cfg-calendar__inner .el-calendar__body {
  padding: 8px 12px 0;
}
.bb-cfg-calendar__time {
  width: 100%;
  margin-top: 8px;
}
.bb-cfg-calendar__save {
  width: 80%;
  margin: 10px 10% 0;
}
.bb-cfg-custom {
  padding: 8px 12px;
}
.bb-cfg-custom__head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.bb-cfg-custom__num {
  width: 110px;
}
.bb-cfg-custom__unit {
  width: 90px;
}
.bb-cfg-custom__weeks {
  display: flex;
  margin-top: 10px;
  border: 1px solid var(--el-border-color);
}
.bb-cfg-custom__week {
  flex: 1;
  height: 32px;
  line-height: 32px;
  text-align: center;
  font-size: 13px;
  color: #323232;
  cursor: pointer;
}
.bb-cfg-custom__week.is-active {
  background: var(--bg-accent);
  color: #fff;
}
.bb-cfg-custom__save {
  width: 80%;
  margin: 10px 10% 0;
}
</style>
