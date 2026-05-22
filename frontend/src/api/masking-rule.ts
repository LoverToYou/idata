import request from './request'
import type { ApiResult } from '@/types'

export interface MaskingRule {
  id: number
  name: string
  type: string
  config?: string
  description?: string
  createdAt: string
  updatedAt: string
}

export interface MaskingRuleRequest {
  id?: number
  name: string
  type: string
  config?: string
  description?: string
}

export function listMaskingRules(): Promise<ApiResult<MaskingRule[]>> {
  return request.get('/masking-rule/list')
}

export function getMaskingRule(id: number): Promise<ApiResult<MaskingRule>> {
  return request.get(`/masking-rule/${id}`)
}

export function createMaskingRule(data: MaskingRuleRequest): Promise<ApiResult<MaskingRule>> {
  return request.post('/masking-rule/create', data)
}

export function updateMaskingRule(data: MaskingRuleRequest): Promise<ApiResult<MaskingRule>> {
  return request.put('/masking-rule/update', data)
}

export function deleteMaskingRule(id: number): Promise<ApiResult<null>> {
  return request.delete(`/masking-rule/${id}`)
}
