<template>
  <Layout>
  <div class="board-outer" ref="outerEl">
    <div class="board" ref="boardEl" :style="canvasStyle">
      <div class="board-header">
        <div class="board-title">{{ dashboard?.name || '数据大屏' }}</div>

        <div v-if="filterDefs.length" class="board-filters">
          <div v-for="f in filterDefs" :key="f.key" class="filter-field">
            <span class="filter-label">{{ f.label || f.key }}</span>
            <el-input
              v-if="f.type === 'text'"
              v-model="filterValues[f.key]"
              size="small"
              clearable
              style="width: 140px"
              @change="refresh"
              @keyup.enter="refresh"
            />
            <el-select
              v-else-if="f.type === 'select'"
              v-model="filterValues[f.key]"
              size="small"
              clearable
              style="width: 150px"
              @change="refresh"
            >
              <el-option v-for="o in selectOptions(f)" :key="o" :label="o" :value="o" />
            </el-select>
            <el-date-picker
              v-else-if="f.type === 'date'"
              v-model="filterValues[f.key]"
              type="date"
              size="small"
              value-format="YYYY-MM-DD"
              style="width: 150px"
              @change="refresh"
            />
            <el-date-picker
              v-else
              v-model="filterValues[f.key]"
              type="daterange"
              size="small"
              value-format="YYYY-MM-DD"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              style="width: 250px"
              @change="refresh"
            />
          </div>
          <el-button size="small" type="primary" plain :loading="loading" @click="refresh">查询</el-button>
        </div>

        <div class="board-meta">
          <span v-if="lastRunAt" class="meta-text">最近刷新 {{ lastRunAt }}</span>
          <span v-if="autoRefresh && countdown > 0" class="meta-text">· {{ countdown }}s 后刷新</span>
          <el-switch v-model="autoRefresh" size="small" style="margin: 0 8px" />
          <el-button size="small" @click="refresh" :loading="loading">刷新</el-button>
          <el-button size="small" @click="toggleFullscreen">{{ isFullscreen ? '退出全屏' : '全屏' }}</el-button>
          <el-button size="small" :disabled="!cards.length" @click="exportPng" :loading="exporting">导出图片</el-button>
          <el-button size="small" @click="copyLink">复制链接</el-button>
          <el-button size="small" @click="router.push(`/dashboards/${id}/edit`)">编辑布局</el-button>
          <el-button size="small" @click="router.push('/dashboards')">返回</el-button>
        </div>
      </div>

      <div
        ref="gridEl"
        class="board-grid"
        :class="{ 'no-scroll': fitScale }"
        :style="{ height: gridHeight + 'px' }"
      >
        <div v-for="(card, idx) in cards" :key="idx" class="board-card" :style="cardStyle(card)">
          <div class="board-card-title">
            <span class="bar" />
            <span class="card-name">{{ card.title || card.reportName }}</span>
            <span
              v-if="alertFor(card).triggered"
              class="card-alert"
              :class="alertLevelClass(card)"
              :title="alertText(card)"
            >
              ⚠ {{ alertText(card) }}
            </span>
            <span class="drill-tip" title="点击图表可下钻查看明细">🔍 点击图表下钻</span>
          </div>
          <div class="board-card-body">
            <div v-if="dataFor(card).errorMessage" class="card-error">{{ dataFor(card).errorMessage }}</div>
            <ReportRender
              v-else
              theme="dark"
              :chartType="card.chartType"
              :columns="dataFor(card).columns"
              :rows="dataFor(card).rows"
              :chartConfig="card.chartConfig"
              height="100%"
              @point-click="(p: any) => handleDrill(card, p)"
            />
          </div>
        </div>
        <div v-if="!cards.length" class="board-empty">该看板还没有卡片，请先到「编辑布局」中添加报表卡片</div>
      </div>
    </div>

    <DetailDrawer
      v-model="drillVisible"
      :title="drillTitle"
      :columns="drillColumns"
      :rows="drillRows"
      :filter-desc="drillDesc"
    />
  </div>
  </Layout>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import html2canvas from 'html2canvas'
