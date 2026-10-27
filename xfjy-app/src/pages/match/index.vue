<template>
  <view class="match-page">
    <!-- 顶部栏 -->
    <view class="top-bar" :style="{ paddingTop: statusBarHeight + 'px' }">
      <view class="city" @click="showCityPicker = true" aria-label="选择城市">
        <text class="city-name">{{ currentCity }}</text>
        <text class="arrow">▾</text>
      </view>
      <view class="filter-btn" @click="goNearby" aria-label="查看附近的人">
        <text>附近</text>
      </view>
    </view>

    <!-- 卡片区域 -->
    <view class="card-area">
      <!-- 骨架卡片（首屏加载中，模拟堆叠效果） -->
      <view v-if="cardList.length === 0 && loading" class="skeleton-cards">
        <view
          v-for="i in VISIBLE_CARD_COUNT"
          :key="i"
          class="skeleton skel-card"
          :style="{
            transform: `scale(${1 - (i - 1) * CARD_SCALE_STEP}) translateY(${(i - 1) * CARD_OFFSET_STEP_RPX}rpx)`,
            zIndex: CARD_BASE_Z_INDEX - (i - 1),
          }"
        />
      </view>

      <!-- 空状态 -->
      <view v-if="cardList.length === 0 && !loading" class="empty-state">
        <view class="empty-icon">
          <text>💭</text>
        </view>
        <text class="empty-title">暂无推荐</text>
        <text class="empty-desc">稍后再来看看，缘分不期而至</text>
      </view>

      <!-- 卡片堆叠（从后往前渲染） -->
      <view
        v-for="(card, index) in visibleCards"
        :key="card.id"
        class="card-wrapper"
        :style="getCardStyle(index)"
        @touchstart="onTouchStart($event, index)"
        @touchmove="onTouchMove($event, index)"
        @touchend="onTouchEnd($event, index)"
      >
        <!-- 主图 -->
        <image
          class="card-image"
          :src="card.avatar"
          mode="aspectFill"
        />
        <!-- 底部渐变遮罩 -->
        <view class="card-mask" />
        <!-- 用户信息 -->
        <view class="card-info">
          <view class="name-row">
            <text class="nickname">{{ card.nickname }}</text>
            <text class="age">{{ card.age }}</text>
          </view>
          <!-- 缘分值进度条 -->
          <view v-if="card.fateValue" class="fate-row">
            <text class="fate-label">缘分值</text>
            <view class="fate-bar">
              <view
                class="fate-fill"
                :style="{ width: Math.min(card.fateValue, 100) + '%' }"
              />
            </view>
            <text class="fate-value">{{ card.fateValue }}</text>
          </view>
          <!-- 标签 -->
          <view v-if="card.tags && card.tags.length" class="tags">
            <text v-for="tag in card.tags.slice(0, MAX_TAG_COUNT)" :key="tag" class="tag glass">{{ tag }}</text>
          </view>
        </view>
        <!-- 滑动提示（喜欢 / 不喜欢） -->
        <view v-if="index === 0" class="swipe-hint like" :style="{ opacity: likeOpacity }">
          喜欢
        </view>
        <view v-if="index === 0" class="swipe-hint nope" :style="{ opacity: nopeOpacity }">
          不喜欢
        </view>
      </view>
    </view>

    <!-- 底部操作栏 -->
    <view class="action-bar safe-area-bottom">
      <view class="action-btn rewind btn-feedback" @click="handleRewind" aria-label="撤销上一个滑动">
        <text>↩</text>
      </view>
      <view class="action-btn dislike btn-feedback" @click="handleDislike" aria-label="不喜欢">
        <text>✕</text>
      </view>
      <view class="action-btn superlike btn-feedback" @click="handleSuperlike" aria-label="超级喜欢">
        <text>★</text>
      </view>
      <view class="action-btn like btn-feedback" @click="handleLike" aria-label="喜欢">
        <text>♥</text>
      </view>
      <view class="action-btn boost btn-feedback" @click="handleBoost" aria-label="曝光提升">
        <text>⚡</text>
      </view>
    </view>

    <!-- 匹配成功弹窗 -->
    <view v-if="matchPopup" class="match-popup" @click="matchPopup = null">
      <view class="match-content pop-in">
        <text class="match-title gradient-text">It's a Match!</text>
        <view class="match-avatars">
          <image class="match-avatar" :src="matchPopup.avatar" mode="aspectFill" />
          <text class="match-heart heart-beat">♥</text>
          <image class="match-avatar" src="/static/logo/logo-icon-transparent.png" mode="aspectFill" />
        </view>
        <text class="match-name">你和 {{ matchPopup.nickname }} 互相喜欢了</text>
        <view class="match-actions">
          <button class="send-msg" @click.stop="goChat(matchPopup.userId)">发消息</button>
          <button class="keep-swipe" @click.stop="matchPopup = null">继续滑动</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from "vue";
