<script setup lang="ts">
import { computed } from 'vue'
import { NodeViewWrapper, type NodeViewProps } from '@tiptap/vue-3'
import { ElMessage } from 'element-plus'
import { openAttachment } from '@/api/file'
import { formatFileSize } from '@/utils/format'
import NoteIcon from './NoteIcon.vue'

const props = defineProps<NodeViewProps>()

const fileName = computed(() => String(props.node.attrs.fileName || '附件'))
const contentType = computed(() => String(props.node.attrs.contentType || ''))
const size = computed(() => Number(props.node.attrs.size) || 0)
const downloadUrl = computed(() => String(props.node.attrs.downloadUrl || ''))
const previewUrl = computed(() => String(props.node.attrs.previewUrl || downloadUrl.value))

/** 预览按钮只放行与 api/file.ts 的 PREVIEW_MIME 一致的位图 / PDF / 纯文本；SVG、HTML 一律只给下载。 */
const PREVIEWABLE_MIME = /^(?:image\/(?:png|jpeg|gif|webp)|application\/pdf|text\/plain)(?:\s*;|$)/i
const PREVIEWABLE_EXT = /\.(?:png|jpe?g|gif|webp|pdf|txt|md)$/i
const SCRIPTABLE_EXT = /\.(?:svg|svgz|html?|xhtml|mhtml|xml|js)$/i
const canPreview = computed(
  () =>
    !SCRIPTABLE_EXT.test(fileName.value) &&
    (PREVIEWABLE_MIME.test(contentType.value) || PREVIEWABLE_EXT.test(fileName.value)),
)

const sizeText = computed(() => formatFileSize(size.value))

const ext = computed(() => {
  const index = fileName.value.lastIndexOf('.')
  return index >= 0 ? fileName.value.slice(index + 1).toUpperCase() : 'FILE'
})

async function open(mode: 'preview' | 'download'): Promise<void> {
  const url = mode === 'preview' ? previewUrl.value : downloadUrl.value
  if (!url) {
    ElMessage.error('附件地址不存在')
    return
  }
  try {
    await openAttachment({ mode, url, fileName: fileName.value, contentType: contentType.value || undefined })
  } catch (error) {
    ElMessage.error((error as Error)?.message || '打开附件失败')
  }
}
</script>

<template>
  <NodeViewWrapper
    as="div"
    class="note-attachment"
    :class="{ 'is-selected': selected }"
    data-note-attachment
    :data-file-name="fileName"
  >
    <span class="note-attachment__icon">
      <NoteIcon name="attachment" :size="19" />
    </span>
    <span class="note-attachment__main">
      <span class="note-attachment__name" :title="fileName">{{ fileName }}</span>
      <span class="note-attachment__meta">{{ ext }}<template v-if="sizeText"> · {{ sizeText }}</template></span>
    </span>
    <span class="note-attachment__ops">
      <button
        v-if="canPreview"
        type="button"
        class="note-attachment__btn"
        title="预览附件"
        @mousedown.stop
        @click.stop="open('preview')"
      >
        预览
      </button>
      <button
        type="button"
        class="note-attachment__btn"
        title="下载附件"
        @mousedown.stop
        @click.stop="open('download')"
      >
        下载
      </button>
      <button
        v-if="editor.isEditable"
        type="button"
        class="note-attachment__btn is-danger"
        title="移除附件卡片"
        @mousedown.stop
        @click.stop="deleteNode"
      >
        移除
      </button>
    </span>
  </NodeViewWrapper>
</template>

<style scoped>
.note-attachment {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0.9em 0;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--bb-radius);
  background: var(--bg-secondary);
}

.note-attachment.is-selected {
  border-color: var(--bg-accent);
  box-shadow: 0 0 0 2px var(--bg-active);
}

.note-attachment__icon {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: var(--bb-radius);
  background: var(--bg-active);
  color: var(--bg-accent);
}

.note-attachment__main {
  display: flex;
  flex: 1 1 auto;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.note-attachment__name {
  overflow: hidden;
  color: var(--bb-text);
  font-size: 13.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.note-attachment__meta {
  color: var(--bb-text-muted);
  font-size: 11.5px;
}

.note-attachment__ops {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 4px;
}

.note-attachment__btn {
  height: 26px;
  padding: 0 8px;
  border: 0;
  border-radius: var(--bb-radius);
  background: transparent;
  color: var(--bb-text-secondary);
  font: inherit;
  font-size: 12px;
  cursor: pointer;
}

.note-attachment__btn:hover {
  background: var(--bg-hover);
  color: var(--bb-text);
}

.note-attachment__btn.is-danger:hover {
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
}
</style>
