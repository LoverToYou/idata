import request from './request'
import type { DatasourceConfig, DatasourceRequest, ConnectionTestRequest, ApiResult } from '@/types'

export function listDatasources(): Promise<ApiResult<DatasourceConfig[]>> {
  return request.get('/datasource/list')
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
