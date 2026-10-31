<template>
  <view class="vip-page">
    <!-- 顶部权益区 -->
    <view class="header" :style="{ paddingTop: statusBarHeight + 20 + 'px' }">
      <!-- 返回按钮（custom 导航无系统返回键，必须自绘） -->
      <view
        class="back-btn btn-feedback"
        role="button"
        aria-label="返回上一页"
        :style="{ top: statusBarHeight + 10 + 'px' }"
        @tap="goBack"
      >
        <text class="back-icon" aria-hidden="true">‹</text>
      </view>
      <uni-icons class="crown float" type="vip" size="40"></uni-icons>
      <text class="title gradient-gold">心飞 VIP</text>
      <text class="subtitle">解锁全部特权，遇见更好的缘分</text>
      <view class="glow-circle c1" aria-hidden="true" />
      <view class="glow-circle c2" aria-hidden="true" />
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

    <!-- 套餐选择：语义单选组（涉及支付，必须可用键盘操作） -->
    <view class="section-head">
      <text class="section-title">会员套餐</text>
      <text class="section-hint">开通后立即生效</text>
    </view>
    <a11y-radio-group
      class="plans"
      label="会员套餐"
      :options="planOptions"
      :model-value="selectedId"
      option-class="plan-card btn-feedback"
      @update:model-value="selectedId = $event"
    >
      <template #option="{ option }">
        <text v-if="option.tag" class="plan-tag">{{ option.tag }}</text>
        <text class="plan-name">{{ option.name }}</text>
        <text class="plan-price">¥{{ option.priceYuan }}</text>
        <text class="plan-unit">{{ option.unit }}</text>
      </template>
    </a11y-radio-group>

    <!-- 支付按钮 -->
    <button class="pay-btn btn-feedback" :disabled="paying" @click="handlePay">
      {{ paying ? "支付中..." : `立即开通 ¥${currentPlan?.priceYuan || 0}` }}
    </button>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import A11yRadioGroup from "@/components/a11y-radio-group.vue";
import {
  getVipPackages,
  createVipOrder,
  cancelVipOrder,
  getVipStatus,
  getVipBenefits,
  type VipPackage,
  type UserVip,
  type ValueAddedService,
} from "@/api/mine";
import { getPayChannel } from "@/utils/pay";
import { safeGoBack } from "@/utils/nav";

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
    const priceYuan = Number((p.price / 100).toFixed(2));
    const months = Math.max(1, Math.round(p.durationDays / 30));
    const perMonth = String(parseFloat((priceYuan / months).toFixed(2)));
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

/**
 * 套餐选项：适配 A11yRadioGroup 的 { value, label } 结构。
 * label 需包含名称与价格，否则读屏用户无法获知套餐内容即要付费。
 */
const planOptions = computed(() =>
  plans.value.map((p) => ({
    value: p.id,
    label: `${p.name}，${p.priceYuan} 元，${p.unit}`,
    name: p.name,
    priceYuan: p.priceYuan,
    unit: p.unit,
    tag: p.tag,
  }))
);

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

/** 发起支付：下单 → 二次确认 → 渠道支付（当前为模拟渠道）→ 刷新状态 */
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
    // 2. 二次确认（生产环境接入真实渠道 SDK 后，此弹窗可保留为金额确认）
    const confirmed = await new Promise<boolean>((resolve) => {
      uni.showModal({
        title: "确认开通",
        content: `${plan.name} ¥${plan.priceYuan}（模拟支付）`,
        confirmText: "确认支付",
        cancelText: "取消",
        success: (res) => resolve(res.confirm),
        fail: () => resolve(false),
      });
    });
    if (!confirmed) {
      // 用户取消：关单避免堆积未支付订单；关单失败（网络等）静默，订单到期自动作废
      cancelVipOrder(order.orderNo).catch(() => {});
      uni.showToast({ title: "已取消支付", icon: "none" });
      return;
    }
    // 3. 渠道支付（utils/pay.ts 抽象：当前为模拟渠道，生产替换为微信/支付宝渠道实现）
    const paid = await getPayChannel().requestPayment({
      orderNo: order.orderNo,
      amountFen: Math.round(plan.priceYuan * 100),
      subject: plan.name,
    });
    if (!paid) {
      uni.showToast({ title: "支付未完成，请重试", icon: "none" });
      return;
    }
    uni.showToast({ title: "开通成功", icon: "success" });
    // 4. 刷新会员状态
    vipStatus.value = await getVipStatus();
  } catch (e) {
    // 请求层已统一 toast
  } finally {
    paying.value = false;
  }
}

/** 返回上一页（直接打开无上级页面时兜底回「我的」Tab；
 *  不能用 navigateBack 的 fail 回调做兜底——H5 无栈时 fail 不触发，会静默无动作） */
