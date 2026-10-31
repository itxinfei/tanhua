<template>
  <div class="page-container">
    <!-- 统计卡片 -->
    <el-row :gutter="16">
      <el-col :xs="12" :sm="6" v-for="card in statCards" :key="card.label">
        <StatCard :label="card.label" :value="card.value" :change="card.change" :icon="card.icon" :suffix="card.suffix" :icon-bg="card.bg" />
      </el-col>
    </el-row>

    <!-- 趋势大图 + 性别/年龄 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :xs="24" :lg="16">
        <div class="chart-card">
          <div class="chart-header">
            <span class="chart-title">用户趋势</span>
            <el-radio-group v-model="trendDays" size="small" @change="loadTrend">
              <el-radio-button :value="7">近7天</el-radio-button>
              <el-radio-button :value="30">近30天</el-radio-button>
            </el-radio-group>
          </div>
          <BaseChart :option="trendOption" height="240px" />
          <div v-if="!hasDau" class="chart-hint">DAU 统计待接入，当前仅展示新增用户</div>
        </div>
      </el-col>
      <el-col :xs="24" :lg="8">
        <div class="chart-card">
          <div class="chart-header">
            <span class="chart-title">性别比例</span>
          </div>
          <BaseChart :option="genderOption" height="180px" />
        </div>
        <div class="chart-card" style="margin-top: 12px">
          <div class="chart-header">
            <span class="chart-title">年龄分布</span>
          </div>
          <BaseChart :option="ageOption" height="180px" />
        </div>
      </el-col>
    </el-row>

    <!-- 转化率 + 平台总览 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :xs="24" :lg="8">
        <div class="chart-card">
          <div class="chart-header">
            <span class="chart-title">匹配率 & 付费率趋势</span>
          </div>
          <BaseChart v-if="hasRate" :option="rateOption" height="200px" />
          <div v-else class="chart-empty">匹配率 / 付费率统计待接入</div>
        </div>
      </el-col>
      <el-col :xs="24" :lg="16">
        <div class="chart-card">
          <div class="chart-header"><span class="chart-title">平台总览</span></div>
          <el-row :gutter="12">
            <el-col :xs="12" :sm="8" v-for="item in overviewCards" :key="item.label">
              <div class="ov-card">
                <div class="ov-card__icon" :style="{ background: item.bg, color: item.color }">
                  <el-icon :size="16"><component :is="item.icon" /></el-icon>
                </div>
                <div class="ov-card__n">{{ formatNumber(item.value) }}</div>
                <div class="ov-card__l">{{ item.label }}</div>
              </div>
            </el-col>
          </el-row>
          <div class="todo-row">
            <div class="todo-item" v-for="todo in todoList" :key="todo.label" @click="todo.action">
              <span class="todo-label">{{ todo.label }}</span>
              <el-badge :value="todo.count" :type="todo.count > 0 ? 'danger' : 'info'" />
            </div>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'
import * as echarts from 'echarts'
import { StatCard, BaseChart } from '@/components'
import {
  getStatOverviewApi,
  getTrendDataApi,
  getGenderRatioApi,
  getAgeDistributionApi,
} from '@/api/dashboard'
import { getBrandColors, hexToRgba } from '@/utils/brand'
const BRAND = getBrandColors()

const router = useRouter()
const appStore = useAppStore()

// ==================== 统计卡片 ====================
const statCards = ref<Array<{ label: string; value: number | null; suffix: string; change: number | null; icon: string; bg: string }>>([
  { label: '日活跃用户(DAU)', value: 0, suffix: '', change: 0, icon: 'TrendCharts', bg: 'linear-gradient(135deg, var(--xfjy-primary-deep), var(--xfjy-primary))' },
  { label: '今日新增用户', value: 0, suffix: '', change: 0, icon: 'UserFilled', bg: 'linear-gradient(135deg, var(--xfjy-primary), var(--xfjy-primary-soft))' },
  { label: '匹配成功率', value: 0, suffix: '%', change: 0, icon: 'Connection', bg: 'linear-gradient(135deg, var(--xfjy-primary-violet), var(--xfjy-primary-deep))' },
  { label: '付费转化率', value: 0, suffix: '%', change: 0, icon: 'Wallet', bg: 'linear-gradient(135deg, var(--xfjy-primary-soft), var(--xfjy-primary-mist))' },
])

