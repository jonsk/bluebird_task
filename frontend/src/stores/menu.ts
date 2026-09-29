import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  addMenuItem,
  createMenu,
  deleteMenu,
  listMenus,
  removeMenuItem,
  updateMenu,
  type Menu,
} from '@/api/menu'

/**
 * 自定义栏 store（03 §5.3.4）。栏及其条目用于「移动到自定义栏」与左栏筛选。
 */
export const useMenuStore = defineStore('menu', () => {
  const menus = ref<Menu[]>([])
  const loading = ref(false)

  async function load(force = false): Promise<void> {
    if (!force && menus.value.length) return
    loading.value = true
    try {
      menus.value = (await listMenus()) ?? []
    } finally {
      loading.value = false
    }
  }

  async function reload(): Promise<void> {
    await load(true)
  }

  async function create(name: string): Promise<number> {
    const id = await createMenu(name)
    await reload()
    return id
  }

  async function rename(id: number, name: string): Promise<void> {
    await updateMenu(id, name)
    await reload()
  }

  async function remove(id: number): Promise<void> {
    await deleteMenu(id)
    await reload()
  }

  /** 移动任务到栏（已存在则幂等）。 */
  async function addTask(menuId: number, taskId: number): Promise<void> {
    await addMenuItem(menuId, taskId)
    await reload()
  }

  async function removeTask(menuId: number, itemId: number): Promise<void> {
    await removeMenuItem(menuId, itemId)
    await reload()
  }

  /** 任务是否已在某栏中，返回该栏条目 id。 */
  function itemIdOf(menuId: number, taskId: number): number | null {
    const menu = menus.value.find((m) => Number(m.id) === menuId)
    const item = menu?.items?.find((i) => Number(i.taskId) === taskId)
    return item ? Number(item.id) : null
  }

  /** 任务所属的所有栏 id。 */
  function menuIdsOf(taskId: number): number[] {
    return menus.value.filter((m) => m.items?.some((i) => Number(i.taskId) === taskId)).map((m) => Number(m.id))
  }

  return {
    menus,
    loading,
    load,
    reload,
    create,
    rename,
    remove,
    addTask,
    removeTask,
    itemIdOf,
    menuIdsOf,
  }
})
