<template>
  <div
    class="kpi-card"
    :class="{ 'kpi-card--link': to }"
    :role="to ? 'link' : undefined"
    :tabindex="to ? 0 : undefined"
    @click="handleClick"
    @keydown.enter="handleClick"
  >
    <div class="kpi-card__top">
      <span
        class="kpi-card__icon"
        :style="{ color: iconColor, background: iconBg }"
      >
        <el-icon :size="15"><component :is="icon" /></el-icon>
      </span>
      <span class="kpi-card__label">{{ label }}</span>
    </div>
    <div class="kpi-card__value">
      {{ formattedValue }}
    </div>
    <div class="kpi-card__foot">
      <div class="kpi-card__foot-main">
        <span
          v-if="delta !== null && delta !== undefined"
          class="kpi-card__chip"
          :class="delta > 0 ? 'kpi-card__chip--up' : delta < 0 ? 'kpi-card__chip--down' : 'kpi-card__chip--flat'"
        >
          <el-icon
            :size="11"
            class="kpi-card__chip-icon"
          >
            <CaretTop v-if="delta > 0" />
            <CaretBottom v-else-if="delta < 0" />
            <Minus v-else />
          </el-icon>
          {{ formatDelta(delta) }}
        </span>
        <span
          v-if="hasDelta"
          class="kpi-card__hint"
        >较昨日</span>
        <span
          v-else-if="hint"
          class="kpi-card__hint"
        >{{ hint }}</span>
      </div>
      <span
        v-if="to"
        class="kpi-card__more"
      >
        详情
        <el-icon :size="11"><ArrowRight /></el-icon>
      </span>
    </div>
    <div class="kpi-card__spark">
      <BaseChart
        v-if="hasSpark"
        :option="sparkOption"
        height="24px"
      />
      <div
        v-else-if="pulse"
        class="kpi-card__live"
      >
        <span class="kpi-card__live-dot"><span class="kpi-card__live-wave" /></span>
        <span>实时心跳统计</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, type Component } from 'vue'
import { CaretTop, CaretBottom, Minus, ArrowRight } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { BaseChart } from '@/components'
import { sparklineOption } from '../chartOptions'
import { useCountUp } from '../composables/useCountUp'

const props = defineProps<{
  label: string
  value: number | null
  icon: Component
  iconColor: string
  iconBg: string
  /** 环比差值；null/undefined 时显示 hint */
  delta?: number | null
  hint?: string
  /** 数值与差值的自定义格式化（如分→元），默认千分位整数 */
  formatter?: (n: number) => string
  /** 迷你趋势数据；少于 2 个有效点时不渲染 */
  spark?: Array<number | null> | null
  sparkColor?: string
  /** 实时模式：spark 区显示呼吸点而非趋势线 */
  pulse?: boolean
  /** 点击跳转路由地址；不传则为纯展示卡 */
  to?: string
}>()

const router = useRouter()

function handleClick() {
  if (props.to) router.push(props.to)
}

const animated = useCountUp(computed(() => props.value))

const formatInt = (n: number) => n.toLocaleString('zh-CN')
const formatValue = (n: number) => (props.formatter ? props.formatter(n) : formatInt(n))

const formattedValue = computed(() => {
  if (animated.value === null || props.value === null) return '—'
  return formatValue(animated.value)
})

function formatDelta(n: number) {
  const abs = Math.abs(n)
  return `${n > 0 ? '+' : '-'}${formatValue(abs)}`
}

const hasDelta = computed(() => props.delta !== null && props.delta !== undefined)

const hasSpark = computed(() => {
  if (!props.spark) return false
  return props.spark.filter((v) => v !== null).length >= 2
})

const sparkOption = computed(() =>
  sparklineOption(props.spark ?? [], props.sparkColor ?? 'var(--xfjy-chart-1)')
)
</script>

