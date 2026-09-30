<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Calendar, ChatDotRound, List, Star, Sunny, User } from '@element-plus/icons-vue'
import BbTaskList from '@/components/bb/BbTaskList.vue'
import BbTaskComposer from '@/components/bb/BbTaskComposer.vue'
import BbTaskDetailPanel from '@/components/bb/BbTaskDetailPanel.vue'
import BbMiniCalendar from '@/components/bb/BbMiniCalendar.vue'
import BbTagConfig from '@/components/bb/BbTagConfig.vue'
import { useTaskStore } from '@/stores/task'
import { useMetaStore } from '@/stores/meta'
import { useMenuStore } from '@/stores/menu'
import { useAuthStore } from '@/stores/auth'
import { ROUTE_SCOPE_MAP, type TaskScope } from '@/utils/constants'
import { formatHeaderDate } from '@/utils/date'
import type { TaskDetailVO, TaskVO } from '@/api/task'
import type { CategoryNode } from '@/api/category'

/**
 * 统一任务视图（旧 `.right-box`）：主区（标题/日期 + 搜索 + 标签 + 内联编辑器 + 列表）
 * + 右栏 360px（日历 + 内联任务详情面板）。六大视图按 route.name → scope（R9-契约）。
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
  const map: Record<TaskScope, unknown> = { day: Sunny, week: Calendar, joined: ChatDotRound, assigned: User, collect: Star, all: List }
  return map[scope.value]
})

const keyword = ref('')
const dateFilter = ref<string | null>(null)
const activeId = ref<number | null>(null)

const currentUserId = computed(() => (auth.user?.id != null ? Number(auth.user.id) : null))

async function reload(): Promise<void> {
  await taskStore.fetchList(scope.value, { keyword: keyword.value })
  void taskStore.fetchCounts()
}

watch(scope, () => {
  keyword.value = ''
  dateFilter.value = null
  activeId.value = null
  void reload()
})

watch(
  () => [taskStore.categoryId, taskStore.menuId],
  () => {
    void reload()
  },
)

const activeCategoryName = computed(() => {
  const id = taskStore.categoryId
  if (id == null) return ''
  const find = (nodes: CategoryNode[]): string => {
    for (const n of nodes) {
      if (Number(n.id) === id) return n.name ?? ''
      const hit = find(n.children ?? [])
      if (hit) return hit
    }
    return ''
  }
  return find(metaStore.categories)
})

const activeMenuName = computed(() => menuStore.menus.find((m) => Number(m.id) === taskStore.menuId)?.name ?? '')
const hasFilter = computed(() => taskStore.categoryId != null || taskStore.menuId != null || dateFilter.value != null)

function clearAllFilters(): void {
  taskStore.clearFilters()
  keyword.value = ''
  dateFilter.value = null
}

onMounted(async () => {
  await Promise.all([reload(), metaStore.loadTags(), metaStore.loadCategories(), menuStore.load()])
  await metaStore.searchUsers('')
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
        <section class="right-box-top">
          <div class="r-b-t-left">
            <div class="r-b-t-l-left">
              <el-icon class="r-b-t-l-l-icon" :size="28"><component :is="scopeIcon" /></el-icon>
              <div class="r-b-t-l-l-text">
                <div class="r-b-t-l-l-t-top">{{ title }}</div>
                <div class="r-b-t-l-l-t-bottom">{{ headerDate }}</div>
              </div>
            </div>

            <div class="r-b-t-l-right">
              <el-input
                v-model="keyword"
                class="r-b-t-l-r-search"
                placeholder="请输入"
                clearable
                @keyup.enter="reload"
                @clear="reload"
              />
            </div>

            <div class="r-b-t-right">
              <BbTagConfig />
            </div>
          </div>
        </section>

        <section class="right-box-bottom">
          <div class="todo-box">
            <div class="todo-list">
              <BbTaskComposer
                :categories="metaStore.categories"
                :tags="metaStore.tags"
                :users="metaStore.users"
                :default-category-id="taskStore.categoryId"
                @saved="reload"
              />

              <h2 class="h2">任务</h2>

              <div v-if="hasFilter" class="todo-list__filters">
                <el-tag
                  v-if="taskStore.categoryId != null"
                  closable
                  data-test="filter-category"
                  @close="taskStore.setCategoryFilter(null)"
                >
                  分类：{{ activeCategoryName || taskStore.categoryId }}
                </el-tag>
                <el-tag
                  v-if="taskStore.menuId != null"
                  type="warning"
                  closable
                  data-test="filter-menu"
                  @close="taskStore.setMenuFilter(null)"
                >
                  自定义栏：{{ activeMenuName || taskStore.menuId }}
                </el-tag>
                <el-tag v-if="dateFilter" closable data-test="filter-date" @close="dateFilter = null">
                  日期：{{ dateFilter }}
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
          <BbMiniCalendar :selected="dateFilter" @select-date="dateFilter = $event" />
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
.right-box-top {
  height: 50px;
  margin-bottom: 20px;
  padding-top: 15px;
  box-sizing: border-box;
  user-select: none;
}
.r-b-t-left {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-left: 20px;
}
.r-b-t-l-left {
  display: flex;
  align-items: center;
}
.r-b-t-l-l-icon {
  color: var(--bb-text-secondary);
}
.r-b-t-l-l-text {
  margin-left: 10px;
}
.r-b-t-l-l-t-top {
  font-size: 16px;
}
.r-b-t-l-l-t-bottom {
  font-size: 12px;
  color: var(--bb-text-muted);
}
.r-b-t-l-right {
  width: 500px;
  margin-left: 50px;
}
.r-b-t-r-search {
  width: 500px;
}
.r-b-t-right {
  display: flex;
  align-items: center;
  padding-right: 30px;
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
}
.calendar-box-content {
  height: 280px;
  margin-top: 20px;
}
.detail {
  height: calc(100vh - 310px);
  min-height: 0;
  overflow-y: auto;
}
</style>
