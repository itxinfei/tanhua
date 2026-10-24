<template>
  <view class="notifications-page">
    <!-- 空状态 -->
    <view v-if="list.length === 0" class="empty-state">
      <view class="empty-icon"><text>🔔</text></view>
      <text class="empty-title">暂无通知</text>
      <text class="empty-desc">有人喜欢你时会在这里提醒你</text>
    </view>

    <!-- 通知列表 -->
    <view v-for="item in list" :key="item.id" class="notice-item shadow-card btn-feedback" @click="goDetail(item)">
      <view class="avatar-wrap">
        <image class="avatar" :src="item.avatar || '/static/logo/logo-icon-transparent.png'" mode="aspectFill" />
        <view class="type-badge" :style="{ background: typeMeta.color }"><text>{{ typeMeta.icon }}</text></view>
      </view>
      <view class="info">
        <text class="nickname">{{ item.nickname }}</text>
        <text class="desc">{{ item.desc }}</text>
      </view>
      <text class="time">{{ item.time }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from "vue";
import { onLoad, onShow } from "@dcloudio/uni-app";

/** 通知类型元数据（图标+颜色，与会话列表 NOTICE_META 保持一致） */
const TYPE_META: Record<string, { icon: string; color: string }> = {
  like: { icon: "♥", color: "#ff5e8e" },
  comment: { icon: "💬", color: "#4a90d9" },
  love: { icon: "★", color: "#ffd700" },
  announcement: { icon: "📢", color: "#4cd964" },
  visitors: { icon: "👁", color: "#a78bfa" },
  fans: { icon: "👥", color: "#ff8fab" },
};

/** 页面标题映射 */
const PAGE_TITLES: Record<string, string> = {
  like: "谁喜欢我", comment: "评论", love: "心动",
  announcement: "公告", visitors: "谁看过我", fans: "粉丝",
};

const type = ref("like");
const list = ref<any[]>([]);

/** 当前类型元数据（默认喜欢） */
const typeMeta = computed(() => TYPE_META[type.value] || TYPE_META.like);

/** 跳转用户详情 */
function goDetail(item: any): void {
  uni.navigateTo({ url: `/pages/match/detail?id=${item.userId}` });
}

onLoad((options: any) => {
  type.value = options?.type || "like";
  uni.setNavigationBarTitle({ title: PAGE_TITLES[type.value] || "通知" });
});

onShow(() => {
  // TODO: 对接 GET /messages/likes | comments | loves | announcements
});
</script>

<style lang="scss" scoped>
.notifications-page { min-height: 100vh; background: #f5f5f5; padding: 20rpx 24rpx; }

.notice-item { display: flex; align-items: center; padding: 24rpx; background: #fff; border-radius: 24rpx; margin-bottom: 16rpx;
  .avatar-wrap { position: relative; flex-shrink: 0;
    .avatar { width: 88rpx; height: 88rpx; border-radius: 50%; background: #f0f0f0; border: 3rpx solid #fff0f5; }
    .type-badge { position: absolute; bottom: -4rpx; right: -4rpx; width: 36rpx; height: 36rpx; border-radius: 50%; border: 3rpx solid #fff; display: flex; align-items: center; justify-content: center; font-size: 18rpx; color: #fff; }
  }
  .info { flex: 1; margin-left: 20rpx;
    .nickname { font-size: 28rpx; font-weight: bold; color: #333; display: block; }
    .desc { font-size: 24rpx; color: #999; margin-top: 6rpx; display: block; }
  }
  .time { font-size: 22rpx; color: #ccc; flex-shrink: 0; }
}
</style>