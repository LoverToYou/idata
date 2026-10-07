<template>
  <div class="report-render" :class="{ dark: isDark }">
    <EChart
      v-if="option"
      :option="option"
      :theme="isDark ? 'dark' : undefined"
      :height="height"
      @chart-click="(p: any) => emit('point-click', p)"
    />
    <div v-else-if="rows.length" class="table-wrap">
      <table class="mini-table">
        <thead>
          <tr>
            <th v-for="c in columns" :key="c">{{ c }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(r, i) in rows.slice(0, maxRows)" :key="i">
            <td v-for="c in columns" :key="c">{{ formatCell(r[c]) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-else class="empty-tip">暂无数据</div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import EChart from './EChart.vue'
import { buildChartOption, parseChartConfig } from '@/utils/reportChart'

const props = withDefaults(
  defineProps<{
    chartType?: string
    columns: string[]
    rows: Record<string, any>[]
    chartConfig?: string
    height?: string
    theme?: 'light' | 'dark'
    maxRows?: number
  }>(),
  { height: '100%', theme: 'light', maxRows: 200, chartType: 'TABLE' },
)

const emit = defineEmits<{ (e: 'point-click', params: any): void }>()

const isDark = computed(() => props.theme === 'dark')

const option = computed(() => {
  if ((props.chartType || 'TABLE').toUpperCase() === 'TABLE') return null
  return buildChartOption(props.chartType || 'TABLE', props.columns, props.rows, parseChartConfig(props.chartConfig))
})

function formatCell(v: any) {
  if (v === null || v === undefined) return ''
  if (typeof v === 'object') return JSON.stringify(v)
  return String(v)
}
</script>

<style scoped>
.report-render {
  width: 100%;
  height: 100%;
  overflow: hidden;
}
.table-wrap {
  width: 100%;
  height: 100%;
  overflow: auto;
}
.mini-table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--fs-sm);
}
.mini-table th,
.mini-table td {
  padding: 4px 8px;
  border-bottom: 1px solid var(--border-light);
  text-align: left;
  white-space: nowrap;
}
.mini-table th {
  background: var(--bg-muted);
  color: var(--text-body);
  position: sticky;
  top: 0;
}
.empty-tip {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-sub);
  font-size: var(--fs-sm);
}
/* 大屏深色风格 */.report-render.dark .mini-table th,
.report-render.dark .mini-table td {
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  color: #cfe3ff;
}
.report-render.dark .mini-table th {
  background: rgba(255, 255, 255, 0.06);
  color: #8fb8e8;
}
.report-render.dark .empty-tip {
  color: #7c93ad;
}
</style>
