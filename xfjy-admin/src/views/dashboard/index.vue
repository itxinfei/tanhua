<template>
  <div
    v-loading="loading && !!lastUpdated"
    class="page-container dashboard"
    element-loading-text="看板数据刷新中"
  >
    <!-- ============ 1. 顶部控制条 ============ -->
    <div class="dash-toolbar">
      <div class="dash-toolbar__left">
        <span class="dash-toolbar__title">运营数据看板</span>
        <span class="live-badge">
          <span class="live-badge__dot"><span class="live-badge__wave" /></span>
          实时
        </span>
        <span
          v-if="autoRefresh"
          class="dash-toolbar__meta"
        >{{ countdown }}s 后刷新</span>
        <span
          v-if="lastUpdated"
          class="dash-toolbar__meta"
        >
          更新于 {{ formatClock(lastUpdated) }}
        </span>
      </div>
      <div class="dash-toolbar__right">
        <el-radio-group
          v-model="days"
          size="small"
        >
          <el-radio-button :value="1">
            24 小时
          </el-radio-button>
          <el-radio-button :value="7">
            近 7 天
          </el-radio-button>
          <el-radio-button :value="15">
            近 15 天
          </el-radio-button>
          <el-radio-button :value="30">
            近 30 天
          </el-radio-button>
        </el-radio-group>
        <span class="dash-toolbar__switch">
          <span class="dash-toolbar__meta">自动刷新</span>
          <el-switch v-model="autoRefresh" />
        </span>
        <el-button
          :loading="refreshing"
          @click="refreshAll"
        >
          <el-icon class="dash-toolbar__refresh-icon">
            <Refresh />
          </el-icon>
          刷新
        </el-button>
      </div>
    </div>

    <!-- ============ 首屏骨架：首次加载（无数据）时用结构化骨架替代 spinner ============ -->
    <div
      v-if="loading && !lastUpdated"
      class="dash-skeleton"
      aria-busy="true"
      aria-label="看板数据加载中"
    >
      <div class="dash-skeleton__kpi-row">
        <div
          v-for="i in 4"
          :key="i"
          class="dash-skeleton__kpi"
        />
      </div>
      <div class="dash-skeleton__panel-row">
        <div class="dash-skeleton__panel" />
        <div class="dash-skeleton__panel" />
      </div>
    </div>

    <template v-else>
      <!-- ============ 2. 核心指标 KPI（首屏黄金区，4 卡一行） ============ -->
      <el-row
        :gutter="16"
        class="dash-row"
      >
        <el-col
          v-for="kpi in kpiList"
          :key="kpi.label"
          :xs="24"
          :sm="12"
          :lg="6"
          :xl="6"
        >
          <KpiCard
            :label="kpi.label"
            :value="kpi.value"
            :icon="kpi.icon"
            :live="kpi.live"
            :delta="kpi.delta"
            :hint="kpi.hint"
            :formatter="kpi.formatter"
            :to="kpi.to"
          />
        </el-col>
      </el-row>

      <!-- ============ 3. 待办与异常（行动入口） ============ -->
      <DashPanel
        title="待办与异常"
        sub="点击数字直达处理页面"
        class="todo-panel"
      >
        <div
          v-if="hasTodo"
          class="todo-grid"
        >
          <div
            v-for="action in todoActions"
            v-show="action.count > 0"
            :key="action.key"
            class="todo-item"
            :class="`todo-item--${action.level}`"
            role="link"
            tabindex="0"
            @click="router.push(action.to)"
            @keydown.enter="router.push(action.to)"
          >
            <span class="todo-item__icon">
              <el-icon :size="18"><Document v-if="action.key === 'review'" /><Warning v-else /></el-icon>
            </span>
            <div class="todo-item__body">
              <span class="todo-item__count">{{ formatCount(action.count) }}</span>
              <span class="todo-item__label">{{ action.label }}</span>
            </div>
            <span class="todo-item__cta">
              去处理
              <el-icon :size="12"><ArrowRight /></el-icon>
            </span>
          </div>
        </div>
        <div
          v-else
          class="todo-all-clear"
        >
          <el-icon :size="16">
            <CircleCheck />
          </el-icon>
          暂无积压，审核与举报均已处理完毕
        </div>
      </DashPanel>

      <!-- ============ 4. 主趋势 + 今日匹配漏斗 ============ -->
      <el-row
        :gutter="16"
        class="dash-row"
      >
        <el-col
          :xs="24"
          :md="16"
        >
          <DashPanel
            :title="trendTabMeta.title"
            :sub="trendSub"
          >
            <template #extra>
              <el-radio-group
                v-model="trendTab"
                size="small"
                class="trend-tabs"
              >
                <el-radio-button value="register">
                  新增注册
                </el-radio-button>
                <el-radio-button value="dau">
                  日活 DAU
                </el-radio-button>
                <el-radio-button value="match">
                  匹配互动
                </el-radio-button>
                <el-radio-button value="content">
                  内容发布
                </el-radio-button>
                <el-radio-button value="revenue">
                  付费趋势
                </el-radio-button>
              </el-radio-group>
            </template>
            <BaseChart
              v-if="mainTrendOption"
              :option="mainTrendOption"
              height="220px"
            />
            <div
              v-else
              class="chart-empty"
            >
              <el-empty
                description="数据积累中"
                :image-size="72"
              />
            </div>
          </DashPanel>
        </el-col>

        <el-col
          :xs="24"
          :md="8"
        >
          <DashPanel
            title="今日匹配漏斗"
            sub="滑动 → 右滑 → 互喜匹配"
          >
            <template #extra>
              <span class="panel-side-meta">{{ formatCount(funnel?.levels[0].value) }} 次滑动</span>
            </template>
            <div
              v-if="funnel"
              class="funnel"
            >
              <div
                v-for="level in funnel.levels"
                :key="level.name"
                class="funnel-level"
              >
                <div class="funnel-level__bar-wrap">
                  <div
                    class="funnel-level__bar"
                    :class="[`funnel-level__bar--${level.id}`, { 'funnel-level__bar--zero': level.zero }]"
                    :style="level.zero ? undefined : { width: level.pct + '%' }"
                  >
                    {{ formatCount(level.value) }}
                  </div>
                </div>
                <div class="funnel-level__meta">
                  <span class="funnel-level__name">{{ level.name }}</span>
                  <span class="funnel-level__rate">{{ level.rateText }}</span>
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
            <div
              v-else
              class="chart-empty"
            >
              <el-empty
                description="今日暂无滑动数据"
                :image-size="72"
              />
            </div>
          </DashPanel>
        </el-col>
      </el-row>


      <!-- ============ 5. 留存 / 付费概览 ============ -->
      <el-row
        :gutter="16"
        class="dash-row"
      >
        <!-- 留存 -->
        <el-col
          :xs="24"
          :md="isOperator ? 24 : 12"
        >
          <DashPanel
            title="用户留存"
            sub="各注册批次今日仍活跃占比"
          >
            <div class="retention-grid">
              <div
                v-for="item in activity?.retention ?? []"
                :key="item.days"
                class="retention-item"
              >
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
            <p class="panel-note">
              留存基于日活埋点计算，部署当日起开始积累。
            </p>
          </DashPanel>
        </el-col>

        <!-- 付费概览（OPERATOR 不可见） -->
        <el-col
          v-if="!isOperator"
          :xs="24"
          :md="12"
        >
          <DashPanel
            title="付费概览"
            sub="累计支付订单结构"
          >
            <BaseChart
              v-if="payTypeOption"
              :option="payTypeOption"
              height="180px"
            />
            <div
              v-else
              class="chart-empty chart-empty--compact"
            >
              <el-empty
                description="暂无支付数据"
                :image-size="60"
              />
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

      <!-- ============ 6. 内容安全：审核 / 举报 ============ -->
      <el-row
        :gutter="16"
        class="dash-row"
      >
        <el-col
          v-if="!isOperator"
          :xs="24"
          :md="12"
        >
          <DashPanel
            title="内容审核"
            :sub="`近 ${days} 天送审与处理量`"
          >
            <div class="mod-stats">
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
                <BaseChart
                  v-if="reviewOption"
                  :option="reviewOption"
                  height="220px"
                />
                <div
                  v-else
                  class="chart-empty chart-empty--compact"
                >
                  <el-empty
                    description="暂无审核数据"
                    :image-size="60"
                  />
                </div>
              </div>
              <div class="mod-body__types">
                <div class="type-title">
                  待审核类型
                </div>
                <div
                  v-if="reviewTypeRows.length"
                  class="type-list"
                >
                  <div
                    v-for="row in reviewTypeRows"
                    :key="row.name"
                    class="type-row"
                  >
                    <div class="type-row__head">
                      <span class="type-row__name">{{ typeLabel(row.name) }}</span>
                      <span class="type-row__count">{{ row.count }}</span>
                    </div>
                    <div class="type-row__track">
                      <div
                        class="type-row__fill"
                        :style="{ width: row.pct + '%' }"
                      />
                    </div>
                  </div>
                </div>
                <div
                  v-else
                  class="type-empty"
                >
                  暂无积压
                </div>
              </div>
            </div>
          </DashPanel>
        </el-col>

        <el-col
          :xs="24"
          :md="isOperator ? 24 : 12"
        >
          <DashPanel
            title="用户举报"
            :sub="`近 ${days} 天举报与处理量`"
          >
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
                <BaseChart
                  v-if="reportOption"
                  :option="reportOption"
                  height="220px"
                />
                <div
                  v-else
                  class="chart-empty chart-empty--compact"
                >
                  <el-empty
                    description="暂无举报数据"
                    :image-size="60"
                  />
                </div>
              </div>
              <div class="mod-body__types">
                <div class="type-title">
                  举报对象分布
                </div>
                <div
                  v-if="reportTypeRows.length"
                  class="type-list"
                >
                  <div
                    v-for="row in reportTypeRows"
                    :key="row.name"
                    class="type-row"
                  >
                    <div class="type-row__head">
                      <span class="type-row__name">{{ typeLabel(row.name) }}</span>
                      <span class="type-row__count">{{ row.count }}</span>
                    </div>
                    <div class="type-row__track">
                      <div
                        class="type-row__fill"
                        :style="{ width: row.pct + '%' }"
                      />
                    </div>
                  </div>
                </div>
                <div
                  v-else
                  class="type-empty"
                >
                  暂无举报
                </div>
              </div>
            </div>
          </DashPanel>
        </el-col>
      </el-row>

      <!-- ============ 7. 折叠分析区：用户画像 / 热门内容 ============ -->
      <el-collapse
        v-model="analysisExpanded"
        class="analysis-collapse"
      >
        <el-collapse-item name="demographics">
          <template #title>
            <span class="analysis-collapse__title">用户画像</span>
            <span class="analysis-collapse__sub">性别 / 年龄 / 地区分布</span>
          </template>
          <el-row
            :gutter="16"
            class="dash-row"
          >
            <el-col
              :xs="24"
              :md="8"
            >
              <DashPanel
                title="性别分布"
                sub="全部注册用户"
              >
                <BaseChart
                  v-if="genderOption"
                  :option="genderOption"
                  height="240px"
                />
                <div
                  v-else
                  class="chart-empty"
                >
                  <el-empty
                    description="暂无用户数据"
                    :image-size="72"
                  />
                </div>
              </DashPanel>
            </el-col>
            <el-col
              :xs="24"
              :md="8"
            >
              <DashPanel
                title="年龄结构"
                sub="按注册资料年龄分桶"
              >
                <BaseChart
                  v-if="ageOption"
                  :option="ageOption"
                  height="240px"
                />
                <div
                  v-else
                  class="chart-empty"
                >
                  <el-empty
                    description="暂无年龄数据"
                    :image-size="72"
                  />
                </div>
              </DashPanel>
            </el-col>
            <el-col
              :xs="24"
              :md="8"
            >
              <DashPanel
                title="地区分布 TOP10"
                sub="注册资料中的城市"
              >
                <BaseChart
                  v-if="cityOption"
                  :option="cityOption"
                  height="240px"
                />
                <div
                  v-else
                  class="chart-empty"
                >
                  <el-empty
                    description="暂无城市数据"
                    :image-size="72"
                  />
                </div>
              </DashPanel>
            </el-col>
          </el-row>
        </el-collapse-item>
        <el-collapse-item name="hot">
          <template #title>
            <span class="analysis-collapse__title">热门内容</span>
            <span class="analysis-collapse__sub">动态 / 视频 TOP5</span>
          </template>
          <el-row
            :gutter="16"
            class="dash-row"
          >
            <el-col
              :xs="24"
              :md="12"
            >
              <DashPanel
                title="热门动态 TOP5"
                sub="按点赞数排序"
              >
                <div
                  v-if="hotPosts.length"
                  class="hot-list"
                >
                  <div
                    v-for="(post, idx) in hotPosts"
                    :key="post.id"
                    class="hot-item"
                  >
                    <span
                      class="hot-item__rank"
                      :class="{ 'hot-item__rank--top': idx < 3 }"
                    >{{ idx + 1 }}</span>
                    <span class="hot-item__text">{{ post.text || '（无文案）' }}</span>
                    <span class="hot-item__stats">
                      <span class="hot-item__stat">赞 {{ post.likeCount }}</span>
                      <span class="hot-item__stat">评 {{ post.commentCount }}</span>
                    </span>
                  </div>
                </div>
                <div
                  v-else
                  class="chart-empty chart-empty--compact"
                >
                  <el-empty
                    description="暂无热门动态"
                    :image-size="60"
                  />
                </div>
              </DashPanel>
            </el-col>
            <el-col
              :xs="24"
              :md="12"
            >
              <DashPanel
                title="热门视频 TOP5"
                sub="近期小视频"
              >
                <div
                  v-if="hotVideos.length"
                  class="hot-list"
                >
                  <div
                    v-for="(video, idx) in hotVideos"
                    :key="video.videoId"
                    class="hot-item"
                  >
                    <span
                      class="hot-item__rank"
                      :class="{ 'hot-item__rank--top': idx < 3 }"
                    >{{ idx + 1 }}</span>
                    <span class="hot-item__text">{{ video.text || '（无文案）' }}</span>
                  </div>
                </div>
                <div
                  v-else
                  class="chart-empty chart-empty--compact"
                >
                  <el-empty
                    description="暂无热门视频"
                    :image-size="60"
                  />
                </div>
              </DashPanel>
            </el-col>
          </el-row>
        </el-collapse-item>
      </el-collapse>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, type Component } from 'vue'
