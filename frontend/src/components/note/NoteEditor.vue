<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getNote, createNote, updateNote } from '@/api/note'
import { BizError, HttpError } from '@/api/http'
import { ERROR_CODE } from '@/utils/errorCode'
import type { NoteDetailVO } from '@/types/note'

/**
 * NoteEditor — 新建 / 编辑日志。
 * 新建：content，日志日期由后端固定为当天（不再提供日期选择）。
 * 编辑：GET 详情回显，提交带 version（乐观锁 10006 冲突提示并保留草稿）。
 * 日志日期创建后不可修改（后端对携带 logDate 的更新返回 400）。
 */
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
}>()

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

const canSave = computed(() => !submitting.value)

async function onSubmit(): Promise<void> {
  if (submitting.value) return
  if (!content.value.trim()) {
    ElMessage.warning('内容不能为空')
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

function onCtrlEnter(e?: Event): void {
  if ((e as KeyboardEvent | undefined)?.isComposing) return
  void onSubmit()
}
</script>

<template>
  <div class="note-editor" data-test="note-editor">
    <div v-if="isEdit && loading" v-loading="loading" class="note-editor__loading">加载中…</div>

    <template v-else>
      <el-input
        v-model="content"
        type="textarea"
        :rows="6"
        maxlength="20000"
        show-word-limit
        resize="vertical"
        :readonly="!canEditContent"
        placeholder="记录今日工作…（Ctrl+Enter 保存）"
        class="note-editor__content"
        data-test="note-editor-content"
        @keydown.ctrl.enter="onCtrlEnter"
      />

      <p v-if="isEdit && logDate" class="note-editor__meta" data-test="note-editor-logdate">
        日志日期：{{ logDate }}（创建后不可修改）
      </p>
      <p v-if="!canEditContent" class="note-editor__hint" data-test="note-editor-readonly-hint">
        当前为只读视图，无可编辑字段。
      </p>

      <div class="note-editor__ops">
        <el-button type="primary" :disabled="!canSave" :loading="submitting" @click="onSubmit">
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
.note-editor__content :deep(textarea) {
  white-space: pre-wrap;
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
