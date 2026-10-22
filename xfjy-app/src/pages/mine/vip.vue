<template>
  <view class="vip-page">
    <!-- 顶部权益区 -->
    <view class="header" :style="{ paddingTop: statusBarHeight + 20 + 'px' }">
      <text class="title">心飞 VIP</text>
      <text class="subtitle">解锁全部特权，遇见更好的缘分</text>
    </view>

    <!-- 权益对比 -->
    <view class="benefits-card">
      <view class="benefit-row" v-for="b in benefits" :key="b.name">
        <text class="benefit-name">{{ b.name }}</text>
        <text class="free">{{ b.free }}</text>
        <text class="vip">{{ b.vip }}</text>
      </view>
    </view>

    <!-- 套餐选择 -->
    <view class="plans">
      <view v-for="plan in plans" :key="plan.id" class="plan-card" :class="{ selected: selectedPlan === plan.id }" @click="selectedPlan = plan.id">
        <text v-if="plan.tag" class="plan-tag">{{ plan.tag }}</text>
        <text class="plan-name">{{ plan.name }}</text>
        <text class="plan-price">¥{{ plan.price }}</text>
        <text class="plan-unit">{{ plan.unit }}</text>
      </view>
    </view>

    <!-- 支付按钮 -->
    <button class="pay-btn safe-area-bottom" @click="handlePay">
      立即开通 ¥{{ currentPlan?.price || 0 }}
    </button>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from "vue";

const statusBarHeight = ref(20);
const selectedPlan = ref("year");

const benefits = [
  { name: "无限滑动", free: "50次/天", vip: "无限" },
  { name: "超级喜欢", free: "—", vip: "5次/天" },
  { name: "查看谁喜欢我", free: "—", vip: "全部可见" },
  { name: "位置穿越", free: "—", vip: "任意城市" },
  { name: "撤销左滑", free: "—", vip: "不限次" },
  { name: "专属标识", free: "—", vip: "VIP角标" },
];

const plans = [
  { id: "month", name: "月卡", price: 28, unit: "¥28/月", tag: "" },
  { id: "quarter", name: "季卡", price: 68, unit: "¥22.7/月", tag: "" },
  { id: "year", name: "年卡", price: 198, unit: "¥16.5/月", tag: "最划算" },
];

const currentPlan = computed(() => plans.find((p) => p.id === selectedPlan.value));

async function handlePay() {
  // TODO: 对接微信/支付宝支付
  uni.showActionSheet({
    itemList: ["微信支付", "支付宝支付"],
    success: () => {
      uni.showToast({ title: "支付功能开发中", icon: "none" });
    },
  });
}
</script>

<style lang="scss" scoped>
.vip-page { min-height: 100vh; background: #1a1a2e; padding-bottom: 180rpx; }

.header { text-align: center; padding-bottom: 40rpx;
  background: linear-gradient(180deg, #2c2c54 0%, #1a1a2e 100%);
  .title { font-size: 56rpx; font-weight: 900; color: #ffd700; display: block; }
  .subtitle { font-size: 26rpx; color: rgba(255, 215, 0, 0.6); margin-top: 12rpx; display: block; }
}

.benefits-card { margin: 0 32rpx; background: rgba(255, 255, 255, 0.08); border-radius: 24rpx; padding: 24rpx;
  .benefit-row { display: flex; align-items: center; padding: 16rpx 0; border-bottom: 1rpx solid rgba(255, 255, 255, 0.06);
    &:last-child { border-bottom: none; }
    .benefit-name { flex: 1; font-size: 26rpx; color: #fff; }
    .free { width: 160rpx; text-align: center; font-size: 24rpx; color: rgba(255, 255, 255, 0.4); }
    .vip { width: 160rpx; text-align: center; font-size: 24rpx; color: #ffd700; font-weight: bold; }
  }
}

.plans { display: flex; gap: 16rpx; margin: 32rpx; }
.plan-card { flex: 1; background: rgba(255, 255, 255, 0.08); border-radius: 20rpx; padding: 32rpx 16rpx; text-align: center; position: relative; border: 2rpx solid transparent;
  &.selected { border-color: #ffd700; background: rgba(255, 215, 0, 0.1); }
  .plan-tag { position: absolute; top: -16rpx; left: 50%; transform: translateX(-50%); background: #ff5e8e; color: #fff; font-size: 18rpx; padding: 2rpx 16rpx; border-radius: 16rpx; }
  .plan-name { font-size: 28rpx; color: #fff; display: block; }
  .plan-price { font-size: 48rpx; font-weight: 900; color: #ffd700; display: block; margin: 12rpx 0 4rpx; }
  .plan-unit { font-size: 20rpx; color: rgba(255, 255, 255, 0.4); display: block; }
}

.pay-btn { position: fixed; left: 32rpx; right: 32rpx; bottom: 40rpx; height: 96rpx; line-height: 96rpx;
  background: linear-gradient(90deg, #ffd700, #ffb347); color: #1a1a2e;
  font-size: 34rpx; font-weight: 900; border-radius: 48rpx; border: none;
}
</style>