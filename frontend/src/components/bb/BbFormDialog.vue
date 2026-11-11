<script setup lang="ts">
/**
 * 表单弹窗外壳：统一 el-dialog 的开关 / 标题 / 宽度，并内置「取消 / 保存」footer。
 *
 * - open 用 v-model:open 双向绑定；点「取消」或点弹窗自身的关闭（X / ESC / 遮罩）都会回写 false，
 *   行为与原页面里 `xxxOpen = false` 完全一致。
 * - 表单放默认插槽（仍属于页面作用域，页面里的 ref / rules / scoped 样式照常生效），
 *   校验与提交逻辑仍留在各页面；「保存」只抛 @submit，不代跑业务逻辑。
 * - 需要保留特殊按钮文案或 data-test 的弹窗，可用 #footer 插槽覆盖内置 footer，
 *   此时 @submit 不再触发，页面自行绑定保存按钮。
 * - $attrs 透传给 el-dialog，保证 destroy-on-close 等原有属性不丢失。
 */
defineOptions({ inheritAttrs: false })

defineProps<{
  /** 是否显示（v-model:open） */
  open: boolean
  /** 弹窗标题 */
  title: string
  /** 弹窗宽度，留给 el-dialog 原样处理（未传时沿用其默认 50%） */
  width?: string
  /** 保存按钮的 loading 状态 */
  loading?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'submit'): void
}>()

/** el-dialog 关闭时把 modelValue 的变化转成 update:open。 */
function onOpenUpdate(value: boolean): void {
  emit('update:open', value)
}

function onCancel(): void {
  emit('update:open', false)
}
</script>

<template>
  <el-dialog
    v-bind="$attrs"
    :model-value="open"
    :title="title"
    :width="width"
    @update:model-value="onOpenUpdate"
  >
    <slot />
    <template #footer>
      <slot name="footer">
        <el-button @click="onCancel">取消</el-button>
        <el-button type="primary" :loading="loading" @click="emit('submit')">保存</el-button>
      </slot>
    </template>
  </el-dialog>
</template>
