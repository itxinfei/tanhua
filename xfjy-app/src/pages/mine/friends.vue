<template>
  <view class="friends-page">
    <!-- 页面标题：custom 导航无系统返回键，返回键 + 标题均自绘；标题随当前 Tab 变化 -->
    <view
      class="page-header"
      :style="{ paddingTop: statusBarHeight + 10 + 'px' }"
    >
      <!-- 返回按钮必须用 a11y-button（全站按钮单一真源），图标用 UnoCSS Phosphor i-ph-*，热区 88rpx 满足 WCAG 2.5.5 -->
      <a11y-button
        class="back-btn"
        label="返回上一页"
        @tap="goBack"
      >
        <view
          class="i-ph-arrow-left back-icon"
          aria-hidden="true"
        />
      </a11y-button>
      <text class="page-title">
        {{ pageTitle }}
      </text>
    </view>

    <!-- 统计条：数据来自 GET /users/counts，点击切换对应 Tab -->
    <view
      class="stats-bar"
      role="tablist"
      aria-label="好友关系统计"
    >
      <a11y-button
        v-for="tab in TAB_LIST"
        :key="`stat-${tab.type}`"
        class="stat-item"
        :class="{ 'stat-active': activeType === tab.type }"
        role="tab"
        :checked="activeType === tab.type"
        :label="`${tab.label} ${counts[tab.type] ?? 0} 人，查看列表`"
        @tap="switchTab(tab.type)"
      >
        <text class="stat-num">
          {{ counts[tab.type] ?? 0 }}
        </text>
        <text class="stat-label">
          {{ tab.label }}
        </text>
      </a11y-button>
    </view>

    <!-- 搜索框：列表为空时隐藏（0 条数据下搜索无意义），避免"搜了还是空"的困惑 -->
    <view
      v-if="userList.length > 0 || loading"
      class="search-bar"
    >
      <input
        v-model="keyword"
        class="search-input"
        :placeholder="searchPlaceholder"
        :aria-label="searchPlaceholder"
        confirm-type="search"
        @confirm="handleSearch"
      >
    </view>

    <!-- 列表 -->
    <scroll-view
      class="list-scroll"
      scroll-y
      refresher-enabled
      :refresher-triggered="refreshing"
      @refresherrefresh="onRefresh"
      @scrolltolower="loadMore"
    >
      <empty-state
        v-if="userList.length === 0 && !loading && !loadError"
        :icon="emptyIcon"
        :title="emptyTitle"
        :desc="emptyDesc"
      >
        <!-- 空态 CTA：可点击按钮，直接去匹配页认识更多人（原为纯文本不可点） -->
        <a11y-button
          class="empty-cta"
          variant="primary"
          label="去匹配页认识新朋友"
          @tap="goMatch"
        >
          去匹配页认识新朋友
        </a11y-button>
      </empty-state>

      <!-- 首屏加载失败：与「真的没有好友」严格区分，给明确原因 + 可重试入口 -->
      <empty-state
        v-if="userList.length === 0 && !loading && loadError"
        role="alert"
        icon="info"
        title="暂时没能获取好友列表"
        desc="服务可能开小差了，检查网络后重试"
      >
        <a11y-button
          class="empty-cta"
          variant="primary"
          label="重新加载好友列表"
          @tap="loadList"
        >
          重新加载
        </a11y-button>
      </empty-state>

      <!-- 通用用户列表行：主体可点击进详情 + 右侧操作按钮 -->
      <user-list-item
        v-for="user in userList"
        :key="user.id"
        :avatar="resolveAvatar(user.avatar)"
        :nickname="user.nickname"
        :meta="activeType === FRIEND_TYPE.VISITOR && user.visitTime
          ? `${formatVisitTime(user.visitTime)}看过你`
          : `${user.gender === 'man' ? '男' : '女'} · ${user.age || '—'}岁${user.city ? ' · ' + user.city : ''}`"
        clickable
        @tap-item="goDetail(user.id)"
      >
        <template #action>
          <!-- 互相关注：直连聊天 -->
          <a11y-button
            v-if="activeType === FRIEND_TYPE.MUTUAL"
            variant="ghost"
            size="sm"
            :label="`发消息给 ${user.nickname || '该用户'}`"
            @tap="goChat(user)"
          >
            <text class="i-ph-chat-circle-dots action-ico" />
            <text>发消息</text>
          </a11y-button>
          <!-- 我喜欢：取消喜欢 -->
          <a11y-button
            v-else-if="activeType === FRIEND_TYPE.FOLLOW"
            variant="ghost"
            size="sm"
            :label="`取消喜欢 ${user.nickname || '该用户'}`"
            @tap="handleCancelLike(user.id)"
          >
            <text>取消喜欢</text>
          </a11y-button>
          <!-- 粉丝：喜欢 TA（若对方也喜欢我则触发配对庆祝） -->
          <a11y-button
            v-else-if="activeType === FRIEND_TYPE.FANS"
            variant="primary"
            size="sm"
            :loading="likingId === user.id"
            :label="`喜欢 ${user.nickname || '该用户'}`"
            @tap="handleFollowFan(user.id, user.avatar)"
          >
            <text>喜欢 TA</text>
          </a11y-button>
        </template>
      </user-list-item>

      <view
        v-if="loading"
        class="loading-tip"
      >
        <text>加载中…</text>
      </view>
      <view
        v-if="!hasMore && userList.length > 0"
        class="loading-tip"
      >
        <text>没有更多了</text>
      </view>
    </scroll-view>

    <!-- 配对成功庆祝：喜欢粉丝且对方也喜欢我时弹出 -->
    <match-celebrate
      :visible="celebrate.visible"
      :my-avatar="celebrate.myAvatar"
      :other-avatar="celebrate.otherAvatar"
      @close="celebrate.visible = false"
      @chat="handleCelebrateChat"
    />
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from "vue";
import EmptyState from "@/components/empty-state.vue";
import { onLoad } from "@dcloudio/uni-app";
import A11yButton from "@/components/a11y-button.vue";
import UserListItem from "@/components/user-list-item.vue";
import MatchCelebrate from "@/components/match-celebrate.vue";
import {
  getFriendList,
  getRelationCounts,
  cancelLike,
  followFan,
  FRIEND_TYPE,
  type FriendUser,
} from "@/api/mine";
import { info, success, fail, confirm } from "@/utils/ux";
import { resolveAvatar } from "@/utils/avatar";
import { safeGoBack } from "@/utils/nav";
import { useUserStore } from "@/store/user";