const overview = reactive({
  dau: 0,
  dauChange: 0,
  newUsers: 0,
  newUsersChange: 0,
  matchRate: 0,
  matchRateChange: 0,
  payRate: 0,
  payRateChange: 0,
  totalUsers: 0,
  totalPosts: 0,
  totalReports: 0,
  pendingReviews: 0,
})

const overviewCards = computed(() => [
  { label: '注册用户', value: overview.totalUsers, icon: 'User', bg: 'var(--xfjy-primary-50)', color: 'var(--xfjy-primary)' },
  { label: '动态总数', value: overview.totalPosts, icon: 'Document', bg: 'var(--xfjy-primary-50)', color: 'var(--xfjy-primary-deep)' },
  { label: '今日DAU', value: overview.dau, icon: 'DataLine', bg: 'var(--xfjy-primary-50)', color: 'var(--xfjy-primary)' },
  { label: '今日新增', value: overview.newUsers, icon: 'CirclePlus', bg: 'var(--xfjy-primary-50)', color: 'var(--xfjy-primary-deep)' },
  { label: '待审核', value: overview.pendingReviews, icon: 'Warning', bg: '#fff5f0', color: 'var(--el-color-warning)' },
  { label: '待举报', value: overview.totalReports, icon: 'WarningFilled', bg: '#fff5f0', color: 'var(--el-color-danger)' },
])

const todoList = ref([
  { label: '待审核内容', count: 0, action: () => router.push('/audit/review') },
  { label: '待处理举报', count: 0, action: () => router.push('/audit/report') },
  { label: '草稿公告', count: 0, action: () => router.push('/operation/notice') },
])

// ==================== 图表数据 ====================
const trendDays = ref(7)
const trendData = ref<{ dates: string[]; dauList: number[] | null; newUserList: number[] }>({ dates: [], dauList: null, newUserList: [] })
const genderData = ref<{ male: number; female: number }>({ male: 0, female: 0 })
const rateData = ref<{ dates: string[]; matchRateList: number[] | null; payRateList: number[] | null }>({ dates: [], matchRateList: null, payRateList: null })
const ageData = ref<{ ranges: string[]; counts: number[] }>({ ranges: [], counts: [] })

const hasDau = computed(() => Array.isArray(trendData.value.dauList) && trendData.value.dauList.length > 0)
const hasRate = computed(
  () => Array.isArray(rateData.value.matchRateList) && rateData.value.matchRateList.length > 0
)

// ==================== 图表配置 ====================
const trendOption = computed<echarts.EChartsOption>(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: hasDau.value ? ['DAU', '新增用户'] : ['新增用户'], bottom: 0 },
  grid: { left: 50, right: 50, top: 24, bottom: 40 },
  xAxis: { type: 'category', data: trendData.value.dates, boundaryGap: false },
  yAxis: [
    { type: 'value', name: 'DAU', position: 'left' },
    { type: 'value', name: '新增', position: 'right' },
  ],
  series: [
    ...(hasDau.value
      ? [
          {
            name: 'DAU',
            type: 'line' as const,
            smooth: true,
            data: trendData.value.dauList as number[],
            itemStyle: { color: BRAND.chartSecondary },
            areaStyle: {
              color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                { offset: 0, color: hexToRgba(BRAND.chartSecondary, 0.3) },
                { offset: 1, color: hexToRgba(BRAND.chartSecondary, 0.02) },
              ]),
            },
          },
        ]
      : []),
    {
      name: '新增用户',
      type: 'line',
      smooth: true,
      yAxisIndex: 1,
      data: trendData.value.newUserList,
      itemStyle: { color: BRAND.chartPrimary },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: hexToRgba(BRAND.chartPrimary, 0.3) },
          { offset: 1, color: hexToRgba(BRAND.chartPrimary, 0.02) },
        ]),
      },
    },
  ],
}))

const genderOption = computed<echarts.EChartsOption>(() => ({
  tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
  legend: { bottom: 0, itemWidth: 10, itemHeight: 10, textStyle: { fontSize: 11 } },
  series: [
    {
      type: 'pie',
      radius: ['20%', '44%'],
      center: ['50%', '42%'],
      avoidLabelOverlap: false,
      label: { show: true, formatter: '{b} {d}%', fontSize: 9 },
      data: [
        { value: genderData.value.male, name: '男性', itemStyle: { color: BRAND.chartSecondary } },
        { value: genderData.value.female, name: '女性', itemStyle: { color: BRAND.chartPrimary } },
      ],
    },
  ],
}))

