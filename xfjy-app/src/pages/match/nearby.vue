<template>
  <view class="nearby-page">
    <!-- 筛选栏 -->
    <view class="filter-bar">
      <view class="filter-item btn-feedback" v-for="d in distances" :key="d" :class="{ active: distance === d }" @click="distance = d">
        <text>{{ d === 0 ? '全部' : d + 'km' }}</text>
      </view>
      <view class="filter-item gender btn-feedback" :class="{ active: gender === '女' }" @click="gender = gender === '女' ? '全部' : '女'">
        <text>{{ gender }}</text>
      </view>
    </view>

    <!-- 用户列表 -->
    <view class="user-list">
      <!-- 空状态 -->
      <view v-if="userList.length === 0 && !loading" class="empty-state">
        <view class="empty-icon"><text>📍</text></view>
        <text class="empty-title">附近暂无用户</text>
        <text class="empty-desc">扩大搜索范围或稍后再来看看</text>
      </view>

      <!-- 用户卡片 -->
      <view v-for="user in userList" :key="user.id" class="user-item shadow-card btn-feedback" @click="goDetail(user.id)">
        <view class="avatar-wrap">
          <image class="avatar" :src="user.avatar || '/static/logo/logo-icon-transparent.png'" mode="aspectFill" lazy-load />
          <view v-if="user.online" class="online-dot" />
        </view>
        <view class="info">
          <view class="name-row">
            <text class="nickname">{{ user.nickname || '未设置昵称' }}</text>
            <view class="age-badge"><text>{{ user.age || '—' }}</text></view>
          </view>
          <view class="distance-tag">
            <text>📍 {{ formatDistance(user.distance) }}</text>
          </view>
        </view>
        <view class="like-btn btn-feedback" @click.stop="handleQuickLike(user.id)">
          <text>♥</text>
        </view>
      </view>

      <!-- 加载中 -->
      <view v-if="loading" class="loading-tip"><text>加载中...</text></view>
      <!-- 没有更多 -->
      <view v-if="!hasMore && userList.length > 0" class="loading-tip"><text>没有更多了</text></view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { onShow, onPullDownRefresh, onReachBottom } from "@dcloudio/uni-app";
import { getNearby } from "@/api/match";

/** 距离筛选选项（km，0 表示全部） */
const DISTANCE_OPTIONS = [0, 1, 3, 5, 10];
/** 默认分页大小 */
const DEFAULT_PAGE_SIZE = 10;

const distances = DISTANCE_OPTIONS;
const distance = ref(0);
const gender = ref("全部");
const userList = ref<any[]>([]);
const loading = ref(false);
const currentPage = ref(1);
const hasMore = ref(true);

/**
 * 格式化距离显示：小于 1km 显示米，否则保留 1 位小数
 * @param km 距离（千米）
 */
function formatDistance(km: number): string {
  if (km < 1) return `${Math.round(km * 1000)}m`;
  return `${km.toFixed(1)}km`;
}

/** 跳转用户详情 */
function goDetail(id: string): void {
  uni.navigateTo({ url: `/pages/match/detail?id=${id}` });
}

/** 快速喜欢 */
function handleQuickLike(id: string): void {
  // TODO: 对接 POST /xfjy/{id}/love
  uni.showToast({ title: "已喜欢", icon: "success" });
}

/** 加载附近用户（首页） */
async function loadNearby(): Promise<void> {
  if (loading.value) return;
  loading.value = true;
  try {
    // TODO: 对接 GET /xfjy/search?gender=&distance=&page=1&pagesize=10
    currentPage.value = 1;
    hasMore.value = false; // 接口未接入前标记无更多数据
    userList.value = [];
  } finally {
    loading.value = false;
  }
}

/** 上拉加载更多附近用户 */
async function loadMoreNearby(): Promise<void> {
  if (loading.value || !hasMore.value) return;
  loading.value = true;
  try {
    // TODO: 对接 GET /xfjy/search 分页参数 page=currentPage+1&pagesize=10
    const nextPage = currentPage.value + 1;
    currentPage.value = nextPage;
    hasMore.value = false;
  } finally {
    loading.value = false;
  }
}

onShow(() => loadNearby());

/** 下拉刷新：重新加载附近用户 */
onPullDownRefresh(async () => {
  try {
    await loadNearby();
  } finally {
    uni.stopPullDownRefresh();
  }
});

/** 上拉加载更多 */
onReachBottom(() => {
  loadMoreNearby();
});
</script>

<style lang="scss" scoped>
.nearby-page { min-height: 100vh; background: #f5f5f5; }

/* ===== 筛选栏 ===== */
.filter-bar { display: flex; gap: 12rpx; padding: 20rpx 24rpx; background: #fff; position: sticky; top: 0; z-index: 10; box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.03);
  .filter-item { padding: 10rpx 26rpx; border-radius: 28rpx; font-size: 24rpx; color: #666; background: #f5f5f5; transition: all 0.2s;
    &.active { background: linear-gradient(135deg, #ff5e8e, #ff8fab); color: #fff; box-shadow: 0 4rpx 12rpx rgba(255, 94, 142, 0.3); }
    &.gender.active { background: linear-gradient(135deg, #ff5e8e, #ff8fab); }
  }
}

/* ===== 用户列表 ===== */
.user-list { padding: 20rpx 24rpx; }

.user-item { display: flex; align-items: center; padding: 24rpx; background: #fff; border-radius: 24rpx; margin-bottom: 16rpx;
  .avatar-wrap { position: relative; flex-shrink: 0;
    .avatar { width: 100rpx; height: 100rpx; border-radius: 50%; background: #f0f0f0; border: 3rpx solid #fff0f5; }
    .online-dot { position: absolute; bottom: 4rpx; right: 4rpx; width: 22rpx; height: 22rpx; background: #4cd964; border: 3rpx solid #fff; border-radius: 50%; }
  }
  .info { flex: 1; margin-left: 24rpx;
    .name-row { display: flex; align-items: center; gap: 12rpx;
      .nickname { font-size: 30rpx; font-weight: bold; color: #333; }
      .age-badge { background: linear-gradient(135deg, #ff5e8e, #ff8fab); color: #fff; font-size: 20rpx; padding: 2rpx 12rpx; border-radius: 12rpx; }
    }
    .distance-tag { margin-top: 10rpx; display: inline-flex; background: #fff0f5; border-radius: 16rpx; padding: 4rpx 16rpx;
      text { font-size: 22rpx; color: #ff5e8e; }
    }
  }
  .like-btn { width: 72rpx; height: 72rpx; border-radius: 50%; background: #fff0f5; display: flex; align-items: center; justify-content: center; font-size: 36rpx; color: #ff5e8e; flex-shrink: 0; transition: all 0.2s;
    &:active { background: #ff5e8e; color: #fff; }
  }
}

.loading-tip { display: flex; justify-content: center; padding: 32rpx 0;
  text { font-size: 24rpx; color: #ccc; }
}
</style>