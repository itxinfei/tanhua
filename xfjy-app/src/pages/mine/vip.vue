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

    <!-- 会员状态 -->
    <view v-if="vipInfo.isVip" class="vip-status-card">
      <text class="status-icon">✨</text>
      <view class="status-text">
        <text class="status-level">{{ vipInfo.levelName }}生效中</text>
        <text class="status-expire">{{ vipInfo.expireText }}到期</text>
      </view>
    </view>

    <!-- 权益矩阵（后端 tb_value_added_service 驱动） -->
    <view class="benefits-card shadow-gold">
      <view class="benefit-head">
        <text class="head-name">权益</text>
        <text class="head-vip">所需等级</text>
      </view>
      <view class="benefit-row" v-for="b in benefits" :key="b.serviceCode">
        <view class="benefit-info">
          <text class="benefit-name">{{ b.name }}</text>
          <text class="benefit-desc">{{ b.description }}</text>
        </view>
        <text class="vip">VIP{{ b.vipLevelRequired }}+</text>
      </view>
    </view>

    <!-- 套餐选择 -->
    <view class="plans">
      <view v-for="plan in plans" :key="plan.id" class="plan-card btn-feedback" :class="{ selected: selectedId === plan.id }" @click="selectedId = plan.id">
        <text v-if="plan.tag" class="plan-tag">{{ plan.tag }}</text>
        <text class="plan-name">{{ plan.name }}</text>
        <text class="plan-price">¥{{ plan.priceYuan }}</text>
        <text class="plan-unit">{{ plan.unit }}</text>
      </view>
    </view>

    <!-- 支付按钮 -->
    <button class="pay-btn safe-area-bottom btn-feedback" :disabled="paying" @click="handlePay">
      {{ paying ? "支付中..." : `立即开通 ¥${currentPlan?.priceYuan || 0}` }}
    </button>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import {
  getVipPackages,
  createVipOrder,
  confirmVipPay,
  getVipStatus,
  getVipBenefits,
  type VipPackage,
  type UserVip,
  type ValueAddedService,
} from "@/api/mine";

/** 默认状态栏高度（px） */
const DEFAULT_STATUS_BAR_HEIGHT = 20;

const statusBarHeight = ref(DEFAULT_STATUS_BAR_HEIGHT);
/** 选中套餐 id（后端数字主键） */
const selectedId = ref<number>(0);
/** 套餐列表（从后端拉取） */
const packages = ref<VipPackage[]>([]);
/** 当前用户 VIP 状态 */
const vipStatus = ref<UserVip | null>(null);
/** 支付进行中标记（防重复提交） */
const paying = ref(false);

/** 权益矩阵（后端 tb_value_added_service 驱动，新增权益只改 DB 不改前端） */
const benefits = ref<ValueAddedService[]>([]);

/** 套餐展示模型：分转元 + 月均价 + 营销标签 */
const plans = computed(() =>
  packages.value.map((p) => {
    const priceYuan = Math.round(p.price / 100);
    const months = Math.max(1, Math.round(p.durationDays / 30));
    const perMonth = (priceYuan / months).toFixed(1);
    let tag = "";
    if (p.durationDays >= 365) {
      tag = "最划算";
    } else if (p.durationDays >= 90) {
      tag = "立省12元";
    }
    return {
      id: p.id,
      name: p.name,
      priceYuan,
      unit: `¥${perMonth}/月`,
      tag,
    };
  })
);

/** 当前选中套餐 */
const currentPlan = computed(() => plans.value.find((p) => p.id === selectedId.value));

/** 会员状态展示（等级名从套餐 name 动态取，避免硬编码与后端套餐名脱节） */
const vipInfo = computed(() => {
  const level = vipStatus.value?.level ?? 0;
  const isVip = level > 0;
  const pkg = packages.value.find((p) => p.level === level);
  const levelName = pkg ? pkg.name : "VIP";
  let expireText = "";
  if (isVip && vipStatus.value?.expireTime) {
    const d = new Date(vipStatus.value.expireTime);
    expireText = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
  }
  return { isVip, levelName, expireText };
});

