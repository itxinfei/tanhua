<template>
  <view class="friends-page">
    <!-- Tab 切换 -->
    <view class="tab-bar">
      <view
        v-for="tab in TAB_LIST"
        :key="tab.type"
        class="tab-item"
        :class="{ active: activeType === tab.type }"
        @click="switchTab(tab.type)"
      >
        <text>{{ tab.label }}</text>
      </view>
    </view>

    <!-- 搜索框 -->
    <view class="search-bar">
      <input
        class="search-input"
        v-model="keyword"
        placeholder="按昵称搜索"
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
      <view v-if="userList.length === 0 && !loading" class="empty-state">
        <view class="empty-icon"><text>👥</text></view>
        <text class="empty-title">暂无数据</text>
        <text class="empty-desc">去探花频道发现更多有缘人</text>
      </view>

      <view
        v-for="user in userList"
        :key="user.id"
        class="user-item"
      >
        <image
          class="user-avatar"
          :src="user.avatar || '/static/logo/logo-icon-transparent.png'"
          mode="aspectFill"
          lazy-load
          @click="goDetail(user.id)"
        />
        <view class="user-info" @click="goDetail(user.id)">
          <text class="user-nickname">{{ user.nickname || '未设置昵称' }}</text>
          <text class="user-meta">
            {{ user.gender === 'man' ? '男' : '女' }} · {{ user.age || '—' }}岁
            <template v-if="user.city"> · {{ user.city }}</template>
          </text>
        </view>
        <!-- 操作按钮 -->
        <view
          v-if="activeType === FRIEND_TYPE.FOLLOW"
          class="action-btn cancel"
          @click="handleCancelLike(user.id)"
        >
          <text>取消喜欢</text>
        </view>
        <view
          v-else-if="activeType === FRIEND_TYPE.FANS"
          class="action-btn follow"
          @click="handleFollowFan(user.id)"
        >
          <text>喜欢 TA</text>
        </view>
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
import { onLoad } from "@dcloudio/uni-app";
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
      color: #ff5e8e;
      font-weight: bold;

      &::after {
        content: "";
        position: absolute;
        bottom: 0;
        left: 50%;
        transform: translateX(-50%);
        width: 48rpx;
        height: 6rpx;
        background: #ff5e8e;
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

.user-item {
  display: flex;
  align-items: center;
  padding: 24rpx;
  background: #fff;
  border-radius: 24rpx;
  margin-bottom: 16rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);

  .user-avatar {
    width: 100rpx;
    height: 100rpx;
    border-radius: 50%;
    background: #f0f0f0;
    border: 3rpx solid #fff0f5;
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

    .user-meta {
      font-size: 24rpx;
      color: #999;
      margin-top: 8rpx;
      display: block;
    }
  }

  .action-btn {
    font-size: 24rpx;
    padding: 12rpx 28rpx;
    border-radius: 28rpx;

    &.cancel {
      color: #999;
      background: #f5f5f5;
    }

    &.follow {
      color: #fff;
      background: linear-gradient(135deg, #ff5e8e, #ff8fab);
      box-shadow: 0 4rpx 14rpx rgba(255, 94, 142, 0.3);
    }
  }
}

.loading-tip {
  display: flex;
  justify-content: center;
  padding: 24rpx 0;

  text {
    font-size: 24rpx;
    color: #ccc;
  }
}
</style>