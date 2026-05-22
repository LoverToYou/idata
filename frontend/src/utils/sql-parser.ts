import { Parser } from 'node-sql-parser'
import type { SqlAnalysis, SqlSuggestion } from '@/api/sql'

const parser = new Parser()

interface TableRef {
  db: string | null
  table: string
  as: string | null
  join?: string
  on?: any
}

/**
 * 解析 SQL 并返回分析结果
 */
export function parseSql(sql: string): SqlAnalysis {
  const cleanSql = sql.replace(/;+\s*$/, '').trim()
  if (!cleanSql) {
    return { valid: false, errorMessage: 'SQL 内容为空', tables: [], columns: [], queryType: '', joinTypes: [], suggestions: [] }
  }

  try {
    const ast = parser.astify(cleanSql, { database: 'mysql' })
    const stmts = Array.isArray(ast) ? ast : [ast]
    // Take only the first statement
    const stmt = stmts[0] as any

    const analysis: SqlAnalysis = {
      valid: true,
      errorMessage: '',
      tables: [],
      columns: [],
      queryType: detectQueryType(stmt),
      joinTypes: [],
      suggestions: [],
    }

    switch (stmt.type) {
      case 'select':
        extractSelectInfo(stmt, analysis)
        break
      case 'insert':
        extractInsertInfo(stmt, analysis)
        break
      case 'update':
        extractUpdateInfo(stmt, analysis)
        break
      case 'delete':
        extractDeleteInfo(stmt, analysis)
        break
      case 'replace':
        analysis.queryType = 'REPLACE'
        break
      case 'create':
        analysis.queryType = 'CREATE'
        break
      case 'alter':
        analysis.queryType = 'ALTER'
        break
      case 'drop':
        analysis.queryType = 'DROP'
        break
      case 'truncate':
        analysis.queryType = 'TRUNCATE'
        break
      case 'call':
        analysis.queryType = 'CALL'
        break
      case 'use':
        analysis.queryType = 'USE'
        break
      case 'set':
        analysis.queryType = 'SET'
        break
      case 'show':
        analysis.queryType = 'SHOW'
        break
      default:
        analysis.queryType = 'OTHER'
    }

    analysis.suggestions = generateSuggestions(analysis, stmt)
    return analysis
  } catch (e: any) {
    // Parser failed — fall back to regex-based analysis
    return analyzeWithRegex(cleanSql, e.message || '解析失败')
  }
}

function detectQueryType(stmt: any): string {
  const typeMap: Record<string, string> = {
    select: 'SELECT',
    insert: 'INSERT',
    replace: 'REPLACE',
    update: 'UPDATE',
    delete: 'DELETE',
    create: 'CREATE',
    alter: 'ALTER',
    drop: 'DROP',
    truncate: 'TRUNCATE',
    call: 'CALL',
    use: 'USE',
    set: 'SET',
    show: 'SHOW',
  }
  return typeMap[stmt.type] || 'OTHER'
}

function extractSelectInfo(stmt: any, analysis: SqlAnalysis) {
  // Extract columns
  if (stmt.columns && Array.isArray(stmt.columns)) {
    analysis.columns = stmt.columns.map((c: any) => {
      if (c.expr?.type === 'star' || c.expr?.type === 'all_column_ref') return '*'
      if (c.expr?.type === 'column_ref' && c.expr?.column) return c.expr.column
      if (c.expr) return c.expr.value ?? c.expr.toString?.() ?? String(c.expr)
      return String(c)
    })
  }

  // Extract tables and JOINs from FROM clause
  if (stmt.from && Array.isArray(stmt.from)) {
    const tables: string[] = []
    for (const item of stmt.from) {
      collectTablesFromFrom(item, tables)
    }
    analysis.tables = tables

    // Detect JOINs
    analysis.joinTypes = detectJoins(stmt.from)
  }
}

function collectTablesFromFrom(item: any, tables: string[]) {
  if (!item) return

  // subquery in FROM
  if (item.expr && item.expr.type === 'select') {
    extractSelectInfo(item.expr, { tables, columns: [], joinTypes: [] } as any)
    return
  }

  if (item.table) {
    const name = item.db ? `${item.db}.${item.table}` : item.table
    if (!tables.includes(name)) tables.push(name)
  }

  // Parenthesized join groups
  if (item.type === 'expr_list' && Array.isArray(item.value)) {
    for (const v of item.value) {
      if (v.table) {
        const name = v.db ? `${v.db}.${v.table}` : v.table
        if (!tables.includes(name)) tables.push(name)
      }
    }
  }
}

