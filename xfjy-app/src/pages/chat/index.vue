<template>
  <view class="chat-page">
    <network-status-bar />
    <!-- 顶栏：返回 + 标题（custom 导航，自行避让状态栏） -->
    <view class="nav-bar">
      <a11y-button
        class="back-btn"
        label="返回"
        @tap="goBack"
      >
        <view class="i-ph-caret-left" />
      </a11y-button>
      <text class="nav-title">
        消息
      </text>
      <view class="nav-placeholder" />
    </view>

    <view class="page-body">
      <!-- 通知入口条：喜欢/评论/心动/公告 -->
      <view class="notice-bar">
        <a11y-button
          v-for="item in noticeItems"
          :key="item.type"
          class="notice-item"
          :class="{ active: noticePanelType === item.type }"
          :label="item.unread > 0 ? item.label + '，' + item.unread + ' 条未读' : item.label"
          @tap="toggleNoticePanel(item.type)"
        >
          <view class="notice-icon-wrap">
            <view
              class="notice-icon-bg"
              :style="{ background: item.color }"
            >
              <view
                class="notice-icon"
                :class="item.iconClass"
                aria-hidden="true"
              />
            </view>
            <view
              v-if="item.unread > 0"
              class="badge pulse"
              aria-hidden="true"
            >
              {{ item.unread > 99 ? '99+' : item.unread }}
            </view>
          </view>
          <text class="notice-label">
            {{ item.label }}
          </text>
        </a11y-button>
      </view>

      <!-- 通知列表面板：内联展开，不跳转页面 -->
      <view
        v-if="noticePanelType"
        class="notice-panel"
      >
        <view class="notice-panel-list">
          <view
            v-if="noticeLocked"
            class="locked-state"
          >
            <view class="locked-icon">
              <view
                class="i-ph-lock"
                aria-hidden="true"
              />
            </view>
            <!-- 权益态徽章：统一走 components/vip-badge.vue（营销态，不冒充用户身份） -->
            <view class="locked-title-row">
              <vip-badge
                promo
                variant="pill"
              />
              <text class="locked-title">
                专享权益
              </text>
            </view>
            <text class="locked-desc">
              开通会员即可查看谁喜欢你
            </text>
            <a11y-button
              class="locked-btn"
              label="开通 VIP"
              @tap="goVip"
            >
              <text>开通 VIP</text>
            </a11y-button>
          </view>
          <template v-else>
            <!-- 失败态与「真的没通知」严格区分；love 权益锁定走上方锁定视图，不进此分支 -->
            <empty-state
              v-if="noticeList.length === 0 && !noticeLoading && !noticeLoadFailed"
              icon="notification"
              title="暂无通知"
              desc="有人喜欢你时会在这里提醒你"
            />
            <empty-state
              v-else-if="noticeList.length === 0 && !noticeLoading && noticeLoadFailed"
              role="alert"
              icon="info"
              title="暂时没能获取通知"
              desc="服务可能开小差了，检查网络后重试"
            >
              <a11y-button
                class="feed-retry"
                label="重新加载通知"
                @tap="fetchAndMarkNotice(noticePanelType)"
              >
                重新加载
              </a11y-button>
            </empty-state>
            <view
              v-if="noticeLoading"
              class="notice-loading"
            >
              加载中…
            </view>
            <a11y-button
              v-for="item in noticeList"
              :key="item.id"
              class="notice-list-item"
              :label="noticeItemLabel(item)"
              @tap="goNoticeDetail(item)"
            >
              <view class="notice-avatar-wrap">
                <smart-image
                  class="notice-avatar"
                  :src="item.avatar"
                  mode="aspectFill"
                  lazy-load
                  :aria-label="item.nickname + ' 的头像'"
                />
                <view
                  class="notice-type-badge"
                  :style="{ background: noticeTypeColor }"
                  aria-hidden="true"
                >
                  <view :class="noticeTypeIcon" />
                </view>
              </view>
              <view class="notice-info">
                <text class="notice-nickname">
                  {{ item.nickname }}
                </text>
                <text class="notice-desc">
                  {{ item.desc }}
                </text>
              </view>
              <text class="notice-time">
                {{ item.time }}
              </text>
            </a11y-button>
          </template>

          <!-- 查看全部：跳独立通知页（承载完整分页列表 + 公告详情）。
               仅有效类型（like/comment/love/announcement）展示，
               visitors/fans 后端无接口，不给出必然空白的入口。 -->
          <a11y-button
            v-if="isSupportedNoticeType(noticePanelType)"
            class="notice-more"
            label="查看全部"
            @tap="goNoticeList(noticePanelType)"
          >
            <text class="notice-more-text">
              查看全部
            </text>
            <view
              class="notice-more-arrow i-ph-caret-right"
              aria-hidden="true"
            />
          </a11y-button>
        </view>
      </view>

      <!-- 通知面板打开时隐藏消息列表：同屏只保留一个列表滚动区，避免双滚动层 -->
      <!-- S6：错误边界捕获会话列表渲染异常，@retry 重新拉取会话 -->
      <error-boundary @retry="loadMessages">
        <scroll-view
          v-show="!noticePanelType"
          scroll-y
          class="msg-list"
        >
          <!-- 空状态：确实没有会话；失败态严格区分，给重试入口，避免把故障误报成「没聊天」 -->
          <empty-state
            v-if="chatList.length === 0 && !chatLoadFailed"
            title="还没有聊天"
            desc="去匹配页滑动，遇见新朋友吧"
          />
          <empty-state
            v-else-if="chatList.length === 0 && chatLoadFailed"
            role="alert"
            icon="info"
            title="暂时没能获取聊天列表"
            desc="服务可能开小差了，检查网络后重试"
          >
            <a11y-button
              class="feed-retry"
              label="重新加载聊天列表"
              @tap="loadMessages()"
            >
              重新加载
            </a11y-button>
          </empty-state>
          <a11y-button
            v-for="item in chatList"
            :key="item.userId"
            class="chat-item"
            :label="chatItemLabel(item)"
            @tap="goChatDetail(item)"
          >
            <view class="avatar-wrap">
              <smart-image
                class="avatar"
                :src="item.avatar"
                mode="aspectFill"
                lazy-load
                :aria-label="item.nickname + ' 的头像'"
              />
              <view
                v-if="item.online"
                class="online-dot"
                aria-hidden="true"
              />
            </view>
            <view class="chat-info">
              <view class="top-row">
                <text class="nickname">
                  {{ item.nickname }}
                </text>
              </view>
              <view class="bottom-row">
                <text class="last-msg">
                  {{ item.lastMessage || item.city || '暂无消息' }}
                </text>
                <view
                  v-if="item.unreadCount > 0"
                  class="unread-badge"
                  aria-hidden="true"
                >
                  {{ item.unreadCount > 99 ? '99+' : item.unreadCount }}
                </view>
              </view>
            </view>
          </a11y-button>
        </scroll-view>
      </error-boundary>
    </view>

    <!-- 公告详情弹窗：公告无对应用户，点击通知项弹出正文，不跳用户主页 -->
    <AnnouncementDetailDialog
      :visible="!!announcementDetail"
      :notice="announcementDialogNotice"
      @close="announcementDetail = null"
    />
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from "vue";
import { onShow } from "@dcloudio/uni-app";
import EmptyState from "@/components/empty-state.vue";
import NetworkStatusBar from "@/components/network-status-bar.vue";
import AnnouncementDetailDialog from "@/components/announcement-detail-dialog.vue";
import A11yButton from "@/components/a11y-button.vue";
import SmartImage from "@/components/smart-image.vue";
import ErrorBoundary from "@/components/error-boundary.vue";
import VipBadge from "@/components/vip-badge.vue";
import { getContacts, getLikeMessages, getCommentMessages, getLoveMessages, getAnnouncementMessages, type Contact, type MessageLike, type MessageAnnouncement } from "@/api/chat";
import { useChatStore, type NoticeType } from "@/store/chat";
import { ensureSelfIM, getConversations } from "@/utils/wukongim";
import { checkBenefit, getUserProfile, type UserProfile } from "@/api/mine";

