<script setup lang="ts">
import { computed, nextTick, onMounted, ref, shallowRef } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Close, Edit, Lock, Plus } from '@element-plus/icons-vue'
import BbEmpty from './BbEmpty.vue'
import {
  createCategory,
  deleteCategory,
  listCategories,
  updateCategory,
  type CategoryNode,
  type CategoryScope,
} from '@/api/category'
import { useAuthStore } from '@/stores/auth'
import { useMetaStore } from '@/stores/meta'

/**
 * BbCategoryTree — 分类树（契约：BbCategoryTree.md；共享范围 ADR-015）。
 * 读 `GET /categories`（可选 `?scope=`）；写 `POST/PUT/DELETE /categories`。
 * 点击节点 → emit('select', id) 联动任务列表过滤（补齐旧实现缺失的选中链路）。
 */
const emit = defineEmits<{ (e: 'select', id: number | null): void; (e: 'changed'): void }>()

interface TreeInstance {
  append: (data: Record<string, unknown>, parent?: unknown) => void
  remove: (data: unknown) => void
  getNode: (key: number | string) => { data: Record<string, unknown> } | undefined
  setCurrentKey: (key?: number | string | null) => void
}

const DRAFT_ID = '__bb_draft__'

const auth = useAuthStore()
const meta = useMetaStore()

const treeRef = ref<TreeInstance>()
const nameInput = shallowRef<{ focus: () => void } | null>(null)

const tree = ref<CategoryNode[]>([])
const loading = ref(false)
const scopeFilter = ref<'' | CategoryScope>('')
const activeId = ref<number | null>(null)

const draftActive = ref(false)
const draftParentId = ref<number | null>(null)
const draftScope = ref<CategoryScope>('PERSONAL')
const draftDeptId = ref<number | null>(null)
const editingId = ref<number | null>(null)
const draftName = ref('')

const me = computed(() => (auth.user?.id != null ? Number(auth.user.id) : null))
const myDeptId = computed(() => (auth.user?.deptId != null ? Number(auth.user.deptId) : null))
const isAdmin = computed(() => auth.roleCode === 'ADMIN')
const isUserManager = computed(() => auth.roleCode === 'USER_MANAGER')

/** 部门分类可选部门（扁平化，供草稿行内选择）。 */
const deptOptions = computed(() =>
  meta.flatDepartments().map((d) => ({ id: Number(d.id), name: d.name ?? '' })),
)

/** 新增可选范围（超出权限的选项不渲染，避免提交必败）。 */
const scopeOptions = computed(() => {
  const opts: Array<{ value: CategoryScope; label: string }> = [{ value: 'PERSONAL', label: '个人' }]
  if (isAdmin.value || isUserManager.value || myDeptId.value != null) {
    opts.push({ value: 'DEPARTMENT', label: '部门' })
  }
  if (isAdmin.value || isUserManager.value) opts.push({ value: 'ORG', label: '组织' })
  return opts
})

const scopeTabs = [
  { value: '' as const, label: '全部' },
  { value: 'PERSONAL' as const, label: '个人' },
  { value: 'DEPARTMENT' as const, label: '部门' },
  { value: 'ORG' as const, label: '组织' },
]

function deptName(deptId?: number | null): string {
  if (deptId == null) return '部门'
  const d = meta.flatDepartments().find((x) => Number(x.id) === Number(deptId))
  return d?.name ?? '部门'
}

function isDeptLeader(deptId?: number | null): boolean {
  if (deptId == null) return false
  const d = meta.flatDepartments().find((x) => Number(x.id) === Number(deptId))
  return d?.leaderId != null && Number(d.leaderId) === me.value
}

/** 与后端 CategoryService.assertWritable 对齐（ADR-015 §3）。 */
function writable(node: CategoryNode): boolean {
  if (isAdmin.value) return true
  if (node.scope === 'ORG') return isUserManager.value
  if (node.scope === 'DEPARTMENT') return Number(node.ownerId) === me.value || isDeptLeader(node.deptId)
  return Number(node.ownerId) === me.value
}

function sameScope(a: CategoryNode, b: CategoryNode): boolean {
  if (a.scope !== b.scope) return false
  return a.scope !== 'DEPARTMENT' || Number(a.deptId) === Number(b.deptId)
}

