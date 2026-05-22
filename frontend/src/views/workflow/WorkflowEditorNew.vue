<template>
  <Layout>
    <div class="workflow-editor">
      <!-- ============ Toolbar ============ -->
      <div class="toolbar">
        <div class="toolbar-left">
          <el-button text @click="goBack">
            <el-icon><ArrowLeft /></el-icon> 返回
          </el-button>
          <div class="workflow-name-section">
            <el-input
              v-model="workflowName"
              placeholder="工作流名称"
              class="name-input"
              :disabled="isPublished"
            />
            <el-tag v-if="isPublished" type="success" effect="light" size="small">已发布</el-tag>
            <el-tag v-else type="info" effect="light" size="small">草稿</el-tag>
          </div>
        </div>
        <div class="toolbar-right">
          <span v-if="store.isDirty" class="unsaved-badge">未保存</span>
          <el-button @click="handleSave" type="primary" :loading="saving" :disabled="isPublished">
            <el-icon><Check /></el-icon> 保存
          </el-button>
          <el-button @click="handleRun" type="success" :disabled="!canRun">
            <el-icon><CaretRight /></el-icon> 运行
          </el-button>
        </div>
      </div>

      <!-- ============ Error Fallback ============ -->
      <el-alert
        v-if="flowError"
        title="DAG 编辑器加载失败"
        type="error"
        description="Vue Flow 渲染异常，请刷新页面重试。"
        show-icon
        :closable="false"
        class="flow-error-alert"
      />

      <!-- ============ Compat Dialog ============ -->
      <el-dialog v-model="showCompatDialog" title="旧格式工作流" width="500px" :close-on-click-modal="false" :close-on-press-escape="false">
        <el-alert type="warning" show-icon :description="'当前工作流「' + workflowName + '」是旧版本格式，已不兼容新编辑器。请重新配置。'" />
        <template #footer>
          <el-button @click="goBack">取消查看</el-button>
          <el-button type="primary" @click="showCompatDialog = false; compatResetDone = true">重新配置</el-button>
        </template>
      </el-dialog>

      <!-- ============ Editor Body ============ -->
      <div v-if="!flowError" class="editor-body">
        <!-- ======= Left: Node Templates ======= -->
        <div class="node-panel">
          <h3 class="panel-title">节点模板</h3>
          <p class="panel-hint">拖拽到画布</p>
          <div
            v-for="tpl in nodeTemplates"
            :key="tpl.label"
            class="node-template-card"
            :class="tpl.nodeType"
            draggable="true"
            @dragstart="onDragStart($event, tpl)"
          >
            <el-icon :size="18">
              <component :is="tpl.icon" />
            </el-icon>
            <span>{{ tpl.label }}</span>
          </div>
        </div>

        <!-- ======= Center: Vue Flow Canvas ======= -->
        <div
          class="canvas-wrapper"
          ref="canvasWrapperRef"
          @drop.prevent="onCanvasDrop"
          @dragover.prevent
        >
          <VueFlow
            v-model:nodes="flowNodes"
            v-model:edges="flowEdges"
            :node-types="nodeTypes"
            :default-edge-options="defaultEdgeOptions"
            :default-viewport="{ x: 0, y: 0, zoom: 1 }"
            :min-zoom="0.1"
            :max-zoom="4"
            fit-view-on-init
            class="vue-flow-instance"
            @node-click="onNodeClick"
            @pane-click="onPaneClick"
            @connect="onConnect"
          >
            <Background :gap="20" :size="1" />
            <Controls show-interactive-fields="false" />
          </VueFlow>
        </div>

        <!-- ======= Right: Config Panel ======= -->
        <div class="property-panel">
          <div v-show="configPanel === 'empty'">
            <el-empty description="选择节点以配置属性" :image-size="80" />
          </div>
          <div v-show="configPanel === 'sql_task'">
            <div class="panel-header">
              <h3 class="panel-title">SQL 任务配置</h3>
            </div>

            <el-form label-position="top" size="small">
              <el-form-item label="节点名称">
                <el-input
                  :model-value="selectedNode?.label"
                  @update:model-value="onNodeLabelChange"
                  placeholder="请输入节点名称"
                />
              </el-form-item>

              <el-form-item label="选择 SQL 任务">
                <el-select
                  :model-value="sqlTaskConfig?.sqlTaskId"
                  @update:model-value="onSqlTaskChange"
                  placeholder="选择已发布的 SQL 任务"
                  filterable
                  clearable
                  style="width: 100%"
                >
                  <el-option
                    v-for="task in publishedSqlTasks"
                    :key="task.id"
                    :label="task.name"
                    :value="task.id"
                  >
                    <span>{{ task.name }}</span>
                    <el-tag v-if="task.sqlType" size="small" style="margin-left: 6px">{{ task.sqlType }}</el-tag>
                  </el-option>
                </el-select>
              </el-form-item>

              <template v-if="selectedSqlTask">
                <el-form-item label="数据源">
                  <span class="config-value">{{ selectedSqlTask.datasourceId ? 'ID: ' + selectedSqlTask.datasourceId : '未指定' }}</span>
                </el-form-item>
                <el-form-item label="SQL 内容预览">
                  <pre class="sql-preview">{{ selectedSqlTask.sqlContent?.slice(0, 500) }}</pre>
                </el-form-item>
              </template>
            </el-form>

            <el-divider />
            <el-button type="danger" size="small" @click="deleteSelectedNode">
              <el-icon><Delete /></el-icon> 删除节点
            </el-button>
          </div>
          <div v-show="configPanel === 'datax'">
            <div class="panel-header">
              <h3 class="panel-title">ETL 任务配置</h3>
            </div>

            <el-form label-position="top" size="small">
              <el-form-item label="节点名称">
                <el-input
                  :model-value="selectedNode?.label"
                  @update:model-value="onNodeLabelChange"
                  placeholder="请输入节点名称"
                />
              </el-form-item>

              <el-form-item label="选择 ETL 任务">
                <el-select
                  :model-value="dataxConfig?.dataxTaskId"
                  @update:model-value="onDataxTaskChange"
                  placeholder="选择已发布的 ETL 任务"
                  filterable
                  clearable
                  style="width: 100%"
                >
                  <el-option
                    v-for="task in publishedDataxTasks"
                    :key="task.id"
                    :label="task.name"
                    :value="task.id"
                  >
                    <span>{{ task.name }}</span>
                    <el-tag v-if="task.configMode" size="small" style="margin-left: 6px">{{ task.configMode === 'SCRIPT' ? '脚本' : 'UI' }}</el-tag>
                  </el-option>
                </el-select>
              </el-form-item>

              <template v-if="selectedDataxTask">
                <el-form-item label="读取端">
                  <span class="config-value">{{ formatDataxEndpoint(selectedDataxTask.readerDatasourceId, selectedDataxTask.readerDatabase, selectedDataxTask.readerTable) }}</span>
                </el-form-item>
                <el-form-item label="写入端">
                  <span class="config-value">{{ formatDataxEndpoint(selectedDataxTask.writerDatasourceId, selectedDataxTask.writerDatabase, selectedDataxTask.writerTable) }}</span>
                </el-form-item>
                <el-form-item label="写入模式">
                  <span class="config-value">{{ selectedDataxTask.writeMode === 'overwrite' ? '覆盖' : '追加' }}</span>
                </el-form-item>
              </template>
            </el-form>

            <el-divider />
            <el-button type="danger" size="small" @click="deleteSelectedNode">
              <el-icon><Delete /></el-icon> 删除节点
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </Layout>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted, onErrorCaptured, watch, markRaw } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import Layout from '@/components/common/Layout.vue'
import { useWorkflowStore, type DataxConfig, type SqlTaskConfig } from '@/stores/workflow'
import { listTasks, type SqlTask } from '@/api/sql-task'
import { listDataxTasks, type DataxTask } from '@/api/datax-task'
import { runWorkflow, getWorkflow } from '@/api/workflow'

