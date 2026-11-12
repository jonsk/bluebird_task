<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, onUnmounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { EditorContent, useEditor } from '@tiptap/vue-3'
import { BubbleMenu } from '@tiptap/vue-3/menus'
import DragHandle from '@tiptap/extension-drag-handle-vue-3'
import { getNote, createNote, createWeeklyNote, updateNote, bindAttachment } from '@/api/note'
import { uploadFile } from '@/api/file'
import { BizError, HttpError } from '@/api/http'
import { watchMarkdownImages, type ImageHydrator } from '@/utils/markdownImages'
import { tableFromClipboard } from '@/utils/excelTablePaste'
import { buildNoteExtensions } from '@/utils/noteEditorExtensions'
import { isBlankHtml, plainTextFromHtml, toEditorHtml, visibleLength } from '@/utils/richText'
import { containsDangerousRichText } from '@/utils/sanitize'
import { formatClock } from '@/utils/format'
import { todayWeekStart, weekHeadings } from '@/utils/date'
import { validateUploadFile } from '@/utils/upload'
import { useFileConfig } from '@/composables/useFileConfig'
import { useBlockDragHandle } from '@/composables/useBlockDragHandle'
import BbSaveStatus from '@/components/bb/BbSaveStatus.vue'
import NoteToolbar from './editor/NoteToolbar.vue'
import NoteBubbleMenu from './editor/NoteBubbleMenu.vue'
import NoteOutline from './editor/NoteOutline.vue'
import type { NoteDetailVO, NotePeriod } from '@/types/note'
import '@/styles/note-editor.css'

/**
 * NoteEditor — 新建 / 编辑日志（Tiptap 富文本编辑器，**自动保存**）。
 *
 * 与列表/详情共用 `buildNoteExtensions` 的 schema；正文以 HTML 落库。
 * 历史 Markdown 日志在进入编辑器时用 `toEditorHtml` 转换，用户继续编辑并保存后
 * 自动升级为 HTML，不需要一次性迁移脚本。
 *
 * - 没有「保存 / 取消」按钮：停止输入 AUTO_SAVE_DELAY 后自动落库（Ctrl+S / Ctrl+Enter 立即保存，
 *   组件销毁前兜底保存一次）；状态行显示 保存中 / 已保存 HH:mm / 保存失败·点击重试。
 * - 新建：首行预置当天日期标题；正文非空才真正创建（只有标题不会凭空多一条日志）。
 * - 编辑：GET 详情回显，更新带 version；失败保留草稿在编辑器里。
 * - 宿主若监听 `cancel`，传 `closable`，用户可按 Esc 收起（行内编辑 / 详情编辑）。
 * 日志日期创建后不可修改（后端对携带 logDate 的更新返回 400）。
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
    /**
     * 周期：DAY = 工作日志（新建预置当天日期标题）；WEEK = 每周工作记录
     * （新建预置 7 个日期标题，首次落库走 `POST /notes/weekly`，一周一条由后端兜底）。
     */
    period?: NotePeriod
  }>(),
  { noteId: null, initial: null, fieldPermissions: () => ({}), closable: false, compact: false, period: 'DAY' },
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

/** 附件配置（大小/扩展名上限）；三个上传入口共享一次请求。 */
const { config: fileConfig, load: loadFileConfig } = useFileConfig()

let saveTimer: ReturnType<typeof setTimeout> | undefined
let saving: Promise<void> | null = null
let loadToken = 0

/** 编辑态用 props.noteId；新建态首次保存后改用 createdId。 */
const currentId = computed(() => props.noteId ?? createdId.value)

/* ==================== Tiptap 实例 ==================== */

const hostRef = ref<HTMLElement | null>(null)
const stateTick = ref(0)
let hydrator: ImageHydrator | null = null
/** setContent 期间忽略 onUpdate，避免“回显”被误判成用户输入。 */
let hydrating = false

