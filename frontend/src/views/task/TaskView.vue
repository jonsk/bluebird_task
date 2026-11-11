<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ChatDotRound, List, Star, Sunny, User } from '@element-plus/icons-vue'
import BbTaskList from '@/components/bb/BbTaskList.vue'
import BbTaskComposer from '@/components/bb/BbTaskComposer.vue'
import BbTaskDetailPanel from '@/components/bb/BbTaskDetailPanel.vue'
import BbMiniCalendar from '@/components/bb/BbMiniCalendar.vue'
import BbTagConfig from '@/components/bb/BbTagConfig.vue'
import BbPageHeader from '@/components/bb/BbPageHeader.vue'
import { useTaskStore } from '@/stores/task'
import { useMetaStore } from '@/stores/meta'
import { useMenuStore } from '@/stores/menu'
import { useAuthStore } from '@/stores/auth'
import { ROUTE_SCOPE_MAP, type TaskScope } from '@/utils/constants'
import { formatHeaderDate } from '@/utils/date'
import type { TaskDetailVO, TaskVO } from '@/api/task'

/**
 * 统一任务视图（旧 `.right-box`）：主区（标题/日期 + 搜索 + 标签 + 内联编辑器 + 列表）
 * + 右栏 360px（日历 + 内联任务详情面板）。各任务视图按 route.name → scope（R9-契约）。
 */
const route = useRoute()
const taskStore = useTaskStore()
const metaStore = useMetaStore()
const menuStore = useMenuStore()
const auth = useAuthStore()

const scope = computed<TaskScope>(() => ROUTE_SCOPE_MAP[String(route.name)] ?? 'all')
const title = computed(() => (route.meta.title as string) ?? '任务')
const headerDate = computed(() => formatHeaderDate())

const scopeIcon = computed(() => {
  const map: Record<TaskScope, unknown> = { day: Sunny, joined: ChatDotRound, assigned: User, collect: Star, all: List }
  return map[scope.value]
})

const keyword = ref('')
const activeId = ref<number | null>(null)
/** 日历日期筛选只在按时间开窗的视图生效（day），其余视图不展示该筛选项。 */
const scopeUsesDate = computed(() => scope.value === 'day')

const currentUserId = computed(() => (auth.user?.id != null ? Number(auth.user.id) : null))

async function reload(): Promise<void> {
  await taskStore.fetchList(scope.value, { keyword: keyword.value })
  void taskStore.fetchCounts()
}

watch(scope, () => {
  keyword.value = ''
  // 切视图回到「默认」：我的一天即当天
  taskStore.date = null
  activeId.value = null
  void reload()
})

watch(
  () => [taskStore.menuId, taskStore.date],
  () => {
    void reload()
  },
)

const activeMenuName = computed(() => menuStore.menus.find((m) => Number(m.id) === taskStore.menuId)?.name ?? '')
const hasFilter = computed(
  () => taskStore.menuId != null || (scopeUsesDate.value && taskStore.date != null),
)

function clearAllFilters(): void {
  taskStore.clearFilters()
  keyword.value = ''
}

onMounted(async () => {
  // 任一初始化失败都不能阻断人员选择器加载（此前 Promise.all 抛错会静默跳过 searchUsers）。
  const results = await Promise.allSettled([reload(), metaStore.loadTags(), menuStore.load()])
  const failed = results.find((r): r is PromiseRejectedResult => r.status === 'rejected')
  if (failed) ElMessage.error((failed.reason as Error)?.message || '初始化失败')
  try {
    await metaStore.searchUsers('')
  } catch (e) {
    ElMessage.error((e as Error).message || '人员列表加载失败')
  }
})

function onEdit(task: TaskDetailVO | TaskVO): void {
  activeId.value = Number(task.id)
}

function onOpen(task: TaskVO): void {
  activeId.value = Number(task.id)
}

async function onToggle(task: TaskVO): Promise<void> {
  try {
    await taskStore.toggleComplete(task)
  } catch (e) {
    ElMessage.error((e as Error).message || '操作失败')
  }
}

async function onCollect(task: TaskVO): Promise<void> {
  try {
    await taskStore.toggleCollect(task)
  } catch (e) {
    ElMessage.error((e as Error).message || '操作失败')
  }
}

