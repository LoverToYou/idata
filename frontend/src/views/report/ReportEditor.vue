<template>
  <Layout>
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>{{ isEditing ? '编辑报表' : '新建报表' }}</span>
          <div>
            <el-button @click="router.push('/report')">返回列表</el-button>
            <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
          </div>
        </div>
      </template>

      <el-form :model="form" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="报表名称" required>
              <el-input v-model="form.name" placeholder="例：每日任务成功率" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="数据源" required>
              <el-select v-model="form.datasourceId" placeholder="选择数据源" style="width: 100%" @change="handleDatasourceChange">
                <el-option v-for="ds in datasources" :key="ds.id" :label="ds.name" :value="ds.id as number" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="自动刷新">
              <el-input-number v-model="form.refreshInterval" :min="0" :max="86400" controls-position="right" style="width: 140px" />
              <span class="hint-inline">秒（0=不自动）</span>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="描述">
          <el-input v-model="form.description" placeholder="选填" />
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="查询 SQL" required>
              <div class="sql-editor-wrapper">
                <div ref="sqlContainer" class="sql-editor"></div>
              </div>
              <div class="hint-block">
                支持参数管理中的参数占位：<code v-pre>${参数名}</code>；结果集用于报表展示。
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="展示形式">
              <el-radio-group v-model="form.chartType">
                <el-radio-button value="TABLE">表格</el-radio-button>
                <el-radio-button value="LINE">折线图</el-radio-button>
                <el-radio-button value="BAR">柱状图</el-radio-button>
                <el-radio-button value="PIE">饼图</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item v-if="form.chartType !== 'TABLE'" label="字段映射">
              <div class="field-mapping">
                <el-select v-model="config.xField" placeholder="X / 分类字段" size="small" style="width: 100%">
                  <el-option v-for="c in columns" :key="c" :label="c" :value="c" />
                </el-select>
                <el-select v-model="config.yFields" multiple placeholder="Y / 数值字段" size="small" style="width: 100%">
                  <el-option v-for="c in columns" :key="c" :label="c" :value="c" />
                </el-select>
                <el-select v-if="form.chartType === 'LINE' || form.chartType === 'BAR'" v-model="config.seriesField" clearable placeholder="分组字段（可选）" size="small" style="width: 100%">
                  <el-option v-for="c in columns" :key="c" :label="c" :value="c" />
                </el-select>
                <el-input-number v-model="config.limit" :min="0" :max="5000" size="small" controls-position="right" style="width: 100%" placeholder="显示条数上限(0=全部)" />
                <div v-if="isValueAxisChart" class="y-axis-range">
                  <el-input-number v-model="config.yMin" :controls="false" size="small" placeholder="Y轴最小" style="width: 100%" />
                  <el-input-number v-model="config.yMax" :controls="false" size="small" placeholder="Y轴最大" style="width: 100%" />
                </div>
                <div v-if="isValueAxisChart" class="hint-inline">
                  设置「Y 轴最小」即截断坐标轴（不从 0 开始），留空为自动。
                </div>
                <div v-if="isLineChart" class="smooth-row">
                  <span class="smooth-label">曲线弧度</span>
                  <el-slider v-model="smoothValue" :min="0" :max="1" :step="0.1" class="smooth-slider" />
                  <span class="smooth-value">{{ smoothValue.toFixed(1) }}</span>
                </div>
                <div v-if="isLineChart" class="hint-inline">
                  0 = 直线，1 = 最弯；默认 0.5。
                </div>
              </div>
            </el-form-item>
            <div class="preview-actions">
              <el-button type="primary" plain :loading="previewing" @click="handlePreview">运行预览</el-button>
              <span v-if="result" class="hint-inline">
                {{ result.rows.length }} 行 / {{ result.elapsedMs }} ms
              </span>
            </div>
            <!-- 表格型：预览区直接展示数据表格（图表位置）；其他类型先画图 -->
            <el-table
              v-if="result && isTableType"
              :data="result.rows.slice(0, 100)"
              border
              stripe
              height="300"
              style="width: 100%"
            >
              <el-table-column
                v-for="c in result.columns"
                :key="c"
                :prop="c"
                :label="c"
                min-width="120"
                show-overflow-tooltip
              />
            </el-table>
            <EChart v-else-if="chartOption" :option="chartOption" height="300px" />
            <el-empty v-else-if="!result" description="点击「运行预览」查看结果" :image-size="70" />
          </el-col>
        </el-row>
      </el-form>
    </el-card>

    <el-card v-if="result" shadow="hover" class="result-card">
      <template #header>
        <div class="card-header"><span>预览结果（前 {{ Math.min(result.rows.length, 100) }} 行）</span></div>
      </template>
      <el-table :data="result.rows.slice(0, 100)" stripe height="320" border>
        <el-table-column v-for="c in result.columns" :key="c" :prop="c" :label="c" min-width="120" show-overflow-tooltip />
      </el-table>
    </el-card>
  </Layout>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as monaco from 'monaco-editor'
