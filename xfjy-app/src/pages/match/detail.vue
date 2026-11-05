<template>
  <view class="detail-page">
    <!-- 照片轮播 -->
    <swiper class="photo-swiper" circular indicator-dots indicator-color="rgba(255,255,255,0.4)" indicator-active-color="#fff" @change="onSwiperChange">
      <swiper-item v-for="(photo, i) in photos" :key="i">
        <image lazy-load
          class="photo"
          :src="photo"
          mode="aspectFill"
          :aria-label="`用户照片 ${i + 1}，共 ${photos.length} 张`"
          @click="previewPhoto(i)"
        />
      </swiper-item>
    </swiper>

    <!-- 返回按钮（毛玻璃）：纯符号必须提供 aria-label，否则读屏只播符号名 -->
    <a11y-button class="back-btn glass" :style="{ top: statusBarHeight + 8 + 'px' }" label="返回上一页" @tap="goBack">
      <view class="i-ph-arrow-left" aria-hidden="true" />
    </a11y-button>

    <!-- 照片计数 -->
    <view v-if="photos.length > 1" class="photo-count glass">
      <text>{{ currentPhotoIndex + 1 }} / {{ photos.length }}</text>
    </view>

    <!-- 更多按钮（毛玻璃）：举报入口，纯符号需 aria-label -->
    <a11y-button class="more-btn glass" :style="{ top: statusBarHeight + 8 + 'px' }" label="更多操作，举报该用户" @tap="handleMore">
      <text>⋯</text>
    </a11y-button>

    <!-- 资料卡 -->
    <view class="info-card shadow-card">
      <view class="name-row">
        <text class="nickname">{{ user?.nickname || '—' }}</text>
        <view class="age-badge"><text>{{ user?.age || '—' }}</text></view>
      </view>

      <!-- 缘分值 -->
      <!-- 缘分值：进度条需暴露数值语义，否则读屏只见一个空条 -->
      <view class="fate-row">
        <text class="fate-label">缘分值</text>
        <view
          class="fate-bar"
          role="progressbar"
          aria-label="缘分值"
          :aria-valuenow="fateScore"
          :aria-valuemin="0"
          :aria-valuemax="FATE_SCORE_MAX"
          :aria-valuetext="`${fateScore} 分，满分 ${FATE_SCORE_MAX} 分`"
        >
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
      <!-- 动态项：改为可聚焦按钮；不设 aria-label 以保留内部正文朗读 -->
      <a11y-button v-for="m in movements" :key="m.id" class="movement-item" @tap="goMovementDetail(m.id)">
        <image lazy-load v-if="m.imageContent && m.imageContent.length > 0" class="m-cover" :src="m.imageContent[0]" mode="aspectFill" aria-label="动态封面图片" />
        <view class="m-body">
          <text class="m-text ellipsis-2">{{ m.textContent }}</text>
          <view class="m-stats">
            <text><uni-icons type="heart-filled" size="14" color="var(--color-primary-text)"></uni-icons> {{ m.likeCount }}</text>
            <text><uni-icons type="chatbubble" size="14" color="var(--text-hint)"></uni-icons> {{ m.commentCount }}</text>
          </view>
        </view>
      </a11y-button>
    </view>

    <!-- 底部操作栏：4 按钮，全部补齐语义与键盘操作 -->
    <view class="action-bar safe-area-bottom">
      <a11y-button class="action small dislike" label="不喜欢" @tap="handleDislike"><uni-icons type="close" size="22" color="var(--color-primary)"></uni-icons></a11y-button>
      <a11y-button class="action small chat" label="打招呼" @tap="handleChat"><uni-icons type="chatbubble" size="22" color="var(--color-primary)"></uni-icons></a11y-button>
      <a11y-button class="action super-like" label="超级喜欢" @tap="handleSuperLike"><uni-icons type="star-filled" size="22" color="#4dabf7"></uni-icons></a11y-button>
      <a11y-button class="action like" label="喜欢" @tap="handleLike"><uni-icons type="heart-filled" size="22" color="var(--color-primary)"></uni-icons></a11y-button>
    </view>

    <!-- 匹配成功弹窗（与匹配页行为一致） -->
    <MatchPopup />
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import A11yButton from "@/components/a11y-button.vue";
import MatchPopup from "@/components/match-popup.vue";
import { useMatchStore } from "@/store/match";
import { submitReport, REPORT_TARGET_TYPE, checkBenefit, recordBenefitUsage } from "@/api/mine";
import { getPersonalInfo, getStrangerQuestion, loveUser, unloveUser, type TodayBest } from "@/api/match";
import { getUserMovements, type Movement } from "@/api/circle";
import { safeGoBack } from "@/utils/nav";
import { success, info, fail, loading, hideLoading } from "@/utils/ux";

