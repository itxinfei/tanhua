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
      <span class="kpi-card__label">{{ label }}</span>
      <span
        class="kpi-card__icon"
        :class="{ 'kpi-card__icon--live': live }"
      >
        <el-icon :size="17"><component :is="icon" /></el-icon>
      </span>
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
  </div>
</template>

<script setup lang="ts">
import { computed, type Component } from 'vue'
import { CaretTop, CaretBottom, Minus, ArrowRight } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { useCountUp } from '../composables/useCountUp'

const props = defineProps<{
  label: string
  value: number | null
  icon: Component
  /** 实时在线卡：图标走品牌玫红，其余卡统一中性灰 */
  live?: boolean
  /** 环比差值；null/undefined 时显示 hint */
  delta?: number | null
  hint?: string
  /** 数值与差值的自定义格式化（如分→元），默认千分位整数 */
  formatter?: (n: number) => string
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
  // 0 值不再渲染 "-0"（负数歧义），改为中性文案
  if (n === 0) return '持平'
  const abs = Math.abs(n)
  return `${n > 0 ? '+' : '-'}${formatValue(abs)}`
}

const hasDelta = computed(() => props.delta !== null && props.delta !== undefined)
</script>

<style scoped>
.kpi-card {
  display: flex;
  flex-direction: column;
  height: 100%;
  gap: var(--xfjy-space-3);
  padding: var(--xfjy-space-5);
  background: var(--xfjy-bg-card);
  border: 1px solid var(--xfjy-border);
  border-radius: var(--xfjy-radius-lg);
  transition: transform 0.2s ease, border-color 0.2s ease;
}

/* 可点击指标卡：hover 上浮 + 品牌色描边（去阴影，仅细边框表达层级） */
.kpi-card--link {
  cursor: pointer;
}

.kpi-card--link:hover,
.kpi-card--link:focus-visible {
  transform: translateY(-2px);
  border-color: var(--xfjy-primary-mist);
  outline: none;
}

.kpi-card__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--xfjy-space-2);
  min-width: 0;
}

.kpi-card__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  border-radius: var(--xfjy-radius-md);
  /* 默认中性灰图标，消除四色图标带来的色相噪音 */
  color: var(--xfjy-text-regular);
  background: var(--xfjy-bg-hover);
}

/* 唯一保留品牌色的图标：当前在线（实时语义） */
.kpi-card__icon--live {
  color: var(--xfjy-primary);
  background: var(--xfjy-primary-50);
}

.kpi-card__label {
  font-size: var(--xfjy-font-size-sm);
  color: var(--xfjy-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.kpi-card__value {
  font-size: var(--xfjy-font-size-3xl);
  font-weight: 700;
  line-height: 1.1;
  letter-spacing: -0.5px; /* 大数字字距光学收紧 */
  color: var(--xfjy-text-primary);
  font-variant-numeric: tabular-nums;
}

.kpi-card__foot {
  min-height: 20px;
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
  gap: 3px;
  padding: 0 var(--xfjy-space-2);
  /* chip 高 20px，3xl(28px) 已过半高，渲染即全圆角胶囊；admin 无 pill token，不裸写 999px */
  border-radius: var(--xfjy-radius-3xl);
  font-size: var(--xfjy-font-size-xs);
  font-weight: 500;
  line-height: 20px;
  white-space: nowrap;
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}

.kpi-card__chip-icon {
  font-weight: 700;
}

.kpi-card__chip--up {
  color: var(--xfjy-success-deep);
  background: var(--xfjy-success-50);
}

.kpi-card__chip--down {
  color: var(--xfjy-danger-deep);
  background: var(--xfjy-danger-50);
}

.kpi-card__chip--flat {
  color: var(--xfjy-text-secondary);
  background: var(--xfjy-bg-hover);
}

.kpi-card__hint {
  margin-left: var(--xfjy-space-2);
  font-size: var(--xfjy-font-size-sm);
  /* 辅助文案非禁用态：disabled 灰仅 1.9:1，secondary 4.6:1 过 AA */
  color: var(--xfjy-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
