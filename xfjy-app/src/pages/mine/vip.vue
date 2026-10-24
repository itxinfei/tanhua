<template>
  <view class="vip-page">
    <!-- 顶部权益区 -->
    <view class="header" :style="{ paddingTop: statusBarHeight + 20 + 'px' }">
      <view class="crown float">👑</view>
      <text class="title gradient-gold">心飞 VIP</text>
      <text class="subtitle">解锁全部特权，遇见更好的缘分</text>
      <view class="glow-circle c1" />
      <view class="glow-circle c2" />
    </view>

    <!-- 权益对比 -->
    <view class="benefits-card shadow-gold">
      <view class="benefit-head">
        <text class="head-name">权益</text>
        <text class="head-free">普通用户</text>
        <text class="head-vip">VIP</text>
      </view>
      <view class="benefit-row" v-for="b in benefits" :key="b.name">
        <text class="benefit-name">{{ b.name }}</text>
        <text class="free">{{ b.free }}</text>
        <text class="vip">{{ b.vip }}</text>
      </view>
    </view>

    <!-- 套餐选择 -->
    <view class="plans">
      <view v-for="plan in plans" :key="plan.id" class="plan-card btn-feedback" :class="{ selected: selectedPlan === plan.id }" @click="selectedPlan = plan.id">
        <text v-if="plan.tag" class="plan-tag">{{ plan.tag }}</text>
        <text class="plan-name">{{ plan.name }}</text>
        <text class="plan-price">¥{{ plan.price }}</text>
        <text class="plan-unit">{{ plan.unit }}</text>
      </view>
    </view>

    <!-- 支付按钮 -->
    <button class="pay-btn safe-area-bottom btn-feedback" @click="handlePay">
      立即开通 ¥{{ currentPlan?.price || 0 }}
    </button>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from "vue";
import { onLoad } from "@dcloudio/uni-app";

/** 默认状态栏高度（px） */
const DEFAULT_STATUS_BAR_HEIGHT = 20;

const statusBarHeight = ref(DEFAULT_STATUS_BAR_HEIGHT);
const selectedPlan = ref("year");

/** 权益对比数据 */
const benefits = [
  { name: "无限滑动", free: "50次/天", vip: "无限" },
  { name: "超级喜欢", free: "—", vip: "5次/天" },
  { name: "查看谁喜欢我", free: "—", vip: "全部可见" },
  { name: "位置穿越", free: "—", vip: "任意城市" },
  { name: "撤销左滑", free: "—", vip: "不限次" },
  { name: "专属标识", free: "—", vip: "VIP角标" },
];

/** 套餐配置 */
const plans = [
  { id: "month", name: "月卡", price: 28, unit: "¥28/月", tag: "" },
  { id: "quarter", name: "季卡", price: 68, unit: "¥22.7/月", tag: "" },
  { id: "year", name: "年卡", price: 198, unit: "¥16.5/月", tag: "最划算" },
];

/** 当前选中套餐 */
const currentPlan = computed(() => plans.find((p) => p.id === selectedPlan.value));

/** 发起支付（开发期占位） */
async function handlePay(): Promise<void> {
  // TODO: 对接微信/支付宝支付
  uni.showActionSheet({
    itemList: ["微信支付", "支付宝支付"],
    success: () => {
      uni.showToast({ title: "支付功能开发中", icon: "none" });
    },
  });
}

onLoad(() => {
  const sysInfo = uni.getSystemInfoSync();
  statusBarHeight.value = sysInfo.statusBarHeight || DEFAULT_STATUS_BAR_HEIGHT;
});
</script>

