<template>
  <div ref="chartRef" class="base-chart" :style="{ height: height || '200px' }"></div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import * as echarts from 'echarts'

const props = defineProps<{
  option: echarts.EChartsOption
  height?: string
}>()

const chartRef = ref<HTMLElement>()
let chart: echarts.ECharts | null = null

function render() {
  if (!chartRef.value) return
  chart?.dispose()
  chart = echarts.init(chartRef.value)
  chart.setOption(props.option)
}

function resize() {
  chart?.resize()
}

onMounted(async () => {
  await nextTick()
  render()
})

watch(() => props.option, render, { deep: true })

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  chart?.dispose()
  chart = null
})

window.addEventListener('resize', resize)

defineExpose({ resize })
</script>

<style scoped>
.base-chart {
  width: 100%;
}
</style>
