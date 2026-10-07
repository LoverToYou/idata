import * as monaco from 'monaco-editor'
// @ts-ignore monaco 内置词法定义没有类型声明（见 vite.config.ts 的 monaco-sql-builtin 别名）
import { language as sqlBuiltin } from 'monaco-sql-builtin'

/**
 * Monaco 内置的 SQL 词法表只覆盖标准 SQL，Hive 专有关键字
 * （LATERAL VIEW / DISTRIBUTE BY / STORED AS / TBLPROPERTIES / MSCK 等）
 * 不会被识别为关键字，在编辑器里显示成普通文本。这里补齐后重新注册。
 */
const EXTRA_KEYWORDS = [
  // Hive 查询子句
  'LATERAL', 'VIEW', 'DISTRIBUTE', 'CLUSTER', 'SORT', 'TABLESAMPLE',
  'BUCKET', 'OVERWRITE', 'SEMI', 'ANTI', 'RLIKE', 'REGEXP', 'DIV',
  // Hive 建表 / 存储
  'PARTITIONED', 'CLUSTERED', 'SORTED', 'BUCKETS', 'SKEWED', 'STORED',
  'LOCATION', 'TBLPROPERTIES', 'SERDEPROPERTIES', 'SERDE', 'INPUTFORMAT',
  'OUTPUTFORMAT', 'DELIMITED', 'TERMINATED', 'ESCAPED', 'COLLECTION',
  'ITEMS', 'KEYS', 'DEFINED', 'RCFILE', 'SEQUENCEFILE', 'TEXTFILE',
  'PARQUET', 'AVRO', 'ORC', 'DIRECTORIES', 'FIELDS', 'LINES', 'PARTITION',
  'EXTERNAL', 'LOCAL', 'CASCADE', 'CONCATENATE', 'COMPACT',
  'MAP', 'ARRAY', 'STRUCT', 'UNIONTYPE',
  // Hive 运维 / 元数据
  'MSCK', 'REPAIR', 'INPATH', 'EXPORT', 'IMPORT', 'ANALYZE', 'COMPUTE',
  'STATISTICS', 'RESET', 'ARCHIVE', 'UNARCHIVE', 'CACHE', 'UNCACHE',
  'REFRESH', 'FORMATTED', 'EXTENDED', 'PARTITIONS', 'FUNCTIONS',
  'DATABASES', 'COLUMNS', 'INDEXES', 'TRANSACTION', 'DISTRIBUTED',
  // Hive 常用函数（内置表里没有的）
  'EXPLODE', 'POSEXPLODE', 'COLLECT_LIST', 'COLLECT_SET', 'NAMED_STRUCT',
  'GET_JSON_OBJECT', 'FROM_JSON', 'TO_JSON', 'SORT_ARRAY', 'ARRAY_CONTAINS',
  'MAP_KEYS', 'MAP_VALUES', 'NVL', 'IF', 'SPLIT', 'REGEXP_REPLACE',
  'REGEXP_EXTRACT', 'PARSE_URL', 'DATEDIFF', 'TO_DATE', 'PERCENTILE',
  'PERCENTILE_APPROX', 'HISTOGRAM_NUMERIC', 'WIDTH_BUCKET',
]

let registered = false

/** 注册带 Hive 关键字的 SQL 词法（幂等，可在各编辑器初始化时调用） */
export function ensureSqlHighlight(): void {
  if (registered) return
  registered = true
  const keywords: string[] = Array.from(
    new Set([...(sqlBuiltin.keywords || []), ...EXTRA_KEYWORDS]),
  )
  monaco.languages.setMonarchTokensProvider('sql', { ...sqlBuiltin, keywords })
}