import { VueFlow, MarkerType, type Connection, type NodeMouseEvent } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import SqlTaskNodeComponent from '@/components/dag/SqlTaskNode.vue'
import DataxNodeComponent from '@/components/dag/DataxNode.vue'

import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'

// ============ Route ============
const route = useRoute()
const router = useRouter()

const isCreateMode = computed(() => route.path === '/workflow/create')
const workflowId = computed(() => {
  if (isCreateMode.value) return null
  return Number(route.params.id)
})

// ============ Store ============
const store = useWorkflowStore()
const flowNodes = computed({
  get: () => store.nodes as any[],
  set: (val) => { store.nodes = val },
})
const flowEdges = computed({
  get: () => store.edges,
  set: (val: any) => { store.edges = val },
})

// ============ Vue Flow ============
const nodeTypes: Record<string, any> = {
  sqlTaskNode: markRaw(SqlTaskNodeComponent),
  dataxNode: markRaw(DataxNodeComponent),
}

const defaultEdgeOptions = {
  markerEnd: MarkerType.ArrowClosed,
}

interface NodeTemplate {
  nodeType: 'sql_task' | 'datax'
  label: string
  icon: string
}

const nodeTemplates: NodeTemplate[] = [
  { nodeType: 'sql_task', label: 'SQL 任务', icon: 'Document' },
  { nodeType: 'datax', label: 'ETL 任务', icon: 'Share' },
]