import ReportRender from '@/components/chart/ReportRender.vue'
import DetailDrawer from '@/components/chart/DetailDrawer.vue'
import Layout from '@/components/common/Layout.vue'
import {
  evaluateAlert,
  filterDrillRows,
  parseAlertConfig,
  parseChartConfig,
} from '@/utils/reportChart'
import {
  getDashboard,
  getDashboardData,
  type DashboardCardData,
  type DashboardFilter,
  type DashboardInfo,
  type DashboardItem,
} from '@/api/dashboard'

const GRID_COLS = 12
const ROW_H = 56
const GAP = 8
const DESIGN_W = 1920
const DESIGN_H = 1080

const route = useRoute()
const router = useRouter()

const id = Number(route.params.id)
const dashboard = ref<DashboardInfo | null>(null)
const cards = ref<DashboardItem[]>([])
const dataMap = ref<Record<number, DashboardCardData>>({})
const loading = ref(false)
const autoRefresh = ref(true)
const countdown = ref(0)
const lastRunAt = ref('')
const isFullscreen = ref(false)
const fitScale = ref(false)

const filterDefs = ref<DashboardFilter[]>([])
const filterValues = reactive<Record<string, any>>({})

const outerEl = ref<HTMLDivElement>()
const gridEl = ref<HTMLDivElement>()
const boardEl = ref<HTMLDivElement>()
const exporting = ref(false)
const capturing = ref(false)
const gridWidth = ref(1200)
const viewport = reactive({ w: window.innerWidth, h: window.innerHeight })
let observer: ResizeObserver | null = null
let timer: ReturnType<typeof setInterval> | null = null

const scale = computed(() => Math.min(viewport.w / DESIGN_W, viewport.h / DESIGN_H))

const canvasStyle = computed(() => {
  if (!fitScale.value) return {}
  const s = scale.value
  return {
    width: `${DESIGN_W}px`,
    height: `${DESIGN_H}px`,
    transform: capturing.value ? 'none' : `scale(${s})`,
    transformOrigin: 'top left',
    position: 'absolute',
    left: capturing.value ? '0px' : `${Math.max(0, (viewport.w - DESIGN_W * s) / 2)}px`,
    top: capturing.value ? '0px' : `${Math.max(0, (viewport.h - DESIGN_H * s) / 2)}px`,
  }
})

// ---- 告警 ----
function alertFor(card: DashboardItem) {
  const data = dataFor(card)
  return evaluateAlert(parseAlertConfig(card.alertConfig), data.columns, data.rows)
}

function alertLevelClass(card: DashboardItem) {
  return parseAlertConfig(card.alertConfig).level === 'error' ? 'alert-error' : 'alert-warn'
}

function alertText(card: DashboardItem) {
  const cfg = parseAlertConfig(card.alertConfig)
  const r = alertFor(card)
  const op = cfg.operator || '>'
  return `${cfg.field ?? ''} ${op} ${cfg.value ?? ''}（当前 ${r.value ?? '-'}）`
}

// ---- 下钻 ----
const drillVisible = ref(false)
const drillTitle = ref('数据明细')
const drillColumns = ref<string[]>([])
const drillRows = ref<Record<string, any>[]>([])
const drillDesc = ref('')

function handleDrill(card: DashboardItem, point: { name?: string; seriesName?: string }) {
  const data = dataFor(card)
  if (!data.rows?.length) return
  const cfg = parseChartConfig(card.chartConfig)
  const rows = filterDrillRows(data.columns, data.rows, cfg, point)
  drillTitle.value = `${card.title || card.reportName} - 明细`
  drillColumns.value = data.columns
  drillRows.value = rows
  const descParts: string[] = []
  if (point.name !== undefined) descParts.push(`分类：${point.name}`)
  if (point.seriesName) descParts.push(`分组：${point.seriesName}`)
  drillDesc.value = descParts.join(' / ')
  drillVisible.value = true
}

// ---- 导出图片 / 复制链接 ----
async function exportPng() {
  if (!boardEl.value) return
  exporting.value = true
  capturing.value = true
  await nextTick()
  await new Promise((r) => setTimeout(r, 400))
  try {
    const canvas = await html2canvas(boardEl.value, {
      backgroundColor: '#07111f',
      scale: window.devicePixelRatio > 1 ? 1.5 : 1,
      useCORS: true,
      logging: false,
    })
    const url = canvas.toDataURL('image/png')
    const a = document.createElement('a')
    a.href = url
    a.download = `${dashboard.value?.name || 'dashboard'}.png`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    ElMessage.success('图片已导出')
  } catch (e: any) {
    ElMessage.error(`导出失败：${e?.message || '未知错误'}`)
  } finally {
    capturing.value = false
    exporting.value = false
  }
}

