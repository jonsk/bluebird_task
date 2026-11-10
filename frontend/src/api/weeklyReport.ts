import { del, get, post, put } from './http'
import type {
  WeeklyReportGenerateReq,
  WeeklyReportListData,
  WeeklyReportPreference,
  WeeklyReportUpdateReq,
  WeeklyReportVO,
} from '@/types/weeklyReport'

export function listWeeklyReports(page = 1, size = 20): Promise<WeeklyReportListData> {
  return get<WeeklyReportListData>('/weekly-reports', { page, size })
}

export function getWeeklyReport(id: number): Promise<WeeklyReportVO> {
  return get<WeeklyReportVO>(`/weekly-reports/${id}`)
}

export function generateWeeklyReport(payload: WeeklyReportGenerateReq): Promise<WeeklyReportVO> {
  return post<WeeklyReportVO>('/weekly-reports/generate', payload)
}

export function updateWeeklyReport(id: number, payload: WeeklyReportUpdateReq): Promise<WeeklyReportVO> {
  return put<WeeklyReportVO>(`/weekly-reports/${id}`, payload)
}

export function deleteWeeklyReport(id: number): Promise<void> {
  return del<void>(`/weekly-reports/${id}`)
}

export function getWeeklyReportPreference(): Promise<WeeklyReportPreference> {
  return get<WeeklyReportPreference>('/weekly-reports/preference')
}

export function saveWeeklyReportPreference(
  payload: WeeklyReportPreference,
): Promise<WeeklyReportPreference> {
  return put<WeeklyReportPreference>('/weekly-reports/preference', payload)
}
