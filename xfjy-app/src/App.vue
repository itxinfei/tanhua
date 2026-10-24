<script setup lang="ts">
import { onLaunch, onShow, onHide } from "@dcloudio/uni-app";

onLaunch(() => {
  console.log("心飞交友 App Launch");
});

onShow(() => {
  console.log("App Show");
});

onHide(() => {
  console.log("App Hide");
});
</script>

<style lang="scss">
/* uview-plus 样式 */
@import "uview-plus/index.scss";

/* 全局公共样式 */
page {
  background-color: #f5f5f5;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto,
    "Helvetica Neue", Arial, sans-serif;
  color: #333333;
  font-size: 28rpx;
}

/* 安全区域 */
.safe-area-bottom {
  padding-bottom: constant(safe-area-inset-bottom);
  padding-bottom: env(safe-area-inset-bottom);
}

/* 单行省略 */
.ellipsis {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

/* 多行省略 */
.ellipsis-2 {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}

/* 主题色 */
.text-primary {
  color: #ff5e8e;
}

.bg-primary {
  background-color: #ff5e8e;
}

/* 渐变遮罩（卡片底部） */
.card-mask {
  background: linear-gradient(
    to top,
    rgba(0, 0, 0, 0.75) 0%,
    rgba(0, 0, 0, 0.3) 50%,
    transparent 100%
  );
}

/* ===== 以下为 UI 细节优化工具类 ===== */

/* 按钮点击反馈：按下缩放 */
.btn-feedback {
  transition: transform 0.15s ease, opacity 0.15s ease;

  &:active {
    transform: scale(0.92);
    opacity: 0.85;
  }
}

/* 毛玻璃效果 */
.glass {
  background: rgba(255, 255, 255, 0.18);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1rpx solid rgba(255, 255, 255, 0.25);
}

/* 渐变文字 */
.gradient-text {
  background: linear-gradient(90deg, #ff5e8e, #ff8fab);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

/* 空状态容器 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80rpx 40rpx;

  .empty-icon {
    width: 160rpx;
    height: 160rpx;
    border-radius: 50%;
    background: #fff0f5;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 64rpx;
    margin-bottom: 24rpx;
  }

  .empty-title {
    font-size: 30rpx;
    font-weight: bold;
    color: #666;
    margin-bottom: 8rpx;
  }

  .empty-desc {
    font-size: 24rpx;
    color: #aaa;
  }
}

/* 脉冲动画（badge 提醒） */
@keyframes pulse {
  0% {
    transform: scale(1);
    opacity: 1;
  }
  50% {
    transform: scale(1.15);
    opacity: 0.8;
  }
  100% {
    transform: scale(1);
    opacity: 1;
  }
}

.pulse {
  animation: pulse 1.5s ease-in-out infinite;
}

/* 弹窗弹出动画 */
@keyframes popIn {
  0% {
    transform: scale(0.6);
    opacity: 0;
  }
  60% {
    transform: scale(1.05);
    opacity: 1;
  }
  100% {
    transform: scale(1);
    opacity: 1;
  }
}

.pop-in {
  animation: popIn 0.35s cubic-bezier(0.34, 1.56, 0.64, 1) both;
}

/* 心形弹跳动画 */
@keyframes heartBeat {
  0% {
    transform: scale(1);
  }
  25% {
    transform: scale(1.3);
  }
  40% {
    transform: scale(1.1);
  }
  60% {
    transform: scale(1.25);
  }
  100% {
    transform: scale(1);
  }
}

.heart-beat {
  animation: heartBeat 0.6s ease-in-out;
}

/* 漂浮呼吸动画 */
@keyframes float {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-20rpx);
  }
}

.float {
  animation: float 3s ease-in-out infinite;
}

/* 双层阴影（卡片质感） */
.shadow-card {
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.06), 0 12rpx 48rpx rgba(0, 0, 0, 0.1);
}

/* 渐变主按钮 */
.btn-gradient {
  background: linear-gradient(90deg, #ff5e8e, #ff8fab);
  color: #fff;
  border: none;
  box-shadow: 0 8rpx 24rpx rgba(255, 94, 142, 0.35);
  transition: transform 0.15s ease, box-shadow 0.15s ease;

  &:active {
    transform: scale(0.96);
    box-shadow: 0 4rpx 12rpx rgba(255, 94, 142, 0.25);
  }
}
</style>