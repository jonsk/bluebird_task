import type { FileConfig } from '@/api/file'
import { formatFileSize } from './format'

/**
 * 上传入口的统一校验口径。
 *
 * 原先 NoteAttachmentList、NoteEditor（图片）、BbAttachmentList（无校验）三处各写一套，
 * 甚至连错误文案都不同；这里收敛成单点，保证任何入口都至少做同样的检查。
 *
 * 注意：前端校验只负责 UX 与「少传一次」，**不是安全边界**——文件名、MIME、大小都能被
 * 绕过。真正的防线必须是后端按 magic bytes 判定类型并强制以 attachment 方式返回。
 */

/** 能携带脚本、且可能被浏览器内联渲染的扩展名，任何配置下都拒绝。 */
const SCRIPTABLE_EXT = new Set(['svg', 'svgz', 'html', 'htm', 'xhtml', 'mhtml', 'hta', 'swf'])

export function extensionOf(name?: string): string {
  const i = (name ?? '').lastIndexOf('.')
  return i >= 0 ? (name ?? '').slice(i + 1).toLowerCase() : ''
}

export interface UploadValidationOptions {
  /** 图片等场景额外要求的 MIME 白名单。 */
  allowedMime?: RegExp
  mimeLabel?: string
  /** 正文插图按 MIME 判定，历史上不套用附件的扩展名白名单。 */
  skipExtensionCheck?: boolean
}

/** 返回错误提示；null 表示校验通过。 */
export function validateUploadFile(
  file: File,
  config: FileConfig,
  opts: UploadValidationOptions = {},
): string | null {
  if (config.maxSize && file.size > config.maxSize) {
    return `${file.name} 超过上限 ${formatFileSize(config.maxSize)}`
  }
  const allowed = (config.allowedExt ?? []).map((ext) => ext.toLowerCase().replace(/^\./, ''))
  if (!opts.skipExtensionCheck && allowed.length && !allowed.includes(extensionOf(file.name))) {
    return `${file.name} 类型不支持，仅允许 ${config.allowedExt.join(' / ')}`
  }
  // `invoice.svg.png` 这类双扩展也要挡住（取最后一段会误判为 png）。
  if (/\.(?:svg|svgz|html?|xhtml|mhtml|hta|swf)\./i.test(file.name)) {
    return `${file.name} 属于高危文件类型，禁止上传`
  }
  if (SCRIPTABLE_EXT.has(extensionOf(file.name))) {
    return `${file.name} 属于高危文件类型，禁止上传`
  }
  if (opts.allowedMime && !opts.allowedMime.test(file.type)) {
    return `${file.name} ${opts.mimeLabel ?? '文件类型不支持'}`
  }
  return null
}
