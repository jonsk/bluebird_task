/** 个人周报（前端手写类型：后端 DTO 与 openapi 生成器解耦，避免新接口需先跑 gen:types）。 */

/** 周报状态：AI 生成即定稿，不再有 DRAFT。 */
export type WeeklyReportStatus = 'GENERATING' | 'DONE' | 'FAILED'

export interface WeeklyReportBrief {
  id: number
  weekStart: string
  weekEnd: string
  status: WeeklyReportStatus
  summary?: string
  errorMessage?: string
  generatedAt?: string
  updatedAt?: string
}

/**
 * 左侧「按周生成」列表项：当前周及之前、且至少有 1 条日志的周。
 * 该周还没有周报时 reportId / status 为空，选中后可直接生成。
 */
export interface WeeklyReportWeek {
  weekStart: string
  weekEnd: string
  noteCount: number
  reportId?: number | null
  status?: WeeklyReportStatus | null
  errorMessage?: string | null
}

export interface WeeklyReportVO {
  id: number
  ownerId: number
  weekStart: string
  weekEnd: string
  status: WeeklyReportStatus
  contentHtml?: string
  aiResultJson?: string
  sourceNoteIds: number[]
  modelName?: string
  errorMessage?: string
  inputTokens?: number
  outputTokens?: number
  generatedAt?: string
  completedAt?: string
  createdAt?: string
  updatedAt?: string
  version?: number
}

export interface WeeklyReportListData {
  list: WeeklyReportBrief[]
  total: number
  page: number
  size: number
}

export interface WeeklyReportGenerateReq {
  weekStart?: string
  overwrite?: boolean
}

export interface WeeklyReportUpdateReq {
  contentHtml: string
  version?: number
}

export type WeeklyReportStyle = 'concise' | 'detailed' | 'result' | 'plan'

export interface WeeklyReportPreference {
  style: WeeklyReportStyle
  focus?: string
  customRules?: string
}
