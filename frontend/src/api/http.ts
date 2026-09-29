import axios, { type AxiosInstance, type AxiosRequestConfig, type AxiosError } from 'axios'
import { ERROR_CODE, messageOf } from '@/utils/errorCode'
import { getAccessToken, getRefreshToken, setAccessToken, setRefreshToken, clearTokens } from './token'

/** 后端统一响应体（02 §1.3）。 */
export interface ApiResult<T> {
  code: number
  message: string
  data: T
  traceId: string
}

/** 业务错误（携带 code/message/traceId，03 §4.1）。 */
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

// 响应拦截：解包 ApiResult；10002/20005 触发续签重试（03 §4.1）
http.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiResult<unknown>>) => {
    const resp = error.response
    const original = error.config as AxiosRequestConfig & { _retried?: boolean }
    const body = resp?.data

    if (body && typeof body.code === 'number' && !original._retried) {
      if (body.code === ERROR_CODE.TOKEN_INVALID) {
        original._retried = true
        refreshing = refreshing ?? doRefresh()
        const token = await refreshing
        refreshing = null
        if (token) {
          return http.request(original)
        }
        redirectToLogin()
      }
    }
    return Promise.reject(toBizError(error))
  },
)

function toBizError(error: AxiosError<ApiResult<unknown>>): BizError {
  const body = error.response?.data
  if (body && typeof body.code === 'number') {
    return new BizError(body.code, body.message || messageOf(body.code), body.traceId)
  }
  return new BizError(ERROR_CODE.SYSTEM_ERROR, error.message || messageOf(ERROR_CODE.SYSTEM_ERROR))
}

/** 解包业务响应：code==0 返回 data，否则抛 BizError。 */
function unwrap<T>(result: ApiResult<T>): T {
  if (result.code === ERROR_CODE.OK) {
    return result.data
  }
  if (result.code === ERROR_CODE.UNAUTHENTICATED) {
    redirectToLogin()
  }
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
