<script setup lang="ts">
import { watch } from "vue";
import { onLaunch, onShow } from "@dcloudio/uni-app";
import { installAuthGuard } from "@/utils/auth-guard";
import { isDevExemptionEnabled } from "@/config/env";
// #ifdef H5
import { installGlobalTabbar } from "@/utils/global-tabbar";
// #endif
import { getToken } from "@/utils/request";
import {
  startNetworkMonitor,
  isOnline,
  showNetworkBar,
  hideNetworkBar,
} from "@/utils/network-monitor";
import { ensureSelfIM, isIMLoggedIn, onMatchReceived } from "@/utils/wukongim";
import { initPush } from "@/utils/push";
import { requestPermission } from "@/utils/notification";
import { useMatchStore } from "@/store/match";
import { resolveAvatar } from "@/utils/avatar";
import { prefetchRecommendVideos } from "@/utils/video-cache";
import { prefetchCircleFeed } from "@/utils/feed-cache";

onLaunch(() => {
  // 安装全局登录路由守卫（需先于任何页面导航）
  installAuthGuard();
  // 启动网络状态监控：使 isOnline / shouldBlockRequest 生效，断网时可拦截请求
  startNetworkMonitor();
  // 断网/恢复时给出全局提示条（NetworkStatusBar 组件未全局挂载，这里用内置 toast 条兜底）
  watch(isOnline, (online) => {
    if (online) hideNetworkBar();
    else showNetworkBar();
  });
  // #ifdef H5
  // 安装 H5 全局底栏：非 tab 页（vip/nearby 等）原生 tabBar 不存在，注入统一底栏
  installGlobalTabbar();
  // #endif
  // 启动兜底：冷启动落在非登录页且无 token 时，重定向到登录页
  // 开发环境 mock：跳过 token 检查，便于本地调试
  const pages = getCurrentPages();
  const current = pages.length ? pages[pages.length - 1].route : "";
  // 开发环境判断：统一走 config/env.ts，支持 VITE_DISABLE_DEV_EXEMPTION=true 生产关闭
  const isDev = isDevExemptionEnabled();
  if (
    !getToken() &&
    current &&
    !current.startsWith("pages/auth/login") &&
    !isDev
  ) {
    uni.reLaunch({ url: "/pages/auth/login" });
  }

  // 全局监听 WuKongIM 匹配消息：互喜时后端透传，任意页面实时弹 "It's a Match"
  const matchStore = useMatchStore();
  onMatchReceived((event) => {
    matchStore.showMatchPopup({
      userId: event.from,
      nickname: event.nickname,
      avatar: resolveAvatar(event.avatar),
    });
  });

  // 视频首屏预取：后端组装 VideoVo 需跨服务查用户信息，线上约 4~5s。
  // 冷启动时后台先拉一次「推荐」首页写缓存，用户稍后点开视频 tab 即可 0 秒出画面。
  // 延迟 1.2s 是为了不与登录态恢复 / IM 初始化抢网络，且失败完全静默。
  setTimeout(() => {
    if (getToken()) prefetchRecommendVideos().catch(() => {});
  }, 1200);
  // 圈子动态 /movements 同样有 3~11s 波动，稍晚错峰预取，避免与视频预取抢连接
  setTimeout(() => {
    if (getToken()) prefetchCircleFeed().catch(() => {});
  }, 2200);
});

onShow(() => {
  if (!getToken()) return;
  // 上报设备推送标识（APP-PLUS 端；未集成极光原生插件时静默跳过，幂等）
  initPush();
  // 请求本地通知权限（H5 Web Notification / MP 订阅消息）
  // 仅首次或权限未定时请求，已授权/已拒绝不再弹
  requestPermission().catch(() => {});
  // WuKongIM 登录（幂等）：凭证优先由服务端签发（GET /messages/im/credential），
  // 不再在前端按 userId 派生可预测 token，避免顶号/冒充风险
  if (isIMLoggedIn()) return;
  ensureSelfIM().catch(() => {
    // 登录失败静默降级：聊天功能在会话页级逻辑兜底
  });
});

