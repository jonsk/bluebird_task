<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowDown, ArrowRight, Bell, Calendar, Delete, Edit, Flag, FolderAdd, RefreshRight, Star, StarFilled } from '@element-plus/icons-vue'
import { subtasks as fetchSubtasks, type TaskVO } from '@/api/task'
import { useMenuStore } from '@/stores/menu'
import { PRIORITY_LABEL, type Priority } from '@/utils/constants'
import { formatCreatedDate, formatDateTime, isOverdue } from '@/utils/date'

/**
 * 任务卡片（旧 `.task-card` / `.tast-item`）：62px 白底行 + Fluent 阴影（无圆角）。
 * 信息行顺序与旧站一致：创建于 / 子任务进度 / 截止 / 提醒 / 优先级 / 周期 / 指派。
 * 写入口（完成/编辑）仅对 owner/assignee/ADMIN 显示（CC 只读，02 §4.7）。
 */
const props = withDefaults(
  defineProps<{
    task: TaskVO
    collected?: boolean
    writable?: boolean
    active?: boolean
  }>(),
  { collected: false, writable: true, active: false },
)

const emit = defineEmits<{
  (e: 'toggle', task: TaskVO): void
  (e: 'collect', task: TaskVO): void
  (e: 'open', task: TaskVO): void
  (e: 'edit', task: TaskVO): void
  (e: 'remove', task: TaskVO): void
  (e: 'moved'): void
}>()

const menuStore = useMenuStore()

/** 添加到自定义任务栏（旧卡片上的 el-dropdown）。 */
async function onMoveTo(menuId: number): Promise<void> {
  try {
    await menuStore.addTask(menuId, Number(props.task.id))
    ElMessage.success('已添加到自定义任务栏')
    emit('moved')
  } catch (e) {
    ElMessage.error((e as Error)?.message || '添加失败')
  }
}

const completed = computed(() => Boolean(props.task.completed))
const overdue = computed(() => isOverdue(props.task.dueAt, completed.value))
const assignees = computed(() => props.task.assignees ?? [])
const subtaskTotal = computed(() => Number(props.task.subtaskTotal ?? 0))
const subtaskCompleted = computed(() => Number(props.task.subtaskCompleted ?? 0))
const priority = computed<Priority>(() => (props.task.priority as Priority) ?? 'MEDIUM')
const showPriority = computed(() => priority.value !== 'MEDIUM' && priority.value !== 'LOW')
const urgentColor = computed(() => (priority.value === 'URGENT' ? '#e5484d' : '#e6a23c'))

const expanded = ref(false)
const children = ref<TaskVO[]>([])
const loadingChildren = ref(false)

/** 卡片空白处 → 打开详情面板；操作区/下拉/展开区不触发。 */
function onCardClick(event: MouseEvent): void {
  const target = event.target as HTMLElement | null
  if (target?.closest('.el-dropdown, .el-checkbox, .bb-task-card__op, .bb-task-card__expander')) return
  emit('open', props.task)
}

async function toggleExpand(): Promise<void> {
  expanded.value = !expanded.value
  if (!expanded.value || children.value.length) return
  loadingChildren.value = true
  try {
    const data = await fetchSubtasks(Number(props.task.id))
    children.value = data.list
  } catch (e) {
    ElMessage.error((e as Error)?.message || '加载子任务失败')
  } finally {
    loadingChildren.value = false
  }
}
</script>

