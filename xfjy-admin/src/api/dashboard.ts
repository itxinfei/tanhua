import request from './request'

export interface StatOverview {
  dau: number
  dauChange: number
  newUsers: number
  newUsersChange: number
  matchRate: number
  matchRateChange: number
  payRate: number
  payRateChange: number
  totalUsers: number
  totalPosts: number
  totalReports: number
  pendingReviews: number
}

export interface TrendData {
  dates: string[]
  dauList: number[]
  newUserList: number[]
  matchRateList: number[]
  payRateList: number[]
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