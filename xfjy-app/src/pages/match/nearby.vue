<template>
  <view class="nearby-page">
    <!-- 筛选栏 -->
    <view class="filter-bar" role="radiogroup" aria-label="筛选条件">
      <a11y-button class="filter-item btn-feedback" :class="{ active: distance === d }" v-for="d in distances" :key="d" role="radio" :checked="distance === d" :label="d === 0 ? '距离不限' : `距离 ${d} 公里以内`" @tap="distance = d">
        <text>{{ d === 0 ? '全部' : d + 'km' }}</text>
      </a11y-button>
      <a11y-button class="filter-item gender btn-feedback" :class="{ active: gender !== '全部' }" role="radio" :checked="gender === '女'" :label="`性别筛选：${gender}`" @tap="gender = gender === '女' ? '全部' : '女'">
        <text>{{ gender }}</text>
      </a11y-button>
    </view>

    <!-- 定位未授权提示 -->
    <view v-if="!hasLocationAuth" class="location-tip" role="alert">
      <view class="i-ph-map-pin location-tip__ico" aria-hidden="true" />
      <text class="location-tip__text">未开启定位，可能看不到附近的人</text>
      <a11y-button class="location-tip__btn" label="开启定位" @tap="requestLocation">开启</a11y-button>
    </view>

    <!-- 用户列表 -->
    <scroll-view
      class="user-list"
      scroll-y
      @scrolltolower="onLoadMore"
      :lower-threshold="80"
    >
      <user-list-item
        v-for="user in userList"
        :key="user.userId"
        :avatar="resolveAvatar(user.avatar)"
        :nickname="user.nickname"
        :age="user.age"
        clickable
        @tap-item="goDetail(user.userId)"
      >
        <template #meta>
          <view v-if="user.city" class="distance-tag">
            <view class="i-ph-map-pin distance-tag__ico" aria-hidden="true" />
            <text>{{ user.city }}</text>
          </view>
        </template>
        <template #action>
          <a11y-button
            class="like-btn btn-feedback"
            :label="`喜欢 ${user.nickname || '该用户'}`"
            @tap="handleQuickLike(user.userId)"
          >
            <view class="i-ph-heart like-btn__ico" aria-hidden="true" />
          </a11y-button>
        </template>
      </user-list-item>

      <!-- 加载更多 -->
      <view v-if="loadingMore" class="load-more">
        <text>加载中...</text>
      </view>
      <view v-else-if="!hasMore && userList.length > 0" class="load-more">
        <text>没有更多了</text>
      </view>

      <!-- 骨架 -->
      <skeleton v-if="userList.length === 0 && loading" variant="feed" />

      <!-- 空状态 -->
      <empty-state
        v-if="userList.length === 0 && !loading"
        icon="location"
        title="附近暂无用户"
        desc="扩大搜索范围或稍后再来看看"
      />
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, watch } from "vue";
import EmptyState from "@/components/empty-state.vue";
import Skeleton from "@/components/skeleton.vue";
import { onShow, onPullDownRefresh } from "@dcloudio/uni-app";
import A11yButton from "@/components/a11y-button.vue";
import UserListItem from "@/components/user-list-item.vue";
import { getNearby, loveUser, type NearUser } from "@/api/match";
import { success, info } from "@/utils/ux";
import { resolveAvatar } from "@/utils/avatar";

/** 距离筛选选项（km，0 表示全部） */
const DISTANCE_OPTIONS = [0, 1, 3, 5, 10];
/** "全部"距离对应的搜索半径（米），取 50km 覆盖全城 */
const ALL_DISTANCE_METER = 50000;

const distances = DISTANCE_OPTIONS;
const distance = ref(0);
const gender = ref("全部");
const userList = ref<NearUser[]>([]);
const loading = ref(false);
const loadingMore = ref(false);
const hasMore = ref(true);

/** 分页（后端接口不分页，前端缓存全量数据做 slice 分页） */
const page = ref(1);
const pageSize = 10;
/** 后端返回的全量数据缓存，loadMore 从此缓存截取，避免重复请求 */
const allItemsCache = ref<NearUser[]>([]);

/** 定位权限状态 */
const hasLocationAuth = ref(true);

/** 请求序号：筛选连续切换时，仅最后一次请求的响应允许写入列表（防竞态） */
let loadSeq = 0;

/** 跳转用户详情 */
function goDetail(id: number): void {
  uni.navigateTo({ url: `/pages/match/detail?id=${id}` });
}

/** 快速喜欢 */
async function handleQuickLike(id: number): Promise<void> {
  try {
    const matched = await loveUser(id);
    success(matched ? "互相喜欢，匹配成功！" : "已喜欢");
  } catch {
    // 请求失败由 request 层统一 toast
  }
}

/** 加载附近用户（允许重入，过期响应丢弃） */
async function loadNearby(isRefresh = false): Promise<void> {
  const seq = ++loadSeq;
  if (isRefresh) {
    page.value = 1;
    hasMore.value = true;
    loading.value = true;
  }
  try {
    const genderParam = gender.value === "女" ? "woman" : undefined;
    const distanceMeter = distance.value === 0 ? ALL_DISTANCE_METER : distance.value * 1000;
    const list = await getNearby(genderParam, distanceMeter);
    if (seq !== loadSeq) return;

    const allItems = list || [];
    allItemsCache.value = allItems;

    if (isRefresh || page.value === 1) {
      userList.value = allItems.slice(0, pageSize);
      hasMore.value = allItems.length > pageSize;
    }
  } catch {
    // 请求失败由 request 层统一 toast
  } finally {
    if (seq === loadSeq) {
      loading.value = false;
    }
  }
}