import * as echarts from 'echarts'
import {
  CirclePlus,
  Histogram,
  Connection,
  Wallet,
  Refresh,
  Document,
  Warning,
  ArrowRight,
  CircleCheck,
} from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
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
import { useAuthStore } from '@/stores/auth'

const router = useRouter()

/** 角色裁剪：OPERATOR 不可见付费概览与内容审核（对应路由已限管理员） */
const authStore = useAuthStore()
const isOperator = computed(() => authStore.adminInfo?.role === 'OPERATOR')

/** 折叠分析区展开项（默认全部收起，降低首屏信息噪音） */
const analysisExpanded = ref<string[]>([])

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
  overview,
  registerTrend,
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
const revenueDelta = computed(() => lastDiff(revenue.value?.trend.amountList))

interface KpiItem {
  label: string
  value: number | null
  icon: Component
  /** 实时在线卡图标走品牌玫红，其余卡统一中性灰 */
  live?: boolean
  delta?: number | null
  hint?: string
  formatter?: (n: number) => string
  /** 点击跳转路由地址；不传则为纯展示卡 */
  to?: string
}

const kpiList = computed<KpiItem[]>(() => [
  {
    label: '今日收入',
    value: revenue.value ? revenue.value.overview.todayAmount : null,
    icon: Wallet,
    delta: revenueDelta.value,
    formatter: formatFen,
    to: '/commercial/pay',
  },
  {
    label: '今日新增',
    value: overview.value?.newUsers ?? null,
    icon: CirclePlus,
    delta: newUsersDelta.value,
    hint: `累计注册 ${formatCount(overview.value?.totalUsers)}`,
    to: '/user/list',
  },
  {
    label: '今日活跃',
    value: online.value?.todayActive ?? null,
    icon: Histogram,
    hint: 'DAU · 日活埋点',
  },
  {
    label: '当前在线',
    value: online.value?.onlineUsers ?? null,
    icon: Connection,
    live: true,
    hint: `今日峰值 ${formatCount(online.value?.peakToday)}`,
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
    default:
      return { title: '趋势分析' }
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

// ==================== 待办与异常（行动入口） ====================
interface TodoAction {
  key: string
  label: string
  count: number
  to: string
  /** danger=需立即处理（红） / warning=有积压（琥珀） / ok=无积压（绿） */
  level: 'danger' | 'warning' | 'ok'
}

const todoActions = computed<TodoAction[]>(() => {
  const pendingReview = moderation.value?.review.pending ?? 0
  const pendingReport = moderation.value?.report.pending ?? 0
  const processingReport = moderation.value?.report.processing ?? 0
  const actions: TodoAction[] = [
    {
      key: 'review',
      label: '待审核内容',
      count: pendingReview,
      to: '/audit/review',
      level: pendingReview > 0 ? 'warning' : 'ok',
    },
    {
      key: 'report',
      label: '待处理举报',
      count: pendingReport,
      to: '/audit/report',
      level: pendingReport > 0 ? 'danger' : 'ok',
    },
    {
      key: 'processing',
      label: '处理中举报',
      count: processingReport,
      to: '/audit/report',
      level: processingReport > 0 ? 'warning' : 'ok',
    },
  ]
  // OPERATOR 无内容审核路由权限，不展示审核待办
  return isOperator.value ? actions.filter((a) => a.key !== 'review') : actions
})

const hasTodo = computed(() => todoActions.value.some((a) => a.count > 0))

// ==================== 匹配漏斗 ====================
interface FunnelLevel {
  name: string
  value: number
  /** 条宽度百分比；zero 层不绑定宽度，走 CSS 固定空态 */
  pct: number
  /** 层序号，对应 CSS 梯形斜切档（1 最宽、3 最窄） */
  id: 1 | 2 | 3
  /** 0 值空态：斜纹灰条 */
  zero: boolean
  /** 右侧转化率文案 */
  rateText: string
}
/** 非首层梯形宽度下限，保证小数据下漏斗形态不塌缩 */
const FUNNEL_MIN_WIDTH = 40
const funnel = computed<{
  levels: FunnelLevel[]
  likeRate: number | null
  matchRate: number | null
} | null>(() => {
  const t = match.value?.today
  if (!t) return null
  const likeRate = t.swipes > 0 ? (t.likes / t.swipes) * 100 : null
  const matchRate = t.likes > 0 ? (t.matches / t.likes) * 100 : null
  // 每层宽度相对上一层（与率口径一致），设下限保持收窄梯形视觉
  const widthOf = (v: number, base: number) =>
    v > 0 && base > 0 ? Math.max((v / base) * 100, FUNNEL_MIN_WIDTH) : 0
  return {
    levels: [
      {
        name: '滑动浏览', value: t.swipes, pct: 100, id: 1, zero: t.swipes === 0,
        rateText: '占比 100%',
      },
      {
        name: '右滑喜欢', value: t.likes, pct: widthOf(t.likes, t.swipes), id: 2, zero: t.likes === 0,
        rateText: `喜欢率 ${ratePercent(likeRate)}`,
      },
      {
        name: '互喜匹配', value: t.matches, pct: widthOf(t.matches, t.likes), id: 3, zero: t.matches === 0,
        rateText: `匹配率 ${ratePercent(matchRate)}`,
      },
    ],
    likeRate,
    matchRate,
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
  /* page-container 已提供 24px 内边距，这里只约束纵向节奏；
     区块间距 16px 必须大于卡内间距，避免视觉倒挂 */
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
  gap: var(--xfjy-space-2);
  padding: var(--xfjy-space-2) var(--xfjy-space-3);
  background: var(--xfjy-bg-card);
  border: 1px solid var(--xfjy-border);
  border-radius: var(--xfjy-radius-lg);
}

.dash-toolbar__left,
.dash-toolbar__right {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--xfjy-space-3);
}

.dash-toolbar__title {
  /* H1 与面板标题（16px）拉开层级 */
  font-size: var(--xfjy-font-size-lg);
  font-weight: 700;
  color: var(--xfjy-text-primary);
}

.dash-toolbar__meta {
  font-size: var(--xfjy-font-size-xs);
  color: var(--xfjy-text-secondary);
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
  color: var(--xfjy-success-deep);
}

.live-badge__dot {
  position: relative;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  /* 信息图形需过 WCAG 1.4.11 非文本 3:1，旧 success 仅 2.46:1，走 deep 4.79:1 */
  background: var(--xfjy-success-deep);
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
  /* 与 live-badge__dot 同色，同步走 deep 档 */
  background: var(--xfjy-success-deep);
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
  min-height: 200px;
}

.chart-empty--compact {
  min-height: 120px;
}

.panel-note {
  margin-top: var(--xfjy-space-2);
  font-size: var(--xfjy-font-size-xs);
  line-height: 1.6;
  color: var(--xfjy-text-secondary);
}

/* ==================== 待办与异常（行动入口） ==================== */
.todo-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: var(--xfjy-space-2);
}

.todo-item {
  display: flex;
  align-items: center;
  gap: var(--xfjy-space-2);
  padding: var(--xfjy-space-2) var(--xfjy-space-3);
  background: var(--xfjy-bg-hover);
  border-radius: var(--xfjy-radius-md);
  cursor: pointer;
  transition: filter 0.2s ease;
}

.todo-item:hover,
.todo-item:focus-visible {
  filter: brightness(0.96);
  outline: none;
}

.todo-item--warning {
  background: var(--xfjy-warning-50);
}

.todo-item--danger {
  background: var(--xfjy-danger-50);
}

.todo-item--ok {
  background: var(--xfjy-success-50);
}

.todo-item__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  border-radius: var(--xfjy-radius-md);
  background: var(--xfjy-bg-card);
}

.todo-item--warning .todo-item__icon {
  color: var(--xfjy-warning-deep);
}

.todo-item--danger .todo-item__icon {
  color: var(--xfjy-danger-deep);
}

.todo-item--ok .todo-item__icon {
  color: var(--xfjy-success-deep);
}

.todo-item__body {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
}

.todo-item__count {
  font-size: var(--xfjy-font-size-lg);
  font-weight: 700;
  line-height: 1.1;
  color: var(--xfjy-text-primary);
  font-variant-numeric: tabular-nums;
}

.todo-item__label {
  margin-top: 2px;
  font-size: var(--xfjy-font-size-xs);
  color: var(--xfjy-text-secondary);
}

.todo-item__cta {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
  font-size: var(--xfjy-font-size-xs);
  font-weight: 500;
  color: var(--xfjy-primary-dark);
  opacity: 0;
  transition: opacity 0.2s ease;
}

.todo-item:hover .todo-item__cta,
.todo-item:focus-visible .todo-item__cta {
  opacity: 1;
}

.todo-all-clear {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--xfjy-space-2);
  padding: var(--xfjy-space-2) var(--xfjy-space-3);
  font-size: var(--xfjy-font-size-sm);
  color: var(--xfjy-success-deep);
  background: var(--xfjy-success-50);
  border-radius: var(--xfjy-radius-lg);
}


/* ==================== 匹配漏斗（居中收窄梯形，单色三档表达衰减） ==================== */
.funnel {
  display: flex;
  flex-direction: column;
}

.funnel-level {
  margin-bottom: var(--xfjy-space-2);
}

.funnel-level:last-child {
  margin-bottom: 0;
}

.funnel-level__bar-wrap {
  display: flex;
  justify-content: center;
}

.funnel-level__bar {
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--xfjy-radius-md);
  font-size: var(--xfjy-font-size-sm);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  transition: width 0.5s ease;
}

