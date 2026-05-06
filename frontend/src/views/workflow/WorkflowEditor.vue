<template>
  <Layout>
    <div class="workflow-editor">
      <!-- Toolbar -->
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
          </div>
        </div>
        <div class="toolbar-right">
          <span v-if="store.isDirty" class="unsaved-badge">未保存</span>
          <el-button @click="handleSave" type="primary" :loading="saving" :disabled="isPublished">
            <el-icon><Check /></el-icon> 保存
          </el-button>
          <el-button @click="handleRun" type="success">
            <el-icon><CaretRight /></el-icon> 运行
          </el-button>
        </div>
      </div>

      <!-- Error fallback -->
      <el-alert
        v-if="flowError"
        title="DAG 编辑器加载失败"
        type="error"
        description="Vue Flow 渲染异常，请刷新页面重试。"
        show-icon
        :closable="false"
        class="flow-error-alert"
      />

      <!-- Editor Body (three-panel) -->
      <div v-else class="editor-body">
        <!-- Left Panel: Node Templates -->
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

        <!-- Center: Vue Flow Canvas -->
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

        <!-- Right Panel: Property Panel -->
        <div class="property-panel">
          <template v-if="selectedNode">
            <div class="panel-header">
              <h3 class="panel-title">节点配置</h3>
              <el-radio-group v-model="configMode" size="small">
                <el-radio-button value="form">界面配置</el-radio-button>
                <el-radio-button value="json">JSON 配置</el-radio-button>
              </el-radio-group>
            </div>

            <template v-if="configMode === 'form'">
              <el-form label-position="top" size="small">
                <el-form-item label="节点名称">
                  <el-input
                    :model-value="getNodeLabel(selectedNode)"
                    @input="onNodeNameChange"
                    placeholder="请输入节点名称"
                  />
                </el-form-item>

                <el-form-item label="ETL 类型">
                  <el-tag type="warning" effect="plain">DataX</el-tag>
                </el-form-item>

                <el-form-item label="节点类型">
                  <el-tag
                    :type="selectedNode.data?.nodeType === 'source' ? 'primary' : 'success'"
                    effect="plain"
                  >
                    {{ selectedNode.data?.nodeType === 'source' ? '数据源 (Source)' : '数据目标 (Sink)' }}
                  </el-tag>
                </el-form-item>

                <el-form-item label="数据源">
                  <el-select
                    v-if="selectedNode.data?.config"
                    :model-value="selectedNode.data.config.datasourceId"
                    @update:model-value="onConfigChange('datasourceId', $event)"
                    placeholder="选择数据源"
                    filterable
                    clearable
                  >
                    <el-option
                      v-for="ds in datasourceList"
                      :key="ds.id"
                      :label="ds.name"
                      :value="ds.id"
                    >
                      <span>{{ ds.name }}</span>
                      <el-tag
                        :type="ds.type === 'MYSQL' ? 'success' : 'warning'"
                        size="small"
                        style="margin-left: 8px"
                      >
                        {{ ds.type }}
                      </el-tag>
                    </el-option>
                  </el-select>
                </el-form-item>

                <el-form-item label="目标表名">
                  <el-input
                    v-if="selectedNode.data?.config"
                    :model-value="selectedNode.data.config.tableName"
                    @input="onConfigChange('tableName', $event)"
                    placeholder="请输入表名"
                  />
                </el-form-item>

                <el-form-item label="字段映射">
                  <el-input
                    v-model="columnsText"
                    type="textarea"
                    :rows="4"
                    placeholder="每行一个字段，或使用 source_col:target_col 格式映射"
                  />
                </el-form-item>

                <el-form-item v-if="selectedNode.data?.nodeType === 'source'" label="过滤条件 (WHERE)">
                  <el-input
                    :model-value="selectedNode.data?.config?.where ?? ''"
                    @input="onConfigChange('where', $event)"
                    placeholder="例如: status = 1"
                  />
                </el-form-item>

                <el-form-item v-if="selectedNode.data?.nodeType === 'sink'" label="写入模式">
                  <el-select
                    v-if="selectedNode.data?.config"
                    :model-value="selectedNode.data.config.writeMode"
                    @update:model-value="onConfigChange('writeMode', $event)"
                  >
                    <el-option label="插入 (insert)" value="insert" />
                    <el-option label="覆盖 (overwrite)" value="overwrite" />
                  </el-select>
                </el-form-item>
              </el-form>

              <el-divider />

              <el-button type="danger" size="small" @click="deleteSelectedNode">
                <el-icon><Delete /></el-icon> 删除节点
              </el-button>
            </template>

            <template v-else>
              <pre class="json-preview">{{ getDataXJson() }}</pre>
            </template>
          </template>

          <template v-else>
            <el-empty description="选择节点以配置属性" :image-size="80" />
          </template>
        </div>
      </div>
    </div>
  </Layout>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onErrorCaptured, markRaw } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import Layout from '@/components/common/Layout.vue'
