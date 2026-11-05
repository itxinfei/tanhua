<template>
  <view class="vip-page">
    <!-- 顶部权益区 -->
    <view class="header" :style="{ paddingTop: statusBarHeight + 20 + 'px' }">
      <!-- 返回按钮（custom 导航无系统返回键，必须自绘） -->
      <!-- 返回按钮必须用 a11y-button（全站按钮单一真源），图标用 UnoCSS Phosphor i-ph-*，热区 88rpx 满足 WCAG 2.5.5 -->
      <a11y-button
        class="back-btn"
        label="返回上一页"
        :style="{ top: statusBarHeight + 10 + 'px' }"
        @tap="goBack"
      >
        <view class="i-ph-arrow-left back-icon" aria-hidden="true" />
      </a11y-button>
      <view class="crown float i-ph-crown" aria-hidden="true" />
      <text class="title gradient-gold">心飞 VIP</text>
      <text class="subtitle">解锁全部特权，遇见更好的缘分</text>
      <view class="glow-circle c1" aria-hidden="true" />
      <view class="glow-circle c2" aria-hidden="true" />
    </view>

    <!-- 可滚动内容区 -->
    <scroll-view class="content-scroll" scroll-y>
      <!-- 会员状态 -->
      <view v-if="vipInfo.isVip" class="vip-status-card">
        <view class="status-icon i-ph-sparkle" aria-hidden="true" />
        <view class="status-text">
          <text class="status-level">{{ vipInfo.levelName }}生效中</text>
          <text class="status-expire">{{ vipInfo.expireText }}到期</text>
        </view>
      </view>

      <!-- 加载失败提示：不再静默空白，提供重试入口 -->
      <view v-if="loadError" class="load-error">
        <text class="load-error-text">数据加载失败，部分内容未显示</text>
        <a11y-button class="retry-btn" label="重新加载" @tap="loadVipData">重新加载</a11y-button>
      </view>

      <!-- 套餐选择置顶：金额必须首屏可见（付费转化第一优先级） -->
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
          <text class="plan-level">VIP{{ option.level }}</text>
          <text class="plan-name">{{ option.name }}</text>
          <text class="plan-price">¥{{ option.priceYuan }}</text>
          <text class="plan-unit">{{ option.unit }}</text>
        </template>
      </a11y-radio-group>

      <!-- 权益矩阵（后端 tb_value_added_service 驱动） -->
      <view class="benefits-card shadow-gold">
        <view class="benefit-head">
          <text class="head-name">权益</text>
          <text class="head-vip">所需等级</text>
        </view>
        <!-- 等级口径说明：把 VIP1+/2+/3+ 与套餐等级对应起来 -->
        <view class="level-note">
          <text class="level-note-text">开通对应等级的会员套餐即可解锁该权益（VIP1 / VIP2 / VIP3 依次对应上方月卡 / 季卡 / 年卡）</text>
        </view>
        <view class="benefit-row" v-for="b in benefits" :key="b.serviceCode">
          <view class="benefit-info">
            <text class="benefit-name">{{ b.name }}</text>
            <text class="benefit-desc">{{ b.description }}</text>
          </view>
          <text class="vip">VIP{{ b.vipLevelRequired }}+</text>
        </view>
      </view>

      <!-- 服务说明：价格口径、生效时间、续费与退款，避免付费前有疑问 -->
      <view class="service-note">
        <text class="service-note-title">购买须知</text>
        <text class="service-note-line">· 一次性购买，开通后立即生效，到期不自动续费</text>
        <text class="service-note-line">· 权益等级按套餐 VIP 等级解锁，等级越高可用权益越多</text>
        <text class="service-note-line">· 虚拟商品一经开通不支持退款，如有问题可联系在线客服</text>
      </view>

      <!-- 底部占位：给固定操作栏留空间 -->
      <view class="bottom-placeholder" />
    </scroll-view>

    <!-- 底部固定支付栏（固定在 tabbar 正上方） -->
    <view class="pay-bar">
      <!-- 明确当前选中套餐，避免 CTA 金额与实际选择脱节 -->
      <text v-if="currentPlan" class="pay-selected">
        已选：{{ currentPlan.name }}（VIP{{ currentPlan.level }}） · ¥{{ currentPlan.priceYuan }}
      </text>
      <!-- 未选套餐（如套餐接口失败）时禁用，避免点击「¥0」静默无动作 -->
      <a11y-button class="pay-btn" :label="selectedId ? '立即开通会员' : '请先选择套餐'" :disabled="paying || !selectedId" @tap="handlePay">
        <text class="pay-btn-text">{{ paying ? "支付中..." : selectedId ? `立即开通 ¥${currentPlan?.priceYuan || 0}` : "请选择套餐" }}</text>
      </a11y-button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import A11yButton from "@/components/a11y-button.vue";
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
import { success, fail } from "@/utils/ux";
import { useUserStore } from "@/store/user";

