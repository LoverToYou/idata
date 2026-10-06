export interface DatasourceConfig {
  id: number
  name: string
  type: 'MYSQL' | 'HIVE'
  host: string
  port: number
  databaseName: string
  jdbcUrl?: string
  username: string
  createdAt: string
  updatedAt: string
}

export interface DatasourceRequest {
  id?: number
  name: string
  type?: string
  host?: string
  port?: number
  databaseName?: string
  jdbcUrl?: string
  username: string
  password: string
  props?: string
}

export interface ConnectionTestRequest {
  type?: string
  host?: string
  port?: number
  databaseName?: string
  jdbcUrl?: string
  username?: string
  password?: string
}

export interface WorkflowDefinition {
  id: number
  name: string
  description: string
  dagJson: string
  status: 'DRAFT' | 'PUBLISHED'
  etlType?: string
  createdAt: string
  updatedAt: string
}

export interface WorkflowInstance {
  id: number
  workflowId: number
  status: 'RUNNING' | 'SUCCESS' | 'FAILED'
  startedAt: string
  finishedAt: string
  triggeredBy: string
  errorMessage: string
  createdAt: string
}

export interface ScheduleConfig {
  id: number
  workflowId: number
  cronExpression: string
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export interface InstanceNodeLog {
  nodeId: string
  nodeName: string
  status: 'WAITING' | 'RUNNING' | 'SUCCESS' | 'FAILED'
  startedAt: string
  finishedAt: string | null
  errorMessage: string | null
  dataxPid: number | null
  logPath: string | null
  dataxJson: string | null
  outputLog: string | null
}

export interface ColumnDefinition {
  columnName: string
  dataType: string
  length: number | null
  nullable: boolean
  defaultValue: string | null
  comment: string
  primaryKey: boolean
  autoIncrement: boolean
}

export interface PartitionItem {
  partitionName: string
  value: string
}

export interface PartitionColumn {
  name: string
  dataType: string
}

export interface PartitionConfig {
  type: 'RANGE' | 'LIST' | 'HASH' | 'KEY' | 'HIVE'
  column?: string
  columns?: PartitionColumn[]
  count?: number
  partitions?: PartitionItem[]
}

export interface IndexDefinition {
  indexName: string
  indexType: 'INDEX' | 'UNIQUE' | 'FULLTEXT'
  columns: string[]
}

export interface PythonScript {
  id: number
  name: string
  description: string
  content: string
  timeoutSeconds: number
  status?: 'DRAFT' | 'PUBLISHED'
  createdBy?: string
  createdAt: string
  updatedAt: string
}

export interface PythonRun {
  id: number
  scriptId: number
  scriptName: string
  params: string
  status: 'RUNNING' | 'SUCCESS' | 'FAILED' | 'CANCELLED'
  stdout: string
  stderr: string
  exitCode: number
  triggeredBy: 'MANUAL' | 'WORKFLOW'
  startedAt: string
  finishedAt: string
}

export interface UdfDefinition {
  id: number
  name: string
  className: string
  jarFileName: string
  jarPath: string
  jarSize: number
  databaseName: string
  datasourceId: number
  datasourceName: string
  functionType: 'UDF' | 'UDAF' | 'UDTF'
  description: string
  registerStatus: 'UNREGISTERED' | 'REGISTERED' | 'FAILED'
  registerMessage: string
  registerSql: string
  createdAt: string
  updatedAt: string
}

export interface UdfDefinitionRequest {
  id?: number
  name: string
  className: string
  /** 关联文件管理记录（jar 来源）；与 jarPath 二选一 */
  fileId?: number
  /** 手动指定 JAR 的 HDFS 路径（如 hdfs://127.0.0.1:9000/data/x.jar）；与 fileId 二选一，优先使用 */
  jarPath?: string
  databaseName?: string
  datasourceId: number
  functionType?: string
  description?: string
}

export interface FileManage {
  id: number
  fileName: string
  filePath: string
  fileSize: number
  fileExt: string
  description: string
  createdAt: string
  updatedAt: string
}

export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

export interface PageResult<T> {
  data: T[]
  total: number
  page: number
  pageSize: number
}
