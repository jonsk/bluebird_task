<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, onUnmounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getNote, createNote, updateNote, bindAttachment } from '@/api/note'
import { uploadFile } from '@/api/file'
import { BizError, HttpError } from '@/api/http'
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
 * NoteEditor — 新建 / 编辑日志（Markdown 编辑器，**自动保存**）。
 * - 没有「保存 / 取消」按钮：停止输入 AUTO_SAVE_DELAY 后自动落库（Ctrl+S / Ctrl+Enter 立即保存，
 *   组件销毁前兜底保存一次）；状态行显示 保存中 / 已保存 HH:mm / 保存失败·点击重试。
 * - 新建：首行预置当天日期标题；正文非空才真正创建（只有标题不会凭空多一条日志）。
 *   创建成功后继续编辑就是 update 同一条，无需再操作。
 * - 编辑：GET 详情回显，更新带 version；失败保留草稿在编辑器里。
 * - 宿主若监听 `cancel`，传 `closable`，用户可按 Esc 收起（行内编辑 / 详情编辑）。
 * 日志日期创建后不可修改（后端对携带 logDate 的更新返回 400）。
 *
 * 编辑器用 Vditor 的 **IR（即时渲染）模式**：单栏，敲 `# ` 当场变标题、`**粗**` 当场变粗体，
 * 没有「左边源码 / 右边预览」的双栏。只读态仍是同一渲染器，只是隐藏工具栏并锁输入。
 *
 * Vditor 的 lute 引擎、工具栏图标、i18n 是按 `cdn` 前缀动态插 <script> 加载的，bundler 管不到；
 * 因此构建时把 `node_modules/vditor/dist` 的子集拷到 `public/vditor/dist`，`cdn: '/vditor'` 走同源。
 */
const CONTENT_LIMIT = 20000
/** 停止输入多久后自动保存（毫秒） */
const AUTO_SAVE_DELAY = 1200

const props = withDefaults(
  defineProps<{
    noteId?: number | null
    initial?: { content?: string } | null
    /**
     * 字段级可编辑性（后端 `fieldPermissions` 原样透传：content）。
     * 新建或未传时视为可改；编辑态以 `GET /notes/{id}` 下发的值为准（后端是唯一事实来源）。
     */
    fieldPermissions?: { content?: boolean }
    /** 宿主是否监听 `cancel`（行内编辑 / 详情编辑为 true）；true 时状态行提示「按 Esc 收起」 */
    closable?: boolean
    /**
     * 紧凑模式：隐藏编辑器内部的状态行与「日志日期」提示，由宿主自行在标题栏展示保存状态。
     * 用于列表行内编辑（NoteHomeView）这类有独立「正在编辑」标题栏的场景。
     */
    compact?: boolean
  }>(),
  { noteId: null, initial: null, fieldPermissions: () => ({}), closable: false, compact: false },
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
  /** 自动保存成功；`meta.created` 表示这次是首次落库（列表需要刷新） */
  (e: 'saved', detail: NoteDetailVO, meta: { created: boolean }): void
  /** 用户按 Esc 收起编辑（不再有「取消」按钮） */
  (e: 'cancel'): void
  /** 正文里插入了新图片（附件已绑定），宿主据此刷新附件列表。 */
  (e: 'attachments-changed'): void
}>()

const app = useAppStore()
const isDark = computed(() => app.theme === 'dark')

const isEdit = computed(() => props.noteId != null)

const content = ref('')
const logDate = ref('')
const loading = ref(false)

/* ==================== 自动保存状态 ==================== */

/** 新建成功后拿到的 id：之后继续写就是 update 同一条，不必再点任何按钮。 */
const createdId = ref<number | null>(null)
/** 当前这条的乐观锁版本（新建/更新成功后由后端下发）。 */
const currentVersion = ref<number | undefined>(undefined)
/** 最近一次成功保存的内容，用来判断"是否还有未保存的改动"。 */
const lastSaved = ref('')
const status = ref<'idle' | 'saving' | 'saved' | 'error'>('idle')
const savedAt = ref('')
const errorMessage = ref('')

let saveTimer: ReturnType<typeof setTimeout> | undefined
let saving: Promise<void> | null = null

/** 编辑态用 props.noteId；新建态首次保存后改用 createdId。 */
const currentId = computed(() => props.noteId ?? createdId.value)