const userStore = useUserStore();

/** 状态栏高度（custom 导航自绘头部需避让状态栏；H5 无状态栏时取兜底值） */
const statusBarHeight = ref(20);
try {
  statusBarHeight.value = uni.getWindowInfo().statusBarHeight || 20;
} catch {
  statusBarHeight.value = 20;
}

/** 返回上一页（直接打开无上级页面时兜底回「我的」Tab） */
function goBack(): void {
  safeGoBack("/pages/mine/index");
}

/** Tab 配置（key 与 counts 字段对应） */
const TAB_LIST = [
  { type: FRIEND_TYPE.MUTUAL, label: "互相关注", countKey: "eachLoveCount" },
  { type: FRIEND_TYPE.FOLLOW, label: "我喜欢", countKey: "loveCount" },
  { type: FRIEND_TYPE.FANS, label: "粉丝", countKey: "fanCount" },
  { type: FRIEND_TYPE.VISITOR, label: "谁看过我", countKey: "visitorCount" },
] as const;

/** 默认分页大小 */
const DEFAULT_PAGE_SIZE = 10;

const activeType = ref<string>(FRIEND_TYPE.MUTUAL);
const keyword = ref("");
const userList = ref<FriendUser[]>([]);
const currentPage = ref(1);
const hasMore = ref(true);
const loading = ref(false);
/** 首屏加载失败标记（展示错误态，避免误显示空态） */
const loadError = ref(false);
/** 下拉刷新中状态（驱动 scroll-view refresher） */
const refreshing = ref(false);
/** 正在「喜欢」中的用户 id（防重复点击，驱动按钮 loading 态） */
const likingId = ref<string>("");
/** 关系统计（GET /users/counts） */
const counts = ref<Record<string, number>>({});
/** 配对成功庆祝弹窗状态 */
const celebrate = ref<{ visible: boolean; myAvatar: string; otherAvatar: string; userId: string; nickname: string }>({
  visible: false,
  myAvatar: "",
  otherAvatar: "",
  userId: "",
  nickname: "",
});

