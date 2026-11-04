<template>
  <div v-loading="loading" class="page-container dashboard" element-loading-text="看板数据加载中">
    <!-- ============ 1. 顶部控制条 ============ -->
    <div class="dash-toolbar">
      <div class="dash-toolbar__left">
        <span class="dash-toolbar__title">运营数据看板</span>
        <span class="live-badge">
          <span class="live-badge__dot"><span class="live-badge__wave" /></span>
          实时
        </span>
        <span v-if="autoRefresh" class="dash-toolbar__meta">{{ countdown }}s 后刷新</span>
        <span v-if="lastUpdated" class="dash-toolbar__meta">
          更新于 {{ formatClock(lastUpdated) }}
        </span>
      </div>
      <div class="dash-toolbar__right">
        <span class="dash-toolbar__clock">{{ formatClock(now) }}</span>
        <el-radio-group v-model="days" size="small">
          <el-radio-button :value="7">近 7 天</el-radio-button>
          <el-radio-button :value="30">近 30 天</el-radio-button>
        </el-radio-group>
        <span class="dash-toolbar__switch">
          <span class="dash-toolbar__meta">自动刷新</span>
          <el-switch v-model="autoRefresh" />
        </span>
        <el-button :loading="refreshing" @click="refreshAll">
          <el-icon class="dash-toolbar__refresh-icon"><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
    </div>

    <!-- ============ 2. 核心指标 KPI ============ -->
    <el-row :gutter="16" class="dash-row">
      <el-col
        v-for="kpi in kpiList"
        :key="kpi.label"
        :xs="24"
        :sm="12"
        :lg="8"
        :xl="4"
      >
        <KpiCard
          :label="kpi.label"
          :value="kpi.value"
          :icon="kpi.icon"
          :icon-color="kpi.iconColor"
          :icon-bg="kpi.iconBg"
          :delta="kpi.delta"
          :hint="kpi.hint"
          :formatter="kpi.formatter"
          :spark="kpi.spark"
          :spark-color="kpi.sparkColor"
          :pulse="kpi.pulse"
        />
      </el-col>
    </el-row>

    <!-- ============ 3. 主趋势 + 实时动态 ============ -->
    <el-row :gutter="16" class="dash-row">
      <el-col :xs="24" :md="16">
        <DashPanel :title="trendTabMeta.title" :sub="trendSub">
          <template #extra>
            <el-radio-group v-model="trendTab" size="small" class="trend-tabs">
              <el-radio-button value="register">新增注册</el-radio-button>
              <el-radio-button value="dau">日活 DAU</el-radio-button>
              <el-radio-button value="match">匹配互动</el-radio-button>
              <el-radio-button value="content">内容发布</el-radio-button>
              <el-radio-button value="revenue">付费趋势</el-radio-button>
            </el-radio-group>
          </template>
          <BaseChart v-if="mainTrendOption" :option="mainTrendOption" height="300px" />
          <div v-else class="chart-empty">
            <el-empty description="数据积累中" :image-size="72" />
          </div>
        </DashPanel>
      </el-col>

      <el-col :xs="24" :md="8">
        <DashPanel title="实时动态" sub="5 分钟内心跳为在线">
          <div class="rt-hero">
            <div class="rt-hero__main">
              <div class="rt-hero__label">
                <span class="live-badge__dot live-badge__dot--sm"><span class="live-badge__wave" /></span>
                当前在线
              </div>
              <div class="rt-hero__value">{{ onlineDisplay === null ? '—' : formatCount(onlineDisplay) }}</div>
            </div>
            <div class="rt-hero__side">
              <div class="rt-hero__item">
                <span class="rt-hero__item-label">今日峰值</span>
                <span class="rt-hero__item-value">{{ formatCount(online?.peakToday) }}</span>
              </div>
              <div class="rt-hero__item">
                <span class="rt-hero__item-label">今日活跃</span>
                <span class="rt-hero__item-value">{{ formatCount(online?.todayActive) }}</span>
              </div>
            </div>
          </div>

          <div class="rt-grid">
            <div class="rt-item">
              <span class="rt-item__icon rt-item__icon--rose">
                <el-icon><ChatDotRound /></el-icon>
              </span>
              <div class="rt-item__body">
                <div class="rt-item__value">{{ formatCount(realtime?.todayMessages) }}</div>
                <div class="rt-item__label">今日消息</div>
              </div>
            </div>
            <div class="rt-item">
              <span class="rt-item__icon rt-item__icon--violet">
                <el-icon><Share /></el-icon>
              </span>
              <div class="rt-item__body">
                <div class="rt-item__value">{{ formatCount(realtime?.todayPosters) }}</div>
                <div class="rt-item__label">发圈子人数</div>
              </div>
            </div>
            <div class="rt-item">
              <span class="rt-item__icon rt-item__icon--amber">
                <el-icon><VideoCamera /></el-icon>
              </span>
              <div class="rt-item__body">
                <div class="rt-item__value">{{ formatCount(realtime?.todayVideos) }}</div>
                <div class="rt-item__label">视频发送量</div>
              </div>
            </div>
          </div>
        </DashPanel>
      </el-col>
    </el-row>

    <!-- ============ 4. 匹配漏斗 / 留存 / 付费概览 ============ -->
    <el-row :gutter="16" class="dash-row">
      <!-- 匹配漏斗 -->
      <el-col :xs="24" :md="8">
        <DashPanel title="今日匹配漏斗" sub="滑动 → 右滑 → 互喜匹配">
          <template #extra>
            <span class="panel-side-meta">{{ formatCount(funnel?.levels[0].value) }} 次滑动</span>
          </template>
          <div v-if="funnel" class="funnel">
            <div
              v-for="level in funnel.levels"
              :key="level.name"
              class="funnel-level"
            >
              <div class="funnel-level__track">
                <div class="funnel-level__bar" :class="level.cls" :style="{ width: level.pct + '%' }" />
              </div>
              <div class="funnel-level__meta">
                <span class="funnel-level__name">{{ level.name }}</span>
                <span class="funnel-level__count">{{ formatCount(level.value) }}</span>
              </div>
            </div>
            <div class="rate-grid">
              <div class="rate-item">
                <span class="rate-item__label">喜欢率（右滑/滑动）</span>
                <span class="rate-item__value">{{ ratePercent(funnel.likeRate) }}</span>
              </div>
              <div class="rate-item">
                <span class="rate-item__label">匹配率（匹配/右滑）</span>
                <span class="rate-item__value rate-item__value--rose">{{ ratePercent(funnel.matchRate) }}</span>
              </div>
            </div>
          </div>
          <div v-else class="chart-empty">
            <el-empty description="今日暂无滑动数据" :image-size="72" />
          </div>
        </DashPanel>
      </el-col>

      <!-- 留存 -->
      <el-col :xs="24" :md="8">
        <DashPanel title="用户留存" sub="各注册批次今日仍活跃占比">
          <div class="retention-grid">
            <div v-for="item in activity?.retention ?? []" :key="item.days" class="retention-item">
              <span class="retention-item__label">{{ item.label }}</span>
              <span class="retention-item__value">{{ ratePercent(item.rate) }}</span>
              <span class="retention-item__sub">
                <template v-if="item.cohortSize > 0">
                  {{ item.retained ?? '—' }}/{{ item.cohortSize }} 人
                </template>
                <template v-else>无注册样本</template>
              </span>
            </div>
          </div>
          <p class="panel-note">留存基于日活埋点计算，部署当日起开始积累。</p>
        </DashPanel>
      </el-col>

      <!-- 付费概览 -->
      <el-col :xs="24" :md="8">
        <DashPanel title="付费概览" sub="累计支付订单结构">
          <BaseChart
            v-if="payTypeOption"
            :option="payTypeOption"
            height="220px"
          />
          <div v-else class="chart-empty chart-empty--compact">
            <el-empty description="暂无支付数据" :image-size="60" />
          </div>
          <div class="pay-stats">
            <div class="mini-stat">
              <span class="mini-stat__label">累计付费率</span>
              <span class="mini-stat__value">{{ ratePercent(revenue?.payRate ?? null) }}</span>
            </div>
            <div class="mini-stat">
              <span class="mini-stat__label">今日 ARPPU</span>
              <span class="mini-stat__value">{{ arppuText }}</span>
            </div>
            <div class="mini-stat">
              <span class="mini-stat__label">今日订单</span>
              <span class="mini-stat__value">{{ formatCount(revenue?.overview.todayOrders) }}</span>
            </div>
          </div>
        </DashPanel>
      </el-col>
    </el-row>

    <!-- ============ 5. 用户结构：性别 / 年龄 / 城市 ============ -->
    <el-row :gutter="16" class="dash-row">
      <el-col :xs="24" :md="8">
        <DashPanel title="性别分布" sub="全部注册用户">
          <BaseChart v-if="genderOption" :option="genderOption" height="300px" />
          <div v-else class="chart-empty">
            <el-empty description="暂无用户数据" :image-size="72" />
          </div>
        </DashPanel>
      </el-col>
      <el-col :xs="24" :md="8">
        <DashPanel title="年龄结构" sub="按注册资料年龄分桶">
          <BaseChart v-if="ageOption" :option="ageOption" height="300px" />
          <div v-else class="chart-empty">
            <el-empty description="暂无年龄数据" :image-size="72" />
          </div>
        </DashPanel>
      </el-col>
      <el-col :xs="24" :md="8">
        <DashPanel title="地区分布 TOP10" sub="注册资料中的城市">
          <BaseChart v-if="cityOption" :option="cityOption" height="300px" />
          <div v-else class="chart-empty">
            <el-empty description="暂无城市数据" :image-size="72" />
          </div>
        </DashPanel>
      </el-col>
    </el-row>

    <!-- ============ 6. 内容安全：审核 / 举报 ============ -->
    <el-row :gutter="16" class="dash-row">
      <el-col :xs="24" :md="12">
        <DashPanel title="内容审核" :sub="`近 ${days} 天送审与处理量`">
          <div class="mod-stats">
            <div class="mini-stat">
              <span class="mini-stat__label">待审核</span>
              <span
                class="mini-stat__value"
                :class="{ 'mini-stat__value--amber': (moderation?.review.pending ?? 0) > 0 }"
              >{{ formatCount(moderation?.review.pending) }}</span>
            </div>
            <div class="mini-stat">
              <span class="mini-stat__label">今日通过</span>
              <span class="mini-stat__value mini-stat__value--green">
                {{ formatCount(moderation?.review.todayApproved) }}
              </span>
            </div>
            <div class="mini-stat">
              <span class="mini-stat__label">今日拒绝</span>
              <span class="mini-stat__value mini-stat__value--red">
                {{ formatCount(moderation?.review.todayRejected) }}
              </span>
            </div>
            <div class="mini-stat">
              <span class="mini-stat__label">今日通过率</span>
              <span class="mini-stat__value">{{ ratePercent(reviewPassRate) }}</span>
            </div>
          </div>
          <div class="mod-body">
            <div class="mod-body__chart">
              <BaseChart v-if="reviewOption" :option="reviewOption" height="260px" />
              <div v-else class="chart-empty chart-empty--compact">
                <el-empty description="暂无审核数据" :image-size="60" />
              </div>
            </div>
            <div class="mod-body__types">
              <div class="type-title">待审核类型</div>
              <div v-if="reviewTypeRows.length" class="type-list">
                <div v-for="row in reviewTypeRows" :key="row.name" class="type-row">
                  <div class="type-row__head">
                    <span class="type-row__name">{{ typeLabel(row.name) }}</span>
                    <span class="type-row__count">{{ row.count }}</span>
                  </div>
                  <div class="type-row__track">
                    <div class="type-row__fill" :style="{ width: row.pct + '%' }" />
                  </div>
                </div>
              </div>
              <div v-else class="type-empty">暂无积压</div>
            </div>
          </div>
        </DashPanel>
      </el-col>

      <el-col :xs="24" :md="12">
        <DashPanel title="用户举报" :sub="`近 ${days} 天举报与处理量`">
          <div class="mod-stats">
            <div class="mini-stat">
              <span class="mini-stat__label">待处理</span>
              <span
                class="mini-stat__value"
                :class="{ 'mini-stat__value--red': (moderation?.report.pending ?? 0) > 0 }"
              >{{ formatCount(moderation?.report.pending) }}</span>
            </div>
            <div class="mini-stat">
              <span class="mini-stat__label">处理中</span>
              <span
                class="mini-stat__value"
                :class="{ 'mini-stat__value--amber': (moderation?.report.processing ?? 0) > 0 }"
              >{{ formatCount(moderation?.report.processing) }}</span>
            </div>
            <div class="mini-stat">
              <span class="mini-stat__label">今日新增</span>
              <span class="mini-stat__value">{{ formatCount(moderation?.report.todayNew) }}</span>
            </div>
            <div class="mini-stat">
              <span class="mini-stat__label">今日已处理</span>
              <span class="mini-stat__value mini-stat__value--green">{{ reportTodayDone }}</span>
            </div>
          </div>
          <div class="mod-body">
            <div class="mod-body__chart">
              <BaseChart v-if="reportOption" :option="reportOption" height="260px" />
              <div v-else class="chart-empty chart-empty--compact">
                <el-empty description="暂无举报数据" :image-size="60" />
              </div>
            </div>
            <div class="mod-body__types">
              <div class="type-title">举报对象分布</div>
              <div v-if="reportTypeRows.length" class="type-list">
                <div v-for="row in reportTypeRows" :key="row.name" class="type-row">
                  <div class="type-row__head">
                    <span class="type-row__name">{{ typeLabel(row.name) }}</span>
                    <span class="type-row__count">{{ row.count }}</span>
                  </div>
                  <div class="type-row__track">
                    <div class="type-row__fill" :style="{ width: row.pct + '%' }" />
                  </div>
                </div>
              </div>
              <div v-else class="type-empty">暂无举报</div>
            </div>
          </div>
        </DashPanel>
      </el-col>
    </el-row>

    <!-- ============ 7. 热门内容 ============ -->
    <el-row :gutter="16" class="dash-row">
      <el-col :xs="24" :md="12">
        <DashPanel title="热门动态 TOP5" sub="按点赞数排序">
          <div v-if="hotPosts.length" class="hot-list">
            <div v-for="(post, idx) in hotPosts" :key="post.id" class="hot-item">
              <span class="hot-item__rank" :class="{ 'hot-item__rank--top': idx < 3 }">{{ idx + 1 }}</span>
              <span class="hot-item__text">{{ post.text || '（无文案）' }}</span>
              <span class="hot-item__stats">
                <span class="hot-item__stat">赞 {{ post.likeCount }}</span>
                <span class="hot-item__stat">评 {{ post.commentCount }}</span>
              </span>
            </div>
          </div>
          <div v-else class="chart-empty chart-empty--compact">
            <el-empty description="暂无热门动态" :image-size="60" />
          </div>
        </DashPanel>
      </el-col>
      <el-col :xs="24" :md="12">
        <DashPanel title="热门视频 TOP5" sub="近期小视频">
          <div v-if="hotVideos.length" class="hot-list">
            <div v-for="(video, idx) in hotVideos" :key="video.videoId" class="hot-item">
              <span class="hot-item__rank" :class="{ 'hot-item__rank--top': idx < 3 }">{{ idx + 1 }}</span>
              <span class="hot-item__text">{{ video.text || '（无文案）' }}</span>
            </div>
          </div>
          <div v-else class="chart-empty chart-empty--compact">
            <el-empty description="暂无热门视频" :image-size="60" />
          </div>
        </DashPanel>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, type Component } from 'vue'
