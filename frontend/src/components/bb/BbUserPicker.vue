<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { listDepartments, type Department } from '@/api/dept'
import { listUsers, type UserVO } from '@/api/user'

/**
 * BbUserPicker — 人员选择器（左树右表 + 搜索）。
 *
 * 契约来源：`docs/frontend-baseline/contracts/BbUserSelect.md`（旧 `selectUser.vue` 为「搜索 + 表格 + 分页」弹窗）。
 * 本实现按大型组织（约 8 千部门 / 4 万人员）补强为**左机构树 + 右人员表 + 服务端搜索/分页**：
 * - 只按姓名搜索在大组织里很难定位到正确的人，故同时提供**按机构树逐级下钻**；
 * - 人员列表**服务端分页**（`GET /users`），默认 `includeSubDept=true`：
 *   中间层部门通常没有直属成员，只看本层会误判「该部门没人」；
 * - 勾选跨分页/跨搜索保留（旧实现的「取消勾选不移除」缺陷在此修正）：
 *   每次 `selection-change` 只重算**当前页**的勾选，其余页签保留在 `picked`。
 */
const visible = defineModel<boolean>({ required: true })

const props = withDefaults(
  defineProps<{
    /** 打开时用于回显的人员（跨分页时后端未必在第一页返回）。 */
    pickedUsers?: UserVO[]
  }>(),
  { pickedUsers: () => [] },
)

const emit = defineEmits<{
  (e: 'confirm', users: UserVO[]): void
}>()

const tree = ref<Department[]>([])
const treeLoading = ref(false)
const deptKeyword = ref('')
const treeRef = ref<{ setCurrentKey: (k?: number | null) => void; filter: (v: string) => void }>()

const selectedDeptId = ref<number | null>(null)
const selectedDeptName = ref('全部人员')

const keyword = ref('')
const loading = ref(false)
const rows = ref<UserVO[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)

const tableRef = ref<{ toggleRowSelection: (row: UserVO, selected?: boolean) => void; clearSelection: () => void }>()
/** 已选人员：id → 用户对象（跨页累积）。 */
const picked = ref<Map<number, UserVO>>(new Map())

const pickedList = computed(() => [...picked.value.values()])

async function loadTree(): Promise<void> {
  if (tree.value.length) return
  treeLoading.value = true
  try {
    tree.value = (await listDepartments()) ?? []
  } catch (e) {
    ElMessage.error((e as Error)?.message || '加载机构树失败')
  } finally {
    treeLoading.value = false
  }
}

async function loadUsers(): Promise<void> {
  loading.value = true
  try {
    const res = await listUsers({
      deptId: selectedDeptId.value ?? undefined,
      // 有搜索词时跨部门全局搜索（大组织里按名字找人优先于按部门下钻）
      includeSubDept: true,
      keyword: keyword.value.trim() || undefined,
      page: page.value,
      size: size.value,
    })
    rows.value = res.list ?? []
    total.value = res.total ?? 0
    await nextTick()
    syncPageSelection()
  } catch (e) {
    ElMessage.error((e as Error)?.message || '加载人员失败')
  } finally {
    loading.value = false
  }
}

/** 用已选集合回显当前页勾选态。 */
function syncPageSelection(): void {
  const t = tableRef.value
  if (!t) return
  for (const r of rows.value) {
    t.toggleRowSelection(r, picked.value.has(Number(r.id)))
  }
}

/** 只重算当前页的勾选，其他页/搜索结果的已选保持不变。 */
function onSelectionChange(selection: UserVO[]): void {
  const pageIds = new Set(rows.value.map((r) => Number(r.id)))
  for (const id of pageIds) picked.value.delete(id)
  for (const u of selection) picked.value.set(Number(u.id), u)
  picked.value = new Map(picked.value)
}

function onDeptClick(dept: Department | null): void {
  selectedDeptId.value = dept ? Number(dept.id) : null
  selectedDeptName.value = dept?.name ?? '全部人员'
  treeRef.value?.setCurrentKey(selectedDeptId.value)
  page.value = 1
  void loadUsers()
}

function onSearch(): void {
  page.value = 1
  void loadUsers()
}

function onReset(): void {
  keyword.value = ''
  selectedDeptId.value = null
  selectedDeptName.value = '全部人员'
  treeRef.value?.setCurrentKey(null)
  page.value = 1
  void loadUsers()
}

function removePicked(id: number): void {
  picked.value.delete(id)
  picked.value = new Map(picked.value)
  syncPageSelection()
}

function clearPicked(): void {
  picked.value = new Map()
  tableRef.value?.clearSelection()
}

function onConfirm(): void {
  emit('confirm', pickedList.value)
  visible.value = false
}

function onClosed(): void {
  picked.value = new Map()
  keyword.value = ''
  deptKeyword.value = ''
  selectedDeptId.value = null
  selectedDeptName.value = '全部人员'
  page.value = 1
}

/** 机构树按名称过滤（节点过滤由 el-tree 的 filter 方法处理）。 */
function onDeptFilter(v: string): void {
  treeRef.value?.filter(v)
}

