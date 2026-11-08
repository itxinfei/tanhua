<template>
  <view
    v-if="isVip"
    class="vip-ring"
    aria-hidden="true"
  >
    <view class="vip-ring-spin" />
  </view>
</template>

<script setup lang="ts">
/**
 * VIP 金色头像环（全局唯一实现）
 *
 * 当前为「预留未启用」：他人资料页/列表/评论区等处后端尚未下发 VIP 字段，
 * 前端不传假值、不渲染；等 xfjy-server 的 VO 补上 vipLevel 后再逐处接入。
 *
 * 用法（接入时）：
 * - 头像容器 position: relative，本组件放在头像之前（层级更低）
 * - 环比头像外扩 6rpx，中心区域由上层圆形头像自身遮住，无需 mask（小程序兼容更好）
 * - 传 isVip（真实状态）；false 时不渲染，列表项尺寸与布局完全不变
 *
 * 动效：只做 transform 旋转（复用 App.vue 全局 spin），不改 box-shadow / border-color；
 * 关闭列表见 App.vue 的 prefers-reduced-motion。
 */
withDefaults(defineProps<{
  isVip?: boolean;
  /** 预留：会员等级，后续用于分级配色，当前未参与计算 */
  level?: number;
}>(), {
  isVip: false,
  level: 0,
});
</script>

<style scoped lang="scss">
.vip-ring {
  position: absolute;
  inset: -6rpx;
  border-radius: 50%;
  overflow: hidden;
  pointer-events: none;
}

.vip-ring-spin {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: conic-gradient(
    from 0deg,
    rgba(var(--gold-rgb), 0.2) 0%,
    var(--gold-light) 25%,
    rgba(var(--gold-soft-rgb), 0.5) 55%,
    rgba(var(--gold-rgb), 0.2) 100%
  );
  animation: spin 6s linear infinite;
  will-change: transform;
}
</style>
