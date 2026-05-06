import request from './request'
import type { ApiResult, WorkflowDefinition } from '@/types'

// --- Workflow CRUD ---

export function listWorkflows(): Promise<ApiResult<WorkflowDefinition[]>> {
  return request.get('/workflow/list')
}

export function getWorkflow(id: number): Promise<ApiResult<WorkflowDefinition>> {
  return request.get(`/workflow/${id}`)
}

export function createWorkflow(data: {
  name: string
  description?: string
  dagJson?: string
}): Promise<ApiResult<WorkflowDefinition>> {
  return request.post('/workflow/create', data)
}

export function updateWorkflow(data: {
  id: number
  name?: string
  description?: string
  dagJson?: string
}): Promise<ApiResult<WorkflowDefinition>> {
  return request.put('/workflow/update', data)
}

export function deleteWorkflow(id: number): Promise<ApiResult<null>> {
  return request.delete(`/workflow/${id}`)
}

export function publishWorkflow(id: number): Promise<ApiResult<WorkflowDefinition>> {
  return request.post(`/workflow/${id}/publish`)
}

export function unpublishWorkflow(id: number): Promise<ApiResult<WorkflowDefinition>> {
  return request.post(`/workflow/${id}/unpublish`)
}

// --- Schedule ---

export function listSchedules(): Promise<ApiResult<any[]>> {
  return request.get('/schedule/list')
}

export function listSchedulesByWorkflow(workflowId: number): Promise<ApiResult<any[]>> {
  return request.get(`/schedule/workflow/${workflowId}`)
}

export function createSchedule(data: {
  workflowId: number
  cronExpression: string
  enabled?: boolean
}): Promise<ApiResult<any>> {
  return request.post('/schedule/create', data)
}

export function updateSchedule(data: any): Promise<ApiResult<any>> {
  return request.put('/schedule/update', data)
}

export function deleteSchedule(id: number): Promise<ApiResult<null>> {
  return request.delete(`/schedule/${id}`)
}

export function toggleSchedule(id: number, enabled: boolean): Promise<ApiResult<any>> {
  return request.put(`/schedule/${id}/toggle?enabled=${enabled}`)
}

// --- Monitor ---

export function listInstances(workflowId?: number): Promise<ApiResult<any[]>> {
  const params = workflowId ? { workflowId } : {}
  return request.get('/monitor/instances', { params })
}

export function getInstance(id: number): Promise<ApiResult<any>> {
  return request.get(`/monitor/instances/${id}`)
}

export function getInstanceNodeLogs(id: number): Promise<ApiResult<any[]>> {
  return request.get(`/monitor/instances/${id}/nodes`)
}

export function runWorkflow(workflowId: number): Promise<ApiResult<any>> {
  return request.post(`/monitor/workflow/${workflowId}/run`)
}