/** 页面标题：根据当前 Tab 动态显示 */
const pageTitle = computed(() => {
  const tab = TAB_LIST.find((t) => t.type === activeType.value);
  return tab?.label || "好友关系";
});

/** 搜索框占位文案：根据当前 Tab 动态显示 */
const searchPlaceholder = computed(() => {
  const tab = TAB_LIST.find((t) => t.type === activeType.value);
  return `按昵称搜索${tab?.label || "好友"}`;
});

/** 空状态配置：根据当前 Tab 动态显示 */
const emptyIcon = computed(() => {
  switch (activeType.value) {
    case FRIEND_TYPE.MUTUAL:
      return "heart";
    case FRIEND_TYPE.FOLLOW:
      return "heart";
    case FRIEND_TYPE.FANS:
      return "person";
    case FRIEND_TYPE.VISITOR:
      return "eye";
    default:
      return "person";
  }
});

const emptyTitle = computed(() => {
  const tab = TAB_LIST.find((t) => t.type === activeType.value);
  return `暂无${tab?.label || "数据"}`;
});

const emptyDesc = computed(() => {
  switch (activeType.value) {
    case FRIEND_TYPE.MUTUAL:
      return "主动打个招呼，缘分就从此刻开始";
    case FRIEND_TYPE.FOLLOW:
      return "去认识更多有趣的人吧";
    case FRIEND_TYPE.FANS:
      return "分享你的动态，吸引更多人关注你";
    case FRIEND_TYPE.VISITOR:
      return "完善资料，让更多人看到你";
    default:
      return "去认识更多有趣的人吧";
  }
});

/** 空态 CTA：跳到匹配 tab（tabBar 页必须用 switchTab） */
function goMatch(): void {
  uni.switchTab({ url: "/pages/match/index" });
}

/** 页面加载时读取传入的 type 参数 */
onLoad((options) => {
  if (options?.type) {
    activeType.value = options.type;
  }
});

onMounted(() => {
  loadList();
  loadCounts();
});

/** 切换 Tab */
function switchTab(type: string): void {
  if (activeType.value === type) return;
  activeType.value = type;
  keyword.value = "";
  loadList();
}

/** 加载关系统计（失败不阻断页面） */
async function loadCounts(): Promise<void> {
  try {
    const c = await getRelationCounts();
    counts.value = {
      [FRIEND_TYPE.MUTUAL]: c.eachLoveCount || 0,
      [FRIEND_TYPE.FOLLOW]: c.loveCount || 0,
      [FRIEND_TYPE.FANS]: c.fanCount || 0,
      [FRIEND_TYPE.VISITOR]: c.visitorCount || 0,
    };
  } catch {
    // 统计失败不阻断列表浏览，错误提示由 request 封装统一处理
  }
}

/** 加载序号：切 Tab/搜索并发时，仅最新一次请求的响应允许写入列表（防滞后响应覆盖） */
let loadSeq = 0;

/** 加载列表（首页） */
async function loadList(): Promise<void> {
  const seq = ++loadSeq;
  loading.value = true;
  loadError.value = false;
  try {
    const result = await getFriendList(
      activeType.value,
      { page: 1, pagesize: DEFAULT_PAGE_SIZE },
      keyword.value || undefined
    );
    // 响应返回时已切换到更新的请求（切Tab/搜索），丢弃本次结果
    if (seq !== loadSeq) return;
    userList.value = result.items || [];
    currentPage.value = 1;
    hasMore.value = (result.items?.length || 0) >= DEFAULT_PAGE_SIZE;
  } catch {
    // 首屏失败标记：页面据此显示错误态+重试，而非误报「暂无数据」
    if (seq === loadSeq) loadError.value = true;
  } finally {
    if (seq === loadSeq) loading.value = false;
  }
}

