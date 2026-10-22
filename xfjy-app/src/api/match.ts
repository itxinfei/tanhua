import { get, post } from "@/utils/request";
import type { MatchCard, MatchResult } from "@/store/match";

// 分页参数
export interface PageParam {
  page: number;
  pagesize: number;
}

/**
 * 获取推荐卡片列表
 * 对接后端: GET /xfjy/cards（xfjy-server 服务）
 */
export function getCards(params?: PageParam): Promise<MatchCard[]> {
  return get<MatchCard[]>("/xfjy/cards", params as any);
}

/**
 * 上报滑动动作（喜欢/不喜欢/超级喜欢）
 * 对接后端: POST /xfjy/{id}/love | /xfjy/{id}/unlove
 */
export function swipeCard(
  userId: number | string,
  action: "like" | "dislike" | "superlike"
): Promise<MatchResult> {
  if (action === "dislike") {
    return post<MatchResult>(`/xfjy/${userId}/unlove`);
  }
  return post<MatchResult>(`/xfjy/${userId}/love`);
}

/**
 * 今日佳人
 * 对接后端: GET /xfjy/todayBest
 */
export function getTodayBest(): Promise<MatchCard> {
  return get<MatchCard>("/xfjy/todayBest");
}

/**
 * 推荐用户列表（分页）
 * 对接后端: GET /xfjy/recommendation
 */
export function getRecommendation(params: PageParam): Promise<{
  items: MatchCard[];
  counts: number;
}> {
  return get("/xfjy/recommendation", params as any);
}

/**
 * 附近的人
 * 对接后端: GET /xfjy/search
 */
export function getNearby(params: {
  gender: string;
  distance: number;
}): Promise<MatchCard[]> {
  return get<MatchCard[]>("/xfjy/search", params);
}