function errMsg(e: unknown): string {
  return (e as Error)?.message || '操作失败'
}

async function reload(): Promise<void> {
  loading.value = true
  try {
    tree.value = (await listCategories(scopeFilter.value || undefined)) ?? []
    if (activeId.value != null) {
      const exists = (function find(nodes: CategoryNode[]): boolean {
        return nodes.some((n) => Number(n.id) === activeId.value || find(n.children ?? []))
      })(tree.value)
      if (!exists) activeId.value = null
    }
    // 「全部」范围下同步 meta store，供任务编辑器分类下拉复用
    if (!scopeFilter.value) meta.categories = tree.value
    await nextTick()
    treeRef.value?.setCurrentKey(activeId.value)
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await meta.loadDepartments()
  await reload()
})

/** el-radio-group 的 change 载荷为宽松联合类型，此处收敛到范围枚举。 */
async function onScopeFilter(value: string | number | boolean | undefined): Promise<void> {
  scopeFilter.value = (value as '' | CategoryScope) ?? ''
  await reload()
}

function onNodeClick(data: CategoryNode): void {
  // 草稿行（id 为占位串 __bb_draft__）不是真实分类：点击不得联动过滤，
  // 否则 Number('__bb_draft__') = NaN → 向后端发出 ?categoryId=NaN（参数类型错误）。
  const raw = data as unknown as { id?: number | string | null; __draft?: boolean }
  if (raw.__draft || raw.id == null) return
  const id = Number(raw.id)
  if (!Number.isFinite(id)) return
  activeId.value = id
  treeRef.value?.setCurrentKey(id)
  emit('select', id)
}

function clearSelection(): void {
  activeId.value = null
  treeRef.value?.setCurrentKey(null)
  emit('select', null)
}

function focusDraft(): void {
  void nextTick(() => nameInput.value?.focus())
}

function removeDraft(): void {
  const node = treeRef.value?.getNode(DRAFT_ID)
  if (node) treeRef.value?.remove(node)
}

function startAdd(data: CategoryNode | null, scope: CategoryScope): void {
  if (draftActive.value || editingId.value != null) return
  draftActive.value = true
  draftScope.value = scope
  // 部门分类的所属部门由系统静默判定：优先本部门，兜底系统默认部门；
  // 用户只需输入分类名即可（不再要求选择「部门」）。
  draftDeptId.value =
    scope === 'DEPARTMENT' ? (myDeptId.value ?? deptOptions.value[0]?.id ?? null) : null
  draftParentId.value = data?.id != null ? Number(data.id) : null
  draftName.value = ''
  const parent = data?.id != null ? treeRef.value?.getNode(Number(data.id)) : undefined
  treeRef.value?.append({ id: DRAFT_ID, name: '', __draft: true }, parent)
  focusDraft()
}

/** 顶部「新增」：新增根级分类。 */
function onAddRoot(scope: CategoryScope): void {
  startAdd(null, scope)
}

/** 节点「新增子分类」（模板内不支持内联类型标注，经 $event 传 command）。 */
function onAddChild(data: CategoryNode, scope: CategoryScope): void {
  startAdd(data, scope)
}

/** el-tree 拖拽入参（结构等价于 AllowDropFunction 的 Node，避免依赖未导出的内部类型）。 */
interface DragNode {
  data: CategoryNode
  parent?: DragNode | null
}

/** el-tree allow-drag。 */
function allowDrag(node: DragNode): boolean {
  return writable(node.data)
}

function startEdit(data: CategoryNode): void {
  if (draftActive.value || editingId.value != null) return
  editingId.value = Number(data.id)
  draftName.value = data.name ?? ''
  focusDraft()
}

/** 草稿行失焦：焦点仍在草稿行内（例如点开同行的「选择部门」下拉）时不提交，避免误用默认部门建档。 */
function onDraftBlur(e: FocusEvent): void {
  const target = e.target as HTMLElement | null
  const next = e.relatedTarget as Node | null
  const row = target?.closest('.cat-node__draft')
  if (row && next && row.contains(next)) return
  void commitDraft()
}

