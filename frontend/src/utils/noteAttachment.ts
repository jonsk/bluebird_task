import { Node, mergeAttributes } from '@tiptap/core'
import { VueNodeViewRenderer } from '@tiptap/vue-3'
import NoteAttachmentNode from '@/components/note/editor/NoteAttachmentNode.vue'

export interface NoteAttachmentAttrs {
  attachmentId?: number | null
  fileId?: number | null
  fileName?: string
  size?: number
  contentType?: string
  downloadUrl?: string
  previewUrl?: string
}

function numberOrNull(value: unknown): number | null {
  const num = Number(value)
  return Number.isFinite(num) && num > 0 ? num : null
}

/**
 * 正文里的非图片附件卡片。
 *
 * 附件本身仍走 note_attachment_ref（下载权限、引用保护都由后端判断）；
 * 这里只保存渲染所需的最小字段，避免打开日志时再查一次附件列表。
 */
export const NoteAttachment = Node.create({
  name: 'noteAttachment',
  group: 'block',
  atom: true,
  draggable: true,
  selectable: true,

  addAttributes() {
    return {
      attachmentId: {
        default: null,
        parseHTML: (element) => numberOrNull(element.getAttribute('data-attachment-id')),
        renderHTML: (attributes) =>
          numberOrNull(attributes.attachmentId)
            ? { 'data-attachment-id': String(attributes.attachmentId) }
            : {},
      },
      fileId: {
        default: null,
        parseHTML: (element) => numberOrNull(element.getAttribute('data-file-id')),
        renderHTML: (attributes) =>
          numberOrNull(attributes.fileId) ? { 'data-file-id': String(attributes.fileId) } : {},
      },
      fileName: {
        default: '附件',
        parseHTML: (element) => element.getAttribute('data-file-name') || element.textContent || '附件',
        renderHTML: (attributes) => ({ 'data-file-name': attributes.fileName || '附件' }),
      },
      size: {
        default: 0,
        parseHTML: (element) => Number(element.getAttribute('data-file-size')) || 0,
        renderHTML: (attributes) => ({ 'data-file-size': String(Number(attributes.size) || 0) }),
      },
      contentType: {
        default: '',
        parseHTML: (element) => element.getAttribute('data-content-type') || '',
        renderHTML: (attributes) => ({ 'data-content-type': attributes.contentType || '' }),
      },
      downloadUrl: {
        default: '',
        parseHTML: (element) => element.getAttribute('data-download-url') || '',
        renderHTML: (attributes) => ({ 'data-download-url': attributes.downloadUrl || '' }),
      },
      previewUrl: {
        default: '',
        parseHTML: (element) => element.getAttribute('data-preview-url') || '',
        renderHTML: (attributes) => ({ 'data-preview-url': attributes.previewUrl || '' }),
      },
    }
  },

  parseHTML() {
    return [{ tag: 'div[data-note-attachment]' }]
  },

  renderHTML({ HTMLAttributes }) {
    const attrs = mergeAttributes(HTMLAttributes, {
      class: 'note-attachment',
      'data-note-attachment': '',
    })
    const name = String(HTMLAttributes['data-file-name'] || '附件')
    const url = String(HTMLAttributes['data-download-url'] || '')
    return [
      'div',
      attrs,
      ['span', { class: 'note-attachment__name' }, name],
      ...(url ? [['a', { href: url, target: '_blank', rel: 'noopener noreferrer' }, '下载']] : []),
    ]
  },

  addNodeView() {
    return VueNodeViewRenderer(NoteAttachmentNode)
  },
})