function detectJoins(fromList: any[]): string[] {
  const joins: string[] = []
  for (const item of fromList) {
    if (item.join) {
      const joinType = item.join.replace(/\s+JOIN$/, '').trim() || 'INNER'
      if (!joins.includes(joinType)) joins.push(joinType)
    }
  }
  return joins
}

function extractInsertInfo(stmt: any, analysis: SqlAnalysis) {
  if (stmt.table && Array.isArray(stmt.table)) {
    analysis.tables = stmt.table.map((t: any) => t.db ? `${t.db}.${t.table}` : t.table)
  }
  if (stmt.columns && Array.isArray(stmt.columns)) {
    analysis.columns = stmt.columns
  }
}

function extractUpdateInfo(stmt: any, analysis: SqlAnalysis) {
  if (stmt.table && Array.isArray(stmt.table)) {
    analysis.tables = stmt.table.map((t: any) => t.db ? `${t.db}.${t.table}` : t.table)
  }
}

function extractDeleteInfo(stmt: any, analysis: SqlAnalysis) {
  if (stmt.from && Array.isArray(stmt.from)) {
    analysis.tables = stmt.from.map((t: any) => t.db ? `${t.db}.${t.table}` : t.table)
  }
}

function generateSuggestions(analysis: SqlAnalysis, stmt: any): SqlSuggestion[] {
  const suggestions: SqlSuggestion[] = []

  // 1. SELECT * warning
  if (analysis.queryType === 'SELECT' && analysis.columns.length === 1 && analysis.columns[0] === '*') {
    suggestions.push({
      type: 'WARNING',
      title: '避免使用 SELECT *',
      detail: '当前查询使用了 SELECT *，会读取所有列。',
      suggestion: '请明确列出需要的列名，减少 IO 和网络传输。',
    })
  }

  // 2. Missing WHERE on SELECT with tables and no subquery
  if (analysis.queryType === 'SELECT' && analysis.tables.length > 0 && !stmt.where) {
    suggestions.push({
      type: 'WARNING',
      title: '缺少 WHERE 条件',
      detail: '查询没有 WHERE 条件，将返回全表数据。',
      suggestion: '请添加 WHERE 条件过滤数据，或确认是否需要全表扫描。',
    })
  }

  // 3. Missing WHERE on UPDATE/DELETE
  if ((analysis.queryType === 'UPDATE' || analysis.queryType === 'DELETE') && !stmt.where) {
    suggestions.push({
      type: 'WARNING',
      title: `${analysis.queryType} 缺少 WHERE 条件`,
      detail: `${analysis.queryType} 语句没有 WHERE 条件，将操作全表数据。`,
      suggestion: '请添加 WHERE 条件限制操作范围，或确认是否需要全表操作。',
    })
  }

  // 4. Cartesian JOIN (no ON condition)
  if (stmt.from && Array.isArray(stmt.from)) {
    for (const item of stmt.from) {
      if (item.join && !item.on) {
        suggestions.push({
          type: 'ERROR',
          title: '笛卡尔积 JOIN',
          detail: '检测到没有连接条件的 JOIN，会产生笛卡尔积。',
          suggestion: '请添加 ON 条件指定表之间的连接关系。',
        })
        break
      }
    }
  }

  // 5. ORDER BY without LIMIT
  if (analysis.queryType === 'SELECT' && stmt.orderby && Array.isArray(stmt.orderby) && stmt.orderby.length > 0 && !stmt.limit) {
    suggestions.push({
      type: 'INFO',
      title: 'ORDER BY 未搭配 LIMIT',
      detail: 'ORDER BY 会对全结果集排序，未使用 LIMIT 可能导致大量排序开销。',
      suggestion: '考虑添加 LIMIT 限制返回行数。',
    })
  }

  // 6. HAVING without WHERE
  if (analysis.queryType === 'SELECT' && stmt.having && !stmt.where) {
    suggestions.push({
      type: 'INFO',
      title: '使用 HAVING 但未使用 WHERE',
      detail: 'HAVING 在聚合后过滤，效率低于 WHERE 在聚合前过滤。',
      suggestion: '将能在 WHERE 中过滤的条件移到 WHERE 子句。',
    })
  }

  // 7. INSERT without column list
  if (analysis.queryType === 'INSERT' && (!stmt.columns || stmt.columns.length === 0)) {
    suggestions.push({
      type: 'INFO',
      title: 'INSERT 未指定列列表',
      detail: '未显式指定插入列，依赖表结构顺序，表结构变更可能导致错误。',
      suggestion: '建议明确列出列名: INSERT INTO table (col1, col2, ...) VALUES ...',
    })
  }

  return suggestions
}

/**
 * Regex fallback when the parser can't handle MySQL-specific syntax.
 */