const chatStore = useChatStore();

const BADGE_MAX_COUNT = 99;

/**
 * 人工客服 IM 账号（与后端 KefuService.KEFU_AGENT_ID 一致）。
 * 客服是 IM 通道账号、非平台用户：不在 tb_user_info / 好友列表里。
 * 客服有独立入口 pages/kefu/chat，会话列表需过滤掉该账号，避免重复出现。
 */
const KEFU_AGENT_ID = "kefu0001";

interface ChatListItem extends Contact {
  lastMessage: string;
  unreadCount: number;
}

const NOTICE_META: Record<NoticeType, { iconClass: string; label: string; color: string }> = {
  like: { iconClass: "i-ph-heart-fill", label: "喜欢", color: "var(--color-primary)" },
  comment: { iconClass: "i-ph-chat-circle-dots", label: "评论", color: "var(--info-blue)" },
  love: { iconClass: "i-ph-star-fill", label: "心动", color: "var(--color-primary)" },
  announcement: { iconClass: "i-ph-bell", label: "公告", color: "var(--success)" },
};

const chatList = ref<ChatListItem[]>([]);
/** 会话列表加载失败标记：失败且列表为空时渲染失败态，与「确实没有会话」区分 */
const chatLoadFailed = ref(false);

const noticeItems = computed(() =>
  (Object.keys(NOTICE_META) as NoticeType[]).map((type) => ({
    type,
    iconClass: NOTICE_META[type].iconClass,
    label: NOTICE_META[type].label,
    color: NOTICE_META[type].color,
    unread: chatStore.noticeUnreads[type],
  }))
);

