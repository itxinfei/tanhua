<template>
  <div class="page-container dashboard">
    <PageHeader title="数据看板" subtitle="平台运营概览">
      <template #actions>
        <span v-if="updatedAt" class="dash-updated">更新于 {{ updatedAt }}</span>
        <el-button :icon="Refresh" :loading="loading" @click="loadAll">刷新</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="hasError" class="dash-alert" type="warning" :closable="false" show-icon>
      <template #title>
        部分数据加载失败，当前展示可能不完整
        <el-button link type="primary" @click="loadAll">重新加载</el-button>
      </template>
    </el-alert>

    <!-- 核心指标：只放有真实数据源的字段 -->
    <el-row v-loading="loading" :gutter="16" class="dash-row">
      <el-col v-for="item in kpiCards" :key="item.label" :xs="12" :sm="12" :md="6">
        <div
          class="kpi-card"
          :class="{ 'kpi-card--link': item.to }"
          @click="item.to && router.push(item.to)"
        >
          <div class="kpi-card__top">
            <span class="kpi-card__icon" :style="{ color: item.color, background: item.bg }">
              <el-icon :size="15"><component :is="item.icon" /></el-icon>
            </span>
            <span class="kpi-card__label">{{ item.label }}</span>
          </div>
          <div class="kpi-card__value" :style="{ color: item.valueColor }">{{ item.value }}</div>
          <div class="kpi-card__foot">
            <span>{{ item.hint }}</span>
            <el-icon v-if="item.to" :size="12"><ArrowRight /></el-icon>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 趋势 + 快捷入口 -->
    <el-row :gutter="16" class="dash-row">
      <el-col :xs="24" :lg="16">
        <div class="dash-card">
          <div class="dash-card__head">
            <div class="dash-card__heading">
              <span class="dash-card__title">新增用户趋势</span>
              <span v-if="trendSummary" class="dash-card__sub">
                近 {{ trendDays }} 天累计 {{ formatCount(trendSummary.total) }} 人 · 日均
                {{ formatCount(trendSummary.avg) }} 人
              </span>
            </div>
            <el-radio-group v-model="trendDays" size="small" @change="refreshTrend">
              <el-radio-button :value="7">近 7 天</el-radio-button>
              <el-radio-button :value="30">近 30 天</el-radio-button>
            </el-radio-group>
          </div>
          <BaseChart v-if="trendData.dates.length" :option="trendOption" height="268px" />
          <el-empty v-else-if="!loading" description="暂无趋势数据" :image-size="72" />
        </div>
      </el-col>

      <el-col :xs="24" :lg="8">
        <div class="dash-card">
          <div class="dash-card__head">
            <span class="dash-card__title">快捷入口</span>
          </div>
          <div class="shortcut-grid">
            <div v-for="s in shortcuts" :key="s.label" class="shortcut" @click="router.push(s.to)">
              <el-icon :size="18"><component :is="s.icon" /></el-icon>
              <span>{{ s.label }}</span>
            </div>
          </div>
          <p class="dash-note">
            DAU、匹配成功率、付费转化率暂无统计源，未纳入本页展示，避免用 0 值误导运营判断。
          </p>
        </div>
      </el-col>
    </el-row>

    <!-- 用户结构 -->
    <el-row :gutter="16" class="dash-row">
      <el-col :xs="24" :lg="12">
        <div class="dash-card">
          <div class="dash-card__head">
            <span class="dash-card__title">性别构成</span>
            <span v-if="genderTotal" class="dash-card__meta">共 {{ formatCount(genderTotal) }} 人</span>
          </div>
          <BaseChart v-if="genderTotal" :option="genderOption" height="220px" />
          <el-empty v-else-if="!loading" description="暂无性别数据" :image-size="72" />
        </div>
      </el-col>

      <el-col :xs="24" :lg="12">
        <div class="dash-card">
          <div class="dash-card__head">
            <span class="dash-card__title">年龄分布</span>
          </div>
          <BaseChart v-if="ageData.counts.length" :option="ageOption" height="220px" />
          <el-empty v-else-if="!loading" description="暂无年龄数据" :image-size="72" />
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { PageHeader, BaseChart } from '@/components'
import {
  getStatOverviewApi,
  getTrendDataApi,
  getGenderRatioApi,
  getAgeDistributionApi,
} from '@/api/dashboard'
import { getBrandColors, hexToRgba } from '@/utils/brand'

const BRAND = getBrandColors()
const router = useRouter()

