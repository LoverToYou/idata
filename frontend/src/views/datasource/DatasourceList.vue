<template>
  <Layout>
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>数据源列表</span>
          <el-button type="primary" @click="$router.push('/datasource/create')">
            <el-icon><Plus /></el-icon> 新建数据源
          </el-button>
        </div>
      </template>

      <el-table :data="datasources" stripe v-loading="loading">
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
    </el-card>
  </Layout>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import Layout from '@/components/common/Layout.vue'
import { listDatasources, deleteDatasource, testConnectionById } from '@/api/datasource'
import type { DatasourceConfig } from '@/types'

const datasources = ref<DatasourceConfig[]>([])
const loading = ref(false)

onMounted(() => fetchData())

async function fetchData() {
  loading.value = true
  try {
    const res = await listDatasources()
    datasources.value = res.data
  } finally {
    loading.value = false
  }
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
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
