/**
 * Generates DataX job JSON configuration from workflow node configs.
 * Mirrors the backend DataXConfigBuilder.java logic.
 */

export interface DataxNodeConfig {
  datasourceId: number | null
  tableName: string
  columns: string[]
  where: string
  writeMode: string
  dataxReader: string
  dataxWriter: string
  username?: string
  password?: string
  jdbcUrl?: string
  splitPk?: string
  preSql?: string
  postSql?: string
  partition?: string
}

interface Edge {
  id: string
  source: string
  target: string
}

interface DagNode {
  id: string
  label: string
  type?: string
  data?: {
    nodeType: string
    config: DataxNodeConfig
  }
}

function buildReaderParam(type: string, config: DataxNodeConfig): Record<string, any> {
  const param: Record<string, any> = {}
  const username = config.username || 'root'
  const password = config.password || ''
  const jdbcUrl = config.jdbcUrl || 'jdbc:mysql://localhost:3306/test'
  const table = config.tableName
  const columnList = config.columns?.length ? config.columns : ['*']

  switch (type) {
    case 'mysqlreader': {
      const connection = [{
        jdbcUrl: [jdbcUrl],
        table: [table],
      }]
      param.username = username
      param.password = password
      param.connection = connection
      param.column = columnList

      if (config.splitPk) param.splitPk = config.splitPk
      if (config.where) param.where = config.where
      break
    }
    case 'hivereader': {
      const connection = [{ jdbcUrl, table: [table] }]
      param.username = username
      param.password = password
      param.connection = connection
      param.column = columnList
      if (config.partition) param.partition = config.partition
      break
    }
    case 'mysqlwriter': {
      const connection = [{ jdbcUrl, table: [table] }]
      param.username = username
      param.password = password
      param.connection = connection
      param.column = columnList
      if (config.preSql) param.preSql = [config.preSql]
      if (config.postSql) param.postSql = [config.postSql]
      break
    }
    case 'hivewriter': {
      const connection = [{ jdbcUrl, table: [table] }]
      param.username = username
      param.password = password
      param.connection = connection
      param.column = columnList
      if (config.partition) param.partition = config.partition
      param.writeMode = config.writeMode || 'insert'
      break
    }
  }
  return param
}

function buildReaderOrWriter(node: DagNode, role: 'source' | 'sink'): Record<string, any> {
  if (!node?.data?.config) {
    throw new Error(`Node ${node?.id || 'unknown'} has no config`)
  }

  const config = node.data.config
  const type = role === 'source' ? config.dataxReader : config.dataxWriter
  if (!type) {
    throw new Error(`Node ${node.label} missing ${role} type`)
  }

  return {
    name: type,
    parameter: buildReaderParam(type, config),
  }
}

/**
 * Generate complete DataX JSON string from workflow nodes and edges.
 */
export function generateDataXJson(nodes: DagNode[], edges: Edge[]): string {
  const nodeMap = new Map(nodes.map((n) => [n.id, n]))
  const contentList: Record<string, any>[] = []

  for (const edge of edges) {
    const source = nodeMap.get(edge.source)
    const target = nodeMap.get(edge.target)

    if (!source || !target) continue
    if (source.data?.nodeType !== 'source' || target.data?.nodeType !== 'sink') continue

    contentList.push({
      reader: buildReaderOrWriter(source, 'source'),
      writer: buildReaderOrWriter(target, 'sink'),
    })
  }

  const job: Record<string, any> = {
    setting: {
      speed: { channel: 1 },
    },
    content: contentList,
  }

  const root = { job }
  return JSON.stringify(root, null, 2)
}

/**
 * Generate DataX JSON from GUI config (new editor mode).
 * Produces a single-source-to-single-target DataX job configuration.
 */
export interface GuiDataXConfig {
  sourceDatasourceType: string
  sourceDatasource: { username: string; password: string; jdbcUrl: string } | null
  targetDatasourceType: string
  targetDatasource: { username: string; password: string; jdbcUrl: string } | null
  tableName: string
  columns: string[]
  filterCondition?: string
  writeMode?: string
  // advanced
  channel?: number
  splitPk?: string
  batchSize?: number
  encoding?: string
  preSql?: string
  postSql?: string
}

export function generateDataXJsonFromGuiConfig(config: GuiDataXConfig): string {
  const readerType = config.sourceDatasourceType === 'MYSQL' ? 'mysqlreader' : 'hivereader'
  const writerType = config.targetDatasourceType === 'MYSQL' ? 'mysqlwriter' : 'hivewriter'

  const readerParam: Record<string, any> = {
    username: config.sourceDatasource?.username || '',
    password: config.sourceDatasource?.password || '',
    connection: [{
      jdbcUrl: [config.sourceDatasource?.jdbcUrl || ''],
      table: [config.tableName],
    }],
    column: config.columns.length ? config.columns : ['*'],
    ...(config.filterCondition ? { where: config.filterCondition } : {}),
    ...(config.splitPk ? { splitPk: config.splitPk } : {}),
    ...(config.encoding ? { encoding: config.encoding } : {}),
  }

  const writerParam: Record<string, any> = {
    username: config.targetDatasource?.username || '',
    password: config.targetDatasource?.password || '',
    connection: [{
      jdbcUrl: [config.targetDatasource?.jdbcUrl || ''],
      table: [config.tableName],
    }],
    column: config.columns.length ? config.columns : ['*'],
    writeMode: config.writeMode || 'insert',
    ...(config.batchSize ? { batchSize: config.batchSize } : {}),
    ...(config.encoding ? { encoding: config.encoding } : {}),
    ...(config.preSql ? { preSql: [config.preSql] } : {}),
    ...(config.postSql ? { postSql: [config.postSql] } : {}),
  }

  const job: Record<string, any> = {
    setting: {
      speed: { channel: config.channel || 1 },
    },
    content: [{ reader: { name: readerType, parameter: readerParam }, writer: { name: writerType, parameter: writerParam } }],
  }

  return JSON.stringify({ job }, null, 2)
}