import * as echarts from 'echarts'
import {
  User,
  CirclePlus,
  Histogram,
  Connection,
  Aim,
  Wallet,
  Refresh,
  ChatDotRound,
  Share,
  VideoCamera,
} from '@element-plus/icons-vue'
import { BaseChart } from '@/components'
import { getBrandColors } from '@/utils/brand'
import {
  areaLineOption,
  revenueTrendOption,
  donutOption,
  horizontalBarOption,
  moderationTrendOption,
  formatYuan,
  type DonutItem,
} from './chartOptions'
import DashPanel from './components/DashPanel.vue'
import KpiCard from './components/KpiCard.vue'
import { useDashboardData } from './composables/useDashboardData'
import { useCountUp } from './composables/useCountUp'

const BRAND = getBrandColors()
const PALETTE = BRAND.chartPalette
/** 未知性别 / 沙箱支付等中性灰（ECharts canvas 不识别 CSS 变量，取计算色） */
const NEUTRAL = BRAND.neutral

const {
  loading,
  refreshing,
  lastUpdated,
  days,
  autoRefresh,
  countdown,
  now,
  overview,
  registerTrend,
  realtime,
  online,
  activity,
  match,
  demographics,
  revenue,
  moderation,
  dimensionTrend,
  hot,
  refreshAll,
} = useDashboardData()

