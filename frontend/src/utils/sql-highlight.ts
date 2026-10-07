import * as monaco from 'monaco-editor'
// @ts-ignore monaco 内置词法定义没有类型声明（见 vite.config.ts 的 monaco-sql-builtin 别名）
import { language as sqlBuiltin } from 'monaco-sql-builtin'

/**
 * Hive 关键字。
 *
 * 取自 Hive 官方 LanguageManual DDL 的「Keywords, Non-reserved Keywords and
 * Reserved Keywords」表中 Hive 1.2.0 ~ 3.0.0 的非保留关键字并集（即本机
 * Hive 3.1.3 的集合），另补上 Hive 数据类型、文件格式与编辑器需要的 Hive
 * 保留字。Monaco 内置的 SQL 词法表来自通用 SQL 定义，不含这些词，导致它们
 * 在编辑器里不被高亮。
 *
 * 来源：https://hive.apache.org/docs/latest/language/languagemanual-ddl/
 */
const HIVE_KEYWORDS = [
  // Hive 1.2.0 非保留关键字
  'ADD', 'ADMIN', 'AFTER', 'ANALYZE', 'ARCHIVE', 'ASC', 'BEFORE', 'BUCKET', 'BUCKETS',
  'CASCADE', 'CHANGE', 'CLUSTER', 'CLUSTERED', 'CLUSTERSTATUS', 'COLLECTION', 'COLUMNS',
  'COMMENT', 'COMPACT', 'COMPACTIONS', 'COMPUTE', 'CONCATENATE', 'CONTINUE', 'DATA',
  'DATABASES', 'DATETIME', 'DAY', 'DBPROPERTIES', 'DEFERRED', 'DEFINED', 'DELIMITED',
  'DEPENDENCY', 'DESC', 'DIRECTORIES', 'DIRECTORY', 'DISABLE', 'DISTRIBUTE', 'ENABLE',
  'ESCAPED', 'EXCLUSIVE', 'EXPLAIN', 'EXPORT', 'FIELDS', 'FILE', 'FILEFORMAT', 'FIRST',
  'FORMAT', 'FORMATTED', 'FUNCTIONS', 'HOLD_DDLTIME', 'HOUR', 'IDXPROPERTIES', 'IGNORE',
  'INDEX', 'INDEXES', 'INPATH', 'INPUTDRIVER', 'INPUTFORMAT', 'ITEMS', 'JAR', 'KEYS',
  'LIMIT', 'LINES', 'LOAD', 'LOCATION', 'LOCK', 'LOCKS', 'LOGICAL', 'LONG', 'MAPJOIN',
  'MATERIALIZED', 'METADATA', 'MINUS', 'MINUTE', 'MONTH', 'MSCK', 'NOSCAN', 'NO_DROP',
  'OFFLINE', 'OPTION', 'OUTPUTDRIVER', 'OUTPUTFORMAT', 'OVERWRITE', 'OWNER',
  'PARTITIONED', 'PARTITIONS', 'PLUS', 'PRETTY', 'PRINCIPALS', 'PROTECTION', 'PURGE',
  'READ', 'READONLY', 'REBUILD', 'RECORDREADER', 'RECORDWRITER', 'REGEXP', 'RELOAD',
  'RENAME', 'REPAIR', 'REPLACE', 'REPLICATION', 'RESTRICT', 'REWRITE', 'RLIKE', 'ROLE',
  'ROLES', 'SCHEMA', 'SCHEMAS', 'SECOND', 'SEMI', 'SERDE', 'SERDEPROPERTIES', 'SERVER',
  'SETS', 'SHARED', 'SHOW', 'SHOW_DATABASE', 'SKEWED', 'SORT', 'SORTED', 'SSL',
  'STATISTICS', 'STORED', 'STREAMTABLE', 'STRING', 'STRUCT', 'TABLES', 'TBLPROPERTIES',
  'TEMPORARY', 'TERMINATED', 'TINYINT', 'TOUCH', 'TRANSACTIONS', 'UNARCHIVE', 'UNDO',
  'UNIONTYPE', 'UNLOCK', 'UNSET', 'UNSIGNED', 'URI', 'USE', 'UTC', 'VIEW', 'WHILE', 'YEAR',

  // Hive 2.0.0 ~ 3.0.0 增补
  'AUTOCOMMIT', 'ISOLATION', 'LEVEL', 'OFFSET', 'SNAPSHOT', 'TRANSACTION', 'WORK', 'WRITE',
  'ABORT', 'KEY', 'LAST', 'NORELY', 'NOVALIDATE', 'NULLS', 'RELY', 'VALIDATE',
  'CACHE', 'DAYS', 'DAYOFWEEK', 'DUMP', 'HOURS', 'MATCHED', 'MERGE', 'MINUTES', 'MONTHS',
  'QUARTER', 'REPL', 'SECONDS', 'STATUS', 'VIEWS', 'WEEK', 'WEEKS', 'YEARS',
  'DETAIL', 'EXPRESSION', 'OPERATOR', 'SUMMARY', 'VECTORIZATION', 'WAIT',
  'ACTIVATE', 'ACTIVE', 'ALLOC_FRACTION', 'CHECK', 'DEFAULT', 'DO', 'ENFORCED', 'KILL',
  'MANAGEMENT', 'MAPPING', 'MOVE', 'PATH', 'PLAN', 'PLANS', 'POOL', 'QUERY',
  'QUERY_PARALLELISM', 'REOPTIMIZATION', 'RESOURCE', 'SCHEDULING_POLICY', 'UNMANAGED',
  'WORKLOAD', 'ZONE',

  // Hive 数据类型与文件格式
  'MAP', 'ARRAY', 'BINARY', 'BOOLEAN', 'BIGINT', 'TIMESTAMP', 'DECIMAL', 'DOUBLE',
  'FLOAT', 'SMALLINT', 'VARCHAR', 'CHAR', 'INT', 'INTERVAL', 'JSONFILE', 'SEQUENCEFILE',
  'TEXTFILE', 'RCFILE', 'ORC', 'PARQUET', 'AVRO',

  // Hive 保留字（官方非保留表中不列，但编辑器需要高亮）
  'LATERAL', 'ANTI', 'DIV', 'DISTRIBUTED',
]

