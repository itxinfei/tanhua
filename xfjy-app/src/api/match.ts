import { get, post } from "@/utils/request";

/**
 * 今日佳人/探花卡片
 * 与后端 com.xfjy.server.vo.TodayBest 精确对齐
 */
export interface TodayBest {
  /** 用户ID */
  id: number;
  /** 头像URL */
  avatar: string;
  /** 昵称 */
  nickname: string;
  /** 性别：man | woman */
  gender: string;
  /** 年龄 */
  age: number;
  /** 个人标签 */
  tags: string[];
  /** 缘分值 */
  fateValue: number;
  /** 城市 */
  city?: string;
  /** 学历 */
  edu?: string;
  /** 行业 */
  industry?: string;
}

/**
 * 附近的人
 * 与后端 com.xfjy.server.vo.NearUserVo 精确对齐
 */
export interface NearUser {
  /** 用户ID */
  userId: number;
  /** 头像URL */
  avatar: string;
  /** 昵称 */
  nickname: string;
  /** 年龄 */
  age?: number;
  /** 居住城市 */
  city?: string;
}

/**
 * 推荐用户查询参数
 * 与后端 com.xfjy.server.vo.RecommendUserQueryParam 精确对齐
 */
export interface RecommendParam {
  /** 当前页数，默认1 */
  page?: number;
  /** 页尺寸，默认10 */
  pagesize?: number;
  /** 性别：man | woman */
  gender?: string;
  /** 近期登陆时间 */
  lastLogin?: string;
  /** 年龄 */
  age?: number;
  /** 居住地 */
  city?: string;
  /** 学历 */
  education?: string;
}

/**
 * 分页结果
 * 与后端 com.xfjy.server.vo.PageResult 精确对齐
 */
export interface PageResult<T> {
  /** 当前页码 */
  page: number;
  /** 每页条数 */
  pagesize: number;
  /** 总页数 */
  pages: number;
  /** 总记录数 */
  counts: number;
  /** 数据列表 */
  items: T[];
}

/**
 * 探花卡片列表
 * 对接后端: GET /xfjy/cards
 * @returns 卡片列表
 */
export function getCards(): Promise<TodayBest[]> {
  return get<TodayBest[]>("/xfjy/cards");
}

/**
 * 喜欢（右滑/超级喜欢）
 * 对接后端: GET /xfjy/{id}/love
 * @param userId 对方用户ID
 * @returns true=互相喜欢（匹配成功）；false=单方面喜欢
 */
export function loveUser(userId: number | string): Promise<boolean> {
  return get<boolean>(`/xfjy/${userId}/love`);
}

/**
 * 不喜欢（左滑）
 * 对接后端: GET /xfjy/{id}/unlove
 * @param userId 对方用户ID
 */
export function unloveUser(userId: number | string): Promise<void> {
  return get<void>(`/xfjy/${userId}/unlove`);
}

/**
 * 今日佳人
 * 对接后端: GET /xfjy/todayBest
 * @returns 今日佳人信息
 */
export function getTodayBest(): Promise<TodayBest> {
  return get<TodayBest>("/xfjy/todayBest");
}

/**
 * 今日佳人详情
 * 对接后端: GET /xfjy/{id}/personalInfo
 * @param userId 用户ID
 * @returns 佳人详情
 */
export function getPersonalInfo(userId: number | string): Promise<TodayBest> {
  return get<TodayBest>(`/xfjy/${userId}/personalInfo`);
}

/**
 * 推荐用户列表（分页）
 * 对接后端: GET /xfjy/recommendation
 * @param params 查询参数
 * @returns 分页结果
 */
export function getRecommendation(params: RecommendParam): Promise<PageResult<TodayBest>> {
  return get<PageResult<TodayBest>>("/xfjy/recommendation", params);
}

/**
 * 附近的人
 * 对接后端: GET /xfjy/search
 * @param gender 性别筛选：man | woman（可选）
 * @param distance 距离范围，单位米，默认2000
 * @returns 附近用户列表
 */
export function getNearby(gender?: string, distance?: number): Promise<NearUser[]> {
  return get<NearUser[]>("/xfjy/search", { gender, distance });
}

/**
 * 查询陌生人问题
 * 对接后端: GET /xfjy/strangerQuestions
 * @param userId 对方用户ID
 * @returns 问题内容
 */
export function getStrangerQuestion(userId: number | string): Promise<string> {
  return get<string>("/xfjy/strangerQuestions", { userId });
}

/**
 * 回复陌生人问题
 * 对接后端: POST /xfjy/strangerQuestions
 * @param userId 对方用户ID
 * @param reply 回复内容
 */
export function replyStrangerQuestion(userId: number | string, reply: string): Promise<void> {
  return post<void>("/xfjy/strangerQuestions", { userId, reply });
}