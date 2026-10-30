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
      <view v-for="user in userList" :key="user.userId" class="user-item shadow-card btn-feedback" @click="goDetail(user.userId)">
        <view class="avatar-wrap">
          <image class="avatar" :src="user.avatar || '/static/logo/logo-icon-transparent.png'" mode="aspectFill" lazy-load />
        </view>
        <view class="info">
          <view class="name-row">
            <text class="nickname">{{ user.nickname || '未设置昵称' }}</text>
            <view class="age-badge"><text>{{ user.age || '—' }}</text></view>
          </view>
          <view class="distance-tag">
            <text>📍 {{ user.city || '同范围' }}</text>
          </view>
        </view>
        <view class="like-btn btn-feedback" @click.stop="handleQuickLike(user.userId)">
          <text>♥</text>
        </view>
      </view>

      <!-- 加载中 -->
      <view v-if="loading" class="loading-tip"><text>加载中...</text></view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, watch } from "vue";
import { onShow, onPullDownRefresh } from "@dcloudio/uni-app";
import { getNearby, loveUser, type NearUser } from "@/api/match";

/** 距离筛选选项（km，0 表示全部） */
const DISTANCE_OPTIONS = [0, 1, 3, 5, 10];
/** "全部"距离对应的搜索半径（米），取 50km 覆盖全城 */
const ALL_DISTANCE_METER = 50000;

const distances = DISTANCE_OPTIONS;
const distance = ref(0);
const gender = ref("全部");
const userList = ref<NearUser[]>([]);
const loading = ref(false);

/** 跳转用户详情 */
function goDetail(id: number): void {
  uni.navigateTo({ url: `/pages/match/detail?id=${id}` });
}

/** 快速喜欢 */
async function handleQuickLike(id: number): Promise<void> {
  try {
    const matched = await loveUser(id);
    uni.showToast({ title: matched ? "互相喜欢，匹配成功！" : "已喜欢", icon: "success" });
  } catch {
    // 请求失败由 request 层统一 toast
  }
}

/** 加载附近用户 */
async function loadNearby(): Promise<void> {
  if (loading.value) return;
  loading.value = true;
  try {
    const genderParam = gender.value === "女" ? "woman" : undefined;
    const distanceMeter = distance.value === 0 ? ALL_DISTANCE_METER : distance.value * 1000;
    const list = await getNearby(genderParam, distanceMeter);
    userList.value = list || [];
  } catch {
    // 请求失败由 request 层统一 toast
  } finally {
    loading.value = false;
  }
}

/** 筛选条件变化时重新加载 */
watch([distance, gender], () => loadNearby());

onShow(() => loadNearby());

/** 下拉刷新：重新加载附近用户 */
onPullDownRefresh(async () => {
  try {
    await loadNearby();
  } finally {
    uni.stopPullDownRefresh();
  }
});
</script>

<style lang="scss" scoped>
.nearby-page { min-height: 100vh; background: #f5f5f5; }

/* ===== 筛选栏 ===== */
.filter-bar { display: flex; gap: 12rpx; padding: 20rpx 24rpx; background: #fff; position: sticky; top: 0; z-index: 10; box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.03);
  .filter-item { padding: 10rpx 26rpx; border-radius: 28rpx; font-size: 24rpx; color: #666; background: #f5f5f5; transition: all 0.2s;
    &.active { background: linear-gradient(135deg, #ff5e9c, #ff8fb1); color: #fff; box-shadow: 0 4rpx 12rpx rgba(255, 94, 142, 0.3); }
    &.gender.active { background: linear-gradient(135deg, #ff5e9c, #ff8fb1); }
  }
}

/* ===== 用户列表 ===== */
.user-list { padding: 20rpx 24rpx; }

.user-item { display: flex; align-items: center; padding: 24rpx; background: #fff; border-radius: 24rpx; margin-bottom: 16rpx;
  .avatar-wrap { position: relative; flex-shrink: 0;
    .avatar { width: 100rpx; height: 100rpx; border-radius: 50%; background: #f0f0f0; border: 3rpx solid #ffeff5; }
  }
  .info { flex: 1; margin-left: 24rpx;
    .name-row { display: flex; align-items: center; gap: 12rpx;
      .nickname { font-size: 30rpx; font-weight: bold; color: #333; }
      .age-badge { background: linear-gradient(135deg, #ff5e9c, #ff8fb1); color: #fff; font-size: 20rpx; padding: 2rpx 12rpx; border-radius: 12rpx; }
    }
    .distance-tag { margin-top: 10rpx; display: inline-flex; background: #ffeff5; border-radius: 16rpx; padding: 4rpx 16rpx;
      text { font-size: 22rpx; color: #ff5e9c; }
    }
  }
  .like-btn { width: 72rpx; height: 72rpx; border-radius: 50%; background: #ffeff5; display: flex; align-items: center; justify-content: center; font-size: 36rpx; color: #ff5e9c; flex-shrink: 0; transition: all 0.2s;
    &:active { background: #ff5e9c; color: #fff; }
  }
}

.loading-tip { display: flex; justify-content: center; padding: 32rpx 0;
  text { font-size: 24rpx; color: #ccc; }
}
</style>