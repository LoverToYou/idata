/**
 * 报表图表配置与 ECharts option 构建。
 * chartConfig 结构：{ xField, yFields: string[], limit?, yMin?, yMax?, smooth? }
 */

export interface ChartConfig {
  xField?: string
  yFields?: string[]
  limit?: number
  /** Y 轴最小值；填写即截断坐标轴（不从 0 开始） */
  yMin?: number | null
  /** Y 轴最大值 */
  yMax?: number | null
  /** 折线曲线弧度 0~1（0 = 直线）；不填时折线默认平滑、柱状图默认直线 */
  smooth?: number | null
}

export function parseChartConfig(raw?: string | null): ChartConfig {
  if (!raw) return {}
  try {
    const obj = JSON.parse(raw)
    return obj && typeof obj === 'object' ? obj : {}
  } catch {
    return {}
  }
}

export function numericColumns(columns: string[], rows: Record<string, any>[]): string[] {
  return columns.filter((c) => rows.some((r) => typeof r[c] === 'number'))
}

/** 字段名解析：优先精确匹配，其次忽略大小写（兼容数据库返回的大写列名） */
export function resolveField(columns: string[], field?: string | null): string | undefined {
  if (!field) return undefined
  if (columns.includes(field)) return field
  const lower = field.toLowerCase()
  return columns.find((c) => c.toLowerCase() === lower)
}

export function buildChartOption(
  chartType: string,
  columns: string[],
  rows: Record<string, any>[],
  config: ChartConfig,
): any | null {
  if (!columns?.length || !rows?.length) return null
  const type = (chartType || 'TABLE').toUpperCase()
  if (type === 'TABLE') return null

  const data = config.limit && config.limit > 0 ? rows.slice(0, config.limit) : rows
  const xField = resolveField(columns, config.xField) || columns[0]
  const nums = numericColumns(columns, data)
  const resolvedY = (config.yFields || [])
    .map((f) => resolveField(columns, f))
    .filter((f): f is string => !!f)
  const yFields =
    resolvedY.length > 0
      ? resolvedY
      : nums.length > 0
        ? [nums[0]]
        : [columns[1] || columns[0]]
  if (type === 'PIE') {
    const valueField = yFields[0]
    return {
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { bottom: 0, type: 'scroll' },
      series: [
        {
          type: 'pie',
          radius: ['40%', '66%'],
          center: ['50%', '45%'],
          data: data.map((r) => ({
            name: String(r[xField] ?? ''),
            value: Number(r[valueField] ?? 0),
          })),
          emphasis: { itemStyle: { shadowBlur: 10, shadowColor: 'rgba(0,0,0,0.3)' } },
        },
      ],
    }
  }

  const categories = data.map((r) => String(r[xField] ?? ''))
  const smooth = typeof config.smooth === 'number' ? config.smooth : type !== 'BAR'

  const series = yFields.map((f) => ({
    name: f,
    type: type === 'BAR' ? 'bar' : 'line',
    smooth,
    barMaxWidth: 32,
    data: data.map((r) => Number(r[f] ?? 0)),
  }))

  return {
    tooltip: { trigger: 'axis' },
    legend: { top: 0, type: 'scroll' },
    grid: { left: 56, right: 24, top: 40, bottom: categories.length > 30 ? 56 : 40 },
    xAxis: {
      type: 'category',
      data: categories,
      axisLabel: { rotate: categories.length > 8 ? 30 : 0, hideOverlap: true },
    },
    yAxis: {
      type: 'value',
      min: typeof config.yMin === 'number' ? config.yMin : undefined,
      max: typeof config.yMax === 'number' ? config.yMax : undefined,
      // 设了最小值即视为截断坐标轴，不再强制包含 0
      scale: typeof config.yMin === 'number',
    },
    dataZoom:
      categories.length > 30
        ? [{ type: 'inside' }, { type: 'slider', height: 16, bottom: 12 }]
        : undefined,
    series,
  }
}

export interface AlertConfig {
  field?: string
  operator?: string
  value?: number | null
  level?: 'warn' | 'error'
}

export const ALERT_OPERATORS = [
  { value: '>', label: '大于 (>)' },
  { value: '>=', label: '大于等于 (>=)' },
  { value: '<', label: '小于 (<)' },
  { value: '<=', label: '小于等于 (<=)' },
  { value: '==', label: '等于 (==)' },
  { value: '!=', label: '不等于 (!=)' },
]

export function parseAlertConfig(raw?: string | null): AlertConfig {
  if (!raw) return {}
  try {
    const obj = JSON.parse(raw)
    return obj && typeof obj === 'object' ? obj : {}
  } catch {
    return {}
  }
}

function compare(a: number, b: number, op: string): boolean {
  if (Number.isNaN(a) || Number.isNaN(b)) return false
  switch (op) {
    case '>':
      return a > b
    case '>=':
      return a >= b
    case '<':
      return a < b
    case '<=':
      return a <= b
    case '==':
      return a === b
    case '!=':
      return a !== b
    default:
      return false
  }
}

/** 评估告警：任一行满足比较条件即触发 */
export function evaluateAlert(
  cfg: AlertConfig,
  columns: string[],
  rows: Record<string, any>[],
): { triggered: boolean; value?: any; field?: string } {
  if (!cfg || !cfg.field || cfg.value === null || cfg.value === undefined || !rows?.length) {
    return { triggered: false }
  }
  const field = resolveField(columns, cfg.field) || cfg.field
  const hit = rows.find((r) => compare(Number(r[field]), Number(cfg.value), cfg.operator || '>'))
  return { triggered: !!hit, value: hit ? hit[field] : undefined, field }
}

/** 图表点击下钻：按点击的分类值过滤出行明细 */
export function filterDrillRows(
  columns: string[],
  rows: Record<string, any>[],
  config: ChartConfig,
  point: { name?: string },
): Record<string, any>[] {
  const xField = resolveField(columns, config.xField) || columns[0]
  return rows.filter((r) => {
    if (point.name !== undefined && xField && String(r[xField] ?? '') !== String(point.name)) return false
    return true
  })
}

/** 结果集导出 CSV（Excel 兼容：BOM + 转义） */export function exportCsv(fileName: string, columns: string[], rows: Record<string, any>[]) {
  const escape = (v: any) => {
    if (v === null || v === undefined) return ''
    const s = String(v)
    return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s
  }
  const lines = [columns.map(escape).join(',')]
  rows.forEach((r) => lines.push(columns.map((c) => escape(r[c])).join(',')))
  const blob = new Blob(['\uFEFF' + lines.join('\n')], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName.endsWith('.csv') ? fileName : `${fileName}.csv`
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}
