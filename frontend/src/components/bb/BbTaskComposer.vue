<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createTask, updateTask, type CycleRule, type TaskVO, type TaskUpsertPayload } from '@/api/task'
import { PRIORITY, PRIORITY_LABEL, type Priority } from '@/utils/constants'
import { toOffsetIso } from '@/utils/date'
import type { CategoryNode } from '@/api/category'
import type { TagVO } from '@/api/tag'
import type { UserVO } from '@/api/user'

/**
 * 任务编辑器（03 §5.3.3）：新建/编辑主任务，含周期规则、参与人、标签、分类。
 * 编辑须回传 version（ADR-013）。
 */
const props = withDefaults(
  defineProps<{
    modelValue: boolean
    task?: TaskVO | null
    categories?: CategoryNode[]
    tags?: TagVO[]
    users?: UserVO[]
    defaultCategoryId?: number | null
  }>(),
  { task: null, categories: () => [], tags: () => [], users: () => [], defaultCategoryId: null },
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'saved'): void
}>()

interface FormState {
  title: string
  content: string
  priority: Priority
  dueAt: string
  remindAt: string
  categoryId: number | null
  assigneeIds: number[]
  ccIds: number[]
  tagIds: number[]
  recurring: boolean
  freq: 'DAILY' | 'WEEKLY' | 'MONTHLY'
  interval: number
  until: string
}

const form = reactive<FormState>(emptyForm())

function emptyForm(): FormState {
  return {
    title: '',
    content: '',
    priority: 'MEDIUM',
    dueAt: '',
    remindAt: '',
    categoryId: props.defaultCategoryId,
    assigneeIds: [],
    ccIds: [],
    tagIds: [],
    recurring: false,
    freq: 'DAILY',
    interval: 1,
    until: '',
  }
}

const visible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v),
})

const isEdit = computed(() => Boolean(props.task?.id))
const title = computed(() => (isEdit.value ? '编辑任务' : '新建任务'))

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    if (props.task) {
      const rule = props.task.cycleRule as CycleRule | undefined
      Object.assign(form, {
        title: String(props.task.title ?? ''),
        content: String(props.task.content ?? ''),
        priority: (props.task.priority as Priority) ?? 'MEDIUM',
        dueAt: props.task.dueAt ? toDatetimeLocal(props.task.dueAt) : '',
        remindAt: props.task.remindAt ? toDatetimeLocal(props.task.remindAt) : '',
        categoryId: props.task.category?.id ?? null,
        assigneeIds: (props.task.assignees ?? []).map((a) => Number(a.id)),
        ccIds: (props.task.ccUsers ?? []).map((a) => Number(a.id)),
        tagIds: (props.task.tags ?? []).map((t) => Number(t.id)),
        recurring: Boolean(rule),
        freq: rule?.freq ?? 'DAILY',
        interval: rule?.interval ?? 1,
        until: rule?.until ?? '',
      })
    } else {
      Object.assign(form, emptyForm())
    }
  },
)

/** ISO → el-date-picker 可识别的本地字符串。 */
function toDatetimeLocal(iso: string): string {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  const pad = (n: number): string => (n < 10 ? `0${n}` : String(n))
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:00`
}

async function onSubmit(): Promise<void> {
  if (!form.title.trim()) {
    ElMessage.warning('请填写标题')
    return
  }
  const payload: TaskUpsertPayload = {
    title: form.title.trim(),
    content: form.content || null,
    priority: form.priority,
    dueAt: form.dueAt ? toOffsetIso(form.dueAt) : null,
    remindAt: form.remindAt ? toOffsetIso(form.remindAt) : null,
    categoryId: form.categoryId,
    assigneeIds: form.assigneeIds,
    ccIds: form.ccIds,
    tagIds: form.tagIds,
    cycleRule: form.recurring
      ? {
          freq: form.freq,
          interval: Number(form.interval) || 1,
          dtstart: form.dueAt ? toOffsetIso(form.dueAt) : toOffsetIso(new Date()),
          until: form.until || null,
          tz: 'Asia/Shanghai',
          count: null,
          byDay: null,
        }
      : null,
  }
  try {
    if (isEdit.value && props.task?.id != null) {
      await updateTask(Number(props.task.id), { ...payload, version: Number(props.task.version ?? 0) })
      ElMessage.success('已保存')
    } else {
      await createTask(payload)
      ElMessage.success('已创建')
    }
    visible.value = false
    emit('saved')
  } catch (e) {
    ElMessage.error((e as Error).message || '保存失败')
  }
}
</script>

<template>
  <el-dialog v-model="visible" :title="title" width="620px" append-to-body>
    <el-form label-width="72px" label-position="left">
      <el-form-item label="标题" required>
        <el-input v-model="form.title" maxlength="200" show-word-limit placeholder="任务标题" />
      </el-form-item>
      <el-form-item label="描述">
        <el-input v-model="form.content" type="textarea" :rows="3" placeholder="补充说明（可选）" />
      </el-form-item>
      <el-form-item label="优先级">
        <el-select v-model="form.priority" style="width: 160px">
          <el-option v-for="p in PRIORITY" :key="p" :label="PRIORITY_LABEL[p]" :value="p" />
        </el-select>
      </el-form-item>
      <el-form-item label="截止">
        <el-date-picker v-model="form.dueAt" type="datetime" placeholder="截止时间" style="width: 220px" />
      </el-form-item>
      <el-form-item label="提醒">
        <el-date-picker v-model="form.remindAt" type="datetime" placeholder="提醒时间（暂不启用）" style="width: 220px" />
      </el-form-item>
      <el-form-item label="周期">
        <el-switch v-model="form.recurring" />
        <template v-if="form.recurring">
          <el-select v-model="form.freq" style="width: 120px; margin-left: 8px">
            <el-option label="每天" value="DAILY" />
            <el-option label="每周" value="WEEKLY" />
            <el-option label="每月" value="MONTHLY" />
          </el-select>
          <span class="bb-composer__gap">每</span>
          <el-input-number v-model="form.interval" :min="1" :max="30" controls-position="right" style="width: 100px" />
          <el-date-picker v-model="form.until" type="date" placeholder="截止日期（可选）" style="width: 180px; margin-left: 8px" />
        </template>
      </el-form-item>
      <el-form-item label="分类">
        <el-select v-model="form.categoryId" clearable placeholder="选择分类" style="width: 220px">
          <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="Number(c.id)" />
        </el-select>
      </el-form-item>
      <el-form-item label="负责人">
        <el-select v-model="form.assigneeIds" multiple filterable placeholder="指派给" style="width: 100%">
          <el-option v-for="u in users" :key="u.id" :label="u.name" :value="Number(u.id)" />
        </el-select>
      </el-form-item>
      <el-form-item label="@知会">
        <el-select v-model="form.ccIds" multiple filterable placeholder="知会（只读）" style="width: 100%">
          <el-option v-for="u in users" :key="u.id" :label="u.name" :value="Number(u.id)" />
        </el-select>
      </el-form-item>
      <el-form-item label="标签">
        <el-select v-model="form.tagIds" multiple filterable placeholder="选择标签" style="width: 100%">
          <el-option v-for="t in tags" :key="t.id" :label="t.name" :value="Number(t.id)" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" @click="onSubmit">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.bb-composer__gap {
  margin: 0 6px;
  color: var(--bb-color-muted);
}
</style>
