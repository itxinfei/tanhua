import { get, post, put, del } from "@/utils/request";
import type { PageResult, PageParam } from "@/api/circle";

/** 关系列表类型 */
export const FRIEND_TYPE = {
  MUTUAL: "1", // 互相关注（互相喜欢）
  FOLLOW: "2", // 我关注（我喜欢）
  FANS: "3", // 粉丝
  VISITOR: "4", // 谁看过我
} as const;

/** 用户资料（后端 UserInfoVo 结构） */
export interface UserProfile {
  id: string;
  avatar: string;
  nickname: string;
  birthday: string;
  age: string;
  gender: string;
  city: string;
  education: string;
  income: string;
  profession: string;
  marriage: number;
}

/** 关系统计（后端 CountsVo 结构） */
export interface RelationCounts {
  eachLoveCount: number;
  loveCount: number;
  fanCount: number;
}

/** 用户设置（后端 SettingsVo 结构） */
export interface UserSettings {
  id: string;
  strangerQuestion: string;
  phone: string;
  likeNotification: boolean;
  pinglunNotification: boolean;
  gonggaoNotification: boolean;
}

/** 位置上报参数 */
export interface LocationParam {
  longitude: number;
  latitude: number;
  addrStr: string;
}

/** 通知设置参数 */
export interface NotificationSettingParam {
  likeNotification: boolean;
  pinglunNotification: boolean;
  gonggaoNotification: boolean;
}

/**
 * 查询用户资料
 * 对接后端: GET /users
 *
 * @param userId   用户 ID（可选，不传则查当前登录用户）
 * @param huanxinId 环信 ID（可选，按环信 ID 查询）
 */
export function getUserProfile(
  userId?: string,
  huanxinId?: string
): Promise<UserProfile> {
  return get<UserProfile>("/users", {
    userID: userId,
    huanxinID: huanxinId,
  });
}

/**
 * 更新当前用户资料
 * 对接后端: PUT /users
 */
export function updateUserProfile(profile: Partial<UserProfile>): Promise<void> {
  return put<void>("/users", profile as any);
}

/**
 * 查询关系统计（互相喜欢/我喜欢/粉丝）
 * 对接后端: GET /users/counts
 */
export function getRelationCounts(): Promise<RelationCounts> {
  return get<RelationCounts>("/users/counts");
}

/**
 * 查询关系列表（互相关注/我关注/粉丝/谁看过我）
 * 对接后端: GET /users/friends/{type}
 *
 * @param type     关系类型（见 FRIEND_TYPE 常量）
 * @param params   分页参数
 * @param nickname 昵称搜索关键词（可选）
 */
export function getFriendList(
  type: string,
  params: PageParam,
  nickname?: string
): Promise<PageResult<UserProfile>> {
  return get<PageResult<UserProfile>>(`/users/friends/${type}`, {
    ...params,
    nickname,
  });
}

/**
 * 取消喜欢
 * 对接后端: DELETE /users/like/{uid}
 */
export function cancelLike(userId: string): Promise<void> {
  return del<void>(`/users/like/${userId}`);
}

/**
 * 关注粉丝（反向喜欢）
 * 对接后端: POST /users/fans/{uid}
 */
export function followFan(userId: string): Promise<void> {
  return post<void>(`/users/fans/${userId}`);
}

/**
 * 查询用户设置
 * 对接后端: GET /users/settings
 */
export function getUserSettings(): Promise<UserSettings> {
  return get<UserSettings>("/users/settings");
}

/**
 * 设置陌生人问题
 * 对接后端: POST /users/questions
 */
export function saveStrangerQuestion(content: string): Promise<void> {
  return post<void>("/users/questions", { content });
}

/**
 * 查询黑名单列表
 * 对接后端: GET /users/blacklist
 */
export function getBlacklist(params: PageParam): Promise<PageResult<UserProfile>> {
  return get<PageResult<UserProfile>>("/users/blacklist", params as any);
}

/**
 * 移除黑名单
 * 对接后端: DELETE /users/blacklist/{uid}
 */
export function removeFromBlacklist(userId: string): Promise<void> {
  return del<void>(`/users/blacklist/${userId}`);
}

/**
 * 更新通知设置
 * 对接后端: POST /users/notifications/setting
 */
export function updateNotificationSetting(param: NotificationSettingParam): Promise<void> {
  return post<void>("/users/notifications/setting", param as any);
}

/**
 * 上报地理位置
 * 对接后端: POST /baidu/location
 */
export function reportLocation(param: LocationParam): Promise<void> {
  return post<void>("/baidu/location", param as any);
}