/** 加载更多（从缓存的全量数据中截取下一页，不重复请求接口） */
async function onLoadMore(): Promise<void> {
  if (loadingMore.value || !hasMore.value) return;
  loadingMore.value = true;
  try {
    // 模拟异步延迟，让加载态可见
    await new Promise((resolve) => setTimeout(resolve, 300));
    const allItems = allItemsCache.value;
    const nextPage = page.value + 1;
    const start = (nextPage - 1) * pageSize;
    const end = nextPage * pageSize;
    const nextItems = allItems.slice(start, end);
    userList.value = [...userList.value, ...nextItems];
    page.value = nextPage;
    hasMore.value = end < allItems.length;
  } finally {
    loadingMore.value = false;
  }
}

/** 筛选条件变化时重新加载 */
watch([distance, gender], () => loadNearby(true));

/** 请求定位权限 */
function requestLocation(): void {
  uni.getLocation({
    type: "wgs84",
    success: () => {
      hasLocationAuth.value = true;
      loadNearby(true);
    },
    fail: () => {
      info("请在系统设置中开启定位权限");
    },
  });
}

/** 检查定位权限（不触发系统弹窗，仅读取当前授权状态） */
function checkLocationAuth(): void {
  // 用 getSetting 检查权限状态，不主动触发定位请求
  uni.getSetting({
    success: (res) => {
      const auth = res.authSetting?.["scope.userLocation"];
      hasLocationAuth.value = auth !== false;
    },
    fail: () => {
      hasLocationAuth.value = true;
    },
  });
}

onShow(() => {
  checkLocationAuth();
  loadNearby(true);
});

/** 下拉刷新：重新加载附近用户 */
onPullDownRefresh(async () => {
  try {
    await loadNearby(true);
  } finally {
    uni.stopPullDownRefresh();
  }
});
</script>

<style lang="scss" scoped>
/* 纵向 flex：筛选栏与定位提示固定，列表吸收剩余高度。
   原先 .user-list 写死 calc(100vh - 112rpx)，未计入定位提示条与
   安全区，提示出现时列表底部被裁切、小屏更明显。 */
.nearby-page { display: flex; flex-direction: column; min-height: 100vh; background: var(--bg-page); }

/* ===== 定位提示 ===== */
.location-tip { display: flex; align-items: center; gap: 12rpx; padding: 20rpx 32rpx; background: var(--bg-icon-soft); border-bottom: 1rpx solid var(--border-faint);
  .location-tip__ico { width: 32rpx; height: 32rpx; color: var(--color-primary); flex-shrink: 0; }
  .location-tip__text { flex: 1; font-size: 26rpx; color: var(--text-secondary); }
  .location-tip__btn { font-size: 24rpx; color: var(--color-primary); font-weight: 600; padding: 8rpx 20rpx; border: 2rpx solid var(--color-primary); border-radius: var(--radius-pill); }
}

/* ===== 筛选栏（探探式：左距离胶囊组，右性别筛选） ===== */
.filter-bar { display: flex; align-items: center; gap: 12rpx; padding: 20rpx 24rpx; background: var(--bg-card); position: sticky; top: 0; z-index: 10; box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
  .filter-item { display: flex; align-items: center; justify-content: center; padding: 0 26rpx; min-height: 64rpx; border-radius: 32rpx; font-size: 24rpx; line-height: 1; color: var(--text-hint-strong); background: var(--bg-sub); transition: all 0.2s;
    &.active { background: var(--gradient-brand); color: var(--text-inverse); box-shadow: var(--shadow-primary); }
    &.gender.active { background: var(--gradient-brand); }
  }
  .gender { margin-left: auto; }
}

/* ===== 用户列表 ===== */
.user-list {
  padding: 20rpx 24rpx;
  /* 吸收剩余高度并允许自身滚动；min-height:0 防止内容撑破 flex 容器 */
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
}

/* 距离标签 */
.distance-tag { margin-top: 12rpx; display: inline-flex; align-items: center; gap: 6rpx; background: var(--bg-icon-soft); border-radius: 20rpx; padding: 6rpx 16rpx;
  text { font-size: 22rpx; color: var(--color-primary-text); font-weight: 500; }
  .distance-tag__ico { width: 22rpx; height: 22rpx; color: var(--color-primary-text); }
}

/* 喜欢按钮 */
.like-btn { width: 80rpx; height: 80rpx; border-radius: 50%; background: var(--gradient-brand); display: flex; align-items: center; justify-content: center; flex-shrink: 0; transition: all 0.2s; box-shadow: var(--shadow-primary);
  &:active { transform: scale(0.9); }
  .like-btn__ico { width: 40rpx; height: 40rpx; color: var(--text-inverse); }
}

/* 加载更多 */
.load-more { display: flex; justify-content: center; padding: 32rpx 0;
  text { font-size: 24rpx; color: var(--text-hint); }
}
</style>