// ============ State ============
const loading = ref(false)
const saving = ref(false)
const flowError = ref(false)
const workflowName = ref('')
const selectedNodeId = ref<string | null>(null)
const canvasWrapperRef = ref<HTMLDivElement | null>(null)
const showCompatDialog = ref(false)
const compatResetDone = ref(false)

// ============ Data Sources ============
const allDataxTasks = ref<DataxTask[]>([])
const publishedDataxTasks = computed(() =>
  allDataxTasks.value.filter((t) => t.status === 'PUBLISHED'),
)

const selectedDataxTask = computed(() => {
  const id = dataxConfig.value?.dataxTaskId
  if (!id) return null
  return allDataxTasks.value.find((t) => t.id === id) ?? null
})

// ============ SQL Tasks ============
const allSqlTasks = ref<SqlTask[]>([])
const publishedSqlTasks = computed(() =>
  allSqlTasks.value.filter((t) => t.status === 'PUBLISHED'),
)

const selectedSqlTask = computed(() => {
  const id = sqlTaskConfig.value?.sqlTaskId
  if (!id) return null
  return allSqlTasks.value.find((t) => t.id === id) ?? null
})

// ============ Computed ============
const isPublished = computed(() => store.currentWorkflow?.status === 'PUBLISHED')
const canRun = computed(() => store.currentWorkflow?.status === 'PUBLISHED' && !!store.currentWorkflow?.id)

const configPanel = computed(() => {
  if (!selectedNode.value || flowError.value) return 'empty'
  return selectedNodeData.value?.nodeType || 'empty'
})

const selectedNode = computed<any | undefined>(() => {
  if (!selectedNodeId.value) return undefined
  return store.nodes.find((n) => n.id === selectedNodeId.value)
})

const selectedNodeData = computed(() => selectedNode.value?.data as any)

const dataxConfig = computed<DataxConfig | null>(() => {
  if (selectedNodeData.value?.nodeType !== 'datax') return null
  return selectedNodeData.value?.config as DataxConfig
})

const sqlTaskConfig = computed<SqlTaskConfig | null>(() => {
  if (selectedNodeData.value?.nodeType !== 'sql_task') return null
  return selectedNodeData.value?.config as SqlTaskConfig
})

// ============ Error Boundary ============
onErrorCaptured((err: Error) => {
  console.error('[WorkflowEditor] Caught error:', err)
  if (err.message?.includes('VueFlow') || err.message?.includes('vue-flow')) {
    flowError.value = true
    return false
  }
  return true
})

