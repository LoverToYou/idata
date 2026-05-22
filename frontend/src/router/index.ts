import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/dashboard',
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('@/views/dashboard/IndexView.vue'),
    meta: { title: '工作台' },
  },
  {
    path: '/datasource',
    name: 'Datasource',
    component: () => import('@/views/datasource/DatasourceList.vue'),
    meta: { title: '数据源管理' },
  },
  {
    path: '/datasource/create',
    name: 'DatasourceCreate',
    component: () => import('@/views/datasource/DatasourceForm.vue'),
    meta: { title: '新建数据源' },
  },
  {
    path: '/datasource/:id/edit',
    name: 'DatasourceEdit',
    component: () => import('@/views/datasource/DatasourceForm.vue'),
    meta: { title: '编辑数据源' },
  },
  {
    path: '/datasource/:id/hive',
    name: 'HiveMeta',
    component: () => import('@/views/datasource/HiveMetaView.vue'),
    meta: { title: 'Hive 元数据' },
  },
  {
    path: '/parameter',
    name: 'Parameter',
    component: () => import('@/views/parameter/ParameterList.vue'),
    meta: { title: '参数管理' },
  },
  {
    path: '/sql-task',
    name: 'SqlTask',
    component: () => import('@/views/sql-editor/SqlEditorView.vue'),
    meta: { title: 'SQL 任务管理' },
  },
  {
    path: '/datax-task',
    name: 'DataxTask',
    component: () => import('@/views/datax-task/DataxTaskList.vue'),
    meta: { title: 'ETL 任务管理' },
  },
  {
    path: '/datax-task/create',
    name: 'DataxTaskCreate',
    component: () => import('@/views/datax-task/DataxTaskEditor.vue'),
    meta: { title: '新建 ETL 任务' },
  },
  {
    path: '/datax-task/:id/edit',
    name: 'DataxTaskEdit',
    component: () => import('@/views/datax-task/DataxTaskEditor.vue'),
    meta: { title: '编辑 ETL 任务' },
  },
  {
    path: '/workflow',
    name: 'Workflow',
    component: () => import('@/views/workflow/WorkflowList.vue'),
    meta: { title: '工作流管理' },
  },
  {
    path: '/workflow/create',
    name: 'WorkflowCreate',
    component: () => import('@/views/workflow/WorkflowEditorNew.vue'),
    meta: { title: '新建工作流' },
  },
  {
    path: '/workflow/:id/edit',
    name: 'WorkflowEdit',
    component: () => import('@/views/workflow/WorkflowEditorNew.vue'),
    meta: { title: '编辑工作流' },
  },
  {
    path: '/monitor',
    name: 'Monitor',
    component: () => import('@/views/monitor/MonitorView.vue'),
    meta: { title: '任务监控' },
  },
  {
    path: '/masking-rule',
    name: 'MaskingRule',
    component: () => import('@/views/masking-rule/MaskingRuleList.vue'),
    meta: { title: '脱敏规则管理' },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

export default router
