<template>
  <view class="publish-page">
    <!-- 断网/恢复全局提示条（fixed 定位自管理显隐，S1） -->
    <network-status-bar />
    <!-- 顶部栏：左侧关闭 + 居中标题 + 右侧发布按钮 -->
    <view
      class="top-bar"
      :style="{ paddingTop: statusBarHeight + 'px' }"
    >
      <a11y-button
        class="icon-btn"
        label="取消发布"
        @tap="handleCancel"
      >
        <uni-icons
          type="close"
          size="26"
          color="var(--text-primary)"
        />
      </a11y-button>
      <text class="top-title">
        发布动态
      </text>
      <a11y-button
        class="top-publish"
        :class="{ disabled: !canPublish || submitting }"
        :label="canPublish ? '发布动态' : '请先输入内容或选择图片'"
        @tap="handlePublish"
      >
        <text>{{ submitting ? '发布中' : '发布' }}</text>
      </a11y-button>
    </view>

    <!-- 上传进度条（M-C2）：提交中且有进度时展示，弱网不再无期等待 -->
    <view
      v-if="submitting && uploadProgress > 0"
      class="upload-progress"
      role="status"
      :aria-label="`上传进度 ${uploadProgress}%`"
    >
      <view class="upload-progress-track">
        <view
          class="upload-progress-fill"
          :style="{ transform: 'scaleX(' + uploadProgress / 100 + ')' }"
        />
      </view>
      <text class="upload-progress-text">
        {{ uploadProgress }}%
      </text>
    </view>

    <!-- 正文（无边框大输入，抖音式） -->
    <!-- 发布按钮禁用原因（P1）：明确告知可发布条件，避免"按钮灰了却不知道为什么" -->
    <view
      v-if="!canPublish"
      class="publish-hint"
      role="status"
    >
      <uni-icons
        type="info"
        size="14"
        color="var(--color-primary-text)"
      />
      <text class="publish-hint-text">
        写点文字，或添加图片 / 视频后即可发布
      </text>
    </view>

    <!-- 正文分组标题：让"写什么 / 还能写多少"一目了然 -->
    <view class="field-head">
      <text class="field-title">
        动态内容
      </text>
      <text class="field-hint">
        最多 {{ MAX_CONTENT_LENGTH }} 字
      </text>
    </view>
    <textarea
      v-model="content"
      class="content-area"
      placeholder="分享此刻的心情、遇见的有趣的事…"
      placeholder-class="placeholder"
      aria-label="动态正文"
      :maxlength="MAX_CONTENT_LENGTH"
      :auto-height="false"
    />
    <text
      class="char-count"
      :class="{ 'char-near-limit': content.length >= MAX_CONTENT_LENGTH - 50 }"
    >
      {{ content.length }} / {{ MAX_CONTENT_LENGTH }} 字
    </text>

    <!-- 媒体分组标题：说明图片上限，避免"点了没反应" -->
    <view class="field-head">
      <text class="field-title">
        添加图片或视频
      </text>
      <text class="field-hint">
        图片最多 {{ MAX_IMAGE_COUNT }} 张 · 视频 1 个
      </text>
    </view>
    <!-- 媒体行：横向缩略图 + 添加（抖音式）；选择视频后与图片互斥 -->
    <scroll-view
      class="media-row"
      scroll-x
      :show-scrollbar="false"
    >
      <template v-if="videoFile">
        <view class="media-item">
          <image
            lazy-load
            class="media-img"
            :src="videoCover"
            mode="aspectFill"
            aria-label="已选视频封面"
          />
          <view
            class="media-video-badge"
            aria-hidden="true"
          >
            <uni-icons
              type="videocam-filled"
              size="16"
              color="var(--text-inverse)"
            />
          </view>
          <a11y-button
            class="media-remove"
            label="删除已选视频"
            @tap="removeVideo"
          >
            <uni-icons
              type="close"
              size="13"
              color="var(--text-inverse)"
            />
          </a11y-button>
        </view>
      </template>
      <template v-else>
        <view
          v-for="(img, i) in images"
          :key="img.id"
          class="media-item"
        >
          <image
            lazy-load
            class="media-img"
            :src="img.url"
            mode="aspectFill"
            :aria-label="`已选图片 ${i + 1}`"
          />
          <a11y-button
            class="media-remove"
            :label="`删除第 ${i + 1} 张图片`"
            @tap="removeImage(i)"
          >
            <uni-icons
              type="close"
              size="13"
              color="var(--text-inverse)"
            />
          </a11y-button>
        </view>
        <a11y-button
          v-if="images.length < MAX_IMAGE_COUNT"
          class="media-add"
          label="选择图片"
          @tap="chooseImage"
        >
          <uni-icons
            type="image"
            size="30"
            color="var(--text-hint)"
          />
          <text class="media-add-label">
            发图片
          </text>
        </a11y-button>
        <a11y-button
          class="media-add"
          label="选择视频发布"
          @tap="chooseVideo"
        >
          <uni-icons
            type="videocam"
            size="30"
            color="var(--text-hint)"
          />
          <text class="media-add-label">
            发视频
          </text>
        </a11y-button>
      </template>
    </scroll-view>

    <!-- 附加信息分组标题：话题 / 位置有明确归属，不再只靠 placeholder 猜 -->
    <view
      v-if="!videoFile"
      class="field-head"
    >
      <text class="field-title">
        更多设置
      </text>
      <text class="field-hint">
        话题最多 {{ MAX_TOPIC_COUNT }} 个 · 可选
      </text>
    </view>
    <!-- 设置列表（抖音式分组行，右侧带箭头）；小视频发布仅接收封面+视频文件，文字/话题/位置不随视频上传，隐藏避免误导 -->
    <view
      v-if="!videoFile"
      class="setting-list"
    >
      <a11y-button
        class="setting-row"
        :label="`话题，已选 ${selectedTopics.length} 个`"
        @tap="toggleTopicPanel"
      >
        <view class="row-left">
          <text
            class="row-hash"
            aria-hidden="true"
          >
            #
          </text>
          <text class="row-name">
            话题
          </text>
        </view>
        <view class="row-right">
          <text
            class="row-value"
            :class="{ placeholder: selectedTopics.length === 0 }"
          >
            {{ selectedTopics.length ? selectedTopics.join("、") : "添加话题" }}
          </text>
          <uni-icons
            type="right"
            size="14"
            color="var(--text-hint)"
          />
        </view>
      </a11y-button>

      <a11y-button
        class="setting-row"
        :label="location ? `位置：${location}，点击修改` : '添加位置'"
        @tap="chooseLocation"
      >
        <view class="row-left">
          <uni-icons
            type="location"
            size="20"
            color="var(--text-primary)"
          />
          <text class="row-name">
            位置
          </text>
        </view>
        <view class="row-right">
          <text
            class="row-value"
            :class="{ placeholder: !location }"
          >
            {{ location || "添加位置" }}
          </text>
          <uni-icons
            type="right"
            size="14"
            color="var(--text-hint)"
          />
        </view>
      </a11y-button>
      <a11y-button
        v-if="location"
        class="setting-row"
        label="清除已选位置"
        @tap="clearLocation"
      >
        <view class="row-left">
          <uni-icons
            type="trash"
            size="20"
            color="var(--text-hint)"
          />
          <text class="row-name">
            清除位置
          </text>
        </view>
      </a11y-button>
    </view>

    <!-- 话题选择面板（点击话题行展开/收起） -->
    <view
      v-if="showTopicPanel"
      class="topic-panel"
    >
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
    <app-tabbar
      v-if="!keyboardOpen"
      :active="-1"
    />
    <!-- #endif -->
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from "vue";
import { onUnload, onBackPress } from "@dcloudio/uni-app";
import A11yButton from "@/components/a11y-button.vue";
import NetworkStatusBar from "@/components/network-status-bar.vue";
import AppTabbar from "@/components/AppTabbar.vue";
import { publishMovement } from "@/api/circle";
import { publishVideo } from "@/api/video";
import { CIRCLE_NEED_REFRESH_KEY } from "@/store/circle";
import { safeGoBack } from "@/utils/nav";
import { success, info, fail } from "@/utils/ux";
import {
  resolveAddressName,
  resolveCurrentLocation,
  locationDisplayName,
} from "@/utils/location";
import type { UploadPromise } from "@/utils/request";

