<template>
  <div class="page-container dashboard">
    <div class="dash-toolbar">
      <span v-if="updatedAt" class="dash-updated">更新于 {{ updatedAt }}</span>
      <el-button :icon="Refresh" :loading="loading" @click="loadAll">刷新</el-button>
    </div>

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

    <!-- 平台实时数据 + 数据洞察（对半对称：12 : 12） -->
    <el-row :gutter="16" class="dash-row">
      <el-col :xs="24" :lg="12">
        <div class="dash-card">
          <div class="dash-card__head">
            <div class="dash-card__heading">
              <span class="dash-card__title">平台实时数据</span>
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

          <!-- 今日发送行为：三个指标横向并排，各占一列，不各占一行 -->
          <div class="rt-metrics">
            <div class="rt-metric">
              <div class="rt-metric__icon" style="color: var(--xfjy-primary); background: var(--xfjy-primary-50)">
                <el-icon><ChatDotRound /></el-icon>
              </div>
              <div class="rt-metric__body">
                <div class="rt-metric__value">{{ formatCount(todayMessages) }}</div>
                <div class="rt-metric__label">消息发送<span class="rt-metric__pending">待接入</span></div>
              </div>
            </div>
            <div class="rt-metric">
              <div class="rt-metric__icon" style="color: var(--xfjy-primary-deep); background: var(--xfjy-deep-50)">
                <el-icon><Share /></el-icon>
              </div>
              <div class="rt-metric__body">
                <div class="rt-metric__value">{{ formatCount(todayMomentPosters) }}</div>
                <div class="rt-metric__label">发圈子人数<span class="rt-metric__pending">待接入</span></div>
              </div>
            </div>
            <div class="rt-metric">
              <div class="rt-metric__icon" style="color: var(--xfjy-warning); background: #fff7ed">
                <el-icon><VideoCamera /></el-icon>
              </div>
              <div class="rt-metric__body">
                <div class="rt-metric__value">{{ formatCount(todayVideos) }}</div>
                <div class="rt-metric__label">视频发送量<span class="rt-metric__pending">待接入</span></div>
              </div>
            </div>
          </div>

          <BaseChart v-if="trendData.dates.length" :option="trendOption" height="232px" />
          <el-empty v-else-if="!loading" description="暂无趋势数据" :image-size="72" />
        </div>
      </el-col>

      <el-col :xs="24" :lg="12">
        <div class="dash-card">
          <div class="dash-card__head">
            <span class="dash-card__title">数据洞察</span>
            <span class="dash-card__meta">用户最关心的实时数据</span>
          </div>

          <!-- APP 使用人数：两个指标横向并排 -->
          <div class="insight-block">
            <div class="insight-block__head">
              <span class="insight-block__title">APP 使用人数</span>
              <span class="insight-pending">待接入</span>
            </div>
            <div class="rt-metrics">
              <div class="rt-metric">
                <div class="rt-metric__icon" style="color: var(--xfjy-primary); background: var(--xfjy-primary-50)">
                  <el-icon><Connection /></el-icon>
                </div>
                <div class="rt-metric__body">
                  <div class="rt-metric__value">{{ formatCount(onlineUsers) }}</div>
                  <div class="rt-metric__label">当前在线</div>
                </div>
              </div>
              <div class="rt-metric">
                <div class="rt-metric__icon" style="color: var(--xfjy-warning); background: #fff7ed">
                  <el-icon><TrendCharts /></el-icon>
                </div>
                <div class="rt-metric__body">
                  <div class="rt-metric__value">{{ formatCount(peakToday) }}</div>
                  <div class="rt-metric__label">今日峰值</div>
                </div>
              </div>
            </div>
          </div>

          <!-- 用户结构洞察：男女比例 + 主力年龄，横向比例条一行带过，不再各占整图 -->
          <div class="insight-block">
            <div class="insight-block__head">
              <span class="insight-block__title">用户结构洞察</span>
              <span class="insight-block__meta">共 {{ formatCount(genderTotal) }} 人</span>
            </div>
            <div class="insight-profile">
              <!-- 男女比例条：一段男 + 一段女，宽度按占比，条后跟百分比+人数 -->
              <div class="insight-profile__row insight-profile__row--bar">
                <span class="insight-profile__label">性别</span>
                <div class="insight-profile__bar">
                  <div
                    class="insight-profile__bar-male"
                    :style="{ width: malePct + '%' }"
                  />
                  <div
                    class="insight-profile__bar-female"
                    :style="{ width: femalePct + '%' }"
                  />
                </div>
                <span class="insight-profile__num">{{ malePct }}%男 · {{ femalePct }}%女</span>
              </div>
              <!-- 主力年龄：一句话结论，右侧人数 -->
              <div class="insight-profile__row">
                <span class="insight-profile__dot" style="background: var(--xfjy-primary-deep)" />
                <span class="insight-profile__label">主力年龄</span>
                <span class="insight-profile__pct">{{ topAgeRange }}</span>
                <span class="insight-profile__num">{{ formatCount(topAgeCount) }} 人</span>
              </div>
            </div>
          </div>

          <!-- 热点 & 热门视频（占位，后端聚合后替换） -->
          <div class="insight-block">
            <div class="insight-block__head">
              <span class="insight-block__title">热点 & 热门视频</span>
              <span class="insight-pending">待接入</span>
            </div>
            <div class="insight-empty">热门动态 / 话题 / 视频排行，后端聚合后展示</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 支付订单趋势 + 支付方式分布（对半对称：12 : 12） -->
    <el-row :gutter="16" class="dash-row">
      <el-col :xs="24" :lg="12">
        <div class="dash-card">
          <div class="dash-card__head">
            <span class="dash-card__title">支付订单趋势</span>
            <span v-if="payTrendSummary" class="dash-card__meta">
              近 {{ trendDays }} 天累计 {{ formatCount(payTrendSummary.total) }} 单
            </span>
          </div>
          <BaseChart v-if="payTrendData.dates.length" :option="payTrendOption" height="176px" />
          <el-empty v-else-if="!loading" description="暂无支付数据" :image-size="72" />
        </div>
      </el-col>

      <el-col :xs="24" :lg="12">
        <div class="dash-card">
          <div class="dash-card__head">
            <span class="dash-card__title">支付方式分布</span>
            <span v-if="payTypeTotal > 0" class="dash-card__meta">已支付 {{ formatCount(payTypeTotal) }} 单</span>
          </div>
          <BaseChart v-if="payTypeTotal > 0" :option="payTypeOption" height="176px" />
          <el-empty v-else-if="!loading" description="暂无支付数据" :image-size="72" />
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh, ChatDotRound, Share, VideoCamera, Connection, TrendCharts } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { BaseChart } from '@/components'
import {
  getStatOverviewApi,
  getTrendDataApi,
  getGenderRatioApi,
  getAgeDistributionApi,
  getPayTrendApi,
  getPayTypeApi,
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

// 支付订单趋势 + 支付方式分布（App 商业化实时数据）
const payTrendData = reactive<{ dates: string[]; orderList: number[] }>({ dates: [], orderList: [] })
const payTypeData = reactive({ alipay: 0, wechat: 0, sandbox: 0 })

// 洞察面板数据源：APP 使用人数（待后端接入实时在线接口，暂为 null 占位）
const onlineUsers = ref<number | null>(null)
const peakToday = ref<number | null>(null)

// 平台实时发送行为数据（待后端接入聚合接口，暂为 null 占位）
// 今日消息发送数 / 发圈子人数 / 视频发送量
const todayMessages = ref<number | null>(null)
const todayMomentPosters = ref<number | null>(null)
const todayVideos = ref<number | null>(null)

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

/** 今日新增用户：取趋势接口最后一天（最新）的真实数据，作为四宫格真实指标 */
const todayNewUsers = computed(() => {
  const list = trendData.newUserList
  return list.length ? list[list.length - 1] : 0
})

const genderTotal = computed(() => genderData.male + genderData.female)

/** 男女占比（整数百分比）：用于洞察面板横向比例条宽度 */
const malePct = computed(() => {
  const total = genderTotal.value || 0
  return total ? Math.round((genderData.male / total) * 100) : 0
})

const femalePct = computed(() => {
  const total = genderTotal.value || 0
  return total ? Math.round((genderData.female / total) * 100) : 0
})

/** 年龄分布主力区间：取 count 最大的年龄段，用于洞察面板一句话结论 */
const topAgeRange = computed(() => {
  if (!ageData.ranges.length || !ageData.counts.length) return '—'
  const maxIdx = ageData.counts.indexOf(Math.max(...ageData.counts))
  return ageData.ranges[maxIdx] || '—'
})

/** 主力年龄段的人数 */
const topAgeCount = computed(() => {
  if (!ageData.counts.length) return 0
  return Math.max(...ageData.counts)
})

/** 支付订单趋势累计单数 */
const payTrendSummary = computed(() => {
  if (!payTrendData.orderList.length) return null
  const total = payTrendData.orderList.reduce((sum, n) => sum + n, 0)
  return { total }
})

/** 支付方式分布总单数 */
const payTypeTotal = computed(() => payTypeData.alipay + payTypeData.wechat + payTypeData.sandbox)

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

const payTrendOption = computed<echarts.EChartsOption>(() => ({
  tooltip: { ...tooltipBase, trigger: 'axis', axisPointer: { type: 'line', lineStyle: { color: BRAND.primaryLight7 } } },
  grid: { left: 8, right: 16, top: 16, bottom: 8, containLabel: true },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: payTrendData.dates,
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
      name: '已支付订单',
      type: 'line',
      smooth: true,
      showSymbol: false,
      symbol: 'circle',
      symbolSize: 6,
      data: payTrendData.orderList,
      lineStyle: { width: 2, color: BRAND.primaryDeep },
      itemStyle: { color: BRAND.primaryDeep },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: hexToRgba(BRAND.primaryDeep, 0.22) },
          { offset: 1, color: hexToRgba(BRAND.primaryDeep, 0.01) },
        ]),
      },
    },
  ],
}))

