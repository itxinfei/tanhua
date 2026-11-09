<template>
  <div class="stat-card">
    <div class="stat-card__icon" :style="{ background: iconBg }">
      <div class="stat-card__icon-glow" :style="{ background: iconBg }"></div>
      <el-icon :size="24"><component :is="icon" /></el-icon>
    </div>
    <div class="stat-card__info">
      <div class="stat-card__label">{{ label }}</div>
      <div class="stat-card__value">
        {{ formattedValue }}<span v-if="suffix" class="stat-card__suffix">{{ suffix }}</span>
      </div>
    </div>
    <span v-if="change != null" class="stat-card__change" :class="change >= 0 ? 'up' : 'down'">
      <el-icon :size="12"><component :is="change >= 0 ? 'ArrowUp' : 'ArrowDown'" /></el-icon>
      {{ Math.abs(change) }}%
    </span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  label: string
  value: number | null
  change?: number | null
  icon: string
  suffix?: string
  iconBg?: string
}>()

const formattedValue = computed(() => {
  if (props.value === null || props.value === undefined || isNaN(props.value)) return '待接入'
  return props.value.toLocaleString('zh-CN')
})
</script>

<style scoped>
.stat-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--xfjy-space-3);
  background: var(--xfjy-bg-card);
  border: 1px solid var(--xfjy-border);
  border-radius: var(--xfjy-radius-lg);
  padding: var(--xfjy-space-3) var(--xfjy-space-4);
  box-shadow: var(--xfjy-shadow-card);
}

/* 纯展示卡片不设 hover 抬升（§7.2 列表卡片禁止 hover 抬升） */

.stat-card__icon {
  position: relative;
  width: 48px;
  height: 48px;
  border-radius: var(--xfjy-radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--xfjy-text-inverse);
  flex-shrink: 0;
  overflow: hidden;
}

/* 内发光高光：左上角 30% 处的白色径向渐变，让图标容器有立体感 */
.stat-card__icon-glow {
  position: absolute;
  inset: 0;
  background: var(--xfjy-glass-highlight);
}

.stat-card__info {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.stat-card__label {
  font-size: var(--xfjy-font-size-xs);
  color: var(--xfjy-text-secondary);
  line-height: 1.3;
  overflow: hidden;
}

.stat-card__value {
  font-size: var(--xfjy-font-size-xl);
  font-weight: 600;
  color: var(--xfjy-text-primary);
  line-height: 1.3;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.stat-card__suffix {
  font-size: var(--xfjy-font-size-sm);
  color: var(--xfjy-text-secondary);
  margin-left: var(--xfjy-space-1);
}

.stat-card__change {
  position: absolute;
  right: var(--xfjy-space-3);
  bottom: var(--xfjy-space-2);
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-size: var(--xfjy-font-size-xs);
  /* 1px 为图标光学贴边修正，不归 8px 栅格（AGENTS.md §11 存量豁免） */
  padding: 1px var(--xfjy-space-2);
  border-radius: var(--xfjy-radius-2xl);
  font-variant-numeric: tabular-nums;
}

.stat-card__change.up {
  color: var(--xfjy-success-deep);
  background: var(--xfjy-success-tint);
}

.stat-card__change.down {
  color: var(--xfjy-danger-deep);
  background: var(--xfjy-danger-tint);
}
</style>