async function commitDraft(): Promise<void> {
  if (!draftActive.value) return
  const name = draftName.value.trim()
  const parentId = draftParentId.value
  const scope = draftScope.value
  const deptId = draftDeptId.value
  if (!name) {
    draftActive.value = false
    removeDraft()
    return
  }
  // 部门分类未选部门时保留草稿行（选中部门后会再次触发提交），避免静默失败
  if (scope === 'DEPARTMENT' && deptId == null) {
    ElMessage.warning('部门分类必须选择所属部门')
    return
  }
  draftActive.value = false
  removeDraft()
  try {
    await createCategory({ name, parentId, scope, deptId })
    await reload()
    emit('changed')
    ElMessage.success('已新增分类')
  } catch (e) {
    ElMessage.error(errMsg(e))
  }
}

async function commitEdit(data: CategoryNode): Promise<void> {
  if (editingId.value == null) return
  const id = editingId.value
  const name = draftName.value.trim()
  editingId.value = null
  if (!name || name === data.name) return
  try {
    // parentId 必须回传：后端 PUT 会整体覆盖（缺省即置空，等于把节点移到根）
    await updateCategory(id, { name, parentId: data.parentId ?? null })
    await reload()
    emit('changed')
    ElMessage.success('已重命名')
  } catch (e) {
    ElMessage.error(errMsg(e))
  }
}

async function onDelete(data: CategoryNode): Promise<void> {
  const shared = data.scope === 'ORG' || data.scope === 'DEPARTMENT'
  const tip = shared
    ? `「${data.name}」为${data.scope === 'ORG' ? '组织' : '部门'}共享分类，删除将影响范围内成员。确认删除？`
    : `确认删除分类「${data.name}」？`
  try {
    await ElMessageBox.confirm(tip, '删除分类', { type: 'warning', confirmButtonText: '删除' })
  } catch {
    return
  }
  try {
    await deleteCategory(Number(data.id))
    if (activeId.value === Number(data.id)) clearSelection()
    await reload()
    emit('changed')
    ElMessage.success('已删除')
  } catch (e) {
    ElMessage.error(errMsg(e))
  }
}

function allowDrop(dragNode: DragNode, dropNode: DragNode, type: string): boolean {
  const a = dragNode.data
  if (!writable(a)) return false
  if (type === 'inner') return writable(dropNode.data) && sameScope(a, dropNode.data)
  const parent = dropNode.parent?.data
  if (!parent || parent.id == null) return true
  return writable(parent) && sameScope(a, parent)
}

async function onDrop(dragNode: DragNode, dropNode: DragNode, type: string): Promise<void> {
  const a = dragNode.data
  if (!writable(a)) return
  const parentRaw = type === 'inner' ? dropNode.data : dropNode.parent?.data
  const parentId = parentRaw?.id != null ? Number(parentRaw.id) : null
  if (type === 'inner' && parentRaw && !sameScope(a, parentRaw)) {
    ElMessage.warning('禁止跨范围拖拽')
    await reload()
    return
  }
  try {
    await updateCategory(Number(a.id), { name: a.name ?? '', parentId })
    await reload()
    emit('changed')
    ElMessage.success('已移动分类')
  } catch (e) {
    ElMessage.error(errMsg(e))
    await reload()
  }
}

defineExpose({ reload, clearSelection })
</script>

