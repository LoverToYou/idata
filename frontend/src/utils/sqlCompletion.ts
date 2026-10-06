import * as monaco from 'monaco-editor'
import type { SqlKeywords, GrammarContext } from '@/api/grammar'
import type { UdfDefinition } from '@/types'

export interface SqlTableRef {
  tableName: string
  schema: string
}

/** SQL 补全所需的数据来源，由调用方维护，补全触发时实时读取 */
export interface SqlCompletionSources {
  keywords?: SqlKeywords | null
  databases?: string[]
  tables?: SqlTableRef[]
  tablesByDb?: Record<string, SqlTableRef[]>
  udfs?: UdfDefinition[]
  /** 数据源类型；HIVE 时额外提示已注册的 UDF */
  datasourceType?: string | null
  /** 语法上下文（按光标位置过滤候选），可为空 */
  grammarContext?: GrammarContext | null
}

/**
 * 为 Monaco 注册 SQL 补全：数据库 / 表名 / 语句 / 子句 / 内置函数 / 数据类型 / Hive UDF。
 * 返回的 disposable 请在组件卸载时 dispose()。
 */
export function registerSqlCompletion(
  getSources: () => SqlCompletionSources,
  language = 'sql',
): monaco.IDisposable {
  return monaco.languages.registerCompletionItemProvider(language, {
    triggerCharacters: ['.', ' '],
    provideCompletionItems: (model, position) => {
      const sources = getSources() || {}
      const ctx = sources.grammarContext || null
      const databases = sources.databases || []
      const tables = sources.tables || []
      const tablesByDb = sources.tablesByDb || {}
      const keywords = sources.keywords || null
      const udfs = sources.udfs || []

      const result: any[] = []
      const ks = monaco.languages.CompletionItemKind

      const lineContent = model.getLineContent(position.lineNumber)
      const textBefore = lineContent.substring(0, position.column - 1)

      // 语法上下文有防抖延迟，刚输入完可能还是上一次的结果；
      // 光标前若停在 FROM / JOIN 等之后，则认为正在等表名，避免表提示不出现
      let expectsTable = ctx ? ctx.expectsTable : true
      if (ctx && !ctx.expectsTable && /\b(from|join|into|update|table|truncate|describe|use)\s+[\w.$]*$/i.test(textBefore)) {
        expectsTable = true
      }

      // --- Dot prefix detection from model text ---
      // 语法上下文有防抖延迟，刚敲下 '.' 时可能还没更新，这里直接从前文推断
      let effectivePrefix = ctx?.dotPrefix
      let effectivePrefixType = ctx?.dotPrefixType
      if (!effectivePrefix) {
        const dotMatch = textBefore.match(/(\w+)\.\s*$/)
        if (dotMatch) {
          effectivePrefix = dotMatch[1].toUpperCase()
          effectivePrefixType = ctx?.expectsTable ? 'DATABASE' : undefined
          if (!effectivePrefixType && databases.some((d) => d.toUpperCase() === effectivePrefix)) {
            effectivePrefixType = 'DATABASE'
          }
        }
      }

      // --- Database name suggestions ---
      if (!effectivePrefix && databases.length > 0) {
        if (!ctx || ctx.expectsDatabase) {
          result.push(...databases.map((db) => ({
            label: db,
            kind: ks.Module,
            insertText: db + '.',
            detail: '数据库',
            sortText: 'b' + db,
          })))
        }
      }

      // --- Table name suggestions ---
      if ((!ctx || expectsTable) && tables.length > 0) {
        if (effectivePrefix && effectivePrefixType === 'DATABASE') {
          const prefix = effectivePrefix
          const perDb = tablesByDb[prefix]
          if (perDb && perDb.length > 0) {
            result.push(...perDb.map((t) => ({
              label: t.tableName,
              kind: ks.Class,
              insertText: t.tableName,
              detail: '表名 (' + t.schema + ')',
              sortText: 'a' + t.tableName,
            })))
          } else {
            const filtered = tables.filter((t) => t.schema.toUpperCase() === prefix)
            result.push(...filtered.map((t) => ({
              label: t.tableName,
              kind: ks.Class,
              insertText: t.tableName,
              detail: '表名 (' + t.schema + ')',
              sortText: 'a' + t.tableName,
            })))
          }
        } else {
          result.push(...tables.map((t) => ({
            label: t.tableName,
            kind: ks.Class,
            insertText: t.tableName,
            detail: '表名',
            sortText: 'a' + t.tableName,
          })))
        }
      }

      // --- Keyword suggestions (filtered by context) ---
      if (keywords) {
        const kw = keywords
        const allSuggestions = [
          ...kw.statements.map((k) => ({ label: k, kind: ks.Keyword, insertText: k, detail: 'SQL 语句', sortText: 'z' + k })),
          ...kw.functions.map((k) => ({ label: k, kind: ks.Function, insertText: k, detail: '内置函数', sortText: 'z' + k })),
          ...kw.types.map((k) => ({ label: k, kind: ks.TypeParameter, insertText: k, detail: '数据类型', sortText: 'z' + k })),
          ...kw.clauses.map((k) => ({ label: k, kind: ks.Keyword, insertText: k, detail: 'SQL 子句', sortText: 'z' + k })),
        ]
        if (!ctx) {
          result.push(...allSuggestions)
        } else {
          const validList = ctx.validKeywords.map((k) => k.toUpperCase())
          // 构建优先级索引：validKeywords 中越靠前优先级越高
          const priorityIndex: Record<string, string> = {}
          validList.forEach((k, i) => {
            priorityIndex[k] = String(i).padStart(3, '0')
          })
          const expectFunction = ctx.expectsFunction
          result.push(...allSuggestions
            .filter((s) => {
              const label = (s.label as string).toUpperCase()
              if (s.detail === '内置函数' || label.endsWith('()')) return expectFunction
              if (s.detail === '数据类型') return true
              if (s.detail === 'SQL 语句' || s.detail === 'SQL 子句') {
                if (validList.length === 0) return true
                return validList.some((v) => label.includes(v) || v.includes(label))
              }
              return true
            })
            .map((s) => {
              const label = (s.label as string).toUpperCase()
              const prio = priorityIndex[label]
              if (prio !== undefined) {
                return { ...s, sortText: 'a' + prio + label }
              }
              return s
            })
          )
        }
      }

      // --- Hive UDF suggestions (from UDF module) ---
      if (sources.datasourceType === 'HIVE' && udfs.length > 0) {
        const registeredUdfs = udfs.filter((u) => u.registerStatus === 'REGISTERED')
        if (registeredUdfs.length > 0 && (!ctx || ctx.expectsFunction)) {
          result.push(...registeredUdfs.map((u) => ({
            label: `${u.databaseName}.${u.name}`,
            kind: ks.Function,
            insertText: `${u.databaseName}.${u.name}(`,
            detail: 'Hive UDF',
            sortText: 'y' + u.name,
          })))
        }
      }

      return { suggestions: result }
    },
  })
}

/** 防抖请求语法上下文并写入模块级缓存（供 registerSqlCompletion 读取） */
export function createGrammarContextUpdater(
  detect: (sql: string, offset: number) => Promise<any>,
  setCache: (ctx: any) => void,
  delay = 300,
) {
  let timer: ReturnType<typeof setTimeout> | null = null
  return {
    update(sql: string, offset: number) {
      if (timer) clearTimeout(timer)
      timer = setTimeout(async () => {
        try {
          const res = await detect(sql, offset)
          setCache(res.data)
        } catch { /* ignore */ }
      }, delay)
    },
    dispose() {
      if (timer) clearTimeout(timer)
      timer = null
    },
  }
}
