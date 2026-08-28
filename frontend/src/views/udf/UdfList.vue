<template>
  <Layout>
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>UDF 函数管理</span>
          <div class="header-actions">
            <el-input
              v-model="keyword"
              placeholder="搜索函数名 / 类名 / JAR..."
              clearable
              style="width: 240px; margin-right: 12px"
              @clear="handleSearch"
              @keyup.enter="handleSearch"
            />
            <el-button @click="handleSearch">搜索</el-button>
            <el-button @click="loadUdfs()">
              <el-icon><Refresh /></el-icon> 刷新
            </el-button>
            <el-button type="primary" @click="openCreateDialog">
              <el-icon><Plus /></el-icon> 新建 UDF
            </el-button>
          </div>
        </div>
      </template>

      <el-table :data="udfs" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="函数名" min-width="150">
          <template #default="{ row }">
            <span class="func-name">{{ row.databaseName }}.{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="className" label="实现类" min-width="200" show-overflow-tooltip />
        <el-table-column prop="jarFileName" label="JAR 文件" min-width="160" show-overflow-tooltip />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.functionType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="数据源" width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.datasourceName || row.datasourceId }}</template>
        </el-table-column>
        <el-table-column label="注册状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.registerStatus)">{{ statusText(row.registerStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="170">
          <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.registerStatus !== 'REGISTERED'" size="small" type="success" plain @click="handleRegister(row)">注册</el-button>
            <el-button v-else size="small" type="warning" plain @click="handleUnregister(row)">注销</el-button>
            <el-button size="small" @click="handleVerify(row)">校验</el-button>
            <el-button size="small" @click="openDetail(row)">详情</el-button>
            <el-button size="small" @click="openEditDialog(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-if="total > 0"
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        background
        small
        style="margin-top: 16px; justify-content: flex-end"
        @current-change="loadUdfs"
        @size-change="loadUdfs"
      />
    </el-card>

    <!-- Create / Edit dialog -->
    <el-dialog v-model="dialogVisible" :title="isEditing ? '编辑 UDF' : '新建 UDF'" width="640px" :close-on-click-modal="false">
      <el-form :model="form" ref="formRef" :rules="formRules" label-width="120px">
        <el-form-item label="数据源" prop="datasourceId">
          <el-select v-model="form.datasourceId" placeholder="请选择 Hive 数据源" style="width: 100%" :disabled="isEditing">
            <el-option v-for="ds in datasources" :key="ds.id" :label="`${ds.name} (${ds.host}:${ds.port})`" :value="ds.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="JAR 文件">
          <div class="upload-row">
            <el-button :disabled="!form.datasourceId" @click="openFilePicker">
              <el-icon><FolderOpened /></el-icon> 选择 JAR 文件
            </el-button>
            <span v-if="selectedFile" class="jar-info">
              {{ selectedFile.fileName }} ({{ formatSize(selectedFile.fileSize) }})
              <el-tag size="small" type="success" effect="plain" style="margin-left: 6px">已选择</el-tag>
            </span>
            <span v-else-if="isEditing" class="jar-info muted">{{ currentJarFileName }}</span>
            <span v-else-if="!form.datasourceId" class="jar-info muted">请先选择数据源，再从 HDFS 文件管理目录中选择 JAR</span>
          </div>
        </el-form-item>
        <el-form-item label="函数名" prop="name">
          <el-input v-model="form.name" placeholder="例如 udf_encrypt" />
        </el-form-item>
        <el-form-item label="实现类" prop="className">
          <el-input v-model="form.className" placeholder="例如 com.example.MyUDF" />
        </el-form-item>
        <el-form-item label="Hive 数据库" prop="databaseName">
          <el-input v-model="form.databaseName" placeholder="注册到哪个数据库，默认 default" />
        </el-form-item>
        <el-form-item label="函数类型">
          <el-select v-model="form.functionType" style="width: 100%">
            <el-option label="UDF（普通函数）" value="UDF" />
            <el-option label="UDAF（聚合函数）" value="UDAF" />
            <el-option label="UDTF（表生成函数）" value="UDTF" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="函数用途说明（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- File picker dialog -->
    <el-dialog v-model="filePickerVisible" title="选择 JAR 文件" width="640px">
      <div class="picker-toolbar">
        <el-input
          v-model="pickerKeyword"
          placeholder="搜索文件名"
          clearable
          style="width: 220px; margin-right: 12px"
          @clear="loadPickerJars"
          @keyup.enter="loadPickerJars"
        />
        <el-button @click="loadPickerJars">搜索</el-button>
        <div class="spacer" />
        <el-button type="primary" plain @click="goFileManage">
          <el-icon><FolderOpened /></el-icon> 前往文件管理上传
        </el-button>
      </div>
      <el-table :data="jarFiles" stripe v-loading="pickerLoading" height="360" @row-click="selectJar">
        <el-table-column prop="fileName" label="文件名" min-width="200" show-overflow-tooltip />
        <el-table-column label="大小" width="100">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="上传时间" width="150">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click.stop="selectJar(row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="jarFiles.length === 0 && !pickerLoading" class="empty-tip">
        暂无 jar 文件，请先前往<a class="link" @click="goFileManage">文件管理</a>上传
      </div>
      <template #footer>
        <el-button @click="filePickerVisible = false">取消</el-button>
      </template>
    </el-dialog>

    <!-- Detail dialog -->
    <el-dialog v-model="detailVisible" :title="`UDF 详情：${detail?.name || ''}`" width="720px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="函数名">{{ detail?.databaseName }}.{{ detail?.name }}</el-descriptions-item>
        <el-descriptions-item label="实现类">{{ detail?.className }}</el-descriptions-item>
        <el-descriptions-item label="JAR 文件">{{ detail?.jarFileName }}（{{ formatSize(detail?.jarSize) }}）</el-descriptions-item>
        <el-descriptions-item label="JAR 路径">{{ detail?.jarPath }}</el-descriptions-item>
        <el-descriptions-item label="注册状态">
          <el-tag size="small" :type="statusType(detail?.registerStatus)">{{ statusText(detail?.registerStatus) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="注册结果">{{ detail?.registerMessage || '-' }}</el-descriptions-item>
        <el-descriptions-item label="注册 SQL">
          <pre class="sql-pre">{{ detail?.registerSql || '-' }}</pre>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </Layout>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import Layout from '@/components/common/Layout.vue'
import { Refresh, Plus, FolderOpened } from '@element-plus/icons-vue'
import { listDatasources } from '@/api/datasource'
import { listHdfsJars } from '@/api/file'
import {
  listUdfsPage,
  createUdf,
  updateUdf,
  deleteUdf,
  registerUdf,
  unregisterUdf,
  verifyUdf,
} from '@/api/udf'
import type { DatasourceConfig, FileManage, UdfDefinition, UdfDefinitionRequest } from '@/types'

const router = useRouter()

const keyword = ref('')
const udfs = ref<UdfDefinition[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

const datasources = ref<DatasourceConfig[]>([])

// ---- 表单 ----
const dialogVisible = ref(false)
const isEditing = ref(false)
const currentId = ref<number | null>(null)
const currentJarFileName = ref('')
const selectedFile = ref<FileManage | null>(null)
const saving = ref(false)
const formRef = ref()
const form = reactive({
  datasourceId: undefined as number | undefined,
  name: '',
  className: '',
  databaseName: 'default',
  functionType: 'UDF',
  description: '',
  /** 选中 HDFS jar 的完整 URI（隐形映射，UI 只展示文件名） */
  jarPath: undefined as string | undefined,
})
const formRules = {
  datasourceId: [{ required: true, message: '请选择数据源', trigger: 'change' }],
  name: [{ required: true, message: '请输入函数名', trigger: 'blur' }],
  className: [{ required: true, message: '请输入实现类全限定名', trigger: 'blur' }],
}

// ---- 文件选择 ----
const filePickerVisible = ref(false)
const pickerKeyword = ref('')
const jarFiles = ref<FileManage[]>([])
const pickerLoading = ref(false)

// ---- 详情 ----
const detailVisible = ref(false)
const detail = ref<UdfDefinition | null>(null)

function formatTime(t: string) {
  if (!t) return ''
  return t.slice(0, 16).replace('T', ' ')
}

function formatSize(size: number | undefined) {
  if (size == null) return ''
  if (size < 1024) return size + ' B'
  if (size < 1024 * 1024) return (size / 1024).toFixed(1) + ' KB'
  return (size / 1024 / 1024).toFixed(1) + ' MB'
}

function statusType(s: string | undefined) {
  if (s === 'REGISTERED') return 'success'
  if (s === 'FAILED') return 'danger'
  return 'info'
}

function statusText(s: string | undefined) {
  if (s === 'REGISTERED') return '已注册'
  if (s === 'UNREGISTERED') return '未注册'
  if (s === 'FAILED') return '失败'
  return s || '-'
}

async function loadDatasources() {
  try {
    const res = await listDatasources()
    datasources.value = res.data.filter((ds: DatasourceConfig) => ds.type === 'HIVE')
  } catch (e: any) {
    ElMessage.error(e.message || '加载数据源失败')
  }
}

async function loadUdfs() {
  loading.value = true
  try {
    const res = await listUdfsPage(keyword.value || undefined, undefined, undefined, page.value, pageSize.value)
    udfs.value = res.data.data
    total.value = res.data.total
  } catch (e: any) {
    ElMessage.error(e.message || '加载 UDF 列表失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  loadUdfs()
}

// ---- 从 HDFS 文件管理目录选 jar（映射隐形：UI 只显示文件名，jar 完整 URI 存 form.jarPath） ----
async function loadPickerJars() {
  pickerLoading.value = true
  try {
    const res = await listHdfsJars()
    const kw = pickerKeyword.value?.trim().toLowerCase()
    jarFiles.value = kw ? res.data.filter(f => f.fileName.toLowerCase().includes(kw)) : res.data
  } catch (e: any) {
    ElMessage.error(e.message || '加载 HDFS jar 文件失败')
  } finally {
    pickerLoading.value = false
  }
}

function openFilePicker() {
  pickerKeyword.value = ''
  jarFiles.value = []
  filePickerVisible.value = true
  loadPickerJars()
}

function selectJar(file: FileManage) {
  selectedFile.value = file
  form.jarPath = file.filePath
  filePickerVisible.value = false
}

function goFileManage() {
  filePickerVisible.value = false
  router.push('/files')
}

// ---- 新建 / 编辑 ----
function openCreateDialog() {
  isEditing.value = false
  currentId.value = null
  currentJarFileName.value = ''
  selectedFile.value = null
  form.datasourceId = undefined
  form.name = ''
  form.className = ''
  form.databaseName = 'default'
  form.functionType = 'UDF'
  form.description = ''
  form.jarPath = undefined
  dialogVisible.value = true
}

function openEditDialog(row: UdfDefinition) {
  isEditing.value = true
  currentId.value = row.id
  currentJarFileName.value = row.jarFileName
  selectedFile.value = null
  form.datasourceId = row.datasourceId
  form.name = row.name
  form.className = row.className
  form.databaseName = row.databaseName || 'default'
  form.functionType = row.functionType || 'UDF'
  form.description = row.description || ''
  form.jarPath = row.jarPath
  dialogVisible.value = true
}

async function handleSave() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  if (!isEditing.value && !form.jarPath) {
    ElMessage.warning('请先选择 JAR 文件')
    return
  }
  // 从文件管理 HDFS 选中的 jar 以完整 URI 提交，映射对用户隐形
  const jarPath = form.jarPath
  const payload: UdfDefinitionRequest = {
    id: isEditing.value ? currentId.value! : undefined,
    name: form.name,
    className: form.className,
    jarPath,
    databaseName: form.databaseName,
    datasourceId: form.datasourceId!,
    functionType: form.functionType,
    description: form.description,
  }
  saving.value = true
  try {
    if (isEditing.value) {
      await updateUdf(payload)
      ElMessage.success('UDF 已更新')
    } else {
      await createUdf(payload)
      ElMessage.success('UDF 已创建')
    }
    dialogVisible.value = false
    await loadUdfs()
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

// ---- 注册相关 ----
async function handleRegister(row: UdfDefinition) {
  try {
    const res = await registerUdf(row.id)
    if (res.data.registerStatus === 'REGISTERED') {
      ElMessage.success('注册成功')
    } else {
      ElMessage.error(`注册失败：${res.data.registerMessage || '未知错误'}`)
    }
    await loadUdfs()
  } catch (e: any) {
    ElMessage.error(e.message || '注册失败')
  }
}

async function handleUnregister(row: UdfDefinition) {
  try {
    await ElMessageBox.confirm(`确定注销函数「${row.databaseName}.${row.name}」？其他 SQL 将无法再调用它。`, '确认注销', { type: 'warning' })
    const res = await unregisterUdf(row.id)
    if (res.data.registerStatus === 'UNREGISTERED') {
      ElMessage.success('已注销')
    } else {
      ElMessage.error(`注销失败：${res.data.registerMessage || '未知错误'}`)
    }
    await loadUdfs()
  } catch { /* cancelled */ }
}

async function handleVerify(row: UdfDefinition) {
  try {
    const res = await verifyUdf(row.id)
    ElMessage.success(`校验完成：${res.data.registerMessage}`)
    await loadUdfs()
  } catch (e: any) {
    ElMessage.error(e.message || '校验失败')
  }
}

async function handleDelete(row: UdfDefinition) {
  try {
    await ElMessageBox.confirm(`确定删除函数「${row.databaseName}.${row.name}」？（JAR 文件保留在文件管理中）`, '确认删除', { type: 'warning' })
    await deleteUdf(row.id)
    ElMessage.success('已删除')
    await loadUdfs()
  } catch { /* cancelled */ }
}

function openDetail(row: UdfDefinition) {
  detail.value = row
  detailVisible.value = true
}

onMounted(() => {
  loadDatasources()
  loadUdfs()
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
}
.func-name {
  font-weight: 500;
  color: #409eff;
}
.upload-row {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
}
.jar-info {
  font-size: 13px;
}
.muted {
  color: #909399;
}
.picker-toolbar {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
}
.spacer {
  flex: 1;
}
.empty-tip {
  text-align: center;
  color: #909399;
  font-size: 13px;
  padding: 24px 0;
}
.link {
  color: #409eff;
  cursor: pointer;
  margin: 0 4px;
}
.sql-pre {
  background: #1e1e1e;
  color: #d4d4d4;
  border-radius: 4px;
  padding: 10px;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
}
</style>
