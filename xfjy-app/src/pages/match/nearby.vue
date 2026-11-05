<template>
  <view class="nearby-page">
    <!-- 筛选栏 -->
    <view class="filter-bar" role="radiogroup" aria-label="筛选条件">
      <!-- 距离维度的"全部"改为"不限距离"，与右侧性别筛选的"不限性别"明确区分 -->
      <a11y-button class="filter-item" :class="{ active: distance === d }" v-for="d in distances" :key="d" role="radio" :checked="distance === d" :label="d === 0 ? '不限距离' : `距离 ${d} 公里以内`" @tap="distance = d">
        <text>{{ d === 0 ? '不限距离' : d + 'km' }}</text>
      </a11y-button>
      <a11y-button class="filter-item gender" :class="{ active: gender !== '全部' }" role="radio" :checked="gender === '女'" :label="`性别筛选：${gender === '全部' ? '不限性别' : '女生'}`" @tap="gender = gender === '女' ? '全部' : '女'">
        <text>{{ gender === '全部' ? '不限性别' : '女生' }}</text>
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
            class="like-btn"
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

      <!-- 空状态：确实没有附近用户 -->
      <empty-state
        v-if="userList.length === 0 && !loading && !loadFailed"
        icon="location"
        title="附近暂无用户"
        desc="扩大搜索范围或稍后再来看看"
      />

      <!-- 加载失败：与「附近真的没人」严格区分，给明确原因 + 可重试入口。
           后端 /xfjy/search 异常时旧版把服务故障误报成「附近暂无用户」，用户无从判断。 -->
      <empty-state
        v-if="userList.length === 0 && !loading && loadFailed"
        role="alert"
        icon="info"
        title="暂时没能获取附近的人"
        desc="服务可能开小差了，检查网络后重试"
      >
        <a11y-button class="nearby-retry" label="重新加载附近的人" @tap="loadNearby(true)">重新加载</a11y-button>
      </empty-state>
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
/** 请求失败标记：与「附近确实没有用户」区分，展示可重试的错误态 */
const loadFailed = ref(false);

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
    loadFailed.value = false;
  }
  try {
    const genderParam = gender.value === "女" ? "woman" : undefined;
    const distanceMeter = distance.value === 0 ? ALL_DISTANCE_METER : distance.value * 1000;
    const list = await getNearby(genderParam, distanceMeter);
    if (seq !== loadSeq) return;
    loadFailed.value = false;

    const allItems = list || [];
    allItemsCache.value = allItems;

    if (isRefresh || page.value === 1) {
      userList.value = allItems.slice(0, pageSize);
      hasMore.value = allItems.length > pageSize;
    }
  } catch {
    // request 层统一 toast；这里再把「服务失败」与「附近没人」区分开
    if (seq === loadSeq && userList.value.length === 0) {
      loadFailed.value = true;
    }
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
  // getLocation 在 H5 由浏览器 Geolocation 提供、App 由系统定位提供，
  // 但个别运行环境可能完全缺失；能力探测避免直接抛 TypeError 中断页面。
  if (typeof (uni as any).getLocation !== "function") {
    hasLocationAuth.value = true;
    info("当前环境不支持精确定位，已为你展示全城用户");
    loadNearby(true);
    return;
  }
  try {
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
  } catch {
    // 定位能力存在但调用异常（如非安全上下文）：降级为全城列表，不让页面卡死
    hasLocationAuth.value = true;
    loadNearby(true);
  }
}

/** 检查定位权限（不触发系统弹窗，仅读取当前授权状态） */
function checkLocationAuth(): void {
  // uni.getSetting 是微信小程序专属 API，H5 / App 运行时并不存在。
  // 原先无条件调用会在每次进入「附近」页抛出 `uni.getSetting is not a function`，
  // 触发 Vue 全局错误边界并刷屏 console.error；这里先做能力探测再调用。
  if (typeof (uni as any).getSetting !== "function") {
    // H5 / App 没有「scope.userLocation」授权位：保持可用态，
    // 真正的授权结果由 requestLocation 的 success/fail 决定。
    hasLocationAuth.value = true;
    return;
  }
  (uni as any).getSetting({
    success: (res: any) => {
      const auth = res?.authSetting?.["scope.userLocation"];
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

/* 加载失败态的重试按钮（置于 empty-state 的 slot 内） */
.nearby-retry {
  margin-top: 32rpx;
  padding: 16rpx 56rpx;
  border-radius: var(--radius-pill);
  font-size: 28rpx;
  font-weight: 600;
  color: var(--text-inverse);
  background: linear-gradient(135deg, #ff4d8d, #8a5cf6);
}
</style>