// ==================== 状态 ====================
const loading = ref(false)
const hasError = ref(false)
const updatedAt = ref('')

const overview = reactive<{
  totalUsers: number | null
  newUsers: number | null
  pendingReviews: number | null
  totalReports: number | null
}>({
  totalUsers: null,
  newUsers: null,
  pendingReviews: null,
  totalReports: null,
})

const trendDays = ref(7)
const trendData = reactive<{ dates: string[]; newUserList: number[] }>({ dates: [], newUserList: [] })
const genderData = reactive({ male: 0, female: 0 })
const ageData = reactive<{ ranges: string[]; counts: number[] }>({ ranges: [], counts: [] })

// ==================== 派生数据 ====================
/** 今日新增与昨日的差值：由趋势接口的最后两天真实数据相减，不依赖后端 newUsersChange（当前恒为 null） */
const newUsersDelta = computed<number | null>(() => {
  const list = trendData.newUserList
  if (list.length < 2) return null
  return list[list.length - 1] - list[list.length - 2]
})

const trendSummary = computed(() => {
  const list = trendData.newUserList
  if (!list.length) return null
  const total = list.reduce((sum, n) => sum + n, 0)
  return { total, avg: Math.round(total / list.length) }
})

const genderTotal = computed(() => genderData.male + genderData.female)

interface KpiCard {
  label: string
  value: string
  hint: string
  icon: string
  color: string
  bg: string
  valueColor: string
  to?: string
}

const kpiCards = computed<KpiCard[]>(() => [
  {
    label: '总用户数',
    value: formatCount(overview.totalUsers),
    hint: '累计注册用户',
    icon: 'User',
    color: 'var(--xfjy-primary)',
    bg: 'var(--xfjy-primary-50)',
    valueColor: 'var(--xfjy-text-primary)',
  },
  {
    label: '今日新增',
    value: formatCount(overview.newUsers),
    hint: newUsersDelta.value === null
      ? '较昨日 —'
      : `较昨日 ${newUsersDelta.value >= 0 ? '+' : ''}${newUsersDelta.value}`,
    icon: 'CirclePlus',
    color: 'var(--xfjy-primary-deep)',
    bg: 'var(--xfjy-deep-50)',
    valueColor: 'var(--xfjy-text-primary)',
  },
  {
    label: '待审核内容',
    value: formatCount(overview.pendingReviews),
    hint: '点击处理',
    icon: 'DocumentChecked',
    color: 'var(--xfjy-warning)',
    bg: 'var(--xfjy-warning-50)',
    valueColor: (overview.pendingReviews ?? 0) > 0 ? 'var(--xfjy-warning)' : 'var(--xfjy-text-primary)',
    to: '/audit/review',
  },
  {
    label: '待处理举报',
    value: formatCount(overview.totalReports),
    hint: '点击处理',
    icon: 'WarningFilled',
    color: 'var(--xfjy-danger)',
    bg: 'var(--xfjy-danger-50)',
    valueColor: (overview.totalReports ?? 0) > 0 ? 'var(--xfjy-danger)' : 'var(--xfjy-text-primary)',
    to: '/audit/report',
  },
])

const shortcuts = [
  { label: '用户管理', icon: 'UserFilled', to: '/user/list' },
  { label: '内容审核', icon: 'DocumentChecked', to: '/audit/review' },
  { label: '举报处理', icon: 'Warning', to: '/audit/report' },
  { label: '订单管理', icon: 'Wallet', to: '/operation/pay' },
]

// ==================== 图表配置 ====================
const tooltipBase = {
  backgroundColor: 'rgba(31, 35, 41, 0.92)',
  borderWidth: 0,
  padding: [8, 12],
  textStyle: { color: '#ffffff', fontSize: 12 },
}

const trendOption = computed<echarts.EChartsOption>(() => ({
  tooltip: { ...tooltipBase, trigger: 'axis', axisPointer: { type: 'line', lineStyle: { color: BRAND.primaryLight7 } } },
  grid: { left: 8, right: 16, top: 16, bottom: 8, containLabel: true },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: trendData.dates,
    axisLine: { lineStyle: { color: BRAND.chartGrid } },
    axisTick: { show: false },
    axisLabel: { fontSize: 12, color: BRAND.chartInk3, hideOverlap: true },
  },
  yAxis: {
    type: 'value',
    axisLine: { show: false },
    axisTick: { show: false },
    splitLine: { lineStyle: { color: BRAND.chartGrid } },
    axisLabel: { fontSize: 12, color: BRAND.chartInk3 },
  },
  series: [
    {
      name: '新增用户',
      type: 'line',
      smooth: true,
      showSymbol: false,
      symbol: 'circle',
      symbolSize: 6,
      data: trendData.newUserList,
      lineStyle: { width: 2, color: BRAND.primary },
      itemStyle: { color: BRAND.primary },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: hexToRgba(BRAND.primary, 0.22) },
          { offset: 1, color: hexToRgba(BRAND.primary, 0.01) },
        ]),
      },
    },
  ],
}))

