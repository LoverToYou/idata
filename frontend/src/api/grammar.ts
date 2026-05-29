import request from './request'
import type { ApiResult } from '@/types'

export interface SqlKeywords {
  statements: string[]
  functions: string[]
  types: string[]
  clauses: string[]
}

export interface GrammarContext {
  state: string
  queryType: string
  validKeywords: string[]
  expectsTable: boolean
  expectsColumn: boolean
  expectsDatabase: boolean
  expectsFunction: boolean
  expectsValue: boolean
  expectsKeyword: boolean
  dotPrefix: string | null
  dotPrefixType: string | null
  insideSubquery: boolean
  parenthesisDepth: number
}

/** 模块级缓存，避免每次补全都发请求 */
let cachedContext: GrammarContext | null = null

export function detectGrammarContext(
  sql: string,
  cursorPosition: number,
): Promise<ApiResult<GrammarContext>> {
  return request.post('/sql/grammar/context', { sql, cursorPosition })
}

export function getSqlKeywords(type: string = 'MYSQL'): Promise<ApiResult<SqlKeywords>> {
  return request.get('/sql/grammar/keywords', { params: { type } })
}

export function getCachedGrammarContext(): GrammarContext | null {
  return cachedContext
}

export function setCachedGrammarContext(ctx: GrammarContext | null) {
  cachedContext = ctx
}
