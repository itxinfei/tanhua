import { get, post } from "@/utils/request";

/** FAQ 常见问题 */
export interface FaqItem {
  id: number;
  category: string;
  question: string;
  answer: string;
  sort: number;
}

/** 意见反馈 */
export interface FeedbackItem {
  id: number;
  userId: number;
  content: string;
  contact: string | null;
  reply: string | null;
  /** 0-待处理 1-已回复 2-已关闭 */
  status: number;
  createDate: string;
}

/**
 * 查询启用中的 FAQ 列表（免登录）
 * 对接后端: GET /kefu/faq
 */
export function getFaqListApi(): Promise<FaqItem[]> {
  return get<FaqItem[]>("/kefu/faq");
}

/**
 * 提交意见反馈
 * 对接后端: POST /kefu/feedback
 *
 * @param content 反馈内容
 * @param contact 联系方式（可选）
 */
export function submitFeedbackApi(content: string, contact?: string): Promise<void> {
  return post<void>("/kefu/feedback", { content, contact });
}

/**
 * 查询我的反馈列表
 * 对接后端: GET /kefu/feedback/my
 */
export function getMyFeedbackApi(): Promise<FeedbackItem[]> {
  return get<FeedbackItem[]>("/kefu/feedback/my");
}