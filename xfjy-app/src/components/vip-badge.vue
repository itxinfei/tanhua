<template>
  <view
    v-if="promo || isVip"
    class="vip-badge"
    :class="`vip-badge--${variant}`"
    aria-hidden="true"
  >
    <view
      v-if="variant === 'emblem'"
      class="vip-badge-crown i-ph-crown"
    />
    <text
      v-else
      class="vip-badge-text"
    >
      {{ label }}
    </text>
  </view>
</template>

<script setup lang="ts">
/**
 * VIP 徽章（全局唯一实现，页面/组件不要各自复制一份）
 *
 * 两种形态：
 * - pill：文字药丸，用于昵称旁、状态卡、权益卡等身份标识位
 * - emblem：金底方徽 + 皇冠，用于 VIP 入口卡等需要大图标的场景
 *
 * 渲染闸门：
 * - 身份态：传 isVip（真实 VIP 状态）。false/undefined 时不渲染，非 VIP 的 DOM 与布局不变
 * - 营销态：传 promo（如「VIP 专享权益」锁定卡）。不受 isVip 限制，也不冒充用户身份
 * - level：预留给后续分级（VIP1/2/3 差异化），当前不改变视觉，等后端分级字段落地再启用
 *
 * 动效：只用 background-position（paint-only）做慢速扫光，复用 App.vue 全局 shimmer，
 * 不逐帧改 box-shadow / border-color；关闭列表见 App.vue 的 prefers-reduced-motion。
 */
withDefaults(defineProps<{
  /** 真实 VIP 状态：身份态渲染闸门 */
  isVip?: boolean;
  /** 营销态：始终渲染（不冒充身份） */
  promo?: boolean;
  variant?: "pill" | "emblem";
  label?: string;
  /** 预留：会员等级，当前未参与样式计算 */
  level?: number;
}>(), {
  isVip: false,
  promo: false,
  variant: "pill",
  label: "VIP",
  level: 0,
});
</script>

<style scoped lang="scss">
.vip-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

/* 文字药丸：金渐变底 + 深色字，在任何底色上都有对比度 */
.vip-badge--pill {
  height: 36rpx;
  padding: 0 14rpx;
  border-radius: var(--radius-pill);
  background-image: linear-gradient(
    100deg,
    var(--gold) 0%,
    var(--gold-light) 30%,
    var(--gold-soft) 62%,
    var(--gold) 100%
  );
  background-size: 220% 100%;
  background-position: 0 0;
  box-shadow: 0 2rpx 8rpx rgba(var(--gold-rgb), 0.35);
  animation: shimmer 4.5s linear infinite;

  .vip-badge-text {
    font-size: 20rpx;
    line-height: 1;
    font-weight: 700;
    letter-spacing: 1rpx;
    color: var(--bg-deep);
  }
}

/* 金底方徽：金渐变 + 内侧顶部高光 + 暗投影，深色皇冠压在上面 */
.vip-badge--emblem {
  width: 64rpx;
  height: 64rpx;
  border-radius: var(--radius-md);
  background: linear-gradient(145deg, var(--gold-light) 0%, var(--gold) 45%, var(--gold-deep) 100%);
  box-shadow:
    0 4rpx 12rpx rgba(0, 0, 0, 0.35),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.55);

  .vip-badge-crown {
    font-size: var(--fs-xl);
    line-height: 1;
    color: var(--bg-deep);
  }
}
</style>
