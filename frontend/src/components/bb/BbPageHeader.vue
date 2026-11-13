<script setup lang="ts">
import { computed, useSlots } from 'vue'

/**
 * 页头外壳：图标 + 标题 + 日期，按需提供具名插槽。
 *
 * - #title-extra：标题行内补充信息（如周记录的日期区间），紧跟标题显示。
 * - #search：搜索区（.r-b-t-l-right 内）。插槽内容仍由页面自持，父级 scoped 样式照常生效。
 * - #actions：操作区（.r-b-t-right 内）。未提供时该节点整体不渲染，
 *   保持日志页原本不存在 .r-b-t-right 的 DOM 结构。
 * - 结构、class 名与两个页面改造前逐字一致；样式由 @/styles/page-shell.css 全局提供，
 *   组件内不再自带样式，避免重复定义。
 */
defineProps<{
  /** 主标题（route.meta.title） */
  title: string
  /** 标题左侧图标组件，沿用项目内图标映射的 unknown 约定 */
  icon: unknown
  /** 标题下方的日期行 */
  date: string
}>()

const slots = useSlots()
/** 仅在使用 #actions 时渲染右侧操作区，避免多出空占位改变栅格。 */
const hasActions = computed(() => Boolean(slots.actions))
</script>

<template>
  <section class="right-box-top">
    <div class="r-b-t-left">
      <div class="r-b-t-l-left">
        <el-icon class="r-b-t-l-l-icon" :size="28"><component :is="icon" /></el-icon>
        <div class="r-b-t-l-l-text">
          <div class="r-b-t-l-l-t-top">{{ title }}<slot name="title-extra" /></div>
          <div class="r-b-t-l-l-t-bottom">{{ date }}</div>
        </div>
      </div>

      <div class="r-b-t-l-right">
        <slot name="search" />
      </div>

      <div v-if="hasActions" class="r-b-t-right">
        <slot name="actions" />
      </div>
    </div>
  </section>
</template>
