<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getNote, createNote, updateNote, bindAttachment } from '@/api/note'
import { uploadFile } from '@/api/file'
import { BizError, HttpError } from '@/api/http'
import { ERROR_CODE } from '@/utils/errorCode'
import { useAppStore } from '@/stores/app'
import { watchMarkdownImages, type ImageHydrator } from '@/utils/markdownImages'
import {
  VDITOR_CDN,
  loadVditor,
  vditorContentTheme,
  vditorHljs,
  vditorTheme,
} from '@/utils/vditor'
import type { NoteDetailVO } from '@/types/note'
import type Vditor from 'vditor'

/**
 * NoteEditor — 新建 / 编辑日志（Markdown 编辑器）。
 * 新建：content，日志日期由后端固定为当天（不再提供日期选择）。
 * 编辑：GET 详情回显，提交带 version（乐观锁 10006 冲突提示并保留草稿）。
 * 日志日期创建后不可修改（后端对携带 logDate 的更新返回 400）。
 *
 * 编辑器用 Vditor 的 **IR（即时渲染）模式**：单栏，敲 `# ` 当场变标题、`**粗**` 当场变粗体，
 * 没有「左边源码 / 右边预览」的双栏。只读态仍是同一渲染器，只是隐藏工具栏并锁输入。
 *
 * Vditor 的 lute 引擎、工具栏图标、i18n 是按 `cdn` 前缀动态插 <script> 加载的，bundler 管不到；
 * 因此构建时把 `node_modules/vditor/dist` 的子集拷到 `public/vditor/dist`，`cdn: '/vditor'` 走同源。
 */
const CONTENT_LIMIT = 20000
const EDITOR_HEIGHT = 420

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
const isDark = computed(() => app.theme === 'dark')

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

/* ==================== Vditor ==================== */

const hostRef = ref<HTMLElement | null>(null)
let vditor: Vditor | null = null
let hydrator: ImageHydrator | null = null
let creating: Promise<void> | null = null

/**
 * 创建编辑器实例。只读态仍是 IR 渲染器（Vditor 没有 preview 编辑模式），
 * 隐藏工具栏并 `disabled()` 锁输入，保证与可编辑态同一套排版。
 */
async function createVditor(): Promise<void> {
  const host = hostRef.value
  if (!host || vditor || creating) return
  creating = (async () => {
    const VditorCtor = await loadVditor()
    if (!hostRef.value) return
    const readOnly = !canEditContent.value
    vditor = new VditorCtor(hostRef.value, {
      cdn: VDITOR_CDN,
      lang: 'zh_CN',
      mode: 'ir',
      value: content.value,
      height: EDITOR_HEIGHT,
      cache: { enable: false },
      theme: vditorTheme(isDark.value),
      icon: 'ant',
      placeholder: '记录今日工作…（Ctrl+Enter 保存）',
      counter: { enable: true, type: 'markdown', max: CONTENT_LIMIT },
      toolbarConfig: { hide: readOnly, pin: true },
      preview: {
        theme: { current: vditorContentTheme(isDark.value) },
        hljs: vditorHljs(isDark.value),
        math: { engine: 'KaTeX' },
      },
      input: (value: string) => {
        content.value = value
      },
      upload: {
        accept: 'image/png,image/jpeg,image/gif,image/webp,image/bmp',
        handler: (files: File[]) => onUploadImg(files),
      },
    })
    if (readOnly) vditor.disabled()
    await nextTick()
    hydrator = watchMarkdownImages(() => hostRef.value)
  })()
  await creating
  creating = null
}

function destroyVditor(): void {
  hydrator?.stop()
  hydrator = null
  vditor?.destroy()
  vditor = null
}

/** 详情加载完成后（v-if 放行）再挂载；切换日志时重建，避免把上一条的内容留在编辑器里。 */
watch(
  loading,
  async (busy) => {
    if (busy) {
      destroyVditor()
      return
    }
    await nextTick()
    await createVditor()
  },
  { immediate: true },
)

/** 主题切换：Vditor 需要同时切编辑器与预览两套主题。 */
watch(isDark, (dark) => {
  vditor?.setTheme(dark ? 'dark' : 'classic', dark ? 'dark' : 'light', dark ? 'monokai' : 'github')
})

/** 可编辑性变化（如权限回填）：Vditor 不支持原地切工具栏显隐，直接重建实例。 */
watch(canEditContent, async () => {
  destroyVditor()
  await nextTick()
  await createVditor()
})

onUnmounted(destroyVditor)

/* ==================== 保存 ==================== */

const canSave = computed(() => !submitting.value)

async function onSubmit(): Promise<void> {
  if (submitting.value) return
  // 以编辑器里的实时值提交：`input` 回调有渲染延迟（粘贴 Markdown、输入法上屏后立即点保存时
  // `content` 可能还没跟上），直接读编辑器可避免存下旧内容。
  const value = vditor?.getValue ? vditor.getValue() : content.value
  if (!value.trim()) {
    ElMessage.warning('内容不能为空')
    return
  }
  if ([...value].length > CONTENT_LIMIT) {
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
        content: value,
        version: version.value,
      })
    } else {
      detail = await createNote({ content: value })
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

/** Vditor 只有默认快捷键；这里补 Ctrl+S / Ctrl+Enter，并跳过输入法组词。 */
function onKeydown(e?: Event): void {
  const event = e as KeyboardEvent | undefined
  if (!event || event.isComposing) return
  const mod = event.ctrlKey || event.metaKey
  if (mod && (event.key === 'Enter' || event.key.toLowerCase() === 's')) {
    event.preventDefault()
    void onSubmit()
  }
}

/* ==================== 图片上传 ==================== */

const IMAGE_ACCEPT = /^image\/(png|jpe?g|gif|webp|bmp)$/i

/**
 * 工具栏「上传图片」：先 `POST /files` 落盘，再 `POST /notes/{id}/attachments` 关联，
 * 插入正文的是 note 专用预览地址（`/notes/{id}/attachments/{aid}/preview`）——
 * 通用 `/files/{id}/preview` 只有上传者/ADMIN 能看，别人看到的日志会是一堆破图。
 *
 * <p>新建态还没有 noteId，无法关联，直接提示先保存（否则图片会挂在一个不存在的日志上）。
 *
 * @returns 空串 = 成功（Vditor 约定：返回非空字符串即错误提示）
 */
async function onUploadImg(files: File[]): Promise<string> {
  if (!canEditContent.value) return '当前为只读视图'
  if (props.noteId == null) {
    ElMessage.warning('请先保存日志，再插入图片')
    return '请先保存日志，再插入图片'
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
  if (urls.length) {
    vditor?.insertValue(
      urls.map((url) => `![](${url})`).join('\n'),
      true,
    )
    emit('attachments-changed')
  }
  if (failed && !urls.length) return '图片上传失败'
  return ''
}
</script>

<template>
  <div class="note-editor" data-test="note-editor" @keydown.capture="onKeydown">
    <div v-if="isEdit && loading" v-loading="loading" class="note-editor__loading">加载中…</div>

    <template v-else>
      <div ref="hostRef" class="note-editor__content" data-test="note-editor-content" />

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
  overflow: hidden;
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

/* Vditor 内容区最小高度：空文档时也有可点击的书写区域 */
.note-editor__content :deep(.vditor-reset) {
  min-height: 240px;
}
</style>
