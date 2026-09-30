import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import {
  listTasks,
  counts as apiCounts,
  completeTask,
  uncompleteTask,
  collectTask,
  uncollectTask,
  deleteTask,
  type CountVO,
  type Scope,
  type TaskVO,
} from '@/api/task'

/**
 * 任务 store（03 §4.2）。六大视图列表 + 计数 + 完成/收藏联动（02 §4.4）。
 */
export const useTaskStore = defineStore('task', () => {
  const list = ref<TaskVO[]>([])
  const total = ref(0)
  const loading = ref(false)
  const counts = ref<CountVO | null>(null)
  const currentScope = ref<Scope>('day')
  const keyword = ref('')
  const subordinate = ref(false)
  const collectedIds = ref<Set<number>>(new Set())
  /** 左栏筛选：分类子树 / 自定义栏（与 scope、keyword 叠加）。 */
  const categoryId = ref<number | null>(null)
  const menuId = ref<number | null>(null)
  /**
   * 右栏日历选中的日期（yyyy-MM-dd）。语义随视图而定：
   * `day`＝该日**新建**的任务（旧系统 getDayTask 的 task_setup_time）；
   * `week`＝以该日为起点的 7 天窗口；其余视图忽略。
   */
  const date = ref<string | null>(null)

  const isEmpty = computed(() => !loading.value && list.value.length === 0)

  async function fetchList(
    scope: Scope,
    opts: {
      keyword?: string
      subordinate?: boolean
      categoryId?: number | null
      menuId?: number | null
      date?: string | null
      page?: number
      size?: number
    } = {},
  ): Promise<void> {
    currentScope.value = scope
    loading.value = true
    try {
      const data = await listTasks({
        scope,
        keyword: opts.keyword ?? keyword.value,
        subordinate: opts.subordinate ?? subordinate.value,
        categoryId: opts.categoryId === undefined ? (categoryId.value ?? undefined) : (opts.categoryId ?? undefined),
        menuId: opts.menuId === undefined ? (menuId.value ?? undefined) : (opts.menuId ?? undefined),
        date: opts.date === undefined ? (date.value ?? undefined) : (opts.date ?? undefined),
        page: opts.page ?? 1,
        size: opts.size ?? 100,
      })
      list.value = data.list
      total.value = data.total
      if (scope === 'collect') {
        collectedIds.value = new Set(data.list.map((t) => Number(t.id)))
      }
    } finally {
      loading.value = false
    }
  }

  /** 选中分类（null = 清除）。仅改状态，由任务视图监听后重查。 */
  function setCategoryFilter(id: number | null): void {
    categoryId.value = id
  }

  /** 选中自定义栏（null = 清除）。仅改状态，由任务视图监听后重查。 */
  function setMenuFilter(id: number | null): void {
    menuId.value = id
  }

  /** 清除分类 + 自定义栏 + 日期筛选。 */
  function clearFilters(): void {
    categoryId.value = null
    menuId.value = null
    date.value = null
  }

  async function fetchCounts(): Promise<void> {
    counts.value = await apiCounts()
  }

  function isCollected(id: number): boolean {
    return collectedIds.value.has(id)
  }

  /** 完成/取消完成（周期任务按实例 dueAt 推进，02 §4.3 R2）。 */
  async function toggleComplete(task: TaskVO): Promise<void> {
    const id = Number(task.id)
    const version = Number(task.version ?? 0)
    const dueAt = task.dueAt ?? null
    if (task.completed) {
      await uncompleteTask(id, { version, dueAt })
    } else {
      await completeTask(id, { version, dueAt })
    }
    await Promise.all([fetchList(currentScope.value), fetchCounts()])
  }

  async function toggleCollect(task: TaskVO): Promise<void> {
    const id = Number(task.id)
    if (isCollected(id)) {
      await uncollectTask(id)
      collectedIds.value.delete(id)
    } else {
      await collectTask(id)
      collectedIds.value.add(id)
    }
    await fetchCounts()
  }

  async function remove(id: number): Promise<void> {
    await deleteTask(id)
    await Promise.all([fetchList(currentScope.value), fetchCounts()])
  }

  return {
    list,
    total,
    loading,
    counts,
    currentScope,
    keyword,
    subordinate,
    collectedIds,
    categoryId,
    menuId,
    date,
    isEmpty,
    fetchList,
    fetchCounts,
    setCategoryFilter,
    setMenuFilter,
    clearFilters,
    isCollected,
    toggleComplete,
    toggleCollect,
    remove,
  }
})