/** 内容最大字数 */
const MAX_CONTENT_LENGTH = 500;
/** 图片最大数量 */
const MAX_IMAGE_COUNT = 9;
/** 话题最大数量：超出后不再追加，避免话题标签无限膨胀挤爆布局（BUG-08 收口） */
const MAX_TOPIC_COUNT = 3;

/** 热门话题推荐 */
const HOT_TOPICS = ["交友日常", "心动瞬间", "周末去哪儿", "美食探店", "运动打卡", "宠物萌照"];

const content = ref("");
/** 已选图片项：本地路径 + 单调 id。列表支持中间删除，用 index 当 key 会让 Vue 复用错 DOM */
interface PickedImage {
  id: number;
  url: string;
}
const images = ref<PickedImage[]>([]);
/** 图片项稳定 id 生成器（模块级单调递增，不随删除回收，避免 id 复用） */
let imageSeq = 0;
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
/** 上传进度（0-99；0 表示未开始/纯文字无进度回传） */
const uploadProgress = ref(0);
/** 在途上传句柄（abort 取消用，M-C2） */
let uploadHandle: UploadPromise<void> | null = null;

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

/** 取消在途上传并复位进度（M-C2：弱网可中断，不再无期等待） */
function abortUpload(): void {
  uploadHandle?.abort();
  uploadHandle = null;
  uploadProgress.value = 0;
}