/* 梯形斜切档：越下层收口越明显，clip-path 零依赖 */
.funnel-level__bar--1 {
  background: linear-gradient(135deg, var(--xfjy-primary), var(--xfjy-primary-soft));
  color: var(--xfjy-text-inverse);
  clip-path: polygon(0 0, 100% 0, 94% 100%, 6% 100%);
}

.funnel-level__bar--2 {
  /* 浅底用深玫红字，保证对比度达标（白字在 #ff8fb1 上仅 2.4:1） */
  background: linear-gradient(135deg, var(--xfjy-primary-200), var(--xfjy-primary-soft));
  color: var(--xfjy-primary-darker);
  clip-path: polygon(6% 0, 94% 0, 90% 100%, 10% 100%);
}

.funnel-level__bar--3 {
  background: var(--xfjy-border);
  color: var(--xfjy-text-secondary);
  clip-path: polygon(10% 0, 90% 0, 86% 100%, 14% 100%);
}

/* 0 值空态：斜纹灰条，固定宽度不参与数据比例；zero 时模板未绑内联 width，普通声明即生效，无需 !important */
.funnel-level__bar--zero {
  width: 52%;
  background: repeating-linear-gradient(
    45deg,
    var(--xfjy-border-light) 0,
    var(--xfjy-border-light) 8px,
    var(--xfjy-bg-hover) 8px,
    var(--xfjy-bg-hover) 16px
  );
  /* 条内数字是真实文本，secondary 保证可读，不用 disabled 灰 */
  color: var(--xfjy-text-secondary);
}