/** 缘分值满分 */
const FATE_SCORE_MAX = 100;
/** 默认状态栏高度（px） */
const DEFAULT_STATUS_BAR_HEIGHT = 20;
/** 举报原因选项 */
const REPORT_REASONS = ["骚扰", "欺诈", "色情信息", "冒充他人"];
/** 动态加载页大小 */
const MOVEMENT_PAGE_SIZE = 10;

const matchStore = useMatchStore();

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

/** 返回上一页（无历史栈时兜底回匹配 tab） */
function goBack(): void {
  safeGoBack("/pages/match/index");
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

/** 操作进行中标记（防连点：喜欢/超级喜欢/不喜欢在途时忽略后续点击） */
const acting = ref(false);

/** 喜欢 */
async function handleLike(): Promise<void> {
  if (!userId.value || acting.value) return;
  acting.value = true;
  try {
    const matched = await loveUser(userId.value);
    if (matched) {
      matchStore.showMatchPopup({
        userId: userId.value,
        nickname: user.value?.nickname || "",
        avatar: user.value?.avatar || "",
      });
    }
    success(matched ? "互相喜欢，已匹配！" : "已喜欢");
    // 匹配成功时留在本页展示匹配弹窗，由弹窗承接后续；未匹配才自动返回
    if (!matched) setTimeout(goBack, 800);
  } catch {
    // 请求失败由 request 层统一 toast
  } finally {
    acting.value = false;
  }
}

/** 超级喜欢（VIP 权益：super_like，VIP1 每日 5 次） */
async function handleSuperLike(): Promise<void> {
  if (!userId.value || acting.value) return;
  acting.value = true;
  try {
    const benefit = await checkBenefit("super_like");
    if (!benefit.hasBenefit) {
      info("超级喜欢需开通VIP");
      setTimeout(() => uni.navigateTo({ url: "/pages/mine/vip" }), 600);
      return;
    }
    if (benefit.remainingToday === 0) {
      info("今日超级喜欢次数已用完");
      return;
    }
    // 先上报喜欢，成功后再扣权益：后端无权益回滚接口，先扣后爱会导致请求失败白扣一次
    const matched = await loveUser(userId.value);
    const ok = await recordBenefitUsage("super_like");
    if (!ok) {
      // 并发下名额可能已被用尽：喜欢已生效，仅提示
      info("今日超级喜欢次数已用完");
    }
    if (matched) {
      matchStore.showMatchPopup({
        userId: userId.value,
        nickname: user.value?.nickname || "",
        avatar: user.value?.avatar || "",
      });
    }
    success(matched ? "超级喜欢，已匹配！" : "已超级喜欢");
    if (!matched) setTimeout(goBack, 800);
  } catch {
    // 请求失败由 request 层统一 toast
  } finally {
    acting.value = false;
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
  if (acting.value) return;
  acting.value = true;
  try {
    await unloveUser(userId.value);
  } finally {
    acting.value = false;
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
      loading("提交中...");
      try {
        await submitReport({
          targetUserId: userId.value,
          targetType: REPORT_TARGET_TYPE.USER,
          reason,
        });
        hideLoading();
        success("举报已提交");
      } catch (e) {
        hideLoading();
        fail("提交失败，请重试");
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
.detail-page { min-height: 100vh; background: var(--bg-sub); padding-bottom: 160rpx; }

/* ===== 照片轮播 ===== */
.photo-swiper { height: 750rpx;
  .photo { width: 100%; height: 100%; }
}

/* 悬浮在照片上的圆形按钮：88rpx 是相对单位，窄屏（320px）会等比缩到 37.5px，
   低于 WCAG 2.5.5 的 44px 物理下限；用 max() 保底，保证任何视口都不缩。
   a11y-button 默认 variant=default（无样式），尺寸在此自理。 */
.back-btn { position: absolute; left: 24rpx; width: max(88rpx, 44px); height: max(88rpx, 44px); border-radius: 50%; display: flex; align-items: center; justify-content: center; color: var(--text-inverse); font-size: 44rpx; z-index: 10; box-sizing: border-box; }

.more-btn { position: absolute; right: 24rpx; width: max(88rpx, 44px); height: max(88rpx, 44px); border-radius: 50%; display: flex; align-items: center; justify-content: center; color: var(--text-inverse); font-size: 40rpx; z-index: 10; box-sizing: border-box; }

.photo-count { position: absolute; right: 24rpx; top: 780rpx; padding: 6rpx 20rpx; border-radius: 20rpx; font-size: 22rpx; color: var(--text-inverse); z-index: 10; }

/* ===== 资料卡 ===== */
.info-card { margin: -40rpx 24rpx 0; background: var(--bg-card); border-radius: 32rpx; padding: 36rpx 32rpx; position: relative; }

.name-row { display: flex; align-items: center; gap: 16rpx;
  .nickname { font-size: 42rpx; font-weight: bold; color: var(--text-primary); }
  .age-badge { background: var(--gradient-brand); color: var(--bg-card); font-size: 24rpx; padding: 4rpx 16rpx; border-radius: 16rpx; }
}

/* 缘分值进度条 */
.fate-row { display: flex; align-items: center; gap: 16rpx; margin-top: 24rpx;
  .fate-label { font-size: 24rpx; color: var(--text-hint); flex-shrink: 0; }
  .fate-bar { flex: 1; height: 16rpx; background: var(--bg-sub); border-radius: 8rpx; overflow: hidden; }
  .fate-fill { height: 100%; background: var(--gradient-brand-h); border-radius: 8rpx; transition: width 0.6s ease-out; }
  .fate-score { font-size: 30rpx; font-weight: bold; color: var(--color-primary); flex-shrink: 0; min-width: 48rpx; text-align: right; }
}

.meta-grid { display: flex; flex-wrap: wrap; gap: 20rpx; margin-top: 28rpx;
  .meta-item { flex: 1; min-width: calc(33.33% - 14rpx); background: var(--bg-sub); border-radius: 16rpx; padding: 16rpx 20rpx;
    .label { font-size: 22rpx; color: var(--text-hint); display: block; }
    .value { font-size: 28rpx; color: var(--text-primary); margin-top: 6rpx; display: block; font-weight: 500; }
  }
}

/* 个人标签 */
.tags-section { margin-top: 28rpx;
  .tags-wrap { display: flex; flex-wrap: wrap; gap: 12rpx; margin-top: 16rpx; }
  .tag { background: var(--bg-icon-soft); color: var(--color-primary); font-size: 24rpx; padding: 8rpx 20rpx; border-radius: 20rpx; border: 1rpx solid var(--color-primary-mist); }
}

.question-section { margin-top: 32rpx;
  .question-item { display: flex; align-items: flex-start; gap: 16rpx; padding: 18rpx 0; border-bottom: 1rpx solid var(--border-faint);
    &:last-child { border-bottom: none; }
    .q-index { width: 36rpx; height: 36rpx; border-radius: 50%; background: var(--gradient-brand); color: var(--bg-card); font-size: 22rpx; display: flex; align-items: center; justify-content: center; flex-shrink: 0; margin-top: 2rpx; }
    .question { font-size: 26rpx; color: var(--text-hint-strong); line-height: 1.5; }
  }
}

.section-title { font-size: 30rpx; font-weight: bold; display: block; margin-bottom: 12rpx; color: var(--text-primary); }

/* ===== TA的动态 ===== */
.movements-card { margin: 24rpx 24rpx 0; background: var(--bg-card); border-radius: 32rpx; padding: 32rpx; }

.movement-item { display: flex; gap: 20rpx; padding: 20rpx 0; border-bottom: 1rpx solid var(--border-faint);
  &:last-child { border-bottom: none; }
  .m-cover { width: 160rpx; height: 160rpx; border-radius: 16rpx; background: var(--bg-sub); flex-shrink: 0; }
  .m-body { flex: 1; display: flex; flex-direction: column; justify-content: space-between; min-width: 0; }
  .m-text { font-size: 26rpx; color: var(--text-primary); line-height: 1.5; }
  .m-stats { display: flex; gap: 24rpx;
    text { font-size: 22rpx; color: var(--text-hint); }
  }
}

/* ===== 底部操作栏 ===== */
.action-bar { position: fixed; left: 0; right: 0; bottom: var(--tab-bar-height, 0px); display: flex; justify-content: center; align-items: center; gap: 40rpx; padding: 28rpx 0; background: rgba(255, 255, 255, 0.92); backdrop-filter: blur(20px); box-shadow: 0 -8rpx 32rpx rgba(0, 0, 0, 0.08); }

.action { border-radius: 50%; display: flex; align-items: center; justify-content: center; transition: transform 0.2s cubic-bezier(0.4, 0, 0.2, 1), box-shadow 0.2s ease;
  &:active { transform: scale(0.92); }
  /* 次按钮：不喜欢 / 打招呼 */
  &.small { width: 96rpx; height: 96rpx; font-size: 38rpx;
    &.dislike { background: var(--bg-card); color: var(--color-danger); border: 4rpx solid var(--color-danger); box-shadow: 0 4rpx 16rpx rgba(255, 59, 48, 0.15); }
    &.chat { background: linear-gradient(135deg, var(--info-blue), var(--info-blue-soft)); color: var(--text-inverse); box-shadow: 0 6rpx 20rpx rgba(74, 144, 217, 0.4); }
  }
  /* 主按钮：超级喜欢 / 喜欢 */
  &.super-like { width: 108rpx; height: 108rpx; font-size: 48rpx; background: linear-gradient(135deg, var(--gold), var(--gold-soft)); color: var(--text-inverse); box-shadow: 0 8rpx 28rpx rgba(255, 179, 71, 0.5); }
  &.like { width: 120rpx; height: 120rpx; font-size: 56rpx; background: var(--gradient-brand); color: var(--bg-card); box-shadow: 0 10rpx 32rpx rgba(254, 60, 114, 0.45); }
}
</style>
