<script setup lang="ts" generic="T extends Record<string, unknown>">
/**
 * 分页表格外壳：统一 el-table 的 loading/border/stripe 与底部分页器。
 *
 * 列定义通过默认插槽由页面提供——各日志表的自定义单元格（时间格式化、状态标签、
 * 空值占位）差异很大，强行用 columns 数组表达反而会引入大量条件分支。
 * 这里只抽取真正同构的「表格外壳 + 分页器」，列保持原样写在页面里。
 *
 * <p>需要勾选/树时通过 `rowKey` 指定行 key；勾选变化由 `selection-change` 上抛。
 */
defineProps<{
  rows: T[]
  total: number
  page: number
  size: number
  loading: boolean
  /** el-table 行 key（需支持勾选/树时传入，如 "id"）。 */
  rowKey?: string
}>()

const emit = defineEmits<{
  (e: 'update:page', value: number): void
  (e: 'selection-change', rows: T[]): void
}>()
</script>

<template>
  <div>
    <el-table
      v-loading="loading"
      :data="rows"
      border
      stripe
      size="small"
      :row-key="rowKey"
      @selection-change="(rows: T[]) => emit('selection-change', rows)"
    >
      <slot />
    </el-table>
    <div class="bb-table-page__pager">
      <el-pagination
        layout="total, prev, pager, next"
        :total="total"
        :page-size="size"
        :current-page="page"
        @current-change="(p: number) => emit('update:page', p)"
      />
    </div>
  </div>
</template>

<style scoped>
.bb-table-page__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
