<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Bell, Calendar, Flag, Plus, RefreshRight, User } from '@element-plus/icons-vue'
import { createTask, type CycleRule, type TaskUpsertPayload } from '@/api/task'
import { PRIORITY, PRIORITY_LABEL, type Priority } from '@/utils/constants'
import { toOffsetIso } from '@/utils/date'
import type { CategoryNode } from '@/api/category'
import type { TagVO } from '@/api/tag'
import type { UserVO } from '@/api/user'

/**
 * 任务编辑器（旧 `.t-b-input-box`，行高 52）：列表顶部内联的「添加任务」输入框，
 * 聚焦后展开配置行（截止/提醒/重复/优先级/指派/标签/分类）+「添加」。
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

function reset(): void {
  form.title = ''
  form.dueAt = ''
  form.remindAt = ''
  form.freq = ''
  form.priority = 'MEDIUM'
  form.categoryId = props.defaultCategoryId
  form.assigneeIds = []
  form.tagIds = []
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
        <div class="t-b-i-b-c-l-d-item">
          <el-icon class="t-b-i-b-c-l-d-item-icon"><Calendar /></el-icon>
          <el-date-picker v-model="form.dueAt" type="datetime" size="small" placeholder="截止时间" class="t-b-i-b-c-field" data-test="composer-due" />
        </div>
        <div class="t-b-i-b-c-l-d-item">
          <el-icon class="t-b-i-b-c-l-d-item-icon"><Bell /></el-icon>
          <el-date-picker v-model="form.remindAt" type="datetime" size="small" placeholder="提醒时间" class="t-b-i-b-c-field" data-test="composer-remind" />
        </div>
        <div class="t-b-i-b-c-l-d-item">
          <el-icon class="t-b-i-b-c-l-d-item-icon"><RefreshRight /></el-icon>
          <el-select v-model="form.freq" size="small" placeholder="重复" clearable class="t-b-i-b-c-field-sm" data-test="composer-repeat">
            <el-option label="每天" value="DAILY" />
            <el-option label="每周" value="WEEKLY" />
            <el-option label="每月" value="MONTHLY" />
          </el-select>
        </div>
        <div class="t-b-i-b-c-l-d-item">
          <el-icon class="t-b-i-b-c-l-d-item-icon"><Flag /></el-icon>
          <el-select v-model="form.priority" size="small" class="t-b-i-b-c-field-sm" data-test="composer-priority">
            <el-option v-for="p in PRIORITY" :key="p" :label="PRIORITY_LABEL[p]" :value="p" />
          </el-select>
        </div>
        <div class="t-b-i-b-c-l-d-item">
          <span class="assigning">@</span>
          <el-select v-model="form.assigneeIds" size="small" multiple collapse-tags filterable placeholder="指派" class="t-b-i-b-c-field" data-test="composer-assignee">
            <el-option v-for="u in users" :key="u.id" :label="u.name" :value="Number(u.id)" />
          </el-select>
        </div>
        <div class="t-b-i-b-c-l-d-item">
          <el-icon class="t-b-i-b-c-l-d-item-icon"><User /></el-icon>
          <el-select v-model="form.tagIds" size="small" multiple collapse-tags filterable placeholder="标签" class="t-b-i-b-c-field" data-test="composer-tags">
            <el-option v-for="t in tags" :key="t.id" :label="t.name" :value="Number(t.id)" />
          </el-select>
        </div>
        <div class="t-b-i-b-c-l-d-item">
          <el-icon class="t-b-i-b-c-l-d-item-icon"><Calendar /></el-icon>
          <el-select v-model="form.categoryId" size="small" clearable placeholder="分类" class="t-b-i-b-c-field" data-test="composer-category">
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="Number(c.id)" />
          </el-select>
        </div>
      </div>

      <div class="t-b-i-b-c-right-div">
        <el-button :disabled="!canSubmit" :loading="submitting" @click="onSubmit"><span>添加</span></el-button>
      </div>
    </div>
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
}
.assigning {
  margin-right: 5px;
  color: var(--bb-text-secondary);
}
.t-b-i-b-c-field {
  width: 170px;
}
.t-b-i-b-c-field-sm {
  width: 100px;
}
.t-b-i-b-c-right-div {
  flex-shrink: 0;
}
</style>
