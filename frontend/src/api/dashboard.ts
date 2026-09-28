import request from './request'
import type { ApiResult } from '@/types'
import type { SqlResult } from './report'

export interface DashboardItem {
  id?: number
  reportId: number
  reportName?: string
  chartType?: string
  chartConfig?: string
  title?: string
  posX: number
  posY: number
  width: number
  height: number
  /** 阈值告警配置 JSON: {field,operator,value,level} */
  alertConfig?: string
}

export interface DashboardFilter {
  key: string
  label?: string
  type: 'text' | 'select' | 'date' | 'daterange'
  defaultValue?: string
  /** select 类型的选项，逗号分隔 */
  options?: string
}

export interface DashboardInfo {
  id?: number
  name: string
  description?: string
  refreshInterval: number
  filters?: string
  fitScale?: boolean
  folderId?: number | null
  items: DashboardItem[]
  createdAt?: string
  updatedAt?: string
}

export interface DashboardCardData extends SqlResult {
  reportId: number
  title: string
  chartType: string
  chartConfig?: string
}

export interface DashboardData {
  cards: DashboardCardData[]
}

export function listDashboards(): Promise<ApiResult<DashboardInfo[]>> {
  return request.get('/dashboard/list')
}

export function getDashboard(id: number): Promise<ApiResult<DashboardInfo>> {
  return request.get(`/dashboard/${id}`)
}

export function createDashboard(data: Partial<DashboardInfo>): Promise<ApiResult<DashboardInfo>> {
  return request.post('/dashboard/create', data)
}

export function updateDashboard(data: Partial<DashboardInfo>): Promise<ApiResult<DashboardInfo>> {
  return request.put('/dashboard/update', data)
}

export function deleteDashboard(id: number): Promise<ApiResult<null>> {
  return request.delete(`/dashboard/${id}`)
}

/** 大屏取数：一次性拿到看板所有卡片的数据（可传全局筛选参数） */
export function getDashboardData(id: number, params?: Record<string, string>): Promise<ApiResult<DashboardData>> {
  return request.post(`/dashboard/${id}/data`, { params: params || {} })
}

/** 复制看板 */
export function copyDashboard(id: number): Promise<ApiResult<DashboardInfo>> {
  return request.post(`/dashboard/${id}/copy`)
}

/** 移动看板到文件夹（folderId 传 null 表示移出到未分组） */
export function moveDashboard(id: number, folderId: number | null): Promise<ApiResult<DashboardInfo>> {
  return request.put(`/dashboard/${id}/move`, { folderId })
}
