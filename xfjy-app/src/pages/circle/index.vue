<template>
  <view class="social-page">
    <!-- 断网/恢复全局提示条（fixed 定位自管理显隐，S1） -->
    <network-status-bar />
    <!-- 顶级分区：圈子 / 消息 -->
    <view
      class="seg-bar"
      role="tablist"
      aria-label="社区分区"
    >
      <a11y-button
        class="seg"
        :class="{ active: topTab === 'circle' }"
        role="tab"
        :checked="topTab === 'circle'"
        label="圈子"
        @tap="switchTopTab('circle')"
      >
        圈子
      </a11y-button>
      <a11y-button
        class="seg"
        :class="{ active: topTab === 'message' }"
        role="tab"
        :checked="topTab === 'message'"
        label="消息"
        @tap="switchTopTab('message')"
      >
        消息
        <view
          v-if="totalUnread > 0"
          class="seg-badge"
          aria-hidden="true"
        >
          {{ totalUnread > 99 ? '99+' : totalUnread }}
        </view>
      </a11y-button>
    </view>

    <!-- 圈子视图 -->
    <view
      v-show="topTab === 'circle'"
      class="tab-view"
    >
      <view
        class="tab-bar"
        role="tablist"
        aria-label="圈子频道"
      >
        <a11y-button
          v-for="tab in tabs"
          :key="tab.key"
          class="tab-item"
          :class="{ active: activeTab === tab.key }"
          role="tab"
          :checked="activeTab === tab.key"
          :label="tab.label"
          @tap="switchTab(tab.key)"
        >
          <text>{{ tab.label }}</text>
        </a11y-button>
      </view>

      <!-- S6：错误边界捕获动态列表渲染异常，@retry 重新拉取 feed -->
      <error-boundary @retry="loadFeed">
      <scroll-view
        scroll-y
        class="feed-list"
        refresher-enabled
        :refresher-triggered="refreshing"
        @refresherrefresh="onRefresh"
        @scrolltolower="loadMore"
      >
        <skeleton
          v-if="feedList.length === 0 && loading"
          variant="feed"
          :count="SKELETON_CARD_COUNT"
          :grid-cols="GRID_COLUMN_COUNT"
        />
        <!-- 空状态：确实没有动态 -->
        <empty-state
          v-if="feedList.length === 0 && !loading && !loadFailed"
          icon="camera"
          title="暂无动态"
          desc="快来发布第一条，分享你的生活"
        />
        <!-- 加载失败：与「真的没动态」严格区分，给明确原因 + 可重试入口。
             后端异常时旧版把故障误报成「暂无动态」，用户无从判断也无法恢复。 -->
        <empty-state
          v-if="feedList.length === 0 && !loading && loadFailed"
          role="alert"
          icon="info"
          title="暂时没能获取动态"
          desc="服务可能开小差了，检查网络后重试"
        >
          <a11y-button
            class="feed-retry"
            label="重新加载动态"
            @tap="loadFeed()"
          >
            重新加载
          </a11y-button>
        </empty-state>
        <view
          v-for="item in feedList"
          :key="item.id"
          class="post-card"
        >
          <view class="post-header">
            <smart-image
              class="avatar"
              :src="item.avatar"
              mode="aspectFill"
              lazy-load
              :aria-label="item.nickname + ' 的头像'"
            />
            <view class="user-info">
              <text class="nickname">
                {{ item.nickname }}
              </text>
              <text class="meta">
                {{ item.createDate }} · {{ item.location || '未知位置' }}
              </text>
            </view>
          </view>
          <view
            v-if="item.textContent"
            class="post-content ellipsis-2"
          >
            {{ item.textContent }}
          </view>
          <view
            v-if="item.tags && item.tags.length"
            class="tag-row"
          >
            <a11y-button
              v-for="(tag, i) in item.tags"
              :key="i"
              class="tag-chip"
              :label="`查看话题 ${tag}`"
              @tap="goTag(tag)"
            >
              <text># {{ tag }}</text>
            </a11y-button>
          </view>
          <!-- filter(Boolean)：后端可能返回含空字符串的 imageContent（如 [""]），
               空串渲染为占位块但点击预览会因 uni.previewImage 收到空 URL 报错 -->
          <view
            v-if="item.imageContent?.filter(Boolean).length"
            class="image-grid"
            :class="'grid-' + Math.min(item.imageContent.filter(Boolean).length, GRID_COLUMN_COUNT)"
          >
            <smart-image
              v-for="(img, i) in item.imageContent.filter(Boolean).slice(0, MAX_GRID_IMAGE_COUNT)"
              :key="i"
              class="grid-image"
              :src="img"
              :fallback="''"
              mode="aspectFill"
              lazy-load
              :aria-label="'动态图片 ' + (i + 1) + '，点击查看大图'"
              clickable
              @click="previewImage(item.imageContent.filter(Boolean), i)"
            />
          </view>
          <view class="action-row">
            <!-- 计数为 0 时给"抢首赞/抢首评"引导，而非空白让人以为功能缺失 -->
            <a11y-button
              class="action"
              :label="item.hasLiked ? '取消点赞，当前 ' + (item.likeCount || 0) + ' 赞' : '点赞，当前 ' + (item.likeCount || 0) + ' 赞'"
              @tap="handleLike(item)"
            >
              <view
                class="action-icon"
                :class="{ liked: item.hasLiked, 'heart-beat': item.hasLiked, 'i-ph-heart-fill': item.hasLiked, 'i-ph-heart': !item.hasLiked }"
                aria-hidden="true"
              />
              <text class="count">
                {{ item.likeCount > 0 ? item.likeCount : '抢首赞' }}
              </text>
            </a11y-button>
            <a11y-button
              class="action"
              :label="'查看评论，共 ' + (item.commentCount || 0) + ' 条'"
              @tap="goDetail(item)"
            >
              <view
                class="action-icon i-ph-chat-circle-dots"
                aria-hidden="true"
              />
              <text class="count">
                {{ item.commentCount > 0 ? item.commentCount : '抢首评' }}
              </text>
            </a11y-button>
            <a11y-button
              class="action"
              :label="item.hasLoved ? '取消心动' : '心动'"
              @tap="handleLove(item)"
            >
              <view
                class="action-icon"
                :class="{ loved: item.hasLoved, 'i-ph-star-fill': item.hasLoved, 'i-ph-star': !item.hasLoved }"
                aria-hidden="true"
              />
            </a11y-button>
          </view>
        </view>
        <view
          v-if="loading"
          class="loading"
        >
          加载中…
        </view>
        <!-- P2-4：全部加载完给明确终点，避免用户反复上拉以为卡了 -->
        <view
          v-else-if="!hasMore && feedList.length > 0"
          class="loading"
        >
          没有更多了
        </view>
      </scroll-view>
      </error-boundary>
    </view>

    <!-- 消息视图（原 chat/index 内容） -->
    <view
      v-show="topTab === 'message'"
      class="tab-view"
    >
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
            <text class="locked-title">
              VIP 专享权益
            </text>
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

    <MatchPopup />

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
import EmptyState from "@/components/empty-state.vue";
import NetworkStatusBar from "@/components/network-status-bar.vue";
import Skeleton from "@/components/skeleton.vue";
import { onShow, onShareAppMessage } from "@dcloudio/uni-app";
import MatchPopup from "@/components/match-popup.vue";
import AnnouncementDetailDialog from "@/components/announcement-detail-dialog.vue";
import A11yButton from "@/components/a11y-button.vue";
import SmartImage from "@/components/smart-image.vue";
import ErrorBoundary from "@/components/error-boundary.vue";
import { useCircleStore, CIRCLE_NEED_REFRESH_KEY } from "@/store/circle";
import type { Movement } from "@/api/circle";
import { getContacts, getLikeMessages, getCommentMessages, getLoveMessages, getAnnouncementMessages, type Contact, type MessageLike, type MessageAnnouncement } from "@/api/chat";
import { useChatStore, type NoticeType } from "@/store/chat";
import { ensureSelfIM, getConversations } from "@/utils/wukongim";
import { checkBenefit } from "@/api/mine";