const rateOption = computed<echarts.EChartsOption>(() => ({
  tooltip: { trigger: 'axis', formatter: '{b}<br/>{a}: {c}%' },
  legend: { data: ['匹配率', '付费率'], bottom: 0, itemWidth: 8, itemHeight: 8, textStyle: { fontSize: 9 } },
  grid: { left: 32, right: 12, top: 10, bottom: 26 },
  xAxis: { type: 'category', data: rateData.value.dates, boundaryGap: false, axisLabel: { fontSize: 8, interval: 0 } },
  yAxis: { type: 'value', axisLabel: { formatter: '{value}%', fontSize: 8 } },
  series: [
    {
      name: '匹配率',
      type: 'line',
      smooth: true,
      data: rateData.value.matchRateList ?? [],
      itemStyle: { color: BRAND.chartThird },
    },
    {
      name: '付费率',
      type: 'line',
      smooth: true,
      data: rateData.value.payRateList ?? [],
      itemStyle: { color: BRAND.chartPrimary },
    },
  ],
}))

const ageOption = computed<echarts.EChartsOption>(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 36, right: 14, top: 8, bottom: 20 },
  xAxis: { type: 'category', data: ageData.value.ranges, axisLabel: { fontSize: 9 } },
  yAxis: { type: 'value', axisLabel: { fontSize: 9 } },
  series: [
    {
      type: 'bar',
      data: ageData.value.counts,
      barWidth: '40%',
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: BRAND.chartPrimary },
          { offset: 1, color: hexToRgba(BRAND.chartPrimary, 0.3) },
        ]),
        borderRadius: [2, 2, 0, 0],
      },
    },
  ],
}))

// ==================== 工具函数 ====================
function formatNumber(n: number | null | undefined): string {
  if (n === null || n === undefined || isNaN(n)) return '待接入'
  return n.toLocaleString('zh-CN')
}

function generateMockTrend(days: number) {
  const dates: string[] = []
  const dauList: number[] = []
  const newUserList: number[] = []
  const matchRateList: number[] = []
  const payRateList: number[] = []
  for (let i = days - 1; i >= 0; i--) {
    const d = new Date()
    d.setDate(d.getDate() - i)
    dates.push(`${d.getMonth() + 1}/${d.getDate()}`)
    dauList.push(Math.floor(8000 + Math.random() * 6000))
    newUserList.push(Math.floor(300 + Math.random() * 400))
    matchRateList.push(+(35 + Math.random() * 20).toFixed(1))
    payRateList.push(+(5 + Math.random() * 8).toFixed(1))
  }
  return { dates, dauList, newUserList, matchRateList, payRateList }
}

// ==================== 数据加载 ====================
async function loadOverview() {
  try {
    const res = await getStatOverviewApi()
    Object.assign(overview, res)
  } catch {
    if (import.meta.env.DEV) {
      Object.assign(overview, {
        dau: 12856, dauChange: 5.2,
        newUsers: 486, newUsersChange: 12.3,
        matchRate: 42.8, matchRateChange: 2.1,
        payRate: 8.6, payRateChange: -0.5,
        totalUsers: 358920, totalPosts: 1284567,
        totalReports: 23, pendingReviews: 15,
      })
    }
  }
  statCards.value[0].value = overview.dau
  statCards.value[0].change = overview.dauChange
  statCards.value[1].value = overview.newUsers
  statCards.value[1].change = overview.newUsersChange
  statCards.value[2].value = overview.matchRate
  statCards.value[2].change = overview.matchRateChange
  statCards.value[3].value = overview.payRate
  statCards.value[3].change = overview.payRateChange
  todoList.value[0].count = overview.pendingReviews
  todoList.value[1].count = overview.totalReports
}

async function loadTrend() {
  let data
  try {
    data = await getTrendDataApi(trendDays.value)
  } catch {
    if (import.meta.env.DEV) {
      data = generateMockTrend(trendDays.value)
    }
  }
  if (data) {
    trendData.value = { dates: data.dates, dauList: data.dauList, newUserList: data.newUserList }
    rateData.value = { dates: data.dates, matchRateList: data.matchRateList, payRateList: data.payRateList }
  }
}

