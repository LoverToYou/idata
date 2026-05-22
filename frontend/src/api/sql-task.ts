import request from './request'
import type { ApiResult } from '@/types'

export interface SqlTask {
  id: number
  name: string
  description?: string
  datasourceId?: number
  sqlContent: string
  sqlType?: string
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
  sqlType?: string
}

export function listTasks(): Promise<ApiResult<SqlTask[]>> {
  return request.get('/sql-task/list')
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

export function publishTask(id: number): Promise<ApiResult<SqlTask>> {
  return request.post(`/sql-task/${id}/publish`)
}

export function unpublishTask(id: number): Promise<ApiResult<SqlTask>> {
  return request.post(`/sql-task/${id}/unpublish`)
}
