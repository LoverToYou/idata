<template>
  <Layout>
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>参数管理</span>
          <el-button type="primary" @click="openCreateDialog">
            <el-icon><Plus /></el-icon> 新建参数
          </el-button>
        </div>
      </template>

      <el-table :data="parameters" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="paramName" label="参数名称" min-width="160">
          <template #default="{ row }">
            <code class="param-name">{{ '${' + row.paramName + '}' }}</code>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="120">
          <template #default="{ row }">
            <el-tag :type="row.paramType === 'STATIC' ? 'primary' : 'warning'" size="small" effect="light">
              {{ row.paramType === 'STATIC' ? '静态' : '动态' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="值 / 表达式" min-width="200">
          <template #default="{ row }">
            <span v-if="row.paramType === 'STATIC'" class="param-value">{{ row.paramValue || '-' }}</span>
            <span v-else>
              <el-tag size="small" type="info" effect="plain">{{ row.expression }}</el-tag>
              <div class="table-result-box">
                <el-icon :size="12" style="color: #909399; flex-shrink: 0;"><Right /></el-icon>
                <span class="table-result-box__value">{{ evalDynamic(row.expression) }}</span>
              </div>
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="160" show-overflow-tooltip />
        <el-table-column label="启用" width="80" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.enabled"
              :loading="togglingId === row.id"
              size="small"
              @change="(val: boolean) => handleToggleEnabled(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="180">
          <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEditDialog(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- Create/Edit Dialog -->
    <el-dialog v-model="dialogVisible" :title="isEditing ? '编辑参数' : '新建参数'" width="580px" @closed="handleDialogClosed">
      <el-form :model="form" label-position="top" ref="formRef" :rules="formRules">
        <el-form-item label="参数名称" prop="paramName">
          <el-input v-model="form.paramName" placeholder="字母开头，仅支持字母、数字、下划线" :disabled="isEditing && !canChangeName" />
          <div v-if="isEditing && form.paramName" class="form-hint">SQL 中引用方式: <code>{{ '${' + form.paramName + '}' }}</code></div>
        </el-form-item>
        <el-form-item label="参数类型" prop="paramType">
          <el-radio-group v-model="form.paramType">
            <el-radio value="STATIC">静态参数</el-radio>
            <el-radio value="DYNAMIC">动态参数</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.paramType === 'STATIC'" label="参数值" prop="paramValue">
          <el-input v-model="form.paramValue" placeholder="输入参数值" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item v-if="form.paramType === 'DYNAMIC'" label="表达式" prop="expression">
          <!-- Expression Type Toggle -->
          <el-radio-group v-model="expressionMode" size="small" class="expr-mode-toggle">
            <el-radio-button value="function">函数表达式</el-radio-button>
            <el-radio-button value="sql">SQL 语句</el-radio-button>
          </el-radio-group>

          <!-- Function Expression Mode -->
          <template v-if="expressionMode === 'function'">
            <div class="quick-exprs">
              <span class="quick-exprs__label">快捷日期:</span>
              <el-tag
                v-for="expr in quickExpressions"
                :key="expr.value"
                size="small"
                :type="form.expression === expr.value ? 'primary' : 'info'"
                effect="plain"
                class="quick-expr-tag"
                @click="form.expression = expr.value"
              >
                {{ expr.label }}
              </el-tag>
            </div>
            <div v-if="form.expression" class="result-box">
              <div class="result-box__label">求值结果</div>
              <div class="result-box__value">{{ evalDynamic(form.expression) }}</div>
            </div>
          </template>

          <!-- SQL Statement Mode -->
          <template v-if="expressionMode === 'sql'">
            <el-alert type="info" :closable="false" show-icon>
              <template #title>
                使用内置 MySQL 执行测试
              </template>
              编写完整的 SQL 查询语句，引用时将直接替换 <code>${paramName}</code> 占位符
            </el-alert>
            <el-input
              v-model="form.expression"
              placeholder="SELECT column FROM table WHERE condition"
              type="textarea"
              :rows="5"
              class="sql-textarea"
            />
            <div class="sql-exec-bar">
              <el-button
                size="small"
                type="primary"
                :loading="sqlTesting"
                :disabled="!form.expression?.trim()"
                @click="handleSqlTest"
              >
                ▶ 执行查看结果
              </el-button>
            </div>
            <div v-if="sqlTestResult" class="sql-test-result">
              <div class="result-meta">
                <el-tag
                  size="small"
                  :type="sqlTestResult.success ? 'success' : 'danger'"
                  effect="plain"
                >
                  {{ sqlTestResult.success ? '成功' : '失败' }}
                </el-tag>
                <span v-if="sqlTestResult.elapsed" class="result-elapsed">{{ sqlTestResult.elapsed }}</span>
              </div>
              <div v-if="sqlTestResult.success && sqlTestResult.columns && sqlTestResult.columns.length > 0" class="sql-test-table-wrapper">
                <el-table
                  :data="sqlTestResult.rows"
                  border
                  stripe
                  max-height="220"
                  size="small"
                  style="width: 100%"
                >
                  <el-table-column
                    v-for="col in sqlTestResult.columns"
                    :key="col"
                    :prop="col"
                    :label="col"
                    min-width="100"
                  />
                </el-table>
              </div>
              <div v-else-if="sqlTestResult.success" class="sql-test-ok">
                {{ sqlTestResult.message || '执行成功' }}
              </div>
              <el-alert v-if="sqlTestResult.error" type="error" :description="sqlTestResult.error" show-icon />
            </div>
          </template>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="参数用途说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">保存</el-button>
      </template>
    </el-dialog>

    <!-- Resolve Preview Dialog -->
    <el-dialog v-model="previewVisible" title="参数解析预览" width="700px">
      <div class="preview-section">
        <div class="preview-label">原始 SQL:</div>
        <pre class="preview-sql">{{ previewInput }}</pre>
      </div>
      <div v-if="previewResolvedParams && Object.keys(previewResolvedParams).length > 0" class="preview-section">
        <div class="preview-label">参数替换:</div>
        <el-table :data="previewParamList" size="small" stripe>
          <el-table-column prop="name" label="参数" width="160">
            <template #default="{ row }"><code>{{ '${' + row.name + '}' }}</code></template>
          </el-table-column>
          <el-table-column prop="value" label="替换值" />
        </el-table>
      </div>
      <div class="preview-section">
        <div class="preview-label">解析后 SQL:</div>
        <pre class="preview-sql resolved">{{ previewResolvedSql }}</pre>
      </div>
    </el-dialog>
  </Layout>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import Layout from '@/components/common/Layout.vue'
import {
  listParameters,
  createParameter,
  updateParameter,
  deleteParameter,
  resolveParams,
  executeBuiltinSql,
  type Parameter,
  type ParameterRequest,
} from '@/api/parameter'

const parameters = ref<Parameter[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const isEditing = ref(false)
const saving = ref(false)
const togglingId = ref<number | null>(null)
const formRef = ref()
const currentId = ref<number | null>(null)
const canChangeName = ref(true)
const expressionMode = ref<'function' | 'sql'>('function')

const sqlTesting = ref(false)
const sqlTestResult = ref<{
  success: boolean
  columns?: string[]
  rows?: Record<string, any>[]
  elapsed?: string
  message?: string
  error?: string
} | null>(null)

const form = ref<ParameterRequest>({
  paramName: '',
  paramValue: '',
  paramType: 'STATIC',
  expression: '',
  description: '',
  enabled: true,
})

const formRules = {
  paramName: [
    { required: true, message: '请输入参数名称', trigger: 'blur' },
    { pattern: /^\w+$/, message: '仅支持字母、数字、下划线', trigger: 'blur' },
  ],
  paramType: [{ required: true, message: '请选择参数类型', trigger: 'change' }],
  paramValue: [{ required: true, message: '请输入参数值', trigger: 'blur' }],
  expression: [{ required: true, message: '请选择表达式', trigger: 'change' }],
}

const quickExpressions = [
  { label: '今天', value: 'today' },
  { label: '昨天', value: 'yesterday' },
  { label: '本月第一天', value: 'date_trunc(today, \'month\')' },
  { label: '本月', value: 'this_month' },
  { label: '上月', value: 'last_month' },
  { label: '本月最后一天', value: 'this_month_end' },
  { label: '上月最后一天', value: 'last_month_end' },
  { label: '7天前', value: 'date_add(today, -7)' },
  { label: '30天后', value: 'date_add(today, 30)' },
  { label: '当前时间', value: 'now()' },
]

// Resolve preview
const previewVisible = ref(false)
const previewInput = ref('')
const previewResolvedSql = ref('')
const previewResolvedParams = ref<Record<string, string>>({})
const previewParamList = computed(() =>
  Object.entries(previewResolvedParams.value || {}).map(([name, value]) => ({ name, value }))
)

async function handleSqlTest() {
  const sql = form.value.expression?.trim()
  if (!sql) return

  sqlTesting.value = true
  sqlTestResult.value = null
  const start = Date.now()

  try {
    const res = await executeBuiltinSql(sql)
    const elapsed = Date.now() - start
    const data = res.data
    if (data.success === false || data.errorMessage) {
      sqlTestResult.value = { success: false, error: data.errorMessage || '执行失败', elapsed: `耗时: ${elapsed}ms` }
    } else {
      sqlTestResult.value = {
        success: true,
        columns: data.columns || [],
        rows: data.rows || [],
        elapsed: `耗时: ${elapsed}ms`,
        message: data.affectedRows != null && data.affectedRows >= 0 ? `影响行数: ${data.affectedRows}` : '执行成功',
      }
    }
  } catch (e: any) {
    const elapsed = Date.now() - start
    sqlTestResult.value = { success: false, error: e.message || '执行失败', elapsed: `耗时: ${elapsed}ms` }
  } finally {
    sqlTesting.value = false
  }
}

function evalDynamic(expr: string): string {
  // String literal
  if ((expr.startsWith("'") && expr.endsWith("'")) || (expr.startsWith('"') && expr.endsWith('"'))) {
    return expr.slice(1, -1)
  }
  // Function call
  const fnMatch = expr.match(/^(\w+)\(([\s\S]*)\)$/)
  if (fnMatch) {
    return evalFunctionCall(fnMatch[1], parseFnArgs(fnMatch[2]))
  }

  const today = new Date()
  const y = today.getFullYear()
  const m = String(today.getMonth() + 1).padStart(2, '0')
  const d = String(today.getDate()).padStart(2, '0')
  const ym = `${y}-${m}`
  const lastMonth = today.getMonth() === 0 ? 11 : today.getMonth() - 1
  const lastMonthYear = today.getMonth() === 0 ? y - 1 : y
  const lastM = String(lastMonth + 1).padStart(2, '0')
  const lastMonthDays = new Date(lastMonthYear, lastMonth + 1, 0).getDate()
  const thisMonthDays = new Date(y, today.getMonth() + 1, 0).getDate()

  switch (expr) {
    case 'now': return formatDateTime('yyyy-MM-dd HH:mm:ss')
    case 'today': return `${y}-${m}-${d}`
    case 'yesterday': {
      const yd = new Date(today)
      yd.setDate(yd.getDate() - 1)
      return `${yd.getFullYear()}-${String(yd.getMonth() + 1).padStart(2, '0')}-${String(yd.getDate()).padStart(2, '0')}`
    }
    case 'tomorrow': {
      const td = new Date(today)
      td.setDate(td.getDate() + 1)
      return `${td.getFullYear()}-${String(td.getMonth() + 1).padStart(2, '0')}-${String(td.getDate()).padStart(2, '0')}`
    }
    case 'this_month': return ym
    case 'last_month': return `${lastMonthYear}-${lastM}`
    case 'this_year': return String(y)
    case 'yyyyMMdd': return `${y}${m}${d}`
    case 'yyyy-MM-dd': return `${y}-${m}-${d}`
    case 'yyyyMM': return `${y}${m}`
    case 'yyyy-MM': return ym
    case 'yyyy': return String(y)
    case 'this_month_start': return `${y}-${m}-01`
    case 'this_month_end': return `${y}-${m}-${String(thisMonthDays).padStart(2, '0')}`
    case 'last_month_start': return `${lastMonthYear}-${lastM}-01`
    case 'last_month_end': return `${lastMonthYear}-${lastM}-${String(lastMonthDays).padStart(2, '0')}`
    default: {
      // Try as date format pattern, fall back to raw expression
      const formatted = formatDateTime(expr)
      return formatted !== expr ? formatted : expr
    }
  }
}

function parseFnArgs(body: string): string[] {
  const args: string[] = []
  let depth = 0
  let inSingle = false
  let inDouble = false
  let current = ''
  for (const c of body) {
    if (inSingle) {
      if (c === "'") inSingle = false
      current += c
    } else if (inDouble) {
      if (c === '"') inDouble = false
      current += c
    } else if (c === "'") {
      inSingle = true
      current += c
    } else if (c === '"') {
      inDouble = true
      current += c
    } else if (c === '(') {
      depth++
      current += c
    } else if (c === ')') {
      depth--
      current += c
    } else if (c === ',' && depth === 0) {
      args.push(current.trim())
      current = ''
    } else {
      current += c
    }
  }
  const last = current.trim()
  if (last) args.push(last)
  return args
}

function evalFunctionCall(name: string, args: string[]): string {
  switch (name) {
    case 'now': return formatDateTime('yyyy-MM-dd HH:mm:ss')
    case 'today': return evalDynamic('today')
    case 'date_add': {
      if (args.length < 2) return '<参数不足>'
      const dateStr = evalDynamic(args[0])
      const days = parseInt(evalDynamic(args[1]), 10)
      if (isNaN(days)) return '<无效天数>'
      const d = new Date(dateStr)
      if (isNaN(d.getTime())) return '<无效日期>'
      d.setDate(d.getDate() + days)
      return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
    }
    case 'date_sub': {
      if (args.length < 2) return '<参数不足>'
      const dateStr = evalDynamic(args[0])
      const days = parseInt(evalDynamic(args[1]), 10)
      if (isNaN(days)) return '<无效天数>'
      const d = new Date(dateStr)
      if (isNaN(d.getTime())) return '<无效日期>'
      d.setDate(d.getDate() - days)
      return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
    }
    case 'date_format': {
      if (args.length < 2) return '<参数不足>'
      const dateStr = evalDynamic(args[0])
      const pattern = args[1].replace(/^['"]|['"]$/g, '')
      const d = new Date(dateStr)
      if (isNaN(d.getTime())) return '<无效日期>'
      return formatDateTimeWithDate(d, pattern)
    }
    case 'datediff': {
      if (args.length < 2) return '<参数不足>'
      const endStr = evalDynamic(args[0])
      const startStr = evalDynamic(args[1])
      const end = new Date(endStr)
      const start = new Date(startStr)
      if (isNaN(end.getTime()) || isNaN(start.getTime())) return '<无效日期>'
      const diffTime = end.getTime() - start.getTime()
      return String(Math.round(diffTime / (1000 * 60 * 60 * 24)))
    }
    case 'date_trunc': {
      if (args.length < 2) return '<参数不足>'
      const dateStr = evalDynamic(args[0])
      const unit = args[1].replace(/^['"]|['"]$/g, '').toLowerCase()
      const d = new Date(dateStr)
      if (isNaN(d.getTime())) return '<无效日期>'
      switch (unit) {
        case 'month': return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-01`
        case 'quarter': {
          const qMonth = Math.floor(d.getMonth() / 3) * 3 + 1
          return `${d.getFullYear()}-${String(qMonth).padStart(2, '0')}-01`
        }
        case 'year': return `${d.getFullYear()}-01-01`
        default: return '<未知单位>'
      }
    }
    case 'concat': return args.map(a => evalDynamic(a)).join('')
    case 'coalesce': {
      for (const arg of args) {
        const val = evalDynamic(arg)
        if (val) return val
      }
      return ''
    }
    case 'if': {
      if (args.length < 2) return '<参数不足>'
      const cond = evalDynamic(args[0])
      const truthy = cond !== '' && cond.toLowerCase() !== 'false' && cond !== '0'
      if (truthy) return evalDynamic(args[1])
      return args.length >= 3 ? evalDynamic(args[2]) : ''
    }
    case 'trim': return evalDynamic(args[0] || '').trim()
    case 'upper': return evalDynamic(args[0] || '').toUpperCase()
    case 'lower': return evalDynamic(args[0] || '').toLowerCase()
    case 'length': return String(evalDynamic(args[0] || '').length)
    case 'replace': {
      if (args.length < 2) return '<参数不足>'
      const str = evalDynamic(args[0])
      const from = args[1].replace(/^['"]|['"]$/g, '')
      const to = args.length >= 3 ? args[2].replace(/^['"]|['"]$/g, '') : ''
      if (!from) return str
      return str.split(from).join(to)
    }
    case 'substring': {
      if (args.length < 2) return '<参数不足>'
      const str = evalDynamic(args[0])
      const start = Math.max(0, parseInt(evalDynamic(args[1]), 10) - 1)
      if (args.length >= 3) {
        const len = parseInt(evalDynamic(args[2]), 10)
        return str.substring(start, start + len)
      }
      return str.substring(start)
    }
    case 'regexp_extract': {
      if (args.length < 2) return '<参数不足>'
      const str = evalDynamic(args[0])
      const pattern = args[1].replace(/^['"]|['"]$/g, '')
      const group = args.length >= 3 ? parseInt(evalDynamic(args[2]), 10) : 0
      try {
        const match = str.match(new RegExp(pattern))
        return match ? (match[group] || '') : ''
      } catch {
        return '<无效正则>'
      }
    }
    default: return '<未知函数>'
  }
}

function formatDateTimeWithDate(d: Date, pattern: string): string {
  const pad = (n: number, len = 2) => String(n).padStart(len, '0')
  const map: Record<string, string> = {
    'yyyy': String(d.getFullYear()),
    'yy': String(d.getFullYear()).slice(-2),
    'MM': pad(d.getMonth() + 1),
    'dd': pad(d.getDate()),
    'HH': pad(d.getHours()),
    'mm': pad(d.getMinutes()),
    'ss': pad(d.getSeconds()),
    'SSS': pad(d.getMilliseconds(), 3),
  }
  let result = pattern
  for (const [token, val] of Object.entries(map)) {
    result = result.split(token).join(val)
  }
  return result
}

function formatDateTime(pattern: string): string {
  return formatDateTimeWithDate(new Date(), pattern)
}

function formatTime(t: string) {
  if (!t) return ''
  return t.slice(0, 16).replace('T', ' ')
}

async function loadParameters() {
  loading.value = true
  try {
    const res = await listParameters()
    parameters.value = res.data
  } catch { /* ignore */ }
  finally { loading.value = false }
}

function openCreateDialog() {
  isEditing.value = false
  currentId.value = null
  canChangeName.value = true
  expressionMode.value = 'function'
  form.value = { paramName: '', paramValue: '', paramType: 'STATIC', expression: '', description: '', enabled: true }
  dialogVisible.value = true
}

function looksLikeSql(expr: string): boolean {
  const upper = expr.trim().toUpperCase()
  return /^(SELECT|WITH|SHOW|DESC|DESCRIBE|EXPLAIN|CALL|SET|USE)\b/.test(upper)
}

function openEditDialog(param: Parameter) {
  isEditing.value = true
  currentId.value = param.id
  canChangeName.value = false
  const expr = param.expression || ''
  expressionMode.value = (param.paramType === 'DYNAMIC' && looksLikeSql(expr)) ? 'sql' : 'function'
  form.value = {
    paramName: param.paramName,
    paramValue: param.paramValue || '',
    paramType: param.paramType,
    expression: expr,
    description: param.description || '',
    enabled: param.enabled,
  }
  dialogVisible.value = true
}

function handleDialogClosed() {
  // Reset form validation state
  formRef.value?.clearValidate()
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
    if (isEditing.value && currentId.value) {
      await updateParameter({ ...form.value, id: currentId.value })
      ElMessage.success('参数已更新')
    } else {
      await createParameter(form.value)
      ElMessage.success('参数已创建')
    }
    dialogVisible.value = false
    await loadParameters()
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete(param: Parameter) {
  try {
    await ElMessageBox.confirm(`确定删除参数「${param.paramName}」？`, '确认删除', { type: 'warning' })
    await deleteParameter(param.id)
    ElMessage.success('已删除')
    await loadParameters()
  } catch { /* cancelled */ }
}

async function handleToggleEnabled(param: Parameter, val: boolean) {
  togglingId.value = param.id
  try {
    await updateParameter({ id: param.id, paramName: param.paramName, paramType: param.paramType, enabled: val })
    ElMessage.success(val ? '已启用' : '已禁用')
    await loadParameters()
  } catch (e: any) {
    ElMessage.error(e.message || '操作失败')
  } finally {
    togglingId.value = null
  }
}

onMounted(loadParameters)
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.param-name {
  background: #f5f7fa;
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 13px;
  color: #409eff;
}

.param-value {
  color: #606266;
  font-size: 13px;
}

.form-hint {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.form-hint code {
  background: #f5f7fa;
  padding: 1px 4px;
  border-radius: 2px;
  color: #409eff;
}

.result-box {
  margin-top: 8px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  background: #fafafa;
  padding: 8px 12px;
}

.result-box__label {
  font-size: 11px;
  color: #909399;
  margin-bottom: 4px;
}

.result-box__value {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
}

.table-result-box {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 3px;
}

.table-result-box__value {
  font-size: 13px;
  color: #303133;
  font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
  font-weight: 500;
}

.preview-section {
  margin-bottom: 16px;
}

.preview-label {
  font-size: 13px;
  font-weight: 600;
  color: #606266;
  margin-bottom: 6px;
}

.preview-sql {
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 12px;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 200px;
  overflow-y: auto;
  margin: 0;
}

.preview-sql.resolved {
  background: #f0f9eb;
  border-color: #c2e7b0;
}

.quick-exprs {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 6px;
}

.quick-exprs__label {
  font-size: 11px;
  color: #c0c4cc;
  flex-shrink: 0;
}

.quick-expr-tag {
  cursor: pointer !important;
  font-size: 11px !important;
}

.quick-expr-tag:hover {
  opacity: 0.8;
}

.el-form-item .el-textarea {
  margin-top: 0;
}

/* Expression Type Toggle */
.expr-mode-toggle {
  display: flex;
  margin-bottom: 10px;
}

.sql-textarea {
  font-family: 'Menlo', 'Monaco', 'Courier New', monospace !important;
  font-size: 13px !important;
}

.sql-textarea :deep(.el-textarea__inner) {
  font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
}

.sql-exec-bar {
  margin-top: 8px;
  display: flex;
  align-items: center;
}

.sql-test-result {
  margin-top: 10px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 10px;
  background: #fafafa;
}

.sql-test-result .result-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 12px;
  color: #909399;
}

.sql-test-result .result-elapsed {
  font-size: 12px;
  color: #909399;
}

.sql-test-table-wrapper {
  max-height: 240px;
  overflow-y: auto;
}

.sql-test-ok {
  font-size: 13px;
  color: #67c23a;
  padding: 8px 0;
}
</style>
