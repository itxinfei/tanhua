<template>
  <view class="publish-page">
    <!-- 顶部栏：自定义导航（取消返回），使发布成为明确的全屏流程 -->
    <view class="pub-header" :style="{ paddingTop: statusBarHeight + 'px' }">
      <a11y-button class="pub-cancel" label="取消发布" @tap="handleCancel">取消</a11y-button>
      <text class="pub-title">发布动态</text>
      <view class="pub-spacer" aria-hidden="true"></view>
    </view>
    <!-- 文字输入 -->
    <textarea
      v-model="content"
      class="content-input"
      :class="{ focus: isInputFocus }"
      placeholder="分享你的想法..."
      placeholder-class="placeholder"
      aria-label="动态正文"
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
        <a11y-button
          v-for="topic in HOT_TOPICS"
          :key="topic"
          class="topic-tag btn-feedback"
          :class="{ active: selectedTopics.includes(topic) }"
          role="checkbox"
          :checked="selectedTopics.includes(topic)"
          :label="`话题 ${topic}`"
          @tap="toggleTopic(topic)"
        >
          <text># {{ topic }}</text>
        </a11y-button>
      </view>
    </view>

    <!-- 图片选择 -->
    <view class="image-picker">
      <view v-for="(img, i) in images" :key="i" class="image-item">
        <image lazy-load class="preview" :src="img" mode="aspectFill" :aria-label="`已选图片 ${i + 1}`" />
        <a11y-button class="remove btn-feedback" :label="`删除第 ${i + 1} 张图片`" @tap="removeImage(i)"><uni-icons type="close" size="20" color="#ffffff"></uni-icons></a11y-button>
      </view>
      <a11y-button v-if="images.length < MAX_IMAGE_COUNT" class="add-btn btn-feedback" label="选择图片" @tap="chooseImage">
        <text class="plus" aria-hidden="true">+</text>
        <text class="add-label">{{ images.length }}/{{ MAX_IMAGE_COUNT }}</text>
      </a11y-button>
    </view>

    <!-- 位置（避免嵌套可聚焦控件：选择区与清除按钮并列） -->
    <view class="location-row">
      <a11y-button class="loc-pick btn-feedback" :label="location ? `位置：${location}，点击修改` : '添加位置'" @tap="chooseLocation">
        <uni-icons class="loc-icon" type="location" size="16"></uni-icons>
        <text class="loc-text">{{ location || '添加位置' }}</text>
      </a11y-button>
      <a11y-button v-if="location" class="loc-clear" label="清除位置" @tap="clearLocation"><uni-icons type="close" size="20" color="#767676"></uni-icons></a11y-button>
    </view>

    <!-- 发布按钮 -->
    <button class="publish-btn btn-gradient" :disabled="!canPublish || submitting" @click="handlePublish">
      发布
    </button>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from "vue";
import A11yButton from "@/components/a11y-button.vue";
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

/** 状态栏高度（自定义导航适配） */
const statusBarHeight = ref(0);
/** 取消发布：返回上一页（圈子 tab 恢复） */
function handleCancel(): void {
  uni.navigateBack();
}

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
      // 图片由 publishMovement 内部 upload() 直传后端，此处仅暂存本地路径
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
  let published = false;
  try {
    await publishMovement({
      textContent: content.value.trim(),
      location: location.value,
      longitude: longitude.value,
      latitude: latitude.value,
      imagePaths: images.value,
      tags: selectedTopics.value,
    });
    published = true;
    uni.showToast({ title: "发布成功", icon: "success" });
    setTimeout(() => uni.navigateBack(), 800);
  } catch {
    // request 层统一 toast
  } finally {
    // 发布成功后保持 submitting（返回前的窗口期不可重复提交），仅失败时复位
    if (!published) submitting.value = false;
  }
}

onMounted(() => {
  const sys = uni.getSystemInfoSync();
  statusBarHeight.value = sys.statusBarHeight || 20;
});
</script>

<style lang="scss" scoped>
.publish-page { min-height: 100vh; background: #fff; padding: 32rpx; }

.pub-header { position: sticky; top: 0; margin: -32rpx -32rpx 24rpx; padding: 0 24rpx; height: 88rpx; display: flex; align-items: center; justify-content: space-between; background: #fff; border-bottom: 1rpx solid #f5f5f5; z-index: 10; }
.pub-cancel { font-size: 30rpx; color: #767676; }
.pub-title { font-size: 32rpx; font-weight: 600; color: #333; }
.pub-spacer { width: 60rpx; }

/* ===== 文字输入 ===== */
.content-input { width: 100%; height: 280rpx; font-size: 30rpx; line-height: 1.6; padding: 20rpx; border-radius: 16rpx; background: #fafafa; transition: all 0.2s;
  &.focus { background: #fff; box-shadow: 0 0 0 3rpx rgba(255, 94, 142, 0.25); }
}
/* 占位符 #ccc（≈1.6:1）→ #767676 */
.placeholder { color: #767676; }
.char-count { font-size: 22rpx; color: #767676; text-align: right; display: block; margin-top: 8rpx; }

/* ===== 话题标签 ===== */
.topic-section { margin-top: 24rpx;
  .topic-title { font-size: 26rpx; color: #767676; display: block; margin-bottom: 16rpx; }
}
.topic-list { display: flex; flex-wrap: wrap; gap: 16rpx; }
.topic-tag { padding: 10rpx 24rpx; border-radius: 28rpx; font-size: 24rpx; color: #666; background: #f5f5f5; border: 2rpx solid transparent; transition: all 0.2s;
  &.active { background: #ffeff5; color: #ff5e9c; border-color: #ff5e9c; }
}

/* ===== 图片选择 ===== */
.image-picker { display: flex; flex-wrap: wrap; gap: 16rpx; margin: 24rpx 0;
  .image-item { position: relative; width: 200rpx; height: 200rpx;
    .preview { width: 100%; height: 100%; border-radius: 16rpx; border: 2rpx solid #f0f0f0; }
    /* 删除按钮 40rpx≈20px 不足 24px → 提至 48rpx */
    .remove { position: absolute; top: -10rpx; right: -10rpx; width: 48rpx; height: 48rpx; background: rgba(0, 0, 0, 0.55); color: #fff; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 24rpx; }
  }
  .add-btn { width: 200rpx; height: 200rpx; background: #fafafa; border-radius: 16rpx; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8rpx; border: 2rpx dashed #ddd;
    /* #ccc → #767676 */
    .plus { font-size: 56rpx; color: #767676; line-height: 1; }
    .add-label { font-size: 22rpx; color: #767676; }
  }
}

/* ===== 位置 ===== */
.location-row { display: flex; align-items: center; gap: 12rpx; padding: 24rpx 20rpx; border-top: 1rpx solid #f5f5f5; border-bottom: 1rpx solid #f5f5f5; font-size: 28rpx;
  .loc-pick { flex: 1; display: flex; align-items: center; gap: 12rpx; min-height: 48rpx; }
  .loc-icon { font-size: 32rpx; }
  .loc-text { flex: 1; color: #595959; }
  /* 清除按钮 36rpx≈18px 不足 24px → 提至 48rpx */
  .loc-clear { width: 48rpx; height: 48rpx; background: #eee; color: #767676; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 20rpx; flex-shrink: 0; }
}

/* ===== 发布按钮 ===== */
.publish-btn { margin-top: 48rpx; width: 100%; height: 88rpx; line-height: 88rpx; color: #fff; font-size: 32rpx; font-weight: bold; border-radius: 44rpx; border: none;
  &[disabled] { opacity: 0.4; }
}
</style>