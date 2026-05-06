<template>
  <Layout>
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>ETL 任务列表</span>
          <div class="header-actions">
            <el-input
              v-model="searchName"
              placeholder="搜索 ETL 任务名称"
              clearable
              style="width: 220px"
              @input="fetchData"
            />
            <el-button type="primary" @click="$router.push('/workflow/create')">
              <el-icon><Plus /></el-icon> 新建 ETL 任务
            </el-button>
          </div>
        </div>
      </template>

      <el-table :data="filteredList" stripe v-loading="loading" empty-text="暂无 ETL 任务">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="名称" min-width="160">
          <template #default="{ row }">
            <span class="workflow-name">{{ row.name }}</span>
          </template>
        </el-table-column>
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
    </el-card>
  </Layout>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import Layout from '@/components/common/Layout.vue'
import { useWorkflowStore } from '@/stores/workflow'
import { publishWorkflow, runWorkflow, unpublishWorkflow } from '@/api/workflow'
import type { WorkflowDefinition } from '@/types'

const store = useWorkflowStore()

const loading = ref(false)
const searchName = ref('')

const filteredList = computed(() => {
  const q = searchName.value.trim().toLowerCase()
  if (!q) return store.workflowList
  return store.workflowList.filter((w) => w.name.toLowerCase().includes(q))
})

onMounted(() => fetchData())

async function fetchData() {
  loading.value = true
  try {
    await store.fetchList()
  } catch (e: any) {
    ElMessage.error(e.message || '加载工作流列表失败')
  } finally {
    loading.value = false
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
    await store.deleteWorkflow(row.id)
    ElMessage.success('删除成功')
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

.workflow-name {
  font-weight: 500;
  color: #303133;
}
</style>
