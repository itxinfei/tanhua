<template>
  <view class="publish-page">
    <!-- 顶部栏（抖音式）：左侧关闭，右侧发布按钮，无居中标题 -->
    <view class="top-bar" :style="{ paddingTop: statusBarHeight + 'px' }">
      <a11y-button class="icon-btn" label="取消发布" @tap="handleCancel">
        <uni-icons type="close" size="26" color="#161823"></uni-icons>
      </a11y-button>
      <a11y-button
        class="top-publish"
        :class="{ disabled: !canPublish || submitting }"
        :label="canPublish ? '发布动态' : '请先输入内容或选择图片'"
        @tap="handlePublish"
      >
        <text>{{ submitting ? '发布中' : '发布' }}</text>
      </a11y-button>
    </view>

    <!-- 正文（无边框大输入，抖音式） -->
    <textarea
      v-model="content"
      class="content-area"
      placeholder="写点什么..."
      placeholder-class="placeholder"
      aria-label="动态正文"
      :maxlength="MAX_CONTENT_LENGTH"
      :auto-height="false"
    />
    <text class="char-count">{{ content.length }}/{{ MAX_CONTENT_LENGTH }}</text>

    <!-- 媒体行：横向缩略图 + 添加（抖音式）；选择视频后与图片互斥 -->
    <scroll-view class="media-row" scroll-x :show-scrollbar="false">
      <template v-if="videoFile">
        <view class="media-item">
          <image lazy-load class="media-img" :src="videoCover" mode="aspectFill" aria-label="已选视频封面" />
          <view class="media-video-badge" aria-hidden="true">
            <uni-icons type="videocam-filled" size="16" color="#fff"></uni-icons>
          </view>
          <a11y-button class="media-remove" label="删除已选视频" @tap="removeVideo">
            <uni-icons type="close" size="13" color="#ffffff"></uni-icons>
          </a11y-button>
        </view>
      </template>
      <template v-else>
        <view v-for="(img, i) in images" :key="i" class="media-item">
          <image lazy-load class="media-img" :src="img" mode="aspectFill" :aria-label="`已选图片 ${i + 1}`" />
          <a11y-button class="media-remove" :label="`删除第 ${i + 1} 张图片`" @tap="removeImage(i)">
            <uni-icons type="close" size="13" color="#ffffff"></uni-icons>
          </a11y-button>
        </view>
        <a11y-button v-if="images.length < MAX_IMAGE_COUNT" class="media-add" label="选择图片" @tap="chooseImage">
          <uni-icons type="image" size="30" color="#767676"></uni-icons>
          <text class="media-add-label">发图片</text>
        </a11y-button>
        <a11y-button class="media-add" label="选择视频发布" @tap="chooseVideo">
          <uni-icons type="videocam" size="30" color="#767676"></uni-icons>
          <text class="media-add-label">发视频</text>
        </a11y-button>
      </template>
    </scroll-view>

    <!-- 设置列表（抖音式分组行，右侧带箭头）；小视频发布仅接收封面+视频文件，文字/话题/位置不随视频上传，隐藏避免误导 -->
    <view v-if="!videoFile" class="setting-list">
      <a11y-button class="setting-row" :label="`话题，已选 ${selectedTopics.length} 个`" @tap="toggleTopicPanel">
        <view class="row-left">
          <text class="row-hash" aria-hidden="true">#</text>
          <text class="row-name">话题</text>
        </view>
        <view class="row-right">
          <text class="row-value" :class="{ placeholder: selectedTopics.length === 0 }">
            {{ selectedTopics.length ? selectedTopics.join("、") : "添加话题" }}
          </text>
          <uni-icons type="right" size="14" color="#c4c8d0"></uni-icons>
        </view>
      </a11y-button>

      <a11y-button class="setting-row" :label="location ? `位置：${location}，点击修改` : '添加位置'" @tap="chooseLocation">
        <view class="row-left">
          <uni-icons type="location" size="20" color="#161823"></uni-icons>
          <text class="row-name">位置</text>
        </view>
        <view class="row-right">
          <text class="row-value" :class="{ placeholder: !location }">{{ location || "添加位置" }}</text>
          <uni-icons type="right" size="14" color="#c4c8d0"></uni-icons>
        </view>
      </a11y-button>
    </view>

    <!-- 话题选择面板（点击话题行展开/收起） -->
    <view v-if="showTopicPanel" class="topic-panel">
      <a11y-button
        v-for="topic in HOT_TOPICS"
        :key="topic"
        class="topic-chip"
        :class="{ active: selectedTopics.includes(topic) }"
        role="checkbox"
        :checked="selectedTopics.includes(topic)"
        :label="`话题 ${topic}`"
        @tap="toggleTopic(topic)"
      >
        <text># {{ topic }}</text>
      </a11y-button>
    </view>

    <!-- 非 tabBar 页原生底栏不显示，用自定义底栏补齐；键盘弹起时隐藏避免遮挡。
         mp-weixin 端发布页是原生 tab 页（底栏自带发布入口），不再叠加自定义底栏 -->
    <!-- #ifdef H5 -->
    <custom-tabbar v-if="!keyboardOpen" active="publish" />
    <!-- #endif -->
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from "vue";
import { onUnload } from "@dcloudio/uni-app";
import A11yButton from "@/components/a11y-button.vue";
import CustomTabbar from "@/components/custom-tabbar.vue";
import { publishMovement } from "@/api/circle";
import { publishVideo } from "@/api/video";
import { CIRCLE_NEED_REFRESH_KEY } from "@/store/circle";
import { safeGoBack } from "@/utils/nav";

