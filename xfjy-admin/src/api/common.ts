/**
 * 批量操作统一返回结构：后端各 batch 接口（视频/动态/评论批量删除、
 * 用户批量封禁/解封）均返回 Map{success, failed}，此类型为该契约的前端单一真源。
 */
export interface BatchOperateResult {
  /** 成功条数 */
  success: number
  /** 失败条数 */
  failed: number
}