async function loadMessages(): Promise<void> {
  try {
    // 1. 确保 IM 已连接，拉取真实会话列表（含最后消息/未读数/时间）
    await ensureSelfIM();
    const conversations = await getConversations();
    // 2. 拉取联系人资料（昵称/头像），用于合并会话数据
    const contactsRes = await getContacts({ page: 1, pagesize: 50 });
    const contactMap = new Map<string, Contact>(
      (contactsRes.items || []).map((c) => [String(c.userId), c])
    );
    // 3. 客服 IM 账号（kefu0001）有独立入口 pages/kefu/chat，不在会话列表重复出现，先过滤。
    const visibleConversations = conversations.filter(
      (s) => s.conversationId !== KEFU_AGENT_ID
    );
    // 4. 联系人表只收 IM 好友（互相喜欢后注册），非好友单聊对象不在其中；
    //    对缺失资料的会话按 userId 并发补查用户公开资料，避免昵称裸显 uid。
    const missingIds = visibleConversations
      .map((s) => s.conversationId)
      .filter((id) => id.length > 0 && !contactMap.has(id));
    const profileMap = new Map<string, UserProfile>();
    await Promise.all(
      missingIds.map(async (id) => {
        try {
          const profile = await getUserProfile(id);
          if (profile) profileMap.set(id, profile);
        } catch {
          // 单个资料查询失败不影响整表，该会话昵称走「用户+id」语义化兜底
        }
      })
    );
    // 5. 合并：联系人资料优先，其次用户公开资料，最后语义化兜底，不裸露 uid。
    chatList.value = visibleConversations.map((s) => {
      const contact = contactMap.get(s.conversationId);
      const profile = profileMap.get(s.conversationId);
      return {
        userId: s.conversationId,
        nickname: contact?.nickname || profile?.nickname || `用户${s.conversationId}`,
        avatar: contact?.avatar || profile?.avatar || "",
        gender: contact?.gender || profile?.gender || "",
        age: contact?.age || Number(profile?.age) || 0,
        city: contact?.city || profile?.city || "",
        online: false,
        lastMessage: s.lastMessage,
        unreadCount: s.unreadCount,
      };
    });
    chatLoadFailed.value = false;
  } catch {
    // 请求失败由 request 层统一 toast；置失败态，空列表时展示重试入口（已有旧数据则保留不清空）
    chatLoadFailed.value = true;
  }
}