const circleStore = useCircleStore();
const chatStore = useChatStore();

/** 顶级分区：圈子 / 消息 */
const topTab = ref<"circle" | "message">("circle");

/* ---------------- 圈子（原有逻辑） ---------------- */
const MAX_GRID_IMAGE_COUNT = 9;
const GRID_COLUMN_COUNT = 3;
const SKELETON_CARD_COUNT = 3;

const tabs = [
  { key: "friend", label: "好友" },
  { key: "recommend", label: "推荐" },
] as const;

const activeTab = computed(() => circleStore.activeTab);
const feedList = computed<Movement[]>(() =>
  circleStore.activeTab === "friend"
    ? circleStore.friendMovements
    : circleStore.recommendMovements
);
const loading = computed(() => circleStore.loading);
const loadFailed = computed(() => circleStore.loadFailed);
/** 当前 Tab 是否还有更多（P2-4：滑到底显示「没有更多了」） */
const hasMore = computed(() =>
  circleStore.activeTab === "friend" ? circleStore.friendHasMore : circleStore.recommendHasMore
);
const refreshing = ref(false);

function switchTab(key: "friend" | "recommend") {
if (circleStore.activeTab === key) return;
circleStore.switchTab(key);
// 切换关注/推荐时收起通知面板
noticePanelType.value = "";
const targetList = key === "friend" ? circleStore.friendMovements : circleStore.recommendMovements;
if (targetList.length === 0) {
loadFeed();
}
}

