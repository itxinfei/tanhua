<template>
  <div class="page-container">
    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stat-row">
      <el-col :span="6" v-for="card in statCards" :key="card.label">
        <div class="stat-card">
          <div class="stat-icon" :style="{ background: card.bg }">
            <el-icon :size="24"><component :is="card.icon" /></el-icon>
          </div>
          <div>
            <div class="stat-value">
              {{ card.value }}{{ card.suffix }}
              <span class="stat-change" :class="card.change >= 0 ? 'up' : 'down'">
                {{ card.change >= 0 ? '+' : '' }}{{ card.change }}%
              </span>
            </div>
            <div class="stat-label">{{ card.label }}</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 趋势图 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :span="16">
        <div class="chart-card">
          <div class="chart-header">
            <span class="chart-title">用户趋势</span>
            <el-radio-group v-model="trendDays" size="small" @change="loadTrend">
              <el-radio-button :value="7">近7天</el-radio-button>
              <el-radio-button :value="30">近30天</el-radio-button>
            </el-radio-group>
          </div>
          <div ref="trendChartRef" class="chart-box"></div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="chart-card">
          <div class="chart-header">
            <span class="chart-title">性别比例</span>
          </div>
          <div ref="genderChartRef" class="chart-box"></div>
        </div>
      </el-col>
    </el-row>

    <!-- 转化率 + 年龄分布 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :span="12">
        <div class="chart-card">
          <div class="chart-header">
            <span class="chart-title">匹配率 & 付费率趋势</span>
          </div>
          <div ref="rateChartRef" class="chart-box"></div>
        </div>
      </el-col>
      <el-col :span="12">
        <div class="chart-card">
          <div class="chart-header">
            <span class="chart-title">用户年龄分布</span>
          </div>
          <div ref="ageChartRef" class="chart-box"></div>
        </div>
      </el-col>
    </el-row>

    <!-- 快捷信息 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :span="8">
        <div class="chart-card mini-card">
          <div class="chart-header"><span class="chart-title">待处理事项</span></div>
          <div class="todo-list">
            <div class="todo-item" v-for="todo in todoList" :key="todo.label" @click="todo.action">
              <span class="todo-label">{{ todo.label }}</span>
              <el-badge :value="todo.count" :type="todo.count > 0 ? 'danger' : 'info'" />
            </div>
          </div>
        </div>
      </el-col>
      <el-col :span="16">
        <div class="chart-card">
          <div class="chart-header"><span class="chart-title">平台总览</span></div>
          <el-descriptions :column="3" border>
            <el-descriptions-item label="注册用户总数">{{ overview.totalUsers }}</el-descriptions-item>
            <el-descriptions-item label="动态总数">{{ overview.totalPosts }}</el-descriptions-item>
            <el-descriptions-item label="待处理举报">{{ overview.totalReports }}</el-descriptions-item>
            <el-descriptions-item label="今日DAU">{{ overview.dau }}</el-descriptions-item>
            <el-descriptions-item label="今日新增">{{ overview.newUsers }}</el-descriptions-item>
            <el-descriptions-item label="待审核内容">{{ overview.pendingReviews }}</el-descriptions-item>
          </el-descriptions>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import {
  getStatOverviewApi,
  getTrendDataApi,
  getGenderRatioApi,
  getAgeDistributionApi,
} from '@/api/dashboard'

const router = useRouter()

// ==================== 统计卡片 ====================
const statCards = ref([
  { label: '日活跃用户(DAU)', value: 0, suffix: '', change: 0, icon: 'User', bg: 'linear-gradient(135deg, #667eea, #764ba2)' },
  { label: '今日新增用户', value: 0, suffix: '', change: 0, icon: 'Plus', bg: 'linear-gradient(135deg, #f093fb, #f5576c)' },
  { label: '匹配成功率', value: 0, suffix: '%', change: 0, icon: 'Connection', bg: 'linear-gradient(135deg, #4facfe, #00f2fe)' },
  { label: '付费转化率', value: 0, suffix: '%', change: 0, icon: 'Wallet', bg: 'linear-gradient(135deg, #43e97b, #38f9d7)' },
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

// 待处理事项
const todoList = ref([
  { label: '待审核内容', count: 0, action: () => router.push('/review/workbench') },
  { label: '待处理举报', count: 0, action: () => router.push('/report/list') },
  { label: '草稿公告', count: 0, action: () => router.push('/notice/list') },
])

// ==================== 图表 ====================
const trendChartRef = ref<HTMLElement>()
const genderChartRef = ref<HTMLElement>()
const rateChartRef = ref<HTMLElement>()
const ageChartRef = ref<HTMLElement>()

let trendChart: echarts.ECharts | null = null
let genderChart: echarts.ECharts | null = null
let rateChart: echarts.ECharts | null = null
let ageChart: echarts.ECharts | null = null

const trendDays = ref(7)

// Mock 数据生成
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

function initTrendChart(data: ReturnType<typeof generateMockTrend>) {
  if (!trendChartRef.value) return
  trendChart = echarts.init(trendChartRef.value)
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['DAU', '新增用户'], bottom: 0 },
    grid: { left: 50, right: 50, top: 20, bottom: 40 },
    xAxis: { type: 'category', data: data.dates, boundaryGap: false },
    yAxis: [
      { type: 'value', name: 'DAU', position: 'left' },
      { type: 'value', name: '新增', position: 'right' },
    ],
    series: [
      {
        name: 'DAU',
        type: 'line',
        smooth: true,
        data: data.dauList,
        itemStyle: { color: '#667eea' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(102,126,234,0.3)' },
            { offset: 1, color: 'rgba(102,126,234,0.02)' },
          ]),
        },
      },
      {
        name: '新增用户',
        type: 'line',
        smooth: true,
        yAxisIndex: 1,
        data: data.newUserList,
        itemStyle: { color: '#f5576c' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(245,87,108,0.3)' },
            { offset: 1, color: 'rgba(245,87,108,0.02)' },
          ]),
        },
      },
    ],
  })
}

