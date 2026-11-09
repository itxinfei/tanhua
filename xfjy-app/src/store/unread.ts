import { defineStore } from "pinia";
import { ref } from "vue";
import { warn } from "@/utils/logger";

/** 未读消息总数（所有会话聚合） */
const unreadCount = ref(0);
/** 上次刷新时间戳（避免频繁刷新） */
let lastRefreshAt = 0;
/** 最小刷新间隔（毫秒） */
const MIN_REFRESH_INTERVAL = 30_000;

/**
 * 聚合未读消息数：IM 会话未读数 + 互动消息未读数
 * 优先用 IM 会话（实时、准确），失败时降级为 0（不阻塞主流程）
 */
async function refreshUnread(): Promise<void> {
  const now = Date.now();
  if (now - lastRefreshAt < MIN_REFRESH_INTERVAL) return;
  lastRefreshAt = now;

  try {
    // 动态 import：本 store 在首屏链（global-tabbar 30s 轮询）上，
    // 静态依赖会把 wukongimjssdk（utils-wukongim chunk）拉进首屏
    const { getConversations } = await import("@/utils/wukongim");
    const conversations = await getConversations();
    const total = conversations.reduce((sum, c) => sum + (c.unreadCount || 0), 0);
    unreadCount.value = total;
  } catch (e) {
    // 失败不阻塞主流程，仅记录日志
    warn(`[unread] 刷新未读数失败: ${String(e)}`);
  }
}

/**
 * 手动清零未读数（进入聊天页时调用，避免聊完后 tabbar 仍显示红点）
 */
function clearUnread(): void {
  unreadCount.value = 0;
}

/**
 * 未读数管理（tabbar badge 数据源）
 */
export const useUnreadStore = defineStore("unread", () => {
  return {
    unreadCount,
    refreshUnread,
    clearUnread,
  };
});
