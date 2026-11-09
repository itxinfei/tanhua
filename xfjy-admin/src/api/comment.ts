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

/** 评论分页查询参数（字段对齐后端 AdminCommentController 的 @RequestParam） */
export interface CommentQuery {
  page: number
  pageSize: number
  keyword?: string
  userId?: number
  startDate?: string
  endDate?: string
}

/** 分页查询评论列表 */
export function getCommentPageApi(params: CommentQuery) {
  return request.get<unknown, CommentPageResult>('/admin/comments', { params })
}

/** 删除评论 */
export function deleteCommentApi(commentId: string) {
  return request.delete(`/admin/comments/${commentId}`)
}