type ECOption = echarts.EChartsOption

// ==================== 工具函数 ====================
function formatCount(n: number | null | undefined): string {
  if (n === null || n === undefined || Number.isNaN(n)) return '—'
  return n.toLocaleString('zh-CN')
}

function formatClock(d: Date): string {
  const pad = (v: number) => String(v).padStart(2, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

/** 金额：分 → 元（KPI 卡数字/差值用，保留 2 位小数） */
function formatFen(fen: number): string {
  return `¥${(fen / 100).toLocaleString('zh-CN', { maximumFractionDigits: 2 })}`
}

/** 比率：0.0–100 的百分比数值 → 文案，null 显 — */
function ratePercent(n: number | null | undefined): string {
  if (n === null || n === undefined || Number.isNaN(n)) return '—'
  return `${n.toFixed(1)}%`
}

/** 序列末两日差值（今日较昨日），不足两日返回 null */
function lastDiff(list: number[] | undefined): number | null {
  if (!list || list.length < 2) return null
  return list[list.length - 1] - list[list.length - 2]
}

function sum(list: number[]): number {
  return list.reduce((acc, n) => acc + n, 0)
}

/** 审核/举报类型 code → 中文，未知 code 原样展示 */
const TYPE_LABELS: Record<string, string> = {
  post: '动态',
  movement: '动态',
  moment: '动态',
  video: '视频',
  small_video: '视频',
  comment: '评论',
  user: '用户',
  avatar: '头像',
  profile: '资料',
  message: '私信',
}
function typeLabel(code: string): string {
  return TYPE_LABELS[code] ?? code
}

// ==================== KPI ====================
const newUsersDelta = computed(() => lastDiff(registerTrend.value?.newUserList))
const matchesDelta = computed(() => lastDiff(match.value?.daily.matches))
const revenueDelta = computed(() => lastDiff(revenue.value?.trend.amountList))
const revenueSpark = computed<Array<number | null> | null>(() =>
  revenue.value ? revenue.value.trend.amountList.map((v) => v / 100) : null,
)

interface KpiItem {
  label: string
  value: number | null
  icon: Component
  iconColor: string
  iconBg: string
  delta?: number | null
  hint?: string
  formatter?: (n: number) => string
  spark?: Array<number | null> | null
  sparkColor?: string
  pulse?: boolean
}

const kpiList = computed<KpiItem[]>(() => [
  {
    label: '累计注册',
    value: overview.value?.totalUsers ?? null,
    icon: User,
    iconColor: 'var(--xfjy-primary)',
    iconBg: 'var(--xfjy-primary-50)',
    hint: '全部注册用户',
    spark: registerTrend.value?.newUserList ?? null,
    sparkColor: PALETTE[0],
  },
  {
    label: '今日新增',
    value: overview.value?.newUsers ?? null,
    icon: CirclePlus,
    iconColor: 'var(--xfjy-primary-violet)',
    iconBg: 'var(--xfjy-deep-50)',
    delta: newUsersDelta.value,
    spark: registerTrend.value?.newUserList ?? null,
    sparkColor: PALETTE[1],
  },
  {
    label: '今日活跃',
    value: online.value?.todayActive ?? null,
    icon: Histogram,
    iconColor: 'var(--xfjy-chart-3)',
    iconBg: 'var(--xfjy-chart-3-50)',
    hint: 'DAU · 日活埋点',
    spark: activity.value?.dauList ?? null,
    sparkColor: PALETTE[2],
  },
  {
    label: '当前在线',
    value: online.value?.onlineUsers ?? null,
    icon: Connection,
    iconColor: 'var(--xfjy-chart-5)',
    iconBg: 'var(--xfjy-chart-5-50)',
    hint: `今日峰值 ${formatCount(online.value?.peakToday)}`,
    pulse: true,
  },
  {
    label: '今日匹配',
    value: match.value?.today.matches ?? null,
    icon: Aim,
    iconColor: 'var(--xfjy-warning)',
    iconBg: 'var(--xfjy-warning-50)',
    delta: matchesDelta.value,
    spark: match.value?.daily.matches ?? null,
    sparkColor: PALETTE[3],
  },
  {
    label: '今日收入',
    value: revenue.value ? revenue.value.overview.todayAmount : null,
    icon: Wallet,
    iconColor: 'var(--xfjy-success)',
    iconBg: 'var(--xfjy-success-50)',
    delta: revenueDelta.value,
    formatter: formatFen,
    spark: revenueSpark.value,
    sparkColor: PALETTE[7],
  },
])

// ==================== 主趋势 ====================
const trendTab = ref<'register' | 'dau' | 'match' | 'content' | 'revenue'>('register')

const trendTabMeta = computed(() => {
  switch (trendTab.value) {
    case 'register':
      return { title: '新增注册趋势' }
    case 'dau':
      return { title: '日活趋势 DAU' }
    case 'match':
      return { title: '匹配互动趋势' }
    case 'content':
      return { title: '内容发布趋势' }
    case 'revenue':
      return { title: '付费趋势' }
  }
})

const mainTrendOption = computed<ECOption | null>(() => {
  if (trendTab.value === 'register' && registerTrend.value?.dates.length) {
    return areaLineOption(registerTrend.value.dates, [
      { name: '新增注册', data: registerTrend.value.newUserList, color: PALETTE[0] },
    ])
  }
  if (trendTab.value === 'dau' && activity.value?.dates.length) {
    const validPoints = activity.value.dauList.filter((v) => v !== null).length
    if (validPoints < 2) return null
    return areaLineOption(activity.value.dates, [
      { name: '日活 DAU', data: activity.value.dauList, color: PALETTE[2] },
    ])
  }
  if (trendTab.value === 'match' && match.value?.daily.dates.length) {
    return areaLineOption(match.value.daily.dates, [
      { name: '右滑喜欢', data: match.value.daily.likes, color: PALETTE[4] },
      { name: '互喜匹配', data: match.value.daily.matches, color: PALETTE[0] },
    ])
  }
  if (trendTab.value === 'content' && dimensionTrend.value?.dates.length) {
    return areaLineOption(dimensionTrend.value.dates, [
      { name: '动态发布', data: dimensionTrend.value.postList, color: PALETTE[2] },
      { name: '小视频发布', data: dimensionTrend.value.videoList, color: PALETTE[3] },
    ])
  }
  if (trendTab.value === 'revenue' && revenue.value?.trend.dates.length) {
    return revenueTrendOption(
      revenue.value.trend.dates,
      revenue.value.trend.orderList,
      revenue.value.trend.amountList.map((v) => v / 100),
    )
  }
  return null
})

const trendSub = computed<string>(() => {
  if (trendTab.value === 'register' && registerTrend.value) {
    const list = registerTrend.value.newUserList
    const total = sum(list)
    return `近 ${days.value} 天新增 ${formatCount(total)} 人 · 日均 ${formatCount(Math.round(total / list.length))}`
  }
  if (trendTab.value === 'dau') {
    return `今日活跃 ${formatCount(online.value?.todayActive ?? activity.value?.todayActive)} 人`
  }
  if (trendTab.value === 'match' && match.value) {
    return `近 ${days.value} 天右滑 ${formatCount(sum(match.value.daily.likes))} · 匹配 ${formatCount(
      sum(match.value.daily.matches),
    )}`
  }
  if (trendTab.value === 'content' && dimensionTrend.value) {
    return `近 ${days.value} 天动态 ${formatCount(sum(dimensionTrend.value.postList))} 条 · 小视频 ${formatCount(
      sum(dimensionTrend.value.videoList),
    )} 条`
  }
  if (trendTab.value === 'revenue' && revenue.value) {
    return `近 ${days.value} 天收入 ¥${formatYuan(sum(revenue.value.trend.amountList) / 100)}`
  }
  return ''
})

// ==================== 实时面板 ====================
const onlineDisplay = useCountUp(computed(() => online.value?.onlineUsers ?? null))

// ==================== 匹配漏斗 ====================
interface FunnelLevel {
  name: string
  value: number
  pct: number
  cls: string
}
const funnel = computed<{
  levels: FunnelLevel[]
  likeRate: number | null
  matchRate: number | null
} | null>(() => {
  const t = match.value?.today
  if (!t) return null
  // 以最大层（滑动数）为 100% 宽度基准；无滑动时全部 0 宽
  const base = t.swipes > 0 ? t.swipes : 1
  const widthOf = (v: number) => (v > 0 ? Math.max((v / base) * 100, 6) : 0)
  return {
    levels: [
      { name: '滑动浏览', value: t.swipes, pct: widthOf(t.swipes), cls: 'funnel-level__bar--blue' },
      { name: '右滑喜欢', value: t.likes, pct: widthOf(t.likes), cls: 'funnel-level__bar--violet' },
      { name: '互喜匹配', value: t.matches, pct: widthOf(t.matches), cls: 'funnel-level__bar--rose' },
    ],
    likeRate: t.swipes > 0 ? (t.likes / t.swipes) * 100 : null,
    matchRate: t.likes > 0 ? (t.matches / t.likes) * 100 : null,
  }
})

// ==================== 付费概览 ====================
const payTypeOption = computed<ECOption | null>(() => {
  if (!revenue.value) return null
  const p = revenue.value.payType
  if (p.alipay + p.wechat + p.sandbox === 0) return null
  const data: DonutItem[] = [
    { name: '支付宝', value: p.alipay, color: PALETTE[4] },
    { name: '微信', value: p.wechat, color: PALETTE[7] },
    { name: '沙箱', value: p.sandbox, color: NEUTRAL },
  ]
  return donutOption(data, '累计收入(元)', `¥${formatYuan(revenue.value.overview.totalAmount / 100)}`)
})

const arppuText = computed(() => {
  const v = revenue.value?.todayArppu
  if (v === null || v === undefined) return '—'
  return `¥${(v / 100).toFixed(2)}`
})

// ==================== 用户结构 ====================
const genderOption = computed<ECOption | null>(() => {
  const d = demographics.value
  if (!d || d.total === 0) return null
  const data: DonutItem[] = [{ name: '男', value: d.male, color: PALETTE[4] }]
  data.push({ name: '女', value: d.female, color: PALETTE[0] })
  if (d.genderUnknown > 0) data.push({ name: '未知', value: d.genderUnknown, color: NEUTRAL })
  return donutOption(data, '总用户', formatCount(d.total))
})

const ageOption = computed<ECOption | null>(() => {
  const d = demographics.value
  if (!d || d.ageCounts.every((v) => v === 0)) return null
  return horizontalBarOption(d.ageRanges, d.ageCounts, PALETTE[1])
})

const cityOption = computed<ECOption | null>(() => {
  const d = demographics.value
  if (!d || d.cities.length === 0) return null
  return horizontalBarOption(
    d.cities.map((c) => c.name),
    d.cities.map((c) => c.count),
    PALETTE[2],
  )
})

// ==================== 内容安全 ====================
const reviewPassRate = computed<number | null>(() => {
  const r = moderation.value?.review
  if (!r) return null
  const done = r.todayApproved + r.todayRejected
  return done > 0 ? Math.round((r.todayApproved / done) * 1000) / 10 : null
})

const reportTodayDone = computed(() => {
  const r = moderation.value?.report
  if (!r) return '—'
  return formatCount(r.todayResolved + r.todayDismissed)
})

const reviewOption = computed<ECOption | null>(() => {
  const d = moderation.value?.review.daily
  if (!d || d.dates.length === 0) return null
  return moderationTrendOption(
    d.dates,
    d.created,
    [
      { name: '通过', data: d.approved, color: PALETTE[2] },
      { name: '拒绝', data: d.rejected, color: PALETTE[5] },
    ],
    '新增送审',
  )
})

const reportOption = computed<ECOption | null>(() => {
  const d = moderation.value?.report.daily
  if (!d || d.dates.length === 0) return null
  return moderationTrendOption(
    d.dates,
    d.created,
    [
      { name: '已处理', data: d.resolved, color: PALETTE[2] },
      { name: '已驳回', data: d.dismissed, color: PALETTE[3] },
    ],
    '新增举报',
  )
})

interface TypeRow {
  name: string
  count: number
  pct: number
}
function toTypeRows(rows: Array<{ name: string; count: number }> | undefined): TypeRow[] {
  if (!rows || rows.length === 0) return []
  const max = Math.max(...rows.map((r) => r.count))
  return rows.map((r) => ({
    name: r.name,
    count: r.count,
    pct: max > 0 ? Math.round((r.count / max) * 100) : 0,
  }))
}
const reviewTypeRows = computed(() => toTypeRows(moderation.value?.review.pendingTypes))
const reportTypeRows = computed(() => toTypeRows(moderation.value?.report.targetTypes))

// ==================== 热门内容 ====================
const hotPosts = computed(() => hot.value?.hotPosts ?? [])
const hotVideos = computed(() => hot.value?.hotVideos ?? [])
</script>

<style scoped>
.dashboard {
  /* page-container 已提供 24px 内边距，这里只约束纵向节奏 */
  display: flex;
  flex-direction: column;
  gap: var(--xfjy-space-4);
}

.dash-row {
  margin-bottom: 0;
  row-gap: var(--xfjy-space-4);
}

/* ==================== 顶部控制条 ==================== */
.dash-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--xfjy-space-3);
  padding: var(--xfjy-space-3) var(--xfjy-space-4);
  background: var(--xfjy-bg-card);
  border: 1px solid var(--xfjy-border);
  border-radius: var(--xfjy-radius-lg);
  box-shadow: var(--xfjy-shadow-card);
}

