<template>
  <view class="publish-page">
    <!-- 文字输入 -->
    <textarea
      v-model="content"
      class="content-input"
      :class="{ focus: isInputFocus }"
      placeholder="分享你的想法..."
      placeholder-class="placeholder"
      maxlength="500"
      :auto-height="false"
      @focus="isInputFocus = true"
      @blur="isInputFocus = false"
    />
    <text class="char-count">{{ content.length }}/{{ MAX_CONTENT_LENGTH }}</text>

    <!-- 话题标签 -->
    <view class="topic-section">
      <text class="topic-title">添加话题</text>
      <view class="topic-list">
        <view
          v-for="topic in HOT_TOPICS"
          :key="topic"
          class="topic-tag btn-feedback"
          :class="{ active: selectedTopics.includes(topic) }"
          @click="toggleTopic(topic)"
        >
          <text># {{ topic }}</text>
        </view>
      </view>
    </view>

    <!-- 图片选择 -->
    <view class="image-picker">
      <view v-for="(img, i) in images" :key="i" class="image-item">
        <image class="preview" :src="img" mode="aspectFill" />
        <view class="remove btn-feedback" @click="removeImage(i)"><text>✕</text></view>
      </view>
      <view v-if="images.length < MAX_IMAGE_COUNT" class="add-btn btn-feedback" @click="chooseImage">
        <text class="plus">+</text>
        <text class="add-label">{{ images.length }}/{{ MAX_IMAGE_COUNT }}</text>
      </view>
    </view>

    <!-- 位置 -->
    <view class="location-row btn-feedback" @click="chooseLocation">
      <text class="loc-icon">📍</text>
      <text class="loc-text">{{ location || '添加位置' }}</text>
      <text v-if="location" class="loc-clear" @click.stop="clearLocation">✕</text>
    </view>

    <!-- 发布按钮 -->
    <button class="publish-btn btn-gradient" :disabled="!canPublish || submitting" @click="handlePublish">
      发布
    </button>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from "vue";
import { publishMovement } from "@/api/circle";

/** 内容最大字数 */
const MAX_CONTENT_LENGTH = 500;
/** 图片最大数量 */
const MAX_IMAGE_COUNT = 9;

/** 热门话题推荐 */
const HOT_TOPICS = ["交友日常", "心动瞬间", "周末去哪儿", "美食探店", "运动打卡", "宠物萌照"];

const content = ref("");
const images = ref<string[]>([]);
const location = ref("");
const longitude = ref("");
const latitude = ref("");
const selectedTopics = ref<string[]>([]);
const isInputFocus = ref(false);
const submitting = ref(false);

/** 是否可发布（内容或图片至少一项） */
const canPublish = computed(() => content.value.trim().length > 0 || images.value.length > 0);

/** 切换话题选中状态 */
function toggleTopic(topic: string): void {
  const idx = selectedTopics.value.indexOf(topic);
  if (idx >= 0) {
    selectedTopics.value.splice(idx, 1);
  } else {
    selectedTopics.value.push(topic);
  }
}

/** 选择图片 */
function chooseImage(): void {
  uni.chooseImage({
    count: MAX_IMAGE_COUNT - images.value.length,
    sizeType: ["compressed"],
    success: (res) => {
      // TODO: 上传 OSS 后替换为线上 URL
      images.value.push(...res.tempFilePaths);
    },
  });
}

/** 删除图片 */
function removeImage(index: number): void {
  images.value.splice(index, 1);
}

/** 选择位置 */
function chooseLocation(): void {
  uni.chooseLocation({
    success: (res) => {
      location.value = res.name;
      longitude.value = String(res.longitude);
      latitude.value = String(res.latitude);
    },
    fail: () => {
      uni.showToast({ title: "请授权位置权限", icon: "none" });
    },
  });
}

/** 清除位置 */
function clearLocation(): void {
  location.value = "";
  longitude.value = "";
  latitude.value = "";
}

/** 发布动态（图片由 publishMovement 内部 upload 直传后端） */
async function handlePublish(): Promise<void> {
  if (!canPublish.value || submitting.value) return;
  submitting.value = true;
  try {
    await publishMovement({
      textContent: content.value.trim(),
      location: location.value,
      longitude: longitude.value,
      latitude: latitude.value,
      imagePaths: images.value,
    });
    uni.showToast({ title: "发布成功", icon: "success" });
    setTimeout(() => uni.navigateBack(), 800);
  } catch {
    // request 层统一 toast
  } finally {
    submitting.value = false;
  }
}
</script>

<style lang="scss" scoped>
.publish-page { min-height: 100vh; background: #fff; padding: 32rpx; }

/* ===== 文字输入 ===== */
.content-input { width: 100%; height: 280rpx; font-size: 30rpx; line-height: 1.6; padding: 20rpx; border-radius: 16rpx; background: #fafafa; transition: all 0.2s;
  &.focus { background: #fff; box-shadow: 0 0 0 3rpx rgba(255, 94, 142, 0.25); }
}
.placeholder { color: #ccc; }
.char-count { font-size: 22rpx; color: #ccc; text-align: right; display: block; margin-top: 8rpx; }

/* ===== 话题标签 ===== */
.topic-section { margin-top: 24rpx;
  .topic-title { font-size: 26rpx; color: #999; display: block; margin-bottom: 16rpx; }
}
.topic-list { display: flex; flex-wrap: wrap; gap: 16rpx; }
.topic-tag { padding: 10rpx 24rpx; border-radius: 28rpx; font-size: 24rpx; color: #666; background: #f5f5f5; border: 2rpx solid transparent; transition: all 0.2s;
  &.active { background: #fff0f5; color: #ff5e8e; border-color: #ff5e8e; }
}

/* ===== 图片选择 ===== */
.image-picker { display: flex; flex-wrap: wrap; gap: 16rpx; margin: 24rpx 0;
  .image-item { position: relative; width: 200rpx; height: 200rpx;
    .preview { width: 100%; height: 100%; border-radius: 16rpx; border: 2rpx solid #f0f0f0; }
    .remove { position: absolute; top: -10rpx; right: -10rpx; width: 40rpx; height: 40rpx; background: rgba(0, 0, 0, 0.55); color: #fff; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 24rpx; }
  }
  .add-btn { width: 200rpx; height: 200rpx; background: #fafafa; border-radius: 16rpx; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8rpx; border: 2rpx dashed #ddd;
    .plus { font-size: 56rpx; color: #ccc; line-height: 1; }
    .add-label { font-size: 22rpx; color: #ccc; }
  }
}

/* ===== 位置 ===== */
.location-row { display: flex; align-items: center; gap: 12rpx; padding: 24rpx 20rpx; border-top: 1rpx solid #f5f5f5; border-bottom: 1rpx solid #f5f5f5; font-size: 28rpx;
  .loc-icon { font-size: 32rpx; }
  .loc-text { flex: 1; color: #666; }
  .loc-clear { width: 36rpx; height: 36rpx; background: #eee; color: #999; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 20rpx; }
}

/* ===== 发布按钮 ===== */
.publish-btn { margin-top: 48rpx; width: 100%; height: 88rpx; line-height: 88rpx; color: #fff; font-size: 32rpx; font-weight: bold; border-radius: 44rpx; border: none;
  &[disabled] { opacity: 0.4; }
}
</style>