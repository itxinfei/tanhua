import request from './request'

export interface MovementItem {
  id: string
  pid: number
  userId: number
  text: string
  medias: string[]
  seeType: number
  locationName: string
  created: number
  userNickname: string
  userAvatar: string
  likeCount: number
  commentCount: number
  loveCount: number
}

export interface MovementPageResult {
  total: number
  list: MovementItem[]
}

/** 分页查询动态列表 */
export function getMovementPageApi(params: {
  page: number
  pageSize: number
  keyword?: string
  userId?: number
}) {
  return request.get<any, MovementPageResult>('/admin/movements', { params })
}

/** 删除（下架）动态 */
export function deleteMovementApi(publishId: string) {
  return request.delete(`/admin/movements/${publishId}`)
}