function goDetail(item: Movement) {
  uni.navigateTo({ url: "/pages/circle/detail?id=" + item.id });
}

function goTag(tag: string) {
  uni.navigateTo({ url: "/pages/circle/tag?tag=" + encodeURIComponent(tag) });
}

function previewImage(images: string[], index: number) {
  uni.previewImage({ urls: images, current: images[index] });
}

async function handleLike(item: Movement) {
  try {
    await circleStore.toggleLike(item.id);
  } catch {
    // 请求失败由 request 层统一 toast
  }
}

async function handleLove(item: Movement) {
  try {
    await circleStore.toggleLove(item.id);
  } catch {
    // 请求失败由 request 层统一 toast
  }
}

async function loadFeed() {
  try {
    if (circleStore.activeTab === "friend") {
      await circleStore.loadFriendMovements();
    } else {
      await circleStore.loadRecommendMovements();
    }
  } catch {
    // 请求失败由 request 层统一 toast
  }
}

async function onRefresh(): Promise<void> {
  refreshing.value = true;
  try {
    await loadFeed();
  } finally {
    refreshing.value = false;
  }
}

async function loadMore() {
  try {
    if (circleStore.activeTab === "friend") {
      await circleStore.loadMoreFriendMovements();
    } else {
      await circleStore.loadMoreRecommendMovements();
    }
  } catch {
    // 请求失败由 request 层统一 toast
  }
}

/* ---------------- 消息（原 chat/index 逻辑） ---------------- */
/** 原生 tabBar 合并后「圈子」页索引（见 pages.json） */
const TAB_BAR_INDEX = 1;
const BADGE_MAX_COUNT = 99;

/**
 * 人工客服 IM 账号（与后端 KefuService.KEFU_AGENT_ID 一致）。
 * 客服是 IM 通道账号、非平台用户：不在 tb_user_info / 好友列表里，
 * 会话列表拿不到联系人资料，必须单独兜底为「在线客服」而非裸露 uid。
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

const totalUnread = computed(() =>
  chatList.value.reduce((sum, item) => sum + (item.unreadCount || 0), 0)
);


function updateTabBarBadge(): void {
  const total = totalUnread.value;
  if (total > 0) {
    uni.setTabBarBadge({ index: TAB_BAR_INDEX, text: total > BADGE_MAX_COUNT ? "99+" : String(total) });
  } else {
    uni.removeTabBarBadge({ index: TAB_BAR_INDEX });
  }
}

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
// 3. 合并：以会话为主，资料补充；无资料时用会话 ID 兜底
chatList.value = conversations.map((s) => {
const contact = contactMap.get(s.conversationId);
// 客服 IM 账号不是平台用户：永远不在 contactMap 里，必须单独兜底，
// 否则会话列表/聊天标题会裸露 uid「kefu0001」
const isKefu = s.conversationId === KEFU_AGENT_ID;
return {
userId: s.conversationId,
nickname: isKefu ? "在线客服" : (contact?.nickname || s.conversationId),
avatar: isKefu ? "" : (contact?.avatar || ""),
gender: contact?.gender || "",
age: contact?.age || 0,
city: contact?.city || "",
online: false,
lastMessage: s.lastMessage,
unreadCount: s.unreadCount,
};
});
updateTabBarBadge();
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

/**
 * 跳转独立通知页查看完整列表。
 *
 * 背景（§11.7 #41）：`pages/chat/notifications.vue` 承载完整分页列表与公告详情弹窗，
 * 但此前无任何入口可达（circle 面板是内联展开，只显示第一页）。
 * 这里补「查看全部」入口，让长列表可被完整浏览。
 */
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