async function onRemove(task: TaskVO): Promise<void> {
  try {
    await ElMessageBox.confirm('删除将清空关联数据，不可恢复。确认删除？', '删除任务', {
      type: 'warning',
      confirmButtonText: '删除',
    })
    await taskStore.remove(Number(task.id))
    if (activeId.value === Number(task.id)) activeId.value = null
    ElMessage.success('已删除')
  } catch (e) {
    if (e !== 'cancel') ElMessage.error((e as Error).message || '删除失败')
  }
}
</script>

<template>
  <div class="right-box">
    <div class="right-box-div">
      <div class="right-main">
        <BbPageHeader
          class="page-shell--task"
          :title="title"
          :icon="scopeIcon"
          :date="headerDate"
        >
          <template #search>
            <el-input
              v-model="keyword"
              class="r-b-t-l-r-search"
              placeholder="请输入"
              clearable
              @keyup.enter="reload"
              @clear="reload"
            />
          </template>
          <template #actions>
            <BbTagConfig />
          </template>
        </BbPageHeader>

        <section class="right-box-bottom">
          <div class="todo-box">
            <div class="todo-list">
              <BbTaskComposer
                :tags="metaStore.tags"
                :users="metaStore.users"
                @saved="reload"
              />

              <h2 class="h2">任务</h2>

              <div v-if="hasFilter" class="todo-list__filters">
                <el-tag
                  v-if="taskStore.menuId != null"
                  type="warning"
                  closable
                  data-test="filter-menu"
                  @close="taskStore.setMenuFilter(null)"
                >
                  自定义栏：{{ activeMenuName || taskStore.menuId }}
                </el-tag>
                <el-tag
                  v-if="taskStore.date"
                  closable
                  data-test="filter-date"
                  @close="taskStore.date = null"
                >
                  日期：{{ taskStore.date }}
                </el-tag>
                <el-button text size="small" @click="clearAllFilters">清除筛选</el-button>
              </div>

              <div class="todo-list__body">
                <BbTaskList
                  :tasks="taskStore.list"
                  :loading="taskStore.loading"
                  :collected-ids="taskStore.collectedIds"
                  :current-user-id="currentUserId"
                  :role-code="auth.roleCode"
                  :active-id="activeId"
                  :empty-text="`暂无任务（${title}）`"
                  @toggle="onToggle"
                  @collect="onCollect"
                  @open="onOpen"
                  @edit="onEdit"
                  @remove="onRemove"
                  @moved="reload"
                />
              </div>
            </div>
          </div>
        </section>
      </div>

      <div class="calendar-box">
        <div class="calendar-box-content">
          <BbMiniCalendar :selected="taskStore.date" @select-date="taskStore.date = $event" />
        </div>
        <div class="detail">
          <BbTaskDetailPanel
            :task-id="activeId"
            :current-user-id="currentUserId"
            :role-code="auth.roleCode"
            :collected="activeId != null && taskStore.isCollected(activeId)"
            @changed="reload"
          />
        </div>
      </div>
    </div>
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
}
.todo-box {
  height: 100%;
  padding: 0 var(--bb-page-padding);
  box-sizing: border-box;
}
.h2 {
  margin: 0 10px 10px;
  font-size: 20px;
  font-weight: 500;
}
.todo-list__filters {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 10px 10px;
}
.todo-list__body {
  min-height: 200px;
}
.calendar-box {
  flex: 0 0 var(--bb-calendar-width);
  width: var(--bb-calendar-width);
  min-width: 0;
  /* 右栏改为纵向 flex：日历固定 280px，详情/编辑区占据剩余高度并各自滚动，
     避免「日历溢出压住编辑区」（用户反馈 #4）以及 calc(100vh - 310px) 的脆弱耦合。 */
  display: flex;
  flex-direction: column;
  height: calc(100vh - 20px);
  min-height: 0;
}
.calendar-box-content {
  flex: 0 0 280px;
  height: 280px;
  margin-top: 20px;
  overflow: hidden;
}
.detail {
  flex: 1 1 auto;
  min-height: 0;
  /* 详情面板里的表单/长标题比栏宽时左右滚动，而不是溢出压到中间的列表上 */
  overflow: auto;
}
</style>

<!-- 页头共享样式：非 scoped，保证规则能作用到 BbPageHeader 组件内部的节点 -->
<style>
@import '../../styles/page-shell.css';
</style>