const userStore = useUserStore();

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
/** 数据加载失败标记：展示重试入口，避免静默空白让用户以为菜单消失 */
const loadError = ref(false);

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
      level: p.level,
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
    label: `${p.name}，VIP${p.level} 等级，${p.priceYuan} 元，${p.unit}`,
    name: p.name,
    level: p.level,
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

/** 拉取套餐列表与会员状态：allSettled 各请求独立容错，单个接口失败不清空其它数据 */
async function loadVipData(): Promise<void> {
  loadError.value = false;
  const results = await Promise.allSettled([
    getVipPackages(),
    getVipStatus(),
    getVipBenefits(),
  ]);
  if (results[0].status === "fulfilled") packages.value = results[0].value || [];
  if (results[1].status === "fulfilled") vipStatus.value = results[1].value;
  if (results[2].status === "fulfilled") benefits.value = results[2].value || [];
  loadError.value = results.some((r) => r.status === "rejected");
  if (loadError.value) {
    // 请求层已统一 toast，这里只标记错误态展示重试入口
  }
  // 默认选年卡（level=3），无年卡则选最后一个
  const yearPkg = packages.value.find((p) => p.level === 3);
  selectedId.value = yearPkg?.id ?? packages.value[packages.value.length - 1]?.id ?? 0;
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
      fail("下单失败，请重试");
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
      fail("已取消支付");
      return;
    }
    // 3. 渠道支付（utils/pay.ts 抽象：当前为模拟渠道，生产替换为微信/支付宝渠道实现）
    const paid = await getPayChannel().requestPayment({
      orderNo: order.orderNo,
      amountFen: Math.round(plan.priceYuan * 100),
      subject: plan.name,
    });
    if (!paid) {
      fail("支付未完成，请重试");
      return;
    }
    success("开通成功");
    // 同步 store 的 vip 标志，其它页面（如「我的」VIP 卡）返回前即可读到新状态
    userStore.updateUserInfo({ vip: true });
    // 4. 刷新会员状态（失败不吞：用户已看到成功提示，状态卡需给出重试指引）
    try {
      vipStatus.value = await getVipStatus();
    } catch {
      fail("会员状态刷新失败，重新进入页面可重试");
    }
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
  // getSystemInfoSync 为废弃 API，改用 getWindowInfo（旧版运行时无此 API 时取兜底值）
  try {
    statusBarHeight.value = uni.getWindowInfo().statusBarHeight || DEFAULT_STATUS_BAR_HEIGHT;
  } catch {
    statusBarHeight.value = DEFAULT_STATUS_BAR_HEIGHT;
  }
  loadVipData();
});
</script>

<style lang="scss" scoped>
/* ===== 页面容器 =====
 * 布局：header 固定 + content-scroll 可滚动 + pay-bar 固定在 tabbar 正上方
 * pay-bar 用 position: fixed; bottom: var(--tab-bar-height) 确保不被底栏遮挡
 */
.vip-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--bg-deep);
  overflow: hidden;
}

/* ===== 可滚动内容区 =====
   min-height:0 必须：纵向 flex 子项默认 min-height:auto，内容较高时
   会撑破页面并被父级 overflow:hidden 裁切 */