import Layout from '@/components/common/Layout.vue'
import EChart from '@/components/chart/EChart.vue'
import { listDatasources, listDatasourceDatabases, listDatasourceTables } from '@/api/datasource'
import { createReport, getReport, moveReport, previewReport, updateReport, type SqlResult } from '@/api/report'
import { buildChartOption, parseChartConfig, type ChartConfig } from '@/utils/reportChart'
import { detectGrammarContext, getCachedGrammarContext, getSqlKeywords, setCachedGrammarContext, type SqlKeywords } from '@/api/grammar'
import { listUdfsByDatasource } from '@/api/udf'
import { createGrammarContextUpdater, registerSqlCompletion, type SqlTableRef } from '@/utils/sqlCompletion'
import type { DatasourceConfig, UdfDefinition } from '@/types'

const route = useRoute()
const router = useRouter()

const isEditing = computed(() => !!route.params.id)
const datasources = ref<DatasourceConfig[]>([])
const saving = ref(false)
const previewing = ref(false)
const result = ref<SqlResult | null>(null)
const sqlContainer = ref<HTMLDivElement>()
let editor: monaco.editor.IStandaloneCodeEditor | null = null
let completionDisposable: monaco.IDisposable | null = null

// --- SQL 提示：补全候选来源（关键字 / 库 / 表 / UDF）---
const keywords = ref<SqlKeywords | null>(null)
const databases = ref<string[]>([])
const tables = ref<SqlTableRef[]>([])
const tablesByDb = ref<Record<string, SqlTableRef[]>>({})
const udfs = ref<UdfDefinition[]>([])
const grammarUpdater = createGrammarContextUpdater(detectGrammarContext, setCachedGrammarContext)

const form = reactive({
  name: '',
  description: '',
  datasourceId: null as number | null,
  sqlContent: 'SELECT 1 AS demo_value',
  chartType: 'TABLE',
  refreshInterval: 0,
  folderId: null as number | null,
})

const config = reactive<ChartConfig>({ xField: undefined, yFields: [], seriesField: undefined, limit: 0, yMin: null, yMax: null, smooth: null })

const columns = computed(() => result.value?.columns || [])
const isTableType = computed(() => (form.chartType || 'TABLE').toUpperCase() === 'TABLE')
/** 柱状图/折线图才有数值 Y 轴，饼图没有 */
const isValueAxisChart = computed(() => ['LINE', 'BAR'].includes((form.chartType || '').toUpperCase()))
/** 只有折线图可调曲线弧度 */
const isLineChart = computed(() => (form.chartType || '').toUpperCase() === 'LINE')
/** 曲线弧度：未配置时按默认平滑 0.5 展示 */
const smoothValue = computed({
  get: () => (typeof config.smooth === 'number' ? config.smooth : 0.5),
  set: (v: number) => { config.smooth = v },
})
const chartOption = computed(() =>
  result.value ? buildChartOption(form.chartType, result.value.columns, result.value.rows, config) : null,
)

