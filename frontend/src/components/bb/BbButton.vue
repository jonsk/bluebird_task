<script setup lang="ts">
/**
 * BbButton — Element Plus 按钮的 BB 封装原子（契约：docs/frontend-baseline/contracts/BbButton.md）。
 * 走 Design Tokens，禁止组件内硬编码主色（03 §4.4）。
 */
withDefaults(
  defineProps<{
    type?: 'primary' | 'success' | 'warning' | 'danger' | 'info' | 'default'
    size?: 'large' | 'default' | 'small'
    loading?: boolean
    disabled?: boolean
    block?: boolean
  }>(),
  { type: 'default', size: 'default', loading: false, disabled: false, block: false },
)

defineEmits<{ click: [MouseEvent] }>()
</script>

<template>
  <el-button
    :type="type === 'default' ? undefined : type"
    :size="size"
    :loading="loading"
    :disabled="disabled"
    :class="block ? 'bb-btn-block' : undefined"
    @click="$emit('click', $event)"
  >
    <slot />
  </el-button>
</template>

<style scoped>
.bb-btn-block {
  width: 100%;
}
</style>
