<template>
  <view class="match-page">
    <!-- 顶部栏 -->
    <view class="top-bar" :style="{ paddingTop: statusBarHeight + 'px' }">
      <a11y-button class="city" label="选择城市" @tap="openCityPicker">
        <text class="city-name">{{ currentCity }}</text>
        <text class="arrow" aria-hidden="true">▾</text>
      </a11y-button>
      <a11y-button class="filter-btn" label="查看附近的人" @tap="goNearby">
        <text>附近</text>
      </a11y-button>
    </view>

    <!-- 卡片区域 -->
    <view class="card-area">
      <!-- 骨架卡片（首屏加载中）：与真实卡片同几何（全屏铺底单卡） -->
      <skeleton v-if="cardList.length === 0 && loading" variant="match" />

      <!-- 空状态：仅加载完成且无卡片时显示（否则会与骨架/卡片叠显） -->
      <empty-state v-if="cardList.length === 0 && !loading" title="暂无推荐" desc="稍后再来看看，缘分不期而至" />

      <!-- 卡片堆叠（从后往前渲染） -->
      <view
        v-for="(card, index) in visibleCards"
        :key="card.id"
        class="card-wrapper"
        :style="getCardStyle(index)"
        @touchstart="onTouchStart($event, index)"
        @touchmove="onTouchMove($event, index)"
        @touchend="onTouchEnd($event, index)"
        :aria-label="`推荐卡片：${card.nickname}，${card.age}岁`"
      >
        <!-- 主图 -->
        <image lazy-load
          class="card-image"
          :src="card.avatar"
          mode="aspectFill"
          :aria-label="`${card.nickname} 的照片`"
        />
        <!-- 底部渐变遮罩（纯装饰） -->
        <view class="card-mask" aria-hidden="true" />
        <!-- 用户信息 -->
        <view class="card-info">
          <view class="name-row">
            <text class="nickname">{{ card.nickname }}</text>
            <text class="age">{{ card.age }}</text>
          </view>
          <!-- 缘分值进度条：告知读屏当前值而非仅视觉呈现 -->
          <view v-if="card.fateValue" class="fate-row">
            <text class="fate-label">缘分值</text>
            <view
              class="fate-bar"
              role="progressbar"
              aria-label="缘分值"
              :aria-valuenow="Math.min(card.fateValue, 100)"
              aria-valuemin="0"
              aria-valuemax="100"
            >
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
        <!-- 滑动提示（喜欢 / 不喜欢）：随手势显示的视觉反馈，对读屏隐藏 -->
        <view v-if="index === 0" class="swipe-hint like" :style="{ opacity: likeOpacity }" aria-hidden="true">
          喜欢
        </view>
        <view v-if="index === 0" class="swipe-hint nope" :style="{ opacity: nopeOpacity }" aria-hidden="true">
          不喜欢
        </view>
      </view>
    </view>

    <!-- 底部操作栏（探探式五键：回看 / 不喜欢 / 超级喜欢 / 心动 / 曝光提升） -->
    <view class="action-bar safe-area-bottom">
      <a11y-button class="action-btn rewind btn-feedback" label="回看上一张" @tap="handleRewind">
        <text aria-hidden="true">↺</text>
      </a11y-button>
      <a11y-button class="action-btn dislike btn-feedback" label="不喜欢" @tap="handleDislike">
        <uni-icons type="close" size="24" color="#ff3b30"></uni-icons>
      </a11y-button>
      <a11y-button class="action-btn superlike btn-feedback" label="超级喜欢" @tap="handleSuperLike">
        <uni-icons type="star-filled" size="22" color="#4dabf7"></uni-icons>
      </a11y-button>
      <a11y-button class="action-btn like btn-feedback" label="心动" @tap="handleLike">
        <uni-icons type="heart-filled" size="24" color="#4cd964"></uni-icons>
      </a11y-button>
      <a11y-button class="action-btn boost btn-feedback" label="曝光提升" @tap="handleBoost">
        <text aria-hidden="true">⚡</text>
      </a11y-button>
    </view>

    <!-- 匹配成功弹窗（全局组件，由 match store 驱动；同步匹配与环信匹配消息共用） -->
    <MatchPopup />
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from "vue";
import EmptyState from "@/components/empty-state.vue";
import Skeleton from "@/components/skeleton.vue";
import { onShow, onShareAppMessage } from "@dcloudio/uni-app";
import { useMatchStore } from "@/store/match";
import { useSystemStore } from "@/store/system";
import { getCards, type TodayBest } from "@/api/match";
import { checkBenefit, recordBenefitUsage } from "@/api/mine";
import { useUserStore } from "@/store/user";
import MatchPopup from "@/components/match-popup.vue";
import A11yButton from "@/components/a11y-button.vue";

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

