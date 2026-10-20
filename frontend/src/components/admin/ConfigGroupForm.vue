<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { saveConfigGroup, type ConfigGroupVO, type ConfigItemVO } from '@/api/adminConfig'

/**
 * 通用配置分组表单：完全由后端 `GET /admin/config/{group}` 的描述驱动
 * （`ConfigDefinition` 是唯一权威源），前端不硬编码任何配置项。
 *
 * 密文项语义：读取时 value 恒为掩码 `******`；提交时若用户未改动则回传掩码，
 * 后端识别为「不修改」。用户主动清空 → 提交空串 → 后端清空该项。
 */
const props = defineProps<{ group: ConfigGroupVO }>()
const emit = defineEmits<{ (e: 'saved', g: ConfigGroupVO): void }>()

const saving = ref(false)
const draft = reactive<Record<string, string>>({})

function reset(items: ConfigItemVO[]): void {
  for (const item of items) {
    draft[item.key] = item.value ?? ''
  }
}

watch(
  () => props.group,
  (g) => reset(g.items),
  { immediate: true, deep: false },
)

const restartPending = computed(() =>
  props.group.items.some((i) => i.requiresRestart && (draft[i.key] ?? '') !== (i.value ?? '')),
)

function sourceLabel(item: ConfigItemVO): string {
  if (item.source === 'DB') return '已保存'
  if (item.source === 'YML') return '配置文件'
  return '默认值'
}

function sourceType(item: ConfigItemVO): 'success' | 'warning' | 'info' {
  if (item.source === 'DB') return 'success'
  if (item.source === 'YML') return 'warning'
  return 'info'
}

/** 布尔项在界面上用 switch，统一转换 true/false ↔ 字符串。 */
function boolValue(key: string): boolean {
  return draft[key] === 'true'
}

function setBool(key: string, v: boolean): void {
  draft[key] = v ? 'true' : 'false'
}

async function onSave(): Promise<void> {
  saving.value = true
  try {
    const values: Record<string, string | null> = {}
    for (const item of props.group.items) {
      const v = draft[item.key]
      // 未填写 / 保持掩码 → 不修改（后端同样会把 null 与掩码视为不变）
      if (v === undefined || v === null) continue
      if (item.secret && (v === '' || v === '******') && item.hasSecret) continue
      values[item.key] = v
    }
    const updated = await saveConfigGroup(props.group.group, values)
    emit('saved', updated)
    ElMessage.success('已保存')
  } catch (e) {
    ElMessage.error((e as Error).message || '保存失败')
  } finally {
    saving.value = false
  }
}

function onReset(): void {
  reset(props.group.items)
}
</script>

<template>
  <section class="cfg">
    <header class="cfg__head">
      <div>
        <h3 class="cfg__title">{{ group.label }}</h3>
        <p v-if="group.description" class="cfg__desc">{{ group.description }}</p>
      </div>
      <div class="cfg__actions">
        <el-button size="small" @click="onReset">重置</el-button>
        <el-button size="small" type="primary" :loading="saving" :data-test="`cfg-save-${group.group}`" @click="onSave">
          保存
        </el-button>
      </div>
    </header>

    <el-form label-width="140px" label-position="right" class="cfg__form">
      <el-form-item v-for="item in group.items" :key="item.key">
        <template #label>
          <span class="cfg__label">
            {{ item.label }}
            <el-tooltip v-if="item.description" :content="item.description" placement="top" effect="light">
              <span class="cfg__hint">?</span>
            </el-tooltip>
          </span>
        </template>

        <div class="cfg__row">
          <el-select
            v-if="item.type === 'SELECT'"
            v-model="draft[item.key]"
            class="cfg__control"
            :data-test="`cfg-${item.key}`"
          >
            <el-option v-for="opt in item.options ?? []" :key="opt" :label="opt" :value="opt" />
          </el-select>

          <el-switch
            v-else-if="item.type === 'BOOL'"
            :model-value="boolValue(item.key)"
            :data-test="`cfg-${item.key}`"
            @update:model-value="(v: string | number | boolean) => setBool(item.key, Boolean(v))"
          />

          <el-input
            v-else-if="item.type === 'INT'"
            v-model="draft[item.key]"
            type="number"
            class="cfg__control cfg__control--narrow"
            :data-test="`cfg-${item.key}`"
          />

          <el-input
            v-else-if="item.type === 'JSON'"
            v-model="draft[item.key]"
            type="textarea"
            :rows="4"
            class="cfg__control"
            :data-test="`cfg-${item.key}`"
          />

          <el-input
            v-else
            v-model="draft[item.key]"
            :type="item.secret ? 'password' : 'text'"
            :show-password="item.secret"
            :placeholder="item.secret && item.hasSecret ? '已配置，留空表示不修改' : ''"
            class="cfg__control"
            :data-test="`cfg-${item.key}`"
          />

          <el-tag size="small" :type="sourceType(item)" effect="plain" class="cfg__source">
            {{ sourceLabel(item) }}
          </el-tag>
          <el-tag v-if="item.requiresRestart" size="small" type="warning" effect="plain">需重启</el-tag>
        </div>
      </el-form-item>
    </el-form>

    <p v-if="restartPending" class="cfg__warn">该分组含「需重启」项，修改后请重启服务生效。</p>
  </section>
</template>

<style scoped>
.cfg {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--bb-radius-sm);
  background: var(--bg-primary);
}
.cfg__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.cfg__title {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  color: var(--bb-text-primary);
}
.cfg__desc {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--bb-text-muted);
}
.cfg__actions {
  display: flex;
  flex: 0 0 auto;
  gap: 8px;
}
.cfg__form {
  padding: 16px 16px 0;
}
.cfg__form :deep(.el-form-item) {
  margin-bottom: 16px;
}
.cfg__label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
}
.cfg__hint {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: var(--el-border-color-lighter);
  font-size: 10px;
  color: var(--bb-text-muted);
  cursor: help;
}
.cfg__row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  width: 100%;
}
.cfg__control {
  flex: 0 1 420px;
  min-width: 220px;
}
.cfg__control--narrow {
  flex: 0 1 140px;
  min-width: 120px;
}
.cfg__source {
  margin-top: 5px;
}
.cfg__warn {
  margin: 0;
  padding: 0 16px 14px;
  font-size: 12px;
  color: var(--el-color-warning);
}
</style>
