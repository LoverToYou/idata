<template>
  <Layout>
    <div class="home">
      <!-- 页头 -->
      <div class="home-head">
        <div>
          <div class="home-title">工作台</div>
          <div class="home-sub">数据集成 · 任务开发 · 调度运维 · 数据应用 · 数据安全</div>
        </div>
        <div class="home-actions">
          <el-button size="small" @click="router.push('/monitor')">任务监控</el-button>
          <el-button size="small" @click="router.push('/dashboards')">数据看板</el-button>
          <el-button size="small" type="primary" @click="router.push('/report/create')">新建报表</el-button>
        </div>
      </div>

      <!-- 概览数字 -->
      <div class="stat-row">
        <div v-for="s in statCards" :key="s.label" class="stat" @click="router.push(s.path)">
          <span class="stat-label">{{ s.label }}</span>
          <span class="stat-value">{{ s.value }}</span>
        </div>
      </div>

      <div class="grid">
        <!-- 功能导航 -->
        <section class="panel">
          <div class="panel-head">
            <span>功能导航</span>
            <span class="panel-sub">{{ MENU_GROUPS.length }} 个能力域 · {{ totalFeatures }} 个功能</span>
          </div>
          <div class="nav-groups">
            <div v-if="MENU_TOP_ITEMS.length" class="nav-group">
              <div class="nav-group-head">
                <span class="nav-group-title">独立入口</span>
                <span class="nav-group-desc">不归属能力域的菜单</span>
              </div>
              <div class="nav-links">
                <span
                  v-for="item in MENU_TOP_ITEMS"
                  :key="item.path"
                  class="nav-link"
                  @click="router.push(item.path)"
                >
                  {{ item.title }}
                </span>
              </div>
            </div>
            <div v-for="g in MENU_GROUPS" :key="g.key" class="nav-group">
              <div class="nav-group-head">
                <span class="nav-group-title">{{ g.title }}</span>
                <span class="nav-group-desc">{{ g.desc }}</span>
              </div>
              <div class="nav-links">
                <span
                  v-for="c in g.children"
                  :key="c.path"
                  class="nav-link"
                  @click="router.push(c.path)"
                >
                  {{ c.title }}
                </span>
              </div>
            </div>
          </div>
        </section>

        <!-- 侧栏 -->
        <aside class="grid-side">
          <section class="panel">
            <div class="panel-head"><span>快捷操作</span></div>
            <div class="quick">
              <span
                v-for="q in QUICK_ACTIONS"
                :key="q.path"
                class="quick-item"
                @click="router.push(q.path)"
              >
                {{ q.title }}
              </span>
            </div>
          </section>

          <section class="panel">
            <div class="panel-head"><span>资源概览</span></div>
            <div class="res">
              <div v-for="r in resourceRows" :key="r.label" class="res-row">
                <span class="res-label">{{ r.label }}</span>
                <span class="res-value">{{ r.value }}</span>
              </div>
            </div>
          </section>
        </aside>
      </div>

      <!-- 最近运行 -->
      <section class="panel">
        <div class="panel-head">
          <span>最近运行</span>
          <el-link type="primary" :underline="false" @click="router.push('/monitor')">查看全部</el-link>
        </div>
        <el-table :data="recentInstances" v-loading="loading.instances" size="small">
          <el-table-column prop="workflowName" label="工作流" min-width="180" show-overflow-tooltip />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" size="small" effect="plain">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="triggeredBy" label="触发方式" width="100" />
          <el-table-column label="开始时间" width="170">
            <template #default="{ row }">{{ fmtTime(row.startedAt) }}</template>
          </el-table-column>
          <el-table-column label="耗时" width="110">
            <template #default="{ row }">{{ duration(row) }}</template>
          </el-table-column>
        </el-table>
        <el-empty
          v-if="!loading.instances && !recentInstances.length"
          description="暂无运行记录"
          :image-size="56"
        />
      </section>
    </div>
  </Layout>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import Layout from '@/components/common/Layout.vue'
import { MENU_GROUPS, MENU_TOP_ITEMS, QUICK_ACTIONS } from '@/config/menu'
import { listDatasources } from '@/api/datasource'
import { listTasks } from '@/api/sql-task'
import { listDataxTasks } from '@/api/datax-task'
import { listWorkflows, listSchedules, listInstances } from '@/api/workflow'
import { listReports } from '@/api/report'
import { listDashboards } from '@/api/dashboard'
import { listParameters } from '@/api/parameter'
import { listScripts } from '@/api/python-script'
import { listMaskingRules } from '@/api/masking-rule'
import { listUdfsPage } from '@/api/udf'
import { listFilesPage } from '@/api/file'

const router = useRouter()
const loading = reactive({ instances: false })
const recentInstances = ref<any[]>([])

const counts = reactive({
  datasource: 0,
  dataxTask: 0,
  sqlTask: 0,
  workflow: 0,
  report: 0,
  dashboard: 0,
  schedule: 0,
  parameter: 0,
  script: 0,
  masking: 0,
  udf: 0,
  file: 0,
})

const totalFeatures = computed(
  () => MENU_GROUPS.reduce((n, g) => n + g.children.length, 0) + MENU_TOP_ITEMS.length,
)

const statCards = computed(() => [
  { label: '数据源', value: counts.datasource, path: '/datasource' },
  { label: 'ETL 任务', value: counts.dataxTask, path: '/datax-task' },
  { label: 'SQL 任务', value: counts.sqlTask, path: '/sql-task' },
  { label: '工作流', value: counts.workflow, path: '/workflow' },
  { label: '报表', value: counts.report, path: '/report' },
  { label: '数据看板', value: counts.dashboard, path: '/dashboards' },
])

