<template>
  <view class="match-page">
    <!-- 顶部栏 -->
    <view class="top-bar" :style="{ paddingTop: statusBarHeight + 'px' }">
      <view class="city" @click="showCityPicker = true">
        <text class="city-name">{{ currentCity }}</text>
        <text class="arrow">▾</text>
      </view>
      <view class="filter-btn" @click="goNearby">
        <text>附近</text>
      </view>
    </view>

    <!-- 卡片区域 -->
    <view class="card-area">
      <!-- 空状态 -->
      <view v-if="cardList.length === 0 && !loading" class="empty">
        <text class="empty-icon">💔</text>
        <text class="empty-text">暂无推荐，稍后再来看看吧</text>
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
          :src="card.photos[0] || card.avatar"
          mode="aspectFill"
        />
        <!-- 底部渐变遮罩 -->
        <view class="card-mask" />
        <!-- 用户信息 -->
        <view class="card-info">
          <view class="name-row">
            <text class="nickname">{{ card.nickname }}</text>
            <text class="age">{{ card.age }}</text>
            <view v-if="card.verified" class="verified-badge">✓</view>
          </view>
          <view class="meta-row">
            <text v-if="card.city">{{ card.city }}</text>
            <text v-if="card.distance"> · {{ formatDistance(card.distance) }}</text>
          </view>
          <view v-if="card.signature" class="signature">{{ card.signature }}</view>
          <!-- 标签 -->
          <view v-if="card.tags && card.tags.length" class="tags">
            <text v-for="tag in card.tags.slice(0, 3)" :key="tag" class="tag">{{ tag }}</text>
          </view>
        </view>
        <!-- 滑动提示（LIKE / NOPE） -->
        <view v-if="index === 0" class="swipe-hint like" :style="{ opacity: likeOpacity }">
          LIKE
        </view>
        <view v-if="index === 0" class="swipe-hint nope" :style="{ opacity: nopeOpacity }">
          NOPE
        </view>
      </view>
    </view>

    <!-- 底部操作栏 -->
    <view class="action-bar safe-area-bottom">
      <view class="action-btn rewind" @click="handleRewind">
        <text>↩</text>
      </view>
      <view class="action-btn dislike" @click="handleDislike">
        <text>✕</text>
      </view>
      <view class="action-btn superlike" @click="handleSuperlike">
        <text>★</text>
      </view>
      <view class="action-btn like" @click="handleLike">
        <text>♥</text>
      </view>
      <view class="action-btn boost" @click="handleBoost">
        <text>⚡</text>
      </view>
    </view>

    <!-- 匹配成功弹窗 -->
    <view v-if="matchPopup" class="match-popup" @click="matchPopup = null">
      <view class="match-content">
        <text class="match-title">It's a Match!</text>
        <view class="match-avatars">
          <image class="match-avatar" :src="matchPopup.avatar" mode="aspectFill" />
          <text class="match-heart">♥</text>
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
import { onShow } from "@dcloudio/uni-app";
import { useMatchStore, type MatchCard, type MatchResult } from "@/store/match";
import { useUserStore } from "@/store/user";

const matchStore = useMatchStore();
const userStore = useUserStore();

// 状态栏高度（自定义导航栏适配）
const statusBarHeight = ref(0);
const currentCity = ref("北京");
const showCityPicker = ref(false);

// 卡片数据
const cardList = ref<MatchCard[]>([]);
const loading = ref(false);

// 滑动手势状态
const dragX = ref(0);
const dragY = ref(0);
const isDragging = ref(false);
const touchStartX = ref(0);
const touchStartY = ref(0);
const currentCardIndex = ref(0); // 当前顶部卡片在 cardList 中的索引

// 匹配弹窗
const matchPopup = ref<MatchResult | null>(null);

// 可见卡片（最多显示 3 张）
const visibleCards = computed(() => {
  return cardList.value.slice(currentCardIndex.value, currentCardIndex.value + 3);
});

// 滑动提示透明度
const likeOpacity = computed(() => Math.min(Math.max(dragX.value / 100, 0), 1));
const nopeOpacity = computed(() => Math.min(Math.max(-dragX.value / 100, 0), 1));

// 卡片堆叠样式
function getCardStyle(index: number) {
  if (index === 0) {
    // 顶部卡片跟随手势
    return {
      transform: `translate(${dragX.value}rpx, ${dragY.value}rpx) rotate(${dragX.value / 20}deg)`,
      zIndex: 30,
      transition: isDragging.value ? "none" : "transform 0.3s ease-out",
    };
  }
  // 后面的卡片缩放渐隐
  const scale = 1 - index * 0.05;
  const translateY = index * 20;
  return {
    transform: `scale(${scale}) translateY(${translateY}rpx)`,
    zIndex: 30 - index,
    transition: "transform 0.3s ease-out",
  };
}

// ===== 手势处理 =====
function onTouchStart(e: TouchEvent, index: number) {
  if (index !== 0) return;
  isDragging.value = true;
  touchStartX.value = e.touches[0].clientX;
  touchStartY.value = e.touches[0].clientY;
}

