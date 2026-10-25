<template>
  <view class="detail-page">
    <!-- 照片轮播 -->
    <swiper class="photo-swiper" circular indicator-dots indicator-color="rgba(255,255,255,0.4)" indicator-active-color="#fff" @change="onSwiperChange">
      <swiper-item v-for="(photo, i) in photos" :key="i">
        <image class="photo" :src="photo" mode="aspectFill" @click="previewPhoto(i)" />
      </swiper-item>
    </swiper>

    <!-- 返回按钮（毛玻璃） -->
    <view class="back-btn glass" :style="{ top: statusBarHeight + 8 + 'px' }" @click="goBack">
      <text>‹</text>
    </view>

    <!-- 照片计数 -->
    <view v-if="photos.length > 1" class="photo-count glass">
      <text>{{ currentPhotoIndex + 1 }} / {{ photos.length }}</text>
    </view>

    <!-- 更多按钮（毛玻璃）：举报入口 -->
    <view class="more-btn glass" :style="{ top: statusBarHeight + 8 + 'px' }" @click="handleMore">
      <text>⋯</text>
    </view>

    <!-- 资料卡 -->
    <view class="info-card shadow-card">
      <view class="name-row">
        <text class="nickname">{{ user?.nickname || '—' }}</text>
        <view class="age-badge"><text>{{ user?.age || '—' }}</text></view>
        <view v-if="user?.verified" class="verified"><text>✔ 已认证</text></view>
      </view>

      <!-- 缘分值 -->
      <view class="fate-row">
        <text class="fate-label">缘分值</text>
        <view class="fate-bar">
          <view class="fate-fill" :style="{ width: fatePercent }" />
        </view>
        <text class="fate-score">{{ fateScore }}</text>
      </view>

      <view class="meta-grid">
        <view class="meta-item"><text class="label">城市</text><text class="value">{{ user?.city || '—' }}</text></view>
        <view class="meta-item"><text class="label">身高</text><text class="value">{{ user?.height || '—' }}cm</text></view>
        <view class="meta-item"><text class="label">学历</text><text class="value">{{ user?.education || '—' }}</text></view>
        <view class="meta-item"><text class="label">行业</text><text class="value">{{ user?.industry || '—' }}</text></view>
      </view>
      <view v-if="user?.signature" class="signature">
        <text class="quote-mark">“</text>
        <text>{{ user.signature }}</text>
      </view>

      <!-- 陌生人问题 -->
      <view class="question-section">
        <text class="section-title">破冰问题</text>
        <view v-for="(q, i) in questions" :key="i" class="question-item">
          <view class="q-index"><text>{{ i + 1 }}</text></view>
          <text class="question">{{ q }}</text>
        </view>
      </view>
    </view>

    <!-- 底部操作栏：4 按钮 -->
    <view class="action-bar safe-area-bottom">
      <view class="action small dislike btn-feedback" @click="handleDislike"><text>✕</text></view>
      <view class="action small chat btn-feedback" @click="handleChat"><text>💬</text></view>
      <view class="action super-like btn-feedback" @click="handleSuperLike"><text>★</text></view>
      <view class="action like btn-feedback" @click="handleLike"><text>♥</text></view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import { submitReport, REPORT_TARGET_TYPE } from "@/api/mine";

/** 缘分值满分 */
const FATE_SCORE_MAX = 100;
/** 开发期演示缘分值（对接推荐分数接口后移除） */
const DEMO_FATE_SCORE = 86;
/** 默认状态栏高度（px） */
const DEFAULT_STATUS_BAR_HEIGHT = 20;
/** 举报原因选项 */
const REPORT_REASONS = ["骚扰", "欺诈", "色情信息", "冒充他人"];

const statusBarHeight = ref(DEFAULT_STATUS_BAR_HEIGHT);
const userId = ref("");
const user = ref<any>(null);
const photos = ref<string[]>([]);
const questions = ref<string[]>([]);
const currentPhotoIndex = ref(0);
const fateScore = ref(0);

