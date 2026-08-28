import request from './request'
import type { ApiResult, PageResult, PythonRun, PythonScript } from '@/types'

// --- 脚本 CRUD ---

export function listScripts(keyword?: string): Promise<ApiResult<PythonScript[]>> {
  const params = keyword ? { keyword } : {}
  return request.get('/python-task/list', { params })
}

export function listScriptsPage(keyword?: string, page: number = 1, pageSize: number = 10): Promise<ApiResult<PageResult<PythonScript>>> {
  const params: Record<string, any> = { page, pageSize }
  if (keyword) params.keyword = keyword
  return request.get('/python-task/page', { params })
}

export function getScript(id: number): Promise<ApiResult<PythonScript>> {
  return request.get(`/python-task/${id}`)
}

export function createScript(data: {
  name: string
  description?: string
  content: string
  timeoutSeconds?: number
  createdBy?: string
}): Promise<ApiResult<PythonScript>> {
  return request.post('/python-task/create', data)
}

export function updateScript(data: {
  id: number
  name: string
  description?: string
  content: string
  timeoutSeconds?: number
}): Promise<ApiResult<PythonScript>> {
  return request.put('/python-task/update', data)
}

export function publishScript(id: number): Promise<ApiResult<PythonScript>> {
  return request.post(`/python-task/${id}/publish`)
}

export function unpublishScript(id: number): Promise<ApiResult<PythonScript>> {
  return request.post(`/python-task/${id}/unpublish`)
}

export function deleteScript(id: number): Promise<ApiResult<null>> {
  return request.delete(`/python-task/${id}`)
}

export function deleteScriptBatch(ids: number[]): Promise<ApiResult<null>> {
  return request.delete('/python-task/batch', { data: ids })
}

// --- 执行 ---

export function runScript(id: number, params?: string): Promise<ApiResult<number>> {
  return request.post(`/python-task/${id}/run`, { params })
}

export function listRuns(scriptId?: number, page: number = 1, pageSize: number = 10): Promise<ApiResult<PageResult<PythonRun>>> {
  const params: Record<string, any> = { page, pageSize }
  if (scriptId) params.scriptId = scriptId
  return request.get('/python-task/runs', { params })
}

export function getRun(runId: number): Promise<ApiResult<PythonRun>> {
  return request.get(`/python-task/runs/${runId}`)
}

export function cancelRun(runId: number): Promise<ApiResult<null>> {
  return request.post(`/python-task/runs/${runId}/cancel`)
}