import { onShow, onShareAppMessage } from "@dcloudio/uni-app";
import { useMatchStore } from "@/store/match";
import { getCards, type TodayBest } from "@/api/match";
import { checkBenefit, recordBenefitUsage } from "@/api/mine";
import { useUserStore } from "@/store/user";

const matchStore = useMatchStore();
const userStore = useUserStore();

/** ===== 常量定义（阿里规范：不允许任何魔法值直接出现在代码中） ===== */

/** 卡片堆叠可见数量 */
const VISIBLE_CARD_COUNT = 3;
/** 剩余卡片不足此数时触发预加载 */
const MIN_REMAINING_CARDS = 3;
/** 滑动出栈阈值（rpx），超过则判定为喜欢/不喜欢 */
const SWIPE_THRESHOLD_RPX = 120;
/** 卡片飞出屏幕的水平距离（rpx） */
const FLY_DISTANCE_X_RPX = 1000;
/** 超级喜欢飞出的垂直距离（rpx，向上） */
const FLY_DISTANCE_Y_SUPER_RPX = -800;
/** 喜欢/不喜欢飞出的垂直距离（rpx，微向下） */
const FLY_DISTANCE_Y_NORMAL_RPX = 50;
/** 卡片移除动画延迟（ms），需与 CSS 过渡时间一致 */
const CARD_REMOVE_DELAY_MS = 300;
/** 卡片过渡动画时长 */
const CARD_TRANSITION = "transform 0.3s ease-out";
/** 卡片堆叠基础层级 */
const CARD_BASE_Z_INDEX = 30;
/** 后置卡片缩放步长（每层缩小 5%） */
const CARD_SCALE_STEP = 0.05;
/** 后置卡片垂直偏移步长（rpx） */
const CARD_OFFSET_STEP_RPX = 20;
/** 滑动旋转角度除数（dragX / 此值 = 旋转角度） */
const ROTATE_DIVISOR = 20;
/** 滑动提示透明度计算除数 */
const OPACITY_DIVISOR = 100;
/** 标签最大展示数量 */
const MAX_TAG_COUNT = 3;
/** rpx 换算基准（750rpx = 屏幕宽度） */
const RPX_BASE_WIDTH = 750;
/** 默认屏幕宽度兜底值（px） */
const DEFAULT_WINDOW_WIDTH_PX = 375;
/** 默认状态栏高度兜底值（px） */
const DEFAULT_STATUS_BAR_HEIGHT_PX = 20;

/** 匹配成功弹窗数据（后端 love 接口无匹配结果返回，保留 UI 供后续消息推送触发） */
interface MatchPopupData {
  userId: number | string;
  nickname: string;
  avatar: string;
}

// 状态栏高度（自定义导航栏适配）
const statusBarHeight = ref(0);
const currentCity = ref("北京");
const showCityPicker = ref(false);

// 卡片数据
const cardList = ref<TodayBest[]>([]);
const loading = ref(false);

