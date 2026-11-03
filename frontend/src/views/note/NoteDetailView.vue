<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Delete, Edit } from '@element-plus/icons-vue'
import NoteEditor from '@/components/note/NoteEditor.vue'
import NoteAttachmentList from '@/components/note/NoteAttachmentList.vue'
import NoteNotFound from '@/components/note/NoteNotFound.vue'
import { useNoteDetail } from '@/composables/useNoteDetail'
import { useNoteActions } from '@/composables/useNoteActions'
import { useNoteStore } from '@/stores/note'
import { useAppStore } from '@/stores/app'
import { formatHeaderDate } from '@/utils/date'
import { stripMarkdown } from '@/utils/markdown'
import { watchMarkdownImages, type ImageHydrator } from '@/utils/markdownImages'
import { renderVditorPreview } from '@/utils/vditor'

/**
 * 日志详情：查看 + 行内编辑 + 附件。
 * - 正文渲染器与编辑器统一为 Vditor（`Vditor.preview`），保证同一篇日志在两处的排版、代码高亮、
 *   公式/图表表现完全一致；深链直达时按需异步加载。
 * - 一切改动经 `useNoteDetail.reload()` 整体收敛（子组件不局部 append/merge）。
 * - 404/50001 → NoteNotFound（不显示重试、不改标题）。
 * - 按钮显隐一律用后端下发的 canEdit / canManage。
 */
const route = useRoute()
const router = useRouter()
const store = useNoteStore()
const app = useAppStore()
const detailApi = useNoteDetail()
const actions = useNoteActions()
const id = computed(() => Number(route.params.id))
const detail = detailApi.detail
const editing = ref(false)

function load(): void {
  void detailApi.load(id.value)
}

onMounted(() => {
  load()
  store.setActiveNote(id.value)
})
watch(id, () => {
  load()
  store.setActiveNote(id.value)
})
/** 详情页标题带日志摘要；离开时恢复，否则后续页面标题会残留上一条日志。 */
const BASE_TITLE = document.title
watch(detail, (d) => {
  if (!d) return
  const flat = stripMarkdown(d.content)
  document.title = `日志详情 · ${flat.slice(0, 12)}`
})
onUnmounted(() => {
  document.title = BASE_TITLE || '蓝鸟任务管理系统'
})

/**
 * 正文图片鉴权：`![](…/preview)` 渲染后由这里换成本地 blob（`<img>` 带不了 Bearer）。
 * 容器随 `detail` 重建时会重新挂 observer，故在每次内容变化后重启一次。
 */
const preRootRef = ref<HTMLDivElement | null>(null)
let hydrator: ImageHydrator | null = null
function rehydrate(): void {
  hydrator?.stop()
  hydrator = null
  void nextTick(() => {
    hydrator = watchMarkdownImages(() => preRootRef.value)
  })
}

/**
 * 正文渲染：`Vditor.preview` 是命令式的，内容 / 主题变化都要重画一次。
 * 用 token 丢弃过期渲染（快速切换日志时后发的请求可能先回来）。
 */
let renderToken = 0
async function renderPreview(): Promise<void> {
  const d = detail.value
  const el = preRootRef.value
  if (!d || !el) return
  const token = ++renderToken
  try {
    await renderVditorPreview(el, d.content ?? '', {
      dark: app.theme === 'dark',
      after: () => {
        if (token === renderToken) rehydrate()
      },
    })
  } catch {
    // 渲染器加载失败时降级为纯文本：宁可看到源码，也不能整篇空白
    if (token === renderToken) el.textContent = d.content ?? ''
  }
}

watch(
  [detail, editing, () => app.theme],
  async () => {
    if (editing.value) {
      hydrator?.stop()
      hydrator = null
      return
    }
    await nextTick()
    await renderPreview()
  },
  { immediate: true },
)
onUnmounted(() => {
  renderToken += 1
  hydrator?.stop()
  hydrator = null
})

function back(): void {
  void router.push({ name: 'NoteHome' })
}