async function loadGender() {
  let male = 0, female = 0
  try {
    const res = await getGenderRatioApi()
    male = res.male
    female = res.female
  } catch {
    if (import.meta.env.DEV) {
      male = 58
      female = 42
    }
  }
  genderData.value = { male, female }
}

async function loadAge() {
  let ranges: string[] = [], counts: number[] = []
  try {
    const res = await getAgeDistributionApi()
    ranges = res.ranges
    counts = res.counts
  } catch {
    if (import.meta.env.DEV) {
      ranges = ['18-22', '23-27', '28-32', '33-37', '38-42', '43+']
      counts = [45200, 98600, 87400, 52300, 28100, 12300]
    }
  }
  ageData.value = { ranges, counts }
}

// ==================== 生命周期 ====================
onMounted(async () => {
  await loadOverview()
  loadTrend()
  loadGender()
  loadAge()
})

// 侧边栏折叠时图表自适应（等待 0.3s CSS transition 完成后再 resize）
watch(
  () => appStore.sidebarCollapsed,
  () => {
    setTimeout(() => {
      // BaseChart 内部已监听 resize，这里只需等待过渡完成
    }, 350)
  }
)
</script>

<style scoped>
.chart-hint {
  margin-top: 6px;
  font-size: var(--xfjy-font-size-xs);
  color: var(--xfjy-text-secondary);
  text-align: center;
}

.chart-empty {
  height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--xfjy-font-size-sm);
  color: var(--xfjy-text-secondary);
  background: var(--xfjy-bg-subtle, transparent);
  border: 1px dashed var(--xfjy-border);
  border-radius: var(--xfjy-radius-md);
}

.page-container {
  padding: var(--xfjy-space-6);
  box-sizing: border-box;
}

.chart-row {
  margin-bottom: var(--xfjy-space-4);
}

.chart-card {
  background: var(--xfjy-bg-card);
  border: 1px solid var(--xfjy-border);
  border-radius: var(--xfjy-radius-lg);
  padding: var(--xfjy-space-3) var(--xfjy-space-4);
  box-shadow: var(--xfjy-shadow-card);
}

.chart-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--xfjy-space-3);
}

.chart-title {
  font-size: var(--xfjy-font-size-md);
  font-weight: 600;
  color: var(--xfjy-text-primary);
  position: relative;
  padding-left: var(--xfjy-space-2);
}

/* 若依式面板标题：玫红竖条 */
.chart-title::before {
  content: "";
  display: inline-block;
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 14px;
  background: var(--xfjy-primary);
  border-radius: 2px;
}

/* 平台总览卡片 */
.ov-card {
  background: var(--xfjy-bg-hover);
  border-radius: var(--xfjy-radius-sm);
  padding: var(--xfjy-space-3) var(--xfjy-space-4);
  text-align: center;
  margin-bottom: var(--xfjy-space-3);
  transition: background 0.2s;
}

.ov-card:hover {
  background: var(--xfjy-primary-50);
}

.ov-card__icon {
  width: 32px;
  height: 32px;
  border-radius: var(--xfjy-radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto var(--xfjy-space-2);
}

.ov-card__n {
  font-size: var(--xfjy-font-size-md);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: var(--xfjy-text-primary);
}

.ov-card__l {
  font-size: var(--xfjy-font-size-xs);
  color: var(--xfjy-text-secondary);
  margin-top: var(--xfjy-space-1);
}

/* 待处理事项 */
.todo-row {
  display: flex;
  gap: var(--xfjy-space-3);
  border-top: 1px solid var(--xfjy-border-light);
  margin-top: var(--xfjy-space-3);
  padding-top: var(--xfjy-space-3);
}

.todo-item {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--xfjy-space-2) var(--xfjy-space-3);
  background: var(--xfjy-bg-hover);
  border: 1px solid var(--xfjy-border-light);
  border-radius: var(--xfjy-radius-sm);
  cursor: pointer;
  transition: background 0.2s;
}

.todo-item:hover {
  background: var(--xfjy-primary-50);
}

.todo-label {
  font-size: var(--xfjy-font-size-sm);
  color: var(--xfjy-text-regular);
}
</style>
