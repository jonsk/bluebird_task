import { http, BizError } from './http'
import { ERROR_CODE } from '@/utils/errorCode'
import type { ApiResult, FileVO } from './http'

export type { FileVO }

export interface FileConfig {
  maxSize: number
  allowedExt: string[]
}

export interface AttachmentContent {
  blob: Blob
  fileName: string
  contentType: string
}

export type UploadOptions = {
  onProgress?: (percent: number) => void
  signal?: AbortSignal
}

const baseURL = import.meta.env.VITE_API_BASE_URL || '/api/v1'

/** 幂等兜底：若后端误带 /api/v1 前缀则剥掉一次（业务层不应再拼一次）。 */
export function stripApiPrefix(url: string): string {
  if (url.startsWith(`${baseURL}/`)) return url.slice(baseURL.length)
  return url
}

async function unwrapResult<T>(result: ApiResult<T>): Promise<T> {
  if (result.code === ERROR_CODE.OK) return result.data
  throw new BizError(result.code, result.message || `操作失败（${result.code}）`, result.traceId)
}

/** 通用文件上传（不携带 taskId，note 与通用场景）。 */
export async function uploadFile(file: File, opts?: UploadOptions): Promise<FileVO> {
  const form = new FormData()
  form.append('file', file)
  const resp = await http.post<ApiResult<FileVO>>('/files', form, {
    onUploadProgress: (e) => {
      if (opts?.onProgress && e.total) opts.onProgress(Math.round((e.loaded / e.total) * 100))
    },
    signal: opts?.signal,
  })
  return unwrapResult(resp.data)
}

/** 任务附件上传（携带 taskId，走专用端点）。 */
export async function uploadTaskFile(taskId: number, file: File, opts?: UploadOptions): Promise<FileVO> {
  const form = new FormData()
  form.append('file', file)
  const resp = await http.post<ApiResult<FileVO>>(`/tasks/${taskId}/attachments`, form, {
    onUploadProgress: (e) => {
      if (opts?.onProgress && e.total) opts.onProgress(Math.round((e.loaded / e.total) * 100))
    },
    signal: opts?.signal,
  })
  return unwrapResult(resp.data)
}

export async function fetchFileConfig(): Promise<FileConfig> {
  const resp = await http.get<ApiResult<FileConfig>>('/files/config')
  return unwrapResult(resp.data)
}

/** 任务侧兼容：按 id 下载/预览地址（不暴露物理路径）。 */
export function fileDownloadUrl(id: number): string {
  return `${http.defaults.baseURL}/files/${id}`
}

export function filePreviewUrl(id: number): string {
  return `${http.defaults.baseURL}/files/${id}/preview`
}

export async function deleteFile(id: number): Promise<void> {
  const resp = await http.delete<ApiResult<unknown>>(`/files/${id}`)
  await unwrapResult(resp.data)
}

/** 仅允许相对路径；若为绝对 URL 须与当前源同源。 */
const UNSAFE = /^(?:[a-z]+:)?\/\/|\\|\.\./i
function isPathSafe(url: string): boolean {
  if (UNSAFE.test(url)) return false
  if (/^[a-z]+:\/\//i.test(url)) {
    try {
      return new URL(url).origin === location.origin
    } catch {
      return false
    }
  }
  return true
}

function extractFilename(disposition?: string, fallback = 'download'): string {
  if (!disposition) return fallback
  const star = /filename\*=UTF-8''([^;]+)/i.exec(disposition)
  if (star) {
    try {
      return decodeURIComponent(star[1])
    } catch {
      /* ignore */
    }
  }
  const plain = /filename="?([^";]+)"?/i.exec(disposition)
  if (plain) return plain[1]
  return fallback
}

/** 取 blob 流 + 文件名 + 内容类型（带 Bearer，拦截器已注入）。 */
export async function fetchAttachment(url: string): Promise<AttachmentContent> {
  const safe = stripApiPrefix(url)
  if (!isPathSafe(safe)) throw new BizError(ERROR_CODE.PARAM_ERROR, '非法的附件地址')
  const resp = await http.get<Blob>(safe, { responseType: 'blob' })
  const contentType = (resp.headers?.['content-type'] as string) || 'application/octet-stream'
  const fileName = extractFilename(resp.headers?.['content-disposition'] as string)
  return { blob: resp.data as Blob, fileName, contentType }
}

// ===== 引用计数 blob URL 缓存（上限 20，LRU 回收）=====
const PREVIEW_MIME = ['image/png', 'image/jpeg', 'image/gif', 'image/webp', 'application/pdf', 'text/plain']
const FORBID_INLINE = ['text/html', 'image/svg+xml']

class AttachmentBlobCache {
  private urls = new Map<string, { url: string; blob: Blob; count: number; at: number }>()
  private readonly limit = 20

  acquire(key: string, blob: Blob): string {
    const exist = this.urls.get(key)
    if (exist) {
      exist.count += 1
      return exist.url
    }
    const url = URL.createObjectURL(blob)
    this.urls.set(key, { url, blob, count: 1, at: Date.now() })
    this.evict()
    return url
  }

  release(key: string): void {
    const item = this.urls.get(key)
    if (!item) return
    item.count -= 1
    if (item.count <= 0) {
      URL.revokeObjectURL(item.url)
      this.urls.delete(key)
    }
  }

  private evict(): void {
    if (this.urls.size <= this.limit) return
    let oldest: string | null = null
    let oldestAt = Infinity
    for (const [k, v] of this.urls) {
      if (v.count <= 0 && v.at < oldestAt) {
        oldestAt = v.at
        oldest = k
      }
    }
    if (!oldest) return
    const item = this.urls.get(oldest)!
    URL.revokeObjectURL(item.url)
    this.urls.delete(oldest)
  }
}

export const attachmentBlobCache = new AttachmentBlobCache()

/** 预览/下载统一处理：MIME 白名单 + noopener；预览仅在白名单内联，否则降级下载。 */
export async function openAttachment(opts: {
  mode: 'download' | 'preview'
  url: string
  fileName?: string
  contentType?: string
}): Promise<void> {
  const { blob, fileName, contentType } = await fetchAttachment(opts.url)
  const key = opts.url
  const blobUrl = attachmentBlobCache.acquire(key, blob)
  const name = opts.fileName || fileName

  const canPreview = PREVIEW_MIME.includes(contentType) && !FORBID_INLINE.includes(contentType)

  if (opts.mode === 'preview' && canPreview) {
    const win = window.open(blobUrl, '_blank', 'noopener,noreferrer')
    if (win) {
      const release = () => attachmentBlobCache.release(key)
      win.addEventListener('pagehide', release)
      win.addEventListener('beforeunload', release)
    } else {
      triggerDownload(blobUrl, name)
      setTimeout(() => attachmentBlobCache.release(key), 1000)
    }
  } else {
    triggerDownload(blobUrl, name)
    setTimeout(() => attachmentBlobCache.release(key), 1000)
  }
}

function triggerDownload(blobUrl: string, fileName: string): void {
  const a = document.createElement('a')
  a.href = blobUrl
  a.download = fileName
  document.body.appendChild(a)
  a.click()
  a.remove()
}