import { useWorkflowStore } from '@/stores/workflow'
import { listDatasources } from '@/api/datasource'
import { runWorkflow } from '@/api/workflow'
import type { DatasourceConfig } from '@/types'
import { generateDataXJson } from '@/utils/datax-generator'

import { VueFlow, type Connection, type NodeMouseEvent } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import SourceNodeComponent from '@/components/dag/SourceNode.vue'
import SinkNodeComponent from '@/components/dag/SinkNode.vue'

import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'

// --- Route ---
const route = useRoute()
const router = useRouter()

const isCreateMode = computed(() => route.path === '/workflow/create')
const workflowId = computed(() => {
  if (isCreateMode.value) return null
  return Number(route.params.id)
})

// --- Store ---
const store = useWorkflowStore()
const flowNodes = computed({
  get: () => store.nodes as any[],
  set: (val) => { store.nodes = val },
})
const flowEdges = computed({
  get: () => store.edges,
  set: (val: any) => { store.edges = val },
})

// --- Custom Node Types ---
const nodeTypes: Record<string, any> = {
  sourceNode: markRaw(SourceNodeComponent),
  sinkNode: markRaw(SinkNodeComponent),
}

// --- Node Templates ---
interface NodeTemplate {
  nodeType: 'source' | 'sink'
  label: string
  icon: string
  dataxReader?: string
  dataxWriter?: string
}

const nodeTemplates: NodeTemplate[] = [
  { nodeType: 'source', label: 'MySQL Reader', icon: 'Reading', dataxReader: 'mysqlreader' },
  { nodeType: 'source', label: 'Hive Reader', icon: 'Reading', dataxReader: 'hivereader' },
  { nodeType: 'sink', label: 'MySQL Writer', icon: 'EditPen', dataxWriter: 'mysqlwriter' },
  { nodeType: 'sink', label: 'Hive Writer', icon: 'EditPen', dataxWriter: 'hivewriter' },
]

// --- State ---
const loading = ref(false)
const saving = ref(false)
const flowError = ref(false)
const workflowName = ref('')
const selectedNodeId = ref<string | null>(null)
const datasourceList = ref<DatasourceConfig[]>([])
const canvasWrapperRef = ref<HTMLDivElement | null>(null)
const configMode = ref<'form' | 'json'>('form')

// --- Computed ---
const isPublished = computed(() => store.currentWorkflow?.status === 'PUBLISHED')

const selectedNode = computed<any | undefined>(() => {
  if (!selectedNodeId.value) return undefined
  return store.nodes.find((n) => n.id === selectedNodeId.value)
})

const columnsText = computed({
  get: () => {
    const node = selectedNode.value
    if (!node?.data?.config?.columns) return ''
    return (node.data.config.columns as string[]).join('\n')
  },
  set: (val: string) => {
    const node = selectedNode.value
    if (!node?.data?.config) return
    node.data.config.columns = val.split('\n').filter((c) => c.trim())
    markDirty()
  },
})

// --- Error Boundary ---
onErrorCaptured((err: Error) => {
  console.error('[WorkflowEditor] Caught error:', err)
  if (err.message?.includes('VueFlow') || err.message?.includes('vue-flow')) {
    flowError.value = true
    return false
  }
  return true
})

// --- Lifecycle ---
onMounted(async () => {
  await loadDatasources()
  if (!isCreateMode.value && workflowId.value) {
    await loadWorkflow(workflowId.value)
  }
})