/** 顶级分区切换：只切换视图，不重新加载数据（数据已在 onShow 首次加载，切换时保留滚动位置） */
function switchTopTab(tab: "circle" | "message"): void {
if (topTab.value === tab) return;
topTab.value = tab;
// 切换分区时收起通知面板，避免面板停留在非消息视图
noticePanelType.value = "";
// 首次切到消息且无数据时才加载，避免每次切换都重新请求导致页面闪动
if (tab === "message" && chatList.value.length === 0) {
loadMessages();
    chatStore.loadNoticeCounts();
  }
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

/* ---------------- 生命周期 ---------------- */
onShow(() => {
  // 发布成功返回时强制刷新圈子列表
  if (uni.getStorageSync(CIRCLE_NEED_REFRESH_KEY) === "1") {
    uni.removeStorageSync(CIRCLE_NEED_REFRESH_KEY);
    loadFeed();
    return;
  }
  // 数据为空时才加载，避免每次 onShow 都重新请求导致页面闪动
  if (feedList.value.length === 0 && topTab.value === "circle") {
    loadFeed();
  }
  if (chatList.value.length === 0 && topTab.value === "message") {
    loadMessages();
  }
  // 未读角标每次 onShow 都重算（4 个 pagesize=1 的 count 请求，开销可忽略）：
  // 只在列表为空时刷新会让角标长期停留在旧值，新通知时亮时不亮
  chatStore.loadNoticeCounts();
});

onShareAppMessage(() => ({
  title: "心飞交友 · 遇见心动的人",
  path: "/pages/match/index",
}));
</script>

<style lang="scss" scoped>
.social-page {
height: calc(100vh - var(--window-bottom, 0px));
display: flex;
flex-direction: column;
background: var(--bg-sub);
}

/* 圈子/消息视图容器：v-show 切换时保持状态，避免重建 */
.tab-view {
flex: 1;
min-height: 0;
display: flex;
flex-direction: column;
}

/* 顶级分区：圈子 / 消息（custom 导航后直接贴状态栏，需自行避让） */
.seg-bar {
  display: flex;
  align-items: stretch;
  justify-content: center;
  gap: 56rpx;
  height: calc(var(--category-bar-h) + env(safe-area-inset-top, 0px));
  padding-top: env(safe-area-inset-top, 0px);
  background: var(--bg-card);
  position: relative;
  border-bottom: 1rpx solid var(--border-light);

  .seg {
    position: relative;
    align-self: stretch;
    display: flex;
    align-items: center;
    font-size: 32rpx;
    color: var(--text-hint);
    padding: 6rpx 4rpx;

    &.active {
      color: var(--color-primary);
      font-weight: 700;

      &::after {
        content: "";
        position: absolute;
        left: 50%;
        bottom: 0;
        transform: translateX(-50%);
        width: 48rpx;
        height: 8rpx;
        background: var(--color-primary);
        border-radius: var(--radius-pill);
      }
    }
  }

  .seg-badge {
    position: absolute;
    top: -4rpx;
    right: -28rpx;
    min-width: 30rpx;
    height: 30rpx;
    line-height: 30rpx;
    text-align: center;
    font-size: 20rpx;
    color: var(--text-inverse);
    background: var(--gradient-brand);
    border-radius: 15rpx;
    padding: 0 8rpx;
    border: 2rpx solid var(--text-inverse);
  }
}

/* 圈子子标签 */
.tab-bar {
  display: flex;
  align-items: stretch;
  background: var(--bg-card);
  padding: 0 32rpx;
  height: var(--category-bar-h);
  position: relative;

  .tab-item {
    display: flex;
    align-items: center;
    padding: 0 32rpx;
    font-size: 30rpx;
    color: var(--text-hint);
    position: relative;

    &.active {
      color: var(--color-primary);
      font-weight: bold;

      &::after {
        content: "";
        position: absolute;
        bottom: 0;
        left: 50%;
        transform: translateX(-50%);
        width: 48rpx;
        height: 8rpx;
        background: var(--color-primary);
        border-radius: var(--radius-pill);
      }
    }
  }
}

.feed-list {
  flex: 1;
  min-height: 0;
  /* border-box：width:100% 时左右 padding 计入宽度，否则容器被撑破导致横向滚动
     （历史 bug：content-box 下 390 视口实测宽 407px，全设备横向溢出） */
  box-sizing: border-box;
  padding: 16rpx;
}

.post-card {
  background: var(--bg-card);
  border-radius: 24rpx;
  padding: 24rpx;
  margin-bottom: 16rpx;
  box-shadow: var(--shadow-card);

  .post-header {
    display: flex;
    align-items: center;

    .avatar {
      width: 72rpx;
      height: 72rpx;
      border-radius: 50%;
    }

    .user-info {
      margin-left: 16rpx;

      .nickname {
        font-size: 28rpx;
        font-weight: 500;
        display: block;
      }

      .meta {
        font-size: 22rpx;
        color: var(--text-hint);
        margin-top: 4rpx;
        display: block;
      }
    }
  }

  .post-content {
    font-size: 28rpx;
    line-height: 1.6;
    margin: 16rpx 0;
    color: var(--text-primary);
  }

  .tag-row {
    display: flex;
    flex-wrap: wrap;
    gap: 12rpx;
    margin: 4rpx 0 16rpx;
  }

  .tag-chip {
    font-size: 22rpx;
    color: var(--color-primary-text);
    background: var(--bg-icon-soft);
    padding: 6rpx 18rpx;
    border-radius: 24rpx;
  }

  .image-grid {
    display: flex;
    flex-wrap: wrap;
    gap: 8rpx;
    margin: 16rpx 0;

    .grid-image {
      width: 200rpx;
      height: 200rpx;
      border-radius: 8rpx;
    }
  }

  .action-row {
    display: flex;
    gap: 48rpx;
    padding-top: 16rpx;
    border-top: 1rpx solid var(--border-faint);

    .action {
      display: flex;
      align-items: center;
      gap: 8rpx;
      /* 热区 ≥88rpx（44px）：原 48rpx 仅 24px 高，用户难以点中点赞/评论/心动 */
      min-height: 88rpx;
      /* 「心动」仅图标无文字，宽度 22px 不足 44px，补足可点击宽度 */
      min-width: 88rpx;

      .action-icon {
        font-size: 36rpx;
        color: var(--text-hint);
        line-height: 1;

        &.liked { color: var(--color-primary-text); }
        &.loved { color: var(--color-primary-text); }
      }

      .count {
        font-size: 24rpx;
        color: var(--text-hint);
      }
    }
  }
}

.loading {
  text-align: center;
  padding: 24rpx;
  color: var(--text-hint);
  font-size: 24rpx;
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

/* 消息：通知入口 */
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
        border-radius: 16rpx;
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

/* 消息：聊天列表 */
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
          border-radius: 16rpx;
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
  /* 撑满 notice-bar 下方 tab-view 的剩余高度，消除面板下方留白（与 .msg-list 同口径） */
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;

  .notice-panel-list {
    flex: 1;
    min-height: 0;
    /* 面板已撑满 tab-view 剩余高度：列表超长时自身滚动，避免内容被裁切不可见 */
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

</style>
