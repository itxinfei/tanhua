import { get } from "@/utils/request";

/** 协议详情（C 端匿名接口返回结构） */
export interface AgreementDetail {
  id: number;
  type: string;
  title: string;
  content: string;
  version: number;
  status: string;
  publisher?: string;
  publishedAt?: string;
  created?: string;
  updated?: string;
}

/**
 * 获取已发布的协议正文
 * 对接后端: GET /agreements/{type}（匿名可访问，@NoAuthorization）
 *
 * @param type USER(用户协议) / PRIVACY(隐私政策)
 */
export function getAgreement(type: "USER" | "PRIVACY"): Promise<AgreementDetail> {
  return get<AgreementDetail>(`/agreements/${type}`);
}
