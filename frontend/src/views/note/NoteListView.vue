<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import {
  ChatDotRound,
  Document,
  Folder,
  Key,
  List,
  Notebook,
  OfficeBuilding,
  Search,
  User,
  View,
} from '@element-plus/icons-vue'
import NoteList from '@/components/note/NoteList.vue'
import NoteFilterBar from '@/components/note/NoteFilterBar.vue'
import type { NoteBrief, NoteQuery, NoteQueryPatch, NoteScope } from '@/types/note'
import type { TagOption } from '@/types/tag'
import { formatHeaderDate } from '@/utils/date'

/**
 * NoteListView — 列表壳（布局），7 个 scope 页共用。
 * - 标题**单一来源** `route.meta.title`（不接 title prop）；scope/archived 读 `route.meta`。
 * - 壳内渲染 `NoteFilterBar`（筛选条不设 #filters 插槽）+ `NoteList`（纯容器）。
 * - 插槽：`#above-list`（mine 且非 archived 的临时卡片）、`#composer`（唯一新建入口）、`#editor`（编辑宿主）。
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
    tagOptions?: TagOption[]
    tagLoading?: boolean
    tagError?: boolean
    /** 是否显示顶部新建入口（仅 mine 且非 archived） */
    showComposer?: boolean
  }>(),
  {
    loading: false,
    error: null,
    activeId: null,
    emptyText: '暂无工作日志',
    errorMode: 'card',
    tagOptions: undefined,
    tagLoading: false,
    tagError: false,
    showComposer: false,
  },
)

const emit = defineEmits<{
  (e: 'change', patch: NoteQueryPatch): void
  (e: 'retry-tags'): void
  (e: 'page-change', page: number): void
  (e: 'retry'): void
  (e: 'open', id: number): void
  (e: 'edit', id: number): void
  (e: 'pin', id: number): void
  (e: 'unpin', id: number): void
  (e: 'archive', id: number): void
  (e: 'unarchive', id: number): void
  (e: 'remove', id: number): void
}>()

const route = useRoute()
const scope = computed<NoteScope>(() => (route.meta.scope as NoteScope) || 'mine')
const title = computed(() => (route.meta.title as string) || '工作日志')
const headerDate = computed(() => formatHeaderDate())

const SCOPE_ICONS: Record<string, unknown> = {
  mine: Notebook,
  explore: Search,
  shared: View,
  internal: OfficeBuilding,
  subordinate: User,
  all: List,
  visible: ChatDotRound,
  grants: Key,
  archived: Folder,
  calendar: Document,
  detail: Document,
}
const scopeIcon = computed(() => SCOPE_ICONS[String(route.name === 'NoteArchived' ? 'archived' : scope.value)] ?? Document)

/** 403 空态：errorMode='empty' 时把错误转成空态文案，不给重试按钮。 */
const isEmptyError = computed(() => props.errorMode === 'empty' && props.error != null)
</script>

<template>
  <div class="right-box">
    <div class="right-box-div">
      <div class="right-main">
        <section class="right-box-top">
          <div class="r-b-t-left">
            <div class="r-b-t-l-left">
              <el-icon class="r-b-t-l-l-icon" :size="28"><component :is="scopeIcon" /></el-icon>
              <div class="r-b-t-l-l-text">
                <div class="r-b-t-l-l-t-top">{{ title }}</div>
                <div class="r-b-t-l-l-t-bottom">{{ headerDate }}</div>
              </div>
            </div>
          </div>
        </section>

        <section class="right-box-bottom">
          <div class="note-page">
            <NoteFilterBar
              :query="query"
              :scope="scope"
              :loading="loading"
              :tag-options="tagOptions"
              :tag-loading="tagLoading"
              :tag-error="tagError"
              @change="(patch) => emit('change', patch)"
              @retry-tags="emit('retry-tags')"
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
              :empty-text="emptyText"
              @page-change="(p) => emit('page-change', p)"
              @retry="emit('retry')"
              @open="(id) => emit('open', id)"
              @edit="(id) => emit('edit', id)"
              @pin="(id) => emit('pin', id)"
              @unpin="(id) => emit('unpin', id)"
              @archive="(id) => emit('archive', id)"
              @unarchive="(id) => emit('unarchive', id)"
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
.right-box-top {
  height: 50px;
  margin-bottom: 20px;
  padding-top: 15px;
  box-sizing: border-box;
  user-select: none;
}
.r-b-t-left {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-left: 20px;
}
.r-b-t-l-left {
  display: flex;
  align-items: center;
}
.r-b-t-l-l-icon {
  color: var(--bb-text-secondary);
}
.r-b-t-l-l-text {
  margin-left: 10px;
}
.r-b-t-l-l-t-top {
  font-size: 16px;
}
.r-b-t-l-l-t-bottom {
  font-size: 12px;
  color: var(--bb-text-muted);
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