// 滑动手势状态
const dragX = ref(0);
const dragY = ref(0);
const isDragging = ref(false);
const touchStartX = ref(0);
const touchStartY = ref(0);
const currentCardIndex = ref(0); // 当前顶部卡片在 cardList 中的索引

// 匹配弹窗
const matchPopup = ref<MatchPopupData | null>(null);

/** 上一张卡片的滑动记录（用于撤销回滚后端操作） */
interface SwipeRecord {
  card: TodayBest;
  action: "like" | "dislike" | "superlike";
}
const lastSwipe = ref<SwipeRecord | null>(null);

/** 可见卡片（最多显示 VISIBLE_CARD_COUNT 张，从后往前堆叠渲染） */
const visibleCards = computed(() => {
  return cardList.value.slice(
    currentCardIndex.value,
    currentCardIndex.value + VISIBLE_CARD_COUNT
  );
});

/** 滑动提示透明度（跟随水平拖拽距离渐显） */
const likeOpacity = computed(() =>
  Math.min(Math.max(dragX.value / OPACITY_DIVISOR, 0), 1)
);
const nopeOpacity = computed(() =>
  Math.min(Math.max(-dragX.value / OPACITY_DIVISOR, 0), 1)
);

/** 计算卡片堆叠样式：顶部卡片跟随手势，后置卡片逐层缩放偏移 */
function getCardStyle(index: number) {
  if (index === 0) {
    // 顶部卡片跟随手势位移并微旋转
    return {
      transform: `translate(${dragX.value}rpx, ${dragY.value}rpx) rotate(${dragX.value / ROTATE_DIVISOR}deg)`,
      zIndex: CARD_BASE_Z_INDEX,
      transition: isDragging.value ? "none" : CARD_TRANSITION,
    };
  }
  // 后置卡片逐层缩放渐隐
  const scale = 1 - index * CARD_SCALE_STEP;
  const translateY = index * CARD_OFFSET_STEP_RPX;
  return {
    transform: `scale(${scale}) translateY(${translateY}rpx)`,
    zIndex: CARD_BASE_Z_INDEX - index,
    transition: CARD_TRANSITION,
  };
}

// ===== 手势处理 =====
/** 触摸开始：记录起始坐标，仅响应顶部卡片 */
function onTouchStart(e: TouchEvent, index: number) {
  if (index !== 0) return;
  isDragging.value = true;
  touchStartX.value = e.touches[0].clientX;
  touchStartY.value = e.touches[0].clientY;
}

/** 触摸移动：换算 px → rpx 后驱动卡片位移 */
function onTouchMove(e: TouchEvent, index: number) {
  if (index !== 0 || !isDragging.value) return;
  const deltaX = e.touches[0].clientX - touchStartX.value;
  const deltaY = e.touches[0].clientY - touchStartY.value;
  // rpx 换算：750rpx / 屏幕宽度px
  const rpxRatio =
    RPX_BASE_WIDTH /
    (uni.getSystemInfoSync().windowWidth || DEFAULT_WINDOW_WIDTH_PX);
  dragX.value = deltaX * rpxRatio;
  dragY.value = deltaY * rpxRatio;
}

/** 触摸结束：超过阈值判定滑出，否则回弹复位 */
function onTouchEnd(_e: TouchEvent, index: number) {
  if (index !== 0) return;
  isDragging.value = false;
  if (dragX.value > SWIPE_THRESHOLD_RPX) {
    swipeOut("like");
  } else if (dragX.value < -SWIPE_THRESHOLD_RPX) {
    swipeOut("dislike");
  } else {
    // 未达阈值，回弹复位
    dragX.value = 0;
    dragY.value = 0;
  }
}