<style scoped>
.kpi-card {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: var(--xfjy-space-2);
  background: var(--xfjy-bg-card);
  border: 1px solid var(--xfjy-border);
  border-radius: var(--xfjy-radius-lg);
  box-shadow: var(--xfjy-shadow-card);
  transition: box-shadow 0.2s ease, transform 0.2s ease, border-color 0.2s ease;
}

/* 可点击指标卡：hover 上浮 + 品牌色描边（AntD Pro 指标卡交互规范） */
.kpi-card--link {
  cursor: pointer;
}

.kpi-card--link:hover,
.kpi-card--link:focus-visible {
  transform: translateY(-2px);
  border-color: var(--xfjy-primary);
  box-shadow: var(--xfjy-shadow-hover);
  outline: none;
}

.kpi-card__top {
  display: flex;
  align-items: center;
  gap: var(--xfjy-space-2);
  min-width: 0;
}

.kpi-card__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 24px;
  height: 24px;
  border-radius: var(--xfjy-radius-md);
}

.kpi-card__label {
  font-size: var(--xfjy-font-size-sm);
  color: var(--xfjy-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.kpi-card__value {
  margin-top: var(--xfjy-space-1);
  font-size: var(--xfjy-font-size-xl);
  font-weight: 600;
  line-height: 1.15;
  letter-spacing: -0.3px; /* 大数字字距光学收紧，不归栅格（AGENTS.md §11 存量豁免） */
  color: var(--xfjy-text-primary);
  font-variant-numeric: tabular-nums;
}

.kpi-card__foot {
  margin-top: var(--xfjy-space-1);
  min-height: 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--xfjy-space-2);
}

.kpi-card__foot-main {
  display: flex;
  align-items: center;
  min-width: 0;
}

.kpi-card__more {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
  font-size: var(--xfjy-font-size-xs);
  color: var(--xfjy-primary);
  opacity: 0;
  transition: opacity 0.2s ease;
}

.kpi-card--link:hover .kpi-card__more,
.kpi-card--link:focus-visible .kpi-card__more {
  opacity: 1;
}

.kpi-card__chip {
  display: inline-flex;
  align-items: center;
  gap: 2px; /* chip 微间距为光学修正，不归 8px 栅格（AGENTS.md §11 存量豁免） */
  padding: 1px var(--xfjy-space-2); /* 1px 为光学贴边修正 */
  border-radius: var(--xfjy-radius-sm);
  font-size: var(--xfjy-font-size-xs);
  font-weight: 500;
  line-height: 18px;
  font-variant-numeric: tabular-nums;
}

.kpi-card__chip-icon {
  font-weight: 700;
}

.kpi-card__chip--up {
  color: var(--xfjy-success);
  background: var(--xfjy-success-50);
}

.kpi-card__chip--down {
  color: var(--xfjy-danger);
  background: var(--xfjy-danger-50);
}

.kpi-card__chip--flat {
  color: var(--xfjy-text-secondary);
  background: var(--xfjy-bg-hover);
}

.kpi-card__hint {
  font-size: var(--xfjy-font-size-xs);
  color: var(--xfjy-text-secondary);
}

.kpi-card__spark {
  margin-top: var(--xfjy-space-1);
  min-height: 24px;
}

.kpi-card__live {
  display: flex;
  align-items: center;
  gap: var(--xfjy-space-2);
  height: 24px;
  font-size: var(--xfjy-font-size-xs);
  color: var(--xfjy-text-secondary);
}

.kpi-card__live-dot {
  position: relative;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--xfjy-success);
}

/* 脉冲波纹：用子元素缩放+淡出，避免在 .vue 内写 rgba 字面量（§7.2） */
.kpi-card__live-wave {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: var(--xfjy-success);
  animation: kpi-wave 1.6s ease-out infinite;
}

@keyframes kpi-wave {
  0% {
    transform: scale(1);
    opacity: 0.35;
  }
  70% {
    transform: scale(2.4);
    opacity: 0;
  }
  100% {
    transform: scale(2.4);
    opacity: 0;
  }
}
</style>