.dash-toolbar__left,
.dash-toolbar__right {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--xfjy-space-3);
}

.dash-toolbar__title {
  font-size: var(--xfjy-font-size-md);
  font-weight: 600;
  color: var(--xfjy-text-primary);
}

.dash-toolbar__meta {
  font-size: var(--xfjy-font-size-xs);
  color: var(--xfjy-text-secondary);
  font-variant-numeric: tabular-nums;
}

.dash-toolbar__clock {
  font-size: var(--xfjy-font-size-sm);
  font-weight: 600;
  color: var(--xfjy-text-regular);
  font-variant-numeric: tabular-nums;
}

.dash-toolbar__switch {
  display: inline-flex;
  align-items: center;
  gap: var(--xfjy-space-2);
}

.dash-toolbar__refresh-icon {
  margin-right: var(--xfjy-space-1);
}

/* 实时脉冲标记 */
.live-badge {
  display: inline-flex;
  align-items: center;
  gap: var(--xfjy-space-1);
  font-size: var(--xfjy-font-size-xs);
  font-weight: 500;
  color: var(--xfjy-success);
}

.live-badge__dot {
  position: relative;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--xfjy-success);
}

.live-badge__dot--sm {
  width: 6px;
  height: 6px;
}

/* 波纹用子元素缩放+淡出，避免 rgba 字面量（§7.2） */
.live-badge__wave {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: var(--xfjy-success);
  animation: live-wave 1.6s ease-out infinite;
}

