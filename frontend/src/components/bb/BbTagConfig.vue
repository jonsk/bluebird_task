<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Close, Plus, PriceTag } from '@element-plus/icons-vue'
import { createTag, deleteTag, updateTag, type TagVO } from '@/api/tag'
import { useMetaStore } from '@/stores/meta'

/**
 * 标签入口（旧 `.r-b-t-r-item` → `.el-dropdown`，弹层 w-270）：
 * 每行「图标 + 可编辑名称输入框 + 删除」，末尾一行「输入要添加的标签名称」。
 */
const emit = defineEmits<{ (e: 'changed'): void }>()
const metaStore = useMetaStore()
const newName = ref('')

function err(e: unknown): string {
  return (e as Error)?.message || '操作失败'
}

async function onCreate(): Promise<void> {
  const name = newName.value.trim()
  if (!name) return
  try {
    await createTag(name)
    newName.value = ''
    await metaStore.loadTags(true)
    ElMessage.success('已添加标签')
    emit('changed')
  } catch (e) {
    ElMessage.error(err(e))
  }
}

async function onRename(tag: TagVO, value: string): Promise<void> {
  const name = value.trim()
  if (!name || name === tag.name) return
  try {
    await updateTag(Number(tag.id), name)
    await metaStore.loadTags(true)
    ElMessage.success('已重命名')
    emit('changed')
  } catch (e) {
    ElMessage.error(err(e))
  }
}

async function onDelete(tag: TagVO): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认删除标签「${tag.name}」？`, '删除标签', { type: 'warning', confirmButtonText: '删除' })
  } catch {
    return
  }
  try {
    await deleteTag(Number(tag.id))
    await metaStore.loadTags(true)
    ElMessage.success('已删除')
    emit('changed')
  } catch (e) {
    ElMessage.error(err(e))
  }
}
</script>

<template>
  <el-dropdown trigger="click" :hide-on-click="false" @visible-change="(v: boolean) => v && metaStore.loadTags(true)">
    <div class="r-b-t-r-item">
      <el-icon class="r-b-t-r-icon"><PriceTag /></el-icon>
      <span>标签</span>
    </div>
    <template #dropdown>
      <el-dropdown-menu class="bb-tag-config">
        <el-dropdown-item v-for="t in metaStore.tags" :key="t.id" :divided="false">
          <div class="bb-tag-config__row" @click.stop>
            <div class="bb-tag-config__left">
              <el-icon class="bb-tag-config__icon"><PriceTag /></el-icon>
              <el-input
                :model-value="t.name"
                class="bb-tag-config__input"
                size="small"
                placeholder="输入要修改的标签名称"
                @keyup.enter="onRename(t, ($event.target as HTMLInputElement).value)"
                @blur="onRename(t, ($event.target as HTMLInputElement).value)"
              />
            </div>
            <el-icon class="bb-tag-config__del" title="删除" @click.stop="onDelete(t)"><Close /></el-icon>
          </div>
        </el-dropdown-item>

        <el-dropdown-item>
          <div class="bb-tag-config__row" @click.stop>
            <div class="bb-tag-config__left">
              <el-icon class="bb-tag-config__icon"><Plus /></el-icon>
              <el-input
                v-model="newName"
                class="bb-tag-config__input"
                size="small"
                placeholder="输入要添加的标签名称"
                @keyup.enter="onCreate"
              />
            </div>
            <span class="bb-tag-config__add" @click.stop="onCreate">添加</span>
          </div>
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<style scoped>
.r-b-t-r-item {
  display: flex;
  align-items: center;
  cursor: pointer;
  color: var(--bb-text-secondary);
}
.r-b-t-r-icon {
  margin-right: 6px;
  font-size: 16px;
}
.bb-tag-config {
  width: 270px;
}
.bb-tag-config__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 5px 0;
}
.bb-tag-config__left {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0;
}
.bb-tag-config__icon {
  margin-right: 5px;
}
.bb-tag-config__input {
  width: 180px;
}
.bb-tag-config__del {
  cursor: pointer;
  color: var(--bb-text-muted);
}
.bb-tag-config__add {
  font-size: 12px;
  color: var(--bg-accent);
  cursor: pointer;
}
</style>
