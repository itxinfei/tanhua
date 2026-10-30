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
        <view class="meta-item"><text class="label">学历</text><text class="value">{{ user?.edu || '—' }}</text></view>
        <view class="meta-item"><text class="label">行业</text><text class="value">{{ user?.industry || '—' }}</text></view>
      </view>

      <!-- 个人标签 -->
      <view v-if="user?.tags && user.tags.length > 0" class="tags-section">
        <text class="section-title">个人标签</text>
        <view class="tags-wrap">
          <view v-for="(tag, i) in user.tags" :key="i" class="tag"><text>{{ tag }}</text></view>
        </view>
      </view>

      <!-- 陌生人问题 -->
      <view v-if="questions.length > 0" class="question-section">
        <text class="section-title">破冰问题</text>
        <view v-for="(q, i) in questions" :key="i" class="question-item">
          <view class="q-index"><text>{{ i + 1 }}</text></view>
          <text class="question">{{ q }}</text>
        </view>
      </view>
    </view>

    <!-- TA的动态 -->
    <view v-if="movements.length > 0" class="movements-card shadow-card">
      <text class="section-title">TA的动态</text>
      <view v-for="m in movements" :key="m.id" class="movement-item" @click="goMovementDetail(m.id)">
        <image v-if="m.imageContent && m.imageContent.length > 0" class="m-cover" :src="m.imageContent[0]" mode="aspectFill" lazy-load />
        <view class="m-body">
          <text class="m-text ellipsis-2">{{ m.textContent }}</text>
          <view class="m-stats">
            <text>♥ {{ m.likeCount }}</text>
            <text>💬 {{ m.commentCount }}</text>
          </view>
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
import { submitReport, REPORT_TARGET_TYPE, checkBenefit, recordBenefitUsage } from "@/api/mine";
import { getPersonalInfo, getStrangerQuestion, loveUser, unloveUser, type TodayBest } from "@/api/match";
import { getUserMovements, type Movement } from "@/api/circle";

/** 缘分值满分 */
const FATE_SCORE_MAX = 100;
/** 默认状态栏高度（px） */
const DEFAULT_STATUS_BAR_HEIGHT = 20;
/** 举报原因选项 */
const REPORT_REASONS = ["骚扰", "欺诈", "色情信息", "冒充他人"];
/** 动态加载页大小 */
const MOVEMENT_PAGE_SIZE = 10;

const statusBarHeight = ref(DEFAULT_STATUS_BAR_HEIGHT);
const userId = ref("");
const user = ref<TodayBest | null>(null);
const photos = ref<string[]>([]);
const questions = ref<string[]>([]);
const movements = ref<Movement[]>([]);
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

/** photoAlbum 兼容 string(JSON)/string[]/undefined，统一归一为 string[] */
function normalizeAlbum(raw: string | string[] | undefined): string[] {
  if (!raw) return [];
  if (Array.isArray(raw)) return raw.filter(Boolean);
  try {
    const arr = JSON.parse(raw);
    return Array.isArray(arr) ? arr.filter(Boolean) : [];
  } catch {
    return [];
  }
}

/** 加载用户资料 */
async function loadUserInfo(): Promise<void> {
  if (!userId.value) return;
  try {
    const info = await getPersonalInfo(userId.value);
    user.value = info;
    fateScore.value = info?.fateValue || 0;
    // 照片轮播：头像 + 照片墙（photo_album）+ 动态图片，去重
    const avatar = info?.avatar || "";
    const albumPhotos = normalizeAlbum(info?.photoAlbum);
    const mediaPhotos = movements.value
      .flatMap((m) => m.imageContent || [])
      .filter((url) => /\.(jpe?g|png|webp|gif)$/i.test(url));
    const all = [...(avatar ? [avatar] : []), ...albumPhotos, ...mediaPhotos];
    photos.value = [...new Set(all)];
  } catch {
    // 请求失败由 request 层统一 toast
  }
}

/** 加载破冰问题 */
async function loadQuestion(): Promise<void> {
  if (!userId.value) return;
  try {
    const q = await getStrangerQuestion(userId.value);
    questions.value = q ? [q] : [];
  } catch {
    questions.value = [];
  }
}

/** 加载TA的动态 */
async function loadMovements(): Promise<void> {
  if (!userId.value) return;
  try {
    const res = await getUserMovements(userId.value, { page: 1, pagesize: MOVEMENT_PAGE_SIZE });
    movements.value = res.items || [];
  } catch {
    movements.value = [];
  }
}

/** 喜欢 */
async function handleLike(): Promise<void> {
  if (!userId.value) return;
  try {
    const matched = await loveUser(userId.value);
    uni.showToast({ title: matched ? "互相喜欢，已匹配！" : "已喜欢", icon: "success" });
    setTimeout(goBack, 800);
  } catch {
    // 请求失败由 request 层统一 toast
  }
}

