import { describe, it, expect, vi, beforeEach } from 'vitest'
import { listDatasourceTables } from '@/api/datasource'
import { shallowMount, flushPromises } from '@vue/test-utils'
import { nextTick } from 'vue'

// Shared mock functions that need per-test control
const { mockCreateTask, mockUpdateTask, mockGetTask, mockListTasks, mockEditorInstance, mockDatasource } = vi.hoisted(() => ({
  mockCreateTask: vi.fn(),
  mockUpdateTask: vi.fn(),
  mockGetTask: vi.fn(),
  mockListTasks: vi.fn(),
  mockDatasource: {
    id: 1,
    name: 'TestMySQL',
    type: 'MYSQL',
    host: 'localhost',
    port: 3306,
    databaseName: 'test',
    username: 'root',
    createdAt: '',
    updatedAt: '',
  },
  mockEditorInstance: {
    setValue: vi.fn(),
    getValue: vi.fn().mockReturnValue(''),
    dispose: vi.fn(),
    onDidChangeModelContent: vi.fn().mockReturnValue({ dispose: vi.fn() }),
    onDidChangeCursorPosition: vi.fn().mockReturnValue({ dispose: vi.fn() }),
    getSelection: vi.fn().mockReturnValue({ isEmpty: () => true }),
    addCommand: vi.fn(),
    getModel: vi.fn(),
  },
}))

vi.mock('monaco-editor', () => ({
  editor: {
    defineTheme: vi.fn(),
    create: vi.fn().mockReturnValue(mockEditorInstance),
  },
  languages: {
    registerCompletionItemProvider: vi.fn().mockReturnValue({ dispose: vi.fn() }),
    CompletionItemKind: {
      Function: 1,
      Keyword: 2,
      Struct: 3,
      Field: 4,
    },
    CompletionItemInsertTextRule: {
      InsertAsSnippet: 1,
    },
  },
  KeyMod: { CtrlCmd: 1 },
  KeyCode: { Enter: 13 },
  Uri: { parse: vi.fn() },
}))

vi.mock('@/api/datasource', () => ({
  listDatasources: vi.fn().mockResolvedValue({
    code: 200,
    data: [mockDatasource],
  }),
  testConnectionById: vi.fn().mockResolvedValue({ code: 200, data: true }),
  listDatasourceTables: vi.fn().mockResolvedValue({ code: 200, data: [] }),
  getDatasourceTableColumns: vi.fn().mockResolvedValue({ code: 200, data: [] }),
  listAccessibleDatabases: vi.fn().mockResolvedValue({ code: 200, data: [] }),
}))

vi.mock('@/api/sql-task', () => ({
  listTasks: () => mockListTasks(),
  getTask: (id: number) => mockGetTask(id),
  createTask: (data: any) => mockCreateTask(data),
  updateTask: (data: any) => mockUpdateTask(data),
  deleteTask: vi.fn().mockResolvedValue({ code: 200, data: null }),
  publishTask: vi.fn().mockResolvedValue({ code: 200, data: { id: 1, status: 'PUBLISHED' } }),
  unpublishTask: vi.fn().mockResolvedValue({ code: 200, data: { id: 1, status: 'DRAFT' } }),
}))

vi.mock('@/api/sql', () => ({
  executeSql: vi.fn(),
  batchExecuteSql: vi.fn(),
  explainSql: vi.fn(),
  fullAnalyze: vi.fn(),
  formatSql: vi.fn(),
}))

vi.mock('@/api/grammar', () => ({
  detectGrammarContext: vi.fn(),
  getCachedGrammarContext: vi.fn().mockReturnValue(null),
  setCachedGrammarContext: vi.fn(),
}))

import SqlEditorView from '../SqlEditorView.vue'

function createWrapper() {
  return shallowMount(SqlEditorView, {
    global: {
      stubs: {
        Layout: { template: '<div><slot /></div>' },
        'el-card': { template: '<div><slot /></div>' },
      },
    },
  })
}

