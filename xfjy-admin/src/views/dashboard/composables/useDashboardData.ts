/**
 * 数据看板数据编排
 *
 * 刷新策略：
 * - 快刷新 30s：在线/DAU/峰值 + 今日消息/动态/视频（轻量，实时区与 KPI 在线卡）
 * - 慢刷新 5min（=10 个快周期）：全量重拉（趋势/漏斗/人口/收入/审核）
 * - 页面切到后台暂停计时，回到前台立即快刷并重置倒计时
 * - 时间范围（7/30 天）切换只重拉受影响的趋势类接口
 */
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'
import {
  getStatOverviewApi,
  getTrendDataApi,
  getRealtimeStatsApi,
  getHotContentApi,
  getOnlineOverviewApi,
  getActivityApi,
  getMatchStatsApi,
  getDemographicsApi,
  getRevenueApi,
  getModerationApi,
  getDimensionTrendApi,
  type StatOverview,
  type RegisterTrend,
  type RealtimeStats,
  type HotContent,
  type OnlineOverview,
  type ActivityData,
  type MatchStats,
  type Demographics,
  type RevenueData,
  type ModerationData,
  type DimensionTrend,
} from '@/api/dashboard'

/** 快刷新间隔（秒） */
const FAST_INTERVAL_SECONDS = 30
/** 每 N 个快周期做一次全量慢刷新 */
const SLOW_CYCLE_EVERY = 10

/** 单个请求静默失败：看板局部数据不可用不拖垮整页，由各卡片显示空态 */
async function silent<T>(p: Promise<T>): Promise<T | null> {
  try {
    return await p
  } catch {
    return null
  }
}

export function useDashboardData() {
  const loading = ref(true)
  const refreshing = ref(false)
  const lastUpdated = ref<Date | null>(null)
  const days = ref(7)
  const autoRefresh = ref(true)
  /** 距离下次快刷新倒计时（秒） */
  const countdown = ref(FAST_INTERVAL_SECONDS)
  /** 当前秒（顶栏时钟） */
  const now = ref(new Date())

  // ==================== 数据 ====================
  const overview = ref<StatOverview | null>(null)
  const registerTrend = ref<RegisterTrend | null>(null)
  const realtime = ref<RealtimeStats | null>(null)
  const online = ref<OnlineOverview | null>(null)
  const activity = ref<ActivityData | null>(null)
  const match = ref<MatchStats | null>(null)
  const demographics = ref<Demographics | null>(null)
  const revenue = ref<RevenueData | null>(null)
  const moderation = ref<ModerationData | null>(null)
  const dimensionTrend = ref<DimensionTrend | null>(null)
  const hot = ref<HotContent | null>(null)

  // ==================== 加载 ====================
  /** 快刷新：实时在线 + 今日发送行为 */
  async function loadFast() {
    const [onlineRes, realtimeRes] = await Promise.all([
      silent(getOnlineOverviewApi()),
      silent(getRealtimeStatsApi()),
    ])
    if (onlineRes) online.value = onlineRes
    if (realtimeRes) realtime.value = realtimeRes
  }

  /** 受时间范围影响的趋势类接口 */
  async function loadScoped() {
    const d = days.value
    const [trendRes, activityRes, matchRes, revenueRes, moderationRes, dimensionRes] =
      await Promise.all([
        silent(getTrendDataApi(d)),
        silent(getActivityApi(d)),
        silent(getMatchStatsApi(d)),
        silent(getRevenueApi(d)),
        silent(getModerationApi(d)),
        silent(getDimensionTrendApi(d)),
      ])
    if (trendRes) registerTrend.value = trendRes
    if (activityRes) activity.value = activityRes
    if (matchRes) match.value = matchRes
    if (revenueRes) revenue.value = revenueRes
    if (moderationRes) moderation.value = moderationRes
    if (dimensionRes) dimensionTrend.value = dimensionRes
  }

  /** 全量加载（首屏 / 手动刷新 / 慢刷新） */
  async function loadAll(isManual = false) {
    if (isManual) refreshing.value = true
    const [
      overviewRes,
      trendRes,
      realtimeRes,
      onlineRes,
      activityRes,
      matchRes,
      demoRes,
      revenueRes,
      moderationRes,
      dimensionRes,
      hotRes,
    ] = await Promise.all([
      silent(getStatOverviewApi()),
      silent(getTrendDataApi(days.value)),
      silent(getRealtimeStatsApi()),
      silent(getOnlineOverviewApi()),
      silent(getActivityApi(days.value)),
      silent(getMatchStatsApi(days.value)),
      silent(getDemographicsApi()),
      silent(getRevenueApi(days.value)),
      silent(getModerationApi(days.value)),
      silent(getDimensionTrendApi(days.value)),
      silent(getHotContentApi()),
    ])
    if (overviewRes) overview.value = overviewRes
    if (trendRes) registerTrend.value = trendRes
    if (realtimeRes) realtime.value = realtimeRes
    if (onlineRes) online.value = onlineRes
    if (activityRes) activity.value = activityRes
    if (matchRes) match.value = matchRes
    if (demoRes) demographics.value = demoRes
    if (revenueRes) revenue.value = revenueRes
    if (moderationRes) moderation.value = moderationRes
    if (dimensionRes) dimensionTrend.value = dimensionRes
    if (hotRes) hot.value = hotRes

    lastUpdated.value = new Date()
    loading.value = false
    refreshing.value = false
    countdown.value = FAST_INTERVAL_SECONDS
  }

  // ==================== 轮询调度 ====================
  let tickTimer: ReturnType<typeof setInterval> | null = null
  let fastCycleCount = 0

  function stopTimer() {
    if (tickTimer) {
      clearInterval(tickTimer)
      tickTimer = null
    }
  }

  function startTimer() {
    stopTimer()
    if (!autoRefresh.value) return
    tickTimer = setInterval(() => {
      now.value = new Date()
      countdown.value -= 1
      if (countdown.value <= 0) {
        countdown.value = FAST_INTERVAL_SECONDS
        fastCycleCount += 1
        if (fastCycleCount % SLOW_CYCLE_EVERY === 0) {
          void loadAll()
        } else {
          void loadFast()
        }
      }
    }, 1000)
  }

  function onVisibilityChange() {
    if (document.hidden) {
      stopTimer()
    } else if (autoRefresh.value) {
      // 回到前台立即快刷一次，再重启计时
      void loadFast()
      countdown.value = FAST_INTERVAL_SECONDS
      startTimer()
    }
  }

  watch(autoRefresh, (on) => {
    if (on) {
      countdown.value = FAST_INTERVAL_SECONDS
      startTimer()
    } else {
      stopTimer()
    }
  })

  // 时间范围切换：只重拉趋势类
  watch(days, () => {
    void loadScoped()
  })

  onMounted(() => {
    void loadAll()
    startTimer()
    now.value = new Date()
    document.addEventListener('visibilitychange', onVisibilityChange)
  })

  onBeforeUnmount(() => {
    stopTimer()
    document.removeEventListener('visibilitychange', onVisibilityChange)
  })

  return {
    // 状态
    loading,
    refreshing,
    lastUpdated,
    days,
    autoRefresh,
    countdown,
    now,
    // 数据
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
    // 动作
    refreshAll: () => loadAll(true),
  }
}
