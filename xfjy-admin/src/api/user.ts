import request from './request'

export interface UserItem {
  id: number
  mobile: string
  nickname: string
  avatar: string
  gender: string
  age: number
  city: string
  status: 'NORMAL' | 'FROZEN'
  vipLevel: number
  createdAt: string
  lastActiveAt: string
}

export interface UserDetail extends UserItem {
  photos: string[]
  signature: string
  tags: string[]
  postCount: number
  followerCount: number
  followingCount: number
  matchCount: number
  reportCount: number
  freezeReason?: string
  coverPic?: string
  edu?: string
  industry?: string
  income?: string
  marriage?: string
  height?: string
  birthday?: string
}

/** 后台代改用户资料入参（字段均可选：不传=不改；空串=清空） */
export interface AdminProfileUpdate {
  nickname?: string
  avatar?: string
  signature?: string
  gender?: string
  city?: string
  height?: string
  education?: string
  profession?: string
  photoAlbum?: string
  income?: string
  marriage?: number
}

/** 资料编辑历史条目 */
export interface ProfileLogItem {
  id: number
  operatorId: number
  operatorName: string
  detail: string
  ip: string
  createdAt: string
}

export interface UserQuery {
  page: number
  pageSize: number
  keyword?: string
  gender?: string
  status?: string
  startDate?: string
  endDate?: string
}

export interface PageResult<T> {
  total: number
  list: T[]
}

/** 聊天会话摘要 */
export interface ChatSession {
  contactId: string
  lastMessage: string
  lastTime: number
  nickname?: string
  avatar?: string
}

/** 聊天消息 */
export interface ChatMessage {
  from: string
  content: string
  time: number
  type: string
}

/** 分页查询用户 */
export function getUserPageApi(params: UserQuery) {
  return request.get<any, PageResult<UserItem>>('/admin/users', { params })
}

/** 获取用户详情 */
export function getUserDetailApi(id: number) {
  return request.get<any, UserDetail>(`/admin/users/${id}`)
}

/** 封禁用户 */
export function freezeUserApi(id: number, reason: string) {
  return request.put(`/admin/users/${id}/freeze`, { reason })
}

/** 解封用户 */
export function unfreezeUserApi(id: number) {
  return request.put(`/admin/users/${id}/unfreeze`)
}

/** 重置用户密码 */
export function resetUserPasswordApi(id: number) {
  return request.put(`/admin/users/${id}/reset-password`)
}

/** 获取用户会话列表 */
export function getUserChatSessionsApi(id: number) {
  return request.get<any, PageResult<ChatSession>>(`/admin/users/${id}/chat-sessions`)
}

/** 获取与某用户的聊天记录 */
export function getUserChatMessagesApi(id: number, contactId: string, pageSize?: number) {
  return request.get<any, { messages: ChatMessage[]; cursor: string }>(
    `/admin/users/${id}/chat-messages`,
    { params: { contactId, pageSize } }
  )
}

/** 导出用户列表（CSV） */
export function exportUsersApi(params: { keyword?: string; gender?: string; status?: string; startDate?: string; endDate?: string }) {
  return request.get<any, Blob>('/admin/users/export', { params, responseType: 'blob' })
}

/** 客服代改用户资料（白名单字段部分更新） */
export function updateUserProfileApi(id: number, data: AdminProfileUpdate) {
  return request.put(`/admin/users/${id}/profile`, data)
}

/** 获取用户资料编辑历史 */
export function getProfileLogsApi(id: number, params: { page: number; pageSize: number }) {
  return request.get<any, PageResult<ProfileLogItem>>(`/admin/users/${id}/profile-logs`, { params })
}