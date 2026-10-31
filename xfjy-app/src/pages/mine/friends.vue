<template>
  <view class="friends-page">
    <!-- Tab 切换（role=tablist/tab + aria-selected） -->
    <view class="tab-bar" role="tablist" aria-label="好友关系分类">
      <a11y-button
        v-for="tab in TAB_LIST"
        :key="tab.type"
        class="tab-item"
        role="tab"
        :checked="activeType === tab.type"
        :label="tab.label"
        @tap="switchTab(tab.type)"
      >
        <text>{{ tab.label }}</text>
      </a11y-button>
    </view>

    <!-- 搜索框 -->
    <view class="search-bar">
      <input
        class="search-input"
        v-model="keyword"
        placeholder="按昵称搜索"
        aria-label="按昵称搜索好友"
        confirm-type="search"
        @confirm="handleSearch"
      />
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
      <empty-state icon="person" title="暂无数据" desc="去探花频道发现更多有缘人" />

      <!-- 每行 = 一个可聚焦的列表项 + 独立操作按钮（避免可聚焦元素嵌套） -->
      <view v-for="user in userList" :key="user.id" class="user-row">
      <a11y-button
        class="user-item"
        :label="`查看 ${user.nickname || '未设置昵称'} 的个人主页`"
        @tap="goDetail(user.id)"
      >
        <image
          class="user-avatar"
          :src="user.avatar || '/static/logo/logo-icon-transparent.png'"
          mode="aspectFill"
          lazy-load
          aria-hidden="true"
        />
        <view class="user-info">
          <text class="user-nickname">{{ user.nickname || '未设置昵称' }}</text>
          <text class="user-meta">
            {{ user.gender === 'man' ? '男' : '女' }} · {{ user.age || '—' }}岁
            <template v-if="user.city"> · {{ user.city }}</template>
          </text>
        </view>
      </a11y-button>
      <!-- 操作按钮 -->
      <a11y-button
        v-if="activeType === FRIEND_TYPE.FOLLOW"
        class="action-btn cancel"
        :label="`取消喜欢 ${user.nickname || '该用户'}`"
        @tap="handleCancelLike(user.id)"
      >
        <text>取消喜欢</text>
      </a11y-button>
      <a11y-button
        v-else-if="activeType === FRIEND_TYPE.FANS"
        class="action-btn follow"
        :label="`喜欢 ${user.nickname || '该用户'}`"
        @tap="handleFollowFan(user.id)"
      >
        <text>喜欢 TA</text>
      </a11y-button>
      </view>

      <view v-if="loading" class="loading-tip">
        <text>加载中...</text>
      </view>
      <view v-if="!hasMore && userList.length > 0" class="loading-tip">
        <text>没有更多了</text>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue";
import EmptyState from "@/components/empty-state.vue";
import { onLoad } from "@dcloudio/uni-app";
import A11yButton from "@/components/a11y-button.vue";
import {
  getFriendList,
  cancelLike,
  followFan,
  FRIEND_TYPE,
  type UserProfile,
} from "@/api/mine";

/** Tab 配置 */
const TAB_LIST = [
  { type: FRIEND_TYPE.MUTUAL, label: "互相关注" },
  { type: FRIEND_TYPE.FOLLOW, label: "我喜欢" },
  { type: FRIEND_TYPE.FANS, label: "粉丝" },
  { type: FRIEND_TYPE.VISITOR, label: "谁看过我" },
];

/** 默认分页大小 */
const DEFAULT_PAGE_SIZE = 10;

const activeType = ref<string>(FRIEND_TYPE.MUTUAL);
const keyword = ref("");
const userList = ref<UserProfile[]>([]);
const currentPage = ref(1);
const hasMore = ref(true);
const loading = ref(false);
/** 下拉刷新中状态（驱动 scroll-view refresher） */
const refreshing = ref(false);

/** 页面加载时读取传入的 type 参数 */
onLoad((options) => {
  if (options?.type) {
    activeType.value = options.type;
  }
});

onMounted(() => {
  loadList();
});

/** 切换 Tab */
function switchTab(type: string): void {
  if (activeType.value === type) return;
  activeType.value = type;
  keyword.value = "";
  loadList();
}

/** 加载列表（首页） */
async function loadList(): Promise<void> {
  if (loading.value) return;
  loading.value = true;
  try {
    const result = await getFriendList(
      activeType.value,
      { page: 1, pagesize: DEFAULT_PAGE_SIZE },
      keyword.value || undefined
    );
    userList.value = result.items || [];
    currentPage.value = 1;
    hasMore.value = (result.items?.length || 0) >= DEFAULT_PAGE_SIZE;
  } finally {
    loading.value = false;
  }
}