/** 内容最大字数 */
const MAX_CONTENT_LENGTH = 500;
/** 图片最大数量 */
const MAX_IMAGE_COUNT = 9;

/** 热门话题推荐 */
const HOT_TOPICS = ["交友日常", "心动瞬间", "周末去哪儿", "美食探店", "运动打卡", "宠物萌照"];

const content = ref("");
const images = ref<string[]>([]);
/** 已选小视频（本地路径 + 自动生成封面），与图片互斥 */
const videoFile = ref("");
const videoCover = ref("");
const location = ref("");
const longitude = ref("");
const latitude = ref("");
const selectedTopics = ref<string[]>([]);
/** 话题选择面板是否展开（点击话题行切换） */
const showTopicPanel = ref(false);
const submitting = ref(false);

/** 状态栏高度（自定义导航适配） */
const statusBarHeight = ref(0);
/** 键盘是否弹起（用于隐藏自定义底栏，避免遮挡输入/发布按钮） */
const keyboardOpen = ref(false);
/** 发布成功后的返回定时器句柄（onUnload 清理，避免离页后回调再触发导航） */
let backTimer: ReturnType<typeof setTimeout> | null = null;

/** 取消发布：返回上一页（圈子 tab 恢复）；提交在途时禁止，避免导航打断上传后状态不可控 */
function handleCancel(): void {
  if (submitting.value) return;
  safeGoBack("/pages/circle/index");
}

/** 是否可发布（内容、图片、视频至少一项） */
const canPublish = computed(
  () => content.value.trim().length > 0 || images.value.length > 0 || !!videoFile.value,
);

/** 展开/收起话题选择面板 */
function toggleTopicPanel(): void {
  showTopicPanel.value = !showTopicPanel.value;
}

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

/** 选择小视频：thumbTempFilePath 为系统自动截取的封面帧 */
function chooseVideo(): void {
  // 小视频链路仅上传封面+视频文件，不支持文字/图片/话题/位置，先互斥清空
  const hasContent = content.value.trim().length > 0 || images.value.length > 0;
  const proceed = () => {
    uni.chooseVideo({
      sourceType: ["album", "camera"],
      maxDuration: 60,
      success: (res) => {
        // thumbTempFilePath 为系统自动截取的封面帧；部分端/类型定义未声明该字段，做窄化访问
        const r = res as typeof res & { thumbTempFilePath?: string };
        videoFile.value = res.tempFilePath;
        videoCover.value = r.thumbTempFilePath || "";
      },
    });
  };
  if (!hasContent) {
    proceed();
    return;
  }
  uni.showModal({
    title: "切换为视频发布",
    content: "发布视频暂不支持文字和图片，已输入的内容将被清空，是否继续？",
    success: (res) => {
      if (!res.confirm) return;
      content.value = "";
      images.value = [];
      selectedTopics.value = [];
      location.value = "";
      longitude.value = "";
      latitude.value = "";
      proceed();
    },
  });
}