</script>

<style lang="scss">
/* ===== CSS 变量（品牌色：暖玫红 + 珊瑚系，与后台管理 xfjy-admin 对齐；紫色保留为辅助色；深色模式 WIP：变量层已就绪，页面硬编码色待逐步迁移到变量引用） ===== */
page {
  --color-primary: #fe3c72;
  --color-coral: #ff655b;
  --color-primary-deep: #b13bff;
  --color-primary-violet: #8a5cf6;
  /* VIP 权益专属紫（探探式 boost 按钮规范色，需求文档 B2.1「⚡曝光提升(紫)」）；
     与历史紫 --color-primary-deep/violet 不同源，独立 token 便于暗色适配与后续调整 */
  --color-vip-purple: #8a5cf6;
  --color-vip-purple-deep: #b13bff;
  --color-vip-purple-rgb: 138, 92, 246;
  --color-primary-soft: #ff8fb1;
  --color-primary-mist: #ffd1e8;
  --color-primary-text: #c2185b;
  --bg-sub: #f5f5f5;
  --border-light: #f0f0f0;
  --border: #e0e0e0;
  --border-faint: #f5f5f5;
  --text-hint-weak: #999999;
  --gradient-brand: linear-gradient(135deg, #fe3c72, #ff655b);
  --gradient-brand-h: linear-gradient(90deg, #fe3c72, #ff655b);
  --bg-page: #f5f5f5;
  --bg-card: #ffffff;
  --bg-icon-soft: #ffeff5;
  --text-primary: #333333;
  --text-secondary: #666666;
  /* 无障碍：辅助文字色由 #999（≈2.85:1）提深至 #767676（≈4.54:1），满足 WCAG 1.4.3 */
  --text-hint: #767676;
  --text-hint-strong: #595959;
  --border-color: #f0f0f0;

  /* 危险操作色（删除/注销/举报等危险按钮） */
  --color-danger: #e5484d;
  --color-danger-rgb: 229, 72, 77;
  /* 成功态（认证/操作成功反馈） */
  --success: #4cd964;
  --success-rgb: 76, 217, 100;
  /* 信息蓝（性别男选中、超级喜欢星等） */
  --info-blue: #4a90d9;
  --info-blue-rgb: 74, 144, 217;
  --info-blue-soft: #6bb0f5;
  --info-blue-soft-rgb: 107, 176, 245;
  /* 状态徽章浅底（与 --success/--color-danger 配对，避免写死 hex 在暗色下成刺眼色块） */
  --success-tint: #f0fff4;
  --success-text: #1a7f37;
  --warning-tint: #fff7e6;
  --warning-text: #b35c00;
  /* 危险色浅底：输入框错误态等场景（亮底浅红） */
  --color-danger-tint: #fdebec;
  /* VIP 金（会员皇冠/标签/渐变） */
  --gold: #ffd700;
  --gold-rgb: 255, 215, 0;
  --gold-soft: #ffb347;
  --gold-soft-rgb: 255, 179, 71;
  --gold-light: #fff5c0;
  /* 深橙金：VIP 支付按钮渐变收尾停点（VIP 金色系，亮暗同值，随 --gold 不进暗色块重声明） */
  --gold-deep: #ff9a3c;
  /* VIP 页固定深色体系（有意恒暗，亮/暗模式同值，不随主题变） */
  --bg-deep: #1a1a2e;
  --bg-deep-grad: #2c2c54;
  /* 个人页 VIP 卡暖棕黑渐变停点（有意恒暗，亮/暗模式同值） */
  --vip-card-grad-1: #2c2c2c;
  --vip-card-grad-2: #4a3b2a;
  /* 深色品牌按钮上的反色文字（恒白，不随暗色模式变） */
  --text-inverse: #ffffff;
  /* 主色 RGB 分量：供 rgba()/color-mix() 使用（box-shadow / drop-shadow 光晕）。
     不能直接用 var(--color-primary) 拼透明度——hex 无法分解出分量。
     真源约束：本变量必须与上方 --color-primary 同色，改主色必须同步改这里。
     历史问题：多处 box-shadow 写死 rgba(255,94,156/142,…) 是废弃冷粉 #ff5e9c 的分量，
     与现主色 #fe3c72 不同源，导致「玫红按钮 + 冷粉光晕」的视觉割裂。 */
  --color-primary-rgb: 254, 60, 114;
  /* 主按钮投影（跟随主色，暗色模式下由下方 media 覆盖为暗色主色的分量） */
  --shadow-primary: 0 8rpx 24rpx rgba(var(--color-primary-rgb), 0.35);
  /* 中性卡片阴影：卡片/气泡轻投影；--shadow-float 用于贴层浮起元素（更多面板图标等）。
     暗色模式下黑色阴影需加深才可见，由下方 media 覆盖 */
  --shadow-card: 0 2rpx 12rpx rgba(0, 0, 0, 0.06);
  --shadow-float: 0 6rpx 16rpx rgba(0, 0, 0, 0.08);

  /* ===== 视频沉浸页专属 token（2026-11-06 用户裁决：抖音基线对齐 + 视觉冻结）=====
     播放页是永恒深色沉浸环境，强调色不随系统主题翻转；亮/暗模式同值，禁止用于视频页以外场景。
     基线出处：6d4497b（抖音对标版），冻结声明见 pages/video/index.vue 样式段顶部。 */
  --video-accent: #fe2c55;
  /* 同色 RGB 分量，供 rgba() 拼投影/光晕（与 --color-primary-rgb 同范式） */
  --video-accent-rgb: 254, 44, 85;
  /* 播放页纯黑底（抖音原版 .video-page 为 #000；原版唱片 #111 收敛到此值，靠白边勾勒） */
  --video-bg: #000000;

  /* ===== 间距刻度（全站 margin/padding/gap 只能取以下档位；存量 12/20/36/40 等就近归并） ===== */
  --space-xs: 8rpx;
  --space-sm: 16rpx;
  --space-md: 24rpx;
  --space-lg: 32rpx;
  --space-xl: 48rpx;
  --space-2xl: 64rpx;

  /* ===== 字号刻度（基准 28rpx，与 page font-size 对齐） ===== */
  --fs-xs: 22rpx;
  --fs-sm: 24rpx;
  --fs-base: 28rpx;
  --fs-md: 30rpx;
  --fs-lg: 32rpx;
  --fs-xl: 36rpx;
  --fs-2xl: 44rpx;

  /* ===== 圆角刻度（输入框 md / 卡片 lg / 弹窗 xl / 胶囊 pill） ===== */
  --radius-sm: 8rpx;
  --radius-md: 16rpx;
  --radius-lg: 24rpx;
  --radius-xl: 32rpx;
  --radius-pill: 999rpx;

  /* ===== 分类条（tab / 分段条）统一高度：88rpx = 44px，4px 栅格 =====
     真源唯一：friend 统计条 / 圈子分区条 / 圈子频道条 / 视频频道条共用；
     新增分类条必须引用本变量，禁止各页再写死高度（下划线靠父级高度统一后 bottom: 0 对齐）。 */
  --category-bar-h: 88rpx;

  /* ===== 行高 / 热区真源（全站列表行、菜单行、可点击行的唯一取值处） =====
     背景：rpx 是相对单位（750rpx = 视口宽），窄屏会等比缩小——320px 视口下
     88rpx 仅 37.5px，低于 WCAG 2.5.5 的 44px 物理下限。因此「保底」档一律写成
     max(88rpx, 44px)：宽屏维持 rpx 比例，窄屏兜住物理热区。
     新增可点击行高必须引用这里，禁止各页面再写死像素值。

     「视觉行高」与「热区行高」分开两档，是本项目的关键设计约束：
     内容密度由 --row-h-visual-* 决定（看起来多紧凑），点击区域由 --row-h-touch-*
     决定（点起来多轻松）。二者独立取值后，「视觉紧凑 + 热区达标」不再互相牵制——
     min-height 撑出来的部分不承载任何视觉元素，所以看得见的小、摸得着的大。 */
  /** 紧凑行热区：次级操作行/卡片内密集列表（≥44px 物理下限） */
  --row-h-touch-sm: max(88rpx, 44px);
  /** 标准行热区：设置项、菜单项、会话行等主列表（≥48px 物理下限） */
  --row-h-touch-md: max(96rpx, 48px);
  /** 宽松行热区：带头像/多行副标题的富列表行（≥64px 物理下限） */
  --row-h-touch-lg: max(128rpx, 64px);

  /** 紧凑行视觉高：密集列表的内容高度（配 --row-h-touch-sm 使用） */
  --row-h-visual-sm: 72rpx;
  /** 标准行视觉高：菜单项/设置项的内容高度（配 --row-h-touch-md 使用） */
  --row-h-visual-md: 80rpx;
  /** 宽松行视觉高：富列表行的内容高度（配 --row-h-touch-lg 使用） */
  --row-h-visual-lg: 104rpx;

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
    /* 与上方暗色主色 #ff8eb9 同源（RGB 分量必须同步） */
    --color-primary-rgb: 255, 142, 185;
    --color-primary-deep: #9b5cf6;
    --color-primary-violet: #a78bfa;
    /* VIP 权益专属紫暗色适配（需求文档 B2.1，暗色下提亮保持对比度） */
    --color-vip-purple: #a78bfa;
    --color-vip-purple-deep: #9b5cf6;
    --color-vip-purple-rgb: 167, 139, 250;
    --color-primary-soft: #ff8fb1;
    --color-primary-mist: #7a5a70;
    --color-primary-text: #ff8eb9;
    --bg-sub: #262626;
    --border-light: #3a3a3a;
    --border: #3f3f3f;
    --border-faint: #333333;
    --text-hint-weak: #8c8c8c;
    --gradient-brand: linear-gradient(135deg, #f9578f, #ff7a6b);
    --gradient-brand-h: linear-gradient(90deg, #f9578f, #ff7a6b);
    --bg-page: #1a1a1a;
    --bg-card: #2c2c2c;
    --bg-icon-soft: #3a2a30;
    --text-primary: #e8e8e8;
    --text-secondary: #aaaaaa;
    --text-hint: #8c8c8c;
    --text-hint-strong: #a6a6a6;
    --border-color: #3a3a3a;
    /* 状态徽章暗色配对：浅底改为深底、深字改为亮字，保持对比度 */
    --success-tint: #1e3a24;
    --success-text: #6ee787;
    --warning-tint: #3a2a12;
    --warning-text: #ffb86b;
    /* 危险色浅底暗色配对：深底浅红（与暗色危险色 #ff6b6b 同源） */
    --color-danger-tint: #3a1a1a;
    /* 暗色下危险色提亮一档，保证暗底对比度 */
    --color-danger: #ff6b6b;
    /* 与上方暗色危险色 #ff6b6b 同源（RGB 分量必须同步） */
    --color-danger-rgb: 255, 107, 107;
    --shadow-primary: 0 8rpx 24rpx rgba(249, 87, 143, 0.3);
    /* 暗色下卡片与浮层阴影加深，保证在 #1a1a1a 底上仍有层次 */
    --shadow-card: 0 2rpx 12rpx rgba(0, 0, 0, 0.4);
    --shadow-float: 0 6rpx 16rpx rgba(0, 0, 0, 0.5);
    /* VIP 卡渐变有意恒暗，暗色下与亮色同值显式声明 */
    --vip-card-grad-1: #2c2c2c;
    --vip-card-grad-2: #4a3b2a;
    /* 以下功能色亮/暗同值，RGB 分量与上方亮色段保持同源 */
    --success-rgb: 76, 217, 100;
    --info-blue-rgb: 74, 144, 217;
    --info-blue-soft-rgb: 107, 176, 245;
    --gold-rgb: 255, 215, 0;
    --gold-soft-rgb: 255, 179, 71;
    --skel-bg: var(--skel-bg-dark);
    /* 视频沉浸页 token 恒暗锁定：与亮色段同值显式声明，防止暗色主题调整误伤播放页（2026-11-06 裁决） */
    --video-accent: #fe2c55;
    --video-accent-rgb: 254, 44, 85;
    --video-bg: #000000;
  }
}

/* scroll-view 统一 border-box：uni-app 默认 content-box，宽度 100% 时左右 padding
   会额外加到宽度上，导致容器被撑破、页面横向滚动（历史 bug：圈子 feed-list 在 390
   视口实测 407px；chat/kefu 的 msg-list、video-comment-drawer 的 comment-list 等
   同样中招）。scroll-view 的 padding 计入宽度才是正确语义，全站统一。 */
scroll-view {
  box-sizing: border-box;
}

/* 响应系统「减弱动态效果」（WCAG 2.3.3，Reduced Motion 高优先级）：
   H5/APP-webview 识别后把动画/过渡压到近无；微信小程序 WXSS 不支持该媒体查询，静默忽略。
   显式列举标签而非 * 通配（小程序 WXSS 不支持通配选择器）；
   !important 是 reduced-motion 全局兜底的标准范式，用于压过页面级 animation 简写。 */
@media (prefers-reduced-motion: reduce) {
  view,
  text,
  scroll-view,
  image,
  button,
  input,
  navigator {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
  }
}

/* 安全区域 */
.safe-area-bottom {
  padding-bottom: constant(safe-area-inset-bottom);
  padding-bottom: env(safe-area-inset-bottom, 0px);
}

.safe-area-top {
  padding-top: constant(safe-area-inset-top);
  padding-top: env(safe-area-inset-top, 0px);
}

/* #ifdef H5 */
/* ===== 底部 tabBar（仅 H5；小程序端为原生 tabBar，由 theme.json 控制） =====
   统一视觉规格（AppTabbar.vue 单一维护，与 config/tab-config.ts 常量对齐）：
   实色白底 · 图标 28px · 文字 10px · 顶部 1px 浅灰分割线 · 抖音式中央发布键 */

/* 隐藏原生 tabBar：注入底栏（global-tabbar.ts 挂载 AppTabbar）接管所有页面（含 tab 页），避免双栏。
   原生 H5 midButton 渲染不可靠，统一由注入底栏提供发布加号 */
.uni-tabbar,
.uni-tabbar-border {
  display: none !important;
}
/* #endif */

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
  color: var(--color-primary);
}

.bg-primary {
  background-color: var(--color-primary);
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

/* 按压反馈（行级/卡片级元素的真源）
   设计取舍：
   1) 行级元素（菜单行、卡片、会话行）不做 scale——缩放会连带缩放边框/分隔线/圆角，
      整行会明显「跳」，反而显得廉价。改用「背景压暗」，边界保持稳定，
      只传递「被按下」的信号，同时给内容区增加对比。
   2) 按钮级元素（胶囊/方块）的反馈由 a11y-button 组件自身提供（scale + 透明度），
      此处不重复声明，避免两条规则互相覆盖导致幅度失控。
   3) 时长 120ms 起步、150ms 回弹，落在「瞬时但有感知」区间
      （<100ms 感知不到，>300ms 显得拖沓）。统一 ease-out，回弹更干净。
   4) 反馈只依赖 :active，松手即回弹，不需要 JS 参与，也不会残留状态。 */
.btn-feedback {
  transition:
    opacity 0.12s ease-out,
    background-color 0.12s ease-out;

  &:active {
    opacity: 0.72;
    background-color: var(--border-faint);
  }

  /* 若行级元素同时是 a11y-button，交给组件自身的 scale 反馈，
     这里只补背景压暗，不再叠加缩放 */
  &.a11y-btn:active {
    background-color: var(--border-faint);
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

/* VIP 金色渐变文字：全局唯一实现，页面勿再各写一份渐变裁切；
   金色只走 --gold 系 token，与徽章（components/vip-badge.vue）、
   头像环（components/vip-ring.vue）共同构成 VIP 视觉的唯一入口 */
.vip-gradient-text {
  background: linear-gradient(90deg, var(--gold), var(--gold-light), var(--gold));
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  /* 小程序/旧内核不支持 text-fill-color 时兜底 */
  color: transparent;
}

/* 无障碍：-webkit-text-fill-color: transparent 在强制颜色模式（高对比度）下会让文字彻底消失，
   此处回退为系统色，保证 1.4.3 / 1.4.8 在强制颜色模式下仍可读 */
@media (forced-colors: active) {
  .gradient-text,
  .vip-gradient-text {
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

/* ===== 入场动画工具类（页面内容进场的唯一真源，页面禁止自行复制 keyframes） =====
   用法：class="anim-fade-up anim-delay-2"（delay 工具类必须写在本组类之后） */
@keyframes animFadeUp {
  from {
    opacity: 0;
    transform: translateY(32rpx);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes animFadeDown {
  from {
    opacity: 0;
    transform: translateY(-24rpx);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.anim-fade-up {
  animation: animFadeUp 0.5s cubic-bezier(0.22, 1, 0.36, 1) both;
}

.anim-fade-down {
  animation: animFadeDown 0.45s cubic-bezier(0.22, 1, 0.36, 1) both;
}

/* 入场错峰档位：0.1s 步进，最多 5 档 */
.anim-delay-1 { animation-delay: 0.1s; }
.anim-delay-2 { animation-delay: 0.18s; }
.anim-delay-3 { animation-delay: 0.26s; }
.anim-delay-4 { animation-delay: 0.34s; }
.anim-delay-5 { animation-delay: 0.42s; }

/* 遮罩淡入（配合 anim-slide-up 组成底部弹层入场） */
@keyframes animFadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.anim-fade-in {
  animation: animFadeIn 0.25s ease-out both;
}

/* 底部弹层滑入（bottom sheet 专用，与 anim-fade-in 遮罩配套） */
@keyframes animSlideUp {
  from { transform: translateY(100%); }
  to { transform: translateY(0); }
}

.anim-slide-up {
  animation: animSlideUp 0.3s cubic-bezier(0.22, 1, 0.36, 1) both;
}

/* 双层阴影（卡片质感） */
.shadow-card {
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.06), 0 12rpx 48rpx rgba(0, 0, 0, 0.1);
}

/* 渐变主按钮 */
.btn-gradient {
  background: linear-gradient(90deg, var(--color-primary-deep), var(--color-primary));
  color: var(--text-inverse);
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

/* ===== 页面浮层/加载动画公共关键帧（新代码优先复用，勿再新增同义副本） =====
   slideUp / spin / fadeIn / shimmer 收口在此，页面直接 animation: slideUp ... 即可。
   scoped 样式会给本地同名 keyframes 加 hash（如 shimmer-784bec3b），故页面内
   已有的同名定义不会互相覆盖，但不应再复制新的。 */
@keyframes slideUp {
  from {
    transform: translateY(100%);
  }
  to {
    transform: translateY(0);
  }
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(-8rpx);
  }
  to {
    opacity: 1;
    transform: translateY(0);
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
  .btn-gradient,
  .anim-fade-up,
  .anim-fade-down,
  .anim-fade-in,
  .anim-slide-up,
  .vip-badge--pill,
  .vip-ring-spin {
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
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}

</style>