<template>
  <div
    class="bb-task-card"
    :class="{ 'is-completed': completed, 'is-active': active }"
    @click="onCardClick"
  >
    <div class="bb-task-card__row">
      <div class="bb-task-card__main">
        <div v-if="subtaskTotal > 0" class="bb-task-card__expander" @click.stop="toggleExpand">
          <el-icon :size="20"><ArrowDown v-if="expanded" /><ArrowRight v-else /></el-icon>
        </div>

        <div class="bb-task-card__check" @click.stop>
          <el-checkbox :model-value="completed" :disabled="!writable" @change="emit('toggle', task)" />
        </div>

        <div class="bb-task-card__body">
          <div class="bb-task-card__title" :class="{ 'is-done': completed }" :title="task.title">
            {{ task.title }}
          </div>
          <div class="bb-task-card__meta task_details">
            <span class="span-generated-on">创建于 {{ formatCreatedDate(task.createdAt) }}</span>
            <span class="span-step-total">
              {{ subtaskTotal ? `${subtaskCompleted} / ${subtaskTotal}` : ' / ' }}
            </span>
            <template v-if="task.dueAt">
              <el-icon><Calendar /></el-icon>
              <span class="span-w" :class="{ 'task-date-overdue': overdue }">{{ formatDateTime(task.dueAt) }}</span>
            </template>
            <template v-if="task.remindAt">
              <span class="dian-icon" />
              <el-icon><Bell /></el-icon>
              <span class="span-w">{{ formatDateTime(task.remindAt) }}</span>
            </template>
            <template v-if="showPriority">
              <span class="dian-icon" />
              <el-icon :style="{ color: urgentColor }"><Flag /></el-icon>
              <span class="span-w lone-w">{{ PRIORITY_LABEL[priority] }}</span>
            </template>
            <template v-if="task.cycleRule">
              <span class="dian-icon" />
              <el-icon data-test="cycle"><RefreshRight /></el-icon>
            </template>
            <template v-if="assignees.length">
              <span class="dian-icon" />
              <span class="bb-task-card__at">@</span>
              <span class="span-w lone-w">{{ assignees.map((a) => a.name).join('、') }}</span>
            </template>
          </div>
        </div>
      </div>

      <div class="bb-task-card__side">
        <span v-if="task.category" class="bb-task-card__cat">{{ task.category.name }}</span>
        <span class="bb-task-card__op" :title="collected ? '取消收藏' : '收藏'" @click.stop="emit('collect', task)">
          <el-icon :size="20"><StarFilled v-if="collected" /><Star v-else /></el-icon>
        </span>
        <span v-if="writable" class="bb-task-card__op" title="编辑" @click.stop="emit('edit', task)">
          <el-icon :size="20"><Edit /></el-icon>
        </span>
        <el-dropdown trigger="click" class="bb-task-card__dropdown" @command="onMoveTo">
          <span class="bb-task-card__op" title="添加到自定义任务栏" data-test="move-menu"><el-icon :size="20"><FolderAdd /></el-icon></span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-for="m in menuStore.menus" :key="m.id" :command="Number(m.id)">{{ m.name }}</el-dropdown-item>
              <el-dropdown-item v-if="!menuStore.menus.length" disabled>暂无自定义任务栏</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <span v-if="writable" class="bb-task-card__op" title="删除" @click.stop="emit('remove', task)">
          <el-icon :size="20"><Delete /></el-icon>
        </span>
      </div>
    </div>

    <div v-if="expanded" v-loading="loadingChildren" class="bb-task-card__children">
      <div v-for="sub in children" :key="sub.id" class="bb-task-card__child" @click.stop="emit('open', sub)">
        <el-checkbox :model-value="Boolean(sub.completed)" :disabled="!writable" @change="emit('toggle', sub)" />
        <span :class="{ 'is-done': sub.completed }">{{ sub.title }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.bb-task-card {
  margin-bottom: 10px;
}
.bb-task-card__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: var(--bb-card-height);
  padding: 8px 12px;
  box-sizing: border-box;
  background: var(--bg-primary);
  box-shadow: var(--bb-shadow-flat);
  cursor: pointer;
}
.bb-task-card.is-active .bb-task-card__row {
  box-shadow: inset 0 0 0 1px var(--bg-accent), var(--bb-shadow-flat);
}
.bb-task-card__main {
  display: flex;
  align-items: center;
  width: calc(100% - 100px);
  min-width: 0;
}
.bb-task-card__expander {
  display: flex;
  align-items: center;
  cursor: pointer;
  color: var(--bb-text-secondary);
}
.bb-task-card__check {
  margin: 0 8px;
}
.bb-task-card__body {
  width: 100%;
  min-width: 0;
  padding: 0 8px;
}
.bb-task-card__title {
  font-size: 16px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.bb-task-card__title.is-done {
  color: #64748b;
  text-decoration: line-through;
}
.bb-task-card__meta {
  display: flex;
  align-items: center;
  font-size: 15px;
  color: var(--bb-text-meta);
  white-space: nowrap;
  overflow: hidden;
}
.span-generated-on {
  width: 120px;
  flex: 0 0 120px;
}
.span-step-total {
  margin-right: 8px;
}
.span-w {
  margin-left: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
}
.task-date-overdue {
  color: var(--bb-color-overdue);
}
.lone-w {
  max-width: 200px;
}
.dian-icon {
  display: inline-block;
  width: 3px;
  height: 3px;
  margin: 0 10px;
  background: var(--bb-text-muted);
  border-radius: 50%;
}
.bb-task-card__at {
  margin: 0 4px;
}
.bb-task-card__side {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}
.bb-task-card__cat {
  margin-right: 10px;
  font-size: 12px;
  color: var(--bb-text-muted);
}
.bb-task-card__op {
  display: inline-flex;
  align-items: center;
  margin-right: 10px;
  color: var(--bb-text-secondary);
  cursor: pointer;
}
.bb-task-card__op:hover {
  color: var(--bg-accent);
}
.bb-task-card__dropdown {
  display: inline-flex;
  align-items: center;
}
.bb-task-card__children {
  padding: 4px 12px 8px 60px;
  background: var(--bg-primary);
  box-shadow: var(--bb-shadow-flat);
}
.bb-task-card__child {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 40px;
  font-size: 14px;
  color: var(--bb-text-secondary);
  cursor: pointer;
}
.bb-task-card__child .is-done {
  color: #94a3b8;
  text-decoration: line-through;
}
</style>
