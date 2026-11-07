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
      <!-- 首屏加载失败：错误态 + 重试，避免误显示「黑名单为空」 -->
      <view
        v-if="loadError && userList.length === 0 && !loading"
        class="error-state"
      >
        <text class="error-text">
          加载失败，请检查网络后重试
        </text>
        <a11y-button
          variant="ghost"
          label="重试加载黑名单"
          @tap="loadList"
        >
          <text>重试</text>
        </a11y-button>
      </view>

      <empty-state
        v-if="!loadError && userList.length === 0 && !loading"
        title="黑名单为空"
        desc="被拉黑的用户将无法与你互动"
      />

      <!-- 通用用户列表行：主体信息 + 右侧操作按钮。
           clickable 让行主体可进详情作为主热区，右侧 sm 按钮才符合 §7.1 例外 -->
      <user-list-item
        v-for="user in userList"
        :key="user.id"
        :avatar="resolveAvatar(user.avatar)"
        :nickname="user.nickname"
        :meta="`${user.gender === 'man' ? '男' : user.gender === 'woman' ? '女' : '未知'} · ${user.age || '—'}岁`"
        clickable
        @tap-item="goDetail(user.id)"
      >
        <template #action>
          <a11y-button
            variant="ghost"
            size="sm"
            :label="`将 ${user.nickname || '该用户'} 移出黑名单`"
            @tap="handleRemove(user.id)"
          >
            <text>移除</text>
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
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue";
import EmptyState from "@/components/empty-state.vue";
import A11yButton from "@/components/a11y-button.vue";
import UserListItem from "@/components/user-list-item.vue";
import {
  getBlacklist,
  removeFromBlacklist,
  type UserProfile,
} from "@/api/mine";
import { success, fail } from "@/utils/ux";
import { resolveAvatar } from "@/utils/avatar";

/** 默认分页大小 */
const DEFAULT_PAGE_SIZE = 10;

const userList = ref<UserProfile[]>([]);
const currentPage = ref(1);
const hasMore = ref(true);
const loading = ref(false);
/** 首屏加载失败标记（展示错误态，避免误显示空态） */
const loadError = ref(false);
/** 下拉刷新中状态（驱动 scroll-view refresher） */
const refreshing = ref(false);

onMounted(() => {
  loadList();
});

/** 加载黑名单列表（首页） */
async function loadList(): Promise<void> {
  if (loading.value) return;
  loading.value = true;
  loadError.value = false;
  try {
    const result = await getBlacklist({ page: 1, pagesize: DEFAULT_PAGE_SIZE });
    userList.value = result.items || [];
    currentPage.value = 1;
    hasMore.value = (result.items?.length || 0) >= DEFAULT_PAGE_SIZE;
  } catch {
    loadError.value = true;
    // 错误提示由 request 封装统一处理
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
  } catch {
    // 翻页失败保留 hasMore=true，用户可再次上拉重试；给明确反馈避免静默
    fail("加载失败，请上拉重试");
  } finally {
    loading.value = false;
  }
}

/** 跳转用户详情（行主体主热区） */
function goDetail(userId: string): void {
  uni.navigateTo({ url: `/pages/match/detail?id=${userId}` });
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
        // 当前页被清空且后面还有页时回退上一页，避免误现空态
        if (userList.value.length === 0 && hasMore.value && currentPage.value > 1) {
          await loadPrevPage();
        }
        success("已移除");
      } catch {
        // 错误提示由 request 封装统一处理
      }
    },
  });
}

/** 回退加载上一页（整页替换） */
async function loadPrevPage(): Promise<void> {
  currentPage.value -= 1;
  loading.value = true;
  try {
    const result = await getBlacklist({ page: currentPage.value, pagesize: DEFAULT_PAGE_SIZE });
    userList.value = result.items || [];
    hasMore.value = (result.items?.length || 0) >= DEFAULT_PAGE_SIZE;
  } catch {
    // 错误提示由 request 封装统一处理
  } finally {
    loading.value = false;
  }
}
</script>

<style lang="scss" scoped>
.blacklist-page {
  /* 原生导航页：必须同时减去导航栏与全局 tabbar，否则底部被遮挡 44px */
  height: calc(100vh - var(--window-top, 0px) - var(--tab-bar-height, 0px));
  background: var(--bg-sub);
}

.list-scroll {
  height: 100%;
  /* 与 friends 页对齐：防止内部宽卡片撑破容器导致右侧留白丢失 */
  min-width: 0;
  width: 100%;
  box-sizing: border-box;
  overflow-x: hidden;
  padding: 20rpx 24rpx;
}

.loading-tip {
  display: flex;
  justify-content: center;
  padding: 24rpx 0;

  /* #ccc → #767676 */
  text {
    font-size: 24rpx;
    color: var(--text-hint);
  }
}

.error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 24rpx;
  padding: 120rpx 0;

  .error-text {
    font-size: 26rpx;
    color: var(--text-hint);
  }
}
</style>
