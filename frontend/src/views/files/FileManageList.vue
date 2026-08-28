<template>
  <Layout>
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>文件管理</span>
          <div class="header-actions">
            <el-input
              v-model="keyword"
              placeholder="搜索文件名 / 路径..."
              clearable
              style="width: 220px; margin-right: 12px"
              @clear="handleSearch"
              @keyup.enter="handleSearch"
            />
            <el-select v-model="fileExt" placeholder="类型" clearable style="width: 110px; margin-right: 12px" @change="handleSearch">
              <el-option label="jar" value="jar" />
              <el-option label="py" value="py" />
              <el-option label="sql" value="sql" />
              <el-option label="csv" value="csv" />
            </el-select>
            <el-button @click="handleSearch">搜索</el-button>
            <el-button @click="loadFiles()">
              <el-icon><Refresh /></el-icon> 刷新
            </el-button>
            <el-input v-model="uploadDescription" placeholder="上传备注(可选)" style="width: 160px; margin-right: 12px" />
            <label class="upload-btn-wrap">
              <el-button type="primary" :loading="uploading">
                <el-icon><Upload /></el-icon> 上传文件
              </el-button>
              <input type="file" class="file-input" :disabled="uploading" @change="onFileChange" />
            </label>
          </div>
        </div>
      </template>

      <el-table :data="files" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="fileName" label="文件名" min-width="200" show-overflow-tooltip />
        <el-table-column prop="filePath" label="HDFS 路径" min-width="260" show-overflow-tooltip />
        <el-table-column label="大小" width="100">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="类型" width="80">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.fileExt || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="备注" min-width="130" show-overflow-tooltip />
        <el-table-column label="上传时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handleDownload(row)">
              <el-icon><Download /></el-icon> 下载
            </el-button>
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
        @current-change="loadFiles"
        @size-change="loadFiles"
      />
    </el-card>
  </Layout>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import Layout from '@/components/common/Layout.vue'
import { Download, Refresh, Upload } from '@element-plus/icons-vue'
import { downloadFile, uploadFile, listFilesPage, deleteFile } from '@/api/file'
import type { FileManage } from '@/types'

const keyword = ref('')
const fileExt = ref('')
const files = ref<FileManage[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

const uploading = ref(false)
const uploadDescription = ref('')

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

async function loadFiles() {
  loading.value = true
  try {
    const res = await listFilesPage(keyword.value || undefined, fileExt.value || undefined, page.value, pageSize.value)
    files.value = res.data.data
    total.value = res.data.total
  } catch (e: any) {
    ElMessage.error(e.message || '加载文件列表失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  loadFiles()
}

async function onFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  const raw = input.files?.[0]
  if (!raw) return
  try {
    uploading.value = true
    const res = await uploadFile(raw, uploadDescription.value || undefined)
    ElMessage.success(`文件「${res.data.fileName}」上传成功`)
    uploadDescription.value = ''
    await loadFiles()
  } catch (err: any) {
    ElMessage.error(err.message || '上传失败')
  } finally {
    uploading.value = false
    input.value = ''
  }
}

async function handleDownload(row: FileManage) {
  try {
    const blob = await downloadFile(row.id)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = row.fileName
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(url)
  } catch (e: any) {
    ElMessage.error(e.message || '下载失败')
  }
}

async function handleDelete(row: FileManage) {
  try {
    await ElMessageBox.confirm(`确定删除文件「${row.fileName}」？此操作会同时删除 HDFS 上的文件。`, '确认删除', { type: 'warning' })
    await deleteFile(row.id)
    ElMessage.success('已删除')
    await loadFiles()
  } catch (e: any) {
    if (e?.message) ElMessage.error(e.message)
    // 取消时不提示
  }
}

onMounted(() => {
  loadFiles()
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
.upload-btn-wrap {
  position: relative;
  display: inline-flex;
  margin-left: 12px;
  cursor: pointer;
}
.file-input {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  opacity: 0;
  cursor: pointer;
}
</style>
