<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listConfigGroups, type ConfigGroupVO } from '@/api/adminConfig'
import ConfigGroupForm from '@/components/admin/ConfigGroupForm.vue'

/**
 * 常规设置（ADMIN）：登录安全 / 审计留存 / 文件存储。
 * 同样由后端配置注册表驱动，前端不硬编码字段。
 */
const GROUP_ORDER = ['security', 'audit', 'storage'] as const

const loading = ref(false)
const groups = ref<ConfigGroupVO[]>([])

const ordered = computed(() =>
  GROUP_ORDER.map((g) => groups.value.find((x) => x.group === g)).filter((g): g is ConfigGroupVO => Boolean(g)),
)

async function load(): Promise<void> {
  loading.value = true
  try {
    groups.value = await listConfigGroups()
  } catch (e) {
    ElMessage.error((e as Error).message || '配置加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)

function onSaved(updated: ConfigGroupVO): void {
  const i = groups.value.findIndex((g) => g.group === updated.group)
  if (i >= 0) groups.value.splice(i, 1, updated)
}
</script>

<template>
  <div v-loading="loading" class="settings">
    <ConfigGroupForm v-for="g in ordered" :key="g.group" :group="g" @saved="onSaved" />
  </div>
</template>

<style scoped>
.settings {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 14px;
  min-height: 0;
}
</style>