const editor = useEditor({
  content: '<p></p>',
  editable: canEditContent.value,
  extensions: buildNoteExtensions({ mode: 'editor' }),
  editorProps: {
    attributes: {
      class: 'note-prose note-editor__prose',
      spellcheck: 'false',
    },
    handlePaste: (_view, event) => {
      // Excel 复制区域时会把「表格 HTML」和「一张区域快照 PNG」同时放进剪贴板，
      // 必须先判表格再判图片，否则粘贴出来的永远是 image.png 那张快照。
      const table = tableFromClipboard(event.clipboardData)
      const ed = editor.value
      if (table && ed) {
        event.preventDefault()
        ed.chain().focus().insertContent(table).run()
        return true
      }
      const images = imageFilesFromEvent(event.clipboardData)
      const attachments = attachmentFilesFromEvent(event.clipboardData)
      if (!images.length && !attachments.length) return false
      event.preventDefault()
      if (images.length) void uploadImages(images)
      if (attachments.length) void uploadAttachments(attachments)
      return true
    },
    handleDrop: (_view, event) => {
      const images = imageFilesFromEvent(event.dataTransfer)
      const attachments = attachmentFilesFromEvent(event.dataTransfer)
      if (!images.length && !attachments.length) return false
      event.preventDefault()
      if (images.length) void uploadImages(images)
      if (attachments.length) void uploadAttachments(attachments)
      return true
    },
  },
  onCreate: () => {
    applyContent(toEditorHtml(content.value))
  },
  onUpdate: ({ editor: ed }) => {
    content.value = ed.getHTML()
    if (hydrating) return
    if (content.value === lastSaved.value) {
      if (status.value !== 'error') status.value = 'idle'
      return
    }
    if (status.value !== 'error') status.value = 'idle'
    scheduleSave()
  },
  onTransaction: () => {
    stateTick.value += 1
  },
})

watch(canEditContent, (editable) => {
  editor.value?.setEditable(editable)
})

onMounted(() => {
  init()
  hydrator = watchMarkdownImages(() => hostRef.value)
  void loadFileConfig()
})

onUnmounted(() => {
  hydrator?.stop()
  hydrator = null
})

/** 用 HTML 覆盖编辑器内容，并同步 content / lastSaved 基线。 */
function applyContent(html: string): void {
  const ed = editor.value
  if (!ed) {
    content.value = html
    return
  }
  hydrating = true
  try {
    ed.commands.setContent(html || '<p></p>', { emitUpdate: false })
    content.value = ed.getHTML()
  } finally {
    hydrating = false
  }
}

function syncBaseline(): void {
  applyContent(toEditorHtml(content.value))
  lastSaved.value = content.value
}

async function loadDetail(): Promise<void> {
  if (!isEdit.value || props.noteId == null) return
  const token = ++loadToken
  loading.value = true
  savedAt.value = ''
  status.value = 'idle'
  errorMessage.value = ''
  try {
    const d = await getNote(props.noteId)
    if (token !== loadToken) return
    content.value = d.content ?? ''
    if (containsDangerousRichText(d.content ?? '')) {
      ElMessage.warning('本文含已被过滤的嵌入式内容（视频/脚本等），编辑保存后将永久移除')
    }
    logDate.value = d.logDate ?? ''
    currentVersion.value = d.version
    savedAt.value = formatClock(d.updatedAt)
    loadedPerms.value = (d as { fieldPermissions?: typeof loadedPerms.value }).fieldPermissions ?? null
    await nextTick()
    if (token !== loadToken) return
    syncBaseline()
  } catch (e) {
    const msg = e instanceof BizError || e instanceof HttpError ? e.message : (e as Error).message
    ElMessage.error(msg || '加载详情失败')
  } finally {
    if (token === loadToken) loading.value = false
  }
}

/**
 * 新建日志的默认首行：`2026年11月3日`（月/日不补零，符合「202X年X月X日」的写法）。
 * 用本地时区取日期，与后端「日志日期 = 当天」的口径一致。
 */
