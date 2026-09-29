<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listUsers, createUser, updateUser, disableUser, updatePassword, type UserVO, type UserCreateReq } from '@/api/user'
import { useMetaStore } from '@/stores/meta'
import { ROLE_CODE } from '@/utils/constants'

/**
 * 用户管理（03 §5.4）。ADMIN/USER_MANAGER：新建/编辑/禁用/重置密码。
 */
const meta = useMetaStore()

const loading = ref(false)
const rows = ref<UserVO[]>([])
const total = ref(0)
const query = reactive({ keyword: '', deptId: null as number | null, page: 1, size: 20 })

const dialogOpen = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({ username: '', name: '', deptId: null as number | null, roleCode: 'COMMON' as string, password: '' })

const pwdOpen = ref(false)
const pwdUser = ref<UserVO | null>(null)
const pwdForm = reactive({ newPassword: '' })

async function load(): Promise<void> {
  loading.value = true
  try {
    const page = await listUsers({ keyword: query.keyword, deptId: query.deptId ?? undefined, page: query.page, size: query.size })
    rows.value = page.list
    total.value = page.total
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await meta.loadDepartments()
  await load()
})

function openCreate(): void {
  editingId.value = null
  Object.assign(form, { username: '', name: '', deptId: null, roleCode: 'COMMON', password: '' })
  dialogOpen.value = true
}

function openEdit(row: UserVO): void {
  editingId.value = Number(row.id)
  Object.assign(form, {
    username: row.username ?? '',
    name: row.name ?? '',
    deptId: row.deptId ?? null,
    roleCode: row.roleCode ?? 'COMMON',
    password: '',
  })
  dialogOpen.value = true
}

async function save(): Promise<void> {
  try {
    if (editingId.value == null) {
      if (!form.username || !form.name) {
        ElMessage.warning('请填写用户名与姓名')
        return
      }
      const payload: UserCreateReq = {
        username: form.username,
        name: form.name,
        deptId: form.deptId,
        roleCode: form.roleCode as UserCreateReq['roleCode'],
        password: form.password || null,
      }
      await createUser(payload)
      ElMessage.success('已创建（首登须改密）')
    } else {
      await updateUser(editingId.value, {
        name: form.name,
        deptId: form.deptId,
        roleCode: form.roleCode as UserCreateReq['roleCode'],
      })
      ElMessage.success('已保存')
    }
    dialogOpen.value = false
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message || '保存失败')
  }
}

async function onDisable(row: UserVO): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认禁用用户「${row.name}」？`, '禁用用户', { type: 'warning' })
    await disableUser(Number(row.id))
    ElMessage.success('已禁用')
    await load()
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
  <div class="user-manage">
    <header class="user-manage__header">
      <h2>用户管理</h2>
      <div class="user-manage__filters">
        <el-select v-model="query.deptId" clearable placeholder="全部部门" style="width: 180px" @change="load">
          <el-option v-for="d in meta.flatDepartments()" :key="d.id" :label="d.name" :value="Number(d.id)" />
        </el-select>
        <el-input v-model="query.keyword" placeholder="用户名/姓名" clearable style="width: 200px" @keyup.enter="load" @clear="load" />
        <el-button @click="load">查询</el-button>
        <el-button type="primary" @click="openCreate">新建用户</el-button>
      </div>
    </header>

    <el-table v-loading="loading" :data="rows" border stripe size="small">
      <el-table-column prop="username" label="用户名" width="140" />
      <el-table-column prop="name" label="姓名" width="140" />
      <el-table-column prop="deptName" label="部门" width="160" />
      <el-table-column prop="mobile" label="手机" width="140" />
      <el-table-column prop="roleCode" label="角色" width="130" />
      <el-table-column prop="status" label="状态" width="100" />
      <el-table-column label="操作" min-width="200">
        <template #default="{ row }">
          <el-button text size="small" type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button text size="small" @click="openPwd(row)">重置密码</el-button>
          <el-button text size="small" type="danger" @click="onDisable(row)">禁用</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="user-manage__pager">
      <el-pagination
        layout="total, prev, pager, next"
        :total="total"
        :page-size="query.size"
        :current-page="query.page"
        @current-change="(p: number) => { query.page = p; load() }"
      />
    </div>

    <el-dialog v-model="dialogOpen" :title="editingId == null ? '新建用户' : '编辑用户'" width="460px">
      <el-form label-width="80px">
        <el-form-item label="用户名">
          <el-input v-model="form.username" :disabled="editingId != null" />
        </el-form-item>
        <el-form-item label="姓名"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="部门">
          <el-select v-model="form.deptId" clearable placeholder="未分配" style="width: 100%">
            <el-option v-for="d in meta.flatDepartments()" :key="d.id" :label="d.name" :value="Number(d.id)" />
          </el-select>
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.roleCode" style="width: 100%">
            <el-option v-for="(v, k) in ROLE_CODE" :key="k" :label="v" :value="v" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="editingId == null" label="初始密码">
          <el-input v-model="form.password" type="password" placeholder="留空则首登强制改密" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogOpen = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
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
.user-manage {
  padding: 16px 20px;
}
.user-manage__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.user-manage__header h2 {
  margin: 0;
  font-size: 18px;
}
.user-manage__filters {
  display: flex;
  gap: 8px;
}
.user-manage__pager {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
</style>