@keyframes live-wave {
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

/* 5 个趋势 tab 在窄屏允许折行，避免溢出卡片 */
.trend-tabs {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  row-gap: var(--xfjy-space-1);
}

.panel-side-meta {
  font-size: var(--xfjy-font-size-xs);
  color: var(--xfjy-text-secondary);
  font-variant-numeric: tabular-nums;
}

.chart-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 260px;
}

.chart-empty--compact {
  min-height: 180px;
}

.panel-note {
  margin-top: var(--xfjy-space-3);
  font-size: var(--xfjy-font-size-xs);
  line-height: 1.6;
  color: var(--xfjy-text-secondary);
}

/* ==================== 实时动态面板 ==================== */
.rt-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--xfjy-space-4);
  padding: var(--xfjy-space-4);
  background: var(--xfjy-bg-hover);
  border-radius: var(--xfjy-radius-lg);
}

.rt-hero__label {
  display: flex;
  align-items: center;
  gap: var(--xfjy-space-2);
  font-size: var(--xfjy-font-size-xs);
  color: var(--xfjy-text-secondary);
}

.rt-hero__value {
  margin-top: var(--xfjy-space-1);
  font-size: var(--xfjy-font-size-xl);
  font-weight: 700;
  line-height: 1.1;
  color: var(--xfjy-text-primary);
  font-variant-numeric: tabular-nums;
}