async function removeNote(): Promise<void> {
  const d = detail.value
  if (!d) return
  try {
    await ElMessageBox.confirm('删除后不可恢复，确认删除这条工作日志？', '删除日志', {
      type: 'warning',
      confirmButtonText: '删除',
    })
  } catch {
    return
  }
  await actions.remove({ id: Number(d.id) })
  void router.push({ name: 'NoteHome' })
}

function onSaved(): void {
  editing.value = false
  ElMessage.success('已保存')
  detailApi.reload()
  // 列表缓存与详情已不一致，回到列表时必须是新数据
  store.invalidate(['mine', 'subordinate', 'visible', 'all'])
}
</script>

<template>
  <div class="note-detail">
    <div class="note-detail__bar">
      <el-button text :icon="ArrowLeft" data-test="note-detail-back" @click="back">返回</el-button>
      <span class="note-detail__date">{{ formatHeaderDate() }}</span>
    </div>

    <div v-if="detailApi.loading.value" class="note-detail__loading">
      <el-skeleton :rows="6" animated />
    </div>

    <NoteNotFound v-else-if="detailApi.notFound.value" />

    <el-result
      v-else-if="detailApi.error.value"
      icon="error"
      title="加载失败"
      :sub-title="detailApi.error.value.message"
    >
      <template #extra>
        <el-button @click="load">重试</el-button>
      </template>
    </el-result>

    <template v-else-if="detail">
      <section class="note-detail__head">
        <div class="note-detail__meta">
          <span class="note-detail__logdate">{{ detail.logDate }}</span>
          <span class="note-detail__owner">{{ detail.ownerName }}</span>
        </div>
        <div class="note-detail__ops">
          <el-button v-if="detail.canEdit" size="small" type="primary" :icon="Edit" data-test="note-detail-edit" @click="editing = true">
            编辑
          </el-button>
          <el-button v-if="detail.canManage" size="small" type="danger" :icon="Delete" data-test="note-detail-delete" @click="removeNote">
            删除
          </el-button>
        </div>
      </section>

      <section v-if="editing" class="note-detail__editor">
        <NoteEditor
          :note-id="Number(detail.id)"
          @saved="onSaved"
          @cancel="editing = false"
          @attachments-changed="detailApi.reload()"
        />
      </section>
      <div v-else ref="preRootRef" class="note-detail__content" data-test="note-detail-content" />

      <section class="note-detail__block">
        <h3 class="note-detail__block-title">附件</h3>
        <NoteAttachmentList
          :note-id="Number(detail.id)"
          :attachments="detail.attachments"
          :can-upload="Boolean(detail.canEdit)"
          @refresh="detailApi.reload()"
        />
      </section>
    </template>
  </div>
</template>

<style scoped>
/* 不再限制 900px：正文/编辑器铺满列表区域（侧栏之外）的可用宽度 */
.note-detail {
  padding: 20px var(--bb-page-padding) 48px;
  box-sizing: border-box;
}
.note-detail__bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.note-detail__date {
  font-size: 12px;
  color: var(--bb-text-muted);
}
.note-detail__loading {
  padding: 12px 0;
}
.note-detail__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.note-detail__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--bb-text-muted);
}
.note-detail__logdate {
  font-size: 15px;
  color: var(--bb-text-secondary);
}
.note-detail__owner {
  margin-left: 4px;
}
.note-detail__ops {
  display: flex;
  flex: 0 0 auto;
  gap: 6px;
}
.note-detail__content {
  padding: 18px 0;
  font-size: 15px;
  line-height: 1.8;
  color: var(--bb-text-secondary);
  word-break: break-word;
}
/* Vditor 预览的排版对齐详情页正文（同一篇日志在两处字号/行高一致） */
.note-detail__content :deep(.vditor-reset) {
  padding: 0;
  font-size: 15px;
  line-height: 1.8;
  color: inherit;
  background: transparent;
}
.note-detail__editor {
  padding: 12px 0;
}
.note-detail__block {
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.note-detail__block-title {
  margin: 0 0 8px;
  font-size: 14px;
  font-weight: 500;
  color: var(--bb-text-secondary);
}
</style>
