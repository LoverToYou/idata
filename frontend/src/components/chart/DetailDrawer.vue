<template>
  <el-drawer
    :model-value="modelValue"
    :title="title"
    size="62%"
    @update:model-value="(v: boolean) => emit('update:modelValue', v)"
  >
    <div class="drawer-toolbar">
      <span class="drawer-meta">
        共 {{ rows.length }} 行
        <template v-if="filterDesc"> · {{ filterDesc }}</template>
      </span>
      <el-button size="small" :disabled="!rows.length" @click="handleExport">导出 CSV</el-button>
    </div>
    <el-table :data="rows" stripe border height="calc(100vh - 210px)" style="margin-top: 8px">
      <el-table-column
        v-for="c in columns"
        :key="c"
        :prop="c"
        :label="c"
        min-width="140"
        show-overflow-tooltip
      />
    </el-table>
  </el-drawer>
</template>

<script setup lang="ts">
import { exportCsv } from '@/utils/reportChart'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    title?: string
    columns: string[]
    rows: Record<string, any>[]
    filterDesc?: string
  }>(),
  { title: '数据明细', filterDesc: '' },
)

const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void }>()

function handleExport() {
  exportCsv(props.title || 'drill-data', props.columns, props.rows)
}
</script>

<style scoped>
.drawer-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.drawer-meta {
  font-size: 13px;
  color: var(--text-body);
}
</style>