.rt-hero__side {
  display: flex;
  flex-direction: column;
  gap: var(--xfjy-space-2);
  flex-shrink: 0;
}

.rt-hero__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--xfjy-space-4);
  font-size: var(--xfjy-font-size-xs);
  color: var(--xfjy-text-secondary);
}

.rt-hero__item-value {
  font-size: var(--xfjy-font-size-sm);
  font-weight: 600;
  color: var(--xfjy-text-primary);
  font-variant-numeric: tabular-nums;
}

.rt-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--xfjy-space-2);
  margin-top: var(--xfjy-space-3);
}

.rt-item {
  display: flex;
  align-items: center;
  gap: var(--xfjy-space-2);
  padding: var(--xfjy-space-2) var(--xfjy-space-3);
  background: var(--xfjy-bg-hover);
  border-radius: var(--xfjy-radius-md);
  min-width: 0;
}

.rt-item__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  border-radius: var(--xfjy-radius-sm);
  font-size: var(--xfjy-font-size-md);
}

.rt-item__icon--rose {
  color: var(--xfjy-primary);
  background: var(--xfjy-primary-50);
}

.rt-item__icon--violet {
  color: var(--xfjy-primary-violet);
  background: var(--xfjy-deep-50);
}

.rt-item__icon--amber {
  color: var(--xfjy-warning);
  background: var(--xfjy-warning-50);
}