const genderOption = computed<echarts.EChartsOption>(() => {
  const total = genderTotal.value || 1
  return {
    tooltip: {
      ...tooltipBase,
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params: any) => {
        const p = Array.isArray(params) ? params[0] : params
        return `${p.name}<br/>${formatCount(p.value)} 人 · ${((p.value / total) * 100).toFixed(1)}%`
      },
    },
    grid: { left: 8, right: 132, top: 40, bottom: 40, containLabel: true },
    xAxis: { type: 'value', show: false },
    yAxis: {
      type: 'category',
      data: ['男性', '女性'],
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { fontSize: 12, color: BRAND.chartInk2 },
    },
    series: [
      {
        type: 'bar',
        barWidth: 16,
        showBackground: true,
        backgroundStyle: { color: BRAND.chartGrid, borderRadius: 4 },
        data: [
          { value: genderData.male, itemStyle: { color: BRAND.chartSecondary, borderRadius: [0, 4, 4, 0] } },
          { value: genderData.female, itemStyle: { color: BRAND.chartPrimary, borderRadius: [0, 4, 4, 0] } },
        ],
        label: {
          show: true,
          position: 'right',
          distance: 10,
          fontSize: 12,
          color: BRAND.chartInk2,
          formatter: (p: any) => `${formatCount(p.value)} 人 · ${((p.value / total) * 100).toFixed(1)}%`,
        },
      },
    ],
  }
})

const ageOption = computed<echarts.EChartsOption>(() => ({
  tooltip: { ...tooltipBase, trigger: 'axis', axisPointer: { type: 'shadow' } },
  grid: { left: 8, right: 72, top: 8, bottom: 8, containLabel: true },
  xAxis: { type: 'value', show: false },
  yAxis: {
    type: 'category',
    // 年龄从高到低，让 18-22 显示在顶部
    data: [...ageData.ranges].reverse(),
    axisLine: { show: false },
    axisTick: { show: false },
    axisLabel: { fontSize: 12, color: BRAND.chartInk2 },
  },
  series: [
    {
      type: 'bar',
      barWidth: 12,
      showBackground: true,
      backgroundStyle: { color: BRAND.chartGrid, borderRadius: 4 },
      data: [...ageData.counts].reverse(),
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
          { offset: 0, color: hexToRgba(BRAND.primary, 0.5) },
          { offset: 1, color: BRAND.primary },
        ]),
        borderRadius: [0, 4, 4, 0],
      },
      label: {
        show: true,
        position: 'right',
        distance: 10,
        fontSize: 12,
        color: BRAND.chartInk2,
        formatter: (p: any) => formatCount(p.value),
      },
    },
  ],
}))

// ==================== 工具函数 ====================
function formatCount(n: number | null | undefined): string {
  if (n === null || n === undefined || Number.isNaN(n)) return '—'
  return n.toLocaleString('zh-CN')
}

// ==================== 数据加载 ====================
async function loadOverview() {
  const res = await getStatOverviewApi()
  overview.totalUsers = res.totalUsers ?? null
  overview.newUsers = res.newUsers ?? null
  overview.pendingReviews = res.pendingReviews ?? null
  overview.totalReports = res.totalReports ?? null
}

async function loadTrend() {
  const res = await getTrendDataApi(trendDays.value)
  trendData.dates = res.dates ?? []
  trendData.newUserList = res.newUserList ?? []
}

async function loadGender() {
  const res = await getGenderRatioApi()
  genderData.male = res.male ?? 0
  genderData.female = res.female ?? 0
}

async function loadAge() {
  const res = await getAgeDistributionApi()
  ageData.ranges = res.ranges ?? []
  ageData.counts = res.counts ?? []
}

async function loadAll() {
  loading.value = true
  const results = await Promise.allSettled([loadOverview(), loadTrend(), loadGender(), loadAge()])
  hasError.value = results.some((r) => r.status === 'rejected')
  loading.value = false
  updatedAt.value = new Date().toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
  })
}

async function refreshTrend() {
  try {
    await loadTrend()
  } catch {
    hasError.value = true
  }
}

