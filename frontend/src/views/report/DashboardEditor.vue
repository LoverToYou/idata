<template>
  <Layout>
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>{{ isEditing ? '编辑看板布局' : '新建看板' }}</span>
          <div class="header-actions">
            <el-input v-model="info.name" placeholder="看板名称" size="small" style="width: 160px" />
            <el-tree-select
              v-model="info.folderId"
              :data="folderOptions"
              check-strictly
              clearable
              default-expand-all
              size="small"
              placeholder="未分组"
              style="width: 150px"
            />
            <el-input-number
              v-model="info.refreshInterval"
              :min="0"
              :max="86400"
              size="small"
              controls-position="right"
              style="width: 120px"
            />
            <span class="hint-inline">秒自动刷新</span>
            <el-switch v-model="fitScale" size="small" active-text="等比缩放" />
            <el-button size="small" @click="router.push('/dashboards')">返回</el-button>
            <el-button size="small" type="primary" :loading="saving" @click="handleSave">保存布局</el-button>
          </div>
        </div>
      </template>

      <el-row :gutter="16">
        <el-col :span="18">
          <div class="grid-toolbar">
            拖动卡片移动位置 · 拖拽右下角调整大小 · 双击标题重命名（栅格 12 列）
          </div>
          <div class="grid-wrapper">
            <div ref="gridEl" class="grid" :style="{ height: gridHeight + 'px' }">
              <div
                v-for="(card, idx) in cards"
                :key="idx"
                class="grid-item"
                :class="{ dragging: draggingIndex === idx }"
                :style="cardStyle(card)"
                @mousedown="onCardMouseDown($event, card, idx)"
              >
                <div class="grid-item-header">
                  <span class="drag-handle">⣿</span>
                  <span class="item-title" @dblclick.stop="renameCard(card)">{{ card.title || card.reportName }}</span>
                  <span v-if="card.alertConfig" class="item-alert" title="已配置阈值告警">⚠</span>
                  <el-button link size="small" title="阈值告警设置" @mousedown.stop @click.stop="openAlert(card)">⚙</el-button>
                  <el-button link type="danger" size="small" @mousedown.stop @click.stop="removeCard(idx)">×</el-button>
                </div>
                <div class="grid-item-body">
                  <ReportRender
                    :chartType="card.chartType"
                    :columns="dataFor(card).columns"
                    :rows="dataFor(card).rows"
                    :chartConfig="card.chartConfig"
                    height="100%"
                  />
                </div>
                <div class="resize-handle" @mousedown.stop="onResizeMouseDown($event, card, idx)">⤡</div>
              </div>
            </div>
            <div v-if="!cards.length" class="grid-empty">从右侧「报表库」添加卡片开始搭建看板</div>
          </div>
        </el-col>

        <el-col :span="6">
          <el-card shadow="never" class="report-lib">
            <template #header><span>报表库</span></template>
            <div v-for="r in reports" :key="r.id" class="lib-item">
              <span class="lib-name" :title="r.name">{{ r.name }}</span>
              <el-button size="small" type="primary" plain @click="addCard(r)">添加</el-button>
            </div>
            <el-empty v-if="!reports.length" description="暂无可添加的报表" :image-size="60" />
          </el-card>

          <el-card shadow="never" class="filter-panel">
            <template #header>
              <div class="lib-header">
                <span>全局筛选</span>
                <el-button size="small" text type="primary" @click="addFilter">+ 添加</el-button>
              </div>
            </template>
            <div v-for="(f, i) in filters" :key="i" class="filter-item">
              <el-input v-model="f.key" size="small" placeholder="参数名(英文,如 biz_date)" />
              <el-input v-model="f.label" size="small" placeholder="显示名称" />
              <el-select v-model="f.type" size="small">
                <el-option label="文本输入" value="text" />
                <el-option label="下拉选择" value="select" />
                <el-option label="日期" value="date" />
                <el-option label="日期范围" value="daterange" />
              </el-select>
              <el-input v-model="f.defaultValue" size="small" placeholder="默认值" />
              <el-input v-if="f.type === 'select'" v-model="f.options" size="small" placeholder="选项，逗号分隔" />
              <el-button size="small" type="danger" text @click="filters.splice(i, 1)">删除</el-button>
            </div>
            <div class="hint-block">
              参数名对应报表 SQL 中的 <code v-pre>${参数名}</code>；日期范围会生成
              <code>_start</code> / <code>_end</code> 两个参数。
            </div>
          </el-card>
        </el-col>
      </el-row>
    </el-card>

    <el-dialog v-model="alertVisible" title="阈值告警设置" width="460px">
      <el-form label-width="88px">
        <el-form-item label="告警字段">
          <el-select
            v-model="alertForm.field"
            filterable
            allow-create
            default-first-option
            placeholder="选择或输入数值字段"
            style="width: 100%"
          >
            <el-option v-for="c in alertColumns" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="触发条件">
          <el-select v-model="alertForm.operator" style="width: 160px">
            <el-option v-for="o in ALERT_OPERATORS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
          <el-input-number v-model="alertForm.value as number" :controls="false" style="width: 120px; margin-left: 8px" />
        </el-form-item>
        <el-form-item label="告警级别">
          <el-radio-group v-model="alertForm.level">
            <el-radio-button value="warn">提醒</el-radio-button>
            <el-radio-button value="error">严重</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <div class="hint-block">任意数据行满足条件时，大屏卡片会显示告警徽标并闪烁提示。</div>
      </el-form>
      <template #footer>
        <el-button @click="clearAlert">清除告警</el-button>
        <el-button type="primary" @click="saveAlert">确定</el-button>
      </template>
    </el-dialog>
  </Layout>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import Layout from '@/components/common/Layout.vue'
