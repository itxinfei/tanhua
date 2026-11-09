import request from './request'

// ==================== 旧端点（继续使用的部分） ====================

/** 看板概览：总用户、今日新增、待审核、待处理举报 */
export interface StatOverview {
  totalUsers: number | null
  newUsers: number | null
  pendingReviews: number | null
  totalReports: number | null
}

/** 新增注册趋势 */
export interface RegisterTrend {
  dates: string[]
  newUserList: number[]
}

/** 今日实时发送行为 */
export interface RealtimeStats {
  todayMessages: number | null
  todayPosters: number
  todayVideos: number
}

/** 热门内容条目 */
export interface HotContentItem {
  id: string
  text: string | null
  userId: number
  likeCount: number
  commentCount: number
}

export interface HotVideoItem {
  videoId: string
  text: string | null
  userId: number
}

export interface HotContent {
  hotPosts: HotContentItem[]
  hotVideos: HotVideoItem[]
}

// ==================== 新版端点 ====================

/** 实时在线概览 */
export interface OnlineOverview {
  /** 当前在线（5 分钟内心跳） */
  onlineUsers: number
  /** 今日峰值 */
  peakToday: number
  /** 今日活跃 DAU */
  todayActive: number
}

/** 留存单项 */
export interface RetentionItem {
  label: string
  days: number
  /** 队列规模（N 天前注册人数），为 0 时 rate=null 表示无样本 */
  cohortSize: number
  retained: number | null
  /** 留存率（百分比，1 位小数） */
  rate: number | null
}

/** 活跃与留存 */
export interface ActivityData {
  dates: string[]
  /** 埋点未覆盖的历史日为 null */
  dauList: Array<number | null>
  todayActive: number
  retention: RetentionItem[]
}

/** 匹配漏斗单日/今日 */
export interface MatchFunnel {
  /** 总滑动数（含 dislike） */
  swipes: number
  /** 右滑数（like+superlike） */
  likes: number
  superlikes: number
  /** 互喜匹配数 */
  matches: number
  /** 右滑去重用户数 */
  likeUsers: number
  /** 发生匹配的去重用户数 */
  matchedUsers: number
}

/** 匹配统计：今日漏斗 + 每日序列 */
export interface MatchStats {
  today: MatchFunnel
  daily: {
    dates: string[]
    swipes: number[]
    likes: number[]
    superlikes: number[]
    matches: number[]
  }
}

/** 人口属性聚合 */
export interface Demographics {
  total: number
  male: number
  female: number
  genderUnknown: number
  ageRanges: string[]
  ageCounts: number[]
  cities: Array<{ name: string; count: number }>
}

/** 收入概览（金额单位：分） */
export interface RevenueOverview {
  todayAmount: number
  todayOrders: number
  todayPayUsers: number
  totalAmount: number
  totalOrders: number
  totalPayUsers: number
}

/** 收入与付费 */
export interface RevenueData {
  overview: RevenueOverview
  trend: {
    dates: string[]
    orderList: number[]
    amountList: number[]
  }
  payType: {
    alipay: number
    wechat: number
    sandbox: number
  }
  totalUsers: number
  /** 累计付费率（百分比，1 位小数） */
  payRate: number | null
  /** 今日 ARPPU（分） */
  todayArppu: number | null
}

/** 内容发布趋势（动态 / 小视频） */
export interface DimensionTrend {
  dates: string[]
  postList: number[]
  videoList: number[]
}

/** 内容安全统计 */
export interface ModerationData {
  review: {
    pending: number
    todayApproved: number
    todayRejected: number
    daily: {
      dates: string[]
      created: number[]
      approved: number[]
      rejected: number[]
    }
    pendingTypes: Array<{ name: string; count: number }>
  }
  report: {
    pending: number
    processing: number
    todayNew: number
    todayResolved: number
    todayDismissed: number
    daily: {
      dates: string[]
      created: number[]
      resolved: number[]
      dismissed: number[]
    }
    targetTypes: Array<{ name: string; count: number }>
  }
}

// ==================== API ====================

/** 看板概览 */
export function getStatOverviewApi() {
  return request.get<unknown, StatOverview>('/admin/dashboard/overview')
}

/** 新增注册趋势 */
export function getTrendDataApi(days: number = 7) {
  return request.get<unknown, RegisterTrend>('/admin/dashboard/trend', { params: { days } })
}

/** 今日实时发送行为 */
export function getRealtimeStatsApi() {
  return request.get<unknown, RealtimeStats>('/admin/dashboard/realtime-stats')
}

/** 热门内容 */
export function getHotContentApi() {
  return request.get<unknown, HotContent>('/admin/dashboard/hot-content')
}

/** 实时在线概览（当前在线 / 峰值 / DAU） */
export function getOnlineOverviewApi() {
  return request.get<unknown, OnlineOverview>('/admin/dashboard/online-overview')
}

/** 活跃与留存 */
export function getActivityApi(days: number = 30) {
  return request.get<unknown, ActivityData>('/admin/dashboard/activity', { params: { days } })
}

/** 匹配统计 */
export function getMatchStatsApi(days: number = 7) {
  return request.get<unknown, MatchStats>('/admin/dashboard/match-stats', { params: { days } })
}

/** 人口属性（性别/年龄/城市） */
export function getDemographicsApi() {
  return request.get<unknown, Demographics>('/admin/dashboard/demographics')
}

/** 收入与付费 */
export function getRevenueApi(days: number = 7) {
  return request.get<unknown, RevenueData>('/admin/dashboard/revenue', { params: { days } })
}

/** 内容安全（审核/举报） */
export function getModerationApi(days: number = 7) {
  return request.get<unknown, ModerationData>('/admin/dashboard/moderation', { params: { days } })
}

/** 内容发布趋势（动态 / 小视频） */
export function getDimensionTrendApi(days: number = 7) {
  return request.get<unknown, DimensionTrend>('/admin/dashboard/trend-by-dimension', {
    params: { days },
  })
}
