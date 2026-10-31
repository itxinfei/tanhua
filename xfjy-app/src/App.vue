<script setup lang="ts">
import { onLaunch, onShow } from "@dcloudio/uni-app";
import { installAuthGuard } from "@/utils/auth-guard";
import { getToken } from "@/utils/request";
import { getHuanXinUser } from "@/api/chat";
import { loginEasemob, isEasemobLoggedIn, onMatchReceived } from "@/utils/easemob";
import { useMatchStore } from "@/store/match";

onLaunch(() => {
  // 安装全局登录路由守卫（需先于任何页面导航）
  installAuthGuard();
  // 启动兜底：冷启动落在非登录页且无 token 时，重定向到登录页
  const pages = getCurrentPages();
  const current = pages.length ? pages[pages.length - 1].route : "";
  if (!getToken() && current && !current.startsWith("pages/auth/login")) {
    uni.reLaunch({ url: "/pages/auth/login" });
  }

  // 全局监听环信匹配消息：互喜时后端透传，任意页面实时弹 "It's a Match"
  const matchStore = useMatchStore();
  onMatchReceived((event) => {
    matchStore.showMatchPopup({
      userId: event.from,
      nickname: event.nickname,
      avatar: event.avatar || "/static/logo/logo-icon-transparent.png",
    });
  });
});

onShow(() => {
  // 全局环信登录（幂等）：保证任意页面都能实时收到匹配消息
  if (!getToken() || isEasemobLoggedIn()) return;
  getHuanXinUser()
    .then((credential) => {
      if (credential.username && credential.token) {
        return loginEasemob(credential.username, credential.token);
      }
      return false;
    })
    .catch(() => {
      // 登录失败静默降级：聊天相关功能按原有页面级逻辑兜底
    });
});

// 原生 tabBar 居中发布键（抖音式发布入口）：跳转发布动态页
uni.onTabBarMidButtonTap(() => {
  uni.navigateTo({ url: "/pages/circle/publish" });
});
</script>

<style lang="scss">
/* uview-plus 样式 */
@use "uview-plus/index.scss" as *;

/* ===== CSS 变量（品牌色：玫红 + 紫系，与后台管理 xfjy-admin 对齐；深色模式 WIP：变量层已就绪，页面硬编码色待逐步迁移到变量引用） ===== */
page {
  --color-primary: #ff5e9c;
  --color-primary-deep: #b13bff;
  --color-primary-violet: #8a5cf6;
  --color-primary-soft: #ff8fb1;
  --color-primary-mist: #ffd1e8;
  --bg-page: #f5f5f5;
  --bg-card: #ffffff;
  --bg-icon-soft: #ffeff5;
  --text-primary: #333333;
  --text-secondary: #666666;
  /* 无障碍：辅助文字色由 #999（≈2.85:1）提深至 #767676（≈4.54:1），满足 WCAG 1.4.3 */
  --text-hint: #767676;
  --text-hint-strong: #595959;
  --border-color: #f0f0f0;

  /* 骨架屏：统一占位块样式令牌（灰底闪光 + 暗色变体），全站复用 */
  --skel-bg: linear-gradient(90deg, #efefef 25%, #f5f5f5 50%, #efefef 75%);
  --skel-bg-dark: linear-gradient(90deg, #1a1a1a 25%, #2a2a2a 50%, #1a1a1a 75%);
  --skel-radius: 12rpx;

  background-color: var(--bg-page);
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto,
    "Helvetica Neue", Arial, sans-serif;
  color: var(--text-primary);
  font-size: 28rpx;
}

@media (prefers-color-scheme: dark) {
  page {
    --color-primary: #ff8eb9;
    --color-primary-deep: #9b5cf6;
    --color-primary-violet: #a78bfa;
    --color-primary-soft: #ff8fb1;
    --color-primary-mist: #7a5a70;
    --bg-page: #1a1a1a;
    --bg-card: #2c2c2c;
    --bg-icon-soft: #3a2a30;
    --text-primary: #e8e8e8;
    --text-secondary: #aaaaaa;
    --text-hint: #8c8c8c;
    --text-hint-strong: #a6a6a6;
    --border-color: #3a3a3a;
    --skel-bg: var(--skel-bg-dark);
  }
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
  color: #ff5e9c;
}

.bg-primary {
  background-color: #ff5e9c;
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
  background: linear-gradient(90deg, var(--color-primary-deep), var(--color-primary));
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

/* 无障碍：-webkit-text-fill-color: transparent 在强制颜色模式（高对比度）下会让文字彻底消失，
   此处回退为系统色，保证 1.4.3 / 1.4.8 在强制颜色模式下仍可读 */
@media (forced-colors: active) {
  .gradient-text {
    -webkit-text-fill-color: CanvasText;
    color: CanvasText;
    background: none;
  }

  .glass {
    background: Canvas;
    border-color: CanvasText;
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
  background: linear-gradient(90deg, var(--color-primary-deep), var(--color-primary));
  color: #fff;
  border: none;
  box-shadow: 0 8rpx 24rpx rgba(177, 59, 255, 0.35);
  transition: transform 0.15s ease, box-shadow 0.15s ease;

  &:active {
    transform: scale(0.96);
    box-shadow: 0 4rpx 12rpx rgba(177, 59, 255, 0.25);
  }
}

/* ===== 骨架屏工具类 ===== */

/* 闪光扫描动画 */
@keyframes shimmer {
  0% {
    background-position: -400rpx 0;
  }
  100% {
    background-position: 400rpx 0;
  }
}

/* 骨架占位块（统一灰底闪光，令牌化以支持暗色/系统主题） */
.skeleton {
  background: var(--skel-bg);
  background-size: 800rpx 100%;
  animation: shimmer 1.4s ease-in-out infinite;
  border-radius: var(--skel-radius);
}

/* 暗色背景占位（如视频页）：始终使用暗色渐变，不随系统主题切换 */
.skeleton--dark {
  background-image: var(--skel-bg-dark);
}

/* ===== 无障碍：尊重系统「减弱动态效果」偏好（WCAG 2.3.3 / 2.2.2） =====
   前庭功能障碍用户会因无限循环动画诱发眩晕，此处统一关闭所有装饰性动效 */
@media (prefers-reduced-motion: reduce) {
  .pulse,
  .pop-in,
  .heart-beat,
  .float,
  .skeleton,
  .btn-feedback,
  .btn-gradient {
    animation: none !important;
    transition: none !important;
  }

  /* 页面级无限动画（如视频页跑马灯 music-text / 旋转唱片 / 加载 spinner）同样降级 */
  .marquee,
  .music-disc,
  .status-loading {
    animation: none !important;
  }
}

/* ===== 无障碍：键盘焦点可见（WCAG 2.4.7） =====
   A11yButton / 原生控件统一走此处的焦点环，保证键盘用户能定位当前位置 */
.a11y-btn:focus-visible,
switch:focus-visible,
input:focus-visible,
textarea:focus-visible,
button:focus-visible {
  outline: 2px solid #ff5e9c;
  outline-offset: 2px;
}
</style>