// 状态栏高度（自定义导航栏适配）
const statusBarHeight = ref(0);
const systemStore = useSystemStore();
/** 顶部城市：读取 system store（本地持久化，默认北京）；
 *  坐标→城市名的逆地理编码需地图服务 key，接入后改为定位结果 */
const currentCity = computed(() => systemStore.currentCity);
/** 城市选择暂未上线，点击给出明确提示（避免死交互） */
function openCityPicker() {
  uni.showToast({ title: "城市选择功能即将上线", icon: "none" });
}

/**
 * 定位同步：获取当前坐标并上报后端（写入 ES），供「附近的人」检索。
 * 此前整条上报链路从未被调用，导致附近的人搜不到本用户。
 * 授权拒绝/非 https 环境静默跳过，下次进入重试。
 */
function syncLocation(): void {
  uni.getLocation({
    type: "wgs84",
    success: (res) => {
      systemStore
        .updateLocation({
          longitude: res.longitude,
          latitude: res.latitude,
          // 无逆地理服务，地址描述以坐标串占位
          addrStr: `${res.longitude.toFixed(6)},${res.latitude.toFixed(6)}`,
        })
        .catch(() => {});
    },
  });
}

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

/** 计算卡片堆叠样式：顶部卡片跟随手势；后置卡片全屏垫底，滑走即直接露出（无缩放动画） */
function getCardStyle(index: number) {
  if (index === 0) {
    // 顶部卡片跟随手势位移并微旋转
    return {
      transform: `translate(${dragX.value}rpx, ${dragY.value}rpx) rotate(${dragX.value / ROTATE_DIVISOR}deg)`,
      zIndex: CARD_BASE_Z_INDEX,
      transition: isDragging.value ? "none" : CARD_TRANSITION,
    };
  }
  return {
    zIndex: CARD_BASE_Z_INDEX - index,
  };
}

// ===== 手势处理 =====
/**
 * touchmove 合帧节流：拖拽位移先攒进 pending，requestAnimationFrame 每帧只提交一次，
 * 避免高频事件下 Vue 逐事件重排渲染造成的滑动卡顿；
 * 部分小程序运行时无 requestAnimationFrame，降级为直接更新
 */
const supportsRaf = typeof requestAnimationFrame === "function";
let dragRafId = 0;
let pendingDragX = 0;
let pendingDragY = 0;

/** 触摸开始：记录起始坐标，仅响应顶部卡片 */
function onTouchStart(e: TouchEvent, index: number) {
  if (index !== 0) return;
  isDragging.value = true;
  touchStartX.value = e.touches[0].clientX;
  touchStartY.value = e.touches[0].clientY;
  pendingDragX = 0;
  pendingDragY = 0;
}

/** 触摸移动：换算 px → rpx 后攒入 pending，帧末统一驱动卡片位移 */
function onTouchMove(e: TouchEvent, index: number) {
  if (index !== 0 || !isDragging.value) return;
  // rpx 换算比例已在 onMounted 缓存（750rpx / 屏幕宽度px），避免高频同步取系统信息
  pendingDragX = (e.touches[0].clientX - touchStartX.value) * rpxRatio.value;
  pendingDragY = (e.touches[0].clientY - touchStartY.value) * rpxRatio.value;
  if (!supportsRaf) {
    dragX.value = pendingDragX;
    dragY.value = pendingDragY;
    return;
  }
  if (dragRafId) return;
  dragRafId = requestAnimationFrame(() => {
    dragRafId = 0;
    dragX.value = pendingDragX;
    dragY.value = pendingDragY;
  });
}

