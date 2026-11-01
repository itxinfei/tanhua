import { get, post, upload } from "@/utils/request";
import type { PageResult, PageParam } from "@/api/circle";

/** IM 用户凭证（SDK 登录用） */
export interface HuanXinUser {
  username: string;
  token: string;
  /** IM 通道：wukongim（默认）/ huanxin */
  provider?: string;
  /** 长连接地址，由服务端下发 */
  wsUrl?: string;
}

/** 聊天联系人 */
export interface Contact {
  userId: string;
  nickname: string;
  avatar: string;
  gender: string;
  age: number;
  city: string;
  /** 在线状态（后端按需返回，前端展示绿点） */
  online?: boolean;
}

/** 互动消息（点赞/评论/喜欢） */
export interface MessageLike {
  id: string;
  avatar: string;
  nickname: string;
  createDate: string;
  userId?: number;
}

/** 公告消息 */
export interface MessageAnnouncement {
  id: string;
  title: string;
  description: string;
  createDate: string;
}

/**
 * @deprecated 已废弃：HuanXinController 已删除，App.vue/chat页面改用 loginIMDirect 直连 WuKongIM。
 * 本函数仅保留供历史参考，无任何调用方。
 * 原路径 GET /huanxin/user 已下线；现 WuKongIM token 由前端按 userId 本地派生（参见 wukongim.ts）
 */
export function getHuanXinUser(): Promise<HuanXinUser> {
  return get<HuanXinUser>("/messages/im/credential");
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

/** 上传接口返回体（PicUploadResult，status=done 时 name 为 MinIO 访问 URL） */
interface PicUploadResult {
  uid: string;
  name: string;
  status: string;
  response: string;
}

/**
 * 上传到 MinIO
 * 对接后端: POST /upload/image
 *
 * @param filePath 本地图片临时路径
 * @returns 图片 MinIO 访问 URL，上传失败返回空串
 */
export async function uploadChatImage(filePath: string): Promise<string> {
  try {
    const result = await upload<PicUploadResult>("/upload/image", [filePath], "file");
    if (result && result.status === "done" && result.name) {
      return result.name;
    }
    return "";
  } catch {
    return "";
  }
}

/**
 * 上传到 MinIO
 * 对接后端: POST /upload/audio（支持 mp3/aac/m4a/wav/amr/mp4）
 *
 * @param filePath 本地音频临时路径
 * @returns 音频 MinIO 访问 URL，上传失败返回空串
 */
export async function uploadChatAudio(filePath: string): Promise<string> {
  try {
    const result = await upload<PicUploadResult>("/upload/audio", [filePath], "file");
    if (result && result.status === "done" && result.name) {
      return result.name;
    }
    return "";
  } catch {
    return "";
  }
}