describe('SqlEditorView - SQL任务创建与编辑', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockListTasks.mockResolvedValue({ code: 200, data: [] })
  })

  it('取消创建 - 打开对话框后取消，停留在列表模式', async () => {
    const wrapper = createWrapper()
    await flushPromises() // wait for onMounted

    // 初始为列表模式
    expect(wrapper.vm.mode).toBe('list')

    // 打开新建对话框
    wrapper.vm.handleNewTask()
    await nextTick()
    expect(wrapper.vm.newTaskDialogVisible).toBe(true)

    // 取消（关闭对话框）
    wrapper.vm.newTaskDialogVisible = false
    await nextTick()

    // 仍停留在列表模式，未切换
    expect(wrapper.vm.mode).toBe('list')
    expect(wrapper.vm.currentTaskId).toBeNull()
  })

  it('确认创建 - 填写表单后确定，调用createTask并进入编辑模式', async () => {
    const wrapper = createWrapper()
    await flushPromises()

    // 打开对话框并填写表单
    wrapper.vm.handleNewTask()
    await nextTick()
    wrapper.vm.newTaskForm.name = '测试SQL任务'
    wrapper.vm.newTaskForm.sqlType = 'MYSQL'
    wrapper.vm.newTaskForm.datasourceId = 1

    mockCreateTask.mockResolvedValue({
      code: 200,
      data: { id: 42, name: '测试SQL任务', sqlContent: '' },
    })

    // 确认创建
    wrapper.vm.handleNewTaskConfirm()
    await flushPromises()
    await nextTick()

    // createTask 被调用（任务内容为空）
    expect(mockCreateTask).toHaveBeenCalledWith({
      name: '测试SQL任务',
      sqlContent: '',
      datasourceId: 1,
    })

    // 进入编辑模式，编辑器内容为空
    expect(wrapper.vm.mode).toBe('edit')
    expect(wrapper.vm.taskName).toBe('测试SQL任务')
    expect(wrapper.vm.currentTaskId).toBe(42)
    expect(mockEditorInstance.setValue).toHaveBeenCalledWith('')
  })

  it('完整流程：对话框确认创建 → 编辑内容 → 再次保存', async () => {
    const wrapper = createWrapper()
    await flushPromises()

    // --- 对话框确认创建 ---
    wrapper.vm.handleNewTask()
    await nextTick()
    wrapper.vm.newTaskForm.name = '测试SQL任务'
    wrapper.vm.newTaskForm.sqlType = 'MYSQL'
    wrapper.vm.newTaskForm.datasourceId = 1

    mockCreateTask.mockResolvedValue({
      code: 200,
      data: { id: 42, name: '测试SQL任务', sqlContent: '' },
    })

    wrapper.vm.handleNewTaskConfirm()
    await flushPromises()
    await nextTick()

    // 确认创建后直接进入编辑模式，currentTaskId 已设置，内容为空
    expect(mockCreateTask).toHaveBeenCalledWith({
      name: '测试SQL任务',
      sqlContent: '',
      datasourceId: 1,
    })
    expect(wrapper.vm.mode).toBe('edit')
    expect(wrapper.vm.taskName).toBe('测试SQL任务')
    expect(wrapper.vm.currentTaskId).toBe(42)
    expect(mockEditorInstance.setValue).toHaveBeenCalledWith('')

    // 写入 SQL 内容后保存（已有 currentTaskId，走 updateTask）
    const editedSql = 'SELECT id, name, email\nFROM users\nWHERE status = 1\nORDER BY id'
    mockEditorInstance.getValue.mockReturnValue(editedSql)

    mockUpdateTask.mockResolvedValue({
      code: 200,
      data: { id: 42, name: '测试SQL任务', sqlContent: editedSql },
    })

    await wrapper.vm.handleSave()
    await flushPromises()

    expect(mockUpdateTask).toHaveBeenCalledWith({
      id: 42,
      name: '测试SQL任务',
      sqlContent: editedSql,
      datasourceId: 1,
    })
  })

  it('返回列表 - handleBackToList 正确清理编辑器状态', async () => {
    const wrapper = createWrapper()
    await flushPromises()

    // 先创建编辑器（handleNewTaskConfirm 内部调用 ensureEditor 创建编辑器）
    wrapper.vm.handleNewTask()
    await nextTick()
    wrapper.vm.newTaskForm.name = 'test'
    wrapper.vm.newTaskForm.sqlType = 'MYSQL'
    wrapper.vm.newTaskForm.datasourceId = 1

    mockCreateTask.mockResolvedValue({
      code: 200,
      data: { id: 1, name: 'test', sqlContent: '' },
    })

    wrapper.vm.handleNewTaskConfirm()
    await flushPromises()
    await nextTick()

    // 编辑器应已创建（内容为空）
    expect(mockEditorInstance.setValue).toHaveBeenCalledWith('')

    // 额外设置任务状态
    wrapper.vm.currentTaskId = 42
    wrapper.vm.currentTask = { id: 42, name: 'test' }
    wrapper.vm.taskName = 'test'

    // 执行返回列表
    wrapper.vm.handleBackToList()
    await nextTick()

    expect(wrapper.vm.mode).toBe('list')
    expect(wrapper.vm.currentTaskId).toBeNull()
    expect(wrapper.vm.currentTask).toBeNull()
    expect(wrapper.vm.taskName).toBe('')

    // 编辑器和 completion provider 被释放
    expect(mockEditorInstance.dispose).toHaveBeenCalled()

    // 返回时刷新任务列表
    expect(mockListTasks).toHaveBeenCalled()
  })

  it('编辑已有任务 - handleEditTask 加载任务详情并填充编辑器', async () => {
    const wrapper = createWrapper()
    await flushPromises()

    const taskDetail = {
      id: 10,
      name: '已有任务',
      sqlContent: 'SELECT COUNT(*) FROM orders',
      datasourceId: 1,
      status: 'DRAFT',
      sqlType: 'MYSQL',
    }
    mockGetTask.mockResolvedValue({ code: 200, data: taskDetail })

    // 模拟表格中双击/点击编辑
    wrapper.vm.handleEditTask({ id: 10, name: '已有任务' })
    await nextTick()
    await flushPromises()

    expect(wrapper.vm.mode).toBe('edit')
    expect(wrapper.vm.currentTaskId).toBe(10)
    expect(wrapper.vm.taskName).toBe('已有任务')

    // 任务详情加载后，编辑器填充已有SQL
    expect(mockGetTask).toHaveBeenCalledWith(10)
    expect(mockEditorInstance.setValue).toHaveBeenCalledWith('SELECT COUNT(*) FROM orders')
  })

  it('loadAllTableMetadata 加载所有库的表，前端可分别查询不同库的表', async () => {
    const wrapper = createWrapper()
    await flushPromises()

    // mock listAccessibleDatabases and listDatasourceTables
    wrapper.vm.selectedDatasource = 1

    // Need to mock listAccessibleDatabases since it's called inside loadAllTableMetadata
    const { listAccessibleDatabases } = await import('@/api/datasource')
    vi.mocked(listAccessibleDatabases).mockResolvedValue({
      code: 200,
      data: ['test', 'mysql'],
    })

    vi.mocked(listDatasourceTables)
      // first call: test db tables
      .mockResolvedValueOnce({
        code: 200,
        data: [
          { tableSchema: 'test', tableName: 'orders' },
          { tableSchema: 'test', tableName: 'users' },
          { tableSchema: 'test', tableName: 'products' },
        ],
      })
      // second call: mysql db tables
      .mockResolvedValueOnce({
        code: 200,
        data: [
          { tableSchema: 'mysql', tableName: 'help_topic' },
          { tableSchema: 'mysql', tableName: 'time_zone' },
        ],
      })

    await wrapper.vm.loadAllTableMetadata(1)
    await flushPromises()

    // 所有数据库列表已加载
    expect(wrapper.vm.availableDatabases).toEqual(['test', 'mysql'])
    // 默认库（test）的表可通过 databaseTables 查询
    expect(wrapper.vm.databaseTables.get('test')).toEqual(['orders', 'users', 'products'])
    // 跨库（mysql）的表也可通过 databaseTables 查询
    expect(wrapper.vm.databaseTables.get('mysql')).toEqual(['help_topic', 'time_zone'])
    // 默认库的表和跨库的表完全隔离
    expect(wrapper.vm.databaseTables.get('test')).not.toContain('help_topic')
    expect(wrapper.vm.databaseTables.get('test')).not.toContain('time_zone')
  })
})