/** 触摸结束：超过阈值判定滑出，否则回弹复位 */
function onTouchEnd(_e: TouchEvent, index: number) {
  if (index !== 0) return;
  isDragging.value = false;
  // 提交最后一帧的 pending 位移，保证阈值判定用的是松手时刻的真实位置
  if (dragRafId) {
    cancelAnimationFrame(dragRafId);
    dragRafId = 0;
  }
  dragX.value = pendingDragX;
  dragY.value = pendingDragY;
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
/** 滑出动画/上报进行中（防连点重复上报同一张卡片） */
const swiping = ref(false);
/** 待执行的卡片移除定时器（页面卸载时清理） */
let cardRemoveTimer: ReturnType<typeof setTimeout> | null = null;
/** 上一张已完成上报的卡片（回看撤销用） */
const lastSwipe = ref<{ id: number; action: "like" | "dislike" | "superlike" } | null>(null);

/** 卡片滑出并上报操作，互相喜欢时弹出匹配成功弹窗 */
async function swipeOut(action: "like" | "dislike" | "superlike") {
  const card = visibleCards.value[0];
  if (!card) return;
  if (swiping.value) return;
  swiping.value = true;

  // 检查每日免费滑动次数
  if (!matchStore.canSwipe()) {
    uni.showToast({ title: "今日免费次数已用完，开通VIP无限滑", icon: "none" });
    dragX.value = 0;
    dragY.value = 0;
    swiping.value = false;
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
    if (matched) {
      matchStore.showMatchPopup({
        userId: card.id,
        nickname: card.nickname,
        avatar: card.avatar,
      });
    }
    // 超级喜欢为 VIP 权益：先上报成功再扣次数（与 detail 页一致，避免失败白扣）
    if (action === "superlike") {
      const ok = await recordBenefitUsage("super_like");
      if (!ok) {
        uni.showToast({ title: "今日超级喜欢次数已用完", icon: "none" });
      }
    }
  } catch {
    // 上报失败：回弹卡片并提示，避免后端未记录却把推荐划走
    dragX.value = 0;
    dragY.value = 0;
    swiping.value = false;
    uni.showToast({ title: "操作失败，请重试", icon: "none" });
    return;
  }

  // 延迟移除卡片（等待飞出动画完成）
  cardRemoveTimer = setTimeout(() => {
    cardRemoveTimer = null;
    // 记录刚出栈的卡片供「回看」撤销
    lastSwipe.value = { id: card.id, action };
    currentCardIndex.value++;
    dragX.value = 0;
    dragY.value = 0;
    swiping.value = false;
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
/**
 * 回看（探探式 ↺）：撤销上一张的上报（store.undoSwipe 回滚后端并返还次数），
 * 再把卡片放回队首；无历史记录时提示。
 */
async function handleRewind(): Promise<void> {
  const last = lastSwipe.value;
  if (swiping.value || !last) {
    uni.showToast({ title: "没有可回看的卡片", icon: "none" });
    return;
  }
  try {
    // 先回滚后端再动队列：失败时保持现状，避免前后端状态不一致
    await matchStore.undoSwipe(last.id, last.action);
    if (cardRemoveTimer) {
      clearTimeout(cardRemoveTimer);
      cardRemoveTimer = null;
    }
    swiping.value = false;
    currentCardIndex.value = Math.max(0, currentCardIndex.value - 1);
    dragX.value = 0;
    dragY.value = 0;
    lastSwipe.value = null;
    uni.showToast({ title: "已回看上一张", icon: "none" });
  } catch {
    uni.showToast({ title: "回看失败，请重试", icon: "none" });
  }
}
/**
 * 超级喜欢（探探式 ★，VIP1 每日 5 次）：权益校验通过后向上飞出上报，
 * 次数扣减在 swipeOut 上报成功后执行（先上报后扣，避免失败白扣）。
 */
async function handleSuperLike(): Promise<void> {
  if (swiping.value) return;
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
    dragX.value = 0;
    swipeOut("superlike");
  } catch {
    // 请求失败由 request 层统一 toast
  }
}
function handleBoost() {
  uni.showToast({ title: "曝光提升（VIP专属）", icon: "none" });
}

// ===== 跳转 =====
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
      // 按 id 去重后追加：分页间隙服务端可能重复下发同一用户
      const knownIds = new Set(cardList.value.map((card) => card.id));
      cardList.value.push(...newCards.filter((card) => !knownIds.has(card.id)));
    }
  } catch {
    // 请求失败由 request 层统一 toast
  } finally {
    loading.value = false;
  }
}

// ===== 生命周期 =====
/** px → rpx 换算比例（onMounted 按实际屏宽缓存，触摸移动高频路径不再取系统信息） */
const rpxRatio = ref(RPX_BASE_WIDTH / DEFAULT_WINDOW_WIDTH_PX);

onMounted(() => {
  const sysInfo = uni.getSystemInfoSync();
  statusBarHeight.value =
    sysInfo.statusBarHeight || DEFAULT_STATUS_BAR_HEIGHT_PX;
  rpxRatio.value = RPX_BASE_WIDTH / (sysInfo.windowWidth || DEFAULT_WINDOW_WIDTH_PX);
  // 检查登录状态
  if (!userStore.isLogin) {
    uni.reLaunch({ url: "/pages/auth/login" });
    return;
  }
  loadMoreCards();
});

onUnmounted(() => {
  if (cardRemoveTimer) {
    clearTimeout(cardRemoveTimer);
    cardRemoveTimer = null;
  }
  if (dragRafId) {
    cancelAnimationFrame(dragRafId);
    dragRafId = 0;
  }
});

onShow(() => {
  if (userStore.isLogin && cardList.value.length === 0) {
    loadMoreCards();
  }
  // 刷新无限滑动权益状态（VIP unlimited_swipe，每次进入匹配页刷新一次）
  if (userStore.isLogin) {
    matchStore.loadSwipeBenefit();
  }
  // 每次进入同步一次定位：后端 ES 有坐标，附近的人才能搜到本用户
  syncLocation();
});