/** 手势/系统返回拦截：有未发布内容时弹确认，避免误触丢弃（M-C3） */
onBackPress(() => {
  // 提交在途：询问是否取消上传并离开（M-C2：弱网可中断，不再无期等待）
  if (submitting.value) {
    uni.showModal({
      title: "取消上传？",
      content: "内容正在上传中，确定取消并离开吗？",
      confirmText: "取消上传",
      cancelText: "继续等待",
      success: (res) => {
        if (res.confirm) {
          abortUpload();
          submitting.value = false;
          safeGoBack("/pages/circle/index");
        }
      },
    });
    return true;
  }
  // 无内容：放行默认返回
  if (!canPublish.value) return false;
  // 有未发布内容：弹确认，拦截默认返回由回调决定去向
  uni.showModal({
    title: "放弃发布？",
    content: "内容尚未发布，确定离开吗？",
    confirmText: "离开",
    cancelText: "继续编辑",
    success: (res) => {
      if (res.confirm) {
        content.value = "";
        images.value = [];
        videoFile.value = "";
        videoCover.value = "";
        safeGoBack("/pages/circle/index");
      }
    },
  });
  return true;
});

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
    return;
  }
  // 上限拦截：静默 push 会让用户以为「点了没反应」，必须提示（BUG-08 收口）
  if (selectedTopics.value.length >= MAX_TOPIC_COUNT) {
    fail(`最多选择 ${MAX_TOPIC_COUNT} 个话题`);
    return;
  }
  selectedTopics.value.push(topic);
}

/** 选择图片 */
function chooseImage(): void {
  uni.chooseImage({
    count: MAX_IMAGE_COUNT - images.value.length,
    sizeType: ["compressed"],
    success: (res) => {
      // 图片由 publishMovement 内部 upload() 直传后端，此处仅暂存本地路径；
      // 每项分配稳定 id 供 v-for key 使用。uni 类型声明为 string | string[]，先归一数组
      const paths = Array.isArray(res.tempFilePaths)
        ? res.tempFilePaths
        : [res.tempFilePaths];
      images.value.push(...paths.map((url) => ({ id: ++imageSeq, url })));
    },
    fail: handleMediaFail,
  });
}