<template>
  <section class="bb-cat">
    <header class="bb-cat__head">
      <span class="bb-cat__title">分类</span>
      <el-dropdown v-if="scopeOptions.length" trigger="click" @command="onAddRoot">
        <el-icon class="bb-cat__add" title="新增分类"><Plus /></el-icon>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item v-for="o in scopeOptions" :key="o.value" :command="o.value">
              新增{{ o.label }}分类
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </header>

    <el-radio-group :model-value="scopeFilter" size="small" class="bb-cat__filter" @change="onScopeFilter">
      <el-radio-button v-for="t in scopeTabs" :key="t.value || 'all'" :value="t.value">{{ t.label }}</el-radio-button>
    </el-radio-group>

    <div v-loading="loading" class="bb-cat__body">
      <el-tree
        ref="treeRef"
        v-show="tree.length || draftActive"
        class="bb-cat__tree"
        :data="tree"
        :props="{ label: 'name', children: 'children' }"
        node-key="id"
        default-expand-all
        :expand-on-click-node="false"
        highlight-current
        draggable
        :allow-drop="allowDrop"
        :allow-drag="allowDrag"
        @node-click="onNodeClick"
        @node-drop="onDrop"
      >
        <template #default="{ data }">
          <div class="cat-node" :class="{ 'is-active': data.id === activeId }">
            <template v-if="data.id === editingId">
              <el-input
                ref="nameInput"
                v-model="draftName"
                size="small"
                class="cat-node__input"
                @keyup.enter="commitEdit(data)"
                @blur="commitEdit(data)"
              />
            </template>

            <template v-else-if="data.__draft">
              <!-- 部门分类的所属部门由系统自动判定（本部门，兜底系统默认部门），
                   不再让用户选「部门」，避免多一步无意义的选择。 -->
              <el-input
                ref="nameInput"
                v-model="draftName"
                size="small"
                placeholder="分类名称，回车保存"
                class="cat-node__input"
                @keyup.enter="commitDraft"
                @blur="onDraftBlur"
              />
            </template>

            <template v-else>
              <span class="cat-node__label" :title="data.name">{{ data.name }}</span>
              <el-tag v-if="data.scope === 'PERSONAL'" size="small" effect="plain" class="cat-node__badge">
                个人
              </el-tag>
              <el-tag
                v-else-if="data.scope === 'DEPARTMENT'"
                size="small"
                effect="plain"
                type="success"
                class="cat-node__badge"
              >
                {{ deptName(data.deptId) }}
              </el-tag>
              <el-tag v-else-if="data.scope === 'ORG'" size="small" effect="plain" type="warning" class="cat-node__badge">
                组织
              </el-tag>
              <span v-if="data.taskCount" class="cat-node__count">{{ data.taskCount }}</span>
              <el-icon v-if="!writable(data)" class="cat-node__lock" title="只读"><Lock /></el-icon>
              <span v-if="writable(data)" class="cat-node__ops">
                <el-dropdown trigger="click" @command="onAddChild(data, $event)">
                  <el-icon class="cat-node__op" title="新增子分类" @click.stop><Plus /></el-icon>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item v-for="o in scopeOptions" :key="o.value" :command="o.value">
                        新增{{ o.label }}分类
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
                <el-icon class="cat-node__op" title="重命名" @click.stop="startEdit(data)"><Edit /></el-icon>
                <el-icon class="cat-node__op" title="删除" @click.stop="onDelete(data)"><Close /></el-icon>
              </span>
            </template>
          </div>
        </template>
      </el-tree>

      <BbEmpty v-if="!tree.length && !draftActive" description="暂无分类" />
    </div>
  </section>
</template>

<style scoped>
.bb-cat {
  display: flex;
  flex-direction: column;
  min-height: 0;
  min-width: 0;
}
.bb-cat__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 2px 2px 6px;
}
.bb-cat__title {
  font-size: 13px;
  font-weight: 600;
  color: #1f2430;
}
.bb-cat__add {
  cursor: pointer;
  color: var(--bb-color-primary);
}
.bb-cat__filter {
  margin-bottom: 6px;
}
.bb-cat__body {
  min-height: 40px;
  min-width: 0;
  overflow-x: hidden;
}
.bb-cat__tree {
  font-size: 13px;
  background: transparent;
  min-width: 0;
}
.bb-cat__tree :deep(.el-tree-node__content) {
  min-width: 0;
}
.cat-node {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  min-width: 0;
  padding-right: 4px;
  overflow: hidden;
}
.cat-node__label {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cat-node__badge {
  flex-shrink: 0;
  transform: scale(0.85);
  /* 装饰性徽标不得吞掉点击（否则整行点选会落在徽标上而不触发 node-click） */
  pointer-events: none;
}
.cat-node__count {
  font-size: 12px;
  color: var(--bb-color-muted);
  pointer-events: none;
}
.cat-node__lock {
  color: var(--bb-color-muted);
  font-size: 12px;
  pointer-events: none;
}
.cat-node__input {
  width: 100%;
}
.cat-node__ops {
  display: none;
  align-items: center;
  gap: 6px;
  margin-left: auto;
}
.cat-node:hover .cat-node__ops {
  display: inline-flex;
}
.cat-node__op {
  cursor: pointer;
  color: var(--bb-color-muted);
}
.cat-node__op:hover {
  color: var(--bb-color-primary);
}
</style>
