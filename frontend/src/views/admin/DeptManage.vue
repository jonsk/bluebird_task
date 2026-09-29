<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createDepartment, deleteDepartment, listDepartments, updateDepartment, type Department } from '@/api/dept'
import { useMetaStore } from '@/stores/meta'

/**
 * 部门管理（03 §5.4，02 §3.4）。ADMIN/USER_MANAGER：维护部门树与负责人。
 */
const meta = useMetaStore()
const loading = ref(false)
const tree = ref<Department[]>([])
const dialogOpen = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({ name: '', parentId: null as number | null, leaderId: null as number | null })

async function load(): Promise<void> {
  loading.value = true
  try {
    tree.value = await listDepartments()
    await meta.loadDepartments(true)
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await meta.searchUsers('')
  await load()
})

function openCreate(parent?: Department): void {
  editingId.value = null
  Object.assign(form, { name: '', parentId: parent ? Number(parent.id) : null, leaderId: null })
  dialogOpen.value = true
}

function openEdit(node: Department): void {
  editingId.value = Number(node.id)
  Object.assign(form, { name: node.name ?? '', parentId: node.parentId ?? null, leaderId: node.leaderId ?? null })
  dialogOpen.value = true
}

async function save(): Promise<void> {
  if (!form.name.trim()) {
    ElMessage.warning('请填写部门名称')
    return
  }
  try {
    if (editingId.value == null) {
      await createDepartment({ name: form.name, parentId: form.parentId, leaderId: form.leaderId })
    } else {
      await updateDepartment(editingId.value, { name: form.name, parentId: form.parentId, leaderId: form.leaderId })
    }
    ElMessage.success('已保存')
    dialogOpen.value = false
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message || '保存失败')
  }
}

async function onDelete(node: Department): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认删除部门「${node.name}」？`, '删除部门', { type: 'warning' })
    await deleteDepartment(Number(node.id))
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error((e as Error).message || '删除失败')
  }
}
</script>

<template>
  <div class="dept-manage">
    <header class="dept-manage__header">
      <h2>部门管理</h2>
      <el-button type="primary" @click="openCreate()">新建顶级部门</el-button>
    </header>

    <el-tree v-loading="loading" :data="tree" node-key="id" default-expand-all :props="{ label: 'name', children: 'children' }">
      <template #default="{ data }">
        <div class="dept-manage__node">
          <span>{{ data.name }}</span>
          <span class="dept-manage__actions">
            <el-button text size="small" type="primary" @click.stop="openCreate(data)">加子级</el-button>
            <el-button text size="small" @click.stop="openEdit(data)">编辑</el-button>
            <el-button text size="small" type="danger" @click.stop="onDelete(data)">删除</el-button>
          </span>
        </div>
      </template>
    </el-tree>

    <el-dialog v-model="dialogOpen" :title="editingId == null ? '新建部门' : '编辑部门'" width="420px">
      <el-form label-width="80px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="负责人">
          <el-select v-model="form.leaderId" clearable filterable placeholder="选择负责人" style="width: 100%">
            <el-option v-for="u in meta.users" :key="u.id" :label="u.name" :value="Number(u.id)" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogOpen = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.dept-manage {
  padding: 16px 20px;
}
.dept-manage__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.dept-manage__header h2 {
  margin: 0;
  font-size: 18px;
}
.dept-manage__node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding-right: 8px;
}
.dept-manage__actions {
  display: none;
}
.dept-manage__node:hover .dept-manage__actions {
  display: inline-flex;
  gap: 4px;
}
</style>