import ReportRender from '@/components/chart/ReportRender.vue'
import { ALERT_OPERATORS, parseAlertConfig, type AlertConfig } from '@/utils/reportChart'
import { buildFolderTree, listFolders, type FolderItem } from '@/api/folder'
import { listReports, type ReportItem } from '@/api/report'
import {
  createDashboard,
  getDashboard,
  getDashboardData,
  moveDashboard,
  updateDashboard,
  type DashboardCardData,
  type DashboardFilter,
  type DashboardItem,
} from '@/api/dashboard'

const GRID_COLS = 12
const ROW_H = 56
const GAP = 8

const route = useRoute()
const router = useRouter()

const isEditing = computed(() => !!route.params.id)
const reports = ref<ReportItem[]>([])
const cards = ref<DashboardItem[]>([])
const dataMap = ref<Record<number, DashboardCardData>>({})
const saving = ref(false)
const draggingIndex = ref<number | null>(null)

const info = reactive({ name: '', description: '', refreshInterval: 60, folderId: null as number | null })
const folders = ref<FolderItem[]>([])
const folderOptions = computed(() => buildFolderTree(folders.value))
const filters = ref<DashboardFilter[]>([])
const fitScale = ref(true)

function addFilter() {
  filters.value.push({ key: '', label: '', type: 'text', defaultValue: '', options: '' })
}

const gridEl = ref<HTMLDivElement>()
const gridWidth = ref(1000)
let observer: ResizeObserver | null = null

const colWidth = computed(() => Math.max(20, (gridWidth.value - GAP * (GRID_COLS + 1)) / GRID_COLS))

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

const gridHeight = computed(() => {
  const maxY = cards.value.reduce((m, c) => Math.max(m, c.posY + c.height), 0)
  return GAP + Math.max(maxY, 8) * (ROW_H + GAP)
})

function dataFor(card: DashboardItem) {
  return dataMap.value[card.reportId] || { columns: [], rows: [] }
}

function measure() {
  if (gridEl.value) gridWidth.value = gridEl.value.clientWidth
}

// ---- 拖拽 / 缩放 ----
let drag: {
  card: DashboardItem
  mode: 'move' | 'resize'
  startX: number
  startY: number
  start: { posX: number; posY: number; width: number; height: number }
} | null = null

function startDrag(e: MouseEvent, card: DashboardItem, idx: number, mode: 'move' | 'resize') {
  e.preventDefault()
  draggingIndex.value = idx
  drag = {
    card,
    mode,
    startX: e.clientX,
    startY: e.clientY,
    start: { posX: card.posX, posY: card.posY, width: card.width, height: card.height },
  }
  document.addEventListener('mousemove', onMouseMove)
  document.addEventListener('mouseup', onMouseUp)
}