<style lang="scss" scoped>
.vip-page { min-height: 100vh; background: #1a1a2e; padding-bottom: 180rpx; overflow: hidden; }

/* ===== 顶部权益区 ===== */
.header { text-align: center; padding-bottom: 48rpx; background: linear-gradient(180deg, #2c2c54 0%, #1a1a2e 100%); position: relative; overflow: hidden;
  .crown { font-size: 80rpx; display: block; margin-bottom: 8rpx; filter: drop-shadow(0 4rpx 12rpx rgba(255, 215, 0, 0.5)); }
  .title { font-size: 56rpx; font-weight: 900; display: block; letter-spacing: 4rpx; }
  .subtitle { font-size: 26rpx; color: rgba(255, 215, 0, 0.6); margin-top: 12rpx; display: block; }
}

/* 金色渐变文字 */
.gradient-gold { background: linear-gradient(90deg, #ffd700, #fff5c0, #ffd700); -webkit-background-clip: text; color: transparent; }

/* 装饰光斑 */
.glow-circle { position: absolute; border-radius: 50%; filter: blur(60rpx); pointer-events: none;
  &.c1 { width: 300rpx; height: 300rpx; background: rgba(255, 215, 0, 0.12); top: -60rpx; left: -80rpx; }
  &.c2 { width: 240rpx; height: 240rpx; background: rgba(255, 94, 142, 0.08); bottom: -40rpx; right: -60rpx; }
}

/* ===== 权益对比 ===== */
.benefits-card { margin: 0 32rpx; background: rgba(255, 255, 255, 0.08); border-radius: 24rpx; padding: 8rpx 24rpx 24rpx; border: 1rpx solid rgba(255, 215, 0, 0.15); }
.shadow-gold { box-shadow: 0 8rpx 32rpx rgba(255, 215, 0, 0.1); }

.benefit-head { display: flex; align-items: center; padding: 20rpx 0 12rpx; border-bottom: 1rpx solid rgba(255, 215, 0, 0.2);
  .head-name { flex: 1; font-size: 22rpx; color: rgba(255, 255, 255, 0.5); }
  .head-free { width: 160rpx; text-align: center; font-size: 22rpx; color: rgba(255, 255, 255, 0.5); }
  .head-vip { width: 160rpx; text-align: center; font-size: 22rpx; color: #ffd700; font-weight: bold; }
}

.benefit-row { display: flex; align-items: center; padding: 18rpx 0; border-bottom: 1rpx solid rgba(255, 255, 255, 0.06);
  &:last-child { border-bottom: none; }
  .benefit-name { flex: 1; font-size: 26rpx; color: #fff; }
  .free { width: 160rpx; text-align: center; font-size: 24rpx; color: rgba(255, 255, 255, 0.35); }
  .vip { width: 160rpx; text-align: center; font-size: 24rpx; color: #ffd700; font-weight: bold; }
}

/* ===== 套餐选择 ===== */
.plans { display: flex; gap: 16rpx; margin: 32rpx; }
.plan-card { flex: 1; background: rgba(255, 255, 255, 0.08); border-radius: 20rpx; padding: 36rpx 16rpx 32rpx; text-align: center; position: relative; border: 2rpx solid transparent; transition: all 0.25s ease;
  &.selected { border-color: #ffd700; background: rgba(255, 215, 0, 0.12); box-shadow: 0 0 24rpx rgba(255, 215, 0, 0.25); transform: translateY(-8rpx); }
  .plan-tag { position: absolute; top: -16rpx; left: 50%; transform: translateX(-50%); background: linear-gradient(90deg, #ff5e8e, #ff8fab); color: #fff; font-size: 18rpx; padding: 4rpx 18rpx; border-radius: 16rpx; white-space: nowrap; }
  .plan-name { font-size: 28rpx; color: #fff; display: block; }
  .plan-price { font-size: 48rpx; font-weight: 900; color: #ffd700; display: block; margin: 12rpx 0 4rpx; }
  .plan-unit { font-size: 20rpx; color: rgba(255, 255, 255, 0.4); display: block; }
}

/* ===== 支付按钮 ===== */
.pay-btn { position: fixed; left: 32rpx; right: 32rpx; bottom: 40rpx; height: 96rpx; line-height: 96rpx;
  background: linear-gradient(90deg, #ffd700, #ffb347); color: #1a1a2e;
  font-size: 34rpx; font-weight: 900; border-radius: 48rpx; border: none;
  box-shadow: 0 8rpx 28rpx rgba(255, 179, 71, 0.4);
}
</style>