const payTypeOption = computed<echarts.EChartsOption>(() => {
  const data = [
    { value: payTypeData.alipay, name: '支付宝', itemStyle: { color: '#3b82f6' } },
    { value: payTypeData.wechat, name: '微信', itemStyle: { color: '#00b894' } },
    { value: payTypeData.sandbox, name: '沙箱', itemStyle: { color: '#c9cdd4' } },
  ]
  return {
    tooltip: { ...tooltipBase, trigger: 'item', formatter: '{b}: {c} 单 ({d}%)' },
    legend: {
      orient: 'horizontal',
      bottom: 0,
      icon: 'circle',
      itemWidth: 8,
      itemHeight: 8,
      textStyle: { fontSize: 12, color: BRAND.chartInk2 },
    },
    series: [
      {
        type: 'pie',
        radius: ['48%', '72%'],
        center: ['50%', '42%'],
        avoidLabelOverlap: false,
        label: { show: false },
        labelLine: { show: false },
        data,
      },
    ],
  }
})

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

async function loadPayTrend() {
  const res = await getPayTrendApi(trendDays.value)
  payTrendData.dates = res.dates ?? []
  payTrendData.orderList = res.orderList ?? []
}

async function loadPayType() {
  const res = await getPayTypeApi()
  payTypeData.alipay = res.alipay ?? 0
  payTypeData.wechat = res.wechat ?? 0
  payTypeData.sandbox = res.sandbox ?? 0
}

