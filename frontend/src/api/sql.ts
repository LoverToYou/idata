import request from './request'
import type { ApiResult } from '@/types'

export interface SqlExecuteResult {
  columns: string[]
  rows: Record<string, any>[]
  affectedRows: number
  elapsedMs: number
  errorMessage?: string
}

export interface ExplainRow {
  id: string
  selectType: string
  table: string
  partitions: string
  type: string
  possibleKeys: string
  key: string
  keyLen: string
  ref: string
  rows: string
  filtered: string
  extra: string
  depth: number
  children?: ExplainRow[]
}

export interface ExplainPlanResult {
  plan: ExplainRow[]
  rawPlan: string
  elapsedMs: number
}

export interface SqlSuggestion {
  type: string
  title: string
  detail: string
  suggestion: string
}

export interface SqlAnalysis {
  valid: boolean
  errorMessage: string
  tables: string[]
  columns: string[]
  queryType: string
  joinTypes: string[]
  suggestions: SqlSuggestion[]
}

export function executeSql(datasourceId: number, sql: string): Promise<ApiResult<SqlExecuteResult>> {
  return request.post('/sql/execute', { datasourceId, sql })
}

export function explainSql(datasourceId: number, sql: string): Promise<ApiResult<ExplainPlanResult>> {
  return request.post('/sql/explain', { datasourceId, sql })
}

export function analyzeSql(sql: string): Promise<ApiResult<{ analysis: SqlAnalysis }>> {
  return request.post('/sql/analyze', { sql })
}

export function fullAnalyze(datasourceId: number, sql: string): Promise<ApiResult<{
  analysis: SqlAnalysis
  plan: ExplainPlanResult
  suggestions: SqlSuggestion[]
}>> {
  return request.post('/sql/full-analyze', { datasourceId, sql })
}

export function formatSql(sql: string): Promise<ApiResult<{ formatted: string }>> {
  return request.post('/sql/format', { sql })
}
