import { defineStore } from 'pinia'
import { ref } from 'vue'
import { listDepartments, type Department } from '@/api/dept'
import { listCategories, type CategoryNode, type CategoryScope } from '@/api/category'
import { listTags, type TagVO } from '@/api/tag'
import { listUsers, type UserVO } from '@/api/user'

/**
 * 元数据 store（部门/分类/标签/人员选择器）。按需懒加载并缓存。
 */
export const useMetaStore = defineStore('meta', () => {
  const departments = ref<Department[]>([])
  const categories = ref<CategoryNode[]>([])
  const tags = ref<TagVO[]>([])
  const users = ref<UserVO[]>([])

  async function loadDepartments(force = false): Promise<void> {
    if (!force && departments.value.length) return
    departments.value = await listDepartments()
  }

  async function loadCategories(scope?: CategoryScope, force = false): Promise<void> {
    if (!force && categories.value.length) return
    categories.value = await listCategories(scope)
  }

  async function loadTags(force = false): Promise<void> {
    if (!force && tags.value.length) return
    tags.value = await listTags()
  }

  async function searchUsers(keyword = '', deptId?: number): Promise<UserVO[]> {
    const page = await listUsers({ keyword, deptId, page: 1, size: 100 })
    users.value = page.list
    return page.list
  }

  /** 拉平部门树为 {id,name,parentId} 列表（用于下拉/树）。 */
  function flatDepartments(): Department[] {
    const out: Department[] = []
    const walk = (nodes: Department[]): void => {
      for (const n of nodes) {
        out.push(n)
        if (n.children?.length) walk(n.children)
      }
    }
    walk(departments.value)
    return out
  }

  return {
    departments,
    categories,
    tags,
    users,
    loadDepartments,
    loadCategories,
    loadTags,
    searchUsers,
    flatDepartments,
  }
})