async function loadAll() {
  loading.value = true
  const results = await Promise.allSettled([loadOverview(), loadTrend(), loadGender(), loadAge(), loadPayTrend(), loadPayType()])
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
    await loadPayTrend()
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

.dash-toolbar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--xfjy-space-2);
  margin-bottom: var(--xfjy-space-4);
}

.dash-updated {
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

/* ==================== 平台实时数据·今日指标横排 ==================== */
.rt-metrics {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--xfjy-space-2);
  margin-bottom: var(--xfjy-space-3);
}

.rt-metric {
  display: flex;
  align-items: center;
  gap: var(--xfjy-space-2);
  padding: var(--xfjy-space-2) var(--xfjy-space-3);
  background: var(--xfjy-bg-hover);
  border-radius: var(--xfjy-radius-md);
}

.rt-metric__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  border-radius: var(--xfjy-radius-sm);
}

.rt-metric__body {
  min-width: 0;
}

.rt-metric__value {
  font-size: var(--xfjy-font-size-lg);
  font-weight: 700;
  line-height: 1.1;
  letter-spacing: -0.3px;
  font-variant-numeric: tabular-nums;
  color: var(--xfjy-text-primary);
}

.rt-metric__label {
  margin-top: 2px;
  font-size: var(--xfjy-font-size-xs);
  color: var(--xfjy-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 待接入标记：灰色小标签，明确区分真实数据与占位 */
.rt-metric__pending {
  display: inline-block;
  margin-left: 4px;
  font-size: 10px;
  line-height: 1;
  padding: 2px 5px;
  border-radius: 8px;
  color: var(--xfjy-text-disabled);
  background: var(--xfjy-bg-card);
  vertical-align: middle;
}

/* ==================== 数据洞察面板 ==================== */
.insight-block {
  padding: var(--xfjy-space-3) 0;
  border-bottom: 1px solid var(--xfjy-border-light);
}

.insight-block:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.insight-block:first-of-type {
  padding-top: 0;
}

.insight-block__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--xfjy-space-2);
  margin-bottom: var(--xfjy-space-3);
}

