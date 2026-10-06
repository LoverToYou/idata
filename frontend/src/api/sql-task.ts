import request from './request'
import type { ApiResult, PageResult } from '@/types'

export interface SqlTask {
  id: number
  name: string
  description?: string
  datasourceId?: number
  sqlContent: string
  engine?: string
  datasourceType?: string
  datasourceConnected?: boolean | null
  status?: string
  createdAt: string
  updatedAt: string
}

export interface SqlTaskRequest {
  id?: number
  name: string
  description?: string
  datasourceId?: number | null
  sqlContent: string
  /** 执行引擎：HIVE / SPARK（空=跟随数据源默认） */
  engine?: string | null
}

export function listTasks(keyword?: string): Promise<ApiResult<SqlTask[]>> {
  const params = keyword ? { keyword } : {}
  return request.get('/sql-task/list', { params })
}

export function listTasksPage(keyword?: string, page: number = 1, pageSize: number = 10): Promise<ApiResult<PageResult<SqlTask>>> {
  const params: Record<string, any> = { page, pageSize }
  if (keyword) params.keyword = keyword
  return request.get('/sql-task/page', { params })
}

export function getTask(id: number): Promise<ApiResult<SqlTask>> {
  return request.get(`/sql-task/${id}`)
}

export function createTask(data: SqlTaskRequest): Promise<ApiResult<SqlTask>> {
  return request.post('/sql-task/create', data)
}

export function updateTask(data: SqlTaskRequest): Promise<ApiResult<SqlTask>> {
  return request.put('/sql-task/update', data)
}

export function deleteTask(id: number): Promise<ApiResult<null>> {
  return request.delete(`/sql-task/${id}`)
}

export function deleteTaskBatch(ids: number[]): Promise<ApiResult<null>> {
  return request.delete('/sql-task/batch', { data: ids })
}

export function publishTask(id: number): Promise<ApiResult<SqlTask>> {
  return request.post(`/sql-task/${id}/publish`)
}

export function unpublishTask(id: number): Promise<ApiResult<SqlTask>> {
  return request.post(`/sql-task/${id}/unpublish`)
}
