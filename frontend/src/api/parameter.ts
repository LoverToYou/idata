import request from './request'
import type { ApiResult } from '@/types'

export interface Parameter {
  id: number
  paramName: string
  paramValue: string
  paramType: 'STATIC' | 'DYNAMIC'
  expression: string
  description: string
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export interface ParameterRequest {
  id?: number
  paramName: string
  paramValue?: string
  paramType: 'STATIC' | 'DYNAMIC'
  expression?: string
  description?: string
  enabled?: boolean
}

export function listParameters(): Promise<ApiResult<Parameter[]>> {
  return request.get('/parameter/list')
}

export function getParameter(id: number): Promise<ApiResult<Parameter>> {
  return request.get(`/parameter/${id}`)
}

export function createParameter(data: ParameterRequest): Promise<ApiResult<Parameter>> {
  return request.post('/parameter/create', data)
}

export function updateParameter(data: ParameterRequest): Promise<ApiResult<Parameter>> {
  return request.put('/parameter/update', data)
}

export function deleteParameter(id: number): Promise<ApiResult<null>> {
  return request.delete(`/parameter/${id}`)
}

export function resolveParams(sql: string): Promise<ApiResult<{ resolvedSql: string; resolvedParams: Record<string, string> }>> {
  return request.post('/parameter/resolve', { sql })
}

export function executeBuiltinSql(sql: string): Promise<ApiResult<{
  success: boolean
  columns?: string[]
  rows?: Record<string, any>[]
  affectedRows?: number
  elapsedMs?: number
  errorMessage?: string
}>> {
  return request.post('/parameter/execute-sql', { sql })
}
