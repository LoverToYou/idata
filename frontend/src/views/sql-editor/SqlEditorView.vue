<template>
  <div class="sql-task-view">
  <Layout>
    <!-- List View -->
    <el-card v-if="mode === 'list'" shadow="hover">
      <template #header>
        <div class="card-header">
          <span>SQL 任务管理</span>
          <el-button type="primary" @click="handleNewTask">
            <el-icon><Plus /></el-icon> 新建任务
          </el-button>
        </div>
      </template>

      <el-table :data="tasks" stripe v-loading="loadingTasks" @row-dblclick="handleEditTask">
        <el-table-column type="index" label="#" width="60" />
        <el-table-column prop="name" label="任务名称" min-width="200" />
        <el-table-column label="数据库类型" width="130">
          <template #default="{ row }">
            <span
              :class="['status-dot', row.datasourceConnected === true ? 'dot-on' : row.datasourceConnected === false ? 'dot-off' : 'dot-na']"
            />
            <el-tag :type="getDsTypeTagType(row.datasourceType)" size="small" effect="plain">
              {{ row.datasourceType || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="任务类型" width="100">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" type="info">{{ row.sqlType || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'PUBLISHED' ? 'success' : 'info'" size="small" effect="light">
              {{ row.status === 'PUBLISHED' ? '已发布' : '草稿' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建人" width="120">
          <template #default="{ row }">{{ row.createdBy || '-' }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="180">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="180">
          <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handleEditTask(row)">编辑</el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              size="small"
              type="success"
              @click="handlePublishTable(row)"
            >
              发布
            </el-button>
            <el-button
              v-if="row.status === 'PUBLISHED'"
              size="small"
              type="warning"
              @click="handleUnpublishTable(row)"
            >
              下架
            </el-button>
            <el-button size="small" type="danger" @click="handleDeleteTask(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- Editor View -->
    <div v-else class="editor-view">
      <!-- Toolbar -->
      <el-card shadow="hover" class="toolbar-card">
        <div class="toolbar">
          <div class="toolbar-left">
            <el-button @click="handleBackToList">
              <el-icon><ArrowLeft /></el-icon> 返回
            </el-button>
            <el-input
              v-model="taskName"
              placeholder="任务名称"
              style="width: 240px"
              size="default"
            />
            <el-tag
              v-if="currentTask"
              :type="getDsTypeTagType(currentTask.datasourceType)"
              size="small"
              effect="plain"
            >
              {{ currentTask.datasourceType || '-' }}
            </el-tag>
            <el-tag
              v-if="currentTask"
              :type="currentTask.status === 'PUBLISHED' ? 'success' : 'info'"
              size="small"
              effect="light"
            >
              {{ currentTask.status === 'PUBLISHED' ? '已发布' : '草稿' }}
            </el-tag>
            <el-select
              v-model="selectedDatasource"
              placeholder="选择数据源"
              style="width: 200px"
              @change="onDatasourceChange"
            >
              <el-option
                v-for="ds in datasources"
                :key="ds.id"
                :label="`${ds.name} (${ds.type})`"
                :value="ds.id"
              />
            </el-select>
          </div>
          <div class="toolbar-right">
            <el-button
              v-if="currentTask && currentTask.status === 'DRAFT'"
              @click="handlePublish"
              type="success"
            >
              <el-icon><Upload /></el-icon> 发布
            </el-button>
            <el-button
              v-if="currentTask && currentTask.status === 'PUBLISHED'"
              @click="handleUnpublish"
              type="warning"
            >
              下架
            </el-button>
            <el-button @click="handleSave" :loading="saving" type="primary">
              <el-icon><Check /></el-icon> 保存
            </el-button>
            <el-button @click="insertPromptTemplate">
              插入提示词
            </el-button>
            <el-button @click="handleFormat" :loading="formatting">
              格式化
            </el-button>
            <el-button type="success" @click="handleAnalyze" :loading="analyzing">
              解析
            </el-button>
            <el-button type="warning" @click="handleExplain" :loading="explaining">
              执行计划
            </el-button>
            <el-button type="primary" @click="handleExecute" :loading="executing">
              ▶ 运行 (Ctrl+Enter)
            </el-button>
          </div>
        </div>
      </el-card>

      <!-- Editor -->
      <el-card shadow="hover" class="editor-card">
        <div ref="monacoContainer" class="monaco-container"></div>
      </el-card>

      <!-- Results -->
      <el-card shadow="hover" class="result-card">
        <el-tabs v-model="activeTab" class="result-tabs">
          <el-tab-pane label="查询结果" name="result">
            <div v-if="executeResult">
              <div class="result-meta">
                <span v-if="executeResult.elapsedMs">耗时: {{ executeResult.elapsedMs }}ms</span>
                <span v-if="executeResult.affectedRows >= 0"> | 行数: {{ executeResult.affectedRows }}</span>
              </div>
              <el-table
                v-if="executeResult.columns && executeResult.columns.length > 0"
                :data="executeResult.rows"
                border
                stripe
                max-height="300"
                size="small"
                style="width: 100%"
              >
                <el-table-column
                  v-for="col in executeResult.columns"
                  :key="col"
                  :prop="col"
                  :label="col"
                  min-width="120"
                />
              </el-table>
              <el-empty v-else-if="executeResult.affectedRows >= 0" description="执行成功，无返回数据" />
              <el-alert v-if="executeResult.errorMessage" type="error" :description="executeResult.errorMessage" show-icon />
            </div>
            <el-empty v-else description="运行 SQL 查看结果" />
          </el-tab-pane>

          <el-tab-pane label="执行计划" name="plan">
            <div v-if="planResult">
              <div class="result-meta">
                <span>耗时: {{ planResult.elapsedMs }}ms</span>
              </div>
              <el-table
                :data="planResult.plan"
                border
                stripe
                size="small"
                max-height="300"
                style="width: 100%"
              >
                <el-table-column prop="id" label="ID" width="60" />
                <el-table-column prop="selectType" label="类型" width="120" />
                <el-table-column prop="table" label="表" min-width="120" />
                <el-table-column prop="type" label="访问方式" width="120">
                  <template #default="{ row }">
                    <el-tag :type="getPlanTypeTag(row.type)" size="small">
                      {{ row.type }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="possibleKeys" label="可能索引" width="140" />
                <el-table-column prop="key" label="实际索引" width="140">
                  <template #default="{ row }">
                    <span v-if="row.key" class="highlight-key">{{ row.key }}</span>
                    <span v-else class="no-key">-</span>
                  </template>
                </el-table-column>
                <el-table-column prop="rows" label="行数" width="80" />
                <el-table-column prop="extra" label="额外信息" min-width="200" />
              </el-table>
              <el-alert v-if="planResult.rawPlan && planResult.plan?.length === 0" type="error" :description="planResult.rawPlan" show-icon />
            </div>
            <el-empty v-else description="点击「执行计划」查看" />
          </el-tab-pane>

          <el-tab-pane label="优化建议" name="suggestions">
            <div v-if="suggestions.length > 0" class="suggestion-list">
              <el-card
                v-for="(s, idx) in suggestions"
                :key="idx"
                :class="['suggestion-card', 'suggestion-' + s.type.toLowerCase()]"
                shadow="hover"
                style="margin-bottom: 12px"
              >
                <div class="suggestion-header">
                  <el-tag :type="getSuggestionTagType(s.type)" size="small" effect="dark">
                    {{ s.type }}
                  </el-tag>
                  <strong style="margin-left: 8px">{{ s.title }}</strong>
                </div>
                <p class="suggestion-detail">{{ s.detail }}</p>
                <p class="suggestion-advice">
                  <el-icon><Warning /></el-icon> {{ s.suggestion }}
                </p>
              </el-card>
            </div>
            <el-empty v-else-if="analyzed" description="未发现优化建议" />
            <el-empty v-else description="点击「解析」或「全部分析」查看优化建议" />
          </el-tab-pane>

          <el-tab-pane label="分析信息" name="analysis">
            <div v-if="analysis" class="analysis-info">
              <el-descriptions :column="2" border size="small">
                <el-descriptions-item label="SQL 类型">{{ analysis.queryType }}</el-descriptions-item>
                <el-descriptions-item label="语法校验">
                  <el-tag :type="analysis.valid ? 'success' : 'danger'" size="small">
                    {{ analysis.valid ? '通过' : '失败' }}
                  </el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="涉及表">
                  {{ analysis.tables?.join(', ') || '-' }}
                </el-descriptions-item>
                <el-descriptions-item label="JOIN 类型">
                  {{ analysis.joinTypes?.join(', ') || '-' }}
                </el-descriptions-item>
              </el-descriptions>
              <el-alert v-if="!analysis.valid" type="error" :description="analysis.errorMessage" show-icon style="margin-top: 12px" />
            </div>
            <el-empty v-else description="暂无分析数据" />
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </div>
  </Layout>

  <!-- Create Task Dialog -->
  <el-dialog
      v-model="createDialogVisible"
      title="新建 SQL 任务"
      width="480px"
      :close-on-click-modal="false"
      @closed="onCreateDialogClosed"
    >
      <el-form :model="newTaskForm" label-width="100px" :rules="createFormRules" ref="createFormRef">
        <el-form-item label="任务名称" prop="name">
          <el-input v-model="newTaskForm.name" placeholder="请输入任务名称" maxlength="100" />
        </el-form-item>
        <el-form-item label="数据库类型" prop="dbType">
          <el-select v-model="newTaskForm.dbType" placeholder="请选择数据库类型" style="width: 100%" @change="onDbTypeChange">
            <el-option label="MySQL" value="MYSQL" />
            <el-option label="Hive" value="HIVE" />
          </el-select>
        </el-form-item>
        <el-form-item label="数据源" prop="datasourceId">
          <el-select v-model="newTaskForm.datasourceId" placeholder="请选择数据源" style="width: 100%" :disabled="!newTaskForm.dbType">
            <el-option
              v-for="ds in filteredDatasources"
              :key="ds.id"
              :label="`${ds.name} (${ds.host}:${ds.port}/${ds.databaseName})`"
              :value="ds.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleCreateConfirm" :loading="creating">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import Layout from '@/components/common/Layout.vue'
import { listDatasources } from '@/api/datasource'
import { executeSql, explainSql, analyzeSql, fullAnalyze, formatSql } from '@/api/sql'
import { listTasks, getTask, createTask, updateTask, deleteTask, publishTask, unpublishTask } from '@/api/sql-task'
import type { DatasourceConfig } from '@/types'
import type { SqlExecuteResult, ExplainPlanResult, SqlAnalysis, SqlSuggestion } from '@/api/sql'
import * as monaco from 'monaco-editor'

// --- Mode ---
const mode = ref<'list' | 'edit'>('list')

function handleBackToList() {
  editor?.dispose()
  editor = null
  mode.value = 'list'
  currentTaskId.value = null
  currentTask.value = null
  taskName.value = ''
}

// --- Task Table ---
const tasks = ref<any[]>([])
const loadingTasks = ref(false)
const currentTaskId = ref<number | null>(null)
const currentTask = ref<any>(null)
const taskName = ref('')

function formatTime(t: string) {
  if (!t) return ''
  return t.slice(0, 16).replace('T', ' ')
}

async function loadTasks() {
  loadingTasks.value = true
  try {
    const res = await listTasks()
    tasks.value = res.data
  } catch { /* ignore */ }
  finally { loadingTasks.value = false }
}

function handleEditTask(task: any) {
  currentTaskId.value = task.id
  currentTask.value = task
  taskName.value = task.name
  mode.value = 'edit'
  ensureEditor()
  nextTick(() => loadTaskDetail(task.id))
}

async function loadTaskDetail(id: number) {
  try {
    const res = await getTask(id)
    const detail = res.data
    if (editor) {
      editor.setValue(detail.sqlContent || '')
    }
    if (detail.datasourceId) {
      selectedDatasource.value = detail.datasourceId
    }
  } catch {
    ElMessage.error('加载任务失败')
  }
}

// --- Create Task Dialog ---
const createDialogVisible = ref(false)
const creating = ref(false)
const createFormRef = ref<any>(null)
const newTaskForm = reactive({
  name: '',
  dbType: '' as string,
  datasourceId: undefined as number | undefined,
})
const createFormRules = {
  name: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  dbType: [{ required: true, message: '请选择数据库类型', trigger: 'change' }],
  datasourceId: [{ required: true, message: '请选择数据源', trigger: 'change' }],
}

const filteredDatasources = computed(() => {
  if (!newTaskForm.dbType) return []
  return datasources.value.filter(ds => ds.type === newTaskForm.dbType)
})

function onDbTypeChange() {
  newTaskForm.datasourceId = undefined
}

function onCreateDialogClosed() {
  newTaskForm.name = ''
  newTaskForm.dbType = ''
  newTaskForm.datasourceId = undefined
}

function generateDefaultComment(dsType: string): string {
  const now = new Date()
  const pad = (n: number) => n.toString().padStart(2, '0')
  const timeStr = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}`
  return [
    '*****************************',
    '***创建时间: ' + timeStr,
    '***创建人: ',
    '***数据库类型: ' + dsType,
    '*****************************',
  ].join('\n')
}

function handleNewTask() {
  // Show create dialog instead of entering editor directly
  createDialogVisible.value = true
}

async function handleCreateConfirm() {
  const valid = await createFormRef.value?.validate().catch(() => false)
  if (!valid) return

  creating.value = true
  try {
    const defaultComment = generateDefaultComment(newTaskForm.dbType)
    const payload = {
      name: newTaskForm.name.trim(),
      sqlContent: defaultComment,
      datasourceId: newTaskForm.datasourceId!,
    }
    const res = await createTask(payload)
    currentTaskId.value = res.data.id
    currentTask.value = res.data
    taskName.value = newTaskForm.name.trim()
    selectedDatasource.value = newTaskForm.datasourceId!

    createDialogVisible.value = false
    mode.value = 'edit'
    executeResult.value = null
    planResult.value = null
    suggestions.value = []
    analysis.value = null
    analyzed.value = false

    ensureEditor()
    nextTick(() => {
      if (editor) {
        editor.setValue(defaultComment)
      }
    })
    await loadTasks()
    ElMessage.success('任务已创建')
  } catch (e: any) {
    ElMessage.error(e.message || '创建失败')
  } finally {
    creating.value = false
  }
}

async function handleSave() {
  if (!taskName.value.trim()) {
    ElMessage.warning('请输入任务名称')
    return
  }
  const sql = editor?.getValue() || ''
  if (!sql.trim()) {
    ElMessage.warning('SQL 内容不能为空')
    return
  }

  saving.value = true
  try {
    const payload = {
      name: taskName.value.trim(),
      sqlContent: sql,
      datasourceId: selectedDatasource.value || null,
    }
    if (currentTaskId.value) {
      await updateTask({ ...payload, id: currentTaskId.value })
      ElMessage.success('任务已更新')
    } else {
      const res = await createTask(payload)
      currentTaskId.value = res.data.id
      currentTask.value = res.data
      ElMessage.success('任务已创建')
    }
    await loadTasks()
    if (currentTaskId.value) {
      currentTask.value = tasks.value.find(t => t.id === currentTaskId.value) || currentTask.value
    }
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function handleDeleteTask(task: any) {
  try {
    await ElMessageBox.confirm(`确定删除任务「${task.name}」？`, '确认删除', {
      type: 'warning',
    })
    await deleteTask(task.id)
    if (currentTaskId.value === task.id) {
      handleBackToList()
    }
    await loadTasks()
    ElMessage.success('已删除')
  } catch { /* cancelled */ }
}

async function handlePublishTable(task: any) {
  try {
    await ElMessageBox.confirm(`发布任务「${task.name}」？发布后该任务可被工作流调度执行。`, '发布确认')
    await publishTask(task.id)
    ElMessage.success('任务已发布')
    await loadTasks()
  } catch { /* cancelled */ }
}

async function handleUnpublishTable(task: any) {
  try {
    await ElMessageBox.confirm(`下架任务「${task.name}」？下架后该任务将不再被工作流调度执行。`, '下架确认')
    await unpublishTask(task.id)
    ElMessage.success('任务已下架')
    await loadTasks()
  } catch { /* cancelled */ }
}

// --- Datasource ---
const datasources = ref<DatasourceConfig[]>([])
const selectedDatasource = ref<number | undefined>()
const saving = ref(false)

// --- SQL Result ---
const activeTab = ref('result')
const executing = ref(false)
const explaining = ref(false)
const analyzing = ref(false)
const formatting = ref(false)
const analyzed = ref(false)

const executeResult = ref<SqlExecuteResult | null>(null)
const planResult = ref<ExplainPlanResult | null>(null)
const suggestions = ref<SqlSuggestion[]>([])
const analysis = ref<SqlAnalysis | null>(null)

// --- Monaco Editor ---
const monacoContainer = ref<HTMLDivElement>()
let editor: monaco.editor.IStandaloneCodeEditor | null = null

onMounted(async () => {
  try {
    const res = await listDatasources()
    datasources.value = res.data
    if (res.data.length > 0) {
      selectedDatasource.value = res.data[0].id
    }
  } catch { /* ignore */ }
  await loadTasks()
})

function ensureEditor() {
  if (editor) return
  nextTick(() => {
    if (!monacoContainer.value || editor) return
    monaco.editor.defineTheme('idata', {
      base: 'vs',
      inherit: true,
      rules: [],
      colors: { 'editor.background': '#fafafa' },
    })
    editor = monaco.editor.create(monacoContainer.value, {
      value: '',
      language: 'sql',
      theme: 'idata',
      minimap: { enabled: false },
      fontSize: 14,
      lineNumbers: 'on',
      automaticLayout: true,
      scrollBeyondLastLine: false,
      wordWrap: 'on',
    })
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter, () => {
      handleExecute()
    })
    editor.addCommand(monaco.KeyMod.Alt | monaco.KeyCode.KeyP, () => {
      insertPromptTemplate()
    })
  })
}

function insertPromptTemplate() {
  if (!editor) return
  const dsType = currentTask.value?.datasourceType
  const template = generateDefaultComment(dsType || '')
  const selection = editor.getSelection()
  const range = selection ? new monaco.Range(
    selection.startLineNumber, selection.startColumn,
    selection.endLineNumber, selection.endColumn
  ) : null
  editor.executeEdits('prompt-template', [
    { range: range || new monaco.Range(1, 1, 1, 1), text: template, forceMoveMarkers: true },
  ])
}

onBeforeUnmount(() => {
  editor?.dispose()
})

function onDatasourceChange(val: number) {
  selectedDatasource.value = val
}

function getEditorSql(): string {
  if (!editor) return ''
  const selection = editor.getSelection()
  if (selection && !selection.isEmpty()) {
    return editor.getModel()?.getValueInRange(selection) || ''
  }
  return editor.getValue()
}

async function handleExecute() {
  if (!selectedDatasource.value) {
    ElMessage.warning('请先选择数据源')
    return
  }
  const sql = getEditorSql().trim()
  if (!sql) { ElMessage.warning('SQL 不能为空'); return }

  executing.value = true
  activeTab.value = 'result'
  try {
    const res = await executeSql(selectedDatasource.value, sql)
    executeResult.value = res.data
  } finally {
    executing.value = false
  }
}

async function handleExplain() {
  if (!selectedDatasource.value) {
    ElMessage.warning('请先选择数据源')
    return
  }
  const sql = getEditorSql().trim()
  if (!sql) { ElMessage.warning('SQL 不能为空'); return }

  explaining.value = true
  activeTab.value = 'plan'
  try {
    const res = await explainSql(selectedDatasource.value, sql)
    planResult.value = res.data
  } finally {
    explaining.value = false
  }
}

async function handleAnalyze() {
  const sql = getEditorSql().trim()
  if (!sql) { ElMessage.warning('SQL 不能为空'); return }

  analyzing.value = true
  activeTab.value = 'analysis'
  try {
    const res = await analyzeSql(sql)
    analysis.value = res.data.analysis
    analyzed.value = true

    if (selectedDatasource.value && res.data.analysis.valid) {
      try {
        const fullRes = await fullAnalyze(selectedDatasource.value, sql)
        suggestions.value = fullRes.data.suggestions
        planResult.value = fullRes.data.plan
      } catch { /* plan might fail, that's ok */ }
    }
  } finally {
    analyzing.value = false
  }
}

async function handleFormat() {
  const sql = getEditorSql().trim()
  if (!sql) { ElMessage.warning('SQL 不能为空'); return }

  formatting.value = true
  try {
    const res = await formatSql(sql)
    if (editor && res.data.formatted) {
      editor.setValue(res.data.formatted)
    }
    ElMessage.success('格式化完成')
  } finally {
    formatting.value = false
  }
}

function getPlanTypeTag(type: string): string {
  const good = ['const', 'eq_ref', 'ref', 'range', 'index_merge']
  const bad = ['ALL', 'index']
  if (!type) return 'info'
  if (good.includes(type)) return 'success'
  if (bad.includes(type)) return 'danger'
  return 'warning'
}

function getSuggestionTagType(type: string): string {
  switch (type?.toLowerCase()) {
    case 'error': return 'danger'
    case 'warning': return 'warning'
    case 'info': return 'info'
    default: return 'info'
  }
}

function getDsTypeTagType(type: string): string {
  switch (type?.toUpperCase()) {
    case 'MYSQL': return 'primary'
    case 'HIVE': return 'warning'
    default: return 'info'
  }
}

async function handlePublish() {
  if (!currentTaskId.value) {
    ElMessage.warning('请先保存任务')
    return
  }
  try {
    await ElMessageBox.confirm('发布后该任务可被工作流调度执行，确定发布？', '发布确认')
    await publishTask(currentTaskId.value)
    ElMessage.success('任务已发布')
    await loadTasks()
    if (currentTask.value) {
      currentTask.value.status = 'PUBLISHED'
    }
  } catch { /* cancelled */ }
}

async function handleUnpublish() {
  if (!currentTaskId.value) {
    return
  }
  try {
    await ElMessageBox.confirm('下架后该任务将不再被工作流调度执行，确定下架？', '下架确认')
    await unpublishTask(currentTaskId.value)
    ElMessage.success('任务已下架')
    await loadTasks()
    if (currentTask.value) {
      currentTask.value.status = 'DRAFT'
    }
  } catch { /* cancelled */ }
}
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.editor-view {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
}

.toolbar-card {
  flex-shrink: 0;
}

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

.editor-card {
  flex: 1;
  min-height: 150px;
}

.monaco-container {
  width: 100%;
  height: 200px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
}

.result-card {
  flex: 1;
  min-height: 150px;
  overflow: auto;
}

.result-tabs {
  height: 100%;
}

.result-meta {
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
  padding: 4px 0;
}

.highlight-key {
  color: #67c23a;
  font-weight: bold;
}

.no-key {
  color: #c0c4cc;
}

.suggestion-list {
  max-height: 300px;
  overflow-y: auto;
}

.suggestion-card {
  border-left: 4px solid #909399;
}

.suggestion-warning {
  border-left-color: #e6a23c;
}

.suggestion-error {
  border-left-color: #f56c6c;
}

.suggestion-info {
  border-left-color: #409eff;
}

.suggestion-header {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}

.suggestion-detail {
  margin: 4px 0;
  color: #606266;
  font-size: 13px;
}

.suggestion-advice {
  margin: 4px 0;
  color: #409eff;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.analysis-info {
  max-height: 300px;
  overflow-y: auto;
}

.status-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 6px;
  vertical-align: middle;
}
.dot-on {
  background-color: #67c23a;
  box-shadow: 0 0 4px #67c23a;
}
.dot-off {
  background-color: #f56c6c;
  box-shadow: 0 0 4px #f56c6c;
}
.dot-na {
  background-color: #c0c4cc;
}
</style>