onMounted(loadAll)
</script>

<style scoped>
.dashboard {
  padding: var(--xfjy-space-6);
  box-sizing: border-box;
}

.dash-updated {
  margin-right: var(--xfjy-space-2);
  font-size: var(--xfjy-font-size-xs);
  color: var(--xfjy-text-secondary);
  font-variant-numeric: tabular-nums;
}

.dash-alert {
  margin-bottom: var(--xfjy-space-4);
}

.dash-row {
  margin-bottom: var(--xfjy-space-4);
  /* 小屏 KPI 换行时保证纵向间距 */
  row-gap: var(--xfjy-space-4);
}

.dash-row:last-child {
  margin-bottom: 0;
}

/* ==================== KPI 卡 ==================== */
.kpi-card {
  display: flex;
  flex-direction: column;
  gap: var(--xfjy-space-2);
  height: 100%;
  padding: var(--xfjy-space-4) var(--xfjy-space-5);
  background: var(--xfjy-bg-card);
  border: 1px solid var(--xfjy-border);
  border-radius: var(--xfjy-radius-lg);
  box-shadow: var(--xfjy-shadow-card);
  transition: border-color 0.2s, box-shadow 0.2s;
}

/* 可点击的卡片：hover 用主色描边，明确「这里能点」 */
.kpi-card--link {
  cursor: pointer;
}

.kpi-card--link:hover {
  border-color: var(--xfjy-primary-200);
  box-shadow: var(--xfjy-shadow-hover);
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
  border-radius: var(--xfjy-radius-sm);
}

.kpi-card__label {
  font-size: var(--xfjy-font-size-sm);
  color: var(--xfjy-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.kpi-card__value {
  font-size: var(--xfjy-font-size-2xl);
  font-weight: 600;
  line-height: 1.15;
  letter-spacing: -0.5px;
  font-variant-numeric: tabular-nums;
}

.kpi-card__foot {
  display: flex;
  align-items: center;
  gap: var(--xfjy-space-1);
  min-height: 18px;
  font-size: var(--xfjy-font-size-xs);
  color: var(--xfjy-text-secondary);
}

.kpi-card--link .kpi-card__foot {
  color: var(--xfjy-primary);
}

/* ==================== 通用卡片 ==================== */
.dash-card {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: var(--xfjy-space-4) var(--xfjy-space-5);
  background: var(--xfjy-bg-card);
  border: 1px solid var(--xfjy-border);
  border-radius: var(--xfjy-radius-lg);
  box-shadow: var(--xfjy-shadow-card);
}

.dash-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--xfjy-space-3);
  margin-bottom: var(--xfjy-space-3);
}

.dash-card__heading {
  min-width: 0;
}

.dash-card__title {
  font-size: var(--xfjy-font-size-md);
  font-weight: 600;
  color: var(--xfjy-text-primary);
}

.dash-card__sub {
  display: block;
  margin-top: 2px;
  font-size: var(--xfjy-font-size-xs);
  color: var(--xfjy-text-secondary);
  font-variant-numeric: tabular-nums;
}

.dash-card__meta {
  flex-shrink: 0;
  font-size: var(--xfjy-font-size-xs);
  color: var(--xfjy-text-secondary);
  font-variant-numeric: tabular-nums;
}

/* ==================== 快捷入口 ==================== */
.shortcut-grid {
  flex: 1;
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  grid-auto-rows: 1fr;
  gap: var(--xfjy-space-3);
}

.shortcut {
  display: flex;
  align-items: center;
  gap: var(--xfjy-space-2);
  padding: var(--xfjy-space-3) var(--xfjy-space-4);
  border: 1px solid var(--xfjy-border);
  border-radius: var(--xfjy-radius-md);
  font-size: var(--xfjy-font-size-sm);
  color: var(--xfjy-text-regular);
  cursor: pointer;
  transition: border-color 0.2s, color 0.2s, background 0.2s;
}

.shortcut .el-icon {
  color: var(--xfjy-primary);
  flex-shrink: 0;
}

.shortcut:hover {
  border-color: var(--xfjy-primary);
  background: var(--xfjy-primary-50);
  color: var(--xfjy-primary);
}

.dash-note {
  margin-top: var(--xfjy-space-3);
  padding-top: var(--xfjy-space-3);
  border-top: 1px solid var(--xfjy-border-light);
  font-size: var(--xfjy-font-size-xs);
  line-height: 1.6;
  color: var(--xfjy-text-secondary);
}
</style>
