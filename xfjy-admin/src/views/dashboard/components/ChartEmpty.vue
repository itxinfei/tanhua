<template>
  <div
    class="chart-empty-state"
    :class="{ 'chart-empty-state--compact': compact }"
  >
    <span class="chart-empty-state__icon">
      <el-icon :size="compact ? 18 : 20">
        <DataAnalysis />
      </el-icon>
    </span>
    <span class="chart-empty-state__text">{{ description }}</span>
  </div>
</template>

<script setup lang="ts">
import { DataAnalysis } from '@element-plus/icons-vue'

defineProps<{
  /** 空态文案 */
  description: string
  /** 紧凑档：用于 180px 高的小图区（对应旧 el-empty image-size 60） */
  compact?: boolean
}>()
</script>

<style scoped>
/* 替代 EP 默认折纸插画：灰底圆角线性图标 + 一句话，与后台卡片语言统一。
   外层 .chart-empty 已提供 min-height 与 flex 居中，本组件只负责内容。 */
.chart-empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--xfjy-space-2);
}

.chart-empty-state__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: var(--xfjy-radius-md);
  color: var(--xfjy-text-disabled);
  background: var(--xfjy-bg-hover);
}

.chart-empty-state--compact .chart-empty-state__icon {
  width: 34px;
  height: 34px;
}

.chart-empty-state__text {
  font-size: var(--xfjy-font-size-sm);
  color: var(--xfjy-text-secondary);
}
</style>
