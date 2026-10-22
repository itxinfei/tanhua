<template>
  <view class="nearby-page">
    <!-- 筛选栏 -->
    <view class="filter-bar">
      <view class="filter-item" v-for="d in distances" :key="d" :class="{ active: distance === d }" @click="distance = d">
        <text>{{ d === 0 ? '全部' : d + 'km' }}</text>
      </view>
      <view class="filter-item gender" :class="{ active: gender === '女' }" @click="gender = gender === '女' ? '全部' : '女'">
        <text>{{ gender }}</text>
      </view>
    </view>

    <!-- 用户列表 -->
    <view class="user-list">
      <view v-if="userList.length === 0" class="empty">
        <text>附近暂无用户</text>
      </view>
      <view v-for="user in userList" :key="user.id" class="user-item" @click="goDetail(user.id)">
        <image class="avatar" :src="user.avatar" mode="aspectFill" />
        <view class="info">
          <text class="nickname">{{ user.nickname }} · {{ user.age }}</text>
          <text class="distance">{{ formatDistance(user.distance) }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import { getNearby } from "@/api/match";

const distances = [0, 1, 3, 5, 10];
const distance = ref(0);
const gender = ref("全部");
const userList = ref<any[]>([]);

function formatDistance(km: number): string {
  if (km < 1) return `${Math.round(km * 1000)}m`;
  return `${km.toFixed(1)}km`;
}

function goDetail(id: string) {
  uni.navigateTo({ url: `/pages/match/detail?id=${id}` });
}

async function loadNearby() {
  // TODO: 对接 GET /xfjy/search?gender=&distance=
}

onShow(() => loadNearby());
</script>

<style lang="scss" scoped>
.nearby-page { min-height: 100vh; background: #f5f5f5; }

.filter-bar {
  display: flex; gap: 16rpx; padding: 24rpx 32rpx; background: #fff;
  .filter-item {
    padding: 8rpx 24rpx; border-radius: 24rpx; font-size: 24rpx; color: #666; background: #f5f5f5;
    &.active { background: #ff5e8e; color: #fff; }
  }
}

.user-list { padding: 16rpx; }
.user-item {
  display: flex; align-items: center; padding: 24rpx; background: #fff; border-radius: 20rpx; margin-bottom: 16rpx;
  .avatar { width: 96rpx; height: 96rpx; border-radius: 50%; }
  .info { margin-left: 24rpx;
    .nickname { font-size: 30rpx; font-weight: 500; display: block; }
    .distance { font-size: 24rpx; color: #ff5e8e; margin-top: 4rpx; display: block; }
  }
}
.empty { text-align: center; padding: 120rpx 0; color: #999; font-size: 28rpx; }
</style>