function initEditor() {
  if (!sqlContainer.value) return
  const ed = monaco.editor.create(sqlContainer.value, {
    value: form.sqlContent,
    language: 'sql',
    theme: 'vs',
    fontSize: 13,
    minimap: { enabled: false },
    scrollBeyondLastLine: false,
    automaticLayout: true,
    quickSuggestions: true,
    suggestOnTriggerCharacters: true,
  })
  editor = ed

  ed.onDidChangeModelContent(() => {
    form.sqlContent = ed.getValue()
  })

  // 光标移动时刷新语法上下文，补全据此按位置过滤候选
  ed.onDidChangeCursorPosition(() => {
    const model = ed.getModel()
    const pos = ed.getPosition()
    if (!model || !pos) return
    const sql = model.getValue()
    if (!sql.trim()) return
    grammarUpdater.update(sql, model.getOffsetAt(pos))
  })

  completionDisposable?.dispose()
  completionDisposable = registerSqlCompletion(() => ({
    keywords: keywords.value,
    databases: databases.value,
    tables: tables.value,
    tablesByDb: tablesByDb.value,
    udfs: udfs.value,
    datasourceType: currentDatasourceType.value,
    grammarContext: getCachedGrammarContext(),
  }))
}

const currentDatasourceType = computed(
  () => datasources.value.find((d) => d.id === form.datasourceId)?.type,
)

// --- SQL 提示：候选数据加载 ---

async function loadKeywords(dbType = 'MYSQL') {
  try {
    const res = await getSqlKeywords(dbType)
    keywords.value = res.data
  } catch { /* ignore */ }
}

async function loadDatabases(datasourceId: number) {
  try {
    const res = await listDatasourceDatabases(datasourceId)
    databases.value = res.data || []
  } catch {
    databases.value = []
  }
}

async function loadTables(datasourceId: number, database?: string) {
  try {
    const res = await listDatasourceTables(datasourceId, database)
    const mapped = (res.data || [])
      .map((t: any) => ({
        tableName: t.tableName || t.TABLE_NAME || '',
        schema: t.tableSchema || t.TABLE_SCHEMA || (database || ''),
      }))
      .filter((t: SqlTableRef) => t.tableName)
    if (database) tablesByDb.value[database.toUpperCase()] = mapped
    else tables.value = mapped
  } catch {
    if (database) tablesByDb.value[database.toUpperCase()] = []
    else tables.value = []
  }
}

async function loadUdfs(datasourceId: number) {
  // UDF 仅 Hive 数据源可用，其他类型后端会返回 400
  if (currentDatasourceType.value !== 'HIVE') {
    udfs.value = []
    return
  }
  try {
    const res = await listUdfsByDatasource(datasourceId)
    udfs.value = res.data || []
  } catch {
    udfs.value = []
  }
}

/** 按当前数据源刷新关键字 / 库 / 表 / UDF，供 SQL 补全使用 */
async function refreshSqlHints() {
  const id = form.datasourceId
  await loadKeywords(currentDatasourceType.value || 'MYSQL')
  if (!id) {
    databases.value = []
    tables.value = []
    tablesByDb.value = {}
    udfs.value = []
    return
  }
  try {
    await loadDatabases(id)
    await loadTables(id)
    await Promise.all(databases.value.map((db) => loadTables(id, db)))
    await loadUdfs(id)
  } catch {
    // 数据源不可用（如未启动）时不影响 SQL 编写
  }
}

function handleDatasourceChange() {
  refreshSqlHints()
}

async function loadDatasources() {
  try {
    const res = await listDatasources()
    datasources.value = res.data || []
  } catch {
    /* ignore */
  }
}

async function loadReport(id: number) {
  const res = await getReport(id)
  const data = res.data
  form.name = data.name
  form.description = data.description || ''
  form.datasourceId = data.datasourceId
  form.sqlContent = data.sqlContent
  form.chartType = data.chartType || 'TABLE'
  form.refreshInterval = data.refreshInterval || 0
  form.folderId = data.folderId ?? null
  Object.assign(config, parseChartConfig(data.chartConfig))
  config.yFields = config.yFields || []
  editor?.setValue(data.sqlContent)
}

