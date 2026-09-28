import {
  Box,
  Coin,
  Connection,
  Cpu,
  DataAnalysis,
  Document,
  EditPen,
  FolderOpened,
  Hide,
  List,
  Lock,
  MagicStick,
  Monitor,
  Operation,
  PieChart,
  Share,
  Timer,
  TrendCharts,
} from '@element-plus/icons-vue'

export interface MenuChild {
  path: string
  title: string
  icon: any
  desc?: string
}

export interface MenuGroup {
  key: string
  title: string
  icon: any
  desc: string
  children: MenuChild[]
}

/**
 * IDATA 功能框架（菜单与工作台共用）：
 * 工作台 → 数据集成 / 任务开发 / 调度运维 / 数据应用 / 数据安全
 */
export const MENU_GROUPS: MenuGroup[] = [
  {
    key: 'integration',
    title: '数据集成',
    icon: Box,
    desc: '接入数据源、文件与 ETL 同步任务',
    children: [
      { path: '/datasource', title: '数据源管理', icon: Connection, desc: '接入 MySQL / Hive 等数据源' },
      { path: '/datax-task', title: 'ETL 任务管理', icon: Share, desc: 'DataX 数据同步任务' },
      { path: '/files', title: '文件管理', icon: FolderOpened, desc: 'HDFS 文件与资源上传' },
    ],
  },
  {
    key: 'development',
    title: '任务开发',
    icon: EditPen,
    desc: 'SQL / Python / UDF 与参数',
    children: [
      { path: '/sql-task', title: 'SQL 任务管理', icon: Document, desc: 'SQL 编辑、优化与任务化' },
      { path: '/python-script', title: 'Python 脚本', icon: Cpu, desc: '脚本开发与运行记录' },
      { path: '/udf', title: 'UDF 管理', icon: MagicStick, desc: '自定义函数注册与验证' },
      { path: '/parameter', title: '参数管理', icon: Coin, desc: '全局参数与动态时间变量' },
    ],
  },
  {
    key: 'ops',
    title: '调度运维',
    icon: Operation,
    desc: '工作流编排、定时调度与监控',
    children: [
      { path: '/workflow', title: '工作流管理', icon: List, desc: 'DAG 工作流编排' },
      { path: '/schedule', title: '定时调度', icon: Timer, desc: 'Cron 调度与失败重试' },
      { path: '/monitor', title: '任务监控', icon: Monitor, desc: '实例与节点执行日志' },
    ],
  },
  {
    key: 'application',
    title: '数据应用',
    icon: PieChart,
    desc: '报表与大屏看板',
    children: [
      { path: '/report', title: '报表管理', icon: DataAnalysis, desc: '基于 SQL 的图表报表' },
      { path: '/dashboards', title: '数据看板', icon: TrendCharts, desc: '多卡片大屏与筛选联动' },
    ],
  },
  {
    key: 'security',
    title: '数据安全',
    icon: Lock,
    desc: '脱敏与合规',
    children: [
      { path: '/masking-rule', title: '脱敏规则管理', icon: Hide, desc: '字段级脱敏规则' },
    ],
  },
]

/** 工作台快捷操作 */
export const QUICK_ACTIONS = [
  { path: '/datasource/create', title: '新建数据源', icon: Connection },
  { path: '/sql-task', title: 'SQL 任务', icon: Document },
  { path: '/workflow/create', title: '新建工作流', icon: List },
  { path: '/report/create', title: '新建报表', icon: DataAnalysis },
  { path: '/dashboards/create', title: '新建看板', icon: TrendCharts },
  { path: '/monitor', title: '任务监控', icon: Monitor },
]

/** 根据路由路径找到所属分组 key */
export function groupKeyOfPath(path: string): string | undefined {
  return MENU_GROUPS.find((g) => g.children.some((c) => path === c.path || path.startsWith(c.path + '/')))
    ?.key
}
