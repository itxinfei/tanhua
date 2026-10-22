<template>
  <view class="notifications-page">
    <view v-if="list.length === 0" class="empty">
      <text>暂无通知</text>
    </view>
    <view v-for="item in list" :key="item.id" class="notice-item" @click="goDetail(item)">
      <image class="avatar" :src="item.avatar" mode="aspectFill" />
      <view class="info">
        <text class="nickname">{{ item.nickname }}</text>
        <text class="desc">{{ item.desc }}</text>
      </view>
      <text class="time">{{ item.time }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { onLoad, onShow } from "@dcloudio/uni-app";

const type = ref("");
const list = ref<any[]>([]);

function goDetail(item: any) {
  uni.navigateTo({ url: `/pages/match/detail?id=${item.userId}` });
}

onLoad((options: any) => {
  type.value = options?.type || "like";
  const titles: Record<string, string> = { like: "谁喜欢我", comment: "评论", love: "心动", announcement: "公告", visitors: "谁看过我", fans: "粉丝" };
  uni.setNavigationBarTitle({ title: titles[type.value] || "通知" });
});

onShow(() => {
  // TODO: 对接 GET /messages/likes | comments | loves | announcements
});
</script>

<style lang="scss" scoped>
.notifications-page { min-height: 100vh; background: #f5f5f5; }
.empty { text-align: center; padding: 120rpx 0; color: #999; font-size: 28rpx; }
.notice-item {
  display: flex; align-items: center; padding: 24rpx 32rpx; background: #fff; border-bottom: 1rpx solid #f5f5f5;
  .avatar { width: 88rpx; height: 88rpx; border-radius: 50%; }
  .info { flex: 1; margin-left: 20rpx;
    .nickname { font-size: 28rpx; font-weight: 500; display: block; }
    .desc { font-size: 24rpx; color: #999; margin-top: 4rpx; display: block; }
  }
  .time { font-size: 22rpx; color: #ccc; }
}
</style>