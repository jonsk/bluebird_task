<script setup lang="ts">
import { type Component } from 'vue'
import { CircleClose } from '@element-plus/icons-vue'

/**
 * 日期时间选择器（到期 / 提醒共用）：封装 `el-date-picker` + 清除图标，
 * 抽出原 `BbTaskDetailPanel` 里到期、提醒两段重复的「图标 + 选择器 + 清除」结构。
 *
 * <p>值走本地格式 `YYYY-MM-DD HH:mm:ss`（`value-format` 保证入参出参一致），
 * 父级拿到后自行 `toOffsetIso` 转契约格式；清除时回调 null。
 */
withDefaults(
  defineProps<{
    /** 当前值（本地格式 YYYY-MM-DD HH:mm:ss）。 */
    modelValue?: string
    /** 是否禁用（无写权限时）。 */
    disabled?: boolean
    /** 占位文案。 */
    placeholder?: string
    /** 前置图标组件（如 Calendar / Bell）。 */
    icon?: Component
    /** 有值且可写时是否显示清除按钮。 */
    showClear?: boolean
  }>(),
  { modelValue: '', disabled: false, placeholder: '', icon: undefined, showClear: true },
)

const emit = defineEmits<{
  /** 值变更（本地格式字符串）；清除时为 null。 */
  (e: 'change', value: string | null): void
}>()
</script>

<template>
  <div class="drbb-item drbb-item-d drbb-shadow1 drbbi-two">
    <el-icon v-if="icon" class="drbbi-icon"><component :is="icon" /></el-icon>
    <el-date-picker
      :model-value="modelValue"
      type="datetime"
      class="drbb-plainpicker"
      :placeholder="placeholder"
      :disabled="disabled"
      value-format="YYYY-MM-DD HH:mm:ss"
      @update:model-value="(v: string | null) => emit('change', v ?? null)"
    />
    <el-icon
      v-if="showClear && modelValue && !disabled"
      class="drbb-clear"
      title="清除"
      @click="emit('change', null)"
    ><CircleClose /></el-icon>
  </div>
</template>

<style scoped>
.drbb-item {
  background: var(--bg-primary);
}
.drbb-shadow1 {
  box-shadow: var(--bb-shadow-flat);
}
.drbbi-two {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 44px;
  margin-top: 10px;
  padding: 6px 12px;
  box-sizing: border-box;
}
.drbbi-icon {
  margin-right: 20px;
  font-size: 18px;
  color: var(--bb-text-secondary);
}
.drbb-clear {
  margin-left: 10px;
  cursor: pointer;
  color: var(--bb-text-muted);
}
.drbb-plainpicker {
  flex: 1;
}
.drbb-plainpicker :deep(.el-input__wrapper) {
  box-shadow: none;
  background: transparent;
}
</style>
