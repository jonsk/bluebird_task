<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createDepartment, deleteDepartment, listDepartments, updateDepartment, exportDepartments, downloadDeptTemplate, importDepartments, type Department, type DeptImportResult } from '@/api/dept'
import {
  listUsers,
  createUser,
  updateUser,
  disableUser,
  updatePassword,
  type UserVO,
  type UserCreateReq,
} from '@/api/user'
import { useMetaStore } from '@/stores/meta'
import { ROLE_CODE, ROLE_DESC } from '@/utils/constants'

/** 角色下拉项：固定四值枚举（ADR-003 无角色表，角色不可新增/修改）。 */
const roleOptions = Object.values(ROLE_CODE)

/**
 * 组织管理（item 6：用户管理 + 部门管理合并为「左树右表」）。
 * 左栏部门树（选中部门过滤右表用户），右栏用户表（新建/编辑/禁用/重置密码）。
 * ADMIN/USER_MANAGER 可访问。
 */
const meta = useMetaStore()

const tree = ref<Department[]>([])
const treeLoading = ref(false)
/** 选中的部门 id；null = 全部部门。 */
const selectedDept = ref<number | null>(null)
const selectedName = ref('全部部门')

const loading = ref(false)
const rows = ref<UserVO[]>([])
const total = ref(0)
const query = reactive({ keyword: '', page: 1, size: 20 })

const userDialogOpen = ref(false)
const editingId = ref<number | null>(null)
const userForm = reactive({
  username: '',
  name: '',
  deptId: null as number | null,
  roleCode: 'COMMON' as string,
  password: '',
  mobile: '',
  email: '',
})

const pwdOpen = ref(false)
const pwdUser = ref<UserVO | null>(null)
const pwdForm = reactive({ newPassword: '' })

const deptDialogOpen = ref(false)
const deptEditingId = ref<number | null>(null)
const deptForm = reactive({ name: '', parentId: null as number | null, leaderId: null as number | null })

// ── 部门 Excel 导入 / 导出 ──
const importOpen = ref(false)
const importing = ref(false)
const exporting = ref(false)
const tplLoading = ref(false)
const fileInput = ref<HTMLInputElement>()
const importResult = ref<DeptImportResult | null>(null)

function openImport(): void {
  importResult.value = null
  importOpen.value = true
}

async function onDownloadTemplate(): Promise<void> {
  tplLoading.value = true
  try {
    const name = await downloadDeptTemplate()
    ElMessage.success(`已下载「${name}」`)
  } catch (e) {
    ElMessage.error((e as Error).message || '模板下载失败')
  } finally {
    tplLoading.value = false
  }
}

async function onExport(): Promise<void> {
  exporting.value = true
  try {
    const name = await exportDepartments()
    ElMessage.success(`已导出「${name}」`)
  } catch (e) {
    ElMessage.error((e as Error).message || '导出失败')
  } finally {
    exporting.value = false
  }
}

async function onPickImportFile(e: Event): Promise<void> {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = '' // 允许重复选择同一个文件
  if (!file) return
  importing.value = true
  try {
    const r = await importDepartments(file)
    importResult.value = r
    if (r.ok) {
      await loadTree()
      await loadUsers()
      ElMessage.success(`导入成功：新增 ${r.created} 个、更新 ${r.updated} 个`)
    } else {
      ElMessage.warning(`校验未通过：${r.failed} 行有误，本次未导入任何数据`)
    }
  } catch (err) {
    ElMessage.error((err as Error).message || '导入失败')
  } finally {
    importing.value = false
  }
}

/** 部门 id 统一归一为 number：左树与用户对话框（树选择）共用同一份数据。 */
function normalizeDept(list: Department[]): Department[] {
  return list.map((d) => ({ ...d, id: Number(d.id), children: d.children?.length ? normalizeDept(d.children) : d.children }))
}

async function loadTree(): Promise<void> {
  treeLoading.value = true
  try {
    tree.value = normalizeDept(await listDepartments())
    await meta.loadDepartments(true)
  } finally {
    treeLoading.value = false
  }
}

async function loadUsers(): Promise<void> {
  loading.value = true
  try {
    const page = await listUsers({
      keyword: query.keyword,
      deptId: selectedDept.value ?? undefined,
      page: query.page,
      size: query.size,
    })
    rows.value = page.list
    total.value = page.total
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await meta.searchUsers('')
  await loadTree()
  await loadUsers()
})

// ── 部门树 ──
/**
 * 选中部门过滤右表用户。
 *
 * 「全部部门」根节点已按用户要求移除，故保留「再次点击当前部门＝取消筛选」的切换动作——
 * 不额外增加界面元素，但「看全部用户」的能力仍在。
 */
