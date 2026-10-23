<template>
  <view class="blacklist-page">
    <!-- 列表 -->
    <scroll-view class="list-scroll" scroll-y @scrolltolower="loadMore">
      <view v-if="userList.length === 0 && !loading" class="empty">
        <text class="empty-text">黑名单为空</text>
      </view>

      <view v-for="user in userList" :key="user.id" class="user-item">
        <image
          class="user-avatar"
          :src="user.avatar || '/static/logo/logo-icon-transparent.png'"
          mode="aspectFill"
        />
        <view class="user-info">
          <text class="user-nickname">{{ user.nickname || '未设置昵称' }}</text>
          <text class="user-meta">
            {{ user.gender === 'man' ? '男' : '女' }} · {{ user.age || '—' }}岁
          </text>
        </view>
        <view class="action-btn" @click="handleRemove(user.id)">
          <text>移除</text>
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
  padding: 0 32rpx;
}

.user-item {
  display: flex;
  align-items: center;
  padding: 24rpx 0;
  border-bottom: 1rpx solid #f0f0f0;
  background: #fff;
  margin-top: 16rpx;
  border-radius: 20rpx;
  padding: 24rpx;

  &:first-child {
    margin-top: 0;
  }

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

    .user-meta {
      font-size: 24rpx;
      color: #999;
      margin-top: 8rpx;
      display: block;
    }
  }

  .action-btn {
    font-size: 24rpx;
    padding: 12rpx 32rpx;
    border-radius: 24rpx;
    color: #ff5e8e;
    background: #fff0f5;
  }
}

.empty {
  display: flex;
  justify-content: center;
  padding: 120rpx 0;

  .empty-text {
    font-size: 28rpx;
    color: #999;
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