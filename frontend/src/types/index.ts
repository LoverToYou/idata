export interface DatasourceConfig {
  id: number
  name: string
  type: 'MYSQL' | 'HIVE'
  host: string
  port: number
  databaseName: string
  username: string
  createdAt: string
  updatedAt: string
}

export interface DatasourceRequest {
  id?: number
  name: string
  type: string
  host: string
  port: number
  databaseName: string
  username: string
  password: string
  props?: string
}

export interface ConnectionTestRequest {
  type: string
  host: string
  port: number
  databaseName?: string
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