/** 下拉刷新：重新加载当前 Tab 首页 */
async function onRefresh(): Promise<void> {
  refreshing.value = true;
  try {
    await Promise.all([loadList(), loadCounts()]);
  } finally {
    refreshing.value = false;
  }
}

/** 加载更多（上拉） */
async function loadMore(): Promise<void> {
  if (loading.value || !hasMore.value) return;
  const seq = loadSeq;
  loading.value = true;
  try {
    const nextPage = currentPage.value + 1;
    const result = await getFriendList(
      activeType.value,
      { page: nextPage, pagesize: DEFAULT_PAGE_SIZE },
      keyword.value || undefined
    );
    // 翻页期间发生了切Tab/刷新（序号已变），丢弃本次结果
    if (seq !== loadSeq) return;
    userList.value.push(...(result.items || []));
    currentPage.value = nextPage;
    hasMore.value = (result.items?.length || 0) >= DEFAULT_PAGE_SIZE;
  } catch {
    // 翻页失败保留 hasMore=true，用户可再次上拉重试；给明确反馈避免静默
    fail("加载失败，请上拉重试");
  } finally {
    if (seq === loadSeq) loading.value = false;
  }
}

/** 搜索 */
function handleSearch(): void {
  loadList();
}

/** 取消喜欢（M-A5：破坏性操作先确认，对齐黑名单移除交互） */
async function handleCancelLike(userId: string): Promise<void> {
  const ok = await confirm("确认取消喜欢？取消后需重新喜欢对方");
  if (!ok) return;
  try {
    await cancelLike(userId);
    userList.value = userList.value.filter((user) => user.id !== userId);
    counts.value[FRIEND_TYPE.FOLLOW] = Math.max(0, (counts.value[FRIEND_TYPE.FOLLOW] || 0) - 1);
    info("已取消喜欢");
  } catch {
    // 错误提示由 request 封装统一处理
  }
}

/** 喜欢粉丝：成功且对方也喜欢我 → 弹配对庆祝；否则移出列表 */
async function handleFollowFan(userId: string, avatar: string): Promise<void> {
  if (likingId.value) return;
  likingId.value = userId;
  try {
    const result = await followFan(userId);
    userList.value = userList.value.filter((user) => user.id !== userId);
    counts.value[FRIEND_TYPE.FANS] = Math.max(0, (counts.value[FRIEND_TYPE.FANS] || 0) - 1);
    if (result?.matched) {
      // 配对成功：弹庆祝，计数联动（互关 +1）
      counts.value[FRIEND_TYPE.MUTUAL] = (counts.value[FRIEND_TYPE.MUTUAL] || 0) + 1;
      celebrate.value = {
        visible: true,
        myAvatar: userStore.userInfo?.avatar || "",
        otherAvatar: result.user?.avatar || avatar,
        userId: result.user?.id != null ? String(result.user.id) : userId,
        nickname: result.user?.nickname || "",
      };
    } else {
      success("已喜欢");
    }
  } catch {
    // 错误提示由 request 封装统一处理
  } finally {
    likingId.value = "";
  }
}

/** 庆祝弹窗「打个招呼」：进聊天 */
function handleCelebrateChat(): void {
  const { userId, nickname } = celebrate.value;
  celebrate.value.visible = false;
  uni.navigateTo({ url: `/pages/chat/chat?userId=${userId}&nickname=${encodeURIComponent(nickname)}` });
}