.rt-item__body {
  min-width: 0;
}

.rt-item__value {
  font-size: var(--xfjy-font-size-md);
  font-weight: 700;
  line-height: 1.15;
  color: var(--xfjy-text-primary);
  font-variant-numeric: tabular-nums;
}

.rt-item__label {
  margin-top: 2px;
  font-size: var(--xfjy-font-size-xs);
  color: var(--xfjy-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ==================== 匹配漏斗 ==================== */
.funnel {
  display: flex;
  flex-direction: column;
}

.funnel-level {
  margin-bottom: var(--xfjy-space-3);
}

.funnel-level__track {
  height: 28px;
  background: var(--xfjy-bg-hover);
  border-radius: var(--xfjy-radius-md);
  overflow: hidden;
}

.funnel-level__bar {
  height: 100%;
  border-radius: var(--xfjy-radius-md);
  transition: width 0.5s ease;
}

.funnel-level__bar--blue {
  background: var(--xfjy-chart-5);
}

.funnel-level__bar--violet {
  background: var(--xfjy-chart-2);
}

.funnel-level__bar--rose {
  background: var(--xfjy-chart-1);
}

.funnel-level__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: var(--xfjy-space-1);
  font-size: var(--xfjy-font-size-xs);
}

.funnel-level__name {
  color: var(--xfjy-text-secondary);
}

.funnel-level__count {
  font-weight: 600;
  color: var(--xfjy-text-primary);
  font-variant-numeric: tabular-nums;
}

.rate-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--xfjy-space-2);
  margin-top: var(--xfjy-space-1);
}

