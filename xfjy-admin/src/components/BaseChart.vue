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
let observer: ResizeObserver | null = null
let rafId = 0

/** 用 rAF 合并高频 resize（侧边栏过渡期间会连续触发） */
function resize() {
  if (rafId) cancelAnimationFrame(rafId)
  rafId = requestAnimationFrame(() => chart?.resize())
}

onMounted(async () => {
  await nextTick()
  if (!chartRef.value) return

  chart = echarts.init(chartRef.value)
  chart.setOption(props.option)

  // 监听容器尺寸变化：窗口缩放、侧边栏折叠、卡片布局变化都能自适应
  observer = new ResizeObserver(resize)
  observer.observe(chartRef.value)
})

// 复用实例，notMerge=true 保证系列切换后不残留旧数据（不 dispose，避免闪白）
watch(
  () => props.option,
  (option) => {
    chart?.setOption(option, true)
  },
  { deep: true }
)

onBeforeUnmount(() => {
  if (rafId) cancelAnimationFrame(rafId)
  observer?.disconnect()
  observer = null
  chart?.dispose()
  chart = null
})

defineExpose({ resize })
</script>

<style scoped>
.base-chart {
  width: 100%;
}
</style>
