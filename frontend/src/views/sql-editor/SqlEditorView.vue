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
    <div v-else :class="['editor-view', { 'editor-fullscreen': editorFullscreen }]">
      <!-- Toolbar -->
      <el-card shadow="hover" class="toolbar-card">
        <div class="toolbar">
          <div class="toolbar-left">
            <el-button @click="handleBackToList()">
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
            <el-button @click="toggleEditorFullscreen">
              <el-icon><template v-if="editorFullscreen"><Close /></template><template v-else><FullScreen /></template></el-icon>
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
            <div v-if="allDisplayStatements.length > 0">
              <!-- Result Switcher Tabs -->
              <div class="result-switcher">
                <div
                  v-for="(item, rIdx) in allDisplayStatements"
                  :key="item.id"
                  class="switch-tab"
                  :class="{ active: selectedStatement === item, error: !!item.result.errorMessage, pinned: isPinned(item) }"
                  @click="selectedStatement = item"
                >
                  <span class="switch-tab__dot" :class="item.result.errorMessage ? 'dot-error' : 'dot-ok'" />
                  结果 {{ rIdx + 1 }}
                  <span class="switch-tab__meta">{{ item.result.elapsedMs }}ms</span>
                  <span class="switch-tab__pin" :class="{ pinned: isPinned(item) }" @click.stop="togglePin(item)">{{ isPinned(item) ? '已置顶' : '置顶' }}</span>
                </div>
              </div>

              <!-- Selected Result Card -->
              <div v-for="(item, rIdx) in allDisplayStatements" :key="item.id" v-show="selectedStatement === item" class="stmt-result-block" :class="{ 'pinned-border': isPinned(item) }">
                <div class="stmt-result-block__header">
                  <div class="stmt-result-block__header-left">
                    <el-tag
                      size="small"
                      :type="item.result.errorMessage ? 'danger' : 'success'"
                      effect="light"
                    >
                      结果 {{ rIdx + 1 }}
                    </el-tag>
                    <span class="stmt-elapsed" v-if="item.result.elapsedMs">{{ item.result.elapsedMs }}ms</span>
                  </div>
                  <div class="stmt-result-block__header-right">
                    <span v-if="item.result.affectedRows >= 0" class="stmt-rows">{{ item.result.affectedRows }} 行</span>
                    <el-button
                      size="small"
                      text
                      :type="isPinned(item) ? 'warning' : 'default'"
                      @click="togglePin(item)"
                    >
                      {{ isPinned(item) ? '取消置顶' : '置顶' }}
                    </el-button>
                  </div>
                </div>
                <div class="stmt-sql">
                  <code class="stmt-sql__text">{{ item.sql }}</code>
                  <code v-if="item.resolvedSql && item.resolvedSql !== item.sql" class="stmt-sql__resolved">→ {{ item.resolvedSql }}</code>
                </div>
                <div class="stmt-result">
                  <el-table
                    v-if="item.result.columns && item.result.columns.length > 0"
                    :data="item.result.rows"
                    border
                    stripe
                    max-height="300"
                    size="small"
                    style="width: 100%"
                  >
                    <el-table-column
                      v-for="col in item.result.columns"
                      :key="col"
                      :prop="col"
                      :label="col"
                      min-width="120"
                    />
                  </el-table>
                  <el-empty v-else-if="item.result.affectedRows >= 0" description="执行成功，无返回数据" />
                  <el-alert v-if="item.result.errorMessage" type="error" :description="item.result.errorMessage" show-icon />
                </div>
              </div>
            </div>
            <el-empty v-else description="运行 SQL 查看结果" />
          </el-tab-pane>

          <el-tab-pane label="执行日志" name="log">
            <div v-if="execLog.length > 0" class="exec-log">
              <div v-for="(entry, i) in execLog" :key="i" :class="['log-line', 'log-' + entry.type]">
                <span class="log-time">{{ entry.time }}</span>
                <span class="log-msg">{{ entry.message }}</span>
              </div>
            </div>
            <el-empty v-else description="暂无执行日志" />
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
        <el-form-item label="任务描述" prop="description">
          <el-input v-model="newTaskForm.description" placeholder="请输入任务描述" maxlength="200" type="textarea" :rows="2" />
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
import { ref, reactive, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import Layout from '@/components/common/Layout.vue'
import { listDatasources, listDatasourceTables, listDatasourceDatabases } from '@/api/datasource'
import { executeSql, explainSql, analyzeSql, fullAnalyze } from '@/api/sql'
import { listTasks, getTask, createTask, updateTask, deleteTask, publishTask, unpublishTask } from '@/api/sql-task'
import { getSqlKeywords, detectGrammarContext, getCachedGrammarContext, setCachedGrammarContext } from '@/api/grammar'
import type { SqlKeywords } from '@/api/grammar'
import type { DatasourceConfig } from '@/types'
import type { SqlTaskRequest } from '@/api/sql-task'
import type { SqlExecuteResult, ExplainPlanResult, SqlAnalysis, SqlSuggestion } from '@/api/sql'
import { resolveParams } from '@/api/parameter'
import * as monaco from 'monaco-editor'
import { format as formatSql } from 'sql-formatter'

interface ExecutedStatement {
  id: number        // unique auto-increment ID for stable DOM key
  sql: string       // original SQL before param resolution
  resolvedSql: string  // actual SQL sent to database
  result: SqlExecuteResult
}
let nextStmtId = 1

interface ExecLogEntry {
  time: string
  message: string
  type: 'info' | 'success' | 'error'
}

// --- Mode ---
const mode = ref<'list' | 'edit'>('list')

async function handleBackToList(skipSave = false) {
  if (!skipSave && currentTaskId.value) {
    const sql = editor?.getValue() || ''
    const name = taskName.value.trim()
    if (name) {
      saving.value = true
      try {
        const updatePayload: Record<string, any> = { id: currentTaskId.value, name, sqlContent: sql, datasourceId: selectedDatasource.value || null }
        if (currentTask.value?.description) updatePayload.description = currentTask.value.description
        await updateTask(updatePayload as SqlTaskRequest)
        dirty.value = false
        await loadTasks()
        ElMessage.success('任务已自动保存')
      } catch { /* auto-save failure is non-critical */ }
      finally { saving.value = false }
    }
  }
  stopAutoSave()
  contentChangeDisposable?.dispose()
  contentChangeDisposable = null
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
  startAutoSave()
  nextTick(() => loadTaskDetail(task.id))
}

async function loadTaskDetail(id: number) {
  try {
    const res = await getTask(id)
    const detail = res.data
    currentTask.value = detail
    if (editor) {
      editor.setValue(detail.sqlContent || '')
      dirty.value = false
    }
    if (detail.datasourceId) {
      selectedDatasource.value = detail.datasourceId
      await loadDatabases(detail.datasourceId)
      await loadAllTables(detail.datasourceId)
      const ds = datasources.value.find(d => d.id === detail.datasourceId)
      if (ds) {
        loadKeywords(ds.type)
      }
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
  description: '',
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
  newTaskForm.description = ''
  newTaskForm.dbType = ''
  newTaskForm.datasourceId = undefined
}

function generateDefaultComment(dsType: string, taskName: string = ''): string {
  const now = new Date()
  const y = now.getFullYear()
  const m = String(now.getMonth() + 1).padStart(2, '0')
  const d = String(now.getDate()).padStart(2, '0')
  const dateStr = `${y}${m}${d}`
  const typeLabel = dsType === 'HIVE' ? 'Hive' : 'MySQL'
  return [
    `-- ${typeLabel} SQL`,
    '-- **********************************************',
    `-- 所属主题: ${taskName}`,
    '-- 描述: ',
    '-- 创建者 : ',
    `-- 创建日期: ${dateStr}`,
    '-- 修改日志:',
    '-- 修改日期 修改人 修改内容',
    '-- yyyymmdd name comment',
    '-- **********************************************',
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
    const defaultComment = generateDefaultComment(newTaskForm.dbType, newTaskForm.name.trim())
    const payload = {
      name: newTaskForm.name.trim(),
      description: newTaskForm.description.trim() || undefined,
      sqlContent: defaultComment,
      datasourceId: newTaskForm.datasourceId!,
    }
    const res = await createTask(payload)
    currentTaskId.value = res.data.id
    currentTask.value = res.data
    taskName.value = newTaskForm.name.trim()
    selectedDatasource.value = newTaskForm.datasourceId!
    await loadDatabases(newTaskForm.datasourceId!)
      await loadAllTables(newTaskForm.datasourceId!)
    const ds = datasources.value.find(d => d.id === newTaskForm.datasourceId!)
    if (ds) {
      loadKeywords(ds.type)
    }

    createDialogVisible.value = false
    mode.value = 'edit'
    startAutoSave()
    executedStatements.value = []
    execLog.value = []
    planResult.value = null
    suggestions.value = []
    analysis.value = null
    analyzed.value = false

    ensureEditor()
    nextTick(() => {
      if (editor) {
        editor.setValue(defaultComment)
        dirty.value = false
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

async function handleSave(silent = false) {
  if (!taskName.value.trim()) return
  const sql = editor?.getValue() || ''

  saving.value = true
  try {
    const payload: Record<string, any> = {
      name: taskName.value.trim(),
      sqlContent: sql,
      datasourceId: selectedDatasource.value || null,
    }
    if (currentTask.value?.description) {
      payload.description = currentTask.value.description
    }
    if (currentTaskId.value) {
      await updateTask({ ...payload, id: currentTaskId.value } as SqlTaskRequest)
      if (!silent) ElMessage.success('任务已更新')
    } else {
      const res = await createTask(payload as SqlTaskRequest)
      currentTaskId.value = res.data.id
      currentTask.value = res.data
      if (!silent) ElMessage.success('任务已创建')
    }
    dirty.value = false
    await loadTasks()
    if (currentTaskId.value) {
      currentTask.value = tasks.value.find(t => t.id === currentTaskId.value) || currentTask.value
    }
  } catch (e: any) {
    if (!silent) ElMessage.error(e.message || '保存失败')
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
      handleBackToList(true)
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
let autoSaveTimer: ReturnType<typeof setInterval> | null = null
const dirty = ref(false)
let contentChangeDisposable: monaco.IDisposable | null = null

function startAutoSave() {
  stopAutoSave()
  dirty.value = false
  autoSaveTimer = setInterval(tryAutoSave, 30000)
}

function stopAutoSave() {
  if (autoSaveTimer) { clearInterval(autoSaveTimer); autoSaveTimer = null }
}

async function tryAutoSave() {
  if (mode.value !== 'edit' || !currentTaskId.value || !dirty.value) return
  await handleSave(true)
}

watch(taskName, () => { dirty.value = true })

// --- SQL Result ---
const activeTab = ref('result')
const executing = ref(false)
const explaining = ref(false)
const analyzing = ref(false)
const formatting = ref(false)
const analyzed = ref(false)

const executedStatements = ref<ExecutedStatement[]>([])
const selectedStatement = ref<ExecutedStatement | null>(null)
const pinnedStatements = ref<ExecutedStatement[]>([])

function isPinned(item: ExecutedStatement): boolean {
  return pinnedStatements.value.includes(item)
}
function togglePin(item: ExecutedStatement) {
  const idx = pinnedStatements.value.indexOf(item)
  if (idx >= 0) {
    pinnedStatements.value.splice(idx, 1)
  } else {
    pinnedStatements.value.push(item)
  }
}

const allDisplayStatements = computed(() => {
  const pinned = pinnedStatements.value
  const pinnedSet = new Set(pinned)
  // Pinned results first (preserving order they were pinned), then current unpinned results
  return [...pinned, ...executedStatements.value.filter(s => !pinnedSet.has(s))]
})

// After execution, select first unpinned result (pinned stays from before)
watch(executedStatements, (stmts) => {
  if (stmts.length > 0) {
    const firstUnpinned = stmts.find(s => !isPinned(s))
    if (firstUnpinned) selectedStatement.value = firstUnpinned
  }
})
const execLog = ref<ExecLogEntry[]>([])

function addExecLog(message: string, type: ExecLogEntry['type'] = 'info') {
  const d = new Date()
  const time = `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}:${String(d.getSeconds()).padStart(2, '0')}`
  execLog.value.push({ time, message, type })
}
function clearExecLog() { execLog.value = [] }

const planResult = ref<ExplainPlanResult | null>(null)
const suggestions = ref<SqlSuggestion[]>([])
const analysis = ref<SqlAnalysis | null>(null)

// --- Fullscreen ---
const editorFullscreen = ref(false)

function toggleEditorFullscreen() {
  editorFullscreen.value = !editorFullscreen.value
  // Layout recalc needed after the container changes size
  nextTick(() => editor?.layout())
}

// --- Monaco Editor ---
const monacoContainer = ref<HTMLDivElement>()
let editor: monaco.editor.IStandaloneCodeEditor | null = null
let keywordCompletionDisposable: monaco.IDisposable | null = null
const keywords = ref<SqlKeywords | null>(null)
const tables = ref<{ tableName: string; schema: string }[]>([])
const databases = ref<string[]>([])
const tablesByDb = ref<Record<string, { tableName: string; schema: string }[]>>({})

async function loadKeywords(dbType: string = 'MYSQL') {
  try {
    const res = await getSqlKeywords(dbType)
    keywords.value = res.data
    registerKeywordCompletion()
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
    const raw = res.data || []
    const mapped = raw.map((t: any) => ({
      tableName: t.tableName || t.TABLE_NAME || '',
      schema: t.tableSchema || t.TABLE_SCHEMA || (database || ''),
    })).filter(t => t.tableName)
    if (database) {
      tablesByDb.value[database.toUpperCase()] = mapped
    } else {
      tables.value = mapped
    }
  } catch {
    if (database) {
      tablesByDb.value[database.toUpperCase()] = []
    } else {
      tables.value = []
    }
  }
}

async function loadAllTables(datasourceId: number) {
  // Load default database tables first
  await loadTables(datasourceId)
  // Then pre-load tables for all known databases in parallel
  if (databases.value.length > 0) {
    await Promise.all(databases.value.map(db => loadTables(datasourceId, db)))
  }
}

let grammarTimer: ReturnType<typeof setTimeout> | null = null

function updateGrammarContext(sql: string, pos: number) {
  if (grammarTimer) clearTimeout(grammarTimer)
  grammarTimer = setTimeout(async () => {
    try {
      const res = await detectGrammarContext(sql, pos)
      setCachedGrammarContext(res.data)
    } catch { /* ignore */ }
  }, 300)
}

function registerKeywordCompletion() {
  keywordCompletionDisposable?.dispose()
  if (!keywords.value) return
  keywordCompletionDisposable = monaco.languages.registerCompletionItemProvider('sql', {
    triggerCharacters: ['.', ' '],
    provideCompletionItems: (model, position) => {
      const ctx = getCachedGrammarContext()
      const result: any[] = []
      const ks = monaco.languages.CompletionItemKind

      // --- Dot prefix detection from model text ---
      // When grammar context hasn't been updated yet (e.g. just typed '.'),
      // infer the dot prefix from the text before cursor.
      let effectivePrefix = ctx?.dotPrefix
      let effectivePrefixType = ctx?.dotPrefixType
      if (!effectivePrefix) {
        const lineContent = model.getLineContent(position.lineNumber)
        const textBefore = lineContent.substring(0, position.column - 1)
        const dotMatch = textBefore.match(/(\w+)\.\s*$/)
        if (dotMatch) {
          effectivePrefix = dotMatch[1].toUpperCase()
          effectivePrefixType = ctx?.expectsTable ? 'DATABASE' : undefined
          // If ctx not available (debounce not yet fired), infer DATABASE type from known databases
          if (!effectivePrefixType && databases.value.some(d => d.toUpperCase() === effectivePrefix)) {
            effectivePrefixType = 'DATABASE'
          }
        }
      }

      // --- Database name suggestions ---
      // When ctx is not yet loaded, default to showing all databases
      if (!effectivePrefix && databases.value.length > 0) {
        if (!ctx || ctx.expectsDatabase) {
          result.push(...databases.value.map(db => ({
            label: db,
            kind: ks.Module,
            insertText: db + '.',
            detail: '数据库',
            sortText: 'b' + db,
          })))
        }
      }

      // --- Table name suggestions ---
      // When ctx is not yet loaded, default to showing all tables
      if ((!ctx || ctx.expectsTable) && tables.value.length > 0) {
        if (effectivePrefix && effectivePrefixType === 'DATABASE') {
          const prefix = effectivePrefix
          // Use pre-loaded per-database tables if available, fall back to filtering the default list
          const perDb = tablesByDb.value[prefix]
          if (perDb && perDb.length > 0) {
            result.push(...perDb.map(t => ({
              label: t.tableName,
              kind: ks.Class,
              insertText: t.tableName,
              detail: '表名 (' + t.schema + ')',
              sortText: 'a' + t.tableName,
            })))
          } else {
            const filtered = tables.value.filter(t => t.schema.toUpperCase() === prefix)
            result.push(...filtered.map(t => ({
              label: t.tableName,
              kind: ks.Class,
              insertText: t.tableName,
              detail: '表名 (' + t.schema + ')',
              sortText: 'a' + t.tableName,
            })))
          }
        } else {
          result.push(...tables.value.map(t => ({
            label: t.tableName,
            kind: ks.Class,
            insertText: t.tableName,
            detail: '表名',
            sortText: 'a' + t.tableName,
          })))
        }
      }

      // --- Keyword suggestions (filtered by context) ---
      if (keywords.value) {
        const kw = keywords.value
        const allSuggestions = [
          ...kw.statements.map(k => ({ label: k, kind: ks.Keyword, insertText: k, detail: 'SQL 语句', sortText: 'z' + k })),
          ...kw.functions.map(k => ({ label: k, kind: ks.Function, insertText: k, detail: '内置函数', sortText: 'z' + k })),
          ...kw.types.map(k => ({ label: k, kind: ks.TypeParameter, insertText: k, detail: '数据类型', sortText: 'z' + k })),
          ...kw.clauses.map(k => ({ label: k, kind: ks.Keyword, insertText: k, detail: 'SQL 子句', sortText: 'z' + k })),
        ]
        if (!ctx) {
          result.push(...allSuggestions)
        } else {
          const validList = ctx.validKeywords.map(k => k.toUpperCase())
          // 构建优先级索引：validKeywords 中越靠前优先级越高
          const priorityIndex: Record<string, string> = {}
          validList.forEach((k, i) => {
            priorityIndex[k] = String(i).padStart(3, '0')
          })
          const expectFunction = ctx.expectsFunction
          result.push(...allSuggestions
            .filter(s => {
              const label = (s.label as string).toUpperCase()
              if (s.detail === '内置函数' || label.endsWith('()')) return expectFunction
              if (s.detail === '数据类型') return true
              if (s.detail === 'SQL 语句' || s.detail === 'SQL 子句') {
                if (validList.length === 0) return true
                return validList.some(v => label.includes(v) || v.includes(label))
              }
              return true
            })
            .map(s => {
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

      return { suggestions: result }
    },
  })
}

// --- Monaco Editor ---

onMounted(async () => {
  try {
    const res = await listDatasources()
    datasources.value = res.data
    if (res.data.length > 0) {
      selectedDatasource.value = res.data[0].id
      await loadDatabases(res.data[0].id)
      await loadAllTables(res.data[0].id)
      const ds = res.data[0]
      await loadKeywords(ds.type)
    } else {
      await loadKeywords('MYSQL')
    }
  } catch {
    await loadKeywords('MYSQL')
  }
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
      quickSuggestions: true,
      suggestOnTriggerCharacters: true,
    })
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter, () => {
      handleExecute()
    })
    contentChangeDisposable = editor.onDidChangeModelContent(() => {
      dirty.value = true
    })
    editor.onDidChangeCursorPosition(() => {
      const model = editor?.getModel()
      if (!model) return
      const sql = model.getValue()
      if (!sql.trim()) return
      const offset = model.getOffsetAt(editor!.getPosition()!)
      updateGrammarContext(sql, offset)
    })
  })
}

onBeforeUnmount(async () => {
  if (currentTaskId.value) {
    const sql = editor?.getValue() || ''
    const name = taskName.value.trim()
    if (name) {
      const unmountPayload: Record<string, any> = { id: currentTaskId.value, name, sqlContent: sql, datasourceId: selectedDatasource.value || null }
      if (currentTask.value?.description) unmountPayload.description = currentTask.value.description
      try { await updateTask(unmountPayload as SqlTaskRequest) } catch { /* ignore */ }
    }
  }
  stopAutoSave()
  if (grammarTimer) clearTimeout(grammarTimer)
  keywordCompletionDisposable?.dispose()
  contentChangeDisposable?.dispose()
  editor?.dispose()
})

async function onDatasourceChange(val: number) {
  selectedDatasource.value = val
  await loadDatabases(val)
  await loadAllTables(val)
  const ds = datasources.value.find(d => d.id === val)
  if (ds) {
    loadKeywords(ds.type)
  }
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
  clearExecLog()

  // Split original SQL by ; first
  const origStatements = sql.split(';').map(s => s.trim()).filter(s => s.length > 0)
  addExecLog(`开始执行 ${origStatements.length} 条语句`, 'info')

  const stmts: ExecutedStatement[] = []
  for (let i = 0; i < origStatements.length; i++) {
    const origStmt = origStatements[i]
    addExecLog(`── 语句 ${i + 1} ──────────────────────────────`, 'info')
    addExecLog(`原始: ${origStmt}`, 'info')

    // Resolve parameters per statement
    let resolvedStmt: string
    let resolvedParams: Record<string, string> = {}
    try {
      const res = await resolveParams(origStmt)
      resolvedStmt = res.data.resolvedSql ?? origStmt
      resolvedParams = res.data.resolvedParams || {}
    } catch {
      resolvedStmt = origStmt
    }

    if (resolvedStmt !== origStmt) {
      addExecLog(`解析: ${resolvedStmt}`, 'info')
      for (const [name, value] of Object.entries(resolvedParams)) {
        addExecLog(`  参数 ${name} → ${value}`, 'info')
      }
    }

    const label = resolvedStmt.length > 80 ? resolvedStmt.slice(0, 80) + '...' : resolvedStmt
    try {
      addExecLog(`执行: ${label}`, 'info')
      const res = await executeSql(selectedDatasource.value, resolvedStmt)
      const result = res.data
      stmts.push({ id: nextStmtId++, sql: origStmt, resolvedSql: resolvedStmt, result })
      if (result.errorMessage) {
        addExecLog(`失败: ${result.errorMessage}`, 'error')
      } else {
        const parts: string[] = []
        if (result.elapsedMs) parts.push(`耗时 ${result.elapsedMs}ms`)
        if (result.affectedRows >= 0) parts.push(`影响 ${result.affectedRows} 行`)
        if (result.columns?.length) parts.push(`返回 ${result.columns.length} 列 × ${result.rows?.length || 0} 行`)
        addExecLog(`成功: ${parts.join('，')}`, 'success')
      }
    } catch (e: any) {
      const errMsg = e.message || '执行失败'
      stmts.push({ id: nextStmtId++, sql: origStmt, resolvedSql: resolvedStmt, result: { columns: [], rows: [], affectedRows: -1, elapsedMs: 0, errorMessage: errMsg } })
      addExecLog(`失败: ${errMsg}`, 'error')
    }
  }
  executedStatements.value = stmts
  const ok = stmts.filter(s => !s.result.errorMessage).length
  addExecLog(`执行完毕，${ok}/${stmts.length} 条成功`, stmts.some(s => s.result.errorMessage) ? 'error' : 'success')
  executing.value = false
}

async function handleExplain() {
  if (!selectedDatasource.value) {
    ElMessage.warning('请先选择数据源')
    return
  }
  const sql = getEditorSql().trim()
  if (!sql) { ElMessage.warning('SQL 不能为空'); return }

  // Resolve parameters before explain
  let stmtToExplain = sql
  try {
    const res = await resolveParams(sql)
    if (res.data.resolvedSql) stmtToExplain = res.data.resolvedSql
  } catch { /* use original */ }

  explaining.value = true
  activeTab.value = 'plan'
  try {
    const res = await explainSql(selectedDatasource.value, stmtToExplain)
    planResult.value = res.data
  } finally {
    explaining.value = false
  }
}

async function handleAnalyze() {
  const sql = getEditorSql().trim()
  if (!sql) { ElMessage.warning('SQL 不能为空'); return }

  // 多条 SQL 只分析第一条
  const firstStmt = sql.split(';').map(s => s.trim()).find(s => s.length > 0)
  if (!firstStmt) { ElMessage.warning('SQL 不能为空'); return }

  // Resolve parameters before analysis
  let stmtToAnalyze = firstStmt
  try {
    const res = await resolveParams(firstStmt)
    if (res.data.resolvedSql) stmtToAnalyze = res.data.resolvedSql
  } catch { /* use original */ }

  analyzing.value = true
  activeTab.value = 'analysis'
  try {
    const res = await analyzeSql(stmtToAnalyze)
    analysis.value = res.data.analysis
    analyzed.value = true

    if (selectedDatasource.value && res.data.analysis.valid) {
      try {
        const fullRes = await fullAnalyze(selectedDatasource.value, stmtToAnalyze)
        suggestions.value = fullRes.data.suggestions
        planResult.value = fullRes.data.plan
      } catch { /* plan might fail, that's ok */ }
    }
  } finally {
    analyzing.value = false
  }
}

function handleFormat() {
  const sql = getEditorSql().trim()
  if (!sql) { ElMessage.warning('SQL 不能为空'); return }

  try {
    const ds = datasources.value.find(d => d.id === selectedDatasource.value)
    const language = ds?.type === 'HIVE' ? 'hive' : 'mysql'
    const formatted = formatSql(sql, {
      language,
      keywordCase: 'upper',
      tabWidth: 2,
      linesBetweenQueries: 2,
    })
    if (editor) {
      editor.setValue(formatted)
    }
    ElMessage.success('格式化完成')
  } catch (e: any) {
    ElMessage.error('格式化失败: ' + (e.message || '未知错误'))
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
  min-height: 200px;
  height: 55vh;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
}

.editor-fullscreen {
  position: fixed !important;
  inset: 0 !important;
  z-index: 1000 !important;
  background: #fff;
  padding: 12px;
  height: 100vh !important;
}

.editor-fullscreen .result-card {
  flex: 0 0 auto;
  max-height: 35vh;
  overflow: auto;
}

.editor-fullscreen .monaco-container {
  height: 0;
  flex: 1;
  min-height: 0;
}

.editor-fullscreen .editor-card {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.editor-fullscreen .editor-card :deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 12px;
}

.result-card {
  flex: 1;
  min-height: 150px;
  overflow: auto;
}

.result-tabs {
  height: 100%;
}
.result-tabs :deep(.el-tabs__content) {
  overflow-y: auto;
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

/* Executed statement block: SQL + result pair */
.stmt-result-block {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  overflow: hidden;
}
.stmt-result-block.pinned-border {
  border-color: #e6a23c;
  box-shadow: 0 0 0 1px #e6a23c;
}
.stmt-result-block__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 12px;
  background: #f5f7fa;
  border-bottom: 1px solid #ebeef5;
}
.stmt-result-block__header-left {
  display: flex;
  align-items: center;
  gap: 6px;
}
.stmt-result-block__header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.pinned-label {
  font-size: 12px;
  color: #e6a23c;
  font-weight: 600;
}
.stmt-elapsed {
  font-size: 11px;
  color: #c0c4cc;
}
.stmt-rows {
  font-size: 12px;
  color: #909399;
}

/* Result switcher tabs */
.result-switcher {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #e4e7ed;
}
.switch-tab {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
  color: #606266;
  background: #fff;
  transition: all 0.15s;
}
.switch-tab.pinned {
  border-color: #e6a23c;
  background: #fdf6ec;
}
.switch-tab__pin {
  font-size: 11px;
  color: #c0c4cc;
  cursor: pointer;
  padding: 0 2px;
  user-select: none;
}
.switch-tab__pin:hover { color: #e6a23c; }
.switch-tab__pin.pinned { color: #e6a23c; font-weight: 600; }
.switch-tab:hover {
  border-color: #409eff;
  color: #409eff;
}
.switch-tab.active {
  border-color: #409eff;
  color: #409eff;
  background: #ecf5ff;
}
.switch-tab.error {
  border-color: #f56c6c;
  color: #f56c6c;
}
.switch-tab.error.active {
  background: #fef0f0;
  border-color: #f56c6c;
  color: #f56c6c;
}
.switch-tab__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}
.dot-ok { background: #67c23a; }
.dot-error { background: #f56c6c; }
.switch-tab__meta {
  color: #c0c4cc;
  font-size: 11px;
  margin-left: 2px;
}
.stmt-sql {
  display: flex;
  flex-direction: column;
  gap: 4px;
  background: #f5f7fa;
  padding: 8px 12px;
  border-bottom: 1px solid #ebeef5;
}
.stmt-sql__badge {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #409eff;
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  margin-top: 1px;
}
.stmt-sql__text {
  font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
  font-size: 12px;
  color: #303133;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
}
.stmt-sql__resolved {
  display: block;
  font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
  font-size: 12px;
  color: #67c23a;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
  margin-top: 4px;
  padding: 4px 8px;
  background: #f0f9eb;
  border-radius: 3px;
}
.stmt-result {
  padding: 8px 12px;
}

/* Execution log */
.exec-log {
  font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.7;
}
.log-line {
  display: flex;
  gap: 8px;
  padding: 2px 0;
  border-bottom: 1px solid #f2f2f2;
}
.log-time {
  color: #c0c4cc;
  flex-shrink: 0;
}
.log-msg {
  flex: 1;
}
.log-info .log-msg { color: #606266; }
.log-success .log-msg { color: #67c23a; }
.log-error .log-msg { color: #f56c6c; }
</style>
