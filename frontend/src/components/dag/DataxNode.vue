<template>
  <div class="datax-node" :class="{ selected }">
    <Handle type="target" :position="Position.Left" :isConnectable="connectable" />
    <div class="node-body">
      <div class="node-header">
        <el-icon :size="16" color="#fa8c16"><Share /></el-icon>
        <span class="node-label">{{ label || data?.label }}</span>
      </div>
      <div class="node-meta">
        <el-tag size="small" type="danger" effect="plain">ETL</el-tag>
      </div>
    </div>
    <Handle type="source" :position="Position.Right" :isConnectable="connectable" />
  </div>
</template>

<script setup lang="ts">
import { Handle, Position } from '@vue-flow/core'

interface Props {
  id: string
  type: string
  data?: {
    nodeType: string
    label?: string
    config?: Record<string, any>
  }
  label?: string
  selected?: boolean
  connectable?: boolean
}

withDefaults(defineProps<Props>(), {
  selected: false,
  connectable: true,
})
</script>

<style scoped>
.datax-node {
  background: #fff7e6;
  border: 2px solid #ffd591;
  border-radius: 8px;
  padding: 10px 14px;
  min-width: 150px;
  cursor: pointer;
  transition: box-shadow 0.2s, border-color 0.2s;
}

.datax-node:hover {
  box-shadow: 0 2px 8px rgba(250, 140, 22, 0.15);
}

.datax-node.selected {
  border-color: #fa8c16;
  box-shadow: 0 0 0 2px rgba(250, 140, 22, 0.3);
}

.node-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.node-header {
  display: flex;
  align-items: center;
  gap: 6px;
}

.node-label {
  font-size: 13px;
  font-weight: 600;
  color: #333;
  white-space: nowrap;
}

.node-meta {
  display: flex;
  justify-content: center;
}
</style>
