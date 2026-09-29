<script setup lang="ts">
import { computed, nextTick, onMounted, ref, shallowRef } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Close, Edit, Plus } from '@element-plus/icons-vue'
import BbEmpty from './BbEmpty.vue'
import { useMenuStore } from '@/stores/menu'
import type { Menu } from '@/api/menu'

/**
 * BbCustomMenu — 自定义栏（03 §5.3.4）。CRUD `POST/PUT/DELETE /menus`；
 * 点击栏 → emit('select', menuId) 按 `GET /tasks?menuId=` 过滤。
 */
const emit = defineEmits<{ (e: 'select', id: number | null): void; (e: 'changed'): void }>()

const menuStore = useMenuStore()

const activeId = ref<number | null>(null)
const creating = ref(false)
const editingId = ref<number | null>(null)
const draftName = ref('')
const nameInput = shallowRef<{ focus: () => void } | null>(null)

const menus = computed(() => menuStore.menus)

onMounted(() => {
  void menuStore.load()
})

function errMsg(e: unknown): string {
  return (e as Error)?.message || '操作失败'
}

function focusDraft(): void {
  void nextTick(() => nameInput.value?.focus())
}

function select(menu: Menu): void {
  if (menu.id == null) return
  if (creating.value || editingId.value != null) return
  activeId.value = Number(menu.id)
  emit('select', activeId.value)
}

function clearSelection(): void {
  activeId.value = null
  emit('select', null)
}

function startCreate(): void {
  if (creating.value || editingId.value != null) return
  creating.value = true
  draftName.value = ''
  focusDraft()
}

async function commitCreate(): Promise<void> {
  if (!creating.value) return
  const name = draftName.value.trim()
  creating.value = false
  if (!name) return
  try {
    await menuStore.create(name)
    emit('changed')
    ElMessage.success('已新建自定义栏')
  } catch (e) {
    ElMessage.error(errMsg(e))
  }
}

function startEdit(menu: Menu): void {
  if (creating.value || editingId.value != null) return
  editingId.value = Number(menu.id)
  draftName.value = menu.name ?? ''
  focusDraft()
}

async function commitEdit(menu: Menu): Promise<void> {
  if (editingId.value == null) return
  const id = editingId.value
  const name = draftName.value.trim()
  editingId.value = null
  if (!name || name === menu.name) return
  try {
    await menuStore.rename(id, name)
    emit('changed')
    ElMessage.success('已重命名')
  } catch (e) {
    ElMessage.error(errMsg(e))
  }
}

async function onDelete(menu: Menu): Promise<void> {
  const count = menu.items?.length ?? 0
  const tip = count
    ? `「${menu.name}」含 ${count} 个任务，删除仅移除该栏（不影响任务）。确认删除？`
    : `确认删除自定义栏「${menu.name}」？`
  try {
    await ElMessageBox.confirm(tip, '删除自定义栏', { type: 'warning', confirmButtonText: '删除' })
  } catch {
    return
  }
  try {
    await menuStore.remove(Number(menu.id))
    if (activeId.value === Number(menu.id)) clearSelection()
    emit('changed')
    ElMessage.success('已删除')
  } catch (e) {
    ElMessage.error(errMsg(e))
  }
}

defineExpose({ clearSelection, load: () => menuStore.load(true) })
</script>

<template>
  <section class="bb-menu">
    <header class="bb-menu__head">
      <span class="bb-menu__title">自定义栏</span>
      <el-icon class="bb-menu__add" title="新建自定义栏" @click.stop="startCreate"><Plus /></el-icon>
    </header>

    <ul v-if="menus.length || creating" class="bb-menu__list">
      <li
        v-for="m in menus"
        :key="m.id"
        class="bb-menu__item"
        :class="{ 'is-active': Number(m.id) === activeId }"
        @click="select(m)"
      >
        <template v-if="Number(m.id) === editingId">
          <el-input
            ref="nameInput"
            v-model="draftName"
            size="small"
            @keyup.enter="commitEdit(m)"
            @blur="commitEdit(m)"
            @click.stop
          />
        </template>
        <template v-else>
          <span class="bb-menu__name" :title="m.name">{{ m.name }}</span>
          <span class="bb-menu__count">{{ m.items?.length ?? 0 }}</span>
          <span class="bb-menu__ops">
            <el-icon class="bb-menu__op" title="重命名" @click.stop="startEdit(m)"><Edit /></el-icon>
            <el-icon class="bb-menu__op" title="删除" @click.stop="onDelete(m)"><Close /></el-icon>
          </span>
        </template>
      </li>
      <li v-if="creating" class="bb-menu__item">
        <el-input
          ref="nameInput"
          v-model="draftName"
          size="small"
          placeholder="栏名称，回车保存"
          @keyup.enter="commitCreate"
          @blur="commitCreate"
        />
      </li>
    </ul>

    <BbEmpty v-if="!menus.length && !creating" description="暂无自定义栏" />
  </section>
</template>

<style scoped>
.bb-menu {
  display: flex;
  flex-direction: column;
  min-height: 0;
  min-width: 0;
}
.bb-menu__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 2px 2px 6px;
}
.bb-menu__title {
  font-size: 13px;
  font-weight: 600;
  color: #1f2430;
}
.bb-menu__add {
  cursor: pointer;
  color: var(--bb-color-primary);
}
.bb-menu__list {
  margin: 0;
  padding: 0;
  list-style: none;
}
.bb-menu__item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  border-radius: var(--bb-radius);
  font-size: 13px;
  cursor: pointer;
  min-width: 0;
}
.bb-menu__item:hover {
  background: #f5f6f8;
}
.bb-menu__item.is-active {
  background: #eef3ff;
  color: var(--bb-color-primary);
}
.bb-menu__name {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.bb-menu__count {
  margin-left: auto;
  font-size: 12px;
  color: var(--bb-color-muted);
}
.bb-menu__ops {
  display: none;
  align-items: center;
  gap: 6px;
}
.bb-menu__item:hover .bb-menu__ops {
  display: inline-flex;
}
.bb-menu__op {
  cursor: pointer;
  color: var(--bb-color-muted);
}
.bb-menu__op:hover {
  color: var(--bb-color-primary);
}
</style>
