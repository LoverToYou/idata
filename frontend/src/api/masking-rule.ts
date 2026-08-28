import request from './request'
import type { ApiResult, PageResult } from '@/types'

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

export function listMaskingRules(keyword?: string): Promise<ApiResult<MaskingRule[]>> {
  const params = keyword ? { keyword } : {}
  return request.get('/masking-rule/list', { params })
}

export function listMaskingRulesPage(keyword?: string, page: number = 1, pageSize: number = 10): Promise<ApiResult<PageResult<MaskingRule>>> {
  const params: Record<string, any> = { page, pageSize }
  if (keyword) params.keyword = keyword
  return request.get('/masking-rule/page', { params })
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

export function deleteMaskingRuleBatch(ids: number[]): Promise<ApiResult<null>> {
  return request.delete('/masking-rule/batch', { data: ids })
}
