import request from './request'

export interface CommentItem {
  id: string
  publishId: string
  content: string
  userId: number
  publishUserId: number
  created: number
  userNickname: string
  userAvatar: string
}

export interface CommentPageResult {
  total: number
  list: CommentItem[]
}

/** 分页查询评论列表 */
export function getCommentPageApi(params: {
  page: number
  pageSize: number
  keyword?: string
  userId?: number
}) {
  return request.get<any, CommentPageResult>('/admin/comments', { params })
}

/** 删除评论 */
export function deleteCommentApi(commentId: string) {
  return request.delete(`/admin/comments/${commentId}`)
}