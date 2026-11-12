<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import {
  ChatDotRound,
  Document,
  List,
  Notebook,
  User,
} from '@element-plus/icons-vue'
import NoteList from '@/components/note/NoteList.vue'
import NoteFilterBar from '@/components/note/NoteFilterBar.vue'
import NoteSearchInput from '@/components/note/NoteSearchInput.vue'
import BbPageHeader from '@/components/bb/BbPageHeader.vue'
import type { NoteBrief, NotePeriod, NoteQuery, NoteQueryPatch, NoteScope } from '@/types/note'
import { formatHeaderDate } from '@/utils/date'

/**
 * NoteListView — 列表壳（布局），4 个 scope 页 + 后台全部日志共用。
 * - 标题**单一来源** `route.meta.title`（不接 title prop）；scope 读 `route.meta`。
 * - 壳内渲染 `NoteFilterBar`（筛选条不设 #filters 插槽）+ `NoteList`（纯容器）。
 * - 插槽：`#above-list`（mine 的临时卡片）、`#composer`（唯一新建入口）、`#editor`（编辑宿主）。
 * - 不发请求：数据与事件全部由页面透传。
 */
const props = withDefaults(
  defineProps<{
    notes: NoteBrief[]
    query: NoteQuery
    page: number
    total: number
    loading?: boolean
    error?: Error | null
    activeId?: number | null
    emptyText?: string
    /** card=错误卡片（可重试）；empty=空态（403 等，不显示重试） */
    errorMode?: 'card' | 'empty'
    /** 是否显示顶部新建入口（仅 mine） */
    showComposer?: boolean
    /** 编辑区已展开时列表为空是正常的，不显示空态引导 */
    hideEmpty?: boolean
  }>(),
  {
    loading: false,
    error: null,
    activeId: null,
    emptyText: '暂无工作日志',
    errorMode: 'card',
    showComposer: false,
    hideEmpty: false,
  },
)

const emit = defineEmits<{
  (e: 'change', patch: NoteQueryPatch): void
  (e: 'page-change', page: number): void
  (e: 'retry'): void
  (e: 'open', id: number): void
  (e: 'edit', id: number): void
  (e: 'remove', id: number): void
}>()

const route = useRoute()
const scope = computed<NoteScope>(() => (route.meta.scope as NoteScope) || 'mine')
const period = computed<NotePeriod>(() => (route.meta.period === 'WEEK' ? 'WEEK' : 'DAY'))
const title = computed(() => (route.meta.title as string) || '工作日志')
const headerDate = computed(() => formatHeaderDate())

const SCOPE_ICONS: Record<string, unknown> = {
  mine: Notebook,
  subordinate: User,
  all: List,
  visible: ChatDotRound,
  calendar: Document,
  detail: Document,
}
const scopeIcon = computed(() => SCOPE_ICONS[scope.value] ?? Document)

/** 403 空态：errorMode='empty' 时把错误转成空态文案，不给重试按钮。 */
const isEmptyError = computed(() => props.errorMode === 'empty' && props.error != null)
</script>

<template>
  <div class="right-box">
    <div class="right-box-div">
      <div class="right-main">
        <BbPageHeader
          class="page-shell--note"
          :title="title"
          :icon="scopeIcon"
          :date="headerDate"
        >
          <!-- 搜索框与「我的一天」同位置：标题栏右侧，不随列表滚动 -->
          <template #search>
            <NoteSearchInput
              :query="query"
              :loading="loading"
              @change="(patch) => emit('change', patch)"
            />
          </template>
        </BbPageHeader>

        <section class="right-box-bottom">
          <div class="note-page">
            <NoteFilterBar
              v-if="scope !== 'mine'"
              :query="query"
              :loading="loading"
              @change="(patch) => emit('change', patch)"
            />

            <div v-if="showComposer" class="note-page__composer">
              <slot name="composer" />
            </div>

            <!-- 行内编辑宿主：插槽内容由页面自持（页面负责面板容器与样式），壳不做包裹，
                 否则 v-if 关闭后包裹层仍渲染空容器、同级间距翻倍 -->
            <slot name="editor" />

            <slot name="above-list" />

            <div v-if="isEmptyError" class="note-page__empty" data-test="note-page-empty">
              <!-- 403 空态用页面给的策展文案（如「仅管理员可查看」），不直接暴露后端 message -->
              <el-empty :description="emptyText" :image-size="72" />
            </div>

            <NoteList
              v-else
              :notes="notes"
              :loading="loading"
              :error="error"
              :page="page"
              :total="total"
              :active-id="activeId"
              :period="period"
              :empty-text="emptyText"
              :hide-empty="hideEmpty"
              @page-change="(p) => emit('page-change', p)"
              @retry="emit('retry')"
              @open="(id) => emit('open', id)"
              @edit="(id) => emit('edit', id)"
              @remove="(id) => emit('remove', id)"
            />
          </div>
        </section>
      </div>

      <slot name="side" />
    </div>
  </div>
</template>

<style scoped>
.right-box {
  min-height: 100vh;
  box-sizing: border-box;
}
.right-box-div {
  display: flex;
  justify-content: space-between;
}
.right-main {
  flex: 1;
  min-width: 0;
}
.right-box-bottom {
  height: calc(100vh - 80px);
  overflow-y: auto;
}
.note-page {
  padding: 0 var(--bb-page-padding) 24px;
  box-sizing: border-box;
}
.note-page__composer {
  margin-bottom: 12px;
}
.note-page__empty {
  padding: 40px 0;
}
</style>

<!-- 页头共享样式：非 scoped，保证规则能作用到 BbPageHeader 组件内部的节点 -->
<style>
@import '../../styles/page-shell.css';
</style>