// ===== 滑动出栈 =====
/** 卡片滑出并上报操作，互相喜欢时弹出匹配成功弹窗 */
async function swipeOut(action: "like" | "dislike" | "superlike") {
  const card = visibleCards.value[0];
  if (!card) return;

  // 检查每日免费滑动次数
  if (!matchStore.canSwipe()) {
    uni.showToast({ title: "今日免费次数已用完，开通VIP无限滑", icon: "none" });
    dragX.value = 0;
    dragY.value = 0;
    return;
  }

  // 驱动飞出动画：不喜欢向左，其余向右；超级喜欢向上飞
  dragX.value =
    action === "dislike" ? -FLY_DISTANCE_X_RPX : FLY_DISTANCE_X_RPX;
  dragY.value =
    action === "superlike"
      ? FLY_DISTANCE_Y_SUPER_RPX
      : FLY_DISTANCE_Y_NORMAL_RPX;

  // 上报滑动，互相喜欢时弹出匹配成功窗
  try {
    const matched = await matchStore.doSwipe(card.id, action);
    // 记录本次滑动，供撤销回滚使用
    lastSwipe.value = { card, action };
    if (matched) {
      matchPopup.value = {
        userId: card.id,
        nickname: card.nickname,
        avatar: card.avatar,
      };
    }
  } catch {
    // 静默处理，不影响滑动手感
  }

  // 延迟移除卡片（等待飞出动画完成）
  setTimeout(() => {
    currentCardIndex.value++;
    dragX.value = 0;
    dragY.value = 0;
    // 剩余卡片不足时预加载更多
    if (cardList.value.length - currentCardIndex.value < MIN_REMAINING_CARDS) {
      loadMoreCards();
    }
  }, CARD_REMOVE_DELAY_MS);
}

// ===== 按钮操作 =====
function handleLike() {
  dragX.value = 0;
  swipeOut("like");
}
function handleDislike() {
  dragX.value = 0;
  swipeOut("dislike");
}
/** 超级喜欢权益校验中（防连点） */
const superlikeChecking = ref(false);
async function handleSuperlike() {
  if (superlikeChecking.value) return;
  superlikeChecking.value = true;
  try {
    // P2 权益拦截：超级喜欢需 super_like 权益（VIP1，5次/天）
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
    // 扣减每日次数（不限次权益返回 true）
    const ok = await recordBenefitUsage("super_like");
    if (!ok) {
      uni.showToast({ title: "今日超级喜欢次数已用完", icon: "none" });
      return;
    }
    dragX.value = 0;
    dragY.value = 0;
    swipeOut("superlike");
  } catch {
    uni.showToast({ title: "网络异常，请重试", icon: "none" });
  } finally {
    superlikeChecking.value = false;
  }
}
/** 撤销进行中标记（防连点） */
const rewinding = ref(false);
async function handleRewind() {
  if (rewinding.value) return;
  if (!lastSwipe.value) {
    uni.showToast({ title: "暂无可撤销的操作", icon: "none" });
    return;
  }
  if (!matchStore.canRewind()) {
    uni.showToast({ title: "今日撤销次数已用完（3次/天）", icon: "none" });
    return;
  }
  const { card, action } = lastSwipe.value;
  rewinding.value = true;
  try {
    // 回滚后端操作（like/superlike→unlove）并返还滑动次数
    await matchStore.undoSwipe(card.id, action);
    currentCardIndex.value--;
    matchStore.useRewind();
    lastSwipe.value = null;
    uni.showToast({ title: "已撤销", icon: "none" });
  } catch {
    uni.showToast({ title: "撤销失败，请重试", icon: "none" });
  } finally {
    rewinding.value = false;
  }
}
function handleBoost() {
  uni.showToast({ title: "曝光提升（VIP专属）", icon: "none" });
}

// ===== 跳转 =====
function goChat(userId: number | string) {
  matchPopup.value = null;
  uni.navigateTo({ url: `/pages/chat/chat?userId=${userId}` });
}
function goNearby() {
  uni.navigateTo({ url: "/pages/match/nearby" });
}

// ===== 数据加载 =====
async function loadMoreCards() {
  if (loading.value) return;
  loading.value = true;
  try {
    const newCards = await getCards();
    if (newCards && newCards.length > 0) {
      cardList.value.push(...newCards);
    }
  } catch {
    // 请求失败由 request 层统一 toast
  } finally {
    loading.value = false;
  }
}

