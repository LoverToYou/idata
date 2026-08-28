import request from './request'
import type { ApiResult, PageResult, UdfDefinition, UdfDefinitionRequest } from '@/types'

// --- CRUD ---

export function createUdf(data: UdfDefinitionRequest): Promise<ApiResult<UdfDefinition>> {
  return request.post('/udf/create', data)
}

export function listUdfsPage(
  keyword?: string,
  datasourceId?: number,
  registerStatus?: string,
  page: number = 1,
  pageSize: number = 10
): Promise<ApiResult<PageResult<UdfDefinition>>> {
  const params: Record<string, any> = { page, pageSize }
  if (keyword) params.keyword = keyword
  if (datasourceId) params.datasourceId = datasourceId
  if (registerStatus) params.registerStatus = registerStatus
  return request.get('/udf/page', { params })
}

export function getUdf(id: number): Promise<ApiResult<UdfDefinition>> {
  return request.get(`/udf/${id}`)
}

export function updateUdf(data: UdfDefinitionRequest): Promise<ApiResult<UdfDefinition>> {
  return request.put('/udf/update', data)
}

export function deleteUdf(id: number): Promise<ApiResult<null>> {
  return request.delete(`/udf/${id}`)
}

// --- 注册 ---

export function registerUdf(id: number): Promise<ApiResult<UdfDefinition>> {
  return request.post(`/udf/${id}/register`)
}

export function unregisterUdf(id: number): Promise<ApiResult<UdfDefinition>> {
  return request.post(`/udf/${id}/unregister`)
}

export function verifyUdf(id: number): Promise<ApiResult<UdfDefinition>> {
  return request.post(`/udf/${id}/verify`)
}

// --- SQL 编辑器加载 ---

export function listUdfsByDatasource(datasourceId: number, databaseName?: string): Promise<ApiResult<UdfDefinition[]>> {
  const params: Record<string, any> = { datasourceId }
  if (databaseName) params.databaseName = databaseName
  return request.get('/udf/by-datasource', { params })
}
