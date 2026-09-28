<template>
  <div ref="chartEl" class="echart-container" :style="{ height }" />
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

const props = withDefaults(
  defineProps<{
    option: any
    height?: string
    theme?: string
  }>(),
  { height: '360px' },
)

const emit = defineEmits<{ (e: 'chart-click', params: any): void }>()

const chartEl = ref<HTMLDivElement>()
let chart: echarts.ECharts | null = null
let resizeObserver: ResizeObserver | null = null

function render() {
  if (!chart) return
  if (!props.option) {
    chart.clear()
    return
  }
  chart.setOption(props.option, true)
}

onMounted(() => {
  if (!chartEl.value) return
  chart = echarts.init(chartEl.value, props.theme)
  chart.on('click', (params: any) => emit('chart-click', params))
  render()
  resizeObserver = new ResizeObserver(() => chart?.resize())
  resizeObserver.observe(chartEl.value)
})

watch(
  () => props.theme,
  (theme) => {
    if (!chartEl.value) return
    chart?.dispose()
    chart = echarts.init(chartEl.value, theme)
    chart.on('click', (params: any) => emit('chart-click', params))
    render()
  },
)

watch(() => props.option, render, { deep: true })

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
.echart-container {
  width: 100%;
}
</style>