function analyzeWithRegex(sql: string, errorMsg: string): SqlAnalysis {
  const analysis: SqlAnalysis = {
    valid: true,
    errorMessage: '',
    tables: [],
    columns: [],
    queryType: 'OTHER',
    joinTypes: [],
    suggestions: [],
  }

  const upper = sql.toUpperCase().trim()

  // 1. Detect query type
  if (upper.startsWith('SELECT')) analysis.queryType = 'SELECT'
  else if (upper.startsWith('INSERT')) analysis.queryType = 'INSERT'
  else if (upper.startsWith('UPDATE')) analysis.queryType = 'UPDATE'
  else if (upper.startsWith('DELETE')) analysis.queryType = 'DELETE'
  else if (upper.startsWith('SHOW')) analysis.queryType = 'SHOW'
  else if (upper.startsWith('DESC') || upper.startsWith('DESCRIBE')) analysis.queryType = 'DESCRIBE'
  else if (upper.startsWith('EXPLAIN')) analysis.queryType = 'EXPLAIN'
  else if (upper.startsWith('CREATE')) analysis.queryType = 'CREATE'
  else if (upper.startsWith('ALTER')) analysis.queryType = 'ALTER'
  else if (upper.startsWith('DROP')) analysis.queryType = 'DROP'
  else if (upper.startsWith('TRUNCATE')) analysis.queryType = 'TRUNCATE'
  else if (upper.startsWith('WITH')) analysis.queryType = 'WITH'
  else if (upper.startsWith('CALL')) analysis.queryType = 'CALL'
  else if (upper.startsWith('USE')) analysis.queryType = 'USE'
  else if (upper.startsWith('SET')) analysis.queryType = 'SET'

  // 2. Extract table names using regex
  const tables = new Set<string>()
  const tablePattern = /(?:FROM|JOIN|INTO|TABLE|UPDATE)\s+([`"']?\w+(?:[.`"']\w+)?)/gi
  let match
  while ((match = tablePattern.exec(sql)) !== null) {
    const table = match[1].replace(/[`"']/g, '')
    if (table) tables.add(table)
  }
  const deletePattern = /DELETE\s+(?:FROM\s+)?([`"']?\w+(?:[.`"']\w+)?)/gi
  while ((match = deletePattern.exec(sql)) !== null) {
    tables.add(match[1].replace(/[`"']/g, ''))
  }
  analysis.tables = Array.from(tables)

  // 3. Extract selected columns (for SELECT)
  if (analysis.queryType === 'SELECT') {
    const colMatch = sql.match(/SELECT\s+(.+?)\s+FROM/is)
    if (colMatch) {
      const cols = colMatch[1].trim()
      if (cols === '*') {
        analysis.columns = ['*']
      } else {
        analysis.columns = cols.split(/,(?=(?:[^'"`]|['"`][^'"`]*['"`])*$)/).map(c => c.trim().replace(/[`"']/g, ''))
      }
    }
  }

  // 4. Generate basic suggestions
  if (analysis.queryType === 'SELECT' && analysis.columns.length === 1 && analysis.columns[0] === '*') {
    analysis.suggestions.push({
      type: 'WARNING',
      title: '避免使用 SELECT *',
      detail: '当前查询使用了 SELECT *，会读取所有列。',
      suggestion: '请明确列出需要的列名，减少 IO 和网络传输。',
    })
  }

  if ((analysis.queryType === 'SELECT' || analysis.queryType === 'UPDATE' || analysis.queryType === 'DELETE')
      && analysis.tables.length > 0 && !upper.includes('WHERE')) {
    analysis.suggestions.push({
      type: 'WARNING',
      title: '缺少 WHERE 条件',
      detail: `${analysis.queryType} 语句没有 WHERE 条件，可能操作全表数据。`,
      suggestion: '请添加 WHERE 条件过滤数据，或确认是否需要全表操作。',
    })
  }

  if (analysis.queryType === 'INSERT') {
    const hasCols = /INSERT\s+INTO\s+[`"']?\w+(?:[.`"']\w+)?\s*\(\s*\w+/i.test(sql)
    if (!hasCols) {
      analysis.suggestions.push({
        type: 'INFO',
        title: 'INSERT 未指定列列表',
        detail: '未显式指定插入列，依赖表结构顺序，表结构变更可能导致错误。',
        suggestion: '建议明确列出列名: INSERT INTO table (col1, col2, ...) VALUES ...',
      })
    }
  }

  // Record parser error as message (not a failure)
  analysis.errorMessage = errorMsg.replace(/\n/g, ' ').substring(0, 200)

  return analysis
}
