<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, onUnmounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getNote, createNote, updateNote, bindAttachment } from '@/api/note'
import { uploadFile } from '@/api/file'
import { BizError, HttpError } from '@/api/http'
import { ERROR_CODE } from '@/utils/errorCode'
import { useAppStore } from '@/stores/app'
import { watchMarkdownImages, type ImageHydrator } from '@/utils/markdownImages'
import type { NoteDetailVO } from '@/types/note'

/**
 * NoteEditor — 新建 / 编辑日志（Markdown 编辑器）。
 * 新建：content，日志日期由后端固定为当天（不再提供日期选择）。
 * 编辑：GET 详情回显，提交带 version（乐观锁 10006 冲突提示并保留草稿）。
 * 日志日期创建后不可修改（后端对携带 logDate 的更新返回 400）。
 *
 * 编辑区为「左写右看」双栏：md-editor-v3 的预览栏就是 `<MdPreview>` 本体，
 * 输入即时渲染（同一个 markdown-it 实例、同一套主题与代码高亮），不需要额外挂第二个预览组件。
 *
 * 编辑器使用 md-editor-v3：按需异步加载，避免 CodeMirror 进入首屏 chunk。
 */
const CONTENT_LIMIT = 20000
/** 双栏下 300px 太矮，编辑区与预览区都要能看几屏。 */
const EDITOR_HEIGHT = '400px'

const MdEditor = defineAsyncComponent(async () => {
  await import('md-editor-v3/lib/style.css')
  // 预览栏与详情页共用同一套排版样式（style.css 已含大部分，这里补全 markdown 内容样式）
  await import('md-editor-v3/lib/preview.css')
  const mod = await import('md-editor-v3')
  // 本地 highlight.js + 本地扩展实例（prettier/mermaid/katex/echarts/cropper/screenfull）
  const { setupMarkdownEditor } = await import('@/plugins/markdown')
  await setupMarkdownEditor()
  return mod.MdEditor
})

const props = withDefaults(
  defineProps<{
    noteId?: number | null
    initial?: { content?: string } | null
    /**
     * 字段级可编辑性（后端 `fieldPermissions` 原样透传：content）。
     * 新建或未传时视为可改；编辑态以 `GET /notes/{id}` 下发的值为准（后端是唯一事实来源）。
     */
    fieldPermissions?: { content?: boolean }
  }>(),
  { noteId: null, initial: null, fieldPermissions: () => ({}) },
)

/**
 * 字段是否可编辑：
 * 1) 编辑态使用详情接口下发的 `fieldPermissions`（后端唯一事实来源）；
 * 2) 未下发（旧后端 / mock 未升级）时按"可改"处理（只有 owner/ADMIN 能进编辑态）；
 * 3) 新建态不受限。
 */
const loadedPerms = ref<{ content?: boolean } | null>(null)

const canEditContent = computed(() => {
  const explicit = loadedPerms.value ?? props.fieldPermissions ?? null
  // 空对象 = 未下发，视为可改
  const perms = explicit && Object.keys(explicit).length > 0 ? explicit : null
  return perms == null || perms.content !== false
})

const emit = defineEmits<{
  (e: 'saved', detail: NoteDetailVO): void
  (e: 'cancel'): void
  /** 正文里插入了新图片（附件已绑定），宿主据此刷新附件列表。 */
  (e: 'attachments-changed'): void
}>()

const app = useAppStore()
const editorTheme = computed<'light' | 'dark'>(() => (app.theme === 'dark' ? 'dark' : 'light'))

const isEdit = computed(() => props.noteId != null)

const content = ref('')
const logDate = ref('')
const version = ref<number | undefined>(undefined)
const loading = ref(false)
const submitting = ref(false)

async function loadDetail(): Promise<void> {
  if (!isEdit.value || props.noteId == null) return
  loading.value = true
  try {
    const d = await getNote(props.noteId)
    content.value = d.content ?? ''
    logDate.value = d.logDate ?? ''
    version.value = d.version
    loadedPerms.value = (d as { fieldPermissions?: typeof loadedPerms.value }).fieldPermissions ?? null
  } catch (e) {
    const msg = e instanceof BizError || e instanceof HttpError ? e.message : (e as Error).message
    ElMessage.error(msg || '加载详情失败')
  } finally {
    loading.value = false
  }
}

function applyNewDefaults(): void {
  content.value = props.initial?.content ?? ''
  version.value = undefined
}

function init(): void {
  if (isEdit.value) void loadDetail()
  else applyNewDefaults()
}

onMounted(init)
watch(
  () => props.noteId,
  () => init(),
)

/** 编辑器预览栏里的图片同样要带 Bearer，交给水合器换 blob（新增图片、切换预览都会命中）。 */
const rootRef = ref<HTMLElement | null>(null)
let hydrator: ImageHydrator | null = null
onMounted(() => {
  hydrator = watchMarkdownImages(() => rootRef.value)
})
onUnmounted(() => {
  hydrator?.stop()
  hydrator = null
})
const canSave = computed(() => !submitting.value)