async function loadDetail(): Promise<void> {
  if (!isEdit.value || props.noteId == null) return
  loading.value = true
  try {
    const d = await getNote(props.noteId)
    content.value = d.content ?? ''
    logDate.value = d.logDate ?? ''
    currentVersion.value = d.version
    lastSaved.value = content.value
    loadedPerms.value = (d as { fieldPermissions?: typeof loadedPerms.value }).fieldPermissions ?? null
  } catch (e) {
    const msg = e instanceof BizError || e instanceof HttpError ? e.message : (e as Error).message
    ElMessage.error(msg || '加载详情失败')
  } finally {
    loading.value = false
  }
}

/**
 * 新建日志的默认首行：`# 2026年11月3日`（月/日不补零，符合「202X年X月X日」的写法）。
 * 用本地时区取日期，与后端「日志日期 = 当天」的口径一致。
 */
function todayHeading(): string {
  const d = new Date()
  return `# ${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日`
}

function applyNewDefaults(): void {
  // 新建才插入日期标题；编辑态由详情接口回显，宿主若传了 initial 则以传入内容为准
  content.value = props.initial?.content ?? `${todayHeading()}\n\n`
  createdId.value = null
  currentVersion.value = undefined
  // 预置标题不算"已保存内容"：正文写出来才会真正建这条日志
  lastSaved.value = ''
  status.value = 'idle'
  errorMessage.value = ''
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
      // 不设固定 height：编辑器高度随内容自动增长（长日志不会在内部上下翻页，而是整页滚动）
      cache: { enable: false },
      theme: vditorTheme(isDark.value),
      icon: 'ant',
      placeholder: '记录今日工作…（Ctrl+Enter 保存）',
      counter: { enable: true, type: 'markdown', max: CONTENT_LIMIT },
      // 大纲默认展开在左侧，长日志可直接跳转标题
      outline: { enable: true, position: 'left' },
      toolbarConfig: { hide: readOnly, pin: true },
      preview: {
        theme: { current: vditorContentTheme(isDark.value) },
        hljs: vditorHljs(isDark.value),
        math: { engine: 'KaTeX' },
      },
      input: (value: string) => {
        content.value = value
        if (value === lastSaved.value) return
        if (status.value !== 'error') status.value = 'idle'
        scheduleSave()
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

/* ==================== 自动保存 ==================== */

/**
 * 读编辑器实时值：`input` 回调有渲染延迟（粘贴、输入法上屏），直接读可避免存下旧内容。
 *
 * 卸载过程中 Vditor 的内部实例可能已先被释放（`getValue` 会抛 `VditorIRDOM2Md`），
 * 一旦这里抛异常，Vue 的卸载链会中断——表现为「路由变了但视图没换」。因此：
 * 卸载中直接退回 `content`，平时也兜一层 try/catch。
 */
let tearingDown = false
function readValue(): string {
  if (tearingDown) return content.value
  try {
    return vditor?.getValue ? vditor.getValue() : content.value
  } catch {
    return content.value
  }
}

/**
 * 正文是否非空。
 * 新建时首行是预置的 `# 2026年11月3日`，只有标题不建日志——否则用户一进页面就会凭空多一条日志。
 */
function hasBody(value: string): boolean {
  return value.replace(/^[ \t]*#+[^\n]*\n?/, '').trim().length > 0
}

function scheduleSave(): void {
  if (!canEditContent.value) return
  if (saveTimer) clearTimeout(saveTimer)
  saveTimer = setTimeout(() => {
    saveTimer = undefined
    void saveNow()
  }, AUTO_SAVE_DELAY)
}

/**
 * 立即保存。新建态首次保存会 create，之后（以及编辑态）走 update。
 * 失败的草稿留在编辑器里不丢，状态行给「保存失败，点击重试」。
 */
async function saveNow(): Promise<void> {
  if (saveTimer) {
    clearTimeout(saveTimer)
    saveTimer = undefined
  }
  if (!canEditContent.value) return
  if (saving) await saving

  const value = readValue()
  if (value === lastSaved.value) return
  if (!value.trim()) return
  if ([...value].length > CONTENT_LIMIT) {
    status.value = 'error'
    errorMessage.value = `内容不能超过 ${CONTENT_LIMIT} 字`
    return
  }
  // 新建：只写了预置日期标题就先不落库
  if (currentId.value == null && !hasBody(value)) return

  const id = currentId.value
  const created = id == null
  status.value = 'saving'
  errorMessage.value = ''

  saving = (async () => {
    try {
      const detail: NoteDetailVO = created
        ? await createNote({ content: value })
        : await updateNote(id, { content: value, version: currentVersion.value })

      if (created) createdId.value = Number(detail.id)
      currentVersion.value = detail.version
      if (detail.logDate) logDate.value = detail.logDate
      lastSaved.value = value
      status.value = 'saved'
      savedAt.value = new Date().toLocaleTimeString('zh-CN', {
        hour12: false,
        hour: '2-digit',
        minute: '2-digit',
      })
      // 卸载中的兜底保存不再通知宿主（宿主可能已销毁，回调里 setQuery 会打乱新路由）
      if (!tearingDown) emit('saved', detail, { created })
    } catch (e) {
      status.value = 'error'
      // 草稿留在编辑器里不动：状态行会变成「保存失败，点击重试」。
      // 工作日志只有作者本人能改，不存在并发改同一条的场景，这里不做版本冲突的专门引导。
      errorMessage.value = (e as BizError | HttpError | Error)?.message || '保存失败'
    }
  })()

  try {
    await saving
  } finally {
    saving = null
  }
  // 保存过程中又敲了新内容 → 再排一次
  if (readValue() !== lastSaved.value) scheduleSave()
}

/** Vditor 只有默认快捷键；这里补 Ctrl+S / Ctrl+Enter（立即保存）与 Esc（收起编辑）。 */
function onKeydown(e?: Event): void {
  const event = e as KeyboardEvent | undefined
  if (!event || event.isComposing) return
  if (event.key === 'Escape') {
    emit('cancel')
    return
  }
  const mod = event.ctrlKey || event.metaKey
  if (mod && (event.key === 'Enter' || event.key.toLowerCase() === 's')) {
    event.preventDefault()
    void saveNow()
  }
}

/** 离开页面/组件销毁前把未保存的改动补存一次（请求发出后不等待结果）。 */
onBeforeUnmount(() => {
  tearingDown = true
  if (saveTimer) clearTimeout(saveTimer)
  saveTimer = undefined
  try {
    if (canEditContent.value && readValue() !== lastSaved.value) void saveNow()
  } catch {
    // 兜底保存不得影响卸载（否则路由切换会卡在旧视图）
  }
})

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

/* ==================== 暴露给宿主 ==================== */

/**
 * 紧凑模式（compact）下隐藏了编辑器内部的状态行，宿主需要自行渲染保存状态。
 * 这里把保存状态 ref 与重试函数暴露出去，宿主可直接读取 / 调用。
 */
defineExpose({
  status,
  savedAt,
  errorMessage,
  saveNow,
})
</script>

<template>
  <div class="note-editor" data-test="note-editor" @keydown.capture="onKeydown">
    <div v-if="isEdit && loading" v-loading="loading" class="note-editor__loading">加载中…</div>

    <template v-else>
      <div ref="hostRef" class="note-editor__content" data-test="note-editor-content" />

      <p v-if="isEdit && logDate && !compact" class="note-editor__meta" data-test="note-editor-logdate">
        日志日期：{{ logDate }}（创建后不可修改）
      </p>
      <p v-if="!canEditContent" class="note-editor__hint" data-test="note-editor-readonly-hint">
        当前为只读视图，无可编辑字段。
      </p>

      <!-- 自动保存：无常驻按钮，只显示状态；按 Esc 收起编辑 -->
      <div
        v-if="canEditContent && !compact"
        class="note-editor__status"
        :class="`is-${status}`"
        data-test="note-editor-status"
      >
        <template v-if="status === 'saving'">保存中…</template>
        <button
          v-else-if="status === 'error'"
          type="button"
          class="note-editor__retry"
          data-test="note-editor-retry"
          @click="saveNow"
        >
          {{ errorMessage }}，点击重试
        </button>
        <template v-else-if="status === 'saved'">已保存 {{ savedAt }}</template>
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
.note-editor__status {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
  min-height: 20px;
  font-size: 12px;
  color: var(--bb-text-muted);
}
.note-editor__status.is-error {
  color: var(--el-color-danger);
}
.note-editor__retry {
  padding: 0;
  border: 0;
  background: none;
  font: inherit;
  color: inherit;
  cursor: pointer;
  text-decoration: underline;
}

/* 内容区：编辑器高度随内容自适应（未设固定 height），min-height 只保证空文档
   时也有可点击的书写区域；内容变长后整块随内容增高，长日志整页滚动而非内部翻页。
   横向留白收窄到 4px（Vditor 默认 .vditor-reset 左右各 10px，叠在页面栅格上缩进过多），
   只留一点避免正文贴到 1px 边框上，纵向 padding 维持 Vditor 默认的 10px。 */
.note-editor__content :deep(.vditor-reset) {
  min-height: 240px;
  padding-left: 4px;
  padding-right: 4px;
}
</style>
