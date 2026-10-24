<script setup lang="ts">
import { computed } from 'vue'
import type { TagOption } from '@/types/tag'

/**
 * BbTagPicker — 标签多选（从 BbTaskComposer 抽取）。
 * 不依赖 store；纯展示 + v-model 绑定 number[]。
 * options 为 undefined 时表示标签尚未就绪，禁用并提示。
 */
const props = withDefaults(
  defineProps<{
    options?: TagOption[]
    modelValue: number[]
    disabled?: boolean
  }>(),
  { options: undefined, disabled: false },
)

const emit = defineEmits<{ (e: 'update:modelValue', value: number[]): void }>()

const isLoading = computed(() => props.options === undefined)
const selectable = computed(() => (props.options ?? []).filter((o) => o.id != null) as Required<TagOption>[])

function onUpdate(value: number[]): void {
  emit('update:modelValue', value ?? [])
}
</script>

<template>
  <div class="bb-tag-picker">
    <el-select
      :model-value="modelValue"
      multiple
      filterable
      collapse-tags
      collapse-tags-tooltip
      placeholder="选择标签"
      :disabled="disabled || isLoading"
      class="bb-tag-picker__select"
      @update:model-value="onUpdate"
    >
      <el-option v-for="opt in selectable" :key="opt.id" :label="opt.name" :value="opt.id">
        <span class="bb-tag-picker__opt">
          <span>{{ opt.name }}</span>
          <span v-if="opt.count != null" class="bb-tag-picker__count">{{ opt.count }}</span>
        </span>
      </el-option>
    </el-select>
    <span v-if="isLoading" class="bb-tag-picker__hint">标签加载中 / 不可用</span>
  </div>
</template>

<style scoped>
.bb-tag-picker {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}
.bb-tag-picker__select {
  flex: 1;
  min-width: 0;
}
.bb-tag-picker__hint {
  font-size: 12px;
  color: var(--bb-text-muted);
  white-space: nowrap;
}
.bb-tag-picker__opt {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.bb-tag-picker__count {
  font-size: 12px;
  color: var(--bb-text-muted);
}
</style>