// --- Methods ---
async function loadDatasources() {
  try {
    const res = await listDatasources()
    datasourceList.value = res.data
  } catch {
    // ignore
  }
}

async function loadWorkflow(id: number) {
  loading.value = true
  try {
    await store.fetchWorkflow(id)
    workflowName.value = store.currentWorkflow?.name ?? ''
  } catch (e: any) {
    ElMessage.error(e.message || '加载工作流失败')
  } finally {
    loading.value = false
  }
}

function markDirty() {
  store.isDirty = true
}

function getNodeLabel(node: any): string {
  return (node.label as string) ?? ''
}

function onNodeNameChange(val: string | number) {
  const node = selectedNode.value
  if (node) {
    node.label = String(val)
    markDirty()
  }
}

function onConfigChange(field: string, val: any) {
  const node = selectedNode.value
  if (node?.data?.config) {
    ;(node.data.config as any)[field] = val
    markDirty()
  }
}

// --- DataX JSON generation (auto-generated from form config) ---
function getDataXJson(): string {
  try {
    return generateDataXJson(store.nodes, store.edges)
  } catch {
    return '{}'
  }
}

function goBack() {
  router.push('/workflow')
}

// --- Drag and Drop ---
function onDragStart(event: DragEvent, template: NodeTemplate) {
  event.dataTransfer?.setData(
    'application/json',
    JSON.stringify({
      nodeType: template.nodeType,
      label: template.label,
      dataxReader: template.dataxReader,
      dataxWriter: template.dataxWriter,
    }),
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

    // Convert screen coordinates to flow coordinates
    // Since we have defaultViewport { x:0, y:0, zoom:1 }, screen coords relative to canvas work directly
    const position = {
      x: event.clientX - canvasRect.left - 75, // offset half card width
      y: event.clientY - canvasRect.top - 30,
    }

    store.addNode(template, position)
    ElMessage.success(`添加节点: ${template.label}`)
  } catch (e) {
    console.error('Drop error:', e)
  }
}

// --- Vue Flow Events ---
function onNodeClick(event: NodeMouseEvent) {
  selectedNodeId.value = event.node.id
}

function onPaneClick() {
  selectedNodeId.value = null
}

function onConnect(connection: Connection) {
  // Validate: no self-connections
  if (connection.source === connection.target) {
    ElMessage.warning('不能连接到自身')
    return
  }
  store.addEdge(connection)
}

// --- Node Operations ---
function deleteSelectedNode() {
  if (!selectedNodeId.value) return
  store.removeNode(selectedNodeId.value)
  selectedNodeId.value = null
}

// --- Save / Run ---
async function handleSave() {
  if (!workflowName.value.trim()) {
    ElMessage.warning('请输入工作流名称')
    return
  }

  saving.value = true
  try {
    await store.saveWorkflow({ name: workflowName.value })
    ElMessage.success(
      isCreateMode.value ? '工作流创建成功' : '工作流保存成功',
    )

    // If we just created, navigate to edit URL
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

/* Toolbar */
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

/* Error alert */
.flow-error-alert {
  margin: 16px;
}

/* Three-panel layout */
.editor-body {
  display: flex;
  flex: 1;
  gap: 8px;
  overflow: hidden;
}

/* Node Panel (Left) */
.node-panel {
  width: 180px;
  min-width: 180px;
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

.node-template-card.source {
  border-left: 3px solid #409eff;
}

.node-template-card.source:hover {
  background: #e6f7ff;
  border-color: #91d5ff;
}

.node-template-card.sink {
  border-left: 3px solid #67c23a;
}

.node-template-card.sink:hover {
  background: #f0f9eb;
  border-color: #b7eb8f;
}

/* Canvas (Center) */
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

/* Property Panel (Right) */
.property-panel {
  width: 320px;
  min-width: 320px;
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

.json-preview {
  width: 100%;
  height: 500px;
  overflow: auto;
  background: #f8f9fa;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 12px;
  margin: 0;
  font-size: 12px;
  font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
  line-height: 1.6;
  white-space: pre;
  tab-size: 2;
  color: #24292e;
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
</style>
