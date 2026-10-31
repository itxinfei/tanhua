<template>
  <view class="blacklist-page">
    <!-- 列表 -->
    <scroll-view
      class="list-scroll"
      scroll-y
      refresher-enabled
      :refresher-triggered="refreshing"
      @refresherrefresh="onRefresh"
      @scrolltolower="loadMore"
    >
      <empty-state title="黑名单为空" desc="被拉黑的用户将无法与你互动" />

      <!-- 行容器：信息区与操作按钮分离，避免可聚焦控件嵌套 -->
      <view v-for="user in userList" :key="user.id" class="user-row">
        <view class="user-item shadow-card">
          <image
            class="user-avatar"
            :src="user.avatar || '/static/logo/logo-icon-transparent.png'"
            mode="aspectFill"
            lazy-load
            :aria-label="`${user.nickname || '未设置昵称'} 的头像`"
          />
          <view class="user-info">
            <text class="user-nickname">{{ user.nickname || '未设置昵称' }}</text>
            <text class="user-meta">
              {{ user.gender === 'man' ? '男' : '女' }} · {{ user.age || '—' }}岁
            </text>
          </view>
        </view>
        <a11y-button class="action-btn btn-feedback" :label="`将 ${user.nickname || '该用户'} 移出黑名单`" @tap="handleRemove(user.id)">
          <text>移除</text>
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
import A11yButton from "@/components/a11y-button.vue";
import {
  getBlacklist,
  removeFromBlacklist,
  type UserProfile,
} from "@/api/mine";

/** 默认分页大小 */
const DEFAULT_PAGE_SIZE = 10;

const userList = ref<UserProfile[]>([]);
const currentPage = ref(1);
const hasMore = ref(true);
const loading = ref(false);
/** 下拉刷新中状态（驱动 scroll-view refresher） */
const refreshing = ref(false);

onMounted(() => {
  loadList();
});

/** 加载黑名单列表（首页） */
async function loadList(): Promise<void> {
  if (loading.value) return;
  loading.value = true;
  try {
    const result = await getBlacklist({ page: 1, pagesize: DEFAULT_PAGE_SIZE });
    userList.value = result.items || [];
    currentPage.value = 1;
    hasMore.value = (result.items?.length || 0) >= DEFAULT_PAGE_SIZE;
  } finally {
    loading.value = false;
  }
}

/** 下拉刷新：重新加载黑名单首页 */
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
    const result = await getBlacklist({
      page: nextPage,
      pagesize: DEFAULT_PAGE_SIZE,
    });
    userList.value.push(...(result.items || []));
    currentPage.value = nextPage;
    hasMore.value = (result.items?.length || 0) >= DEFAULT_PAGE_SIZE;
  } finally {
    loading.value = false;
  }
}

/** 移除黑名单 */
async function handleRemove(userId: string): Promise<void> {
  uni.showModal({
    title: "提示",
    content: "确定将 TA 移出黑名单吗？",
    success: async (res) => {
      if (!res.confirm) return;
      try {
        await removeFromBlacklist(userId);
        userList.value = userList.value.filter((user) => user.id !== userId);
        uni.showToast({ title: "已移除", icon: "success" });
      } catch {
        // 错误提示由 request 封装统一处理
      }
    },
  });
}
</script>

<style lang="scss" scoped>
.blacklist-page {
  height: 100vh;
  background: #f5f5f5;
}

.list-scroll {
  height: 100%;
  padding: 20rpx 24rpx;
}

.user-row { display: flex; align-items: center; gap: 16rpx; margin-top: 16rpx;
  &:first-child { margin-top: 0; }
}

  .user-item {
    flex: 1;
    display: flex;
    align-items: center;
    background: #fff;
    border-radius: 20rpx;
    padding: 24rpx;

  .user-avatar {
    width: 96rpx;
    height: 96rpx;
    border-radius: 50%;
    background: #f0f0f0;
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

    /* #999 → #767676 */
    .user-meta {
      font-size: 24rpx;
      color: #767676;
      margin-top: 8rpx;
      display: block;
    }
  }
}

/* 触摸目标 ≥24px */
.action-btn {
  font-size: 24rpx;
  padding: 16rpx 32rpx;
  min-height: 48rpx;
  border-radius: 28rpx;
  /* #ff5e9c on #ffeff5 ≈2.6:1 不达标 → 提深至 #c2185b（≈5.4:1） */
  color: #c2185b;
  background: #ffeff5;
  font-weight: 500;
  flex-shrink: 0;
}

.loading-tip {
  display: flex;
  justify-content: center;
  padding: 24rpx 0;

  /* #ccc → #767676 */
  text {
    font-size: 24rpx;
    color: #767676;
  }
}
</style>