function todayHeading(): string {
  const d = new Date()
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日`
}

function applyNewDefaults(): void {
  // 新建才插入日期标题；编辑态由详情接口回显，宿主若传了 initial 则以传入内容为准
  const initial = props.initial?.content
  content.value = initial ?? defaultDraft()
  createdId.value = null
  currentVersion.value = undefined
  loadedPerms.value = null
  status.value = 'idle'
  savedAt.value = ''
  errorMessage.value = ''
  applyContent(toEditorHtml(content.value))
  // 预置标题不算"已保存内容"：正文写出来才会真正建这条日志
  lastSaved.value = initial ? content.value : ''
}

/** 新建态默认正文：日日志一行当天日期；周记录 7 个日期标题（与后端 weekTemplate 同格式）。 */
function defaultDraft(): string {
  if (props.period === 'WEEK') {
    return weekHeadings(todayWeekStart()).map((h) => `<h1>${h}</h1>`).join('') + '<p></p>'
  }
  return `<h1>${todayHeading()}</h1><p></p>`
}

function init(): void {
  if (isEdit.value) void loadDetail()
  else applyNewDefaults()
}

watch(
  () => props.noteId,
  () => {
    if (isEdit.value) void loadDetail()
    else applyNewDefaults()
  },
)

/* ==================== 自动保存 ==================== */

/**
 * 读编辑器实时值。
 *
 * 卸载过程中 Tiptap 实例可能已先释放，一旦这里抛异常，Vue 的卸载链会中断——
 * 表现为「路由变了但视图没换」。因此卸载中直接退回 content，平时也兜一层 try/catch。
 */
let tearingDown = false
function readValue(): string {
  if (tearingDown) return content.value
  try {
    return editor.value?.getHTML() ?? content.value
  } catch {
    return content.value
  }
}

/**
 * 正文是否非空。
 * 新建时首行是预置的日期标题，只有标题不建日志——否则用户一进页面就会凭空多一条日志。
 */
function hasBody(value: string): boolean {
  const lines = plainTextFromHtml(value)
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
  if (!lines.length) return false
  if (props.period === 'WEEK') {
    // 周记录预置 7 个「YYYY年M月D日 周X」标题，只有标题同样不建记录
    return lines.some((line) => !/^\d{4}年\d{1,2}月\d{1,2}日\s*周[一二三四五六日]$/.test(line))
  }
  return !/^\d{4}年\d{1,2}月\d{1,2}日$/.test(lines[0]) || lines.length > 1
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
  if (isBlankHtml(value)) return
  if (visibleLength(value) > CONTENT_LIMIT) {
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
      let detail: NoteDetailVO = created
        ? props.period === 'WEEK'
          ? await createWeeklyNote({ content: value })
          : await createNote({ content: value })
        : await updateNote(id, { content: value, version: currentVersion.value })

      if (created && props.period === 'WEEK' && detail.content != null && detail.content !== value) {
        // 该周已有记录（另一标签页/另一台设备先建了）：以本地这次输入为准覆盖，
        // 避免「返回已有记录」让用户以为保存成功、实际写的是旧内容。
        detail = await updateNote(Number(detail.id), { content: value, version: detail.version })
      }

      if (created) createdId.value = Number(detail.id)
      currentVersion.value = detail.version
      if (detail.logDate) logDate.value = detail.logDate
      lastSaved.value = value
      status.value = 'saved'
      savedAt.value = formatClock(detail.updatedAt) || formatClock()
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

/** Tiptap 默认快捷键之外补 Ctrl+S / Ctrl+Enter（立即保存）与 Esc（收起编辑）。 */
function onKeydown(e?: Event): void {
  const event = e as KeyboardEvent | undefined
  if (!event || event.isComposing) return
  if (event.key === 'Escape') {
    // 斜杠菜单打开时 Esc 只关菜单（由 Suggestion 插件处理），不收起整个编辑器
    if (typeof document !== 'undefined' && document.querySelector('.slash-menu')) return
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

function imageFilesFromEvent(data: DataTransfer | null): File[] {
  if (!data) return []
  return Array.from(data.files).filter((file) => IMAGE_ACCEPT.test(file.type))
}

/** 拖拽 / 粘贴里除图片外的文件，统一走附件卡片。 */
function attachmentFilesFromEvent(data: DataTransfer | null): File[] {
  if (!data) return []
  return Array.from(data.files).filter((file) => !IMAGE_ACCEPT.test(file.type))
}

/**
 * 工具栏 / 粘贴 / 拖拽图片：先 `POST /files` 落盘，再 `POST /notes/{id}/attachments` 关联，
 * 插入正文的是 note 专用预览地址（`/notes/{id}/attachments/{aid}/preview`）——
 * 通用 `/files/{id}/preview` 只有上传者/ADMIN 能看，别人看到的日志会是一堆破图。
 *
 * 新建态还没有 noteId（首次自动保存前）无法关联，直接提示先保存。
 */
async function uploadImages(files: File[]): Promise<void> {
  if (!canEditContent.value) return
  const noteId = currentId.value
  if (noteId == null) {
    ElMessage.warning('请先保存日志，再插入图片')
    return
  }

  let inserted = 0
  let failed = 0
  for (const file of files) {
    const error = validateUploadFile(file, fileConfig.value, {
      allowedMime: IMAGE_ACCEPT,
      mimeLabel: '不是支持的图片格式（png/jpeg/gif/webp/bmp）',
      skipExtensionCheck: true,
    })
    if (error) {
      ElMessage.error(error)
      failed += 1
      continue
    }
    try {
      const vo = await uploadFile(file)
      const att = await bindAttachment(noteId, { attachmentId: Number(vo.id) })
      const url = att.previewUrl || att.downloadUrl
      if (url) {
        editor.value?.chain().focus().setImage({ src: url, alt: file.name }).run()
        inserted += 1
      }
    } catch (e) {
      failed += 1
      ElMessage.error(`${file.name}：${(e as Error)?.message || '上传失败'}`)
    }
  }
  if (inserted) emit('attachments-changed')
  if (failed && !inserted) ElMessage.error('图片上传失败')
}

/* ==================== 附件上传 ==================== */

/**
 * 工具栏「附件」：任何非图片文件都插入一张附件卡片。
 * 和图片一样，先上传并绑定到当前日志，再把 note 专用下载地址写进正文；
 * 这样其他有日志查看权限的人下载时不会落到 `/files/{id}` 的创建者限制上。
 */
async function uploadAttachments(files: File[]): Promise<void> {
  if (!canEditContent.value) return
  const noteId = currentId.value
  if (noteId == null) {
    ElMessage.warning('请先保存日志，再插入附件')
    return
  }

  let inserted = 0
  let failed = 0
  for (const file of files) {
    const error = validateUploadFile(file, fileConfig.value)
    if (error) {
      ElMessage.error(error)
      failed += 1
      continue
    }
    try {
      const vo = await uploadFile(file)
      const att = await bindAttachment(noteId, { attachmentId: Number(vo.id) })
      editor.value
        ?.chain()
        .focus()
        .insertContent({
          type: 'noteAttachment',
          attrs: {
            attachmentId: Number(att.attachmentId ?? vo.id),
            fileId: Number(vo.id),
            fileName: att.fileName || file.name,
            size: att.size ?? file.size,
            contentType: att.contentType || file.type,
            downloadUrl: att.downloadUrl || '',
            previewUrl: att.previewUrl || '',
          },
        })
        .run()
      inserted += 1
    } catch (e) {
      failed += 1
      ElMessage.error(`${file.name}：${(e as Error)?.message || '上传失败'}`)
    }
  }
  if (inserted) emit('attachments-changed')
  if (failed && !inserted) ElMessage.error('附件上传失败')
}

/* ==================== 拖拽手柄（与周报编辑器共用同一套逻辑） ==================== */

const { onNodeChange, insertBelow } = useBlockDragHandle(() => editor.value)

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
      <div class="note-editor__content" :class="{ 'is-readonly': !canEditContent }">
        <NoteToolbar
          v-if="editor && canEditContent"
          :editor="editor"
          :tick="stateTick"
          @upload-image="uploadImages"
          @upload-attachment="uploadAttachments"
        />

        <div class="note-editor__main">
          <aside
            v-if="editor && canEditContent"
            class="note-editor__outline"
            data-test="note-outline-panel"
          >
            <NoteOutline :editor="editor" :tick="stateTick" persistent />
          </aside>

          <div ref="hostRef" class="note-editor__body" data-test="note-editor-content">
            <DragHandle
              v-if="editor && canEditContent"
              :editor="editor"
              class="drag-handle"
              :compute-position-config="{ placement: 'left-start' }"
              :on-node-change="onNodeChange"
            >
              <div class="drag-handle__inner">
                <button
                  class="dh-btn"
                  type="button"
                  title="在下方插入段落"
                  draggable="false"
                  @mousedown.stop
                  @click.stop="insertBelow"
                >
                  +
                </button>
                <button class="dh-btn dh-grip" type="button" title="拖拽排序" draggable="false">
                  ⠿
                </button>
              </div>
            </DragHandle>

            <EditorContent :editor="editor" class="note-editor__editor" />

            <BubbleMenu v-if="editor && canEditContent" :editor="editor" :options="{ placement: 'top' }">
              <NoteBubbleMenu :editor="editor" :tick="stateTick" />
            </BubbleMenu>
          </div>
        </div>
      </div>

      <p v-if="isEdit && logDate && !compact" class="note-editor__meta" data-test="note-editor-logdate">
        日志日期：{{ logDate }}（创建后不可修改）
      </p>
      <p v-if="!canEditContent" class="note-editor__hint" data-test="note-editor-readonly-hint">
        当前为只读视图，无可编辑字段。
      </p>

      <!-- 自动保存：无常驻按钮，只显示状态；按 Esc 收起编辑 -->
      <BbSaveStatus
        v-if="canEditContent && !compact"
        :status="status"
        :saved-at="savedAt"
        :error-message="errorMessage"
        test-id="note-editor"
        idle-text="自动保存已开启 · Ctrl+S 立即保存"
        @retry="saveNow"
      />
    </template>
  </div>
</template>

<style scoped>
/* 外壳布局（内容区 / 大纲 / 正文 / 保存状态）在 @/styles/note-editor.css 中统一定义，
   周报编辑器复用同一套类名，避免两处漂移。这里只保留日志编辑器自己的部分。 */
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
</style>
