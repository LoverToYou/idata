<template>
  <div class="sink-node" :class="{ selected }">
    <Handle type="target" :position="Position.Left" :isConnectable="connectable" />
    <div class="node-body">
      <div class="node-header">
        <el-icon :size="16" color="#67c23a"><EditPen /></el-icon>
        <span class="node-label">{{ label || data?.label }}</span>
      </div>
      <div class="node-meta">
        <el-tag size="small" type="success" effect="plain">Sink</el-tag>
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
.sink-node {
  background: #f0f9eb;
  border: 2px solid #b7eb8f;
  border-radius: 8px;
  padding: 10px 14px;
  min-width: 150px;
  cursor: pointer;
  transition: box-shadow 0.2s, border-color 0.2s;
}

.sink-node:hover {
  box-shadow: 0 2px 8px rgba(103, 194, 58, 0.15);
}

.sink-node.selected {
  border-color: #67c23a;
  box-shadow: 0 0 0 2px rgba(103, 194, 58, 0.3);
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