// ===== 生命周期 =====
onMounted(() => {
  const sysInfo = uni.getSystemInfoSync();
  statusBarHeight.value =
    sysInfo.statusBarHeight || DEFAULT_STATUS_BAR_HEIGHT_PX;
  // 检查登录状态
  if (!userStore.isLogin) {
    uni.reLaunch({ url: "/pages/auth/login" });
    return;
  }
  loadMoreCards();
});

onShow(() => {
  if (userStore.isLogin && cardList.value.length === 0) {
    loadMoreCards();
  }
});

/** 小程序端分享配置（H5 端静默忽略） */
onShareAppMessage(() => ({
  title: "心飞交友 · 遇见心动的人",
  path: "/pages/match/index",
}));
</script>

<style lang="scss" scoped>
.match-page {
  height: calc(100vh - var(--window-bottom, 0px));
  background: #f0f0f5;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.top-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 32rpx;
  height: 88rpx;
  background: #fff;

  .city {
    display: flex;
    align-items: center;
    gap: 8rpx;

    .city-name {
      font-size: 32rpx;
      font-weight: bold;
    }

    .arrow {
      font-size: 24rpx;
      color: #999;
    }
  }

  .filter-btn {
    font-size: 28rpx;
    color: #ff5e8e;
    background: #fff0f5;
    padding: 8rpx 24rpx;
    border-radius: 24rpx;
  }
}

.card-area {
  flex: 1;
  position: relative;
  padding: 24rpx;
  overflow: hidden;
}

/* 骨架卡片堆叠（首屏加载占位） */
.skeleton-cards {
  position: absolute;
  left: 24rpx;
  right: 24rpx;
  top: 24rpx;
  bottom: 24rpx;

  .skel-card {
    position: absolute;
    inset: 0;
    border-radius: 32rpx;
  }
}

.card-wrapper {
  position: absolute;
  left: 24rpx;
  right: 24rpx;
  top: 24rpx;
  bottom: 24rpx;
  border-radius: 32rpx;
  overflow: hidden;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.08), 0 16rpx 48rpx rgba(0, 0, 0, 0.12);
  background: #fff;

  .card-image {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
  }

  .card-mask {
    position: absolute;
    left: 0;
    right: 0;
    bottom: 0;
    height: 40%;
    background: linear-gradient(to top, rgba(0, 0, 0, 0.75), transparent);
  }

  .card-info {
    position: absolute;
    left: 0;
    right: 0;
    bottom: 0;
    padding: 32rpx;

    .name-row {
      display: flex;
      align-items: center;
      gap: 12rpx;

      .nickname {
        font-size: 40rpx;
        font-weight: bold;
        color: #fff;
      }

      .age {
        font-size: 24rpx;
        font-weight: bold;
        color: #fff;
        background: rgba(255, 94, 142, 0.85);
        padding: 2rpx 16rpx;
        border-radius: 16rpx;
      }
    }

    .fate-row {
      display: flex;
      align-items: center;
      gap: 12rpx;
      margin-top: 12rpx;

      .fate-label {
        font-size: 22rpx;
        color: rgba(255, 255, 255, 0.85);
      }

      .fate-bar {
        flex: 1;
        height: 12rpx;
        background: rgba(255, 255, 255, 0.25);
        border-radius: 6rpx;
        overflow: hidden;
      }

      .fate-fill {
        height: 100%;
        background: linear-gradient(90deg, #ff5e8e, #ffd700);
        border-radius: 6rpx;
        transition: width 0.4s ease-out;
      }

      .fate-value {
        font-size: 22rpx;
        font-weight: bold;
        color: #ffd700;
        min-width: 36rpx;
        text-align: right;
      }
    }

    .tags {
      display: flex;
      gap: 12rpx;
      margin-top: 16rpx;

      .tag {
        font-size: 22rpx;
        color: #fff;
        background: rgba(255, 255, 255, 0.18);
        backdrop-filter: blur(12px);
        -webkit-backdrop-filter: blur(12px);
        border: 1rpx solid rgba(255, 255, 255, 0.25);
        padding: 4rpx 16rpx;
        border-radius: 20rpx;
      }
    }
  }

  .swipe-hint {
    position: absolute;
    top: 80rpx;
    font-size: 44rpx;
    font-weight: 900;
    letter-spacing: 4rpx;
    padding: 8rpx 32rpx;
    border-radius: 12rpx;
    border: 6rpx solid;

    &.like {
      right: 40rpx;
      color: #4cd964;
      border-color: #4cd964;
      transform: rotate(15deg);
    }

    &.nope {
      left: 40rpx;
      color: #ff3b30;
      border-color: #ff3b30;
      transform: rotate(-15deg);
    }
  }
}