.rate-item {
  display: flex;
  flex-direction: column;
  gap: var(--xfjy-space-1);
  padding: var(--xfjy-space-3);
  background: var(--xfjy-bg-hover);
  border-radius: var(--xfjy-radius-md);
}

.rate-item__label {
  font-size: var(--xfjy-font-size-xs);
  color: var(--xfjy-text-secondary);
}

.rate-item__value {
  font-size: var(--xfjy-font-size-md);
  font-weight: 700;
  color: var(--xfjy-text-primary);
  font-variant-numeric: tabular-nums;
}

.rate-item__value--rose {
  color: var(--xfjy-primary);
}

/* ==================== 留存 ==================== */
.retention-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--xfjy-space-2);
}

.retention-item {
  display: flex;
  flex-direction: column;
  gap: var(--xfjy-space-1);
  padding: var(--xfjy-space-3);
  background: var(--xfjy-bg-hover);
  border-radius: var(--xfjy-radius-md);
}

.retention-item__label {
  font-size: var(--xfjy-font-size-xs);
  color: var(--xfjy-text-secondary);
}

.retention-item__value {
  font-size: var(--xfjy-font-size-md);
  font-weight: 700;
  color: var(--xfjy-text-primary);
  font-variant-numeric: tabular-nums;
}

.retention-item__sub {
  font-size: var(--xfjy-font-size-xs);
  color: var(--xfjy-text-secondary);
  font-variant-numeric: tabular-nums;
}

/* ==================== 付费概览 ==================== */
.pay-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--xfjy-space-2);
  margin-top: var(--xfjy-space-3);
}

/* ==================== 迷你统计（付费 / 内容安全通用） ==================== */
.mini-stat {
  display: flex;
  flex-direction: column;
  gap: var(--xfjy-space-1);
  padding: var(--xfjy-space-2) var(--xfjy-space-3);
  background: var(--xfjy-bg-hover);
  border-radius: var(--xfjy-radius-md);
  min-width: 0;
}

.mini-stat__label {
  font-size: var(--xfjy-font-size-xs);
  color: var(--xfjy-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.mini-stat__value {
  font-size: var(--xfjy-font-size-md);
  font-weight: 700;
  color: var(--xfjy-text-primary);
  font-variant-numeric: tabular-nums;
}

.mini-stat__value--green {
  color: var(--xfjy-success);
}

.mini-stat__value--red {
  color: var(--xfjy-danger);
}

.mini-stat__value--amber {
  color: var(--xfjy-warning);
}

/* ==================== 内容安全 ==================== */
.mod-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--xfjy-space-2);
  margin-bottom: var(--xfjy-space-3);
}

.mod-body {
  display: flex;
  gap: var(--xfjy-space-4);
}

.mod-body__chart {
  flex: 1;
  min-width: 0;
}

.mod-body__types {
  flex-shrink: 0;
  width: 200px;
}

.type-title {
  font-size: var(--xfjy-font-size-xs);
  font-weight: 600;
  color: var(--xfjy-text-secondary);
  margin-bottom: var(--xfjy-space-3);
}

.type-row {
  margin-bottom: var(--xfjy-space-3);
}

.type-row__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--xfjy-space-2);
  margin-bottom: var(--xfjy-space-1);
  font-size: var(--xfjy-font-size-xs);
}

.type-row__name {
  color: var(--xfjy-text-regular);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.type-row__count {
  flex-shrink: 0;
  font-weight: 600;
  color: var(--xfjy-text-primary);
  font-variant-numeric: tabular-nums;
}

.type-row__track {
  height: 6px;
  border-radius: var(--xfjy-radius-sm);
  background: var(--xfjy-bg-hover);
  overflow: hidden;
}

.type-row__fill {
  height: 100%;
  border-radius: var(--xfjy-radius-sm);
  background: var(--xfjy-primary);
  transition: width 0.4s ease;
}

.type-empty {
  font-size: var(--xfjy-font-size-xs);
  color: var(--xfjy-text-disabled);
}

/* ==================== 热门内容 ==================== */
.hot-list {
  display: flex;
  flex-direction: column;
  gap: var(--xfjy-space-1);
}

.hot-item {
  display: flex;
  align-items: center;
  gap: var(--xfjy-space-2);
  padding: var(--xfjy-space-2) 0;
  min-width: 0;
}

.hot-item__rank {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: var(--xfjy-font-size-xs);
  font-weight: 600;
  color: var(--xfjy-text-secondary);
  background: var(--xfjy-bg-hover);
}

.hot-item__rank--top {
  color: var(--xfjy-text-inverse);
  background: var(--xfjy-primary);
}

.hot-item__text {
  flex: 1;
  min-width: 0;
  font-size: var(--xfjy-font-size-sm);
  color: var(--xfjy-text-regular);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.hot-item__stats {
  flex-shrink: 0;
  display: flex;
  gap: var(--xfjy-space-2);
}

.hot-item__stat {
  font-size: var(--xfjy-font-size-xs);
  color: var(--xfjy-text-secondary);
  font-variant-numeric: tabular-nums;
}

/* ==================== 窄屏适配 ==================== */
@media (max-width: 767px) {
  .trend-tabs {
    justify-content: flex-start;
  }

  .mod-body {
    flex-direction: column;
  }

  .mod-body__types {
    width: 100%;
  }

  .mod-stats {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
