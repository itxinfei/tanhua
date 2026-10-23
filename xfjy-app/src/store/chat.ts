import { defineStore } from "pinia";
import { ref } from "vue";
import {
  getHuanXinUser,
  getContacts,
  addContact,
  getLikeMessages,
  getCommentMessages,
  getLoveMessages,
  getAnnouncementMessages,
  type HuanXinUser,
  type Contact,
} from "@/api/chat";

/** 默认分页大小 */
const DEFAULT_PAGE_SIZE = 20;

/** 通知类型 */
export type NoticeType = "like" | "comment" | "love" | "announcement";

/** 本地存储 key 前缀：记录用户上次查看通知时的总条数 */
const NOTICE_SEEN_KEY_PREFIX = "xfjy_notice_seen_";

/**
 * 读取本地已查看条数
 * @param type 通知类型
 */
function getSeenCount(type: NoticeType): number {
  return uni.getStorageSync(`${NOTICE_SEEN_KEY_PREFIX}${type}`) || 0;
}

/**
 * 写入本地已查看条数
 * @param type 通知类型
 * @param count 当前总条数
 */
function setSeenCount(type: NoticeType, count: number): void {
  uni.setStorageSync(`${NOTICE_SEEN_KEY_PREFIX}${type}`, count);
}

/**
 * 聊天状态管理
 * 管理环信凭证、联系人列表
 * 注：消息收发由环信 SDK 直接管理，此 store 仅负责后端数据
 */
export const useChatStore = defineStore("chat", () => {
  /** 环信账号凭证（登录环信 SDK 用） */
  const huanXinUser = ref<HuanXinUser | null>(null);

  /** 联系人列表 */
  const contacts = ref<Contact[]>([]);
  /** 联系人分页状态 */
  const contactPage = ref(1);
  const contactHasMore = ref(true);

  /** 通知未读数（当前总数 - 上次查看数，后端无已读状态，前端本地计算） */
  const noticeUnreads = ref<Record<NoticeType, number>>({
    like: 0,
    comment: 0,
    love: 0,
    announcement: 0,
  });

  /** 加载状态 */
  const loading = ref(false);

  /**
   * 获取环信账号凭证（首次调用后缓存）
   * 前端用此凭证登录环信 SDK
   */
  async function loadHuanXinUser(): Promise<HuanXinUser> {
    if (huanXinUser.value) {
      return huanXinUser.value;
    }
    const result = await getHuanXinUser();
    huanXinUser.value = result;
    return result;
  }

  /**
   * 加载联系人列表（首页/下拉刷新）
   */
  async function loadContacts(): Promise<void> {
    if (loading.value) return;
    loading.value = true;
    try {
      const result = await getContacts({
        page: 1,
        pagesize: DEFAULT_PAGE_SIZE,
      });
      contacts.value = result.items || [];
      contactPage.value = 1;
      contactHasMore.value = (result.items?.length || 0) >= DEFAULT_PAGE_SIZE;
    } finally {
      loading.value = false;
    }
  }

  /**
   * 搜索联系人
   */
  async function searchContacts(keyword: string): Promise<void> {
    if (loading.value) return;
    loading.value = true;
    try {
      const result = await getContacts(
        { page: 1, pagesize: DEFAULT_PAGE_SIZE },
        keyword
      );
      contacts.value = result.items || [];
      contactHasMore.value = false;
    } finally {
      loading.value = false;
    }
  }

  /**
   * 加载更多联系人（上拉加载）
   */
  async function loadMoreContacts(): Promise<void> {
    if (loading.value || !contactHasMore.value) return;
    loading.value = true;
    try {
      const nextPage = contactPage.value + 1;
      const result = await getContacts({
        page: nextPage,
        pagesize: DEFAULT_PAGE_SIZE,
      });
      contacts.value.push(...(result.items || []));
      contactPage.value = nextPage;
      contactHasMore.value = (result.items?.length || 0) >= DEFAULT_PAGE_SIZE;
    } finally {
      loading.value = false;
    }
  }

  /**
   * 添加联系人（互相喜欢后调用）
   */
  async function addNewContact(userId: string): Promise<void> {
    await addContact(userId);
  }

  /**
   * 加载四类通知未读数
   * 后端无已读/未读状态，采用本地存储方案：
   * 未读数 = 当前总条数(counts) - 上次查看时记录的总条数
   */
  async function loadNoticeCounts(): Promise<void> {
    try {
      const [likeRes, commentRes, loveRes, announcementRes] = await Promise.all([
        getLikeMessages({ page: 1, pagesize: 1 }),
        getCommentMessages({ page: 1, pagesize: 1 }),
        getLoveMessages({ page: 1, pagesize: 1 }),
        getAnnouncementMessages({ page: 1, pagesize: 1 }),
      ]);
      const current: Record<NoticeType, number> = {
        like: likeRes.counts || 0,
        comment: commentRes.counts || 0,
        love: loveRes.counts || 0,
        announcement: announcementRes.counts || 0,
      };
      (Object.keys(current) as NoticeType[]).forEach((type) => {
        const unread = Math.max(0, current[type] - getSeenCount(type));
        noticeUnreads.value[type] = unread;
      });
    } catch {
      // 请求失败由 request 层统一 toast
    }
  }

  /**
   * 标记某类通知为已读（进入通知页时调用）
   * 将本地记录更新为当前总条数，未读清零
   * @param type 通知类型
   */
  function markNoticeRead(type: NoticeType): void {
    const currentUnread = noticeUnreads.value[type];
    if (currentUnread <= 0) return;
    // 当前总数 = 上次查看数 + 未读数
    setSeenCount(type, getSeenCount(type) + currentUnread);
    noticeUnreads.value[type] = 0;
  }

  /** 清除状态（登出时调用） */
  function clear(): void {
    huanXinUser.value = null;
    contacts.value = [];
    contactPage.value = 1;
    contactHasMore.value = true;
    noticeUnreads.value = { like: 0, comment: 0, love: 0, announcement: 0 };
  }

  return {
    huanXinUser,
    contacts,
    contactHasMore,
    loading,
    noticeUnreads,
    loadHuanXinUser,
    loadContacts,
    searchContacts,
    loadMoreContacts,
    addNewContact,
    loadNoticeCounts,
    markNoticeRead,
    clear,
  };
});