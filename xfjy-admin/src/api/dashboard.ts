import request from './request'

export interface StatOverview {
  dau: number | null
  dauChange: number | null
  newUsers: number | null
  newUsersChange: number | null
  matchRate: number | null
  matchRateChange: number | null
  payRate: number | null
  payRateChange: number | null
  totalUsers: number | null
  totalPosts: number | null
  totalReports: number | null
  pendingReviews: number | null
}

export interface TrendData {
  dates: string[]
  /** 暂无统计源时为 null */
  dauList: number[] | null
  newUserList: number[]
  /** 暂无统计源时为 null */
  matchRateList: number[] | null
  /** 暂无统计源时为 null */
  payRateList: number[] | null
}

export interface GenderRatio {
  male: number
  female: number
}

export interface AgeDistribution {
  ranges: string[]
  counts: number[]
}

export interface PayTrend {
  dates: string[]
  orderList: number[]
}

export interface PayTypeDistribution {
  alipay: number
  wechat: number
  sandbox: number
}

/** 实时统计 */
export interface RealtimeStats {
  todayMessages: number | null
  todayPosters: number
  todayVideos: number
}

/** 在线用户统计 */
export interface OnlineStats {
  onlineUsers: number
  peakToday: number
}

/** 热门内容条目 */
export interface HotContentItem {
  id: string
  text: string | null
  userId: number
  likeCount: number
  commentCount: number
}

/** 热门视频条目 */
export interface HotVideoItem {
  videoId: string
  text: string | null
  userId: number
}

/** 热门内容 */
export interface HotContent {
  hotPosts: HotContentItem[]
  hotVideos: HotVideoItem[]
}

/** 按维度趋势 */
export interface TrendByDimension {
  dates: string[]
  postList: number[]
  videoList: number[]
}

/** 获取看板概览数据 */
export function getStatOverviewApi() {
  return request.get<any, StatOverview>('/admin/dashboard/overview')
}

/** 获取趋势数据 */
export function getTrendDataApi(days: number = 7) {
  return request.get<any, TrendData>('/admin/dashboard/trend', { params: { days } })
}

/** 获取性别比例 */
export function getGenderRatioApi() {
  return request.get<any, GenderRatio>('/admin/dashboard/gender-ratio')
}

/** 获取年龄分布 */
export function getAgeDistributionApi() {
  return request.get<any, AgeDistribution>('/admin/dashboard/age-distribution')
}

/** 获取支付订单趋势 */
export function getPayTrendApi(days: number = 7) {
  return request.get<any, PayTrend>('/admin/dashboard/pay-trend', { params: { days } })
}

/** 获取支付方式分布 */
export function getPayTypeApi() {
  return request.get<any, PayTypeDistribution>('/admin/dashboard/pay-type')
}

/** 获取实时统计 */
export function getRealtimeStatsApi() {
  return request.get<any, RealtimeStats>('/admin/dashboard/realtime-stats')
}

/** 获取在线用户统计 */
export function getOnlineStatsApi() {
  return request.get<any, OnlineStats>('/admin/dashboard/online-stats')
}

/** 获取热门内容 */
export function getHotContentApi() {
  return request.get<any, HotContent>('/admin/dashboard/hot-content')
}

/** 获取按维度趋势 */
export function getTrendByDimensionApi(days: number = 7) {
  return request.get<any, TrendByDimension>('/admin/dashboard/trend-by-dimension', { params: { days } })
}