.action-bar {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 32rpx;
  padding: 24rpx 0;
  background: #fff;

  .action-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 50%;
    font-size: 44rpx;

    &.rewind {
      width: 88rpx;
      height: 88rpx;
      background: linear-gradient(135deg, #ffd700, #ffb347);
      color: #fff;
      box-shadow: 0 6rpx 20rpx rgba(255, 179, 71, 0.4);
    }

    &.dislike {
      width: 100rpx;
      height: 100rpx;
      background: #fff;
      color: #ff3b30;
      border: 4rpx solid #ff3b30;
      box-shadow: 0 6rpx 20rpx rgba(255, 59, 48, 0.25);
    }

    &.superlike {
      width: 88rpx;
      height: 88rpx;
      background: linear-gradient(135deg, #4cd964, #2ecc71);
      color: #fff;
      box-shadow: 0 6rpx 20rpx rgba(76, 217, 100, 0.4);
    }

    &.like {
      width: 100rpx;
      height: 100rpx;
      background: #fff;
      color: #ff5e8e;
      border: 4rpx solid #ff5e8e;
      box-shadow: 0 6rpx 20rpx rgba(255, 94, 142, 0.3);
    }

    &.boost {
      width: 88rpx;
      height: 88rpx;
      background: linear-gradient(135deg, #a78bfa, #8b5cf6);
      color: #fff;
      box-shadow: 0 6rpx 20rpx rgba(139, 92, 246, 0.4);
    }
  }
}

.match-popup {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.75);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 999;

  .match-content {
    display: flex;
    flex-direction: column;
    align-items: center;

    .match-title {
      font-size: 64rpx;
      font-weight: 900;
      background: linear-gradient(90deg, #ff5e8e, #ffd700);
      -webkit-background-clip: text;
      background-clip: text;
      -webkit-text-fill-color: transparent;
      margin-bottom: 48rpx;
    }

    .match-avatars {
      display: flex;
      align-items: center;
      gap: 32rpx;
      margin-bottom: 32rpx;

      .match-avatar {
        width: 160rpx;
        height: 160rpx;
        border-radius: 50%;
        border: 6rpx solid #ff5e8e;
      }

      .match-heart {
        font-size: 56rpx;
        color: #ff5e8e;
      }
    }

    .match-name {
      font-size: 30rpx;
      color: #fff;
      margin-bottom: 48rpx;
    }

    .match-actions {
      display: flex;
      gap: 32rpx;

      .send-msg {
        background: linear-gradient(90deg, #ff5e8e, #ff8fab);
        color: #fff;
        font-size: 30rpx;
        padding: 0 48rpx;
        height: 88rpx;
        line-height: 88rpx;
        border-radius: 44rpx;
        border: none;
      }

      .keep-swipe {
        background: transparent;
        color: #fff;
        font-size: 30rpx;
        padding: 0 48rpx;
        height: 88rpx;
        line-height: 88rpx;
        border-radius: 44rpx;
        border: 2rpx solid rgba(255, 255, 255, 0.5);
      }
    }
  }
}
</style>