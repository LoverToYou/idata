<template>
  <Layout>
    <el-card shadow="hover" class="page-card">
          <template #header>
            <div class="card-header">
              <span class="header-left">
                报表管理
                <el-tag v-if="currentFolderLabel" closable size="small" @close="selectedFolder = 'all'">
                  {{ currentFolderLabel }}
                </el-tag>
              </span>
              <div class="header-actions">
                <el-button @click="handleCreateFolder">
                  <el-icon><FolderAdd /></el-icon> 新建文件夹
                </el-button>
                <el-button type="primary" @click="router.push('/report/create')">
                  <el-icon><Plus /></el-icon> 新建报表
                </el-button>
              </div>
            </div>
          </template>

          <div class="page-body">
            <div class="side-pane">
              <FolderTree
                title="报表目录"
                biz-type="REPORT"
                v-model="selectedFolder"
                :folders="folders"
                :items="reports"
                @changed="loadAll"
              />
            </div>
            <div class="list-pane" ref="listPaneEl">
          <el-table :data="filteredReports" stripe v-loading="loading" :height="tableHeight">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="name" label="报表名称" min-width="180" show-overflow-tooltip />
            <el-table-column prop="description" label="描述" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">{{ row.description || '-' }}</template>
            </el-table-column>
            <el-table-column prop="datasourceName" label="数据源" width="130" show-overflow-tooltip>
              <template #default="{ row }">{{ row.datasourceName || `#${row.datasourceId}` }}</template>
            </el-table-column>
            <el-table-column label="展示形式" width="100">
              <template #default="{ row }">
                <el-tag :type="chartTagType(row.chartType)" effect="plain">{{ chartLabel(row.chartType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="文件夹" width="120" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="row.folderId">{{ folderName(row.folderId) }}</span>
                <span v-else class="muted-text">未分组</span>
              </template>
            </el-table-column>
            <el-table-column label="更新时间" width="160">
              <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="270" fixed="right">
              <template #default="{ row }">
                <el-button size="small" type="primary" plain @click="router.push(`/report/${row.id}/view`)">查看</el-button>
                <el-button size="small" @click="router.push(`/report/${row.id}/edit`)">编辑</el-button>
                <el-button size="small" @click="openMove(row)">移动</el-button>
                <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
            </div>
          </div>
    </el-card>

    <el-dialog v-model="moveVisible" title="移动到文件夹" width="420px">
      <el-tree-select
        v-model="moveFolderId"
        :data="folderOptions"
        check-strictly
        clearable
        default-expand-all
        placeholder="选择文件夹（留空 = 未分组）"
        style="width: 100%"
      />
      <div class="hint-block">留空表示移出到「未分组」。</div>
      <template #footer>
        <el-button @click="moveVisible = false">取消</el-button>
        <el-button type="primary" @click="handleMove">确定</el-button>
      </template>
    </el-dialog>
  </Layout>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useWindowSize } from '@vueuse/core'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, FolderAdd } from '@element-plus/icons-vue'
import Layout from '@/components/common/Layout.vue'
import FolderTree from '@/components/common/FolderTree.vue'
import { deleteReport, listReports, moveReport, type ReportItem } from '@/api/report'
import {
  buildFolderTree,
  createFolder,
  descendantFolderIds,
  listFolders,
  type FolderItem,
  type FolderSelection,
} from '@/api/folder'

const router = useRouter()
const reports = ref<ReportItem[]>([])
const folders = ref<FolderItem[]>([])
const loading = ref(false)
const selectedFolder = ref<FolderSelection>('all')

const moveVisible = ref(false)
const moveTarget = ref<ReportItem | null>(null)
const moveFolderId = ref<number | null>(null)

// 表格自适应高度，填满卡片区域
const listPaneEl = ref<HTMLElement>()
const tableHeight = ref(420)

function measureTable() {
  if (listPaneEl.value) {
    tableHeight.value = Math.max(240, listPaneEl.value.clientHeight - 2)
  }
}

const { width: winW, height: winH } = useWindowSize()
watch([winW, winH], () => measureTable())

const folderOptions = computed(() => buildFolderTree(folders.value))

const currentFolderLabel = computed(() => {
  if (selectedFolder.value === 'all') return ''
  if (selectedFolder.value === 'none') return '未分组'
  return folderName(selectedFolder.value as number)
})

const filteredReports = computed(() => {
  const sel = selectedFolder.value
  if (sel === 'all') return reports.value
  if (sel === 'none') return reports.value.filter((r) => !r.folderId)
  const ids = descendantFolderIds(folders.value, sel)
  return reports.value.filter((r) => r.folderId && ids.includes(r.folderId))
})

function folderName(id?: number | null) {
  if (!id) return ''
  return folders.value.find((f) => f.id === id)?.name || `#${id}`
}

function formatTime(t?: string) {
  if (!t) return ''
  return t.slice(0, 16).replace('T', ' ')
}

function chartLabel(type?: string) {
  return { TABLE: '表格', LINE: '折线图', BAR: '柱状图', PIE: '饼图' }[(type || 'TABLE').toUpperCase()] || '表格'
}

function chartTagType(type?: string) {
  return { TABLE: 'info', LINE: 'primary', BAR: 'success', PIE: 'warning' }[(type || 'TABLE').toUpperCase()] as any
}

async function loadAll() {
  loading.value = true
  try {
    const [reportRes, folderRes] = await Promise.all([listReports(), listFolders('REPORT')])
    reports.value = reportRes.data || []
    folders.value = folderRes.data || []
  } catch (e: any) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function handleCreateFolder() {
  try {
    const { value } = await ElMessageBox.prompt('文件夹名称', '新建报表文件夹', {
      inputPlaceholder: '请输入文件夹名称',
      inputValidator: (v: string) => (v && v.trim() ? true : '名称不能为空'),
    })
    await createFolder({ name: value.trim(), parentId: null, bizType: 'REPORT' })
    ElMessage.success('文件夹已创建')
    await loadAll()
  } catch {
    /* cancelled */
  }
}

function openMove(row: ReportItem) {
  moveTarget.value = row
  moveFolderId.value = row.folderId ?? null
  moveVisible.value = true
}

async function handleMove() {
  if (!moveTarget.value?.id) return
  try {
    await moveReport(moveTarget.value.id, moveFolderId.value ?? null)
    ElMessage.success('已移动')
    moveVisible.value = false
    await loadAll()
  } catch (e: any) {
    ElMessage.error(e.message || '移动失败')
  }
}

async function handleDelete(row: ReportItem) {
  try {
    await ElMessageBox.confirm(`确定删除报表「${row.name}」？`, '确认删除', { type: 'warning' })
    await deleteReport(row.id as number)
    ElMessage.success('已删除')
    await loadAll()
  } catch {
    /* cancelled */
  }
}

onMounted(async () => {
  await loadAll()
  await nextTick()
  measureTable()
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.folder-hint {
  font-size: 12px;
  color: var(--text-sub);
  margin-left: 6px;
}
.muted-text {
  color: var(--text-sub);
}
.hint-block {
  font-size: 12px;
  color: var(--text-sub);
  margin-top: 8px;
}
.page-body {
  display: flex;
  align-items: stretch;
  flex: 1;
  min-height: 0;
}
.side-pane {
  width: 208px;
  flex: 0 0 208px;
  padding-right: 12px;
  border-right: 1px solid var(--bg-app);
  display: flex;
  flex-direction: column;
}
.list-pane {
  flex: 1;
  min-width: 0;
  padding-left: 14px;
  display: flex;
  flex-direction: column;
}
</style>