/** chooseImage/chooseVideo fail：区分用户取消（静默）与授权拒绝（提示 + MP 端引导打开设置页） */
function handleMediaFail(err: any): void {
  const msg = String(err?.errMsg || "");
  if (msg.includes("cancel")) return;
  info("需要相册/相机权限才能选择媒体");
  const openSetting = (uni as any).openSetting;
  if (typeof openSetting === "function") {
    openSetting({});
  }
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
      fail: handleMediaFail,
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

/** 选择位置：自动取当前定位（设备优先、IP 兜底），地址名服务端解析 */
async function chooseLocation(): Promise<void> {
  const loc = await resolveCurrentLocation();
  if (!loc) {
    info("当前环境无法获取位置：H5 需 HTTPS 访问并允许定位授权");
    return;
  }
  const addr = await resolveAddressName(loc.latitude, loc.longitude);
  const fallback = loc.source === "ip" ? "当前位置（城市级）" : "当前位置";
  location.value = locationDisplayName(addr, fallback);
  longitude.value = String(loc.longitude);
  latitude.value = String(loc.latitude);
}

/** 清除位置 */
function clearLocation(): void {
  location.value = "";
  longitude.value = "";
  latitude.value = "";
}

/**
 * 发布视频时尽力取定位：设备定位优先、IP 兜底；地址名走服务端逆地理。
 * 失败/无城市名都不阻塞发布（locationName 可为空）。
 */
async function resolveVideoLocation(): Promise<{
  longitude?: number;
  latitude?: number;
  locationName?: string;
}> {
  const loc = await resolveCurrentLocation();
  if (!loc) {
    return {};
  }
  const addr = await resolveAddressName(loc.latitude, loc.longitude);
  return {
    longitude: loc.longitude,
    latitude: loc.latitude,
    locationName: addr?.city || addr?.district || addr?.name || undefined,
  };
}

/** 发布动态（图片由 publishMovement 内部 upload 直传后端；小视频走 publishVideo 直传封面+视频文件） */
async function handlePublish(): Promise<void> {
  if (!canPublish.value || submitting.value) return;
  // 部分端 chooseVideo 不返回封面帧，空封面上传会失败，发布前守卫
  if (videoFile.value && !videoCover.value) {
    fail("未获取到视频封面，请重新选择视频");
    return;
  }
  submitting.value = true;
  let published = false;
  try {
    if (videoFile.value) {
      // 小视频链路：后端 POST /small-videos 接收 videoThumbnail + videoFile + 可选定位
      const loc = await resolveVideoLocation();
      uploadHandle = publishVideo(
        { thumbnailPath: videoCover.value, videoPath: videoFile.value, ...loc },
        (p) => { uploadProgress.value = p; },
      );
      await uploadHandle;
    } else {
      uploadHandle = publishMovement(
        {
          textContent: content.value.trim(),
          location: location.value,
          longitude: longitude.value,
          latitude: latitude.value,
          imagePaths: images.value.map((img) => img.url),
          tags: selectedTopics.value,
        },
        (p) => { uploadProgress.value = p; },
      );
      await uploadHandle;
      // 通知圈子页：返回后刷新动态流（onShow 消费）
      uni.setStorageSync(CIRCLE_NEED_REFRESH_KEY, "1");
    }
    uploadHandle = null;
    published = true;
    success("发布成功");
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
      // 先复位 submitting：否则 safeGoBack→navigateBack 会触发 onBackPress，
      // 此时 submitting=true 会误走「取消上传」分支并再次 navigateBack，形成循环报
      // navigateBack:fail onBackPress（H5 端 onBackPress 拦截即视为导航失败）
      submitting.value = false;
      safeGoBack("/pages/circle/index");
    }, 800);
  } catch {
    // request 层统一 toast
  } finally {
    // 发布成功后保持 submitting（返回前的窗口期不可重复提交），仅失败时复位
    if (!published) {
      submitting.value = false;
      uploadProgress.value = 0;
      uploadHandle = null;
    }
  }
}

function onKb(res: { height: number }): void {
  keyboardOpen.value = res.height > 0;
}

onMounted(() => {
  // getSystemInfoSync 为废弃 API，改用 getWindowInfo（旧运行时无此 API 时取兜底值 20）
  const win = uni.getWindowInfo?.();
  statusBarHeight.value = win?.statusBarHeight || 20;
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
  // 手势返回等非 safeGoBack 路径会跳过 800ms 定时器，submitting 保持 true 会导致
  // 下次进入页面无法发布；页面销毁时必须复位
  submitting.value = false;
});
</script>

<style lang="scss" scoped>
/* 抖音式发布页：白底，顶部安全区，底部留自定义底栏空间 */
.publish-page {
  min-height: 100vh;
  background: var(--bg-card);
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

/* 居中页面标题：发布页此前只靠 placeholder 辨识 */
.top-title {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--text-primary);
  flex: 1;
  text-align: center;
  margin-right: -64rpx; /* 抵消左侧关闭按钮宽度，保持视觉居中 */
}

/* 右侧发布按钮：品牌玫红实心圆角，禁用态浅粉 */
.top-publish {
  height: 64rpx;
  padding: 0 36rpx;
  border-radius: 32rpx;
  background: var(--gradient-brand);
  display: flex;
  align-items: center;
  justify-content: center;

  text {
    color: var(--text-inverse);
    font-size: 28rpx;
    font-weight: 600;
  }

  &.disabled {
    background: var(--bg-sub);

    /* 原白字浅灰底不可读：改用 --text-hint，并在暗色下由 token 自动适配 */
    text {
      color: var(--text-hint);
    }
  }
}