/** 超级喜欢（VIP 权益：super_like，VIP1 每日 5 次） */
async function handleSuperLike(): Promise<void> {
  if (!userId.value) return;
  try {
    const benefit = await checkBenefit("super_like");
    if (!benefit.hasBenefit) {
      uni.showToast({ title: "超级喜欢需开通VIP", icon: "none" });
      setTimeout(() => uni.navigateTo({ url: "/pages/mine/vip" }), 600);
      return;
    }
    if (benefit.remainingToday === 0) {
      uni.showToast({ title: "今日超级喜欢次数已用完", icon: "none" });
      return;
    }
    const ok = await recordBenefitUsage("super_like");
    if (!ok) {
      uni.showToast({ title: "今日超级喜欢次数已用完", icon: "none" });
      return;
    }
    // 超级喜欢=加权喜欢，复用喜欢接口上报
    const matched = await loveUser(userId.value);
    uni.showToast({ title: matched ? "超级喜欢，已匹配！" : "已超级喜欢", icon: "success" });
    setTimeout(goBack, 800);
  } catch {
    // 请求失败由 request 层统一 toast
  }
}

/** 打招呼，跳转聊天页 */
function handleChat(): void {
  const name = user.value?.nickname || "";
  uni.navigateTo({ url: `/pages/chat/chat?userId=${userId.value}&nickname=${encodeURIComponent(name)}` });
}

/** 不喜欢 */
async function handleDislike(): Promise<void> {
  if (!userId.value) {
    setTimeout(goBack, 300);
    return;
  }
  try {
    await unloveUser(userId.value);
  } finally {
    setTimeout(goBack, 300);
  }
}

/** 跳转动态详情 */
function goMovementDetail(id: string): void {
  uni.navigateTo({ url: `/pages/circle/detail?id=${id}` });
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

onLoad(async (options: any) => {
  userId.value = options?.id || "";
  const sysInfo = uni.getSystemInfoSync();
  statusBarHeight.value = sysInfo.statusBarHeight || DEFAULT_STATUS_BAR_HEIGHT;
  // 先加载动态（照片轮播需要动态图片），再加载资料
  await loadMovements();
  await Promise.all([loadUserInfo(), loadQuestion()]);
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
  .age-badge { background: linear-gradient(135deg, #ff5e9c, #ff8fb1); color: #fff; font-size: 24rpx; padding: 4rpx 16rpx; border-radius: 16rpx; }
}

/* 缘分值进度条 */
.fate-row { display: flex; align-items: center; gap: 16rpx; margin-top: 24rpx;
  .fate-label { font-size: 24rpx; color: #999; flex-shrink: 0; }
  .fate-bar { flex: 1; height: 16rpx; background: #f0f0f0; border-radius: 8rpx; overflow: hidden; }
  .fate-fill { height: 100%; background: linear-gradient(90deg, #ff5e9c, #ffd700); border-radius: 8rpx; transition: width 0.6s ease-out; }
  .fate-score { font-size: 30rpx; font-weight: bold; color: #ff5e9c; flex-shrink: 0; min-width: 48rpx; text-align: right; }
}

.meta-grid { display: flex; flex-wrap: wrap; gap: 20rpx; margin-top: 28rpx;
  .meta-item { flex: 1; min-width: calc(33.33% - 14rpx); background: #fafafa; border-radius: 16rpx; padding: 16rpx 20rpx;
    .label { font-size: 22rpx; color: #bbb; display: block; }
    .value { font-size: 28rpx; color: #333; margin-top: 6rpx; display: block; font-weight: 500; }
  }
}

/* 个人标签 */
.tags-section { margin-top: 28rpx;
  .tags-wrap { display: flex; flex-wrap: wrap; gap: 12rpx; margin-top: 16rpx; }
  .tag { background: #ffeff5; color: #ff5e9c; font-size: 24rpx; padding: 8rpx 20rpx; border-radius: 20rpx; border: 1rpx solid #ffd6e4; }
}

.question-section { margin-top: 32rpx;
  .question-item { display: flex; align-items: flex-start; gap: 16rpx; padding: 18rpx 0; border-bottom: 1rpx solid #f5f5f5;
    &:last-child { border-bottom: none; }
    .q-index { width: 36rpx; height: 36rpx; border-radius: 50%; background: linear-gradient(135deg, #ff5e9c, #ff8fb1); color: #fff; font-size: 22rpx; display: flex; align-items: center; justify-content: center; flex-shrink: 0; margin-top: 2rpx; }
    .question { font-size: 26rpx; color: #555; line-height: 1.5; }
  }
}

.section-title { font-size: 30rpx; font-weight: bold; display: block; margin-bottom: 12rpx; color: #222; }

/* ===== TA的动态 ===== */
.movements-card { margin: 24rpx 24rpx 0; background: #fff; border-radius: 32rpx; padding: 32rpx; }

.movement-item { display: flex; gap: 20rpx; padding: 20rpx 0; border-bottom: 1rpx solid #f5f5f5;
  &:last-child { border-bottom: none; }
  .m-cover { width: 160rpx; height: 160rpx; border-radius: 16rpx; background: #f0f0f0; flex-shrink: 0; }
  .m-body { flex: 1; display: flex; flex-direction: column; justify-content: space-between; min-width: 0; }
  .m-text { font-size: 26rpx; color: #333; line-height: 1.5; }
  .m-stats { display: flex; gap: 24rpx;
    text { font-size: 22rpx; color: #999; }
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
  &.like { width: 110rpx; height: 110rpx; font-size: 52rpx; background: linear-gradient(135deg, #ff5e9c, #ff8fb1); color: #fff; box-shadow: 0 8rpx 24rpx rgba(255, 94, 142, 0.4); }
}
</style>