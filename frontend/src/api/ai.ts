import { del, get, post, put } from './http'

/** AI 模型配置（后台，仅 ADMIN）。API Key 只写不读。 */
export interface AiModelVO {
  id: number
  name: string
  provider: string
  baseUrl: string
  model: string
  temperature: number
  maxTokens: number
  timeoutSeconds: number
  enabled: boolean
  active: boolean
  hasApiKey: boolean
  createdAt?: string
  updatedAt?: string
}

export interface AiModelSaveReq {
  name: string
  provider: string
  baseUrl: string
  apiKey?: string
  model: string
  temperature?: number
  maxTokens?: number
  timeoutSeconds?: number
  enabled?: boolean
}

export interface AiModelTestResult {
  ok: boolean
  message: string
  latencyMs: number
  model: string
}

export function listAiModels(): Promise<AiModelVO[]> {
  return get<AiModelVO[]>('/admin/ai/models')
}

export function createAiModel(payload: AiModelSaveReq): Promise<AiModelVO> {
  return post<AiModelVO>('/admin/ai/models', payload)
}

export function updateAiModel(id: number, payload: AiModelSaveReq): Promise<AiModelVO> {
  return put<AiModelVO>(`/admin/ai/models/${id}`, payload)
}

export function deleteAiModel(id: number): Promise<void> {
  return del<void>(`/admin/ai/models/${id}`)
}

export function activateAiModel(id: number): Promise<AiModelVO> {
  return post<AiModelVO>(`/admin/ai/models/${id}/activate`)
}

export function testAiModel(id: number): Promise<AiModelTestResult> {
  return post<AiModelTestResult>(`/admin/ai/models/${id}/test`)
}