function filterNode(value: string, data: Department): boolean {
  if (!value) return true
  return (data.name ?? '').includes(value)
}

watch(visible, async (open) => {
  if (!open) return
  picked.value = new Map((props.pickedUsers ?? []).map((u) => [Number(u.id), u]))
  page.value = 1
  keyword.value = ''
  await Promise.all([loadTree(), loadUsers()])
})
</script>

<template>
  <el-dialog
    v-model="visible"
    title="选择人员"
    width="1024px"
    top="5vh"
    append-to-body
    destroy-on-close
    class="bb-user-picker"
    @closed="onClosed"
  >
    <div class="bb-up">
      <!-- 左：机构树 -->
      <aside class="bb-up__left">
        <el-input
          v-model="deptKeyword"
          size="small"
          placeholder="搜索部门"
          clearable
          class="bb-up__dept-search"
          @input="onDeptFilter"
        />
        <div v-loading="treeLoading" class="bb-up__tree">
          <div
            class="bb-up__all"
            :class="{ 'is-active': selectedDeptId == null }"
            data-test="picker-dept-all"
            @click="onDeptClick(null)"
          >
            全部人员
          </div>
          <el-tree
            ref="treeRef"
            :data="tree"
            node-key="id"
            :props="{ label: 'name', children: 'children' }"
            :filter-node-method="filterNode"
            highlight-current
            :expand-on-click-node="false"
            @node-click="(d: Department) => onDeptClick(d)"
          />
        </div>
      </aside>

      <!-- 右：人员表 -->
      <section class="bb-up__right">
        <div class="bb-up__search">
          <el-input
            v-model="keyword"
            placeholder="搜索姓名 / 账号（支持模糊）"
            clearable
            :prefix-icon="Search"
            class="bb-up__search-input"
            data-test="picker-keyword"
            @keyup.enter="onSearch"
            @clear="onSearch"
          />
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
          <span class="bb-up__scope">当前范围：{{ keyword.trim() ? `全机构（关键词「${keyword.trim()}」）` : selectedDeptName }}</span>
        </div>

        <el-table
          ref="tableRef"
          v-loading="loading"
          :data="rows"
          height="380"
          size="small"
          border
          row-key="id"
          data-test="picker-table"
          @selection-change="onSelectionChange"
        >
          <el-table-column type="selection" width="42" />
          <el-table-column prop="name" label="姓名" width="120" show-overflow-tooltip />
          <el-table-column prop="username" label="账号" width="130" show-overflow-tooltip />
          <el-table-column prop="deptName" label="所属部门" min-width="160" show-overflow-tooltip />
        </el-table>

        <div class="bb-up__pager">
          <el-pagination
            layout="total, sizes, prev, pager, next"
            :total="total"
            :page-size="size"
            :current-page="page"
            :page-sizes="[20, 50, 100]"
            @current-change="(p: number) => { page = p; loadUsers() }"
            @size-change="(s: number) => { size = s; page = 1; loadUsers() }"
          />
        </div>
      </section>
    </div>

    <!-- 已选汇总（跨页保留） -->
    <div class="bb-up__picked">
      <span class="bb-up__picked-label">
        已选 <b>{{ pickedList.length }}</b> 人
        <el-button v-if="pickedList.length" text size="small" type="danger" @click="clearPicked">清空</el-button>
      </span>
      <div class="bb-up__picked-list">
        <el-tag
          v-for="u in pickedList"
          :key="u.id"
          closable
          size="small"
          type="info"
          @close="removePicked(Number(u.id))"
        >
          {{ u.name }}
        </el-tag>
        <span v-if="!pickedList.length" class="bb-up__picked-empty">尚未选择</span>
      </div>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" data-test="picker-confirm" @click="onConfirm">确定</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.bb-up {
  display: flex;
  gap: 12px;
  min-height: 430px;
}
.bb-up__left {
  display: flex;
  flex-direction: column;
  flex: 0 0 260px;
  width: 260px;
  min-width: 0;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--bb-radius-sm);
  overflow: hidden;
}
.bb-up__dept-search {
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.bb-up__tree {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 6px;
}
.bb-up__all {
  padding: 5px 8px;
  margin-bottom: 4px;
  border-radius: 4px;
  font-size: 13px;
  cursor: pointer;
}
.bb-up__all:hover {
  background: var(--bg-hover);
}
.bb-up__all.is-active {
  background: var(--bg-active);
  font-weight: 600;
}
.bb-up__right {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
}
.bb-up__search {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.bb-up__search-input {
  width: 300px;
}
.bb-up__scope {
  margin-left: auto;
  font-size: 12px;
  color: var(--bb-text-muted);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.bb-up__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}
.bb-up__picked {
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px solid var(--el-border-color-lighter);
  font-size: 13px;
}
.bb-up__picked-label {
  margin-right: 8px;
  color: var(--bb-text-secondary);
}
.bb-up__picked-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  max-height: 66px;
  margin-top: 6px;
  overflow-y: auto;
}
.bb-up__picked-empty {
  color: var(--bb-text-muted);
  font-size: 12px;
}
</style>