/** 删除已选视频 */
function removeVideo(): void {
  videoFile.value = "";
  videoCover.value = "";
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

/** 发布动态（图片由 publishMovement 内部 upload 直传后端；小视频走 publishVideo 直传封面+视频文件） */
async function handlePublish(): Promise<void> {
  if (!canPublish.value || submitting.value) return;
  // 部分端 chooseVideo 不返回封面帧，空封面上传会失败，发布前守卫
  if (videoFile.value && !videoCover.value) {
    uni.showToast({ title: "未获取到视频封面，请重新选择视频", icon: "none" });
    return;
  }
  submitting.value = true;
  let published = false;
  try {
    if (videoFile.value) {
      // 小视频链路：后端 POST /small-videos 仅接收 videoThumbnail + videoFile 两个字段
      await publishVideo({ thumbnailPath: videoCover.value, videoPath: videoFile.value });
    } else {
      await publishMovement({
        textContent: content.value.trim(),
        location: location.value,
        longitude: longitude.value,
        latitude: latitude.value,
        imagePaths: images.value,
        tags: selectedTopics.value,
      });
      // 通知圈子页：返回后刷新动态流（onShow 消费）
      uni.setStorageSync(CIRCLE_NEED_REFRESH_KEY, "1");
    }
    published = true;
    uni.showToast({ title: "发布成功", icon: "success" });
    backTimer = setTimeout(() => {
      // mp 端发布页为原生 tab 页，切换不销毁页面，须手动重置表单，避免下次进入残留已发布内容
      content.value = "";
      images.value = [];
      videoFile.value = "";
      videoCover.value = "";
      location.value = "";
      longitude.value = "";
      latitude.value = "";
      selectedTopics.value = [];
      safeGoBack("/pages/circle/index");
    }, 800);
  } catch {
    // request 层统一 toast
  } finally {
    // 发布成功后保持 submitting（返回前的窗口期不可重复提交），仅失败时复位
    if (!published) submitting.value = false;
  }
}

function onKb(res: { height: number }): void {
  keyboardOpen.value = res.height > 0;
}

onMounted(() => {
  const sys = uni.getSystemInfoSync();
  statusBarHeight.value = sys.statusBarHeight || 20;
  // H5 端无键盘高度 API，直接调用会抛错打断页面渲染；仅支持的端注册，H5 用输入框聚焦态兜底
  if (typeof uni.onKeyboardHeightChange === "function") {
    uni.onKeyboardHeightChange(onKb);
  }
});

onUnload(() => {
  if (typeof uni.offKeyboardHeightChange === "function") {
    uni.offKeyboardHeightChange(onKb);
  }
  if (backTimer) {
    clearTimeout(backTimer);
    backTimer = null;
  }
});
</script>

<style lang="scss" scoped>
/* 抖音式发布页：白底，顶部安全区，底部留自定义底栏空间 */
.publish-page {
  min-height: 100vh;
  background: #fff;
  padding: 0 28rpx;
  padding-bottom: calc(env(safe-area-inset-bottom) + 160rpx);
}

/* ===== 顶部栏 ===== */
.top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 88rpx;
  margin: 0 -28rpx;
  padding: 0 24rpx;
}

.icon-btn {
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 右侧发布按钮：品牌粉实心圆角，禁用态浅粉 */
.top-publish {
  height: 64rpx;
  padding: 0 36rpx;
  border-radius: 32rpx;
  background: #ff5e9c;
  display: flex;
  align-items: center;
  justify-content: center;

  text {
    color: #fff;
    font-size: 28rpx;
    font-weight: 600;
  }

  &.disabled {
    background: #ffd0dc;
  }
}

/* ===== 正文（无边框） ===== */
.content-area {
  width: 100%;
  min-height: 240rpx;
  margin-top: 16rpx;
  font-size: 30rpx;
  line-height: 1.6;
  color: #161823;
}

.placeholder {
  color: #9aa3af;
}

.char-count {
  display: block;
  text-align: right;
  font-size: 22rpx;
  color: #9aa3af;
}

/* ===== 横向媒体行 ===== */
.media-row {
  white-space: nowrap;
  margin: 16rpx -28rpx 8rpx;
  padding: 8rpx 28rpx;
}

.media-item {
  position: relative;
  display: inline-block;
  width: 180rpx;
  height: 180rpx;
  margin-right: 16rpx;
  vertical-align: top;

  .media-img {
    width: 100%;
    height: 100%;
    border-radius: 16rpx;
  }

  /* 已选视频左下角角标，区分图片与视频 */
  .media-video-badge {
    position: absolute;
    left: 12rpx;
    bottom: 12rpx;
    width: 40rpx;
    height: 40rpx;
    background: rgba(0, 0, 0, 0.6);
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .media-remove {
    position: absolute;
    top: -12rpx;
    right: -12rpx;
    width: 40rpx;
    height: 40rpx;
    background: rgba(0, 0, 0, 0.6);
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
  }
}

.media-add {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 180rpx;
  height: 180rpx;
  border-radius: 16rpx;
  background: #f5f6f8;
  vertical-align: top;
  gap: 10rpx;

  .media-add-label {
    font-size: 22rpx;
    color: #767676;
  }
}

/* ===== 设置列表（分组行，右箭头） ===== */
.setting-list {
  margin-top: 24rpx;
  border-top: 1rpx solid #f0f0f0;
}

.setting-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 96rpx;
  border-bottom: 1rpx solid #f0f0f0;

  .row-left {
    display: flex;
    align-items: center;
    gap: 16rpx;

    .row-name {
      font-size: 28rpx;
      color: #161823;
    }

    .row-hash {
      font-size: 32rpx;
      color: #161823;
      font-weight: 700;
    }
  }

  .row-right {
    display: flex;
    align-items: center;
    gap: 8rpx;
    max-width: 62%;

    .row-value {
      font-size: 26rpx;
      color: #161823;
      overflow: hidden;
      white-space: nowrap;
      text-overflow: ellipsis;

      &.placeholder {
        color: #9aa3af;
      }
    }
  }
}

/* ===== 话题选择面板 ===== */
.topic-panel {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  padding: 24rpx 0;
}

.topic-chip {
  padding: 12rpx 28rpx;
  border-radius: 32rpx;
  background: #f5f6f8;

  text {
    font-size: 24rpx;
    color: #333;
  }

  &.active {
    background: #ffeff5;

    text {
      color: #ff5e9c;
    }
  }
}
</style>