/** 缘分值百分比（用于进度条宽度） */
const fatePercent = computed(() => `${Math.min(fateScore.value / FATE_SCORE_MAX, 1) * 100}%`);

/** 返回上一页 */
function goBack(): void {
  uni.navigateBack();
}

/** 轮播切换回调，更新照片计数 */
function onSwiperChange(e: any): void {
  currentPhotoIndex.value = e.detail.current;
}

/** 预览照片 */
function previewPhoto(index: number): void {
  uni.previewImage({ urls: photos.value, current: photos.value[index] });
}

/** 喜欢 */
async function handleLike(): Promise<void> {
  // TODO: 对接 POST /xfjy/{id}/love
  uni.showToast({ title: "已喜欢", icon: "success" });
  setTimeout(goBack, 800);
}

/** 超级喜欢（VIP 功能） */
async function handleSuperLike(): Promise<void> {
  // TODO: 对接超级喜欢接口（需 VIP 校验）
  uni.showToast({ title: "超级喜欢已送出 ★", icon: "none" });
  setTimeout(goBack, 800);
}

/** 打招呼，跳转聊天页 */
function handleChat(): void {
  const name = user.value?.nickname || "";
  uni.navigateTo({ url: `/pages/chat/chat?userId=${userId.value}&nickname=${encodeURIComponent(name)}` });
}

/** 不喜欢 */
async function handleDislike(): Promise<void> {
  // TODO: 对接 POST /xfjy/{id}/unlove
  setTimeout(goBack, 300);
}

/** 更多操作：举报入口 */
function handleMore(): void {
  uni.showActionSheet({
    itemList: ["举报该用户"],
    success: (res) => {
      if (res.tapIndex === 0) {
        chooseReportReason();
      }
    },
  });
}

/** 选择举报原因并提交 */
function chooseReportReason(): void {
  uni.showActionSheet({
    itemList: REPORT_REASONS,
    success: async (res) => {
      const reason = REPORT_REASONS[res.tapIndex];
      if (!reason || !userId.value) return;
      uni.showLoading({ title: "提交中..." });
      try {
        await submitReport({
          targetUserId: userId.value,
          targetType: REPORT_TARGET_TYPE.USER,
          reason,
        });
        uni.hideLoading();
        uni.showToast({ title: "举报已提交", icon: "success" });
      } catch (e) {
        uni.hideLoading();
        uni.showToast({ title: "提交失败，请重试", icon: "none" });
      }
    },
  });
}

onLoad((options: any) => {
  userId.value = options?.id || "";
  const sysInfo = uni.getSystemInfoSync();
  statusBarHeight.value = sysInfo.statusBarHeight || DEFAULT_STATUS_BAR_HEIGHT;
  // TODO: 对接 GET /users/{id}/personalInfo 和 GET /users/{id}/strangerQuestions
  // 开发期演示数据（接入接口后移除）
  fateScore.value = DEMO_FATE_SCORE;
});
</script>