/** 下拉刷新：重新加载当前 Tab 首页 */
async function onRefresh(): Promise<void> {
  refreshing.value = true;
  try {
    await loadList();
  } finally {
    refreshing.value = false;
  }
}

/** 加载更多（上拉） */
async function loadMore(): Promise<void> {
  if (loading.value || !hasMore.value) return;
  loading.value = true;
  try {
    const nextPage = currentPage.value + 1;
    const result = await getFriendList(
      activeType.value,
      { page: nextPage, pagesize: DEFAULT_PAGE_SIZE },
      keyword.value || undefined
    );
    userList.value.push(...(result.items || []));
    currentPage.value = nextPage;
    hasMore.value = (result.items?.length || 0) >= DEFAULT_PAGE_SIZE;
  } finally {
    loading.value = false;
  }
}

/** 搜索 */
function handleSearch(): void {
  loadList();
}

/** 取消喜欢 */
async function handleCancelLike(userId: string): Promise<void> {
  try {
    await cancelLike(userId);
    userList.value = userList.value.filter((user) => user.id !== userId);
    uni.showToast({ title: "已取消喜欢", icon: "none" });
  } catch {
    // 错误提示由 request 封装统一处理
  }
}

/** 喜欢粉丝 */
async function handleFollowFan(userId: string): Promise<void> {
  try {
    await followFan(userId);
    uni.showToast({ title: "已喜欢", icon: "success" });
  } catch {
    // 错误提示由 request 封装统一处理
  }
}

/** 跳转用户详情 */
function goDetail(userId: string): void {
  uni.navigateTo({ url: `/pages/match/detail?id=${userId}` });
}
</script>

<style lang="scss" scoped>
.friends-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5f5f5;
}

.tab-bar {
  display: flex;
  background: #fff;
  border-bottom: 1rpx solid #f0f0f0;

  .tab-item {
    flex: 1;
    height: 88rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 28rpx;
    color: #666;
    position: relative;

    &.active {
      color: #ff5e9c;
      font-weight: bold;

      &::after {
        content: "";
        position: absolute;
        bottom: 0;
        left: 50%;
        transform: translateX(-50%);
        width: 48rpx;
        height: 6rpx;
        background: #ff5e9c;
        border-radius: 3rpx;
      }
    }
  }
}

.search-bar {
  padding: 16rpx 32rpx;
  background: #fff;

  .search-input {
    height: 64rpx;
    background: #f5f5f5;
    border-radius: 32rpx;
    padding: 0 32rpx;
    font-size: 26rpx;
  }
}

.list-scroll {
  flex: 1;
  padding: 20rpx 24rpx;
}

/* 行容器：列表项与操作按钮并列 */
.user-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 16rpx;
}

.user-item {
  flex: 1;
  display: flex;
  align-items: center;
  padding: 24rpx;
  background: #fff;
  border-radius: 24rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);

  .user-avatar {
    width: 100rpx;
    height: 100rpx;
    border-radius: 50%;
    background: #f0f0f0;
    border: 3rpx solid #ffeff5;
  }

  .user-info {
    flex: 1;
    margin-left: 24rpx;

    .user-nickname {
      font-size: 30rpx;
      color: #333;
      font-weight: bold;
      display: block;
    }

    /* 副信息 #999 → #767676 */
    .user-meta {
      font-size: 24rpx;
      color: #767676;
      margin-top: 8rpx;
      display: block;
    }
  }
}

/* 触摸目标 ≥24px：原 padding 12rpx 28rpx 在 24rpx 字号下偏小 */
.action-btn {
  font-size: 24rpx;
  padding: 16rpx 28rpx;
  min-height: 48rpx;
  border-radius: 28rpx;
  flex-shrink: 0;

  /* #999 on #f5f5f5 不达标 → 提深 */
  &.cancel {
    color: #767676;
    background: #f5f5f5;
  }

  &.follow {
    color: #fff;
    background: linear-gradient(135deg, var(--color-primary-deep), var(--color-primary));
    box-shadow: 0 4rpx 14rpx rgba(255, 94, 142, 0.3);
  }
}

.loading-tip {
  display: flex;
  justify-content: center;
  padding: 24rpx 0;

  /* #ccc ≈1.6:1 → #767676 */
  text {
    font-size: 24rpx;
    color: #767676;
  }
}
</style>