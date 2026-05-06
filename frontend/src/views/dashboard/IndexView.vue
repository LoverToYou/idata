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
            <div class="stat-value">{{ stats.workflowCount }}</div>
            <div class="stat-label">工作流</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-value">{{ stats.runningCount }}</div>
            <div class="stat-label">运行中</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-value">{{ stats.failedCount }}</div>
            <div class="stat-label">失败任务</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="quick-actions" shadow="hover">
      <template #header>
        <span>快捷操作</span>
      </template>
      <el-row :gutter="20">
        <el-col :span="6">
          <el-button type="primary" @click="$router.push('/datasource/create')" class="action-btn">
            <el-icon><Plus /></el-icon> 新建数据源
          </el-button>
        </el-col>
        <el-col :span="6">
          <el-button type="success" @click="$router.push('/sql-task')" class="action-btn">
            <el-icon><Document /></el-icon> SQL 任务
          </el-button>
        </el-col>
        <el-col :span="6">
          <el-button type="warning" @click="$router.push('/workflow/create')" class="action-btn">
            <el-icon><Plus /></el-icon> 新建工作流
          </el-button>
        </el-col>
        <el-col :span="6">
          <el-button type="info" @click="$router.push('/monitor')" class="action-btn">
            <el-icon><Monitor /></el-icon> 任务监控
          </el-button>
        </el-col>
      </el-row>
    </el-card>
  </Layout>
</template>

<script setup lang="ts">
import { reactive, onMounted } from 'vue'
import Layout from '@/components/common/Layout.vue'
import { listDatasources } from '@/api/datasource'

const stats = reactive({
  datasourceCount: 0,
  workflowCount: 0,
  runningCount: 0,
  failedCount: 0,
})

onMounted(async () => {
  try {
    const res = await listDatasources()
    stats.datasourceCount = res.data.length
  } catch {
    // silently fail
  }
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

.quick-actions {
  margin-top: 20px;
}

.action-btn {
  width: 100%;
  height: 80px;
  font-size: 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
}
</style>