.content-scroll {
  flex: 1 1 auto;
  min-height: 0;
  min-width: 0;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

/* 底部占位：确保最后内容不被固定支付栏遮挡 */
.bottom-placeholder {
  height: 200rpx;
}

/* ===== 加载失败态 ===== */
.load-error { margin: 24rpx 32rpx 0; padding: 40rpx; text-align: center; background: rgba(255, 255, 255, 0.06); border: 1rpx solid rgba(255, 215, 0, 0.3); border-radius: 24rpx;
  .load-error-text { font-size: 26rpx; color: rgba(255, 215, 0, 0.8); display: block; }
  .retry-btn { margin-top: 24rpx; display: inline-flex; align-items: center; justify-content: center; min-height: 88rpx; font-size: 26rpx; color: var(--bg-deep); background: linear-gradient(135deg, var(--gold), var(--gold-soft)); border-radius: 999rpx; padding: 0 56rpx; font-weight: bold; border: none; }
}

/* ===== 顶部权益区 ===== */
.header { text-align: center; padding-bottom: 24rpx; background: linear-gradient(180deg, var(--bg-deep-grad) 0%, var(--bg-deep) 100%); position: relative; overflow: hidden; flex-shrink: 0;
  /* 自绘返回按钮：毛玻璃圆形底，触摸目标 ≥44px */
    .back-btn { position: absolute; left: 24rpx; width: 88rpx; height: 88rpx; display: flex; align-items: center; justify-content: center; z-index: 10; background: rgba(255, 255, 255, 0.12); border-radius: 50%; backdrop-filter: blur(8px);
      /* UnoCSS mask 图标：font-size 控制图标尺寸，color 控制填充色，mask 图标天然视觉居中 */
      .back-icon { font-size: 44rpx; color: rgba(255, 255, 255, 0.95); line-height: 1; }
    }
  .crown { font-size: 48rpx; display: block; margin-bottom: 6rpx; filter: drop-shadow(0 6rpx 16rpx rgba(255, 215, 0, 0.6)); }
  .title { font-size: 40rpx; font-weight: 900; display: block; letter-spacing: 6rpx; }
  .subtitle { font-size: 24rpx; color: rgba(255, 215, 0, 0.8); margin-top: 8rpx; display: block; letter-spacing: 2rpx; }
}

/* 金色渐变文字 */
.gradient-gold { background: linear-gradient(90deg, var(--gold), var(--gold-light), var(--gold)); -webkit-background-clip: text; color: transparent; }

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
.glow-circle { position: absolute; border-radius: 50%; filter: blur(80rpx); pointer-events: none;
  &.c1 { width: 360rpx; height: 360rpx; background: rgba(255, 215, 0, 0.15); top: -80rpx; left: -100rpx; }
  &.c2 { width: 280rpx; height: 280rpx; background: rgba(254, 60, 114, 0.1); bottom: -60rpx; right: -80rpx; }
}

/* ===== 会员状态卡 ===== */
.vip-status-card { margin: 24rpx 32rpx 0; background: linear-gradient(135deg, rgba(255, 215, 0, 0.2), rgba(255, 179, 71, 0.12)); border: 1rpx solid rgba(255, 215, 0, 0.4); border-radius: 24rpx; padding: 28rpx 32rpx; display: flex; align-items: center; gap: 20rpx; box-shadow: 0 8rpx 32rpx rgba(255, 215, 0, 0.12);
  .status-icon { font-size: 44rpx; }
  .status-text { display: flex; flex-direction: column; gap: 6rpx; }
  .status-level { font-size: 30rpx; color: var(--gold); font-weight: bold; }
  .status-expire { font-size: 22rpx; color: rgba(255, 215, 0, 0.8); }
}

/* ===== 权益对比 ===== */
.benefits-card { margin: 0 32rpx; background: rgba(255, 255, 255, 0.06); border-radius: 24rpx; padding: 8rpx 24rpx 24rpx; border: 1rpx solid rgba(255, 215, 0, 0.12); backdrop-filter: blur(12px); }
.shadow-gold { box-shadow: 0 8rpx 32rpx rgba(0, 0, 0, 0.2); }

.benefit-head { display: flex; align-items: center; padding: 20rpx 0 12rpx; border-bottom: 1rpx solid rgba(255, 215, 0, 0.2);
  /* 深色底上原 0.4/0.5 透明度仅约 2–3:1，统一提亮至 ≥4.5:1 */
  .head-name { flex: 1; font-size: 22rpx; color: rgba(255, 255, 255, 0.78); }
  .head-vip { width: 160rpx; text-align: center; font-size: 22rpx; color: var(--gold); font-weight: bold; }
}

.benefit-row { display: flex; align-items: center; padding: 20rpx 0; border-bottom: 1rpx solid rgba(255, 255, 255, 0.08);
  &:last-child { border-bottom: none; }
  .benefit-info { flex: 1; display: flex; flex-direction: column; gap: 6rpx; }
  .benefit-name { font-size: 28rpx; color: var(--text-inverse); font-weight: 500; }
  .benefit-desc { font-size: 22rpx; color: rgba(255, 255, 255, 0.7); }
  .vip { width: 160rpx; text-align: center; font-size: 24rpx; color: var(--gold); font-weight: bold; }
}

/* ===== 套餐选择 ===== */
.section-head { display: flex; align-items: baseline; justify-content: space-between; margin: 24rpx 32rpx 0; }
.section-title { font-size: 32rpx; font-weight: bold; color: var(--text-inverse); }
.section-hint { font-size: 22rpx; color: rgba(255, 255, 255, 0.55); }

.plans { display: flex; gap: 20rpx; margin: 20rpx 32rpx 24rpx; }

/* 选项节点由 a11y-radio-group 内部渲染，页面 scoped 样式必须 :deep 穿透，
   否则卡片外观/选中态全部丢失，plan-tag 也会因失去 relative 锚点飘到页顶 */
/* 等级口径说明 / 购买须知 */
.level-note {
  padding: 4rpx 0 20rpx;
  border-bottom: 1rpx solid rgba(255, 255, 255, 0.08);

  .level-note-text {
    font-size: 22rpx;
    line-height: 1.6;
    color: rgba(255, 255, 255, 0.62);
  }
}

.service-note {
  margin: 32rpx 8rpx 8rpx;
  padding: 24rpx;
  border-radius: 20rpx;
  background: rgba(255, 255, 255, 0.05);
  border: 1rpx solid rgba(255, 255, 255, 0.08);

  .service-note-title {
    display: block;
    font-size: 26rpx;
    font-weight: 600;
    color: rgba(255, 255, 255, 0.9);
    margin-bottom: 12rpx;
  }

  .service-note-line {
    display: block;
    font-size: 22rpx;
    line-height: 1.7;
    color: rgba(255, 255, 255, 0.6);
  }
}

/* 套餐等级徽标：把 VIP1/2/3 与月/季/年卡显式绑定 */
.plans :deep(.plan-level) {
  display: block;
  font-size: 20rpx;
  font-weight: 700;
  letter-spacing: 1rpx;
  color: var(--gold);
  margin-bottom: 6rpx;
}

/* 支付栏当前选中态 */
.pay-selected {
  display: block;
  text-align: center;
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.75);
  margin-bottom: 12rpx;
}