function onTouchMove(e: TouchEvent, index: number) {
  if (index !== 0 || !isDragging.value) return;
  const dx = e.touches[0].clientX - touchStartX.value;
  const dy = e.touches[0].clientY - touchStartY.value;
  // rpx 换算（750rpx / 屏幕宽度px）
  const ratio = 750 / (uni.getSystemInfoSync().windowWidth || 375);
  dragX.value = dx * ratio;
  dragY.value = dy * ratio;
}

function onTouchEnd(_e: TouchEvent, index: number) {
  if (index !== 0) return;
  isDragging.value = false;
  const threshold = 120; // 滑出阈值 rpx
  if (dragX.value > threshold) {
    swipeOut("like");
  } else if (dragX.value < -threshold) {
    swipeOut("dislike");
  } else {
    // 回弹
    dragX.value = 0;
    dragY.value = 0;
  }
}

// ===== 滑动出栈 =====
async function swipeOut(action: "like" | "dislike" | "superlike") {
  const card = visibleCards.value[0];
  if (!card) return;

  // 检查滑动次数
  if (!matchStore.canSwipe()) {
    uni.showToast({ title: "今日免费次数已用完，开通VIP无限滑", icon: "none" });
    dragX.value = 0;
    dragY.value = 0;
    return;
  }

  // 动画飞出
  const flyX = action === "dislike" ? -1000 : 1000;
  dragX.value = flyX;
  dragY.value = action === "superlike" ? -800 : 50;

  // 上报滑动
  try {
    const result = await matchStore.doSwipe(card.id, action);
    if (result) {
      // 匹配成功！
      matchPopup.value = result;
    }
  } catch {
    // 静默处理
  }

  // 延迟移除卡片
  setTimeout(() => {
    currentCardIndex.value++;
    dragX.value = 0;
    dragY.value = 0;
    // 剩余卡片不足时加载更多
    if (cardList.value.length - currentCardIndex.value < 3) {
      loadMoreCards();
    }
  }, 300);
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
function handleSuperlike() {
  dragX.value = 0;
  dragY.value = 0;
  swipeOut("superlike");
}
function handleRewind() {
  if (currentCardIndex.value > 0) {
    currentCardIndex.value--;
    uni.showToast({ title: "已撤销", icon: "none" });
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

// ===== 工具函数 =====
function formatDistance(km: number): string {
  if (km < 1) return `${Math.round(km * 1000)}m`;
  return `${km.toFixed(1)}km`;
}

// ===== 数据加载 =====
async function loadMoreCards() {
  if (loading.value) return;
  loading.value = true;
  try {
    // TODO: 对接后端接口，当前使用模拟数据
    // const newCards = await matchStore.loadCards();
    // cardList.value.push(...newCards);
  } finally {
    loading.value = false;
  }
}

// ===== 生命周期 =====
onMounted(() => {
  const sysInfo = uni.getSystemInfoSync();
  statusBarHeight.value = sysInfo.statusBarHeight || 20;
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
</script>

<style lang="scss" scoped>
.match-page {
  height: 100vh;
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

.card-wrapper {
  position: absolute;
  left: 24rpx;
  right: 24rpx;
  top: 24rpx;
  bottom: 24rpx;
  border-radius: 32rpx;
  overflow: hidden;
  box-shadow: 0 8rpx 40rpx rgba(0, 0, 0, 0.12);
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
        font-size: 30rpx;
        color: rgba(255, 255, 255, 0.9);
      }

      .verified-badge {
        width: 32rpx;
        height: 32rpx;
        background: #ff5e8e;
        border-radius: 50%;
        color: #fff;
        font-size: 20rpx;
        display: flex;
        align-items: center;
        justify-content: center;
      }
    }

    .meta-row {
      font-size: 26rpx;
      color: rgba(255, 255, 255, 0.8);
      margin-top: 8rpx;
    }

    .signature {
      font-size: 26rpx;
      color: rgba(255, 255, 255, 0.9);
      margin-top: 12rpx;
    }

    .tags {
      display: flex;
      gap: 12rpx;
      margin-top: 16rpx;

      .tag {
        font-size: 22rpx;
        color: #fff;
        background: rgba(255, 255, 255, 0.25);
        padding: 4rpx 16rpx;
        border-radius: 20rpx;
      }
    }
  }

  .swipe-hint {
    position: absolute;
    top: 80rpx;
    font-size: 56rpx;
    font-weight: 900;
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

.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;

  .empty-icon {
    font-size: 80rpx;
    margin-bottom: 24rpx;
  }

  .empty-text {
    font-size: 28rpx;
    color: #999;
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
      background: #ffd700;
      color: #fff;
    }

    &.dislike {
      width: 100rpx;
      height: 100rpx;
      background: #fff;
      color: #ff3b30;
      border: 4rpx solid #ff3b30;
    }

    &.superlike {
      width: 88rpx;
      height: 88rpx;
      background: #4cd964;
      color: #fff;
    }

    &.like {
      width: 100rpx;
      height: 100rpx;
      background: #fff;
      color: #ff5e8e;
      border: 4rpx solid #ff5e8e;
    }

    &.boost {
      width: 88rpx;
      height: 88rpx;
      background: #a78bfa;
      color: #fff;
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
      color: #ff5e8e;
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