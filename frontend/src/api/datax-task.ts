import request from './request'
import type { ApiResult } from '@/types'

export interface FieldMapping {
  readerColumn: string
  writerColumn: string
  targetType: string
  maskingRuleId: number | null
}

export interface DataxTask {
  id: number
  name: string
  description?: string
  readerDatasourceId?: number | null
  readerDatabase?: string
  readerTable?: string
  readerColumns?: string[]
  readerWhere?: string
  writerDatasourceId?: number | null
  writerDatabase?: string
  writerTable?: string
  writerColumns?: string[]
  writeMode?: string
  fieldMappings?: FieldMapping[]
  channel?: number
  splitPk?: string
  batchSize?: number
  encoding?: string
  preSql?: string
  postSql?: string
  configMode?: string
  scriptContent?: string
  status?: string
  createdAt: string
  updatedAt: string
}

export interface DataxTaskRequest {
  id?: number
  name: string
  description?: string
  readerDatasourceId?: number | null
  readerDatabase?: string
  readerTable?: string
  readerColumns?: string[]
  readerWhere?: string
  writerDatasourceId?: number | null
  writerDatabase?: string
  writerTable?: string
  writerColumns?: string[]
  writeMode?: string
  fieldMappings?: FieldMapping[]
  channel?: number
  splitPk?: string
  batchSize?: number
  encoding?: string
  preSql?: string
  postSql?: string
  configMode?: string
  scriptContent?: string
}

export function listDataxTasks(): Promise<ApiResult<DataxTask[]>> {
  return request.get('/datax-task/list')
}

export function getDataxTask(id: number): Promise<ApiResult<DataxTask>> {
  return request.get(`/datax-task/${id}`)
}

export function createDataxTask(data: DataxTaskRequest): Promise<ApiResult<DataxTask>> {
  return request.post('/datax-task/create', data)
}

export function updateDataxTask(data: DataxTaskRequest): Promise<ApiResult<DataxTask>> {
  return request.put('/datax-task/update', data)
}

export function deleteDataxTask(id: number): Promise<ApiResult<null>> {
  return request.delete(`/datax-task/${id}`)
}

export function publishDataxTask(id: number): Promise<ApiResult<DataxTask>> {
  return request.post(`/datax-task/${id}/publish`)
}

export function unpublishDataxTask(id: number): Promise<ApiResult<DataxTask>> {
  return request.post(`/datax-task/${id}/unpublish`)
}

export function getDataxTaskJson(id: number): Promise<ApiResult<{ dataxJson: string }>> {
  return request.get(`/datax-task/${id}/datax-json`)
}