function selectDept(dept: Department | null): void {
  const clicked = dept ? Number(dept.id) : null
  if (dept && clicked === selectedDept.value) {
    selectedDept.value = null
    selectedName.value = '全部部门'
  } else if (dept) {
    selectedDept.value = clicked
    selectedName.value = dept.name ?? ''
  } else {
    selectedDept.value = null
    selectedName.value = '全部部门'
  }
  query.page = 1
  void loadUsers()
}

function openCreateDept(parent?: Department): void {
  deptEditingId.value = null
  Object.assign(deptForm, { name: '', parentId: parent ? Number(parent.id) : null, leaderId: null })
  deptDialogOpen.value = true
}

function openEditDept(node: Department): void {
  deptEditingId.value = Number(node.id)
  Object.assign(deptForm, { name: node.name ?? '', parentId: node.parentId ?? null, leaderId: node.leaderId ?? null })
  deptDialogOpen.value = true
}

async function saveDept(): Promise<void> {
  if (!deptForm.name.trim()) {
    ElMessage.warning('请填写部门名称')
    return
  }
  try {
    if (deptEditingId.value == null) {
      await createDepartment({ name: deptForm.name, parentId: deptForm.parentId, leaderId: deptForm.leaderId })
    } else {
      await updateDepartment(deptEditingId.value, { name: deptForm.name, parentId: deptForm.parentId, leaderId: deptForm.leaderId })
    }
    ElMessage.success('已保存')
    deptDialogOpen.value = false
    await loadTree()
  } catch (e) {
    ElMessage.error((e as Error).message || '保存失败')
  }
}

async function onDeleteDept(node: Department): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认删除部门「${node.name}」？`, '删除部门', { type: 'warning' })
    await deleteDepartment(Number(node.id))
    if (selectedDept.value === Number(node.id)) selectDept(null)
    ElMessage.success('已删除')
    await loadTree()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error((e as Error).message || '删除失败')
  }
}

// ── 用户表 ──
function openCreateUser(): void {
  editingId.value = null
  Object.assign(userForm, { username: '', name: '', deptId: selectedDept.value, roleCode: 'COMMON', password: '', mobile: '', email: '' })
  userDialogOpen.value = true
}

function openEditUser(row: UserVO): void {
  editingId.value = Number(row.id)
  Object.assign(userForm, {
    username: row.username ?? '',
    name: row.name ?? '',
    deptId: row.deptId == null ? null : Number(row.deptId),
    roleCode: row.roleCode ?? 'COMMON',
    password: '',
    mobile: row.mobile ?? '',
    email: row.email ?? '',
  })
  userDialogOpen.value = true
}

async function saveUser(): Promise<void> {
  // 用户必须归属一个部门（新建与编辑都要求，后端 deptId 必填）。
  if (userForm.deptId == null) {
    ElMessage.warning('请选择部门')
    return
  }
  try {
    const mobile = userForm.mobile.trim() || null
    const email = userForm.email.trim() || null
    if (editingId.value == null) {
      if (!userForm.username || !userForm.name) {
        ElMessage.warning('请填写用户名与姓名')
        return
      }
      const payload: UserCreateReq = {
        username: userForm.username,
        name: userForm.name,
        deptId: userForm.deptId,
        roleCode: userForm.roleCode as UserCreateReq['roleCode'],
        password: userForm.password || null,
        mobile,
        email,
      }
      await createUser(payload)
      ElMessage.success('已创建（首登须改密）')
    } else {
      await updateUser(editingId.value, {
        name: userForm.name,
        deptId: userForm.deptId,
        roleCode: userForm.roleCode as UserCreateReq['roleCode'],
        mobile,
        email,
      })
      ElMessage.success('已保存')
    }
    userDialogOpen.value = false
    await loadUsers()
    await meta.searchUsers('')
  } catch (e) {
    ElMessage.error((e as Error).message || '保存失败')
  }
}

async function onDisableUser(row: UserVO): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认禁用用户「${row.name}」？`, '禁用用户', { type: 'warning' })
    await disableUser(Number(row.id))
    ElMessage.success('已禁用')
    await loadUsers()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error((e as Error).message || '操作失败')
  }
}

function openPwd(row: UserVO): void {
  pwdUser.value = row
  pwdForm.newPassword = ''
  pwdOpen.value = true
}

async function savePwd(): Promise<void> {
  if (!pwdUser.value || !pwdForm.newPassword) return
  try {
    await updatePassword(Number(pwdUser.value.id), { newPassword: pwdForm.newPassword })
    ElMessage.success('密码已重置')
    pwdOpen.value = false
  } catch (e) {
    ElMessage.error((e as Error).message || '重置失败')
  }
}
</script>