/* ---------------- 通知面板（内联展开，不跳转页面） ---------------- */
interface NoticeItem {
  id: string;
  avatar?: string;
  nickname: string;
  desc: string;
  time: string;
  userId?: number;
}

const NOTICE_TYPE_COLORS: Record<string, string> = {
  like: "var(--color-primary)",
  comment: "var(--info-blue)",
  love: "var(--color-primary)",
  announcement: "var(--success)",
};
const NOTICE_TYPE_ICONS: Record<string, string> = {
  like: "i-ph-heart-fill",
  comment: "i-ph-chat-circle-dots",
  love: "i-ph-star-fill",
  announcement: "i-ph-bell",
};
const NOTICE_DESC: Record<string, string> = {
  like: "赞了你的动态",
  comment: "评论了你",
  love: "喜欢了你",
};
const NOTICE_PAGE_SIZE = 20;

const noticePanelType = ref<string>("");
const noticeList = ref<NoticeItem[]>([]);
const noticeLoading = ref(false);
const noticeLocked = ref(false);
/** 通知列表加载失败标记：love 权益锁定（noticeLocked）是产品设计而非故障，不得置位 */
const noticeLoadFailed = ref(false);

const noticeTypeColor = computed(() => NOTICE_TYPE_COLORS[noticePanelType.value] || "var(--color-primary)");
const noticeTypeIcon = computed(() => NOTICE_TYPE_ICONS[noticePanelType.value] || "i-ph-bell");

function mapLikeItem(msg: MessageLike, desc: string): NoticeItem {
  return {
    id: msg.id,
    avatar: msg.avatar,
    nickname: msg.nickname || "未知用户",
    desc,
    time: msg.createDate,
    userId: msg.userId,
  };
}

function mapAnnouncementItem(msg: MessageAnnouncement): NoticeItem {
  return {
    id: msg.id,
    nickname: msg.title,
    desc: msg.description,
    time: msg.createDate,
  };
}

/** 加载当前类型的通知列表
 * @returns 当前总条数（失败返回 null，不清未读） */
async function loadNoticeList(type: string): Promise<number | null> {
  if (noticeLoading.value) return null;
  noticeLoading.value = true;
  noticeLoadFailed.value = false;
  try {
    const params = { page: 1, pagesize: NOTICE_PAGE_SIZE };

    // 心动（谁喜欢我）需 see_who_likes 权益
    if (type === "love") {
      try {
        const benefit = await checkBenefit("see_who_likes");
        if (!benefit?.hasBenefit) {
          noticeLocked.value = true;
          noticeList.value = [];
          return null;
        }
      } catch {
        // 权益校验失败不阻断（降级放行）
      }
    }
    noticeLocked.value = false;

    if (type === "like") {
      const res = await getLikeMessages(params);
      noticeList.value = (res.items || []).map((m) => mapLikeItem(m, NOTICE_DESC.like));
      return res.counts || 0;
    } else if (type === "comment") {
      const res = await getCommentMessages(params);
      noticeList.value = (res.items || []).map((m) => mapLikeItem(m, NOTICE_DESC.comment));
      return res.counts || 0;
    } else if (type === "love") {
      const res = await getLoveMessages(params);
      noticeList.value = (res.items || []).map((m) => mapLikeItem(m, NOTICE_DESC.love));
      return res.counts || 0;
    } else if (type === "announcement") {
      const res = await getAnnouncementMessages(params);
      noticeList.value = (res.items || []).map(mapAnnouncementItem);
      return res.counts || 0;
    } else {
      noticeList.value = [];
      return null;
    }
  } catch {
    // 请求失败由 request 层统一 toast；置失败态与「确实没有通知」区分（旧数据保留不清空）
    noticeLoadFailed.value = true;
    return null;
  } finally {
    noticeLoading.value = false;
  }
}

