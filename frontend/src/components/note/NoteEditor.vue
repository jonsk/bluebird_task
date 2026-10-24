<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getNote, createNote, updateNote } from '@/api/note'
import { BizError, HttpError } from '@/api/http'
import { ERROR_CODE } from '@/utils/errorCode'
import { todayInShanghai } from '@/utils/date'
import type { NoteDetailVO, NoteVisibility, TagVO } from '@/types/note'
import type { TagOption } from '@/types/tag'
import BbTagPicker from '@/components/bb/BbTagPicker.vue'

/**
 * NoteEditor — 新建 / 编辑日志。
 * 新建：content / logDate(默认上海当天) / visibility(PRIVATE) / tagIds(BbTagPicker)。
 * 编辑：GET 详情回显，提交带 version（乐观锁 10006 冲突提示并保留草稿）。
 * 标签三态：options===undefined → 禁用保存并提示；[]=可保存空；非空=可保存。
 */
const props = withDefaults(
  defineProps<{
    noteId?: number | null
    ownerTags?: TagVO[]
    initial?: { content?: string; logDate?: string; visibility?: NoteVisibility; tagIds?: number[] } | null
  }>(),
  { noteId: null, ownerTags: undefined, initial: null },
)

const emit = defineEmits<{
  (e: 'saved', detail: NoteDetailVO): void
  (e: 'cancel'): void
}>()

const isEdit = computed(() => props.noteId != null)

const tagOptions = computed<TagOption[] | undefined>(() => {
  const tags = props.ownerTags
  if (tags === undefined) return undefined
  return tags.map((t) => ({ id: t.id, name: t.name ?? '' }))
})

const content = ref('')
const logDate = ref(todayInShanghai())
const visibility = ref<NoteVisibility>('PRIVATE')
const tagIds = ref<number[]>([])
const version = ref<number | undefined>(undefined)
const loading = ref(false)
const submitting = ref(false)

async function loadDetail(): Promise<void> {
  if (!isEdit.value || props.noteId == null) return
  loading.value = true
  try {
    const d = await getNote(props.noteId)
    content.value = d.content ?? ''
    logDate.value = d.logDate ?? todayInShanghai()
    visibility.value = d.visibility ?? 'PRIVATE'
    tagIds.value = (d.tags ?? []).map((t) => t.id!).filter((id): id is number => id != null)
    version.value = d.version
  } catch (e) {
    const msg = e instanceof BizError || e instanceof HttpError ? e.message : (e as Error).message
    ElMessage.error(msg || '加载详情失败')
  } finally {
    loading.value = false
  }
}

function applyNewDefaults(): void {
  const init = props.initial
  content.value = init?.content ?? ''
  logDate.value = init?.logDate ?? todayInShanghai()
  visibility.value = init?.visibility ?? 'PRIVATE'
  tagIds.value = init?.tagIds ?? []
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

const canSave = computed(() => tagOptions.value !== undefined && !submitting.value)

async function onSubmit(): Promise<void> {
  if (submitting.value) return
  if (tagOptions.value === undefined) {
    ElMessage.warning('标签加载中，请稍候')
    return
  }
  if (!content.value.trim()) {
    ElMessage.warning('内容不能为空')
    return
  }
  submitting.value = true
  try {
    let detail: NoteDetailVO
    if (isEdit.value && props.noteId != null) {
      detail = await updateNote(props.noteId, {
        content: content.value,
        logDate: logDate.value,
        visibility: visibility.value,
        tagIds: tagIds.value,
        version: version.value,
      })
      ElMessage.success('已保存')
    } else {
      detail = await createNote({
        content: content.value,
        logDate: logDate.value,
        visibility: visibility.value,
        tagIds: tagIds.value,
      })
      ElMessage.success('已创建')
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
        placeholder="记录今日工作…（Ctrl+Enter 保存）"
        class="note-editor__content"
        @keydown.ctrl.enter="onCtrlEnter"
      />

      <div class="note-editor__row">
        <el-date-picker v-model="logDate" type="date" value-format="YYYY-MM-DD" placeholder="日志日期" />
        <el-select v-model="visibility" class="note-editor__sel">
          <el-option label="私有" value="PRIVATE" />
          <el-option label="部门内" value="INTERNAL" />
          <el-option label="公开" value="PUBLIC" />
        </el-select>
        <BbTagPicker
          :options="tagOptions"
          v-model="tagIds"
          class="note-editor__tags"
        />
      </div>

      <div class="note-editor__ops">
        <el-button v-if="tagOptions === undefined" disabled>标签加载中…</el-button>
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
.note-editor__row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
}
.note-editor__sel {
  width: 120px;
}
.note-editor__tags {
  flex: 1;
  min-width: 200px;
}
.note-editor__ops {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
}
</style>
