<template>
  <view class="publish-page">
    <!-- 文字输入 -->
    <textarea
      v-model="content"
      class="content-input"
      placeholder="分享你的想法..."
      maxlength="500"
      :auto-height="false"
    />
    <text class="char-count">{{ content.length }}/500</text>

    <!-- 图片选择 -->
    <view class="image-picker">
      <view v-for="(img, i) in images" :key="i" class="image-item">
        <image class="preview" :src="img" mode="aspectFill" />
        <view class="remove" @click="removeImage(i)">✕</view>
      </view>
      <view v-if="images.length < 9" class="add-btn" @click="chooseImage">
        <text>+</text>
        <text class="add-label">{{ images.length }}/9</text>
      </view>
    </view>

    <!-- 位置 -->
    <view class="location-row" @click="chooseLocation">
      <text>📍 {{ location || '添加位置' }}</text>
    </view>

    <!-- 发布按钮 -->
    <button class="publish-btn" :disabled="!content.trim() && images.length === 0" @click="handlePublish">
      发布
    </button>
  </view>
</template>

<script setup lang="ts">
import { ref } from "vue";

const content = ref("");
const images = ref<string[]>([]);
const location = ref("");

function chooseImage() {
  uni.chooseImage({
    count: 9 - images.value.length,
    sizeType: ["compressed"],
    success: (res) => {
      // TODO: 上传 OSS 后替换为线上 URL
      images.value.push(...res.tempFilePaths);
    },
  });
}

function removeImage(index: number) {
  images.value.splice(index, 1);
}

function chooseLocation() {
  uni.chooseLocation({
    success: (res) => {
      location.value = res.name;
    },
    fail: () => {
      uni.showToast({ title: "请授权位置权限", icon: "none" });
    },
  });
}

async function handlePublish() {
  if (!content.value.trim() && images.value.length === 0) return;
  // TODO: 对接 POST /movements 发布动态
  uni.showToast({ title: "发布成功", icon: "success" });
  setTimeout(() => uni.navigateBack(), 800);
}
</script>

<style lang="scss" scoped>
.publish-page { min-height: 100vh; background: #fff; padding: 32rpx; }

.content-input {
  width: 100%; height: 300rpx; font-size: 30rpx; line-height: 1.6;
}
.char-count { font-size: 22rpx; color: #ccc; text-align: right; display: block; }

.image-picker { display: flex; flex-wrap: wrap; gap: 16rpx; margin: 24rpx 0;
  .image-item { position: relative; width: 200rpx; height: 200rpx;
    .preview { width: 100%; height: 100%; border-radius: 12rpx; }
    .remove { position: absolute; top: -10rpx; right: -10rpx; width: 40rpx; height: 40rpx; background: rgba(0,0,0,0.5); color: #fff; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 24rpx; }
  }
  .add-btn { width: 200rpx; height: 200rpx; background: #f5f5f5; border-radius: 12rpx; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8rpx;
    text { font-size: 56rpx; color: #ccc; }
    .add-label { font-size: 22rpx; color: #ccc; }
  }
}

.location-row { padding: 24rpx 0; border-top: 1rpx solid #f5f5f5; font-size: 28rpx; color: #666; }

.publish-btn {
  margin-top: 48rpx; width: 100%; height: 88rpx; line-height: 88rpx;
  background: linear-gradient(90deg, #ff5e8e, #ff8fab); color: #fff;
  font-size: 32rpx; font-weight: bold; border-radius: 44rpx; border: none;
  &[disabled] { opacity: 0.5; }
}
</style>