async function handlePreview() {
  if (!form.datasourceId) {
    ElMessage.warning('请选择数据源')
    return
  }
  if (!form.sqlContent.trim()) {
    ElMessage.warning('请输入查询 SQL')
    return
  }
  previewing.value = true
  try {
    const res = await previewReport(form.datasourceId, form.sqlContent)
    result.value = res.data
    const cols = res.data.columns || []
    if (!config.xField || !cols.includes(config.xField)) config.xField = cols[0]
    if (!config.yFields || config.yFields.length === 0 || !config.yFields.every((f) => cols.includes(f))) {
      config.yFields = cols.filter((c) => res.data.rows.some((r) => typeof r[c] === 'number')).slice(0, 1)
    }
    ElMessage.success(`查询完成：${res.data.rows.length} 行，耗时 ${res.data.elapsedMs} ms`)
  } catch (e: any) {
    ElMessage.error(e.message || '查询失败')
  } finally {
    previewing.value = false
  }
}

async function handleSave() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入报表名称')
    return
  }
  if (!form.datasourceId) {
    ElMessage.warning('请选择数据源')
    return
  }
  if (!form.sqlContent.trim()) {
    ElMessage.warning('请输入查询 SQL')
    return
  }
  saving.value = true
  try {
    const payload = {
      name: form.name,
      description: form.description,
      datasourceId: form.datasourceId,
      sqlContent: form.sqlContent,
      chartType: form.chartType,
      chartConfig: JSON.stringify({ ...config, yFields: config.yFields || [] }),
      refreshInterval: form.refreshInterval,
      folderId: form.folderId,
    }
    if (isEditing.value) {
      await updateReport({ ...payload, id: Number(route.params.id) })
      await moveReport(Number(route.params.id), form.folderId ?? null)
      ElMessage.success('报表已更新')
    } else {
      await createReport(payload)
      ElMessage.success('报表已创建')
    }
    router.push('/report')
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await loadDatasources()
  initEditor()
  if (isEditing.value) {
    try {
      await loadReport(Number(route.params.id))
    } catch (e: any) {
      ElMessage.error(e.message || '加载报表失败')
    }
  } else {
    // 从目录树的「+」进入时预选文件夹
    const presetFolder = Number(route.query.folderId)
    if (presetFolder) form.folderId = presetFolder
  }
  // 先加载关键字（不依赖数据源），再按已选数据源补齐库 / 表 / UDF
  await refreshSqlHints()
})

onBeforeUnmount(() => {
  completionDisposable?.dispose()
  completionDisposable = null
  grammarUpdater.dispose()
  editor?.dispose()
  editor = null
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.sql-editor-wrapper {
  width: 100%;
  border: 1px solid var(--border);
  border-radius: 4px;
  overflow: hidden;
}
.sql-editor {
  height: 300px;
  width: 100%;
}
.field-mapping {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}
.y-axis-range {
  display: flex;
  gap: 8px;
  width: 100%;
}
.smooth-row {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}
.smooth-label {
  font-size: var(--fs-sm);
  color: var(--text-sub);
  white-space: nowrap;
}
.smooth-slider {
  flex: 1;
  min-width: 0;
}
.smooth-value {
  font-size: var(--fs-sm);
  color: var(--text-sub);
  width: 22px;
  text-align: right;
}
.preview-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.hint-inline {
  font-size: var(--fs-sm);
  color: var(--text-sub);
  margin-left: 8px;
}
.hint-block {
  font-size: var(--fs-sm);
  color: var(--text-sub);
  line-height: 1.6;
  width: 100%;
}
.hint-block code {
  background: var(--bg-muted);
  padding: 1px 4px;
  border-radius: 3px;
  color: var(--primary);
}
.result-card {
  margin-top: 16px;
}
</style>
