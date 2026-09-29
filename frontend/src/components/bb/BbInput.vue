<script setup lang="ts">
/**
 * BbInput — Element Plus 输入框的 BB 封装原子（契约：BbInput.md）。
 */
withDefaults(
  defineProps<{
    modelValue?: string
    type?: string
    placeholder?: string
    disabled?: boolean
    clearable?: boolean
    showPassword?: boolean
  }>(),
  { modelValue: '', type: 'text', clearable: true },
)

const emit = defineEmits<{ 'update:modelValue': [string] }>()
</script>

<template>
  <el-input
    :model-value="modelValue"
    :type="type"
    :placeholder="placeholder"
    :disabled="disabled"
    :clearable="clearable"
    :show-password="showPassword"
    @update:model-value="emit('update:modelValue', $event)"
  />
</template>
