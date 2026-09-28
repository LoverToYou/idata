import request from './request'
import type { ApiResult } from '@/types'

export interface ReportItem {
  id?: number
  name: string
  description?: string
  datasourceId: number | null
  datasourceName?: string
  sqlContent: string
  chartType: string
  chartConfig?: string
  refreshInterval: number
  folderId?: number | null
  createdAt?: string
  updatedAt?: string
}

export interface SqlResult {
  columns: string[]
  rows: Record<string, any>[]
  affectedRows: number
  elapsedMs: number
  errorMessage?: string | null
}

export function listReports(): Promise<ApiResult<ReportItem[]>> {
  return request.get('/report/list')
}

export function getReport(id: number): Promise<ApiResult<ReportItem>> {
  return request.get(`/report/${id}`)
}

export function createReport(data: Partial<ReportItem>): Promise<ApiResult<ReportItem>> {
  return request.post('/report/create', data)
}

export function updateReport(data: Partial<ReportItem>): Promise<ApiResult<ReportItem>> {
  return request.put('/report/update', data)
}

export function deleteReport(id: number): Promise<ApiResult<null>> {
  return request.delete(`/report/${id}`)
}

/** 移动报表到文件夹（folderId 传 null 表示移出到未分组） */
export function moveReport(id: number, folderId: number | null): Promise<ApiResult<ReportItem>> {
  return request.put(`/report/${id}/move`, { folderId })
}

/** 运行已保存的报表，返回结果集 */
export function runReport(id: number): Promise<ApiResult<SqlResult>> {
  return request.post(`/report/${id}/run`)
}

/** 编辑态预览：执行未保存的 SQL */
export function previewReport(datasourceId: number, sql: string): Promise<ApiResult<SqlResult>> {
  return request.post('/report/preview', { datasourceId, sql })
}