.insight-block__title {
  font-size: var(--xfjy-font-size-sm);
  font-weight: 600;
  color: var(--xfjy-text-regular);
}

.insight-block__meta {
  font-size: var(--xfjy-font-size-xs);
  color: var(--xfjy-text-secondary);
  font-variant-numeric: tabular-nums;
}

/* 待接入标记：灰色小标签，明确区分真实数据与占位 */
.insight-pending {
  flex-shrink: 0;
  font-size: var(--xfjy-font-size-xs);
  line-height: 1;
  padding: 3px 8px;
  border-radius: 10px;
  color: var(--xfjy-text-secondary);
  background: var(--xfjy-bg-hover);
}

/* 占位空态 */
.insight-empty {
  font-size: var(--xfjy-font-size-xs);
  line-height: 1.6;
  color: var(--xfjy-text-disabled);
  padding: var(--xfjy-space-2) 0;
}

/* ==================== 用户结构洞察·横向比例条 ==================== */
.insight-profile {
  display: flex;
  flex-direction: column;
  gap: var(--xfjy-space-2);
}

.insight-profile__row {
  display: flex;
  align-items: center;
  gap: var(--xfjy-space-2);
  font-size: var(--xfjy-font-size-sm);
}

/* 性别比例条行：标签 | 比例条 | 人数百分比，横向铺满 */
.insight-profile__row--bar {
  gap: var(--xfjy-space-3);
}

.insight-profile__bar {
  flex: 1;
  display: flex;
  height: 10px;
  border-radius: 5px;
  overflow: hidden;
  background: var(--xfjy-bg-hover);
}

.insight-profile__bar-male {
  background: var(--xfjy-chart-2);
  transition: width 0.3s;
}

.insight-profile__bar-female {
  background: var(--xfjy-primary);
  transition: width 0.3s;
}

.insight-profile__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.insight-profile__label {
  color: var(--xfjy-text-regular);
  min-width: 48px;
  flex-shrink: 0;
}

.insight-profile__pct {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  color: var(--xfjy-text-primary);
  margin-left: auto;
}

.insight-profile__num {
  font-variant-numeric: tabular-nums;
  color: var(--xfjy-text-secondary);
  min-width: 96px;
  text-align: right;
}

.insight-profile__row--age .insight-profile__pct {
  color: var(--xfjy-primary-deep);
}
</style>