.funnel-level__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: var(--xfjy-space-1);
  padding: 0 var(--xfjy-space-1);
  font-size: var(--xfjy-font-size-sm);
}

.funnel-level__name {
  color: var(--xfjy-text-secondary);
}

.funnel-level__rate {
  font-weight: 600;
  color: var(--xfjy-text-primary);
  font-variant-numeric: tabular-nums;
}

.rate-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--xfjy-space-3);
  margin-top: var(--xfjy-space-4);
}

.rate-item {
  display: flex;
  flex-direction: column;
  gap: var(--xfjy-space-1);
  padding: var(--xfjy-space-3) var(--xfjy-space-4);
  border: 1px solid var(--xfjy-border);
  border-radius: var(--xfjy-radius-md);
}

.rate-item__label {
  font-size: var(--xfjy-font-size-sm);
  /* 辅助标签非禁用态，走 secondary 4.6:1；disabled 灰仅 1.9:1 不达标 */
  color: var(--xfjy-text-secondary);
}

.rate-item__value {
  font-size: var(--xfjy-font-size-md);
  font-weight: 700;
  color: var(--xfjy-text-primary);
  font-variant-numeric: tabular-nums;
}

.rate-item__value--rose {
  color: var(--xfjy-primary-dark);
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
  padding: var(--xfjy-space-1) var(--xfjy-space-2);
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
  color: var(--xfjy-success-deep);
}

