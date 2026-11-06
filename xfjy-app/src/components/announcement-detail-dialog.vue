<template>
  <!-- 公告详情弹窗：列表接口已返回完整正文（description），本地展示无需再请求详情 -->
  <a11y-dialog
    class="announcement-dialog"
    :visible="visible"
    title="公告详情"
    @close="emit('close')"
  >
    <view
      v-if="notice"
      class="announcement-panel"
    >
      <view class="announcement-header">
        <view
          class="announcement-icon"
          aria-hidden="true"
        >
          <view class="i-ph-bell-bold" />
        </view>
        <text class="announcement-title">
          {{ notice.title }}
        </text>
        <text class="announcement-time">
          {{ notice.time }}
        </text>
      </view>
      <scroll-view
        scroll-y
        class="announcement-body"
      >
        <text class="announcement-content">
          {{ notice.content }}
        </text>
      </scroll-view>
      <a11y-button
        variant="primary"
        block
        label="我知道了，关闭公告"
        @tap="emit('close')"
      >
        我知道了
      </a11y-button>
    </view>
  </a11y-dialog>
</template>

<script setup lang="ts">
import A11yDialog from "@/components/a11y-dialog.vue";
import A11yButton from "@/components/a11y-button.vue";

/** 公告详情展示模型（由各页面 NoticeItem 的 nickname/desc/time 映射而来） */
interface AnnouncementNotice {
  title: string;
  content: string;
  time: string;
}

interface Props {
  visible: boolean;
  notice: AnnouncementNotice | null;
}

defineProps<Props>();
const emit = defineEmits<{ (e: "close"): void }>();
</script>

<style lang="scss" scoped>
.announcement-dialog {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 999;
  /* 左右对称留白，弹窗不贴边、不溢出视口 */
  padding: 0 48rpx;
  box-sizing: border-box;

  .announcement-panel {
    width: 100%;
    max-width: 640rpx;
    max-height: 80vh;
    box-sizing: border-box;
    display: flex;
    flex-direction: column;
    padding: 40rpx 32rpx 32rpx;
    background: var(--bg-card);
    border-radius: var(--radius-lg);
  }

  .announcement-header {
    display: flex;
    flex-direction: column;
    align-items: center;
    text-align: center;

    .announcement-icon {
      width: 88rpx;
      height: 88rpx;
      border-radius: 50%;
      background: var(--success);
      display: flex;
      align-items: center;
      justify-content: center;
      margin-bottom: 20rpx;

      .i-ph-bell-bold {
        font-size: 40rpx;
        color: var(--text-inverse);
        line-height: 1;
      }
    }

    .announcement-title {
      font-size: var(--fs-lg);
      font-weight: 700;
      color: var(--text-primary);
      line-height: 1.4;
    }

    .announcement-time {
      margin-top: 12rpx;
      font-size: var(--fs-sm);
      color: var(--text-hint);
    }
  }

  /* 正文超长时只在弹窗内纵向滚动，不撑破视口 */
  .announcement-body {
    max-height: 44vh;
    min-height: 0;
    margin: 24rpx 0 32rpx;
  }

  .announcement-content {
    display: block;
    font-size: var(--fs-base);
    line-height: 1.7;
    color: var(--text-primary);
    word-break: break-word;
  }
}
</style>
