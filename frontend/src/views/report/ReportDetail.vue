<template>
  <Layout>
    <el-card shadow="hover" v-loading="loading">
      <template #header>
        <div class="card-header">
          <div class="title-area">
            <span class="report-title">{{ report?.name || '报表' }}</span>
            <el-tag size="small" effect="plain">{{ chartLabel(report?.chartType) }}</el-tag>
            <el-tag size="small" type="info" effect="plain">
              {{ report?.datasourceName || `数据源 #${report?.datasourceId ?? '-'}` }}
            </el-tag>
          </div>
          <div>
            <el-switch
              v-if="(report?.refreshInterval || 0) > 0"
              v-model="autoRefresh"
              active-text="自动刷新"
              style="margin-right: 12px"
            />
            <el-button :loading="running" @click="runNow">刷新</el-button>
            <el-button :disabled="!result" @click="handleExport">导出 CSV</el-button>
            <el-button type="primary" plain @click="router.push(`/report/${report?.id}/edit`)">编辑</el-button>
            <el-button @click="router.push('/report')">返回</el-button>
          </div>
        </div>
      </template>

      <div v-if="report?.description" class="report-desc">{{ report.description }}</div>
      <div class="meta-line">
        <span v-if="result">共 {{ result.rows.length }} 行 · 耗时 {{ result.elapsedMs }} ms</span>
        <span v-if="lastRunAt"> · 最近刷新 {{ lastRunAt }}</span>
        <span v-if="autoRefresh && countdown > 0"> · {{ countdown }}s 后自动刷新</span>
      </div>

      <EChart v-if="chartOption" :option="chartOption" height="420px" @chart-click="handleDrill" />
      <el-empty v-if="!result && !loading" description="暂无数据" />

      <el-table v-if="result" :data="result.rows" stripe border height="420" style="margin-top: 12px">
        <el-table-column
          v-for="c in result.columns"
          :key="c"
          :prop="c"
          :label="c"
          min-width="140"
          show-overflow-tooltip
        />
      </el-table>

      <el-collapse v-if="report" style="margin-top: 12px">
        <el-collapse-item title="查询 SQL" name="sql">
          <pre class="sql-pre">{{ report.sqlContent }}</pre>
        </el-collapse-item>
        <el-collapse-item title="图表配置" name="config">
          <pre class="sql-pre">{{ report.chartConfig || '{}' }}</pre>
        </el-collapse-item>
      </el-collapse>
    </el-card>

    <DetailDrawer
      v-model="drillVisible"
      :title="drillTitle"
      :columns="drillColumns"
      :rows="drillRows"
      :filter-desc="drillDesc"
    />
  </Layout>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import Layout from '@/components/common/Layout.vue'
import EChart from '@/components/chart/EChart.vue'
import DetailDrawer from '@/components/chart/DetailDrawer.vue'
import { getReport, runReport, type ReportItem, type SqlResult } from '@/api/report'
import { buildChartOption, exportCsv, filterDrillRows, parseChartConfig } from '@/utils/reportChart'

const route = useRoute()
const router = useRouter()

const report = ref<ReportItem | null>(null)
const result = ref<SqlResult | null>(null)
const loading = ref(false)
const running = ref(false)
const autoRefresh = ref(true)
const countdown = ref(0)
const lastRunAt = ref('')

let refreshTimer: ReturnType<typeof setInterval> | null = null

const chartOption = computed(() => {
  if (!report.value || !result.value) return null
  return buildChartOption(report.value.chartType, result.value.columns, result.value.rows, parseChartConfig(report.value.chartConfig))
})

function chartLabel(type?: string) {
  return { TABLE: '表格', LINE: '折线图', BAR: '柱状图', PIE: '饼图' }[(type || 'TABLE').toUpperCase()] || '表格'
}

async function loadReport() {
  loading.value = true
  try {
    const res = await getReport(Number(route.params.id))
    report.value = res.data
  } catch (e: any) {
    ElMessage.error(e.message || '加载报表失败')
  } finally {
    loading.value = false
  }
}

async function runNow() {
  if (!report.value?.id) return
  running.value = true
  try {
    const res = await runReport(report.value.id)
    result.value = res.data
    lastRunAt.value = new Date().toLocaleTimeString()
  } catch (e: any) {
    ElMessage.error(e.message || '查询失败')
  } finally {
    running.value = false
  }
}

function startAutoRefresh() {
  stopAutoRefresh()
  const interval = report.value?.refreshInterval || 0
  if (interval <= 0 || !autoRefresh.value) return
  countdown.value = interval
  refreshTimer = setInterval(() => {
    if (!autoRefresh.value) {
      countdown.value = interval
      return
    }
    countdown.value -= 1
    if (countdown.value <= 0) {
      countdown.value = interval
      runNow()
    }
  }, 1000)
}

function stopAutoRefresh() {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}

function handleExport() {
  if (!result.value || !report.value) return
  exportCsv(report.value.name, result.value.columns, result.value.rows)
}

// ---- 图表点击下钻 ----
const drillVisible = ref(false)
const drillTitle = ref('数据明细')
const drillColumns = ref<string[]>([])
const drillRows = ref<Record<string, any>[]>([])
const drillDesc = ref('')

function handleDrill(point: { name?: string; seriesName?: string }) {
  if (!result.value || !report.value?.id) return
  const cfg = parseChartConfig(report.value.chartConfig)
  const rows = filterDrillRows(result.value.columns, result.value.rows, cfg, point)
  drillTitle.value = `${report.value.name} - 明细`
  drillColumns.value = result.value.columns
  drillRows.value = rows
  const parts: string[] = []
  if (point.name !== undefined) parts.push(`分类：${point.name}`)
  if (point.seriesName) parts.push(`分组：${point.seriesName}`)
  drillDesc.value = parts.join(' / ')
  drillVisible.value = true
}

onMounted(async () => {
  await loadReport()
  await runNow()
  startAutoRefresh()
})

onBeforeUnmount(stopAutoRefresh)
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.title-area {
  display: flex;
  align-items: center;
  gap: 8px;
}
.report-title {
  font-size: 16px;
  font-weight: 600;
}
.report-desc {
  color: var(--text-body);
  font-size: 13px;
  margin-bottom: 8px;
}
.meta-line {
  color: var(--text-sub);
  font-size: 12px;
  margin-bottom: 12px;
}
.sql-pre {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