.mini-stat__value--red {
  color: var(--xfjy-danger-deep);
}

.mini-stat__value--amber {
  color: var(--xfjy-warning-deep);
}

/* ==================== 内容安全 ==================== */
.mod-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--xfjy-space-2);
  margin-bottom: var(--xfjy-space-2);
}

.mod-body {
  display: flex;
  gap: var(--xfjy-space-3);
}

.mod-body__chart {
  flex: 1;
  min-width: 0;
}

.mod-body__types {
  flex-shrink: 0;
  width: 180px;
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
  color: var(--xfjy-text-secondary);
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
  /* 圆内是排名数字（12px 文本），白字 on 主色 3.44:1 不达标，走深档 6.82:1 */
  color: var(--xfjy-text-inverse);
  background: var(--xfjy-primary-dark);
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

/* ==================== 折叠分析区 ==================== */
.analysis-collapse {
  border: 1px solid var(--xfjy-border);
  border-radius: var(--xfjy-radius-lg);
  background: var(--xfjy-bg-card);
  box-shadow: var(--xfjy-shadow-card);
}

.analysis-collapse :deep(.el-collapse-item__header) {
  padding: 0 var(--xfjy-space-4);
  height: 48px;
  border-bottom-color: var(--xfjy-border);
}

.analysis-collapse :deep(.el-collapse-item__wrap) {
  border-bottom-color: var(--xfjy-border);
}

.analysis-collapse :deep(.el-collapse-item__content) {
  padding: var(--xfjy-space-4);
}

.analysis-collapse__title {
  font-size: var(--xfjy-font-size-md);
  font-weight: 600;
  color: var(--xfjy-text-primary);
}

.analysis-collapse__sub {
  margin-left: var(--xfjy-space-2);
  font-size: var(--xfjy-font-size-xs);
  font-weight: 400;
  color: var(--xfjy-text-secondary);
}

/* ==================== 窄屏适配 ==================== */
@media (max-width: 767px) {
  .trend-tabs {
    justify-content: flex-start;
  }

  .todo-grid {
    grid-template-columns: 1fr;
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

/* ==================== 首屏骨架 ==================== */
.dash-skeleton {
  display: flex;
  flex-direction: column;
  gap: var(--xfjy-space-4);
}

.dash-skeleton__kpi-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--xfjy-space-4);
}

.dash-skeleton__kpi {
  /* 与重构后 KpiCard 实际高度对齐：padding 40 + 图标 32 + 数字 33 + 脚注 20 + 间距 24 */
  height: 150px;
  border-radius: var(--xfjy-radius-lg);
  background: var(--xfjy-bg-card);
  border: 1px solid var(--xfjy-border);
  position: relative;
  overflow: hidden;
}

.dash-skeleton__kpi::after,
.dash-skeleton__panel::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, transparent, var(--xfjy-skeleton-shimmer), transparent);
  animation: dash-sk-shimmer 1.8s ease-in-out infinite;
}

.dash-skeleton__panel-row {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--xfjy-space-4);
}

.dash-skeleton__panel {
  height: 280px;
  border-radius: var(--xfjy-radius-lg);
  background: var(--xfjy-bg-card);
  border: 1px solid var(--xfjy-border);
  position: relative;
  overflow: hidden;
}

@keyframes dash-sk-shimmer {
  0% {
    transform: translateX(-100%);
  }

  100% {
    transform: translateX(100%);
  }
}

@media (prefers-reduced-motion: reduce) {
  .dash-skeleton__kpi::after,
  .dash-skeleton__panel::after {
    animation: none;
  }
}

@media (max-width: 991px) {
  .dash-skeleton__kpi-row {
    grid-template-columns: repeat(2, 1fr);
  }

  .dash-skeleton__panel-row {
    grid-template-columns: 1fr;
  }
}
</style>
