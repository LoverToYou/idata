import request from './request'
import type { DatasourceConfig, DatasourceRequest, ConnectionTestRequest, ApiResult, PageResult } from '@/types'

export function listDatasources(keyword?: string): Promise<ApiResult<DatasourceConfig[]>> {
  const params = keyword ? { keyword } : {}
  return request.get('/datasource/list', { params })
}

export function listDatasourcesPage(keyword?: string, page: number = 1, pageSize: number = 10): Promise<ApiResult<PageResult<DatasourceConfig>>> {
  const params: Record<string, any> = { page, pageSize }
  if (keyword) params.keyword = keyword
  return request.get('/datasource/page', { params })
}

export function getDatasource(id: number): Promise<ApiResult<DatasourceConfig>> {
  return request.get(`/datasource/${id}`)
}

export function createDatasource(data: DatasourceRequest): Promise<ApiResult<DatasourceConfig>> {
  return request.post('/datasource/create', data)
}

export function updateDatasource(data: DatasourceRequest): Promise<ApiResult<DatasourceConfig>> {
  return request.put('/datasource/update', data)
}

export function deleteDatasource(id: number): Promise<ApiResult<null>> {
  return request.delete(`/datasource/${id}`)
}

export function deleteDatasourceBatch(ids: number[]): Promise<ApiResult<null>> {
  return request.delete('/datasource/batch', { data: ids })
}

export function testConnection(data: ConnectionTestRequest): Promise<ApiResult<boolean>> {
  return request.post('/datasource/test-connection', data)
}

export function testConnectionById(id: number): Promise<ApiResult<boolean>> {
  return request.post(`/datasource/test-connection/${id}`)
}

// Hive metadata
export function listHiveDatabases(id: number): Promise<ApiResult<string[]>> {
  return request.get(`/datasource/${id}/hive/databases`)
}

export function listHiveTables(id: number, database: string): Promise<ApiResult<string[]>> {
  return request.get(`/datasource/${id}/hive/${database}/tables`)
}

export function getHiveTableSchema(id: number, database: string, table: string): Promise<ApiResult<any[]>> {
  return request.get(`/datasource/${id}/hive/${database}/tables/${table}/schema`)
}

export function getHiveTablePartitions(id: number, database: string, table: string): Promise<ApiResult<any[]>> {
  return request.get(`/datasource/${id}/hive/${database}/tables/${table}/partitions`)
}

// Generic JDBC metadata
export function listAccessibleDatabases(id: number): Promise<ApiResult<string[]>> {
  return request.get(`/datasource/${id}/accessible-databases`)
}

export function listDatasourceDatabases(id: number): Promise<ApiResult<string[]>> {
  return request.get(`/datasource/${id}/databases`)
}

export function listDatasourceTables(id: number, database?: string): Promise<ApiResult<any[]>> {
  const params = database ? { database } : {}
  return request.get(`/datasource/${id}/tables`, { params })
}

export function getDatasourceTableColumns(id: number, tableName: string, database?: string): Promise<ApiResult<any[]>> {
  const params = database ? { database } : {}
  return request.get(`/datasource/${id}/tables/${tableName}/columns`, { params })
}

export function getTableDdl(id: number, tableName: string, database?: string): Promise<ApiResult<string>> {
  const params = database ? { database } : {}
  return request.get(`/datasource/${id}/tables/${tableName}/ddl`, { params })
}

export function getTableComment(id: number, tableName: string, database?: string): Promise<ApiResult<string>> {
  const params = database ? { database } : {}
  return request.get(`/datasource/${id}/tables/${tableName}/comment`, { params })
}