/** 相对时间：x 分钟前 / x 小时前 / 昨天 / x 天前 / x 个月前 */
function formatVisitTime(ts?: number): string {
  if (!ts) return "";
  const diff = Date.now() - ts;
  const min = Math.floor(diff / 60000);
  if (min < 1) return "刚刚";
  if (min < 60) return `${min} 分钟前`;
  const hour = Math.floor(min / 60);
  if (hour < 24) return `${hour} 小时前`;
  const day = Math.floor(hour / 24);
  if (day === 1) return "昨天";
  if (day < 30) return `${day} 天前`;
  const month = Math.floor(day / 30);
  if (month < 12) return `${month} 个月前`;
  return `${Math.floor(month / 12)} 年前`;
}

/** 跳转用户详情 */
function goDetail(userId: string): void {
  uni.navigateTo({ url: `/pages/match/detail?id=${userId}` });
}

/** 互相关注 → 直连聊天 */
function goChat(user: FriendUser): void {
  uni.navigateTo({ url: `/pages/chat/chat?userId=${user.id}&nickname=${encodeURIComponent(user.nickname || "")}` });
}
</script>

<style lang="scss" scoped>
.friends-page {
  height: calc(100vh - var(--window-bottom, 0px));
  display: flex;
  flex-direction: column;
  background: var(--bg-sub);
}

.page-header {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24rpx 32rpx;
  background: var(--bg-card);

  .page-title {
    font-size: 36rpx;
    font-weight: bold;
    color: var(--text-primary);
  }

  /* 自绘返回按钮：88rpx 热区（WCAG 2.5.5），左对齐 */
  .back-btn {
    position: absolute;
    left: 16rpx;
    width: 88rpx;
    height: 88rpx;
    display: flex;
    align-items: center;
    justify-content: center;

    .back-icon {
      font-size: 48rpx;
      color: var(--text-primary);
      line-height: 1;
    }
  }
}

/* 统计条：数字在上、标签在下，选中项高亮主色；高度统一走 --category-bar-h */
.stats-bar {
  display: flex;
  height: var(--category-bar-h);
  background: var(--bg-card);
  padding: 0 24rpx;
  border-bottom: 1rpx solid var(--border-light);

  .stat-item {
    position: relative;
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 4rpx;
    padding: 0;
    background: transparent;

    .stat-num {
      font-size: 32rpx;
      font-weight: bold;
      color: var(--text-secondary);
      font-variant-numeric: tabular-nums;
    }

    .stat-label {
      font-size: 22rpx;
      color: var(--text-hint);
    }

    &.stat-active {
      .stat-num,
      .stat-label {
        color: var(--color-primary);
      }

      /* 选中态下划线指示：宽度 48rpx、高 8rpx（4px 栅格），圆角取 --radius-pill */
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
}

.search-bar {
  padding: 16rpx 32rpx;
  background: var(--bg-card);

  .search-input {
    /* 热区 ≥88rpx（44px）：原 64rpx 仅 32px，输入框难以点中聚焦 */
    min-height: 88rpx;
    background: var(--bg-sub);
    border-radius: 44rpx;
    padding: 0 32rpx;
    font-size: 26rpx;
  }
}

/* 空态 CTA：与 empty-state 描述拉开距离，保证可点 */
.empty-cta {
  margin-top: 36rpx;
  padding: 0 56rpx;
  min-height: 88rpx;
  border-radius: 44rpx;
}

.list-scroll {
  flex: 1 1 auto;
  /* min-height:0 必须：纵向 flex 子项默认 min-height:auto，scroll-view 内容超高会撑破
     固定高度容器，导致列表无法滚动（与 kefu/index.vue 同范式） */
  min-height: 0;
  /* min-width:0 防止 flex 子项被内部宽卡片撑破，保证右侧 24rpx 留白不溢出屏幕 */
  min-width: 0;
  width: 100%;
  box-sizing: border-box;
  overflow-x: hidden;
  padding: 20rpx 24rpx;
  padding-bottom: calc(var(--window-bottom, 0px) + 24rpx);
}

/* 操作按钮图标间距 */
.action-ico {
  margin-right: 6rpx;
}

.loading-tip {
  display: flex;
  justify-content: center;
  padding: 24rpx 0;

  /* #ccc ≈1.6:1 → #767676 */
  text {
    font-size: 24rpx;
    color: var(--text-hint);
  }
}
</style>