const resourceRows = computed(() => [
  { label: '定时调度', value: counts.schedule },
  { label: '参数', value: counts.parameter },
  { label: 'Python 脚本', value: counts.script },
  { label: 'UDF', value: counts.udf },
  { label: '脱敏规则', value: counts.masking },
  { label: '文件', value: counts.file },
])

function fmtTime(t?: string) {
  if (!t) return '-'
  return t.slice(0, 16).replace('T', ' ')
}

function duration(row: any) {
  if (!row.startedAt || !row.finishedAt) return '-'
  const ms = new Date(row.finishedAt).getTime() - new Date(row.startedAt).getTime()
  if (Number.isNaN(ms)) return '-'
  const s = Math.max(0, Math.round(ms / 1000))
  return s < 60 ? `${s} 秒` : `${Math.floor(s / 60)}分${s % 60}秒`
}

function statusType(status: string) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'RUNNING') return 'warning'
  return 'info'
}

async function safe<T>(fn: () => Promise<T>, onOk: (v: T) => void) {
  try {
    onOk(await fn())
  } catch {
    /* 单个统计失败不影响页面 */
  }
}

onMounted(async () => {
  loading.instances = true
  await Promise.all([
    safe(listDatasources, (r: any) => (counts.datasource = (r.data || []).length)),
    safe(listDataxTasks, (r: any) => (counts.dataxTask = (r.data || []).length)),
    safe(listTasks, (r: any) => (counts.sqlTask = (r.data || []).length)),
    safe(listWorkflows, (r: any) => (counts.workflow = (r.data || []).length)),
    safe(listReports, (r: any) => (counts.report = (r.data || []).length)),
    safe(listDashboards, (r: any) => (counts.dashboard = (r.data || []).length)),
    safe(listSchedules, (r: any) => (counts.schedule = (r.data || []).length)),
    safe(listParameters, (r: any) => (counts.parameter = (r.data || []).length)),
    safe(listScripts, (r: any) => (counts.script = (r.data || []).length)),
    safe(listMaskingRules, (r: any) => (counts.masking = (r.data || []).length)),
    safe(() => listUdfsPage(undefined, undefined, undefined, 1, 1), (r: any) => (counts.udf = r.data?.total || 0)),
    safe(() => listFilesPage(undefined, undefined, 1, 1), (r: any) => (counts.file = r.data?.total || 0)),
    safe(listInstances, (r: any) => (recentInstances.value = (r.data || []).slice(0, 6))),
  ])
  loading.instances = false
})
</script>

<style scoped>
.home {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.home-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.home-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-title);
  line-height: 1.3;
}

.home-sub {
  font-size: var(--fs-sm);
  color: var(--text-sub);
  margin-top: 3px;
}

.home-actions {
  display: flex;
  gap: 8px;
}

/* 概览数字 */
.stat-row {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
}

.stat {
  background: #fff;
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: 12px 14px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 4px;
  transition: border-color 0.15s;
}

.stat:hover {
  border-color: #c9d6ee;
}

.stat-label {
  font-size: var(--fs-sm);
  color: var(--text-sub);
}

.stat-value {
  font-size: 20px;
  font-weight: 600;
  color: var(--text-title);
  line-height: 1.1;
}

/* 两栏 */
.grid {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(0, 1fr);
  gap: 12px;
  align-items: start;
}

.grid-side {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.panel {
  background: #fff;
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: 14px 16px;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: var(--fs-title);
  font-weight: 600;
  color: var(--text-title);
  padding-bottom: 10px;
  border-bottom: 1px solid var(--border-light);
  margin-bottom: 12px;
}

.panel-sub {
  font-size: var(--fs-sm);
  font-weight: 400;
  color: var(--text-faint);
}

/* 功能导航 */
.nav-groups {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.nav-group-head {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 8px;
}

.nav-group-title {
  font-size: var(--fs-base);
  font-weight: 600;
  color: var(--text-title);
}

.nav-group-desc {
  font-size: var(--fs-sm);
  color: var(--text-faint);
}

.nav-links {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.nav-link {
  font-size: var(--fs-sm);
  color: var(--text-body);
  background: var(--bg-muted);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-sm);
  padding: 4px 10px;
  cursor: pointer;
  transition: all 0.15s;
}

.nav-link:hover {
  color: var(--primary);
  border-color: #c9d6ee;
  background: var(--primary-light);
}

/* 快捷操作 */
.quick {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
}

.quick-item {
  font-size: var(--fs-sm);
  color: var(--text-body);
  background: var(--bg-muted);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-sm);
  padding: 7px 10px;
  text-align: center;
  cursor: pointer;
  transition: all 0.15s;
}

.quick-item:hover {
  color: var(--primary);
  border-color: #c9d6ee;
  background: var(--primary-light);
}

/* 资源概览 */
.res {
  display: flex;
  flex-direction: column;
}

.res-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: var(--fs-base);
  padding: 6px 0;
  border-bottom: 1px dashed var(--border-light);
}

.res-row:last-child {
  border-bottom: none;
}

.res-label {
  color: var(--text-sub);
}

.res-value {
  font-weight: 600;
  color: var(--text-title);
}

@media (max-width: 1280px) {
  .stat-row {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
  .grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
