import axios, { type AxiosInstance, type AxiosRequestConfig, type AxiosError } from 'axios'
import { ERROR_CODE, messageOf } from '@/utils/errorCode'
import type { components } from '@/types/api'
import { getAccessToken, getRefreshToken, setAccessToken, setRefreshToken, clearTokens } from './token'

/** 后端统一响应体（02 §1.3）。 */
export interface ApiResult<T> {
  code: number
  message: string
  data: T
  traceId: string
}

/** 通用文件 VO（downloadUrl 为相对路径，由 http 层透传、不拼前缀）。 */
export type FileVO = components['schemas']['FileVO'] & { md5?: string }

/** 业务错误（HTTP 200 + code≠0）。 */
export class BizError extends Error {
  readonly code: number
  readonly traceId?: string

  constructor(code: number, message: string, traceId?: string) {
    super(message)
    this.name = 'BizError'
    this.code = code
    this.traceId = traceId
  }
}

/**
 * 协议级错误（HTTP 4xx/5xx），携带 status，页面统一读 err.status===404/403。
 *
 * 继承 `BizError` 是有意的：后端 4xx/5xx **同样带统一响应体**（`{code,message,traceId}`），
 * 页面既要用 `err.status` 分派空态，也要用 `err.code` 判定 50001/50016 等业务码。
 * 若把 4xx/5xx 只包成 `BizError`，`status` 就丢了（`/notes/:id` 404 会退化成「加载失败」而不是空态）；
 * 若只包成 `HttpError`，`instanceof BizError` 的分支又会失配。两者合一即消除这对二选一。
 */
export class HttpError extends BizError {
  readonly status: number

  constructor(status: number, code?: number, message?: string, traceId?: string) {
    super(code ?? ERROR_CODE.SYSTEM_ERROR, message || `HTTP ${status}`, traceId)
    this.name = 'HttpError'
    this.status = status
  }
}

const baseURL = import.meta.env.VITE_API_BASE_URL || '/api/v1'

export const http: AxiosInstance = axios.create({
  baseURL,
  timeout: 20000,
  headers: { 'Content-Type': 'application/json' },
})

http.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`)
  }
  // FormData 必须由浏览器自己生成 `multipart/form-data; boundary=...`。
  // 实例默认头 `Content-Type: application/json` 会让 axios 把 FormData 序列化成 `{}`（body 11 字节），
  // 后端拿不到 part → 上传静默失败（曾表现为「POST /files 200 但附件没落地」）。
  if (typeof FormData !== 'undefined' && config.data instanceof FormData) {
    config.headers.delete('Content-Type')
  }
  return config
})

let refreshing: Promise<string | null> | null = null

async function doRefresh(): Promise<string | null> {
  const refreshToken = getRefreshToken()
  if (!refreshToken) return null
  try {
    const resp = await axios.post<ApiResult<{ accessToken: string; refreshToken: string }>>(
      `${baseURL}/auth/refresh`,
      { refreshToken },
    )
    if (resp.data.code === ERROR_CODE.OK) {
      setAccessToken(resp.data.data.accessToken)
      setRefreshToken(resp.data.data.refreshToken)
      return resp.data.data.accessToken
    }
    return null
  } catch {
    return null
  }
}

function redirectToLogin(): void {
  clearTokens()
  if (!location.pathname.startsWith('/login')) {
    location.assign('/login')
  }
}

function isApiResult(v: unknown): v is ApiResult<unknown> {
  return !!v && typeof v === 'object' && typeof (v as ApiResult<unknown>).code === 'number'
}

function toBizError(body: ApiResult<unknown>): BizError {
  return new BizError(body.code, body.message || messageOf(body.code), body.traceId)
}

// 成功拦截：JSON 解包（code≠0 抛 BizError）；blob 仅在确为 JSON 错误时解析处理
http.interceptors.response.use(
  async (response) => {
    const isBlob = response.config.responseType === 'blob'
    if (isBlob) {
      const ct = (response.headers?.['content-type'] || '') as string
      if (ct.includes('application/json') && response.data instanceof Blob) {
        const text = await response.data.text()
        try {
          const parsed = JSON.parse(text)
          if (isApiResult(parsed) && parsed.code !== ERROR_CODE.OK) {
            throw toBizError(parsed)
          }
        } catch {
          // 非 JSON：当作正常 blob 落盘
        }
      }
      return response
    }
    const body = response.data
    if (isApiResult(body) && body.code !== ERROR_CODE.OK) {
      throw toBizError(body)
    }
    return response
  },
  async (error: AxiosError<ApiResult<unknown>>) => {
    const resp = error.response
    const status = resp?.status ?? 0
    const original = error.config as AxiosRequestConfig & { _retried?: boolean }

    // 解析 body（blob 需读文本）
    let body: ApiResult<unknown> | null = null
    if (resp?.data instanceof Blob) {
      try {
        const text = await resp.data.text()
        try {
          body = JSON.parse(text)
        } catch {
          body = null
        }
      } catch {
        body = null
      }
    } else if (isApiResult(resp?.data)) {
      body = resp.data
    }

    // 401 窄化续签：仅 HTTP 401 且 code∈{10002,20005}
    if (
      body &&
      status === 401 &&
      (body.code === ERROR_CODE.UNAUTHENTICATED || body.code === ERROR_CODE.TOKEN_INVALID) &&
      !original._retried
    ) {
      original._retried = true
      refreshing = refreshing ?? doRefresh()
      const token = await refreshing
      refreshing = null
      if (token) {
        return http.request(original)
      }
      redirectToLogin()
    }

    // 协议级错误（4xx/5xx）：抛 HttpError（含 status + body.code）；status=0 为网络/取消，code 取 SYSTEM_ERROR。
    // 注意：这里**不能**降级成 BizError——否则 `err.status===404/403` 的分派（详情空态、列表 403 空态）全部失效。
    return Promise.reject(new HttpError(status, body?.code, body?.message || error.message, body?.traceId))
  },
)

/** 解包业务响应：code==0 返回 data，否则抛 BizError。 */
function unwrap<T>(result: ApiResult<T>): T {
  if (result.code === ERROR_CODE.OK) return result.data
  throw new BizError(result.code, result.message || messageOf(result.code), result.traceId)
}

export async function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  const resp = await http.get<ApiResult<T>>(url, { params })
  return unwrap(resp.data)
}

export async function post<T>(url: string, data?: unknown): Promise<T> {
  const resp = await http.post<ApiResult<T>>(url, data)
  return unwrap(resp.data)
}

export async function put<T>(url: string, data?: unknown): Promise<T> {
  const resp = await http.put<ApiResult<T>>(url, data)
  return unwrap(resp.data)
}

export async function del<T>(url: string, data?: unknown): Promise<T> {
  const resp = await http.delete<ApiResult<T>>(url, { data })
  return unwrap(resp.data)
}

/** blob 下载（带 Bearer，拦截器已注入）；错误由拦截器抛 HttpError/BizError。 */
export async function getBlob(url: string, params?: Record<string, unknown>): Promise<Blob> {
  const resp = await http.get<Blob>(url, { params, responseType: 'blob' })
  return resp.data
}