<style lang="scss" scoped>
.detail-page { min-height: 100vh; background: #f5f5f5; padding-bottom: 160rpx; }

/* ===== 照片轮播 ===== */
.photo-swiper { height: 750rpx;
  .photo { width: 100%; height: 100%; }
}

.back-btn { position: absolute; left: 24rpx; width: 64rpx; height: 64rpx; border-radius: 50%; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 44rpx; z-index: 10; }

.more-btn { position: absolute; right: 24rpx; width: 64rpx; height: 64rpx; border-radius: 50%; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 40rpx; z-index: 10; }

.photo-count { position: absolute; right: 24rpx; top: 780rpx; padding: 6rpx 20rpx; border-radius: 20rpx; font-size: 22rpx; color: #fff; z-index: 10; }

/* ===== 资料卡 ===== */
.info-card { margin: -40rpx 24rpx 0; background: #fff; border-radius: 32rpx; padding: 36rpx 32rpx; position: relative; }

.name-row { display: flex; align-items: center; gap: 16rpx;
  .nickname { font-size: 42rpx; font-weight: bold; color: #222; }
  .age-badge { background: linear-gradient(135deg, #ff5e8e, #ff8fab); color: #fff; font-size: 24rpx; padding: 4rpx 16rpx; border-radius: 16rpx; }
  .verified { font-size: 20rpx; color: #ff5e8e; background: #fff0f5; padding: 4rpx 14rpx; border-radius: 16rpx; border: 1rpx solid #ffd6e4; }
}

/* 缘分值进度条 */
.fate-row { display: flex; align-items: center; gap: 16rpx; margin-top: 24rpx;
  .fate-label { font-size: 24rpx; color: #999; flex-shrink: 0; }
  .fate-bar { flex: 1; height: 16rpx; background: #f0f0f0; border-radius: 8rpx; overflow: hidden; }
  .fate-fill { height: 100%; background: linear-gradient(90deg, #ff5e8e, #ffd700); border-radius: 8rpx; transition: width 0.6s ease-out; }
  .fate-score { font-size: 30rpx; font-weight: bold; color: #ff5e8e; flex-shrink: 0; min-width: 48rpx; text-align: right; }
}

.meta-grid { display: flex; flex-wrap: wrap; gap: 20rpx; margin-top: 28rpx;
  .meta-item { width: calc(50% - 10rpx); background: #fafafa; border-radius: 16rpx; padding: 16rpx 20rpx;
    .label { font-size: 22rpx; color: #bbb; display: block; }
    .value { font-size: 28rpx; color: #333; margin-top: 6rpx; display: block; font-weight: 500; }
  }
}

.signature { font-size: 26rpx; color: #666; line-height: 1.6; margin-top: 24rpx; padding: 20rpx; background: linear-gradient(135deg, #fff8fb, #fff); border-radius: 16rpx; border-left: 6rpx solid #ff5e8e;
  .quote-mark { font-size: 40rpx; color: #ffd6e4; margin-right: 8rpx; vertical-align: -8rpx; }
}

.question-section { margin-top: 32rpx;
  .section-title { font-size: 30rpx; font-weight: bold; display: block; margin-bottom: 20rpx; color: #222; }
  .question-item { display: flex; align-items: flex-start; gap: 16rpx; padding: 18rpx 0; border-bottom: 1rpx solid #f5f5f5;
    &:last-child { border-bottom: none; }
    .q-index { width: 36rpx; height: 36rpx; border-radius: 50%; background: linear-gradient(135deg, #ff5e8e, #ff8fab); color: #fff; font-size: 22rpx; display: flex; align-items: center; justify-content: center; flex-shrink: 0; margin-top: 2rpx; }
    .question { font-size: 26rpx; color: #555; line-height: 1.5; }
  }
}

/* ===== 底部操作栏 ===== */
.action-bar { position: fixed; left: 0; right: 0; bottom: 0; display: flex; justify-content: center; align-items: center; gap: 36rpx; padding: 24rpx 0; background: #fff; box-shadow: 0 -4rpx 24rpx rgba(0, 0, 0, 0.06); }

.action { border-radius: 50%; display: flex; align-items: center; justify-content: center;
  /* 次按钮：不喜欢 / 打招呼 */
  &.small { width: 88rpx; height: 88rpx; font-size: 36rpx;
    &.dislike { background: #fff; color: #ff3b30; border: 4rpx solid #ff3b30; }
    &.chat { background: linear-gradient(135deg, #4a90d9, #6bb0f5); color: #fff; box-shadow: 0 6rpx 18rpx rgba(74, 144, 217, 0.35); }
  }
  /* 主按钮：超级喜欢 / 喜欢 */
  &.super-like { width: 100rpx; height: 100rpx; font-size: 46rpx; background: linear-gradient(135deg, #ffd700, #ffb347); color: #fff; box-shadow: 0 8rpx 22rpx rgba(255, 179, 71, 0.4); }
  &.like { width: 110rpx; height: 110rpx; font-size: 52rpx; background: linear-gradient(135deg, #ff5e8e, #ff8fab); color: #fff; box-shadow: 0 8rpx 24rpx rgba(255, 94, 142, 0.4); }
}
</style>