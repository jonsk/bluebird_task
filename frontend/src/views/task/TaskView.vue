<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import BbTaskList from '@/components/bb/BbTaskList.vue'
import BbTaskComposer from '@/components/bb/BbTaskComposer.vue'
import BbTaskDetailDrawer from '@/components/bb/BbTaskDetailDrawer.vue'
import { useTaskStore } from '@/stores/task'
import { useMetaStore } from '@/stores/meta'
import { useMenuStore } from '@/stores/menu'
import { useAuthStore } from '@/stores/auth'
import { ROUTE_SCOPE_MAP, type TaskScope } from '@/utils/constants'
import type { TaskDetailVO, TaskVO } from '@/api/task'
import type { CategoryNode } from '@/api/category'

/**
 * 统一任务视图（03 §5.3.1）：六大视图按 route.name → scope（R9-契约）。
 * 左栏筛选（分类子树 / 自定义栏）经 task store 叠加，变更后重查。
 */
const route = useRoute()
const taskStore = useTaskStore()
const metaStore = useMetaStore()
const menuStore = useMenuStore()
const auth = useAuthStore()

const scope = computed<TaskScope>(() => ROUTE_SCOPE_MAP[String(route.name)] ?? 'all')
const title = computed(() => (route.meta.title as string) ?? '任务')

const keyword = ref('')
const composerOpen = ref(false)
const editing = ref<TaskVO | null>(null)
const drawerOpen = ref(false)
const activeId = ref<number | null>(null)

const currentUserId = computed(() => (auth.user?.id != null ? Number(auth.user.id) : null))

const scopePrompt = computed(() => {
  const map: Record<TaskScope, string> = {
    day: '今天到期与已过期的待办',
    week: '未来 7 天内到期的待办',
    joined: '我作为知会（@）的任务（只读）',
    assigned: '指派给我的任务',
    collect: '我收藏的任务',
    all: '我参与或拥有的全部任务',
  }
  return map[scope.value]
})

async function reload(): Promise<void> {
  await taskStore.fetchList(scope.value, { keyword: keyword.value })
  void taskStore.fetchCounts()
}

watch(scope, () => {
  keyword.value = ''
  void reload()
})

// 左栏筛选（分类 / 自定义栏）变化 → 重查
watch(
  () => [taskStore.categoryId, taskStore.menuId],
  () => {
    void reload()
  },
)

/** 当前分类名（分类树为树形，需递归查找）。 */
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

const activeMenuName = computed(
  () => menuStore.menus.find((m) => Number(m.id) === taskStore.menuId)?.name ?? '',
)

const hasFilter = computed(() => taskStore.categoryId != null || taskStore.menuId != null)

function clearAllFilters(): void {
  taskStore.clearFilters()
  keyword.value = ''
}

onMounted(async () => {
  await Promise.all([reload(), metaStore.loadTags(), metaStore.loadCategories(), menuStore.load()])
  await metaStore.searchUsers('')
})

function onCreate(): void {
  editing.value = null
  composerOpen.value = true
}

function onEdit(task: TaskDetailVO): void {
  editing.value = task
  composerOpen.value = true
}

function onOpen(task: TaskVO): void {
  activeId.value = Number(task.id)
  drawerOpen.value = true
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
    ElMessage.success('已删除')
  } catch (e) {
    if (e !== 'cancel') ElMessage.error((e as Error).message || '删除失败')
  }
}
</script>

<template>
  <div class="task-view">
    <header class="task-view__header">
      <div>
        <h2>{{ title }}</h2>
        <p class="task-view__prompt">{{ scopePrompt }}</p>
      </div>
      <div class="task-view__actions">
        <el-input
          v-model="keyword"
          placeholder="搜索标题/内容"
          clearable
          style="width: 220px"
          @keyup.enter="reload"
          @clear="reload"
        >
          <template #append>
            <el-button @click="reload">搜索</el-button>
          </template>
        </el-input>
        <el-button type="primary" @click="onCreate">新建任务</el-button>
      </div>
    </header>

    <div v-if="hasFilter" class="task-view__filters">
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
      <el-button text size="small" @click="clearAllFilters">清除筛选</el-button>
    </div>

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
      @remove="onRemove"
    />

    <BbTaskComposer
      v-model="composerOpen"
      :task="editing"
      :categories="metaStore.categories"
      :tags="metaStore.tags"
      :users="metaStore.users"
      @saved="reload"
    />

    <BbTaskDetailDrawer
      v-model="drawerOpen"
      :task-id="activeId"
      :current-user-id="currentUserId"
      :role-code="auth.roleCode"
      :collected="activeId != null && taskStore.isCollected(activeId)"
      @edit="onEdit"
      @changed="reload"
    />
  </div>
</template>

<style scoped>
.task-view {
  padding: 16px 20px;
}
.task-view__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}
.task-view__header h2 {
  margin: 0;
  font-size: 18px;
}
.task-view__prompt {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--bb-color-muted);
}
.task-view__actions {
  display: flex;
  gap: 8px;
}
.task-view__filters {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
</style>
