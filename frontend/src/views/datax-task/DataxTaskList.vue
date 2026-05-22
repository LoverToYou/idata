<template>
  <Layout>
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>ETL 任务管理</span>
          <el-button type="primary" @click="handleCreate">
            <el-icon><Plus /></el-icon> 新建任务
          </el-button>
        </div>
      </template>

      <el-table :data="tasks" stripe v-loading="loading" @row-dblclick="handleEdit">
        <el-table-column type="index" label="#" width="60" />
        <el-table-column prop="name" label="任务名称" min-width="200" />
        <el-table-column label="源端" min-width="150">
          <template #default="{ row }">
            <span v-if="row.readerDatasourceId" class="conn-info">
              {{ getDsName(row.readerDatasourceId) }}.{{ row.readerTable }}
            </span>
            <span v-else class="no-config">未配置</span>
          </template>
        </el-table-column>
        <el-table-column label="目标端" min-width="150">
          <template #default="{ row }">
            <span v-if="row.writerDatasourceId" class="conn-info">
              {{ getDsName(row.writerDatasourceId) }}.{{ row.writerTable }}
            </span>
            <span v-else class="no-config">未配置</span>
          </template>
        </el-table-column>
        <el-table-column label="写入模式" width="110">
          <template #default="{ row }">
            <el-tag :type="row.writeMode === 'overwrite' ? 'warning' : 'info'" size="small" effect="plain">
              {{ row.writeMode === 'overwrite' ? '覆盖' : '追加' }}
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
        <el-table-column label="更新时间" width="180">
          <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              size="small" type="success"
              @click="handlePublish(row)"
            >发布</el-button>
            <el-button
              v-if="row.status === 'PUBLISHED'"
              size="small" type="warning"
              @click="handleUnpublish(row)"
            >下架</el-button>
            <el-button
              size="small"
              @click="handlePreviewJson(row)"
            >JSON</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- Create Task Dialog -->
    <el-dialog v-model="createDialogVisible" title="新建任务" width="520px" :close-on-click-modal="false">
      <el-form label-position="top">
        <el-form-item label="任务名称" required>
          <el-input v-model="createForm.name" placeholder="输入任务名称" />
        </el-form-item>
        <el-form-item label="任务类型">
          <el-select v-model="createForm.taskType" disabled style="width: 100%">
            <el-option label="DataX 数据同步" value="DataX" />
          </el-select>
        </el-form-item>
        <el-form-item label="配置模式">
          <el-radio-group v-model="createForm.configMode">
            <el-radio value="UI">界面配置模式</el-radio>
            <el-radio value="SCRIPT">脚本配置模式</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleCreateConfirm" :loading="creating">确认</el-button>
      </template>
    </el-dialog>

    <!-- DataX JSON Preview Dialog -->
    <el-dialog v-model="jsonDialogVisible" title="DataX JSON" width="800px" top="5vh">
      <pre class="json-preview">{{ dataxJson }}</pre>
      <template #footer>
        <el-button @click="jsonDialogVisible = false">关闭</el-button>
        <el-button type="primary" @click="copyJson">复制</el-button>
      </template>
    </el-dialog>
  </Layout>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import Layout from '@/components/common/Layout.vue'
import {
  listDataxTasks,
  createDataxTask,
  deleteDataxTask,
  publishDataxTask,
  unpublishDataxTask,
  getDataxTaskJson,
  type DataxTaskRequest,
} from '@/api/datax-task'
import { listDatasources } from '@/api/datasource'
import type { DatasourceConfig } from '@/types'
import { Plus } from '@element-plus/icons-vue'

const router = useRouter()
const tasks = ref<any[]>([])
const loading = ref(false)
const datasources = ref<DatasourceConfig[]>([])
const jsonDialogVisible = ref(false)
const dataxJson = ref('')

// Create Dialog
const createDialogVisible = ref(false)
const creating = ref(false)
const createForm = reactive({
  name: '',
  taskType: 'DataX',
  configMode: 'UI',
})

function formatTime(t: string) {
  if (!t) return ''
  return t.slice(0, 16).replace('T', ' ')
}

function getDsName(id: number): string {
  return datasources.value.find(ds => ds.id === id)?.name || `#${id}`
}

async function loadTasks() {
  loading.value = true
  try {
    const res = await listDataxTasks()
    tasks.value = res.data
  } catch { /* ignore */ }
  finally { loading.value = false }
}

function handleCreate() {
  createForm.name = ''
  createForm.configMode = 'UI'
  createDialogVisible.value = true
}

async function handleCreateConfirm() {
  if (!createForm.name.trim()) {
    ElMessage.warning('请输入任务名称')
    return
  }

  creating.value = true
  try {
    const payload: DataxTaskRequest = {
      name: createForm.name.trim(),
      configMode: createForm.configMode,
    }
    const res = await createDataxTask(payload)
    createDialogVisible.value = false
    ElMessage.success('任务已创建')
    await loadTasks()
    router.push(`/datax-task/${res.data.id}/edit`)
  } catch (e: any) {
    ElMessage.error(e.message || '创建失败')
  } finally {
    creating.value = false
  }
}

function handleEdit(task: any) {
  router.push(`/datax-task/${task.id}/edit`)
}

async function handleDelete(task: any) {
  try {
    await ElMessageBox.confirm(`确定删除任务「${task.name}」？`, '确认删除', { type: 'warning' })
    await deleteDataxTask(task.id)
    ElMessage.success('已删除')
    await loadTasks()
  } catch { /* cancelled */ }
}

async function handlePublish(task: any) {
  try {
    await ElMessageBox.confirm(`发布任务「${task.name}」？发布后该任务可被工作流调度执行。`, '发布确认')
    await publishDataxTask(task.id)
    ElMessage.success('已发布')
    await loadTasks()
  } catch { /* cancelled */ }
}

async function handleUnpublish(task: any) {
  try {
    await ElMessageBox.confirm(`确定下架任务「${task.name}」？`, '确认下架')
    await unpublishDataxTask(task.id)
    ElMessage.success('已下架')
    await loadTasks()
  } catch { /* cancelled */ }
}

async function handlePreviewJson(task: any) {
  try {
    const res = await getDataxTaskJson(task.id)
    dataxJson.value = res.data.dataxJson
    jsonDialogVisible.value = true
  } catch {
    ElMessage.error('获取 DataX JSON 失败')
  }
}

async function copyJson() {
  try {
    await navigator.clipboard.writeText(dataxJson.value)
    ElMessage.success('已复制')
  } catch {
    ElMessage.warning('复制失败')
  }
}

onMounted(async () => {
  try {
    const res = await listDatasources()
    datasources.value = res.data
  } catch { /* ignore */ }
  await loadTasks()
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.conn-info {
  font-size: 13px;
}
.no-config {
  color: #c0c4cc;
  font-style: italic;
}
.json-preview {
  background: #1e1e1e;
  color: #d4d4d4;
  padding: 16px;
  border-radius: 4px;
  overflow: auto;
  max-height: 60vh;
  font-size: 13px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
