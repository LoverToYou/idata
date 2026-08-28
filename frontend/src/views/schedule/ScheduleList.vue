<template>
  <Layout>
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>定时调度</span>
          <div class="header-actions">
            <el-button type="primary" @click="openCreateDialog">
              <el-icon><Plus /></el-icon> 新建调度
            </el-button>
          </div>
        </div>
      </template>

      <el-table :data="schedules" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="workflowName" label="工作流" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.workflowName || `工作流 #${row.workflowId}` }}
          </template>
        </el-table-column>
        <el-table-column prop="cronExpression" label="Cron 表达式" width="180">
          <template #default="{ row }">
            <el-tag effect="plain" type="info">{{ row.cronExpression }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="启用" width="90">
          <template #default="{ row }">
            <el-switch
              :model-value="row.enabled"
              :loading="togglingId === row.id"
              @change="(val: boolean) => handleToggle(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="170">
          <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="success" plain :loading="triggeringId === row.id" @click="handleTrigger(row)">
              立即执行
            </el-button>
            <el-button size="small" @click="openEditDialog(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- Create/Edit Dialog -->
    <el-dialog v-model="dialogVisible" :title="isEditing ? '编辑调度' : '新建调度'" width="520px" :close-on-click-modal="false">
      <el-form :model="form" label-position="top" ref="formRef" :rules="formRules">
        <el-form-item label="工作流" prop="workflowId">
          <el-select v-model="form.workflowId" placeholder="选择要调度的工作流" style="width: 100%">
            <el-option
              v-for="wf in workflows"
              :key="wf.id"
              :label="wf.name"
              :value="wf.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="Cron 表达式" prop="cronExpression">
          <el-input v-model="form.cronExpression" placeholder="例：0 0 2 * * ?" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
        <div class="cron-hint">
          Quartz 6 段 cron：秒 分 时 日 月 周。例如
          <code>0 0 2 * * ?</code> 每天凌晨 2 点、
          <code>*/5 * * * * ?</code> 每 5 秒（测试用）。
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </Layout>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import Layout from '@/components/common/Layout.vue'
import {
  listWorkflows,
  listSchedules,
  createSchedule,
  updateSchedule,
  deleteSchedule,
  toggleSchedule,
  triggerSchedule,
} from '@/api/workflow'
import type { WorkflowDefinition } from '@/types'

const schedules = ref<any[]>([])
const workflows = ref<WorkflowDefinition[]>([])
const loading = ref(false)
const saving = ref(false)
const togglingId = ref<number | null>(null)
const triggeringId = ref<number | null>(null)
const dialogVisible = ref(false)
const isEditing = ref(false)
const formRef = ref()
const currentId = ref<number | null>(null)

const form = ref({
  workflowId: null as number | null,
  cronExpression: '',
  enabled: true,
})

const formRules = {
  workflowId: [{ required: true, message: '请选择工作流', trigger: 'change' }],
  cronExpression: [{ required: true, message: '请输入 Cron 表达式', trigger: 'blur' }],
}

function formatTime(t: string) {
  if (!t) return ''
  return t.slice(0, 16).replace('T', ' ')
}

async function loadData() {
  loading.value = true
  try {
    const [scheduleRes, workflowRes] = await Promise.all([listSchedules(), listWorkflows()])
    schedules.value = scheduleRes.data
    workflows.value = workflowRes.data
  } catch { /* ignore */ }
  finally { loading.value = false }
}

function openCreateDialog() {
  isEditing.value = false
  currentId.value = null
  form.value = { workflowId: null, cronExpression: '', enabled: true }
  dialogVisible.value = true
}

function openEditDialog(row: any) {
  isEditing.value = true
  currentId.value = row.id
  form.value = {
    workflowId: row.workflowId,
    cronExpression: row.cronExpression,
    enabled: row.enabled,
  }
  dialogVisible.value = true
}

async function handleSave() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  saving.value = true
  try {
    const payload = { ...form.value, workflowId: form.value.workflowId as number }
    if (isEditing.value && currentId.value) {
      await updateSchedule({ ...payload, id: currentId.value })
      ElMessage.success('调度已更新')
    } else {
      await createSchedule(payload)
      ElMessage.success('调度已创建')
    }
    dialogVisible.value = false
    await loadData()
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm(
      `确定删除调度「${row.workflowName || `工作流 #${row.workflowId}`}」(${row.cronExpression})？`,
      '确认删除',
      { type: 'warning' }
    )
    await deleteSchedule(row.id)
    ElMessage.success('已删除')
    await loadData()
  } catch { /* cancelled */ }
}

async function handleToggle(row: any, enabled: boolean) {
  togglingId.value = row.id
  try {
    await toggleSchedule(row.id, enabled)
    ElMessage.success(enabled ? '已启用' : '已停用')
    await loadData()
  } catch (e: any) {
    ElMessage.error(e.message || '操作失败')
    await loadData()
  } finally {
    togglingId.value = null
  }
}

async function handleTrigger(row: any) {
  triggeringId.value = row.id
  try {
    await triggerSchedule(row.id)
    ElMessage.success('已触发执行，可在任务监控查看')
  } catch (e: any) {
    ElMessage.error(e.message || '触发失败')
  } finally {
    triggeringId.value = null
  }
}

onMounted(loadData)
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
.cron-hint {
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
}
.cron-hint code {
  background: #f4f4f5;
  padding: 1px 4px;
  border-radius: 3px;
  color: #409eff;
}
</style>
