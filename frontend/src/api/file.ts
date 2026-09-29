import { http } from './http'
import { ERROR_CODE, messageOf } from '@/utils/errorCode'
import { BizError } from './http'
import type { components } from '@/types/api'

export type FileVO = components['schemas']['FileVO'] & { md5?: string }

/** 上传附件（multipart，taskId 可选）。 */
export async function uploadFile(file: File, taskId?: number): Promise<FileVO> {
  const form = new FormData()
  form.append('file', file)
  if (taskId != null) form.append('taskId', String(taskId))
  const resp = await http.post<{ code: number; message: string; data: FileVO; traceId?: string }>(
    '/files',
    form,
    { headers: { 'Content-Type': 'multipart/form-data' } },
  )
  if (resp.data.code !== ERROR_CODE.OK) {
    throw new BizError(resp.data.code, resp.data.message || messageOf(resp.data.code), resp.data.traceId)
  }
  return resp.data.data
}

/** 下载地址（按 id 访问，不暴露物理路径）。 */
export function fileDownloadUrl(id: number): string {
  return `${http.defaults.baseURL}/files/${id}`
}

export function filePreviewUrl(id: number): string {
  return `${http.defaults.baseURL}/files/${id}/preview`
}

export async function deleteFile(id: number): Promise<void> {
  const resp = await http.delete<{ code: number; message: string }>(`/files/${id}`)
  if (resp.data.code !== ERROR_CODE.OK) {
    throw new BizError(resp.data.code, resp.data.message || messageOf(resp.data.code))
  }
}
