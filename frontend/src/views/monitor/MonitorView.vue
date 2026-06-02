<template>
  <Layout>
    <!-- Stats Cards -->
    <div class="stats-row">
      <el-card shadow="hover" class="stat-card" v-for="stat in stats" :key="stat.label">
        <div class="stat-inner">
          <div class="stat-icon" :style="{ background: stat.bgColor }">
            <el-icon :size="24" :color="stat.color">
              <component :is="stat.icon" />
            </el-icon>
          </div>
          <div class="stat-info">
            <span class="stat-value">{{ stat.value }}</span>
            <span class="stat-label">{{ stat.label }}</span>
          </div>
        </div>
      </el-card>
    </div>

    <!-- Filters & Table -->
    <el-card shadow="hover" class="table-card">
      <template #header>
        <div class="card-header">
          <span>执行实例</span>
          <div class="header-actions">
            <el-input
              v-model="filters.name"
              placeholder="搜索工作流名称"
              clearable
              style="width: 200px"
              @input="fetchData"
            />
            <el-select
              v-model="filters.status"
              placeholder="状态"
              clearable
              style="width: 130px"
              @change="fetchData"
            >
              <el-option label="运行中" value="RUNNING" />
              <el-option label="成功" value="SUCCESS" />
              <el-option label="失败" value="FAILED" />
            </el-select>
            <el-date-picker
              v-model="filters.dateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              value-format="YYYY-MM-DD"
              style="width: 260px"
              @change="fetchData"
            />
            <el-button @click="fetchData" :icon="Refresh" :loading="loading" circle />
            <el-tag v-if="autoRefreshEnabled" type="success" effect="light" size="small">
              自动刷新 {{ autoRefreshInterval / 1000 }}s
            </el-tag>
          </div>
        </div>
      </template>

      <el-table
        :data="filteredInstances"
        stripe
        v-loading="loading"
        empty-text="暂无执行记录"
        @sort-change="onSortChange"
      >
        <el-table-column prop="id" label="ID" width="70" sortable="custom" />
        <el-table-column label="工作流名称" min-width="160">
          <template #default="{ row }">
            <span class="workflow-name">{{ row.workflowName || `工作流 #${row.workflowId}` }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="110" sortable="custom">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" effect="light">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="startedAt" label="开始时间" width="180" sortable="custom">
          <template #default="{ row }">
            {{ row.startedAt ? formatTime(row.startedAt) : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="finishedAt" label="结束时间" width="180" sortable="custom">
          <template #default="{ row }">
            {{ row.finishedAt ? formatTime(row.finishedAt) : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="100">
          <template #default="{ row }">
            {{ calcDuration(row) }}
          </template>
        </el-table-column>
        <el-table-column prop="triggeredBy" label="触发方式" width="110">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" :type="row.triggeredBy === 'MANUAL' ? 'primary' : 'info'">
              {{ row.triggeredBy === 'MANUAL' ? '手动' : row.triggeredBy === 'SCHEDULE' ? '定时' : row.triggeredBy || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="showDetail(row)">
              详情
            </el-button>
            <el-button size="small" type="primary" @click="showNodeLogs(row)">
              日志
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- Instance Detail Dialog -->
    <el-dialog
      v-model="detailVisible"
      title="实例详情"
      width="700px"
      :close-on-click-modal="false"
    >
      <template v-if="detailInstance">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="实例 ID" :span="1">{{ detailInstance.id }}</el-descriptions-item>
          <el-descriptions-item label="工作流 ID" :span="1">{{ detailInstance.workflowId }}</el-descriptions-item>
          <el-descriptions-item label="工作流名称" :span="2">
            {{ detailInstance.workflowName || `工作流 #${detailInstance.workflowId}` }}
          </el-descriptions-item>
          <el-descriptions-item label="状态" :span="1">
            <el-tag :type="statusType(detailInstance.status)" effect="light">
              {{ statusLabel(detailInstance.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="触发方式" :span="1">
            {{ detailInstance.triggeredBy === 'MANUAL' ? '手动' : detailInstance.triggeredBy === 'SCHEDULE' ? '定时' : detailInstance.triggeredBy || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="开始时间" :span="1">
            {{ detailInstance.startedAt ? formatTime(detailInstance.startedAt) : '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="结束时间" :span="1">
            {{ detailInstance.finishedAt ? formatTime(detailInstance.finishedAt) : '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="耗时" :span="1">
            {{ calcDuration(detailInstance) }}
          </el-descriptions-item>
          <el-descriptions-item label="创建时间" :span="1">
            {{ detailInstance.createdAt ? formatTime(detailInstance.createdAt) : '-' }}
          </el-descriptions-item>
          <el-descriptions-item v-if="detailInstance.errorMessage" label="错误信息" :span="2">
            <el-alert
              :title="detailInstance.errorMessage"
              type="error"
              show-icon
              :closable="false"
            />
          </el-descriptions-item>
        </el-descriptions>
      </template>
    </el-dialog>

    <!-- Node Logs Dialog -->
    <el-dialog
      v-model="logsVisible"
      title="节点执行日志"
      width="820px"
      top="4vh"
      :close-on-click-modal="false"
    >
      <template v-if="nodeLogs.length > 0">
        <div class="node-logs-summary">
          <el-tag size="small" type="success" effect="plain">
            {{ nodeLogs.filter(l => l.status === 'SUCCESS').length }} 成功
          </el-tag>
          <el-tag size="small" type="danger" effect="plain">
            {{ nodeLogs.filter(l => l.status === 'FAILED').length }} 失败
          </el-tag>
          <el-tag size="small" type="warning" effect="plain">
            {{ nodeLogs.filter(l => l.status === 'RUNNING').length }} 运行中
          </el-tag>
          <el-tag size="small" type="info" effect="plain">
            {{ nodeLogs.filter(l => l.status === 'WAITING').length }} 等待
          </el-tag>
        </div>
        <el-timeline>
          <el-timeline-item
            v-for="log in nodeLogs"
            :key="log.nodeId"
            :timestamp="log.startedAt ? formatTime(log.startedAt) : '等待中'"
            :type="log.status === 'SUCCESS' ? 'success' : log.status === 'FAILED' ? 'danger' : 'info'"
            placement="top"
          >
            <el-card shadow="never" class="node-log-card" :class="'status-' + (log.status || '').toLowerCase()">
              <div class="log-node-header">
                <span class="log-node-name">{{ log.nodeName || log.nodeId }}</span>
                <el-tag size="small" :type="log.status === 'SUCCESS' ? 'success' : log.status === 'FAILED' ? 'danger' : log.status === 'RUNNING' ? 'warning' : 'info'" effect="dark">
                  {{ statusLabel(log.status) }}
                </el-tag>
              </div>

              <el-descriptions :column="2" size="small" border class="log-descriptions">
                <el-descriptions-item label="节点 ID" :span="1">
                  <code class="node-id-code">{{ log.nodeId }}</code>
                </el-descriptions-item>
                <el-descriptions-item label="耗时" :span="1">
                  <span :class="calcNodeDurationClass(log)">{{ calcNodeDuration(log) }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="开始时间" :span="1">
                  {{ log.startedAt ? formatTime(log.startedAt) : '-' }}
                </el-descriptions-item>
                <el-descriptions-item label="结束时间" :span="1">
                  {{ log.finishedAt ? formatTime(log.finishedAt) : (log.status === 'RUNNING' ? '运行中...' : '-') }}
                </el-descriptions-item>
                <el-descriptions-item v-if="log.dataxPid" label="DataX PID" :span="1">
                  <code>{{ log.dataxPid }}</code>
                </el-descriptions-item>
                <el-descriptions-item v-if="log.logPath" label="日志路径" :span="1">
                  <code class="log-path">{{ log.logPath }}</code>
                </el-descriptions-item>
              </el-descriptions>

              <div v-if="log.errorMessage" class="log-error-section">
                <div class="log-error-label">错误信息</div>
                <pre class="log-error-content">{{ log.errorMessage }}</pre>
              </div>

              <div v-if="log.dataxJson" class="log-collapsible">
                <div class="log-collapse-toggle" @click="toggleDataxJson(log)">
                  <span class="log-collapse-arrow">{{ log._showDataxJson ? '▼' : '▶' }}</span>
                  <span class="log-collapse-label">DataX 配置 JSON</span>
                </div>
                <pre v-show="log._showDataxJson" class="log-datax-json">{{ log.dataxJson }}</pre>
              </div>

              <div v-if="log.outputLog" class="log-collapsible">
                <div class="log-collapse-toggle" @click="toggleOutputLog(log)">
                  <span class="log-collapse-arrow">{{ log._showOutputLog ? '▼' : '▶' }}</span>
                  <span class="log-collapse-label">执行输出</span>
                </div>
                <pre v-show="log._showOutputLog" class="log-output-content">{{ log.outputLog }}</pre>
              </div>
            </el-card>
          </el-timeline-item>
        </el-timeline>
      </template>
      <template v-else>
        <el-empty description="暂无节点日志" />
      </template>
    </el-dialog>
  </Layout>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import Layout from '@/components/common/Layout.vue'
import { listInstances, getInstanceNodeLogs } from '@/api/workflow'
import type { InstanceNodeLog } from '@/types'

// --- Stats ---
interface StatCard {
  label: string
  value: number | string
  icon: string
  color: string
  bgColor: string
}

const allInstances = ref<any[]>([])
const loading = ref(false)

const stats = computed<StatCard[]>(() => {
  const list = allInstances.value
  if (list.length === 0) {
    return [
      { label: '总工作流数', value: 0, icon: 'Document', color: '#409eff', bgColor: '#ecf5ff' },
      { label: '运行中实例', value: 0, icon: 'VideoPlay', color: '#e6a23c', bgColor: '#fdf6ec' },
      { label: '本周失败', value: 0, icon: 'CircleCloseFilled', color: '#f56c6c', bgColor: '#fef0f0' },
      { label: '平均耗时', value: '0s', icon: 'Timer', color: '#67c23a', bgColor: '#f0f9eb' },
    ]
  }

  const uniqueWorkflows = new Set(list.map((i: any) => i.workflowId)).size
  const running = list.filter((i: any) => i.status === 'RUNNING').length
  const now = Date.now()
  const weekAgo = now - 7 * 24 * 60 * 60 * 1000
  const failedThisWeek = list.filter(
    (i: any) => i.status === 'FAILED' && new Date(i.startedAt || i.createdAt).getTime() >= weekAgo,
  ).length

  // Calculate average duration for completed instances
  const completed = list.filter(
    (i: any) => (i.status === 'SUCCESS' || i.status === 'FAILED') && i.startedAt && i.finishedAt,
  )
  let avgDuration = ''
  if (completed.length > 0) {
    const totalMs = completed.reduce((sum: number, i: any) => {
      return sum + (new Date(i.finishedAt).getTime() - new Date(i.startedAt).getTime())
    }, 0)
    const avgMs = totalMs / completed.length
    avgDuration = formatDuration(avgMs)
  }

  return [
    { label: '总工作流数', value: uniqueWorkflows, icon: 'Document', color: '#409eff', bgColor: '#ecf5ff' },
    { label: '运行中实例', value: running, icon: 'VideoPlay', color: '#e6a23c', bgColor: '#fdf6ec' },
    { label: '本周失败', value: failedThisWeek, icon: 'CircleCloseFilled', color: '#f56c6c', bgColor: '#fef0f0' },
    { label: '平均耗时', value: avgDuration || '-', icon: 'Timer', color: '#67c23a', bgColor: '#f0f9eb' },
  ]
})

// --- Filters ---
const filters = reactive({
  name: '',
  status: '',
  dateRange: null as [string, string] | null,
})

const sortField = ref('')
const sortOrder = ref('')

const filteredInstances = computed(() => {
  let list = [...allInstances.value]

  // Filter by name
  const q = filters.name.trim().toLowerCase()
  if (q) {
    list = list.filter((i: any) =>
      (i.workflowName || '').toLowerCase().includes(q),
    )
  }

  // Filter by status
  if (filters.status) {
    list = list.filter((i: any) => i.status === filters.status)
  }

  // Filter by date range
  if (filters.dateRange && filters.dateRange[0] && filters.dateRange[1]) {
    const start = new Date(filters.dateRange[0]).getTime()
    const end = new Date(filters.dateRange[1]).getTime() + 86400000 // include end date
    list = list.filter((i: any) => {
      const t = new Date(i.startedAt || i.createdAt).getTime()
      return t >= start && t <= end
    })
  }

  // Sort
  if (sortField.value) {
    list.sort((a: any, b: any) => {
      const aVal = a[sortField.value]
      const bVal = b[sortField.value]
      if (aVal == null) return 1
      if (bVal == null) return -1
      const cmp = aVal < bVal ? -1 : aVal > bVal ? 1 : 0
      return sortOrder.value === 'descending' ? -cmp : cmp
    })
  }

  return list
})

function onSortChange({ prop, order }: { prop?: string; order?: string }) {
  sortField.value = prop || ''
  sortOrder.value = order || ''
}

// --- Auto Refresh ---
const autoRefreshEnabled = ref(false)
const autoRefreshInterval = ref(30000)
let refreshTimer: ReturnType<typeof setInterval> | null = null

function startAutoRefresh() {
  stopAutoRefresh()
  autoRefreshEnabled.value = true
  refreshTimer = setInterval(() => {
    fetchData(true)
  }, autoRefreshInterval.value)
}

function stopAutoRefresh() {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
  autoRefreshEnabled.value = false
}

// --- Detail ---
const detailVisible = ref(false)
const detailInstance = ref<any>(null)

function showDetail(row: any) {
  detailInstance.value = row
  detailVisible.value = true
}

// --- Node Logs ---
const logsVisible = ref(false)
const nodeLogs = ref<(InstanceNodeLog & { _showDataxJson?: boolean; _showOutputLog?: boolean })[]>([])

async function showNodeLogs(row: any) {
  logsVisible.value = true
  nodeLogs.value = []
  try {
    const res = await getInstanceNodeLogs(row.id)
    nodeLogs.value = res.data || []
  } catch (e: any) {
    ElMessage.error(e.message || '获取节点日志失败')
  }
}

// --- Data Fetching ---
async function fetchData(silent = false) {
  if (!silent) loading.value = true
  try {
    const res = await listInstances()
    allInstances.value = res.data || []
  } catch (e: any) {
    if (!silent) {
      ElMessage.error(e.message || '获取实例列表失败')
    }
  } finally {
    if (!silent) loading.value = false
  }
}

// --- Helpers ---
function statusType(status: string): string {
  if (status === 'RUNNING') return 'warning'
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  return 'info'
}

function statusLabel(status: string): string {
  if (status === 'RUNNING') return '运行中'
  if (status === 'SUCCESS') return '成功'
  if (status === 'FAILED') return '失败'
  return status || '-'
}

function formatTime(t: string): string {
  if (!t) return '-'
  try {
    const d = new Date(t)
    if (isNaN(d.getTime())) return t
    return d.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false,
    })
  } catch {
    return t
  }
}

function calcDuration(row: any): string {
  if (!row.startedAt) return '-'
  const start = new Date(row.startedAt).getTime()
  const end = row.finishedAt ? new Date(row.finishedAt).getTime() : Date.now()
  const diff = end - start
  if (diff < 0) return '-'
  return formatDuration(diff)
}

function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms}ms`
  const seconds = Math.floor(ms / 1000)
  if (seconds < 60) return `${seconds}s`
  const minutes = Math.floor(seconds / 60)
  const remainSec = seconds % 60
  if (minutes < 60) return `${minutes}m ${remainSec}s`
  const hours = Math.floor(minutes / 60)
  const remainMin = minutes % 60
  return `${hours}h ${remainMin}m ${remainSec}s`
}

function calcNodeDuration(log: InstanceNodeLog): string {
  if (!log.startedAt) return '-'
  if (log.status === 'WAITING') return '未开始'
  const start = new Date(log.startedAt).getTime()
  if (log.status === 'RUNNING' || !log.finishedAt) return '运行中...'
  const end = new Date(log.finishedAt).getTime()
  const diff = end - start
  if (diff < 0) return '-'
  return formatDuration(diff)
}

function calcNodeDurationClass(log: InstanceNodeLog): string {
  const d = calcNodeDuration(log)
  if (d === '未开始' || d === '运行中...' || d === '-') return ''
  // Highlight long-running nodes (>1s)
  const ms = parseInt(d)
  if (!isNaN(ms) && ms > 1000) return 'duration-long'
  // Check formatted durations
  if (d.includes('m') || d.includes('h')) return 'duration-long'
  return 'duration-short'
}

function toggleDataxJson(log: InstanceNodeLog & { _showDataxJson?: boolean }) {
  log._showDataxJson = !log._showDataxJson
}

function toggleOutputLog(log: InstanceNodeLog & { _showOutputLog?: boolean }) {
  log._showOutputLog = !log._showOutputLog
}

// --- Lifecycle ---
onMounted(() => {
  fetchData()
  startAutoRefresh()
})

onUnmounted(() => {
  stopAutoRefresh()
})
</script>

<style scoped>
.stats-row {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
}

.stat-card {
  flex: 1;
  min-width: 0;
}

.stat-card :deep(.el-card__body) {
  padding: 20px;
}

.stat-inner {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-icon {
  width: 52px;
  height: 52px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #303133;
  line-height: 1.2;
}

.stat-label {
  font-size: 13px;
  color: #909399;
}

.table-card {
  margin-top: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.workflow-name {
  font-weight: 500;
  color: #303133;
}

/* Log dialogs */
.node-logs-summary {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.node-log-card {
  border-left: 3px solid #e4e7ed;
  border-radius: 4px;
}

.node-log-card.status-success {
  border-left-color: #67c23a;
}

.node-log-card.status-failed {
  border-left-color: #f56c6c;
}

.node-log-card.status-running {
  border-left-color: #e6a23c;
}

.node-log-card.status-waiting {
  border-left-color: #909399;
}

.node-log-card :deep(.el-card__body) {
  padding: 12px;
}

.log-node-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.log-node-name {
  font-weight: 600;
  font-size: 14px;
  color: #303133;
}

.log-descriptions {
  margin-top: 0;
}

.log-descriptions :deep(.el-descriptions__label) {
  width: 90px;
  color: #909399;
  font-weight: 500;
}

.node-id-code {
  font-size: 12px;
  color: #909399;
}

.log-path {
  font-size: 12px;
  word-break: break-all;
}

.duration-short {
  color: #67c23a;
  font-weight: 500;
}

.duration-long {
  color: #e6a23c;
  font-weight: 500;
}

/* Collapsible sections for DataX JSON and execution output */
.log-collapsible {
  margin-top: 8px;
}

.log-collapse-toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
  background: #f5f7fa;
  user-select: none;
  transition: background 0.15s;
}

.log-collapse-toggle:hover {
  background: #e4e7ed;
}

.log-collapse-arrow {
  font-size: 10px;
  color: #909399;
  flex-shrink: 0;
}

.log-collapse-label {
  font-size: 12px;
  font-weight: 600;
  color: #606266;
}

.log-datax-json {
  margin: 0;
  margin-top: 4px;
  padding: 10px 12px;
  background: #1e1e1e;
  border-radius: 4px;
  font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
  font-size: 11px;
  line-height: 1.5;
  color: #d4d4d4;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 400px;
  overflow: auto;
}

.log-output-content {
  margin: 0;
  margin-top: 4px;
  padding: 10px 12px;
  background: #fdf6ec;
  border: 1px solid #faecd8;
  border-radius: 4px;
  font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
  font-size: 11px;
  line-height: 1.5;
  color: #606266;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 300px;
  overflow: auto;
}

.log-error-section {
  margin-top: 10px;
}

.log-error-label {
  font-size: 12px;
  font-weight: 500;
  color: #f56c6c;
  margin-bottom: 4px;
}

.log-error-content {
  margin: 0;
  padding: 10px 12px;
  background: #fef0f0;
  border: 1px solid #fde2e2;
  border-radius: 4px;
  font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.6;
  color: #f56c6c;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 200px;
  overflow: auto;
}
</style>
