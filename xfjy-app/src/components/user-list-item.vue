<template>
  <view class="user-list-item">
    <!-- 主体区：头像 + 信息；clickable 时用 a11y-button 承载（键盘可达 + 无障碍标签） -->
    <a11y-button
      v-if="clickable"
      class="item-main"
      :label="`查看 ${nickname || '未设置昵称'} 的个人主页`"
      @tap="emit('tap-item')"
    >
      <view class="item-avatar-wrap">
        <image
          class="item-avatar"
          :src="avatar"
          mode="aspectFill"
          lazy-load
          aria-hidden="true"
        />
        <view v-if="online" class="item-online-dot" aria-hidden="true" />
      </view>
      <view class="item-info">
        <view class="item-name-row">
          <text class="item-nickname">{{ nickname || '未设置昵称' }}</text>
          <text v-if="age" class="item-age-badge">{{ age }}岁</text>
        </view>
        <slot name="meta">
          <text v-if="meta" class="item-meta">{{ meta }}</text>
        </slot>
      </view>
    </a11y-button>
    <!-- 不可点击时用普通 view 承载 -->
    <view v-else class="item-main">
      <view class="item-avatar-wrap">
        <image
          class="item-avatar"
          :src="avatar"
          mode="aspectFill"
          lazy-load
          :aria-label="`${nickname || '未设置昵称'} 的头像`"
        />
        <view v-if="online" class="item-online-dot" aria-hidden="true" />
      </view>
      <view class="item-info">
        <view class="item-name-row">
          <text class="item-nickname">{{ nickname || '未设置昵称' }}</text>
          <text v-if="age" class="item-age-badge">{{ age }}岁</text>
        </view>
        <slot name="meta">
          <text v-if="meta" class="item-meta">{{ meta }}</text>
        </slot>
      </view>
    </view>
    <!-- 右侧操作区（按钮嵌在卡片内右缘，不再孤立悬挂） -->
    <view v-if="$slots.action" class="item-action">
      <slot name="action" />
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 通用用户列表行组件
 *
 * 统一「头像 + 昵称 + 副信息 + 右侧操作按钮」的行布局，
 * 消除 blacklist / nearby / friends 三页各自的 .user-row 重复实现。
 *
 * Props：
 *  - avatar：头像 URL（必填，已过 resolveAvatar）
 *  - nickname：昵称（空时显示"未设置昵称"）
 *  - meta：副信息纯文本（如"女 · 24岁 · 北京"）；需自定义 tag/badge 时改用 #meta 插槽
 *  - age：年龄数字，非空时在昵称右侧渲染渐变小胶囊
 *  - clickable：整行主体是否可点击进详情（true 时由 a11y-button 承载，支持键盘）
 *  - online：是否在线（true 时头像右下角渲染绿色小圆点）
 *
 * Slots：
 *  - #meta：自定义副信息（优先于 meta prop）
 *  - #action：右侧操作按钮，推荐用 a11y-button 视觉变体（primary/ghost/text + size="sm"）
 *
 * Events：
 *  - tap-item：主体点击（仅 clickable=true 时触发）
 */
import A11yButton from "@/components/a11y-button.vue";

interface Props {
  avatar: string;
  nickname?: string;
  meta?: string;
  age?: number | string;
  clickable?: boolean;
  online?: boolean;
}

withDefaults(defineProps<Props>(), {
  nickname: "",
  meta: "",
  age: "",
  clickable: false,
  online: false,
});

const emit = defineEmits<{ (e: "tap-item"): void }>();
</script>

<style lang="scss" scoped>
/* 整行一张卡片（探探式），操作按钮嵌在卡片内右缘 */
.user-list-item {
  display: flex;
  align-items: center;
  gap: 20rpx;
  margin-bottom: 16rpx;
  /* 水平内边距 32rpx：给头像/按钮与卡片边缘留出明确呼吸空间（16px@375） */
  padding: 24rpx 32rpx;
  /* 行高真源：富列表行（带头像 + 两行文字）统一走 --row-h-lg，
     与 mine/settings 的 --row-h-md、紧凑操作行的 --row-h-sm 形成三档阶梯。
     只设 min-height 不设固定 height：右侧操作按钮更高时行可自然撑开。 */
  min-height: var(--row-h-touch-lg);
  /* border-box + 宽度 100%：padding 计入宽度，避免卡片在 scroll-view 内撑破容器导致右侧留白丢失 */
  box-sizing: border-box;
  width: 100%;
  background: var(--bg-card);
  border-radius: 28rpx;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.04), 0 12rpx 40rpx rgba(0, 0, 0, 0.06);
  transition: transform 0.2s ease, box-shadow 0.2s ease;

  &:active {
    transform: scale(0.98);
    box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.06);
  }
}

/* 主体区：透明承载（背景由 .user-list-item 统一提供） */
.item-main {
  flex: 1;
  display: flex;
  align-items: center;
  min-width: 0;
}

.item-avatar-wrap {
  position: relative;
  flex-shrink: 0;
}

.item-avatar {
  width: 108rpx;
  height: 108rpx;
  border-radius: 50%;
  background: var(--bg-sub);
  border: 4rpx solid var(--bg-icon-soft);
  /* 主色分量走 CSS 变量：硬编码 rgba(254,60,114,.12) 在暗色模式下仍是玫红，与 --color-primary 暗色值不同源 */
  box-shadow: 0 4rpx 16rpx rgba(var(--color-primary-rgb), 0.12);
}

/* 在线状态小绿点 */
.item-online-dot {
  position: absolute;
  right: 4rpx;
  bottom: 4rpx;
  width: 24rpx;
  height: 24rpx;
  border-radius: 50%;
  background: var(--success);
  border: 4rpx solid var(--bg-card);
}

.item-info {
  flex: 1;
  margin-left: 24rpx;
  min-width: 0;
}

.item-name-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.item-nickname {
  font-size: 32rpx;
  font-weight: bold;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-age-badge {
  background: var(--gradient-brand);
  color: var(--text-inverse);
  font-size: 20rpx;
  font-weight: 600;
  padding: 4rpx 14rpx;
  border-radius: 20rpx;
  /* 主色分量走 CSS 变量，同上 */
  box-shadow: 0 2rpx 8rpx rgba(var(--color-primary-rgb), 0.25);
  flex-shrink: 0;
}

.item-meta {
  font-size: 24rpx;
  color: var(--text-hint);
  margin-top: 8rpx;
  display: block;
}

/* 右侧操作区：与左侧头像对称留白（卡片 padding 32rpx 已提供），不再额外加 padding */
.item-action {
  flex-shrink: 0;
  display: flex;
  align-items: center;
}
</style>