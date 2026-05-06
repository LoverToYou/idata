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
  nodeType: string
  status: 'RUNNING' | 'SUCCESS' | 'FAILED'
  startTime: string
  endTime: string
  logContent: string
}

export interface ApiResult<T> {
  code: number
  message: string
  data: T
}
