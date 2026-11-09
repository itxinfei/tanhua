<template>
  <!-- 匹配成功弹窗 -->
  <a11y-dialog
    class="match-popup"
    :visible="!!matchStore.matchPopup"
    title="匹配成功"
    @close="matchStore.closeMatchPopup()"
  >
    <view class="match-content pop-in">
      <text class="match-title gradient-text">
        配对成功！
      </text>
      <view class="match-avatars">
        <smart-image
          lazy-load
          class="match-avatar"
          :src="matchStore.matchPopup.avatar"
          mode="aspectFill"
          :aria-label="`${matchStore.matchPopup.nickname} 的头像`"
        />
        <view
          class="match-heart heart-beat"
          aria-hidden="true"
        >
          <svg
            viewBox="0 0 24 24"
            width="28"
            height="28"
            aria-hidden="true"
          ><path
            d="M20.84 4.61a5.5 5.5 0 00-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 00-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 000-7.78z"
            fill="var(--color-primary)"
          /></svg>
        </view>
        <smart-image
          lazy-load
          class="match-avatar"
          src="/static/logo/logo-icon-transparent.png"
          mode="aspectFill"
          aria-label="我的头像"
        />
      </view>
      <text class="match-name">
        你和 {{ matchStore.matchPopup.nickname }} 互相喜欢了
      </text>
      <view class="match-actions">
        <a11y-button
          variant="primary"
          label="发消息"
          @tap.stop="goChat"
        >
          <text>发消息</text>
        </a11y-button>
        <a11y-button
          class="keep-swipe"
          variant="ghost"
          label="继续逛逛"
          @tap.stop="matchStore.closeMatchPopup()"
        >
          <text>继续逛逛</text>
        </a11y-button>
      </view>
    </view>
  </a11y-dialog>
</template>

<script setup lang="ts">
import { useMatchStore } from "@/store/match";
import A11yDialog from "@/components/a11y-dialog.vue";
import A11yButton from "@/components/a11y-button.vue";
import SmartImage from "@/components/smart-image.vue";

const matchStore = useMatchStore();

/** 跳转与匹配对象的聊天页 */
function goChat(): void {
  const userId = matchStore.matchPopup?.userId;
  if (userId === undefined) return;
  matchStore.closeMatchPopup();
  uni.navigateTo({ url: `/pages/chat/chat?userId=${userId}` });
}
</script>

<style lang="scss" scoped>
/* 遮罩/定位已收进 a11y-dialog 组件；此处仅保留本弹窗差异（更深的遮罩） */
.match-popup {
  background: rgba(0, 0, 0, 0.8);

  .match-content {
    display: flex;
    flex-direction: column;
    align-items: center;

    .match-title {
      font-size: 64rpx;
      font-weight: 900;
      /* 粉橙渐变（--gradient-brand-h）有意区别于全局 .gradient-text 的紫粉品牌渐变；
         模板同时挂全局类以复用其 forced-colors 兜底，但 scoped 特异性更高，
         故高对比度兜底必须在本文件底部同媒体查询再覆盖一次 */
      background: var(--gradient-brand-h);
      -webkit-background-clip: text;
      background-clip: text;
      -webkit-text-fill-color: transparent;
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
        border: 6rpx solid var(--color-primary);
      }

      .match-heart {
        display: flex;
        align-items: center;
        justify-content: center;
      }
    }

    .match-name {
      font-size: 30rpx;
      color: var(--text-inverse);
      margin-bottom: 48rpx;
    }

    .match-actions {
      display: flex;
      gap: 32rpx;

      /* ghost variant 提供热区/按压反馈/字体，此处只覆写为白色系（深色弹窗背景） */
      .keep-swipe {
        color: var(--text-inverse);
        border-color: rgba(255, 255, 255, 0.5);
      }
    }
  }
}

/* 强制颜色模式（高对比度）：scoped .match-title 特异性高于全局 .gradient-text 兜底，
   不在本组件覆盖则透明文字 + 残留背景会让标题不可见（WCAG 1.4.8） */
@media (forced-colors: active) {
  .match-title {
    -webkit-text-fill-color: CanvasText;
    color: CanvasText;
    background: none;
  }
}
</style>
