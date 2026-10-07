<template>
  <Layout>
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>Python 脚本</span>
          <div class="header-actions">
            <el-button @click="refreshAll">
              <el-icon><Refresh /></el-icon> 刷新
            </el-button>
            <el-button type="primary" @click="openCreateDialog">
              <el-icon><Plus /></el-icon> 新建脚本
            </el-button>
          </div>
        </div>
      </template>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="脚本列表" name="scripts">
          <el-table :data="scripts" stripe v-loading="scriptsLoading">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
            <el-table-column prop="description" label="描述" min-width="160" show-overflow-tooltip />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 'PUBLISHED' ? 'success' : 'info'" effect="plain" size="small">
                  {{ row.status === 'PUBLISHED' ? '已发布' : '草稿' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="创建时间" width="170">
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="300" fixed="right">
              <template #default="{ row }">
                <el-button size="small" type="success" plain @click="openRunDialog(row)">执行</el-button>
                <el-button size="small" @click="openEditDialog(row)">编辑</el-button>
                <el-button
                  v-if="row.status !== 'PUBLISHED'"
                  size="small"
                  type="primary"
                  plain
                  @click="handlePublish(row)"
                >发布</el-button>
                <el-button
                  v-else
                  size="small"
                  @click="handleUnpublish(row)"
                >取消发布</el-button>
                <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="执行记录" name="runs">
          <el-table :data="runs" stripe v-loading="runsLoading">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="scriptName" label="脚本" min-width="140" show-overflow-tooltip />
            <el-table-column label="触发方式" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="row.triggeredBy === 'WORKFLOW' ? 'info' : 'primary'" effect="plain">
                  {{ row.triggeredBy === 'WORKFLOW' ? '工作流' : '手动' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="exitCode" label="退出码" width="80">
              <template #default="{ row }">{{ row.exitCode ?? '-' }}</template>
            </el-table-column>
            <el-table-column label="开始时间" width="170">
              <template #default="{ row }">{{ formatTime(row.startedAt) }}</template>
            </el-table-column>
            <el-table-column label="结束时间" width="170">
              <template #default="{ row }">{{ formatTime(row.finishedAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button size="small" @click="openRunDetail(row)">详情</el-button>
                <el-button v-if="row.status === 'RUNNING'" size="small" type="danger" @click="handleCancelRun(row)">取消</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-row">
            <el-pagination
              layout="total, prev, pager, next"
              :total="runsTotal"
              :current-page="runsPage"
              :page-size="runsPageSize"
              @current-change="(p: number) => loadRuns(p)"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- Run dialog -->
    <el-dialog v-model="runDialogVisible" :title="`执行脚本：${runTarget?.name || ''}`" width="520px">
      <el-form label-position="top">
        <el-form-item label="参数（写入脚本 stdin，脚本内用 sys.stdin.read() 读取）">
          <el-input v-model="runParams" type="textarea" :rows="6" placeholder="可输入 JSON 或任意文本，留空则传空串" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="runDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleRun">执行</el-button>
      </template>
    </el-dialog>

    <!-- Create/Edit dialog -->
    <el-dialog v-model="editorDialogVisible" :title="isEditing ? '编辑脚本' : '新建脚本'" width="760px" :close-on-click-modal="false" @closed="onEditorClosed">
      <el-form :model="editorForm" ref="editorFormRef" :rules="editorRules" label-width="90px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="editorForm.name" placeholder="脚本名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editorForm.description" placeholder="脚本描述（可选）" />
        </el-form-item>
        <el-form-item label="脚本内容" prop="content">
          <div ref="monacoContainer" class="monaco-container"></div>
        </el-form-item>
        <el-form-item label="超时(秒)">
          <el-input-number v-model="editorForm.timeoutSeconds" :min="1" :max="3600" />
          <span class="muted" style="margin-left: 8px">超过自动强杀，默认 60</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editorDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSaveScript">保存</el-button>
      </template>
    </el-dialog>

    <!-- Run detail dialog -->
    <el-dialog v-model="detailVisible" :title="`执行详情 #${detail?.id || ''}`" width="760px">
      <div class="detail-block">
        <div class="detail-label">标准输出 (stdout)</div>
        <pre class="output-pre">{{ detail?.stdout || '(空)' }}</pre>
      </div>
      <div class="detail-block">
        <div class="detail-label">标准错误 (stderr)</div>
        <pre class="output-pre err">{{ detail?.stderr || '(空)' }}</pre>
      </div>
    </el-dialog>
  </Layout>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import Layout from '@/components/common/Layout.vue'
import { Refresh, Plus } from '@element-plus/icons-vue'
import {
  listScriptsPage,
  createScript,
  updateScript,
  deleteScript,
  runScript,
  listRuns,
  getRun,
  cancelRun,
  publishScript,
  unpublishScript,
} from '@/api/python-script'
import type { PythonScript, PythonRun } from '@/types'
import * as monaco from 'monaco-editor'

const activeTab = ref('scripts')

// ---- 脚本列表 ----
const scripts = ref<PythonScript[]>([])
const scriptsLoading = ref(false)

// ---- 执行记录 ----
const runs = ref<PythonRun[]>([])
const runsLoading = ref(false)
const runsPage = ref(1)
const runsPageSize = ref(10)
const runsTotal = ref(0)
const detail = ref<PythonRun | null>(null)
const detailVisible = ref(false)

// ---- 执行弹窗 ----
const runDialogVisible = ref(false)
const runTarget = ref<PythonScript | null>(null)
const runParams = ref('')
const submitting = ref(false)

// ---- 编辑弹窗 ----
const editorDialogVisible = ref(false)
const isEditing = ref(false)
const currentId = ref<number | null>(null)
const saving = ref(false)
const editorFormRef = ref()
const editorForm = reactive({
  name: '',
  description: '',
  content: '',
  timeoutSeconds: 60,
})
const editorRules = {
  name: [{ required: true, message: '请输入脚本名称', trigger: 'blur' }],
  content: [{ required: true, message: '请输入脚本内容', trigger: 'blur' }],
}

// Monaco
const monacoContainer = ref<HTMLDivElement | null>(null)
let editor: monaco.editor.IStandaloneCodeEditor | null = null

function formatTime(t: string) {
  if (!t) return ''
  return t.slice(0, 16).replace('T', ' ')
}

function statusType(s: string) {
  if (s === 'RUNNING') return 'warning'
  if (s === 'SUCCESS') return 'success'
  if (s === 'FAILED') return 'danger'
  return 'info'
}

function statusText(s: string) {
  if (s === 'RUNNING') return '进行中'
  if (s === 'SUCCESS') return '成功'
  if (s === 'FAILED') return '失败'
  if (s === 'CANCELLED') return '已取消'
  return s
}

async function loadScripts() {
  scriptsLoading.value = true
  try {
    const res = await listScriptsPage(undefined, 1, 100)
    scripts.value = res.data.data
  } catch (e: any) {
    ElMessage.error(e.message || '加载脚本失败')
  } finally {
    scriptsLoading.value = false
  }
}

async function loadRuns(page = runsPage.value) {
  runsLoading.value = true
  try {
    const res = await listRuns(undefined, page, runsPageSize.value)
    runs.value = res.data.data
    runsTotal.value = res.data.total
    runsPage.value = res.data.page
  } catch (e: any) {
    ElMessage.error(e.message || '加载执行记录失败')
  } finally {
    runsLoading.value = false
  }
}

function refreshAll() {
  loadScripts()
  loadRuns()
}

// ---- 执行 ----
function openRunDialog(row: PythonScript) {
  runTarget.value = row
  runParams.value = ''
  runDialogVisible.value = true
}

async function handleRun() {
  if (!runTarget.value) return
  submitting.value = true
  try {
    await runScript(runTarget.value.id, runParams.value)
    ElMessage.success('已提交执行，可在执行记录查看')
    runDialogVisible.value = false
    activeTab.value = 'runs'
    await loadRuns(1)
  } catch (e: any) {
    ElMessage.error(e.message || '执行失败')
  } finally {
    submitting.value = false
  }
}

// ---- 新建 / 编辑 ----
function openCreateDialog() {
  isEditing.value = false
  currentId.value = null
  editorForm.name = ''
  editorForm.description = ''
  editorForm.timeoutSeconds = 60
  editorDialogVisible.value = true
  initEditor('')
}

function openEditDialog(row: PythonScript) {
  isEditing.value = true
  currentId.value = row.id
  editorForm.name = row.name
  editorForm.description = row.description || ''
  editorForm.timeoutSeconds = row.timeoutSeconds || 60
  editorDialogVisible.value = true
  initEditor(row.content)
}

function initEditor(value: string) {
  nextTick(() => {
    if (!monacoContainer.value) return
    if (editor) {
      editor.setValue(value || '')
      return
    }
    editor = monaco.editor.create(monacoContainer.value, {
      value: value || '',
      language: 'python',
      theme: 'vs',
      automaticLayout: true,
      minimap: { enabled: false },
      fontSize: 13,
      lineNumbers: 'on',
      scrollBeyondLastLine: false,
      tabSize: 4,
      wordWrap: 'on',
    })
    editor.onDidChangeModelContent(() => {
      editorForm.content = editor?.getValue() || ''
    })
  })
}

function onEditorClosed() {
  editor?.dispose()
  editor = null
}

async function handleSaveScript() {
  if (!editorFormRef.value) return
  try {
    await editorFormRef.value.validate()
  } catch {
    return
  }
  editorForm.content = editor?.getValue() || ''
  saving.value = true
  try {
    const payload = {
      name: editorForm.name,
      description: editorForm.description,
      content: editorForm.content,
      timeoutSeconds: editorForm.timeoutSeconds,
    }
    if (isEditing.value && currentId.value) {
      await updateScript({ id: currentId.value, ...payload })
      ElMessage.success('脚本已更新')
    } else {
      await createScript(payload)
      ElMessage.success('脚本已创建')
    }
    editorDialogVisible.value = false
    await loadScripts()
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: PythonScript) {
  try {
    await ElMessageBox.confirm(`确定删除脚本「${row.name}」？`, '确认删除', { type: 'warning' })
    await deleteScript(row.id)
    ElMessage.success('已删除')
    await loadScripts()
  } catch { /* cancelled */ }
}

async function handlePublish(row: PythonScript) {
  try {
    await publishScript(row.id)
    ElMessage.success('已发布')
    await loadScripts()
  } catch (e: any) {
    ElMessage.error(e.message || '发布失败')
  }
}

async function handleUnpublish(row: PythonScript) {
  try {
    await unpublishScript(row.id)
    ElMessage.success('已取消发布')
    await loadScripts()
  } catch (e: any) {
    ElMessage.error(e.message || '操作失败')
  }
}

// ---- 执行记录 ----
async function openRunDetail(row: PythonRun) {
  try {
    const res = await getRun(row.id)
    detail.value = res.data
    detailVisible.value = true
  } catch (e: any) {
    ElMessage.error(e.message || '获取详情失败')
  }
}

async function handleCancelRun(row: PythonRun) {
  try {
    await ElMessageBox.confirm(`确定取消执行 #${row.id}？`, '确认取消', { type: 'warning' })
    await cancelRun(row.id)
    ElMessage.success('已发送取消请求')
    await loadRuns()
  } catch { /* cancelled */ }
}

// 有 RUNNING 记录时 10s 轮询刷新
let pollTimer: number | null = null
function startPollingIfNeeded() {
  if (runs.value.some((r) => r.status === 'RUNNING')) {
    if (!pollTimer) {
      pollTimer = window.setInterval(() => {
        loadRuns()
      }, 10000)
    }
  } else if (pollTimer) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
}

watch(runs, startPollingIfNeeded)

onMounted(() => {
  loadScripts()
  loadRuns()
})
onBeforeUnmount(() => {
  if (pollTimer) window.clearInterval(pollTimer)
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
.header-actions {
  display: flex;
  align-items: center;
}
.muted {
  color: var(--text-sub);
  font-size: var(--fs-sm);
}
.monaco-container {
  width: 100%;
  height: 320px;
  border: 1px solid var(--border);
  border-radius: 4px;
  overflow: hidden;
}
.pagination-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
.detail-block {
  margin-bottom: 12px;
}
.detail-label {
  font-size: var(--fs-base);
  font-weight: 600;
  color: var(--text-title);
  margin-bottom: 6px;
}
.output-pre {
  background: #1e1e1e;
  color: #d4d4d4;
  border-radius: 4px;
  padding: 12px;
  max-height: 260px;
  overflow: auto;
  font-size: var(--fs-sm);
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
}
.output-pre.err {
  color: #f48771;
}
</style>