/** 拉取通知并在成功后清零未读；失败（返回 null）时保留角标，避免漏提醒。
 *  面板切换与失败态重试共用同一套语义，防止两处实现分叉 */
async function fetchAndMarkNotice(type: string): Promise<void> {
  const total = await loadNoticeList(type);
  // 加载成功后才清零未读
  if (total !== null) chatStore.markNoticeRead(type as NoticeType, total);
}

/** 切换通知面板：同一类型再次点击收起 */
async function toggleNoticePanel(type: string): Promise<void> {
  if (noticePanelType.value === type) {
    noticePanelType.value = "";
    return;
  }
  noticePanelType.value = type;
  noticeList.value = [];
  noticeLocked.value = false;
  noticeLoadFailed.value = false;
  await fetchAndMarkNotice(type);
}

function goVip(): void {
  uni.navigateTo({ url: "/pages/mine/vip" });
}

/**
 * 后端有接口的有效通知类型（与 chatStore.NoticeType 对齐）。
 * visitors / fans 仅预留占位，不给入口——点进去必然空白。
 */
const SUPPORTED_NOTICE_TYPES: readonly string[] = ["like", "comment", "love", "announcement"];

function isSupportedNoticeType(type: string): boolean {
  return SUPPORTED_NOTICE_TYPES.includes(type);
}

/** 跳转独立通知页查看完整列表（完整分页 + 公告详情弹窗）。 */
function goNoticeList(type: string): void {
  if (!isSupportedNoticeType(type)) return;
  uni.navigateTo({ url: `/pages/chat/notifications?type=${type}` });
}

/** 当前查看的公告项（null 表示弹窗关闭） */
const announcementDetail = ref<NoticeItem | null>(null);

/** 公告弹窗展示模型：NoticeItem 的 nickname/desc 即公告标题/正文 */
const announcementDialogNotice = computed(() =>
  announcementDetail.value
    ? {
        title: announcementDetail.value.nickname,
        content: announcementDetail.value.desc,
        time: announcementDetail.value.time,
      }
    : null
);

function goNoticeDetail(item: NoticeItem): void {
  // 公告无对应用户，点击弹出正文详情，不跳用户主页
  if (noticePanelType.value === "announcement") {
    announcementDetail.value = item;
    return;
  }
  if (!item.userId) return;
  uni.navigateTo({ url: `/pages/match/detail?id=${item.userId}` });
}

function noticeItemLabel(item: NoticeItem): string {
  return `${item.nickname}，${item.desc}，${item.time}`;
}

function goChatDetail(contact: ChatListItem): void {
  uni.navigateTo({
    url: "/pages/chat/chat?userId=" + contact.userId + "&nickname=" + encodeURIComponent(contact.nickname),
  });
}

function chatItemLabel(contact: ChatListItem): string {
  const parts = [contact.nickname];
  if (contact.age) parts.push(contact.age + "岁");
  parts.push(contact.lastMessage || contact.city || "暂无消息");
  if (contact.unreadCount > 0) {
    parts.push((contact.unreadCount > BADGE_MAX_COUNT ? "99+" : contact.unreadCount) + " 条未读");
  }
  if (contact.online) parts.push("在线");
  return parts.join("，");
}

function goBack(): void {
  uni.navigateBack({ fail: () => uni.switchTab({ url: "/pages/circle/index" }) });
}

/* ---------------- 生命周期 ---------------- */
onShow(() => {
  // 数据为空时才加载，避免每次 onShow 都重新请求导致页面闪动
  if (chatList.value.length === 0) {
    loadMessages();
  }
  // 未读角标每次 onShow 都重算（4 个 pagesize=1 的 count 请求，开销可忽略）：
  // 只在列表为空时刷新会让角标长期停留在旧值，新通知时亮时不亮
  chatStore.loadNoticeCounts();
});
</script>