function onCardMouseDown(e: MouseEvent, card: DashboardItem, idx: number) {
  const target = e.target as HTMLElement
  if (target.closest('.resize-handle') || target.closest('button')) return
  startDrag(e, card, idx, 'move')
}

function onResizeMouseDown(e: MouseEvent, card: DashboardItem, idx: number) {
  startDrag(e, card, idx, 'resize')
}

function onMouseMove(e: MouseEvent) {
  if (!drag) return
  const unitW = colWidth.value + GAP
  const unitH = ROW_H + GAP
  const dCols = Math.round((e.clientX - drag.startX) / unitW)
  const dRows = Math.round((e.clientY - drag.startY) / unitH)
  const c = drag.card
  if (drag.mode === 'move') {
    c.posX = Math.min(GRID_COLS - c.width, Math.max(0, drag.start.posX + dCols))
    c.posY = Math.max(0, drag.start.posY + dRows)
  } else {
    c.width = Math.min(GRID_COLS - c.posX, Math.max(2, drag.start.width + dCols))
    c.height = Math.max(2, drag.start.height + dRows)
  }
}

function onMouseUp() {
  drag = null
  draggingIndex.value = null
  document.removeEventListener('mousemove', onMouseMove)
  document.removeEventListener('mouseup', onMouseUp)
}

// ---- 卡片操作 ----
function addCard(report: ReportItem) {
  const bottom = cards.value.reduce((m, c) => Math.max(m, c.posY + c.height), 0)
  cards.value.push({
    reportId: report.id as number,
    reportName: report.name,
    chartType: report.chartType,
    chartConfig: report.chartConfig,
    title: report.name,
    posX: 0,
    posY: bottom,
    width: 6,
    height: 5,
  })
}

function removeCard(idx: number) {
  cards.value.splice(idx, 1)
}

async function renameCard(card: DashboardItem) {
  try {
    const { value } = await ElMessageBox.prompt('卡片标题', '重命名', {
      inputValue: card.title || card.reportName || '',
    })
    card.title = value
  } catch {
    /* cancelled */
  }
}

// ---- 阈值告警 ----
const alertVisible = ref(false)
const alertCardIdx = ref<number | null>(null)
const alertForm = reactive<AlertConfig>({ field: '', operator: '>', value: 0, level: 'warn' })

const alertColumns = computed(() => {
  const idx = alertCardIdx.value
  if (idx === null) return []
  const card = cards.value[idx]
  if (!card) return []
  return dataMap.value[card.reportId]?.columns || []
})

function openAlert(card: DashboardItem) {
  alertCardIdx.value = cards.value.indexOf(card)
  const cfg = parseAlertConfig(card.alertConfig)
  alertForm.field = cfg.field || alertColumns.value[0] || ''
  alertForm.operator = cfg.operator || '>'
  alertForm.value = cfg.value ?? 0
  alertForm.level = cfg.level || 'warn'
  alertVisible.value = true
}

function saveAlert() {
  const idx = alertCardIdx.value
  if (idx === null) return
  if (!alertForm.field) {
    ElMessage.warning('请选择告警字段')
    return
  }
  cards.value[idx].alertConfig = JSON.stringify({
    field: alertForm.field,
    operator: alertForm.operator,
    value: alertForm.value,
    level: alertForm.level,
  })
  alertVisible.value = false
}

function clearAlert() {
  const idx = alertCardIdx.value
  if (idx !== null) cards.value[idx].alertConfig = ''
  alertVisible.value = false
}

// ---- 数据 ----
async function loadReports() {
  try {
    const res = await listReports()
    reports.value = res.data || []
  } catch {
    /* ignore */
  }
}

async function loadFolders() {
  try {
    const res = await listFolders('DASHBOARD')
    folders.value = res.data || []
  } catch {
    /* ignore */
  }
}

async function loadDashboard(id: number) {
  const res = await getDashboard(id)
  const d = res.data
  info.name = d.name
  info.description = d.description || ''
  info.refreshInterval = d.refreshInterval ?? 60
  info.folderId = d.folderId ?? null
  fitScale.value = d.fitScale ?? true
  try {
    filters.value = d.filters ? JSON.parse(d.filters) : []
  } catch {
    filters.value = []
  }
  cards.value = (d.items || []).map((it) => ({ ...it }))
}