/* ===== 上传进度条（M-C2）===== */
.upload-progress {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 12rpx 28rpx;
  background: var(--bg-card);
}
.upload-progress-track {
  flex: 1;
  height: 6rpx;
  border-radius: 3rpx;
  background: var(--border-faint);
  overflow: hidden;
}
.upload-progress-fill {
  /* scaleX 模拟填充：transform 只走合成层，width 动画会每帧触发布局 */
  width: 100%;
  height: 100%;
  border-radius: 3rpx;
  background: var(--color-primary);
  transform-origin: left center;
  transition: transform 0.2s ease;
}
.upload-progress-text {
  font-size: var(--fs-sm);
  color: var(--text-hint);
  min-width: 64rpx;
  text-align: right;
}

/* ===== 分组标题 / 禁用原因提示 ===== */
.field-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16rpx;
  margin-top: 28rpx;
  margin-bottom: 4rpx;

  .field-title {
    font-size: 28rpx;
    font-weight: 600;
    color: var(--text-primary);
  }

  .field-hint {
    font-size: 22rpx;
    color: var(--text-hint);
  }
}

.publish-hint {
  display: flex;
  align-items: center;
  gap: 10rpx;
  margin-top: 20rpx;
  padding: 16rpx 20rpx;
  border-radius: var(--radius-md);
  background: var(--bg-icon-soft);
  border: 1rpx solid var(--color-primary-mist);

  .publish-hint-text {
    font-size: 24rpx;
    color: var(--color-primary-text);
  }
}

/* ===== 正文（无边框） ===== */
.content-area {
  width: 100%;
  min-height: 240rpx;
  margin-top: 16rpx;
  font-size: 30rpx;
  line-height: 1.6;
  color: var(--text-primary);
}

.placeholder {
  color: var(--text-hint);
}

.char-count {
  display: block;
  text-align: right;
  font-size: 22rpx;
  color: var(--text-hint);

  &.char-near-limit { color: var(--color-danger); font-weight: 600; }
}

/* ===== 横向媒体行 =====
   去掉负 margin（原先用 -28rpx 抵消父级 padding 来贴边，窄屏下会让
   媒体行超出卡片边界）；改为容器内滚动，溢出裁切在容器内完成 */
.media-row {
  display: flex;
  flex-wrap: nowrap;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  white-space: nowrap;
  margin: 16rpx 0 8rpx;
  padding: 8rpx 0;
}

.media-item {
  position: relative;
  display: inline-block;
  flex-shrink: 0;
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
    /* 视觉 40rpx 圆点，热区扩至 88rpx（WCAG 2.5.5）：padding 24rpx 透明外扩，
       负 margin 抵消位移保持视觉位置，background-clip 让底色只画 content 区 */
    width: 88rpx;
    height: 88rpx;
    padding: 24rpx;
    margin: -24rpx;
    background: rgba(0, 0, 0, 0.6);
    background-clip: content-box;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
  }
}

/* 媒体选择块：加虚线边框 + 引导文案，让"可点"的意图更明确 */
.media-add {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 180rpx;
  height: 180rpx;
  border-radius: 16rpx;
  /* 走 CSS 变量：写死 #faf9fd/#c9bede/#7a6a94 在暗色模式下仍是浅紫底 */
  background: var(--bg-sub);
  border: 2rpx dashed var(--border-light);
  vertical-align: top;
  gap: 10rpx;

  .media-add-label {
    font-size: 22rpx;
    color: var(--text-secondary);
    font-weight: 500;
  }
}

/* ===== 设置列表（分组行，右箭头） ===== */
.setting-list {
  margin-top: 24rpx;
  border-top: 1rpx solid var(--border-light);
}

.setting-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 96rpx;
  border-bottom: 1rpx solid var(--border-light);

  .row-left {
    display: flex;
    align-items: center;
    gap: 16rpx;

    .row-name {
      font-size: 28rpx;
      color: var(--text-primary);
    }

    .row-hash {
      font-size: 32rpx;
      color: var(--text-primary);
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
      color: var(--text-primary);
      overflow: hidden;
      white-space: nowrap;
      text-overflow: ellipsis;

      &.placeholder {
        color: var(--text-hint);
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
  /* 走 --bg-sub：写死 #f5f6f8 在暗色模式下仍是浅灰 */
  background: var(--bg-sub);

  text {
    font-size: 24rpx;
    color: var(--text-primary);
  }

  &.active {
    background: var(--bg-icon-soft);

    text {
      color: var(--color-primary-text);
    }
  }
}
</style>