<style lang="scss" scoped>
.chat-page {
  height: calc(100vh - var(--window-bottom, 0px));
  display: flex;
  flex-direction: column;
  background: var(--bg-sub);
}

/* 顶栏：返回 + 标题（custom 导航，直接贴状态栏，需自行避让） */
.nav-bar {
  display: flex;
  align-items: center;
  height: calc(var(--category-bar-h) + env(safe-area-inset-top, 0px));
  padding: 0 24rpx;
  padding-top: env(safe-area-inset-top, 0px);
  background: var(--bg-card);
  border-bottom: 1rpx solid var(--border-light);

  .back-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 88rpx;
    height: 88rpx;
    margin-left: -24rpx;

    .i-ph-caret-left {
      font-size: 40rpx;
      color: var(--text-primary);
    }
  }

  .nav-title {
    flex: 1;
    text-align: center;
    font-size: 34rpx;
    font-weight: 700;
    color: var(--text-primary);
  }

  /* 与返回按钮等宽占位，保证标题严格居中 */
  .nav-placeholder {
    width: 64rpx;
  }
}

.page-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

/* 通知入口 */
.notice-bar {
  display: flex;
  justify-content: space-around;
  padding: 24rpx 0;
  background: var(--bg-card);
  margin-bottom: 16rpx;

  .notice-item {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8rpx;

    .notice-icon-wrap {
      position: relative;

      .notice-icon-bg {
        width: 80rpx;
        height: 80rpx;
        border-radius: 50%;
        display: flex;
        align-items: center;
        justify-content: center;
      }

      .notice-icon {
        font-size: 36rpx;
        color: var(--text-inverse);
        line-height: 1;
      }

      .badge {
        position: absolute;
        top: -4rpx;
        right: -8rpx;
        min-width: 32rpx;
        height: 32rpx;
        line-height: 32rpx;
        text-align: center;
        font-size: 20rpx;
        color: var(--text-inverse);
        background: var(--gradient-brand);
        border-radius: var(--radius-pill);
        padding: 0 8rpx;
        border: 2rpx solid var(--text-inverse);
      }
    }

    .notice-label {
      font-size: 24rpx;
      color: var(--text-secondary);
    }
  }
}

/* 聊天列表 */
.msg-list {
  flex: 1;
  min-height: 0;
  background: var(--bg-card);

  .chat-item {
    display: flex;
    align-items: center;
    padding: 24rpx 32rpx;
    /* 会话行高真源：与 mine/settings 同一档 --row-h-md */
    min-height: var(--row-h-touch-md);
    box-sizing: border-box;
    border-bottom: 1rpx solid var(--border-light);

    .avatar-wrap {
      position: relative;
      flex-shrink: 0;
    }

    .avatar {
      width: 96rpx;
      height: 96rpx;
      border-radius: 50%;
    }

    .online-dot {
      position: absolute;
      bottom: 2rpx;
      right: 2rpx;
      width: 24rpx;
      height: 24rpx;
      background: var(--success);
      border-radius: 50%;
      border: 4rpx solid var(--text-inverse);
    }

    .chat-info {
      flex: 1;
      margin-left: 24rpx;
      overflow: hidden;

      .top-row {
        display: flex;
        justify-content: space-between;
        align-items: center;

        .nickname {
          font-size: 30rpx;
          font-weight: 500;
          color: var(--text-primary);
        }
      }

      .bottom-row {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-top: 8rpx;

        .last-msg {
          font-size: 26rpx;
          color: var(--text-hint);
          flex: 1;
        }

        .unread-badge {
          min-width: 32rpx;
          height: 32rpx;
          line-height: 32rpx;
          text-align: center;
          font-size: 20rpx;
          color: var(--text-inverse);
          background: var(--gradient-brand);
          border-radius: var(--radius-pill);
          padding: 0 8rpx;
          margin-left: 16rpx;
        }
      }
    }
  }
}

