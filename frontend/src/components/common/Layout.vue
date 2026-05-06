<template>
  <el-container class="layout-container">
    <el-aside width="220px" class="sidebar">
      <div class="logo">
        <span class="logo-text">IDATA</span>
      </div>
      <el-menu
        :default-active="activeRoute"
        router
        class="side-menu"
      >
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon>
          <span>工作台</span>
        </el-menu-item>
        <el-menu-item index="/datasource">
          <el-icon><Connection /></el-icon>
          <span>数据源管理</span>
        </el-menu-item>
        <el-menu-item index="/sql-task">
          <el-icon><Document /></el-icon>
          <span>SQL 任务管理</span>
        </el-menu-item>
        <el-menu-item index="/workflow">
          <el-icon><Share /></el-icon>
          <span>ETL 任务管理</span>
        </el-menu-item>
        <el-menu-item index="/monitor">
          <el-icon><Monitoring /></el-icon>
          <span>任务监控</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span class="page-title">{{ pageTitle }}</span>
      </el-header>
      <el-main class="main-content">
        <slot />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { Odometer, Connection, Document, Share, Monitor as Monitoring } from '@element-plus/icons-vue'

const route = useRoute()
const activeRoute = computed(() => route.path)
const pageTitle = computed(() => (route.meta.title as string) || 'IDATA')
</script>

<style scoped>
.layout-container {
  height: 100vh;
}

.sidebar {
  background-color: #304156;
  overflow-y: auto;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.logo-text {
  font-size: 24px;
  font-weight: bold;
  color: #fff;
  letter-spacing: 2px;
}

.side-menu {
  border-right: none;
  background-color: #304156;
}

.side-menu .el-menu-item {
  color: #bfcbd9;
}

.side-menu .el-menu-item:hover {
  background-color: #3b4a5a;
}

.side-menu .el-menu-item.is-active {
  color: #409eff;
  background-color: #263445;
}

.header {
  background-color: #fff;
  border-bottom: 1px solid #e6e6e6;
  display: flex;
  align-items: center;
  height: 60px;
  padding: 0 20px;
}

.page-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.main-content {
  background-color: #f0f2f5;
  padding: 20px;
  overflow-y: auto;
}
</style>
