<template>
  <Layout>
    <div class="editor-container">
      <!-- Toolbar -->
      <el-card shadow="hover" class="toolbar-card">
        <div class="toolbar">
          <div class="toolbar-left">
            <el-button @click="handleBack">
              <el-icon><ArrowLeft /></el-icon> 返回
            </el-button>
            <el-input
              v-model="form.name"
              placeholder="任务名称"
              style="width: 240px"
              size="default"
            />
            <el-tag v-if="isEditMode && currentStatus" :type="currentStatus === 'PUBLISHED' ? 'success' : 'info'" size="small" effect="light">
              {{ currentStatus === 'PUBLISHED' ? '已发布' : '草稿' }}
            </el-tag>
          </div>
          <div class="toolbar-right">
            <el-button @click="handleSave" :loading="saving" type="primary">
              <el-icon><Check /></el-icon> 保存
            </el-button>
            <el-button
              v-if="(!isEditMode || currentStatus === 'DRAFT') && taskId"
              @click="handlePublish"
              type="success"
            >
              <el-icon><Upload /></el-icon> 发布
            </el-button>
            <el-button v-if="form.configMode !== 'SCRIPT'" @click="showJsonPreview = !showJsonPreview">
              {{ showJsonPreview ? '隐藏 JSON' : 'DataX JSON' }}
            </el-button>
            <el-button v-if="form.configMode === 'SCRIPT'" @click="handleSwitchTemplate">
              更换模板
            </el-button>
            <el-button v-if="form.configMode === 'SCRIPT'" @click="handleFormatScript">
              格式化
            </el-button>
          </div>
        </div>
      </el-card>

      <!-- UI Mode Config -->
      <template v-if="form.configMode === 'UI' || !form.configMode">
        <div class="config-panels">
          <!-- Reader Side -->
          <el-card shadow="hover" class="config-card">
            <template #header><span>读取端 (Reader)</span></template>
            <el-form label-width="100px" size="small">
              <el-form-item label="数据源">
                <el-select v-model="form.readerDatasourceId" placeholder="选择源数据源" style="width: 100%" @change="onReaderDsChange">
                  <el-option v-for="ds in datasources" :key="ds.id" :label="`${ds.name} (${ds.type})`" :value="ds.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="数据库">
                <el-select v-model="form.readerDatabase" placeholder="选择数据库" style="width: 100%" @change="onReaderDbChange" :disabled="!form.readerDatasourceId">
                  <el-option v-for="db in readerDatabases" :key="db" :label="db" :value="db" />
                </el-select>
              </el-form-item>
              <el-form-item label="表名">
                <el-select v-model="form.readerTable" placeholder="选择表" style="width: 100%" @change="onReaderTableChange" :disabled="!form.readerDatabase" filterable>
                  <el-option v-for="t in readerTables" :key="t.tableName" :label="t.tableName" :value="t.tableName" />
                </el-select>
              </el-form-item>
              <el-form-item label="列" v-if="readerColumnsMap.length">
                <div class="column-list">
                  <el-tag v-for="c in readerColumnsMap" :key="c.name" size="small" effect="plain" class="col-tag">
                    {{ c.name }}<span class="col-type"> ({{ c.type }})</span>
                  </el-tag>
                </div>
              </el-form-item>
              <el-form-item label="过滤条件">
                <el-input v-model="form.readerWhere" type="textarea" :rows="2" placeholder="例如: status = 'active'" />
              </el-form-item>
            </el-form>
          </el-card>

          <!-- Writer Side -->
          <el-card shadow="hover" class="config-card">
            <template #header><span>写入端 (Writer)</span></template>
            <el-form label-width="100px" size="small">
              <el-form-item label="数据源">
                <el-select v-model="form.writerDatasourceId" placeholder="选择目标数据源" style="width: 100%" @change="onWriterDsChange">
                  <el-option v-for="ds in datasources" :key="ds.id" :label="`${ds.name} (${ds.type})`" :value="ds.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="数据库">
                <el-select v-model="form.writerDatabase" placeholder="选择数据库" style="width: 100%" @change="onWriterDbChange" :disabled="!form.writerDatasourceId">
                  <el-option v-for="db in writerDatabases" :key="db" :label="db" :value="db" />
                </el-select>
              </el-form-item>
              <el-form-item label="表名">
                <el-select v-model="form.writerTable" placeholder="选择表" style="width: 100%" @change="onWriterTableChange" :disabled="!form.writerDatabase" filterable>
                  <el-option v-for="t in writerTables" :key="t.tableName" :label="t.tableName" :value="t.tableName" />
                </el-select>
              </el-form-item>
              <el-form-item label="列" v-if="writerColumnDefs.length">
                <div class="column-list">
                  <el-tag v-for="c in writerColumnDefs" :key="c.name" size="small" effect="plain" class="col-tag">
                    {{ c.name }}<span class="col-type"> ({{ c.type }})</span>
                  </el-tag>
                </div>
              </el-form-item>
              <el-form-item label="写入模式">
                <el-radio-group v-model="form.writeMode">
                  <el-radio value="insert">追加 (INSERT)</el-radio>
                  <el-radio value="overwrite">覆盖 (OVERWRITE)</el-radio>
                </el-radio-group>
              </el-form-item>
            </el-form>
          </el-card>
        </div>

        <!-- Field Mapping -->
        <el-card shadow="hover" class="mapping-card">
          <template #header>
            <div class="mapping-header">
              <span>字段映射</span>
              <div>
                <el-button size="small" @click="autoMatchColumns" :disabled="!form.readerColumns?.length && !form.writerColumns?.length">自动匹配</el-button>
                <el-button size="small" type="primary" @click="addMapping">添加映射</el-button>
              </div>
            </div>
          </template>
          <el-table :data="form.fieldMappings" border size="small" max-height="300">
            <el-table-column label="源列" min-width="160">
              <template #default="{ row, $index }">
                <el-select v-model="row.readerColumn" placeholder="源列名或参数" filterable allow-create default-first-option style="width: 100%">
                  <el-option v-for="col in form.readerColumns || []" :key="col" :label="`${col} (${getReaderColType(col)})`" :value="col" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="源类型" width="110">
              <template #default="{ row }">
                <el-tag size="small" effect="plain">{{ getReaderColType(row.readerColumn) || '-' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="目标列" min-width="160">
              <template #default="{ row, $index }">
                <el-select v-model="row.writerColumn" placeholder="目标列名或参数" filterable allow-create default-first-option style="width: 100%">
                  <el-option v-for="col in form.writerColumns || []" :key="col" :label="`${col} (${getWriterColType(col)})`" :value="col" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="目标类型" width="110">
              <template #default="{ row }">
                <el-tag size="small" effect="plain">{{ getWriterColType(row.writerColumn) || '-' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="转换类型" min-width="130">
              <template #default="{ row }">
                <el-input v-model="row.targetType" placeholder="如: varchar" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="60">
              <template #default="{ $index }">
                <el-button size="small" type="danger" link @click="removeMapping($index)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!form.fieldMappings?.length" description="暂无字段映射，点击「自动匹配」或手动添加" :image-size="60" />
        </el-card>

        <!-- Advanced Settings -->
        <el-card shadow="hover" class="advanced-card">
          <template #header><span>高级设置</span></template>
          <el-form :model="form" label-width="120px" size="small" class="advanced-form">
            <el-row :gutter="20">
              <el-col :span="8">
                <el-form-item label="并发通道">
                  <el-input-number v-model="form.channel" :min="1" :max="20" style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="分片键">
                  <el-input v-model="form.splitPk" placeholder="如: id" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="批次大小">
                  <el-input-number v-model="form.batchSize" :min="100" :max="100000" :step="100" style="width: 100%" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="8">
                <el-form-item label="编码">
                  <el-select v-model="form.encoding" style="width: 100%">
                    <el-option label="UTF-8" value="UTF-8" />
                    <el-option label="GBK" value="GBK" />
                    <el-option label="UTF-16" value="UTF-16" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="前置 SQL">
                  <el-input v-model="form.preSql" type="textarea" :rows="1" placeholder="执行前 SQL" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="后置 SQL">
                  <el-input v-model="form.postSql" type="textarea" :rows="1" placeholder="执行后 SQL" />
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>
        </el-card>

        <!-- DataX JSON Preview -->
        <el-card v-if="showJsonPreview" shadow="hover" class="json-card">
          <template #header><span>DataX JSON 预览</span></template>
          <pre class="json-preview">{{ dataxJsonPreview || '请先配置读取端和写入端' }}</pre>
        </el-card>
      </template>

      <template v-else>
        <el-card shadow="hover" class="script-editor-card">
          <template #header>
            <span>DataX JSON 脚本</span>
          </template>
          <div ref="monacoContainer" class="monaco-container"></div>
        </el-card>
      </template>
    </div>

    <!-- SCRIPT 模式: 模板生成对话框 -->
    <el-dialog v-model="showScriptTemplateDialog" title="生成脚本模板" width="420px" :close-on-click-modal="false" :close-on-press-escape="false">
      <el-form label-position="top">
        <el-form-item label="源端数据库类型" required>
          <el-select v-model="scriptTemplateForm.readerDbType" style="width: 100%">
            <el-option label="MySQL" value="MYSQL" />
            <el-option label="Hive" value="HIVE" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标端数据库类型" required>
          <el-select v-model="scriptTemplateForm.writerDbType" style="width: 100%">
            <el-option label="MySQL" value="MYSQL" />
            <el-option label="Hive" value="HIVE" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showScriptTemplateDialog = false">取消</el-button>
        <el-button type="primary" @click="handleScriptTemplateConfirm">生成模板</el-button>
      </template>
    </el-dialog>
  </Layout>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import Layout from '@/components/common/Layout.vue'
import {
  getDataxTask,
  createDataxTask,
  updateDataxTask,
  publishDataxTask,
  getDataxTaskJson,
  type DataxTaskRequest,
  type FieldMapping,
} from '@/api/datax-task'
import { listDatasources, listDatasourceDatabases, listDatasourceTables, getDatasourceTableColumns } from '@/api/datasource'
import type { DatasourceConfig } from '@/types'
import { ArrowLeft, Check, Upload } from '@element-plus/icons-vue'
import * as monaco from 'monaco-editor'

const route = useRoute()
const router = useRouter()
const taskId = ref<number | null>(null)
const isEditMode = computed(() => !!taskId.value)
const currentStatus = ref<string | null>(null)
const saving = ref(false)
const showJsonPreview = ref(false)
const dataxJsonPreview = ref('')

const form = reactive<DataxTaskRequest>({
  name: '',
  description: '',
  configMode: 'UI',
  scriptContent: '',
  readerDatasourceId: null,
  readerDatabase: '',
  readerTable: '',
  readerColumns: [],
  readerWhere: '',
  writerDatasourceId: null,
  writerDatabase: '',
  writerTable: '',
  writerColumns: [],
  writeMode: 'insert',
  fieldMappings: [],
  channel: 1,
  splitPk: '',
  batchSize: 1000,
  encoding: 'UTF-8',
  preSql: '',
  postSql: '',
})

// SCRIPT 模板生成对话框
const showScriptTemplateDialog = ref(false)
const scriptTemplateForm = reactive({
  readerDbType: 'MYSQL',
  writerDbType: 'MYSQL',
})

const datasources = ref<DatasourceConfig[]>([])

// Monaco editor for SCRIPT mode
const monacoContainer = ref<HTMLDivElement | null>(null)
const scriptEditorReady = ref(false)
let scriptEditor: monaco.editor.IStandaloneCodeEditor | null = null

// Reader browsing state
const readerDatabases = ref<string[]>([])
const readerTables = ref<{tableSchema: string; tableName: string}[]>([])
const readerColumnsMap = ref<any[]>([])

// Writer browsing state
const writerDatabases = ref<string[]>([])
const writerTables = ref<{tableSchema: string; tableName: string}[]>([])
const writerColumnDefs = ref<any[]>([])

// ---- Monaco Editor (SCRIPT mode) ----

function initScriptEditor() {
  if (!monacoContainer.value || scriptEditorReady.value) return
  try {
    scriptEditor = monaco.editor.create(monacoContainer.value, {
      value: form.scriptContent || '',
      language: 'json',
      theme: 'vs-dark',
      automaticLayout: true,
      minimap: { enabled: false },
      fontSize: 13,
      lineNumbers: 'on',
      scrollBeyondLastLine: false,
      tabSize: 2,
      wordWrap: 'on',
    })

    // Sync editor content back to form on change
    scriptEditor.onDidChangeModelContent(() => {
      form.scriptContent = scriptEditor?.getValue() || ''
    })

    scriptEditorReady.value = true
  } catch (e) {
    console.error('Monaco init error:', e)
  }
}

function handleFormatScript() {
  if (!scriptEditor) return
  scriptEditor.getAction('editor.action.formatDocument')?.run()
}

function disposeScriptEditor() {
  if (scriptEditor) {
    scriptEditor.dispose()
    scriptEditor = null
    scriptEditorReady.value = false
  }
}

// ---- Datasource / DB / Table browsers ----

async function loadDatasources() {
  try {
    const res = await listDatasources()
    datasources.value = res.data
  } catch { /* ignore */ }
}

async function onReaderDsChange(dsId: number | null) {
  form.readerDatabase = ''
  form.readerTable = ''
  form.readerColumns = []
  readerTables.value = []
  readerColumnsMap.value = []
  if (!dsId) { readerDatabases.value = []; return }
  try {
    const res = await listDatasourceDatabases(dsId)
    readerDatabases.value = res.data
  } catch { readerDatabases.value = [] }
}

async function onReaderDbChange(db: string) {
  form.readerTable = ''
  form.readerColumns = []
  readerColumnsMap.value = []
  if (!db || !form.readerDatasourceId) return
  try {
    const res = await listDatasourceTables(form.readerDatasourceId, db)
    readerTables.value = res.data as any
  } catch { readerTables.value = [] }
}

async function onReaderTableChange(table: string) {
  form.readerColumns = []
  form.fieldMappings = []
  if (!table || !form.readerDatasourceId || !form.readerDatabase) return
  try {
    const res = await getDatasourceTableColumns(form.readerDatasourceId, table, form.readerDatabase)
    readerColumnsMap.value = res.data as any
    form.readerColumns = readerColumnsMap.value.map((c: any) => c.name)
  } catch { readerColumnsMap.value = [] }
  tryAutoMatch()
  updateJsonPreview()
}

async function onWriterDsChange(dsId: number | null) {
  form.writerDatabase = ''
  form.writerTable = ''
  form.writerColumns = []
  form.fieldMappings = []
  writerTables.value = []
  writerColumnDefs.value = []
  if (!dsId) { writerDatabases.value = []; return }
  try {
    const res = await listDatasourceDatabases(dsId)
    writerDatabases.value = res.data
  } catch { writerDatabases.value = [] }
}

async function onWriterDbChange(db: string) {
  form.writerTable = ''
  form.writerColumns = []
  form.fieldMappings = []
  writerColumnDefs.value = []
  if (!db || !form.writerDatasourceId) return
  try {
    const res = await listDatasourceTables(form.writerDatasourceId, db)
    writerTables.value = res.data as any
  } catch { writerTables.value = [] }
}

async function onWriterTableChange(table: string) {
  form.writerColumns = []
  form.fieldMappings = []
  if (!table || !form.writerDatasourceId || !form.writerDatabase) return
  try {
    const res = await getDatasourceTableColumns(form.writerDatasourceId, table, form.writerDatabase)
    writerColumnDefs.value = res.data as any
    form.writerColumns = writerColumnDefs.value.map((c: any) => c.name)
  } catch { writerColumnDefs.value = [] }
  tryAutoMatch()
  updateJsonPreview()
}

// ---- Field Mapping ----

function tryAutoMatch() {
  const readerCols = form.readerColumns || []
  const writerCols = form.writerColumns || []
  if (!readerCols.length || !writerCols.length) return
  // Match by column name intersection
  const common = writerCols.filter(c => readerCols.includes(c))
  const mappings: FieldMapping[] = common.map(col => ({
    readerColumn: col,
    writerColumn: col,
    targetType: '',
    maskingRuleId: null,
  }))
  // Add remaining writer columns unmatched
  const matched = new Set(common)
  for (const col of writerCols) {
    if (!matched.has(col)) {
      mappings.push({ readerColumn: '', writerColumn: col, targetType: '', maskingRuleId: null })
    }
  }
  form.fieldMappings = mappings
}

function getReaderColType(colName: string): string {
  if (!colName) return ''
  const col = readerColumnsMap.value.find((c: any) => c.name === colName)
  return col ? col.type : ''
}

function getWriterColType(colName: string): string {
  if (!colName) return ''
  const col = writerColumnDefs.value.find((c: any) => c.name === colName)
  return col ? col.type : ''
}

function addMapping() {
  if (!form.fieldMappings) {
    form.fieldMappings = []
  }
  form.fieldMappings.push({ readerColumn: '', writerColumn: '', targetType: '', maskingRuleId: null })
  updateJsonPreview()
}

function removeMapping(index: number) {
  form.fieldMappings?.splice(index, 1)
  updateJsonPreview()
}

function autoMatchColumns() {
  tryAutoMatch()
  updateJsonPreview()
  if (form.fieldMappings?.length) {
    ElMessage.success(`自动匹配了 ${form.fieldMappings.length} 个字段`)
  }
}

function onMappingChange() {
  // placeholder for future validation
}

// ---- DataX JSON Preview ----

watch(() => [form.readerDatasourceId, form.readerTable, form.writerDatasourceId, form.writerTable], () => {
  updateJsonPreview()
}, { deep: true })

function updateJsonPreview() {
  if (!form.readerDatasourceId || !form.writerDatasourceId || !form.readerTable || !form.writerTable) {
    dataxJsonPreview.value = ''
    return
  }
  // Build preview JSON client-side
  const readerDs = datasources.value.find(d => d.id === form.readerDatasourceId)
  const writerDs = datasources.value.find(d => d.id === form.writerDatasourceId)
  if (!readerDs || !writerDs) { dataxJsonPreview.value = ''; return }

  const readerType = readerDs.type === 'HIVE' ? 'hivereader' : 'mysqlreader'
  const writerType = writerDs.type === 'HIVE' ? 'hivewriter' : 'mysqlwriter'

  // Use field mappings for column definitions if available
  const readerCols = form.fieldMappings?.length
    ? form.fieldMappings.map(m => m.readerColumn).filter(Boolean)
    : (form.readerColumns?.length ? form.readerColumns : [])
  const writerCols = form.fieldMappings?.length
    ? form.fieldMappings.map(m => m.writerColumn).filter(Boolean)
    : (form.writerColumns?.length ? form.writerColumns : [])

  const preview = {
    job: {
      setting: { speed: { channel: form.channel || 1 } },
      content: [{
        reader: {
          name: readerType,
          parameter: {
            username: (readerDs as any).username || '',
            password: (readerDs as any).password || '',
            connection: [{
              jdbcUrl: [buildJdbcUrl(readerDs, form.readerDatabase || readerDs.databaseName)],
              table: [form.readerTable],
            }],
            column: readerCols,
            ...(form.readerWhere ? { where: form.readerWhere } : {}),
            ...(form.splitPk ? { splitPk: form.splitPk } : {}),
          },
        },
        writer: {
          name: writerType,
          parameter: {
            username: (writerDs as any).username || '',
            password: (writerDs as any).password || '',
            connection: [{
              jdbcUrl: [buildJdbcUrl(writerDs, form.writerDatabase || writerDs.databaseName)],
              table: [form.writerTable],
            }],
            column: writerCols,
            writeMode: form.writeMode || 'insert',
            ...(form.preSql ? { preSql: [form.preSql] } : {}),
            ...(form.postSql ? { postSql: [form.postSql] } : {}),
            ...(form.batchSize ? { batchSize: form.batchSize } : {}),
            ...(form.encoding ? { encoding: form.encoding } : {}),
          },
        },
      }],
    },
  }
  dataxJsonPreview.value = JSON.stringify(preview, null, 2)
}

function buildJdbcUrl(ds: DatasourceConfig, db: string): string {
  const host = ds.host || 'localhost'
  const port = ds.port || 3306
  if (ds.type === 'HIVE') return `jdbc:hive2://${host}:${port}/${db}`
  return `jdbc:mysql://${host}:${port}/${db}?useUnicode=true&characterEncoding=utf-8&useSSL=false`
}

// ---- CRUD ----

async function loadTask(id: number) {
  try {
    const res = await getDataxTask(id)
    const task = res.data
    currentStatus.value = task.status || 'DRAFT'
    form.name = task.name
    form.description = task.description || ''
    form.configMode = task.configMode || 'UI'
    form.scriptContent = task.scriptContent || ''
    form.readerDatasourceId = task.readerDatasourceId
    form.readerDatabase = task.readerDatabase || ''
    form.readerTable = task.readerTable || ''
    form.readerColumns = task.readerColumns || []
    form.readerWhere = task.readerWhere || ''
    form.writerDatasourceId = task.writerDatasourceId
    form.writerDatabase = task.writerDatabase || ''
    form.writerTable = task.writerTable || ''
    form.writerColumns = task.writerColumns || []
    form.writeMode = task.writeMode || 'insert'
    form.fieldMappings = (task.fieldMappings || []).map(m => ({ ...m }))
    form.channel = task.channel || 1
    form.splitPk = task.splitPk || ''
    form.batchSize = task.batchSize || 1000
    form.encoding = task.encoding || 'UTF-8'
    form.preSql = task.preSql || ''
    form.postSql = task.postSql || ''

    // Re-load related data
    if (form.readerDatasourceId) {
      await onReaderDsChange(form.readerDatasourceId)
      if (form.readerDatabase) {
        await onReaderDbChange(form.readerDatabase)
        if (form.readerTable) {
          await onReaderTableChange(form.readerTable)
        }
      }
    }
    if (form.writerDatasourceId) {
      await onWriterDsChange(form.writerDatasourceId)
      if (form.writerDatabase) {
        await onWriterDbChange(form.writerDatabase)
        if (form.writerTable) {
          await onWriterTableChange(form.writerTable)
        }
      }
    }

    // Init Monaco for SCRIPT mode
    if (form.configMode === 'SCRIPT') {
      await nextTick()
      initScriptEditor()
    }
  } catch {
    ElMessage.error('加载任务失败')
  }
}

async function handleSave() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入任务名称')
    return
  }

  saving.value = true
  try {
    if (form.configMode === 'SCRIPT') {
      // SCRIPT mode: only save name + configMode + scriptContent
      const payload: DataxTaskRequest = {
        name: form.name.trim(),
        configMode: 'SCRIPT',
        scriptContent: form.scriptContent,
      }
      if (taskId.value) {
        await updateDataxTask({ ...payload, id: taskId.value })
      } else {
        const res = await createDataxTask(payload)
        taskId.value = res.data.id
        currentStatus.value = res.data.status || 'DRAFT'
      }
    } else {
      const payload = { ...form }
      if (taskId.value) {
        await updateDataxTask({ ...payload, id: taskId.value })
        ElMessage.success('任务已更新')
      } else {
        const res = await createDataxTask(payload)
        taskId.value = res.data.id
        currentStatus.value = res.data.status || 'DRAFT'
      }
    }
    ElMessage.success('任务已保存')
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function handlePublish() {
  if (!taskId.value) {
    ElMessage.warning('请先保存任务')
    return
  }
  try {
    await ElMessageBox.confirm('发布后该任务可被工作流调度执行，确定发布？', '发布确认')
    await publishDataxTask(taskId.value)
    currentStatus.value = 'PUBLISHED'
    ElMessage.success('已发布')
  } catch { /* cancelled */ }
}

function handleBack() {
  router.push('/datax-task')
}

// ---- SCRIPT 模板生成 ----

function generateScriptTemplate(readerDbType: string, writerDbType: string): string {
  const readerType = readerDbType === 'HIVE' ? 'hivereader' : 'mysqlreader'
  const writerType = writerDbType === 'HIVE' ? 'hivewriter' : 'mysqlwriter'
  return JSON.stringify({
    job: {
      setting: { speed: { channel: 1 } },
      content: [{
        reader: {
          name: readerType,
          parameter: {
            username: '', password: '',
            connection: [{ jdbcUrl: [''], table: [''] }],
            column: ['*'],
          },
        },
        writer: {
          name: writerType,
          parameter: {
            username: '', password: '',
            connection: [{ jdbcUrl: [''], table: [''] }],
            column: ['*'],
            writeMode: 'insert',
          },
        },
      }],
    },
  }, null, 2)
}

function handleScriptTemplateConfirm() {
  form.scriptContent = generateScriptTemplate(scriptTemplateForm.readerDbType, scriptTemplateForm.writerDbType)
  showScriptTemplateDialog.value = false
  // Update Monaco editor content
  if (scriptEditor) {
    scriptEditor.setValue(form.scriptContent)
  }
}

function handleSwitchTemplate() {
  // Try to detect current DB types from script content
  const content = form.scriptContent || ''
  if (content.includes('hivereader')) {
    scriptTemplateForm.readerDbType = 'HIVE'
  } else {
    scriptTemplateForm.readerDbType = 'MYSQL'
  }
  if (content.includes('hivewriter')) {
    scriptTemplateForm.writerDbType = 'HIVE'
  } else {
    scriptTemplateForm.writerDbType = 'MYSQL'
  }
  showScriptTemplateDialog.value = true
}

onMounted(async () => {
  await loadDatasources()

  const id = route.params.id
  if (id && id !== 'create') {
    taskId.value = Number(id)
    await loadTask(taskId.value)
    // SCRIPT 模式且无脚本内容时弹出模板生成对话框
    if (form.configMode === 'SCRIPT' && !form.scriptContent?.trim()) {
      await nextTick()
      showScriptTemplateDialog.value = true
    }
  }
})

onBeforeUnmount(() => {
  disposeScriptEditor()
})
</script>

<style scoped>
.editor-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.toolbar-card { flex-shrink: 0; }
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.toolbar-left, .toolbar-right {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}
.config-panels {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.config-card { min-width: 0; }
.mapping-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.advanced-form { width: 100%; }
.column-list {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  max-height: 140px;
  overflow-y: auto;
}
.col-tag { font-size: 12px; }
.col-type { color: #909399; }
.script-editor-card { flex-shrink: 0; }
.monaco-container {
  width: 100%;
  height: 60vh;
  border-radius: 4px;
  overflow: hidden;
}
.json-preview {
  background: #1e1e1e;
  color: #d4d4d4;
  padding: 16px;
  border-radius: 4px;
  overflow: auto;
  max-height: 400px;
  font-size: 13px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
}
@media (max-width: 900px) {
  .config-panels { grid-template-columns: 1fr; }
}
</style>
