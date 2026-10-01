import { http, BizError } from './http'
import { messageOf } from '@/utils/errorCode'

/** 从 Content-Disposition 解析文件名（优先 filename*=UTF-8''）。 */
function fileNameOf(disposition: string, fallback: string): string {
  const utf8 = /filename\*=UTF-8''([^;]+)/i.exec(disposition)
  if (utf8?.[1]) {
    try {
      return decodeURIComponent(utf8[1])
    } catch {
      return fallback
    }
  }
  const plain = /filename="?([^";]+)"?/i.exec(disposition)
  return plain?.[1] ?? fallback
}

/**
 * 带鉴权地下载文件（blob + 触发浏览器保存）。
 *
 * 直接用 <a href> 指向接口是拿不到 JWT 的，故走 axios（拦截器会带 Authorization）。
 * 统一响应约定为 HTTP 200 + 体内 code：若后端返回的是 JSON 错误体（而非文件流），
 * 这里解析出来抛 BizError，避免把一段 JSON 当文件存下来。
 *
 * @returns 实际保存的文件名
 */
export async function downloadFile(url: string, fallbackName: string): Promise<string> {
  const resp = await http.get(url, { responseType: 'blob' })
  const contentType = String(resp.headers['content-type'] ?? '')
  if (contentType.includes('application/json')) {
    const body = JSON.parse(await (resp.data as Blob).text()) as { code: number; message?: string; traceId?: string }
    throw new BizError(body.code, body.message || messageOf(body.code), body.traceId)
  }
  const name = fileNameOf(String(resp.headers['content-disposition'] ?? ''), fallbackName)
  const objectUrl = URL.createObjectURL(resp.data as Blob)
  const a = document.createElement('a')
  a.href = objectUrl
  a.download = name
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(objectUrl)
  return name
}
