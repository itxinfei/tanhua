<template>
  <view class="detail-page">
    <!-- 照片轮播 -->
    <swiper class="photo-swiper" circular indicator-dots indicator-color="rgba(255,255,255,0.4)" indicator-active-color="#fff">
      <swiper-item v-for="(photo, i) in photos" :key="i">
        <image class="photo" :src="photo" mode="aspectFill" @click="previewPhoto(i)" />
      </swiper-item>
    </swiper>

    <!-- 返回按钮 -->
    <view class="back-btn" :style="{ top: statusBarHeight + 8 + 'px' }" @click="goBack">
      <text>‹</text>
    </view>

    <!-- 资料卡 -->
    <view class="info-card">
      <view class="name-row">
        <text class="nickname">{{ user?.nickname || '—' }}</text>
        <text class="age">{{ user?.age || '—' }}</text>
        <view v-if="user?.verified" class="verified">已认证</view>
      </view>
      <view class="meta-grid">
        <view class="meta-item"><text class="label">城市</text><text class="value">{{ user?.city || '—' }}</text></view>
        <view class="meta-item"><text class="label">身高</text><text class="value">{{ user?.height || '—' }}cm</text></view>
        <view class="meta-item"><text class="label">学历</text><text class="value">{{ user?.education || '—' }}</text></view>
        <view class="meta-item"><text class="label">行业</text><text class="value">{{ user?.industry || '—' }}</text></view>
      </view>
      <view v-if="user?.signature" class="signature">{{ user.signature }}</view>

      <!-- 陌生人问题 -->
      <view class="question-section">
        <text class="section-title">破冰问题</text>
        <view v-for="(q, i) in questions" :key="i" class="question-item">
          <text class="question">{{ q }}</text>
        </view>
      </view>
    </view>

    <!-- 底部操作栏 -->
    <view class="action-bar safe-area-bottom">
      <view class="action dislike" @click="handleDislike"><text>✕</text></view>
      <view class="action like" @click="handleLike"><text>♥</text></view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";

const statusBarHeight = ref(20);
const userId = ref("");
const user = ref<any>(null);
const photos = ref<string[]>([]);
const questions = ref<string[]>([]);

function goBack() {
  uni.navigateBack();
}

function previewPhoto(index: number) {
  uni.previewImage({ urls: photos.value, current: photos.value[index] });
}

async function handleLike() {
  // TODO: 对接 POST /xfjy/{id}/love
  uni.showToast({ title: "已喜欢", icon: "success" });
  setTimeout(goBack, 800);
}

async function handleDislike() {
  // TODO: 对接 POST /xfjy/{id}/unlove
  setTimeout(goBack, 300);
}

onLoad((options: any) => {
  userId.value = options?.id || "";
  const sysInfo = uni.getSystemInfoSync();
  statusBarHeight.value = sysInfo.statusBarHeight || 20;
  // TODO: 对接 GET /users/{id}/personalInfo 和 GET /users/{id}/strangerQuestions
});
</script>

<style lang="scss" scoped>
.detail-page {
  min-height: 100vh;
  background: #f5f5f5;
  padding-bottom: 140rpx;
}

.photo-swiper {
  height: 750rpx;

  .photo {
    width: 100%;
    height: 100%;
  }
}

.back-btn {
  position: absolute;
  left: 24rpx;
  width: 64rpx;
  height: 64rpx;
  background: rgba(0, 0, 0, 0.3);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 40rpx;
  z-index: 10;
}

.info-card {
  margin: -40rpx 24rpx 0;
  background: #fff;
  border-radius: 32rpx;
  padding: 32rpx;
  position: relative;

  .name-row {
    display: flex;
    align-items: center;
    gap: 12rpx;

    .nickname { font-size: 40rpx; font-weight: bold; }
    .age { font-size: 30rpx; color: #999; }
    .verified { font-size: 20rpx; color: #ff5e8e; background: #fff0f5; padding: 2rpx 12rpx; border-radius: 16rpx; }
  }

  .meta-grid {
    display: flex;
    flex-wrap: wrap;
    gap: 24rpx;
    margin-top: 24rpx;

    .meta-item {
      .label { font-size: 22rpx; color: #ccc; display: block; }
      .value { font-size: 26rpx; color: #333; margin-top: 4rpx; display: block; }
    }
  }

  .signature {
    font-size: 26rpx;
    color: #666;
    line-height: 1.6;
    margin-top: 20rpx;
    padding: 16rpx;
    background: #fafafa;
    border-radius: 12rpx;
  }

  .question-section {
    margin-top: 32rpx;

    .section-title { font-size: 28rpx; font-weight: bold; display: block; margin-bottom: 16rpx; }

    .question-item {
      padding: 16rpx 0;
      border-bottom: 1rpx solid #f5f5f5;

      .question { font-size: 26rpx; color: #666; }
    }
  }
}

.action-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  justify-content: center;
  gap: 80rpx;
  padding: 24rpx 0;
  background: #fff;
  box-shadow: 0 -4rpx 20rpx rgba(0, 0, 0, 0.05);

  .action {
    width: 100rpx;
    height: 100rpx;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 44rpx;

    &.dislike { background: #fff; color: #ff3b30; border: 4rpx solid #ff3b30; }
    &.like { background: #ff5e8e; color: #fff; }
  }
}
</style>