function goBack(): void {
  safeGoBack("/pages/mine/index");
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
  /* 自绘返回按钮：触摸目标 ≥44px */
  .back-btn { position: absolute; left: 16rpx; width: 72rpx; height: 72rpx; display: flex; align-items: center; justify-content: center; z-index: 10;
    .back-icon { font-size: 56rpx; color: rgba(255, 255, 255, 0.9); line-height: 1; }
  }
  .crown { font-size: 80rpx; display: block; margin-bottom: 8rpx; filter: drop-shadow(0 4rpx 12rpx rgba(255, 215, 0, 0.5)); }
  .title { font-size: 56rpx; font-weight: 900; display: block; letter-spacing: 4rpx; }
  .subtitle { font-size: 26rpx; color: rgba(255, 215, 0, 0.85); margin-top: 12rpx; display: block; }
}

/* 金色渐变文字 */
.gradient-gold { background: linear-gradient(90deg, #ffd700, #fff5c0, #ffd700); -webkit-background-clip: text; color: transparent; }

/* 强制颜色模式下 color:transparent 会让文字彻底消失，需回退为系统文本色 */
@media (forced-colors: active) {
  .gradient-gold {
    color: CanvasText;
    background: none;
    -webkit-background-clip: border-box;
    background-clip: border-box;
  }
}

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
  .status-expire { font-size: 22rpx; color: rgba(255, 215, 0, 0.85); }
}

/* ===== 权益对比 ===== */
.benefits-card { margin: 0 32rpx; background: rgba(255, 255, 255, 0.08); border-radius: 24rpx; padding: 8rpx 24rpx 24rpx; border: 1rpx solid rgba(255, 215, 0, 0.15); }
.shadow-gold { box-shadow: 0 8rpx 32rpx rgba(255, 215, 0, 0.1); }

.benefit-head { display: flex; align-items: center; padding: 20rpx 0 12rpx; border-bottom: 1rpx solid rgba(255, 215, 0, 0.2);
  /* 深色底上原 0.4/0.5 透明度仅约 2–3:1，统一提亮至 ≥4.5:1 */
  .head-name { flex: 1; font-size: 22rpx; color: rgba(255, 255, 255, 0.78); }
  .head-vip { width: 160rpx; text-align: center; font-size: 22rpx; color: #ffd700; font-weight: bold; }
}

.benefit-row { display: flex; align-items: center; padding: 18rpx 0; border-bottom: 1rpx solid rgba(255, 255, 255, 0.06);
  &:last-child { border-bottom: none; }
  .benefit-info { flex: 1; display: flex; flex-direction: column; gap: 4rpx; }
  .benefit-name { font-size: 26rpx; color: #fff; }
  .benefit-desc { font-size: 20rpx; color: rgba(255, 255, 255, 0.78); }
  .vip { width: 160rpx; text-align: center; font-size: 24rpx; color: #ffd700; font-weight: bold; }
}

/* ===== 套餐选择 ===== */
.section-head { display: flex; align-items: baseline; justify-content: space-between; margin: 40rpx 32rpx 0; }
.section-title { font-size: 30rpx; font-weight: bold; color: #fff; }
.section-hint { font-size: 22rpx; color: rgba(255, 255, 255, 0.6); }

.plans { display: flex; gap: 16rpx; margin: 20rpx 32rpx 32rpx; }

/* 选项节点由 a11y-radio-group 内部渲染，页面 scoped 样式必须 :deep 穿透，
   否则卡片外观/选中态全部丢失，plan-tag 也会因失去 relative 锚点飘到页顶 */
.plans :deep(.plan-card) {
  flex: 1; background: rgba(255, 255, 255, 0.08); border-radius: 20rpx; padding: 40rpx 12rpx 28rpx; text-align: center; position: relative; border: 2rpx solid transparent; transition: all 0.25s ease;
  &.active { border-color: #ffd700; background: rgba(255, 215, 0, 0.12); box-shadow: 0 0 24rpx rgba(255, 215, 0, 0.25); transform: translateY(-8rpx); }
}
.plan-card {
  /* 18rpx≈9px 低于最小可读字号，放大至 22rpx */
  .plan-tag { position: absolute; top: -18rpx; left: 50%; transform: translateX(-50%); background: var(--gradient-brand-h); color: #fff; font-size: 22rpx; padding: 4rpx 18rpx; border-radius: 16rpx; white-space: nowrap; z-index: 1; }
  .plan-name { font-size: 28rpx; color: #fff; display: block; }
  .plan-price { font-size: 48rpx; font-weight: 900; color: #ffd700; display: block; margin: 12rpx 0 4rpx; }
  .plan-unit { font-size: 22rpx; color: rgba(255, 255, 255, 0.78); display: block; }
}

/* ===== 支付按钮 ===== */
.pay-btn { position: fixed; left: 32rpx; right: 32rpx; bottom: calc(40rpx + env(safe-area-inset-bottom, 0px)); height: 96rpx; line-height: 96rpx;
  background: linear-gradient(90deg, #ffd700, #ffb347); color: #1a1a2e;
  font-size: 34rpx; font-weight: 900; border-radius: 48rpx; border: none;
  box-shadow: 0 8rpx 28rpx rgba(255, 179, 71, 0.4);
  &[disabled] { opacity: 0.6; box-shadow: none; }
}
</style>
