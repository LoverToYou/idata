<template>
  <Layout>
    <el-row :gutter="20">
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-value">{{ stats.datasourceCount }}</div>
            <div class="stat-label">数据源</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-value">{{ stats.sqlTaskCount }}</div>
            <div class="stat-label">SQL 任务</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-value">{{ stats.dataxTaskCount }}</div>
            <div class="stat-label">ETL 任务</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-value">{{ stats.workflowCount }}</div>
            <div class="stat-label">工作流</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

  </Layout>
</template>

<script setup lang="ts">
import { reactive, onMounted } from 'vue'
import Layout from '@/components/common/Layout.vue'
import { listDatasources } from '@/api/datasource'
import { listTasks } from '@/api/sql-task'
import { listDataxTasks } from '@/api/datax-task'
import { listWorkflows } from '@/api/workflow'
const stats = reactive({
  datasourceCount: 0,
  sqlTaskCount: 0,
  dataxTaskCount: 0,
  workflowCount: 0,
})

onMounted(async () => {
  try { const res = await listDatasources(); stats.datasourceCount = res.data.length } catch {}
  try { const res = await listTasks(); stats.sqlTaskCount = res.data.length } catch {}
  try { const res = await listDataxTasks(); stats.dataxTaskCount = res.data.length } catch {}
  try { const res = await listWorkflows(); stats.workflowCount = res.data.length } catch {}
})
</script>

<style scoped>
.stat-card {
  text-align: center;
  padding: 10px 0;
}

.stat-value {
  font-size: 36px;
  font-weight: bold;
  color: #409eff;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-top: 8px;
}
</style>