<template>
  <div class="org-manage">
    <div class="org-manage__left">
      <header class="org-manage__left-head">
        <h2>部门</h2>
        <div class="org-manage__left-actions">
          <el-button size="small" @click="openImport" data-test="dept-import-open">导入</el-button>
          <el-button size="small" type="primary" :loading="exporting" data-test="dept-export" @click="onExport">
            导出
          </el-button>
        </div>
      </header>
      <div class="org-manage__tree" v-loading="treeLoading">
        <el-tree
          :data="tree"
          node-key="id"
          default-expand-all
          :props="{ label: 'name', children: 'children' }"
          :current-node-key="selectedDept ?? undefined"
          highlight-current
          @node-click="(d: Department) => selectDept(d)"
        >
          <template #default="{ data }">
            <div class="org-manage__node">
              <span>{{ data.name }}</span>
              <el-tag v-if="data.system" size="small" effect="plain" type="info" class="org-manage__system-tag">
                系统默认
              </el-tag>
              <span class="org-manage__node-actions">
                <el-button text size="small" type="primary" @click.stop="openCreateDept(data)">加子级</el-button>
                <el-button text size="small" @click.stop="openEditDept(data)">编辑</el-button>
                <!-- 系统默认顶级部门可改名、不可删除（后端同样拒绝），故不提供删除入口 -->
                <el-button
                  v-if="!data.system"
                  text
                  size="small"
                  type="danger"
                  @click.stop="onDeleteDept(data)"
                >
                  删除
                </el-button>
              </span>
            </div>
          </template>
        </el-tree>
      </div>
    </div>

    <div class="org-manage__right">
      <header class="org-manage__right-head">
        <h2>用户管理 <span class="org-manage__right-sub">{{ selectedName }}</span></h2>
        <div class="org-manage__filters">
          <el-input v-model="query.keyword" placeholder="用户名/姓名" clearable style="width: 200px" @keyup.enter="loadUsers" @clear="loadUsers" />
          <el-button @click="loadUsers">查询</el-button>
          <el-button type="primary" @click="openCreateUser">新建用户</el-button>
        </div>
      </header>

      <el-table v-loading="loading" :data="rows" border stripe size="small">
        <el-table-column prop="username" label="用户名" width="130" />
        <el-table-column prop="name" label="姓名" width="110" />
        <el-table-column prop="deptName" label="部门" width="140" />
        <el-table-column prop="mobile" label="手机" width="130" />
        <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
        <el-table-column prop="roleCode" label="角色" width="130" />
        <el-table-column prop="status" label="状态" width="90" />
        <el-table-column label="操作" min-width="190">
          <template #default="{ row }">
            <el-button text size="small" type="primary" @click="openEditUser(row)">编辑</el-button>
            <el-button text size="small" @click="openPwd(row)">重置密码</el-button>
            <el-button text size="small" type="danger" @click="onDisableUser(row)">禁用</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="org-manage__pager">
        <el-pagination
          layout="total, prev, pager, next"
          :total="total"
          :page-size="query.size"
          :current-page="query.page"
          @current-change="(p: number) => { query.page = p; loadUsers() }"
        />
      </div>
    </div>

    <el-dialog v-model="deptDialogOpen" :title="deptEditingId == null ? '新建部门' : '编辑部门'" width="420px">
      <el-form label-width="80px">
        <el-form-item label="名称"><el-input v-model="deptForm.name" /></el-form-item>
        <el-form-item label="负责人">
          <el-select v-model="deptForm.leaderId" clearable filterable placeholder="选择负责人" style="width: 100%">
            <el-option v-for="u in meta.users" :key="u.id" :label="u.name" :value="Number(u.id)" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="deptDialogOpen = false">取消</el-button>
        <el-button type="primary" @click="saveDept">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="importOpen" title="导入部门" width="600px" data-test="dept-import-dialog">
      <div class="dept-import">
        <p class="dept-import__tip">
          ① 先下载模板 → ② 按模板填写（先父后子）→ ③ 选择文件导入。导入为<b>先全量校验、后落库</b>：
          只要有一行不合格，整份都不会导入。
        </p>
        <div class="dept-import__row">
          <el-button :loading="tplLoading" data-test="dept-import-template" @click="onDownloadTemplate">
            下载导入模板
          </el-button>
          <el-button
            type="primary"
            :loading="importing"
            data-test="dept-import-pick"
            @click="fileInput?.click()"
          >
            选择文件并导入
          </el-button>
          <input ref="fileInput" class="dept-import__file" type="file" accept=".xlsx" @change="onPickImportFile" />
        </div>

        <div v-if="importResult" class="dept-import__result" data-test="dept-import-result">
          <el-alert
            v-if="importResult.ok"
            type="success"
            :closable="false"
            show-icon
            :title="`导入成功：共 ${importResult.total} 行，新增 ${importResult.created} 个、更新 ${importResult.updated} 个部门`"
          />
          <template v-else>
            <el-alert
              type="error"
              :closable="false"
              show-icon
              :title="`校验未通过，本次未导入任何数据：共 ${importResult.total} 行，${importResult.failed} 行有误`"
            />
            <el-table :data="importResult.errors" size="small" border max-height="240" class="dept-import__errors">
              <el-table-column prop="row" label="行号" width="80" />
              <el-table-column prop="message" label="原因" />
            </el-table>
          </template>
        </div>
      </div>
      <template #footer>
        <el-button @click="importOpen = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="userDialogOpen" :title="editingId == null ? '新建用户' : '编辑用户'" width="500px">
      <el-form label-width="80px">
        <el-form-item label="用户名">
          <el-input v-model="userForm.username" :disabled="editingId != null" />
        </el-form-item>
        <el-form-item label="姓名"><el-input v-model="userForm.name" /></el-form-item>
        <el-form-item label="部门" required>
          <el-tree-select
            v-model="userForm.deptId"
            :data="tree"
            :props="{ label: 'name', children: 'children' }"
            node-key="id"
            check-strictly
            default-expand-all
            :render-after-expand="false"
            clearable
            placeholder="请选择部门"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="手机号"><el-input v-model="userForm.mobile" placeholder="选填" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="userForm.email" placeholder="选填" /></el-form-item>
        <el-form-item label="角色">
          <el-select v-model="userForm.roleCode" style="width: 100%">
            <el-option v-for="code in roleOptions" :key="code" :label="code" :value="code">
              <span class="org-manage__role-code">{{ code }}</span>
              <span class="org-manage__role-desc"> — {{ ROLE_DESC[code] }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label=" ">
          <div class="org-manage__hint">角色为系统固定枚举（仅上列四项），用于划分功能权限，不可新增或修改。</div>
        </el-form-item>
        <el-form-item v-if="editingId == null" label="初始密码">
          <el-input v-model="userForm.password" type="password" placeholder="留空则首登强制改密" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="userDialogOpen = false">取消</el-button>
        <el-button type="primary" @click="saveUser">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="pwdOpen" title="重置密码" width="380px">
      <el-form label-width="80px">
        <el-form-item label="新密码">
          <el-input v-model="pwdForm.newPassword" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdOpen = false">取消</el-button>
        <el-button type="primary" @click="savePwd">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.org-manage {
  display: flex;
  gap: 16px;
  height: calc(100vh - 80px);
  padding: 16px 20px;
  box-sizing: border-box;
}
.org-manage__left {
  display: flex;
  flex-direction: column;
  flex: 0 0 300px;
  width: 300px;
  min-width: 0;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--bb-radius-sm);
  overflow: hidden;
}
.org-manage__left-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.org-manage__left-head h2 {
  margin: 0;
  font-size: 15px;
}
.org-manage__left-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.org-manage__left-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}
.dept-import__tip {
  margin: 0 0 12px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--bb-text-secondary);
}
.dept-import__row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.dept-import__file {
  display: none;
}
.dept-import__result {
  margin-top: 16px;
}
.dept-import__errors {
  margin-top: 8px;
}
.org-manage__tree {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 8px;
}
.org-manage__node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding-right: 8px;
}
/* 紧随部门名，右侧留白把操作按钮推到最右 */
.org-manage__system-tag {
  margin-left: 6px;
  margin-right: auto;
  flex-shrink: 0;
}
.org-manage__node-actions {
  display: none;
}
.org-manage__node:hover .org-manage__node-actions {
  display: inline-flex;
  gap: 4px;
}
.org-manage__right {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
}
.org-manage__right-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.org-manage__right-head h2 {
  margin: 0;
  font-size: 18px;
}
.org-manage__right-sub {
  font-size: 13px;
  font-weight: 400;
  color: var(--bb-text-muted);
}
.org-manage__filters {
  display: flex;
  gap: 8px;
}
.org-manage__pager {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
.org-manage__role-code {
  font-weight: 600;
}
.org-manage__role-desc,
.org-manage__hint {
  font-size: 12px;
  line-height: 1.5;
  color: var(--bb-text-muted);
}
</style>