function initGenderChart(male: number, female: number) {
  if (!genderChartRef.value) return
  genderChart = echarts.init(genderChartRef.value)
  genderChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0 },
    series: [
      {
        type: 'pie',
        radius: ['40%', '70%'],
        center: ['50%', '45%'],
        avoidLabelOverlap: false,
        label: { show: true, formatter: '{b}\n{d}%' },
        data: [
          { value: male, name: '男性', itemStyle: { color: '#4facfe' } },
          { value: female, name: '女性', itemStyle: { color: '#f5576c' } },
        ],
      },
    ],
  })
}

function initRateChart(data: ReturnType<typeof generateMockTrend>) {
  if (!rateChartRef.value) return
  rateChart = echarts.init(rateChartRef.value)
  rateChart.setOption({
    tooltip: { trigger: 'axis', formatter: '{b}<br/>{a}: {c}%' },
    legend: { data: ['匹配率', '付费率'], bottom: 0 },
    grid: { left: 50, right: 30, top: 20, bottom: 40 },
    xAxis: { type: 'category', data: data.dates, boundaryGap: false },
    yAxis: { type: 'value', axisLabel: { formatter: '{value}%' } },
    series: [
      {
        name: '匹配率',
        type: 'line',
        smooth: true,
        data: data.matchRateList,
        itemStyle: { color: '#43e97b' },
      },
      {
        name: '付费率',
        type: 'line',
        smooth: true,
        data: data.payRateList,
        itemStyle: { color: '#fa709a' },
      },
    ],
  })
}

function initAgeChart(ranges: string[], counts: number[]) {
  if (!ageChartRef.value) return
  ageChart = echarts.init(ageChartRef.value)
  ageChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 50, right: 30, top: 20, bottom: 30 },
    xAxis: { type: 'category', data: ranges },
    yAxis: { type: 'value' },
    series: [
      {
        type: 'bar',
        data: counts,
        barWidth: '50%',
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#667eea' },
            { offset: 1, color: '#764ba2' },
          ]),
          borderRadius: [4, 4, 0, 0],
        },
      },
    ],
  })
}

// ==================== 数据加载 ====================
async function loadOverview() {
  try {
    const res = await getStatOverviewApi()
    Object.assign(overview, res)
  } catch {
    // Mock 兜底仅开发环境生效，生产环境保持真实零值避免假数据误导
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
  // 更新卡片
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
    // Mock 兜底仅开发环境生效，生产环境跳过图表渲染
    if (import.meta.env.DEV) {
      data = generateMockTrend(trendDays.value)
    }
  }
  if (data) {
    initTrendChart(data)
    initRateChart(data)
  }
}

async function loadGender() {
  let male = 0, female = 0
  try {
    const res = await getGenderRatioApi()
    male = res.male
    female = res.female
  } catch {
    // Mock 兜底仅开发环境生效
    if (import.meta.env.DEV) {
      male = 58
      female = 42
    }
  }
  initGenderChart(male, female)
}

async function loadAge() {
  let ranges: string[] = [], counts: number[] = []
  try {
    const res = await getAgeDistributionApi()
    ranges = res.ranges
    counts = res.counts
  } catch {
    // Mock 兜底仅开发环境生效
    if (import.meta.env.DEV) {
      ranges = ['18-22', '23-27', '28-32', '33-37', '38-42', '43+']
      counts = [45200, 98600, 87400, 52300, 28100, 12300]
    }
  }
  initAgeChart(ranges, counts)
}

// ==================== 生命周期 ====================
function handleResize() {
  trendChart?.resize()
  genderChart?.resize()
  rateChart?.resize()
  ageChart?.resize()
}

onMounted(async () => {
  await loadOverview()
  await nextTick()
  loadTrend()
  loadGender()
  loadAge()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose()
  genderChart?.dispose()
  rateChart?.dispose()
  ageChart?.dispose()
})
</script>

<style scoped>
.stat-row {
  margin-bottom: 16px;
}

.stat-change {
  font-size: 12px;
  font-weight: 400;
  margin-left: 8px;
}

.stat-change.up {
  color: #67c23a;
}

.stat-change.down {
  color: #f56c6c;
}

.chart-row {
  margin-bottom: 16px;
}

.chart-card {
  background: #fff;
  border-radius: 8px;
  padding: 16px 20px;
}

.chart-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.chart-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.chart-box {
  height: 300px;
}

.todo-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 8px 0;
}

.todo-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: #f5f7fa;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.2s;
}

.todo-item:hover {
  background: #ecf5ff;
}

.todo-label {
  font-size: 14px;
  color: #606266;
}
</style>