// ============ Lifecycle ============
onMounted(async () => {
  if (isCreateMode.value) {
    store.resetDag()
    workflowName.value = ''
  }

  try {
    const [sqlRes, dataxRes] = await Promise.all([
      listTasks(),
      listDataxTasks(),
    ])
    allSqlTasks.value = sqlRes.data
    allDataxTasks.value = dataxRes.data
  } catch { /* ignore */ }

  if (!isCreateMode.value && workflowId.value) {
    await loadWorkflow(workflowId.value)
  }
})

// Watch for node selection changes
watch(selectedNodeId, () => {
  // no-op: browser state no longer needed for inline DataX config
})

// ============ Load Workflow ============
async function loadWorkflow(id: number) {
  loading.value = true
  try {
    const res = await getWorkflow(id)  // imported separately
    const wf = res.data
    workflowName.value = wf.name

    // Check for old configMode format
    if (wf.dagJson) {
      try {
        const parsed = JSON.parse(wf.dagJson)
        if (parsed.configMode) {
          showCompatDialog.value = true
          return
        }
      } catch { /* invalid JSON */ }
    }

    await store.fetchWorkflow(id)
    workflowName.value = store.currentWorkflow?.name ?? ''
  } catch (e: any) {
    ElMessage.error(e.message || '加载工作流失败')
  } finally {
    loading.value = false
  }
}

// ============ Navigation ============
function goBack() {
  router.push('/workflow')
}

// ============ Drag and Drop ============
function onDragStart(event: DragEvent, template: NodeTemplate) {
  event.dataTransfer?.setData(
    'application/json',
    JSON.stringify({ nodeType: template.nodeType, label: template.label }),
  )
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'copy'
  }
}

function onCanvasDrop(event: DragEvent) {
  const data = event.dataTransfer?.getData('application/json')
  if (!data) return

  try {
    const template = JSON.parse(data)
    const canvasRect = canvasWrapperRef.value?.getBoundingClientRect()
    if (!canvasRect) return

    const position = {
      x: event.clientX - canvasRect.left - 75,
      y: event.clientY - canvasRect.top - 30,
    }

    store.addNode(template, position)
    ElMessage.success(`添加节点: ${template.label}`)
  } catch (e) {
    console.error('Drop error:', e)
  }
}

// ============ Vue Flow Events ============
function onNodeClick(event: NodeMouseEvent) {
  selectedNodeId.value = event.node.id
}

function onPaneClick() {
  selectedNodeId.value = null
}

function onConnect(connection: Connection) {
  if (connection.source === connection.target) {
    ElMessage.warning('不能连接到自身')
    return
  }
  store.addEdge(connection)
}

// ============ Node Operations ============
function deleteSelectedNode() {
  if (!selectedNodeId.value) return
  store.removeNode(selectedNodeId.value)
  selectedNodeId.value = null
}

function onNodeLabelChange(val: string | number) {
  const node = selectedNode.value
  if (node) {
    node.label = String(val)
    store.isDirty = true
  }
}

// ============ SQL Task Config ============
function onSqlTaskChange(val: number | null) {
  if (!selectedNodeId.value) return
  store.updateNodeConfig(selectedNodeId.value, { sqlTaskId: val } as any)
  // Auto-name the node
  if (val) {
    const task = allSqlTasks.value.find((t) => t.id === val)
    if (task && selectedNode.value) {
      selectedNode.value.label = task.name
    }
  }
}

// ============ DataX Task Config ============
function onDataxTaskChange(val: number | null) {
  if (!selectedNodeId.value) return
  store.updateNodeConfig(selectedNodeId.value, { dataxTaskId: val } as any)
  // Auto-name the node
  if (val) {
    const task = allDataxTasks.value.find((t) => t.id === val)
    if (task && selectedNode.value) {
      selectedNode.value.label = task.name
    }
  }
}

