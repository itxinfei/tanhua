import { get, post } from "@/utils/request";
import type { PageResult, PageParam } from "@/api/circle";

/** 环信用户凭证（SDK 5.x token 登录用） */
export interface HuanXinUser {
  username: string;
  token: string;
}

/** 聊天联系人 */
export interface Contact {
  userId: string;
  nickname: string;
  avatar: string;
  gender: string;
  age: number;
  city: string;
}

/** 互动消息（点赞/评论/喜欢） */
export interface MessageLike {
  id: string;
  avatar: string;
  nickname: string;
  createDate: string;
}

/** 公告消息 */
export interface MessageAnnouncement {
  id: string;
  title: string;
  description: string;
  createDate: string;
}

/**
 * 获取当前用户的环信账号凭证
 * 对接后端: GET /huanxin/user
 * 后端已用环信用户名密码换取用户级 token，前端直接用 token 登录 SDK
 */
export function getHuanXinUser(): Promise<HuanXinUser> {
  return get<HuanXinUser>("/huanxin/user");
}

/**
 * 添加联系人（互相喜欢后调用，注册环信好友关系）
 * 对接后端: POST /messages/contacts
 */
export function addContact(userId: string): Promise<void> {
  return post<void>("/messages/contacts", { userId });
}

/**
 * 查询联系人列表
 * 对接后端: GET /messages/contacts
 *
 * @param params 分页参数
 * @param keyword 昵称搜索关键词（可选）
 */
export function getContacts(
  params: PageParam,
  keyword?: string
): Promise<PageResult<Contact>> {
  return get<PageResult<Contact>>("/messages/contacts", {
    ...params,
    keyword,
  });
}

/**
 * 查询点赞消息列表
 * 对接后端: GET /messages/likes
 */
export function getLikeMessages(params: PageParam): Promise<PageResult<MessageLike>> {
  return get<PageResult<MessageLike>>("/messages/likes", params as any);
}

/**
 * 查询评论消息列表
 * 对接后端: GET /messages/comments
 */
export function getCommentMessages(params: PageParam): Promise<PageResult<MessageLike>> {
  return get<PageResult<MessageLike>>("/messages/comments", params as any);
}

/**
 * 查询喜欢消息列表
 * 对接后端: GET /messages/loves
 */
export function getLoveMessages(params: PageParam): Promise<PageResult<MessageLike>> {
  return get<PageResult<MessageLike>>("/messages/loves", params as any);
}

/**
 * 查询公告消息列表（免登录）
 * 对接后端: GET /messages/announcements
 */
export function getAnnouncementMessages(
  params: PageParam
): Promise<PageResult<MessageAnnouncement>> {
  return get<PageResult<MessageAnnouncement>>("/messages/announcements", params as any);
}