function overLimit(): boolean {
  // 后端按 Unicode 码位计数（NOTE_CONTENT_TOO_LONG / 50002），这里保持同一口径。
  return [...content.value].length > CONTENT_LIMIT
}

async function onSubmit(): Promise<void> {
  if (submitting.value) return
  if (!content.value.trim()) {
    ElMessage.warning('内容不能为空')
    return
  }
  if (overLimit()) {
    ElMessage.warning(`内容不能超过 ${CONTENT_LIMIT} 字`)
    return
  }
  submitting.value = true
  try {
    // 成功 toast 交给宿主：新建要提示"已保存并回到第一页"、行内编辑提示"已保存"，
    // 这里再弹一次会出现双 toast（同一动作两条提示）。
    let detail: NoteDetailVO
    if (isEdit.value && props.noteId != null) {
      detail = await updateNote(props.noteId, {
        content: content.value,
        version: version.value,
      })
    } else {
      detail = await createNote({ content: content.value })
    }
    emit('saved', detail)
  } catch (e) {
    if (e instanceof BizError && e.code === ERROR_CODE.VERSION_CONFLICT) {
      ElMessage.error('内容已被他人修改，请刷新后重试')
    } else if (e instanceof BizError || e instanceof HttpError) {
      ElMessage.error(e.message || '保存失败')
    } else {
      ElMessage.error((e as Error).message || '保存失败')
    }
  } finally {
    submitting.value = false
  }
}

/** 编辑器自带的保存快捷键是 Ctrl+S；这里补 Ctrl+Enter，并跳过输入法组词。 */
function onKeydown(e?: Event): void {
  const event = e as KeyboardEvent | undefined
  if (!event || event.isComposing) return
  if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
    event.preventDefault()
    void onSubmit()
  }
}

const IMAGE_ACCEPT = /^image\/(png|jpe?g|gif|webp|bmp)$/i

/**
 * 工具栏「上传图片」：先 `POST /files` 落盘，再 `POST /notes/{id}/attachments` 关联，
 * 插入正文的是 note 专用预览地址（`/notes/{id}/attachments/{aid}/preview`）——
 * 通用 `/files/{id}/preview` 只有上传者/ADMIN 能看，别人看到的日志会是一堆破图。
 *
 * <p>新建态还没有 noteId，无法关联，直接提示先保存（否则图片会挂在一个不存在的日志上）。
 */
async function onUploadImg(
  files: File[],
  callback: (urls: string[]) => void,
): Promise<void> {
  if (!canEditContent.value) {
    callback([])
    return
  }
  if (props.noteId == null) {
    ElMessage.warning('请先保存日志，再插入图片')
    callback([])
    return
  }
  const urls: string[] = []
  let failed = 0
  for (const file of files) {
    if (!IMAGE_ACCEPT.test(file.type)) {
      ElMessage.error(`${file.name} 不是支持的图片格式（png/jpeg/gif/webp/bmp）`)
      failed += 1
      continue
    }
    try {
      const vo = await uploadFile(file)
      const att = await bindAttachment(props.noteId, { attachmentId: Number(vo.id) })
      const url = att.previewUrl || att.downloadUrl
      if (url) urls.push(url)
    } catch (e) {
      failed += 1
      ElMessage.error(`${file.name}：${(e as Error)?.message || '上传失败'}`)
    }
  }
  if (urls.length) emit('attachments-changed')
  if (failed && !urls.length) {
    callback([])
    return
  }
  callback(urls)
}
</script>

<template>
  <div ref="rootRef" class="note-editor" data-test="note-editor" @keydown.capture="onKeydown">
    <div v-if="isEdit && loading" v-loading="loading" class="note-editor__loading">加载中…</div>

    <template v-else>
      <MdEditor
        v-model="content"
        class="note-editor__content"
        language="zh-CN"
        :theme="editorTheme"
        :style="{ height: EDITOR_HEIGHT }"
        :preview="true"
        :max-length="CONTENT_LIMIT"
        :read-only="!canEditContent"
        placeholder="记录今日工作…（Ctrl+Enter 保存）"
        data-test="note-editor-content"
        @on-save="onSubmit"
        :on-upload-img="onUploadImg"
      />

      <p v-if="isEdit && logDate" class="note-editor__meta" data-test="note-editor-logdate">
        日志日期：{{ logDate }}（创建后不可修改）
      </p>
      <p v-if="!canEditContent" class="note-editor__hint" data-test="note-editor-readonly-hint">
        当前为只读视图，无可编辑字段。
      </p>

      <div class="note-editor__ops">
        <el-button
          type="primary"
          :disabled="!canSave"
          :loading="submitting"
          data-test="note-editor-save"
          @click="onSubmit"
        >
          {{ isEdit ? '保存' : '创建' }}
        </el-button>
        <el-button @click="emit('cancel')">取消</el-button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.note-editor {
  padding: 4px 0;
}
.note-editor__loading {
  min-height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--bb-text-muted);
}
.note-editor__content {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--bb-radius-sm);
}
.note-editor__meta {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--bb-text-muted);
}
.note-editor__hint {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--bb-text-muted);
}
.note-editor__ops {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
}
</style>
