<template>
  <Layout>
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>工作流任务列表</span>
          <div class="header-actions">
            <el-input
              v-model="keyword"
              placeholder="搜索工作流任务名称"
              clearable
              style="width: 240px; margin-right: 12px"
              @clear="handleSearch"
              @keyup.enter="handleSearch"
            />
            <el-button @click="handleSearch">搜索</el-button>
            <el-button
              v-if="selectedIds.length > 0"
              type="danger"
              @click="handleBatchDelete"
            >
              <el-icon><Delete /></el-icon> 批量删除 ({{ selectedIds.length }})
            </el-button>
            <el-button type="primary" @click="handleCreate">
              <el-icon><Plus /></el-icon> 新建任务
            </el-button>
          </div>
        </div>
      </template>

      <el-table :data="workflows" stripe v-loading="loading" empty-text="暂无工作流任务" @selection-change="onSelectionChange">
        <el-table-column type="selection" width="50" />
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 'PUBLISHED' ? 'success' : 'info'" effect="light">
              {{ row.status === 'PUBLISHED' ? '已发布' : '草稿' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column prop="updatedAt" label="更新时间" width="180" />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="$router.push(`/workflow/${row.id}/edit`)">
              编辑
            </el-button>
            <el-button
              v-if="row.status === 'PUBLISHED'"
              size="small"
              type="warning"
              @click="handleUnpublish(row)"
            >
              下架
            </el-button>
            <el-button
              v-else
              size="small"
              type="success"
              @click="handlePublish(row)"
            >
              发布
            </el-button>
            <el-button
              v-if="row.status === 'PUBLISHED'"
              size="small"
              type="warning"
              @click="handleRun(row)"
            >
              运行
            </el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-if="total > 0"
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[5, 10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        background
        small
        style="margin-top: 16px; justify-content: flex-end;"
        @current-change="handlePageChange"
        @size-change="handlePageChange"
      />
    </el-card>

    <!-- Create Workflow Dialog -->
    <el-dialog v-model="createDialogVisible" title="新建工作流" width="520px" :close-on-click-modal="false">
      <el-form label-position="top">
        <el-form-item label="工作流名称" required>
          <el-input v-model="createForm.name" placeholder="输入工作流名称" />
        </el-form-item>
        <el-form-item label="任务描述">
          <el-input v-model="createForm.description" placeholder="请输入任务描述" maxlength="200" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleCreateConfirm" :loading="creating">确认</el-button>
      </template>
    </el-dialog>
  </Layout>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import Layout from '@/components/common/Layout.vue'
import { listWorkflowsPage, createWorkflow, deleteWorkflow, deleteWorkflowBatch, publishWorkflow, runWorkflow, unpublishWorkflow } from '@/api/workflow'
import type { WorkflowDefinition } from '@/types'

const router = useRouter()

const workflows = ref<WorkflowDefinition[]>([])
const loading = ref(false)
const creating = ref(false)
const keyword = ref('')
const selectedIds = ref<number[]>([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const createDialogVisible = ref(false)
const createForm = reactive({
  name: '',
  description: '',
})

onMounted(() => fetchData())

function handleCreate() {
  createForm.name = ''
  createForm.description = ''
  createDialogVisible.value = true
}

async function handleCreateConfirm() {
  if (!createForm.name.trim()) {
    ElMessage.warning('请输入工作流名称')
    return
  }

  creating.value = true
  try {
    const res = await createWorkflow({
      name: createForm.name.trim(),
      description: createForm.description.trim() || undefined,
    })
    createDialogVisible.value = false
    ElMessage.success('工作流已创建')
    await fetchData()
    router.push(`/workflow/${res.data.id}/edit`)
  } catch (e: any) {
    ElMessage.error(e.message || '创建失败')
  } finally {
    creating.value = false
  }
}

async function fetchData() {
  loading.value = true
  try {
    const res = await listWorkflowsPage(keyword.value || undefined, page.value, pageSize.value)
    workflows.value = res.data.data
    total.value = res.data.total
  } catch (e: any) {
    ElMessage.error(e.message || '加载工作流列表失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  fetchData()
}

function onSelectionChange(rows: any[]) {
  selectedIds.value = rows.map(r => r.id)
}

function handlePageChange() {
  fetchData()
}

async function handleBatchDelete() {
  const count = selectedIds.value.length
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${count} 个工作流吗？`, '提示')
    await deleteWorkflowBatch(selectedIds.value)
    ElMessage.success(`成功删除 ${count} 个工作流`)
    selectedIds.value = []
    await fetchData()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '批量删除失败')
    }
  }
}

async function handleRun(row: WorkflowDefinition) {
  try {
    await ElMessageBox.confirm(`确定运行工作流 "${row.name}" 吗？`, '确认运行')
    await runWorkflow(row.id)
    ElMessage.success('工作流已触发运行')
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '运行失败')
    }
  }
}

async function handlePublish(row: WorkflowDefinition) {
  try {
    await ElMessageBox.confirm(`确定发布工作流 "${row.name}" 吗？发布后将不可编辑。`, '确认发布')
    await publishWorkflow(row.id)
    ElMessage.success('发布成功')
    await fetchData()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '发布失败')
    }
  }
}

async function handleUnpublish(row: WorkflowDefinition) {
  try {
    await ElMessageBox.confirm(`确定下架工作流 "${row.name}" 吗？下架后恢复为草稿状态。`, '确认下架')
    await unpublishWorkflow(row.id)
    ElMessage.success('下架成功')
    await fetchData()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '下架失败')
    }
  }
}

async function handleDelete(row: WorkflowDefinition) {
  try {
    await ElMessageBox.confirm(`确定删除工作流 "${row.name}" 吗？`, '提示')
    await deleteWorkflow(row.id)
    ElMessage.success('删除成功')
    await fetchData()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '删除失败')
    }
  }
}
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}
</style>
