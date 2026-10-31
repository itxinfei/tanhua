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