async function copyLink() {
  const url = window.location.href
  try {
    await navigator.clipboard.writeText(url)
    ElMessage.success('大屏链接已复制')
  } catch {
    ElMessage.info(url)
  }
}

const colWidth = computed(() => {
  const width = fitScale.value ? DESIGN_W : gridWidth.value
  return Math.max(20, (width - GAP * (GRID_COLS + 1)) / GRID_COLS)
})

const gridHeight = computed(() => {
  const maxY = cards.value.reduce((m, c) => Math.max(m, c.posY + c.height), 0)
  const contentHeight = GAP + Math.max(maxY, 8) * (ROW_H + GAP)
  if (fitScale.value) return Math.max(contentHeight, DESIGN_H - 60)
  return Math.max(contentHeight, viewport.h - 60)
})

function cardStyle(card: DashboardItem) {
  const unitW = colWidth.value + GAP
  const unitH = ROW_H + GAP
  return {
    left: `${GAP + card.posX * unitW}px`,
    top: `${GAP + card.posY * unitH}px`,
    width: `${card.width * unitW - GAP}px`,
    height: `${card.height * unitH - GAP}px`,
  }
}

function dataFor(card: DashboardItem) {
  return dataMap.value[card.reportId] || { columns: [], rows: [] }
}

function selectOptions(f: DashboardFilter): string[] {
  return (f.options || '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
}

function measure() {
  const el = outerEl.value
  if (el) {
    viewport.w = el.clientWidth
    viewport.h = el.clientHeight
  } else {
    viewport.w = window.innerWidth
    viewport.h = window.innerHeight
  }
  if (gridEl.value) gridWidth.value = gridEl.value.clientWidth
}

function initFilters() {
  filterDefs.value.forEach((f) => {
    if (f.type === 'daterange') {
      const parts = (f.defaultValue || '').split(',').map((s) => s.trim())
      filterValues[f.key] = parts.length === 2 && parts[0] ? parts : null
    } else {
      filterValues[f.key] = f.defaultValue ?? (f.type === 'select' ? undefined : '')
    }
  })
}

/** 构造传给后端的筛选参数（日期范围拆成 _start / _end） */
function buildParams(): Record<string, string> {
  const params: Record<string, string> = {}
  filterDefs.value.forEach((f) => {
    const v = filterValues[f.key]
    if (f.type === 'daterange') {
      if (Array.isArray(v) && v[0]) params[`${f.key}_start`] = String(v[0])
      if (Array.isArray(v) && v[1]) params[`${f.key}_end`] = String(v[1])
    } else if (v !== undefined && v !== null && v !== '') {
      params[f.key] = String(v)
    }
  })
  return params
}

async function loadDashboard() {
  const res = await getDashboard(id)
  dashboard.value = res.data
  cards.value = res.data.items || []
  autoRefresh.value = (res.data.refreshInterval ?? 0) > 0
  fitScale.value = res.data.fitScale ?? false
  try {
    filterDefs.value = res.data.filters ? JSON.parse(res.data.filters) : []
  } catch {
    filterDefs.value = []
  }
  initFilters()
}

async function refresh() {
  loading.value = true
  try {
    const res = await getDashboardData(id, buildParams())
    const map: Record<number, DashboardCardData> = {}
    ;(res.data?.cards || []).forEach((c) => (map[c.reportId] = c))
    dataMap.value = map
    lastRunAt.value = new Date().toLocaleTimeString()
  } catch (e: any) {
    ElMessage.error(e.message || '取数失败')
  } finally {
    loading.value = false
  }
}

function startTimer() {
  stopTimer()
  const interval = dashboard.value?.refreshInterval || 0
  if (interval <= 0) return
  countdown.value = interval
  timer = setInterval(() => {
    if (!autoRefresh.value) {
      countdown.value = interval
      return
    }
    countdown.value -= 1
    if (countdown.value <= 0) {
      countdown.value = interval
      refresh()
    }
  }, 1000)
}

function stopTimer() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

async function toggleFullscreen() {
  try {
    if (document.fullscreenElement) {
      await document.exitFullscreen()
    } else {
      await outerEl.value?.requestFullscreen()
      setTimeout(measure, 300)
    }
  } catch {
    ElMessage.warning('当前浏览器不支持全屏')
  }
}

function onFullscreenChange() {
  isFullscreen.value = !!document.fullscreenElement
  measure()
}

onMounted(async () => {
  try {
    await loadDashboard()
    await refresh()
    startTimer()
  } catch (e: any) {
    ElMessage.error(e.message || '加载看板失败')
  }
  measure()
  observer = new ResizeObserver(measure)
  if (outerEl.value) observer.observe(outerEl.value)
  window.addEventListener('resize', measure)
  document.addEventListener('fullscreenchange', onFullscreenChange)
})

onBeforeUnmount(() => {
  stopTimer()
  observer?.disconnect()
  window.removeEventListener('resize', measure)
  document.removeEventListener('fullscreenchange', onFullscreenChange)
})
</script>

<style scoped>
.board-outer {
  position: relative;
  width: 100%;
  height: calc(100vh - 100px);
  min-height: 520px;
  overflow: hidden;
  border-radius: 6px;
  background: radial-gradient(1200px 600px at 20% 0%, #12304f 0%, #0a1729 45%, #060e1a 100%);
}
.board-outer:fullscreen {
  width: 100vw;
  height: 100vh;
  border-radius: 0;
}
.board {
  position: relative;
  height: 100%;
  display: flex;
  flex-direction: column;
  color: #e6f0ff;
}
.board-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  height: 60px;
  padding: 0 20px;
  border-bottom: 1px solid rgba(120, 180, 255, 0.18);
  background: linear-gradient(90deg, rgba(23, 78, 140, 0.55), rgba(10, 26, 46, 0.2));
}
.board-title {
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 2px;
  background: linear-gradient(90deg, #7fd4ff, #4d8dff);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  white-space: nowrap;
}
.board-filters {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.filter-field {
  display: flex;
  align-items: center;
  gap: 6px;
}
.filter-label {
  font-size: 12px;
  color: #8fb8e8;
  white-space: nowrap;
}
.board-meta {
  display: flex;
  align-items: center;
  gap: 6px;
}
.meta-text {
  font-size: 12px;
  color: #8fb8e8;
  white-space: nowrap;
}
.board-grid {
  position: relative;
  flex: 1;
  min-height: 0;
  width: 100%;
  overflow-y: auto;
  overflow-x: hidden;
}
.board-grid.no-scroll {
  overflow-y: auto;
}
.board-card {
  position: absolute;
  display: flex;
  flex-direction: column;
  border: 1px solid rgba(90, 160, 255, 0.25);
  border-radius: 6px;
  background: rgba(20, 44, 74, 0.45);
  box-shadow: inset 0 0 30px rgba(60, 140, 255, 0.08);
  overflow: hidden;
}
.board-card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 32px;
  padding: 0 10px;
  font-size: 13px;
  font-weight: 600;
  color: #cfe6ff;
  border-bottom: 1px solid rgba(90, 160, 255, 0.18);
  background: rgba(40, 90, 150, 0.25);
}
.board-card-title .bar {
  width: 3px;
  height: 14px;
  background: linear-gradient(180deg, #4d8dff, #7fd4ff);
  border-radius: 2px;
}
.card-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card-alert {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 3px;
  white-space: nowrap;
  animation: alert-pulse 1.6s ease-in-out infinite;
}
.card-alert.alert-warn {
  color: #ffd666;
  background: rgba(230, 162, 60, 0.18);
  border: 1px solid rgba(230, 162, 60, 0.5);
}
.card-alert.alert-error {
  color: #ff9c9c;
  background: rgba(245, 108, 108, 0.18);
  border: 1px solid rgba(245, 108, 108, 0.55);
}
@keyframes alert-pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.45;
  }
}
.drill-tip {
  font-size: 11px;
  color: #6f8fb3;
  white-space: nowrap;
  opacity: 0;
  transition: opacity 0.2s;
}
.board-card:hover .drill-tip {
  opacity: 1;
}
.board-card-body {
  flex: 1;
  min-height: 0;
  padding: 6px;
}
.card-error {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ff9c9c;
  font-size: 12px;
  padding: 8px;
  text-align: center;
}
.board-empty {
  text-align: center;
  color: #7c93ad;
  padding-top: 80px;
  font-size: 14px;
}
</style>