.plans :deep(.plan-card) {
  /* min-width:0：长套餐名/标签才能约束在卡内，不撑宽卡片挤掉间距与留白 */
  flex: 1 1 0; min-width: 0; background: rgba(255, 255, 255, 0.06); border-radius: 24rpx; padding: 36rpx 12rpx 24rpx; text-align: center; position: relative; border: 2rpx solid rgba(255, 255, 255, 0.08); transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  &.active { border-color: var(--gold); background: linear-gradient(180deg, rgba(255, 215, 0, 0.15), rgba(255, 215, 0, 0.05)); box-shadow: 0 0 32rpx rgba(255, 215, 0, 0.2), inset 0 1rpx 0 rgba(255, 255, 255, 0.1); transform: translateY(-8rpx); }
}
.plan-card {
  .plan-tag { position: absolute; top: -20rpx; left: 50%; transform: translateX(-50%); background: linear-gradient(90deg, var(--gold), var(--gold-soft)); color: var(--bg-deep); font-size: 20rpx; font-weight: bold; padding: 6rpx 20rpx; border-radius: 999rpx; white-space: nowrap; z-index: 1; box-shadow: 0 4rpx 12rpx rgba(255, 215, 0, 0.3); }
  .plan-name { font-size: 28rpx; color: rgba(255, 255, 255, 0.9); display: block; font-weight: 500; }
  .plan-price { font-size: 52rpx; font-weight: 900; color: var(--gold); display: block; margin: 16rpx 0 8rpx; text-shadow: 0 2rpx 8rpx rgba(255, 215, 0, 0.3); }
  .plan-unit { font-size: 22rpx; color: rgba(255, 255, 255, 0.6); display: block; }
}

/* ===== 底部固定支付栏 =====
 * position: fixed + bottom: var(--tab-bar-height) 确保正好在 tabbar 正上方
 * 不遮挡、不贴底、有毛玻璃质感
 */
.pay-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: var(--tab-bar-height, 0px);
  padding: 20rpx 32rpx calc(20rpx + env(safe-area-inset-bottom, 0px));
  background: rgba(26, 26, 46, 0.92);
  backdrop-filter: blur(20px);
  box-shadow: 0 -8rpx 32rpx rgba(0, 0, 0, 0.3);
  z-index: 100;
}

/* ===== 支付按钮 ===== */
.pay-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 104rpx;
  background: linear-gradient(135deg, var(--gold) 0%, var(--gold-soft) 50%, #ff9a3c 100%);
  color: var(--bg-deep);
  font-size: 36rpx;
  font-weight: 900;
  letter-spacing: 2rpx;
  border-radius: 52rpx;
  border: none;
  box-shadow: 0 12rpx 36rpx rgba(255, 179, 71, 0.45), inset 0 2rpx 0 rgba(255, 255, 255, 0.3);
  position: relative;
  overflow: hidden;
  transition: transform 0.2s ease;

  &:active {
    transform: scale(0.97);
  }

  /* 微光扫过动画 */
  &::after {
    content: '';
    position: absolute;
    top: 0;
    left: -100%;
    width: 100%;
    height: 100%;
    background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.3), transparent);
    animation: shimmer 3s infinite;
  }

  &.a11y-btn--disabled {
    opacity: 0.5;
    box-shadow: none;
    &::after { animation: none; }
  }
}

.pay-btn-text {
  position: relative;
  z-index: 1;
}

@keyframes shimmer {
  0% { left: -100%; }
  50%, 100% { left: 100%; }
}
</style>
