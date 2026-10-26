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

/** 举报对象类型 */
export const REPORT_TARGET_TYPE = {
  USER: "USER",
  POST: "POST",
  COMMENT: "COMMENT",
  MESSAGE: "MESSAGE",
} as const;

/** 举报参数 */
export interface ReportParam {
  targetUserId: string;
  targetType?: string;
  targetId?: string;
  reason: string;
  description?: string;
  evidence?: string;
}

/**
 * 提交举报
 * 对接后端: POST /users/report
 */
export function submitReport(param: ReportParam): Promise<void> {
  return post<void>("/users/report", param as any);
}

/**
 * 注销账号（需先调 sendSmsCode 获取验证码）
 * 对接后端: POST /users/logoff
 */
export function deleteAccount(code: string): Promise<void> {
  return post<void>("/users/logoff", { code });
}

/** 实名认证状态 */
export interface VerifyStatus {
  verified: boolean;
}

/**
 * 提交实名认证（骨架：姓名+身份证格式校验，百度人脸核验 key 到位后接入二要素）
 * 对接后端: POST /users/verify
 */
export function submitVerify(realName: string, idCard: string): Promise<void> {
  return post<void>("/users/verify", { realName, idCard });
}

/**
 * 查询实名认证状态
 * 对接后端: GET /users/verify/status
 */
export function getVerifyStatus(): Promise<VerifyStatus> {
  return get<VerifyStatus>("/users/verify/status");
}

/** VIP 套餐 */
export interface VipPackage {
  id: number;
  name: string;
  level: number;
  /** 售价，单位：分 */
  price: number;
  /** 原价，单位：分 */
  originalPrice: number;
  /** 有效天数 */
  durationDays: number;
  description: string;
}

/** 支付订单 */
export interface PayOrder {
  id: number;
  orderNo: string;
  userId: number;
  packageId: number;
  packageName: string;
  /** 金额，单位：分 */
  amount: number;
  status: string;
  payType: string;
  paidTime: string;
  expireTime: string;
  created: string;
}

/** 用户 VIP 状态 */
export interface UserVip {
  userId: number;
  /** 0-非会员，1-月卡，2-季卡，3-年卡 */
  level: number;
  expireTime: string;
  source: string;
}

/**
 * 查询上架的 VIP 套餐列表
 * 对接后端: GET /users/vip/packages
 */
export function getVipPackages(): Promise<VipPackage[]> {
  return get<VipPackage[]>("/users/vip/packages");
}

/**
 * 创建支付订单
 * 对接后端: POST /users/vip/order
 */
export function createVipOrder(packageId: number): Promise<PayOrder> {
  return post<PayOrder>("/users/vip/order", { packageId });
}

/**
 * 模拟支付确认（沙箱回调）
 * 对接后端: POST /users/vip/pay/confirm
 */
export function confirmVipPay(orderNo: string): Promise<void> {
  return post<void>("/users/vip/pay/confirm", { orderNo });
}

/**
 * 查询当前用户 VIP 状态
 * 对接后端: GET /users/vip/status
 */
export function getVipStatus(): Promise<UserVip> {
  return get<UserVip>("/users/vip/status");
}

// ==================== P2 增值服务权益矩阵 ====================

/** 增值服务权益定义（与后端 ValueAddedService 对齐） */
export interface ValueAddedService {
  id: number;
  /** 权益码 */
  serviceCode: string;
  name: string;
  /** 所需 VIP 等级 */
  vipLevelRequired: number;
  /** 每日次数限制（null=不限） */
  dailyLimit: number | null;
  description: string;
}

/** 权益校验结果（与后端 BenefitCheckVo 对齐） */
export interface BenefitCheck {
  serviceCode: string;
  name: string;
  vipLevelRequired: number;
  /** 当前用户 VIP 等级（非会员为 0） */
  userVipLevel: number;
  /** 是否拥有权益 */
  hasBenefit: boolean;
  /** 每日上限（null=不限） */
  dailyLimit: number | null;
  /** 今日已用（null=不限次权益） */
  usedToday: number | null;
  /** 今日剩余（null=不限；0=已用尽） */
  remainingToday: number | null;
}

/**
 * 查询增值服务权益矩阵
 * 对接后端: GET /users/vip/benefits
 */
export function getVipBenefits(): Promise<ValueAddedService[]> {
  return get<ValueAddedService[]>("/users/vip/benefits");
}

/**
 * 权益校验：当前用户是否拥有指定权益
 * 对接后端: GET /users/vip/benefit/check?serviceCode=xxx
 */
export function checkBenefit(serviceCode: string): Promise<BenefitCheck> {
  return get<BenefitCheck>(`/users/vip/benefit/check?serviceCode=${serviceCode}`);
}

/**
 * 记录权益使用（每日限次权益扣减次数）
 * 对接后端: POST /users/vip/benefit/usage
 * @returns true=扣减成功/不限次；false=今日已用尽
 */
export async function recordBenefitUsage(serviceCode: string): Promise<boolean> {
  try {
    await post<void>("/users/vip/benefit/usage", { serviceCode });
    return true;
  } catch (e) {
    return false;
  }
}