function formatDataxEndpoint(dsId: number | null | undefined, db: string | undefined | null, table: string | undefined | null): string {
  if (!dsId || !table) return '未配置'
  return `数据源 #${dsId}${db ? '.' + db : ''}.${table}`
}

// ============ Save / Run ============
async function handleSave() {
  if (!workflowName.value.trim()) {
    ElMessage.warning('请输入工作流名称')
    return
  }

  saving.value = true
  try {
    await store.saveWorkflow({ name: workflowName.value })
    ElMessage.success(isCreateMode.value ? '工作流创建成功' : '工作流保存成功')

    if (isCreateMode.value && store.currentWorkflow?.id) {
      router.replace(`/workflow/${store.currentWorkflow.id}/edit`)
    }
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function handleRun() {
  if (!store.currentWorkflow?.id) {
    ElMessage.warning('请先保存工作流')
    return
  }
  if (store.currentWorkflow.status !== 'PUBLISHED') {
    ElMessage.warning('请先发布工作流后再运行')
    return
  }
  try {
    await runWorkflow(store.currentWorkflow.id)
    ElMessage.success('工作流已触发运行')
  } catch (e: any) {
    ElMessage.error(e.message || '运行失败')
  }
}
</script>

<style scoped>
.workflow-editor {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 100px);
}

/* ===== Toolbar ===== */
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 16px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  border-radius: 4px;
  margin-bottom: 8px;
  flex-shrink: 0;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.workflow-name-section {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  max-width: 400px;
}

.name-input {
  flex: 1;
}

.unsaved-badge {
  font-size: 12px;
  color: #e6a23c;
  background: #fdf6ec;
  border: 1px solid #faecd8;
  padding: 2px 8px;
  border-radius: 4px;
}

/* ===== Error ===== */
.flow-error-alert {
  margin: 16px;
}

/* ===== Editor Body (Three-panel) ===== */
.editor-body {
  display: flex;
  flex: 1;
  gap: 8px;
  overflow: hidden;
}

/* ===== Node Panel (Left) ===== */
.node-panel {
  width: 160px;
  min-width: 160px;
  background: #fff;
  border-radius: 4px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  overflow-y: auto;
  flex-shrink: 0;
}

.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin: 0;
}

.panel-hint {
  font-size: 12px;
  color: #909399;
  margin: 0;
}

.node-template-card {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  cursor: grab;
  font-size: 13px;
  font-weight: 500;
  transition: all 0.2s;
  user-select: none;
}

.node-template-card:active {
  cursor: grabbing;
}

.node-template-card.sql_task {
  border-left: 3px solid #722ed1;
}

.node-template-card.sql_task:hover {
  background: #f9f0ff;
  border-color: #d3adf7;
}

.node-template-card.datax {
  border-left: 3px solid #fa8c16;
}

.node-template-card.datax:hover {
  background: #fff7e6;
  border-color: #ffd591;
}

/* ===== Canvas (Center) ===== */
.canvas-wrapper {
  flex: 1;
  background: #fafafa;
  border-radius: 4px;
  overflow: hidden;
  position: relative;
  min-height: 400px;
  border: 1px solid #e4e7ed;
}

.vue-flow-instance {
  width: 100%;
  height: 100%;
}

/* ===== Property Panel (Right) ===== */
.property-panel {
  width: 340px;
  min-width: 340px;
  background: #fff;
  border-radius: 4px;
  padding: 12px;
  overflow-y: auto;
  flex-shrink: 0;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.sql-preview {
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 8px;
  font-size: 12px;
  font-family: 'Menlo', 'Monaco', monospace;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 120px;
  overflow-y: auto;
  width: 100%;
  margin: 0;
}

.property-panel :deep(.el-form-item) {
  margin-bottom: 12px;
}

.property-panel :deep(.el-form-item__label) {
  font-size: 12px;
  padding-bottom: 2px;
}

.property-panel :deep(.el-divider) {
  margin: 12px 0;
}

.config-value {
  color: #606266;
  font-size: 13px;
}




.section-actions {
  display: flex;
  gap: 6px;
}

</style>