/* 通知面板：内联展开，不跳转页面 */
.notice-panel {
  background: var(--bg-card);
  border-bottom: 1rpx solid var(--border-light);
  /* 撑满 notice-bar 下方剩余高度，消除面板下方留白（与 .msg-list 同口径） */
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;

  .notice-panel-list {
    flex: 1;
    min-height: 0;
    /* 面板已撑满剩余高度：列表超长时自身滚动，避免内容被裁切不可见 */
    overflow-y: auto;
    padding: 16rpx 24rpx;

    .notice-loading {
      text-align: center;
      padding: 40rpx;
      color: var(--text-hint);
      font-size: 26rpx;
    }

    /* 「查看全部」入口：列表底部居中，88rpx 热区（§7.1 独立功能按钮）。
       视觉上只显示文字 + 箭头，热区由 a11y-button 自身 padding 外扩。 */
    .notice-more {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 4rpx;
      margin-top: 24rpx;
      padding: 20rpx;
      border-top: 1rpx solid var(--border-faint);

      .notice-more-text {
        font-size: 26rpx;
        color: var(--color-primary-text);
      }

      .notice-more-arrow {
        width: 24rpx;
        height: 24rpx;
        color: var(--color-primary-text);
      }
    }

    .notice-list-item {
      display: flex;
      align-items: center;
      padding: 20rpx 0;
      border-bottom: 1rpx solid var(--border-faint);

      &:last-child { border-bottom: none; }

      .notice-avatar-wrap {
        position: relative;
        flex-shrink: 0;

        .notice-avatar {
          width: 80rpx;
          height: 80rpx;
          border-radius: 50%;
          background: var(--bg-sub);
          border: 2rpx solid var(--bg-icon-soft);
        }

        .notice-type-badge {
          position: absolute;
          bottom: -2rpx;
          right: -2rpx;
          width: 32rpx;
          height: 32rpx;
          border-radius: 50%;
          border: 2rpx solid var(--text-inverse);
          display: flex;
          align-items: center;
          justify-content: center;

          view {
            font-size: 16rpx;
            color: var(--text-inverse);
          }
        }
      }

      .notice-info {
        flex: 1;
        margin-left: 20rpx;
        min-width: 0;

        .notice-nickname {
          font-size: 28rpx;
          font-weight: bold;
          color: var(--text-primary);
          display: block;
        }

        .notice-desc {
          font-size: 24rpx;
          color: var(--text-hint);
          margin-top: 4rpx;
          display: block;
        }
      }

      .notice-time {
        font-size: 22rpx;
        color: var(--text-hint);
        flex-shrink: 0;
        margin-left: 16rpx;
      }
    }
  }

  .locked-state {
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 60rpx 48rpx;

    .locked-icon {
      width: 100rpx;
      height: 100rpx;
      border-radius: 50%;
      background: linear-gradient(135deg, var(--gold), var(--gold-soft));
      display: flex;
      align-items: center;
      justify-content: center;
      margin-bottom: 20rpx;

      .i-ph-lock {
        font-size: 48rpx;
        color: var(--bg-deep);
      }
    }

    .locked-title-row {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 10rpx;
    }

    .locked-title {
      font-size: 28rpx;
      color: var(--text-primary);
      font-weight: bold;
    }

    .locked-desc {
      font-size: 24rpx;
      color: var(--text-hint);
      margin-top: 8rpx;
      text-align: center;
    }

    .locked-btn {
      margin-top: 32rpx;
      width: 280rpx;
      height: 72rpx;
      display: flex;
      align-items: center;
      justify-content: center;
      background: linear-gradient(90deg, var(--gold), var(--gold-soft));
      border-radius: 36rpx;

      text {
        font-size: 28rpx;
        font-weight: bold;
        color: var(--bg-deep);
      }
    }
  }
}

/* 加载失败态的重试按钮（置于 empty-state 的 slot 内） */
.feed-retry {
  margin-top: 32rpx;
  padding: 16rpx 56rpx;
  border-radius: var(--radius-pill);
  font-size: 28rpx;
  font-weight: 600;
  color: var(--text-inverse);
  background: var(--gradient-brand);
}
</style>