/** 小程序端分享配置（H5 端静默忽略） */
onShareAppMessage(() => ({
  title: "心飞交友 · 遇见心动的人",
  path: "/pages/match/index",
}));
</script>

<style lang="scss" scoped>
.match-page {
  position: relative;
  /* tab 页可用高度 = 视口 - 原生 tabbar（50px）。
     不能用 var(--window-bottom)：uni-h5 把真实值设在 :root，页面 body 上是 0px，
     用它会铺满整屏，底部操作栏/照片被 tabbar 遮挡一半 */
  height: calc(100vh - var(--tab-bar-height, 0px));
  background: var(--bg-page);
  overflow: hidden;
}

/* 顶栏悬浮在卡片图片上方：透明渐变底保证照片上文字可读 */
.top-bar {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  z-index: 50;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 32rpx;
  height: 88rpx;
  background: linear-gradient(to bottom, rgba(0, 0, 0, 0.38), transparent);

  .city {
    display: flex;
    align-items: center;
    gap: 8rpx;

    .city-name {
      font-size: 32rpx;
      font-weight: bold;
      color: #fff;
    }

    .arrow {
      font-size: 24rpx;
      color: rgba(255, 255, 255, 0.85);
    }
  }

  .filter-btn {
    font-size: 28rpx;
    color: #fff;
    background: rgba(255, 255, 255, 0.18);
    border: 1rpx solid rgba(255, 255, 255, 0.35);
    padding: 8rpx 24rpx;
    border-radius: 24rpx;
  }
}

/* 卡片区全屏铺底：图片最大化，操作栏/顶栏悬浮其上 */
.card-area {
  position: absolute;
  inset: 0;
  overflow: hidden;
}

.card-wrapper {
  position: absolute;
  inset: 0;
  overflow: hidden;
  background: var(--bg-card);
  /* 拖拽期间浏览器需逐帧合成 transform，提升为独立图层避免整卡重绘 */
  will-change: transform;

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
    /* 抬高到悬浮操作栏之上（操作栏高约 148rpx + 安全区） */
    bottom: calc(160rpx + env(safe-area-inset-bottom, 0px));
    padding: 0 32rpx;

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
        background: var(--gradient-brand-h);
        border-radius: 6rpx;
        transition: width 0.4s ease-out;
      }

      .fate-value {
        font-size: 22rpx;
        font-weight: bold;
        color: var(--color-primary);
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
        /* 卡片拖拽期间 backdrop-filter 会逐帧重算模糊导致卡顿，改用半透底近似毛玻璃观感 */
        background: rgba(255, 255, 255, 0.24);
        border: 1rpx solid rgba(255, 255, 255, 0.35);
        padding: 4rpx 16rpx;
        border-radius: 20rpx;
      }
    }
  }

  .swipe-hint {
    position: absolute;
    /* 卡片全屏后下移，避开悬浮顶栏区域 */
    top: 180rpx;
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

/* 悬浮在卡片图片之上：透明底 + 底部渐变压暗，按钮自身白底圆徽保证对比度 */
.action-bar {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 50;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 32rpx;
  padding: 24rpx 0;
  background: linear-gradient(to top, rgba(0, 0, 0, 0.3), transparent);

  .action-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 50%;
    font-size: 44rpx;

    /* 探探式尺寸梯度：✕/心动大、超级喜欢中、回看/曝光小 */
    &.rewind {
      width: 88rpx;
      height: 88rpx;
      background: var(--bg-card);
      color: #f6b93b;
      border: 4rpx solid #f6b93b;
      box-shadow: 0 6rpx 20rpx rgba(246, 185, 59, 0.25);
    }

    &.dislike {
      width: 100rpx;
      height: 100rpx;
      background: var(--bg-card);
      color: #ff3b30;
      border: 4rpx solid #ff3b30;
      box-shadow: 0 6rpx 20rpx rgba(255, 59, 48, 0.25);
    }

    &.superlike {
      width: 88rpx;
      height: 88rpx;
      background: var(--bg-card);
      color: #4dabf7;
      border: 4rpx solid #4dabf7;
      box-shadow: 0 6rpx 20rpx rgba(77, 171, 247, 0.25);
    }

    &.like {
      width: 100rpx;
      height: 100rpx;
      background: var(--bg-card);
      color: #4cd964;
      border: 4rpx solid #4cd964;
      box-shadow: 0 6rpx 20rpx rgba(76, 217, 100, 0.3);
    }

    &.boost {
      width: 88rpx;
      height: 88rpx;
      background: linear-gradient(135deg, #8a5cf6, #b13bff);
      color: #fff;
      box-shadow: 0 6rpx 20rpx rgba(138, 92, 246, 0.4);
    }
  }
}
</style>
