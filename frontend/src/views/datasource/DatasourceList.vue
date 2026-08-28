<template>
  <Layout>
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>数据源列表</span>
          <div class="header-actions">
            <el-input
              v-model="keyword"
              placeholder="搜索数据源名称..."
              clearable
              style="width: 240px; margin-right: 12px"
              @clear="handleSearch"
              @keyup.enter="handleSearch"
            />
            <el-button @click="handleSearch">搜索</el-button>
            <el-button
              v-if="selectedIds.length > 0"
              type="danger"
              @click="handleBatchDelete"
            >
              <el-icon><Delete /></el-icon> 批量删除 ({{ selectedIds.length }})
            </el-button>
            <el-button type="primary" @click="$router.push('/datasource/create')">
              <el-icon><Plus /></el-icon> 新建数据源
            </el-button>
          </div>
        </div>
      </template>

      <el-table :data="datasources" stripe v-loading="loading" @selection-change="onSelectionChange">
        <el-table-column type="selection" width="50" />
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="名称" min-width="150" />
        <el-table-column prop="type" label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.type === 'MYSQL' ? 'success' : 'warning'">
              {{ row.type }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="host" label="主机地址" width="160" />
        <el-table-column prop="port" label="端口" width="80" />
        <el-table-column prop="databaseName" label="数据库" min-width="120" />
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column prop="updatedAt" label="更新时间" width="180" />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="testConn(row)">
              测试连接
            </el-button>
            <el-button size="small" @click="$router.push(`/datasource/${row.id}/edit`)">
              编辑
            </el-button>
            <el-button
              v-if="row.type === 'HIVE'"
              size="small"
              @click="$router.push(`/datasource/${row.id}/hive`)"
            >
              元数据
            </el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-if="total > 0"
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[5, 10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        background
        small
        style="margin-top: 16px; justify-content: flex-end;"
        @current-change="handlePageChange"
        @size-change="handlePageChange"
      />
    </el-card>
  </Layout>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import Layout from '@/components/common/Layout.vue'
import { listDatasources, listDatasourcesPage, deleteDatasource, deleteDatasourceBatch, testConnectionById } from '@/api/datasource'
import type { DatasourceConfig } from '@/types'

const datasources = ref<DatasourceConfig[]>([])
const loading = ref(false)
const keyword = ref('')
const selectedIds = ref<number[]>([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

onMounted(() => fetchData())

function onSelectionChange(rows: DatasourceConfig[]) {
  selectedIds.value = rows.map(r => r.id)
}

async function fetchData() {
  loading.value = true
  try {
    const res = await listDatasourcesPage(keyword.value || undefined, page.value, pageSize.value)
    datasources.value = res.data.data
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function handlePageChange() {
  fetchData()
}

function handleSearch() {
  page.value = 1
  fetchData()
}

async function testConn(row: DatasourceConfig) {
  try {
    await testConnectionById(row.id)
    ElMessage.success('连接成功')
  } catch (e: any) {
    ElMessage.error(e.message || '连接失败')
  }
}

async function handleDelete(row: DatasourceConfig) {
  try {
    await ElMessageBox.confirm(`确定删除数据源 "${row.name}" 吗？`, '提示')
    await deleteDatasource(row.id)
    ElMessage.success('删除成功')
    await fetchData()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '删除失败')
    }
  }
}

async function handleBatchDelete() {
  const count = selectedIds.value.length
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${count} 个数据源吗？`, '提示')
    await deleteDatasourceBatch(selectedIds.value)
    ElMessage.success(`成功删除 ${count} 个数据源`)
    selectedIds.value = []
    await fetchData()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '批量删除失败')
    }
  }
}
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
  gap: 12px;
}
</style>