async function loadData(id: number) {
  try {
    const res = await getDashboardData(id)
    const map: Record<number, DashboardCardData> = {}
    ;(res.data?.cards || []).forEach((c) => (map[c.reportId] = c))
    dataMap.value = map
  } catch {
    /* ignore */
  }
}

async function handleSave() {
  if (!info.name.trim()) {
    ElMessage.warning('请输入看板名称')
    return
  }
  const badFilter = filters.value.find((f) => f.key && !/^\w+$/.test(f.key))
  if (badFilter) {
    ElMessage.warning(`筛选参数名只能包含字母/数字/下划线：${badFilter.key}`)
    return
  }
  saving.value = true
  try {
    const payload = {
      name: info.name,
      description: info.description,
      refreshInterval: info.refreshInterval,
      fitScale: fitScale.value,
      filters: JSON.stringify(filters.value.filter((f) => f.key)),
      folderId: info.folderId,
      items: cards.value.map((c) => ({
        reportId: c.reportId,
        title: c.title,
        posX: c.posX,
        posY: c.posY,
        width: c.width,
        height: c.height,
        alertConfig: c.alertConfig || null,
      })),
    }
    if (isEditing.value) {
      await updateDashboard({ ...payload, id: Number(route.params.id) })
      await moveDashboard(Number(route.params.id), info.folderId ?? null)
      ElMessage.success('看板已保存')
    } else {
      await createDashboard(payload)
      ElMessage.success('看板已创建')
    }
    router.push('/dashboards')
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await loadReports()
  await loadFolders()
  if (isEditing.value) {
    const id = Number(route.params.id)
    try {
      await loadDashboard(id)
      await loadData(id)
    } catch (e: any) {
      ElMessage.error(e.message || '加载看板失败')
    }
  }
  measure()
  observer = new ResizeObserver(measure)
  if (gridEl.value) observer.observe(gridEl.value)
  window.addEventListener('resize', measure)
})

onBeforeUnmount(() => {
  observer?.disconnect()
  window.removeEventListener('resize', measure)
  document.removeEventListener('mousemove', onMouseMove)
  document.removeEventListener('mouseup', onMouseUp)
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
  gap: 8px;
}
.grid-toolbar {
  font-size: 12px;
  color: var(--text-sub);
  margin-bottom: 8px;
}
.grid-wrapper {
  position: relative;
  border: 1px dashed var(--border);
  border-radius: 4px;
  background: var(--bg-muted);
  overflow: hidden;
}
.grid {
  position: relative;
  width: 100%;
}
.grid-item {
  position: absolute;
  background: #fff;
  border: 1px solid var(--border);
  border-radius: 4px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  overflow: hidden;
  cursor: move;
  user-select: none;
}
.grid-item.dragging {
  border-color: var(--primary);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.3);
  z-index: 10;
}
.grid-item-header {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 30px;
  padding: 0 6px;
  background: var(--bg-muted);
  border-bottom: 1px solid var(--border-light);
  font-size: 12px;
  color: var(--text-title);
}
.drag-handle {
  color: var(--text-faint);
  cursor: move;
}
.item-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
}
.item-alert {
  color: var(--warning);
  font-size: 13px;
}
.grid-item-body {
  height: calc(100% - 30px);
  pointer-events: none;
  padding: 4px;
}
.resize-handle {
  position: absolute;
  right: 0;
  bottom: 0;
  width: 16px;
  height: 16px;
  cursor: nwse-resize;
  color: var(--text-sub);
  font-size: 12px;
  text-align: center;
  line-height: 16px;
  background: rgba(64, 158, 255, 0.12);
}
.grid-empty {
  position: absolute;
  left: 0;
  right: 0;
  top: 40px;
  text-align: center;
  color: var(--text-sub);
  font-size: 13px;
}
.report-lib {
  max-height: 640px;
  overflow: auto;
}
.lib-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 0;
  border-bottom: 1px dashed var(--border-light);
}
.lib-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
}
.hint-inline {
  font-size: 12px;
  color: var(--text-sub);
}
.filter-panel {
  margin-top: 16px;
}
.lib-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.filter-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 8px 0;
  border-bottom: 1px dashed var(--border-light);
}
</style>
