import request from './request'

export interface VideoItem {
  id: string
  vid: number
  userId: number
  text: string
  picUrl: string
  videoUrl: string
  seeType: number
  locationName: string
  created: number
  userNickname: string
  userAvatar: string
  likeCount: number
  commentCount: number
}

export interface VideoPageResult {
  total: number
  list: VideoItem[]
}

/** 视频分页查询参数（字段对齐后端 AdminVideoController 的 @RequestParam） */
export interface VideoQuery {
  page: number
  pageSize: number
  keyword?: string
  userId?: number
  startDate?: string
  endDate?: string
}

/** 分页查询视频列表 */
export function getVideoPageApi(params: VideoQuery) {
  return request.get<unknown, VideoPageResult>('/admin/videos', { params })
}

/** 删除（下架）视频 */
export function deleteVideoApi(videoId: string) {
  return request.delete(`/admin/videos/${videoId}`)
}