/** Hive 内置函数（Monaco 内置函数表里没有的） */
const HIVE_FUNCTIONS = [
  'EXPLODE', 'POSEXPLODE', 'COLLECT_LIST', 'COLLECT_SET', 'NAMED_STRUCT', 'SORT_ARRAY',
  'ARRAY_CONTAINS', 'MAP_KEYS', 'MAP_VALUES', 'GET_JSON_OBJECT', 'FROM_JSON', 'TO_JSON',
  'NVL', 'IF', 'SPLIT', 'REGEXP_REPLACE', 'REGEXP_EXTRACT', 'PARSE_URL', 'DATEDIFF',
  'TO_DATE', 'PERCENTILE', 'PERCENTILE_APPROX', 'HISTOGRAM_NUMERIC', 'WIDTH_BUCKET',
  'CONCAT_WS', 'CURRENT_DATE', 'CURRENT_TIMESTAMP', 'INSTR', 'LOCATE', 'SIZE', 'STACK',
  'CHR', 'ASCII', 'BASE64', 'UNBASE64', 'HEX', 'UNHEX',
]

let registered = false

/** 注册带 Hive 关键字/函数的 SQL 词法（幂等，可在各编辑器初始化时调用） */
export function ensureSqlHighlight(): void {
  if (registered) return
  registered = true
  monaco.languages.setMonarchTokensProvider('sql', {
    ...sqlBuiltin,
    keywords: Array.from(new Set([...(sqlBuiltin.keywords || []), ...HIVE_KEYWORDS])),
    builtinFunctions: Array.from(
      new Set([...(sqlBuiltin.builtinFunctions || []), ...HIVE_FUNCTIONS]),
    ),
  })
}