/** 拉取套餐列表与会员状态 */
async function loadVipData(): Promise<void> {
  try {
    const [pkgs, status, benefitList] = await Promise.all([
      getVipPackages(),
      getVipStatus(),
      getVipBenefits(),
    ]);
    packages.value = pkgs || [];
    vipStatus.value = status;
    benefits.value = benefitList || [];
    // 默认选年卡（level=3），无年卡则选最后一个
    const yearPkg = packages.value.find((p) => p.level === 3);
    selectedId.value = yearPkg?.id ?? packages.value[packages.value.length - 1]?.id ?? 0;
  } catch (e) {
    // 请求层已统一 toast，这里静默
  }
}

/** 发起支付：下单 → 二次确认 → 沙箱确认 → 刷新状态 */
async function handlePay(): Promise<void> {
  if (paying.value || !selectedId.value) {
    return;
  }
  const plan = currentPlan.value;
  if (!plan) {
    return;
  }
  paying.value = true;
  try {
    // 1. 创建订单
    const order = await createVipOrder(selectedId.value);
    if (!order?.orderNo) {
      uni.showToast({ title: "下单失败，请重试", icon: "none" });
      return;
    }
    // 2. 二次确认（沙箱环境直接确认；生产环境此处拉起微信/支付宝 SDK）
    const confirmed = await new Promise<boolean>((resolve) => {
      uni.showModal({
        title: "确认开通",
        content: `${plan.name} ¥${plan.priceYuan}（模拟支付）`,
        confirmText: "确认支付",
        success: (res) => resolve(res.confirm),
        fail: () => resolve(false),
      });
    });
    if (!confirmed) {
      return;
    }
    // 3. 沙箱支付确认
    await confirmVipPay(order.orderNo);
    uni.showToast({ title: "开通成功", icon: "success" });
    // 4. 刷新会员状态
    vipStatus.value = await getVipStatus();
  } catch (e) {
    // 请求层已统一 toast
  } finally {
    paying.value = false;
  }
}

onLoad(() => {
  const sysInfo = uni.getSystemInfoSync();
  statusBarHeight.value = sysInfo.statusBarHeight || DEFAULT_STATUS_BAR_HEIGHT;
  loadVipData();
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

/* ===== 会员状态卡 ===== */
.vip-status-card { margin: 24rpx 32rpx 0; background: linear-gradient(90deg, rgba(255, 215, 0, 0.18), rgba(255, 179, 71, 0.12)); border: 1rpx solid rgba(255, 215, 0, 0.35); border-radius: 20rpx; padding: 24rpx 28rpx; display: flex; align-items: center; gap: 16rpx;
  .status-icon { font-size: 40rpx; }
  .status-text { display: flex; flex-direction: column; gap: 4rpx; }
  .status-level { font-size: 28rpx; color: #ffd700; font-weight: bold; }
  .status-expire { font-size: 22rpx; color: rgba(255, 215, 0, 0.6); }
}

/* ===== 权益对比 ===== */
.benefits-card { margin: 0 32rpx; background: rgba(255, 255, 255, 0.08); border-radius: 24rpx; padding: 8rpx 24rpx 24rpx; border: 1rpx solid rgba(255, 215, 0, 0.15); }
.shadow-gold { box-shadow: 0 8rpx 32rpx rgba(255, 215, 0, 0.1); }

.benefit-head { display: flex; align-items: center; padding: 20rpx 0 12rpx; border-bottom: 1rpx solid rgba(255, 215, 0, 0.2);
  .head-name { flex: 1; font-size: 22rpx; color: rgba(255, 255, 255, 0.5); }
  .head-vip { width: 160rpx; text-align: center; font-size: 22rpx; color: #ffd700; font-weight: bold; }
}

.benefit-row { display: flex; align-items: center; padding: 18rpx 0; border-bottom: 1rpx solid rgba(255, 255, 255, 0.06);
  &:last-child { border-bottom: none; }
  .benefit-info { flex: 1; display: flex; flex-direction: column; gap: 4rpx; }
  .benefit-name { font-size: 26rpx; color: #fff; }
  .benefit-desc { font-size: 20rpx; color: rgba(255, 255, 255, 0.4); }
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
  &[disabled] { opacity: 0.6; box-shadow: none; }
}
</style>