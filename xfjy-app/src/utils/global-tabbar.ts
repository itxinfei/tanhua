/**
 * H5 全局底部 tabbar（仅 H5 端生效）。
 *
 * 背景：uni-app 的原生 tabBar 只在 pages.json tabBar.list 的 4 个 tab 页渲染，
 * navigateTo 进入的二级页（vip / nearby / chat 等）天生没有底栏，逐页补丁必然遗漏。
 * 本模块在应用启动时注入一个与原生 tabbar 视觉一致的固定底栏：
 *   - tab 页：显示注入底栏（接管所有页面，原生 tabBar 由 App.vue 隐藏避免双栏）
 *   - 非 tab 页：显示，并按路径前缀高亮所属 tab（如 /pages/mine/vip → 高亮「我的」）
 *   - 登录等全屏页（/pages/auth/*）：不显示
 *
 * 实现：用 createApp(AppTabbar).mount(bar) 挂载统一 AppTabbar 组件，
 * 替代原手写 DOM + 内联 CSS 字符串（视觉规格统一在 AppTabbar.vue 一处维护）。
 *
 * 规则文档：docs/H5-tabbar机制与全局底栏.md
 */
// #ifdef H5
import { createApp } from "vue";
import AppTabbar from "@/components/AppTabbar.vue";
import {
  TABS,
  EXCLUDED_PREFIXES,
  TAB_BAR_HEIGHT_PX,
} from "@/config/tab-config";

/** 挂载目标容器 id */
const BAR_ID = "xfjy-global-tabbar";

/** AppTabbar 实例（通过 defineExpose 暴露 setActive） */
let vm: { setActive?: (i: number) => void } | null = null;
/** 挂载目标 div */
let bar: HTMLElement | null = null;

/** 当前路由（去掉 #/ 前缀与查询串），如 pages/mine/vip */
function currentRoute(): string {
  // 优先取 uni-app 运行时页面栈栈顶（navigateTo/switchTab/reLaunch 之后以它为权威来源），
  // 避免仅依赖 hashchange 在路由已变、事件未触发时底栏“卡在隐藏态”导致二级页 tabbar 消失
  try {
    const pages = getCurrentPages();
    const top = pages[pages.length - 1];
    if (top && top.route) {
      return top.route;
    }
  } catch (_) { /* 非 H5 或不支持时忽略，走 hash 兜底 */ }
  const hash = location.hash.replace(/^#/, "");
  return hash.split("?")[0].replace(/^\//, "");
}

function isExcluded(route: string): boolean {
  return EXCLUDED_PREFIXES.some((p) => route.startsWith(p));
}

/** 二级页高亮归属：返回所属 tab 索引，无归属返回 -1 */
function activeTabIndex(route: string): number {
  return TABS.findIndex((t) => t.prefixes.some((p) => route.startsWith(p)));
}

/** 与原生 tabbar 对齐：把 --window-bottom / --tab-bar-height 写到所有页面 body 上 */
function applyWindowBottom(value: string): void {
  document.querySelectorAll("uni-page-body").forEach((el) => {
    (el as HTMLElement).style.setProperty("--window-bottom", value);
    (el as HTMLElement).style.setProperty("--tab-bar-height", value);
  });
}

function refresh(): void {
  if (!bar) return;
  const route = currentRoute();
  // 注入底栏接管所有页面（含 tab 页）：tab 页原生 tabBar 已由 App.vue 隐藏，
  // 避免依赖 uni-app H5 原生 midButton（渲染不可靠）导致加号不显示
  const show = !!route && !isExcluded(route);
  bar.style.display = show ? "flex" : "none";
  applyWindowBottom(show ? `${TAB_BAR_HEIGHT_PX}px` : "0px");
  if (show && vm) {
    const active = activeTabIndex(route);
    vm.setActive?.(active);
  }
}

/** 在 App.vue onLaunch（H5 条件编译内）调用一次 */
export function installGlobalTabbar(): void {
  if (document.getElementById(BAR_ID)) return;

  bar = document.createElement("div");
  bar.id = BAR_ID;
  document.body.appendChild(bar);

  // 用 createApp 挂载统一 AppTabbar 组件（替代原手写 DOM + 内联 CSS）
  const app = createApp(AppTabbar);
  vm = app.mount(bar) as unknown as { setActive?: (i: number) => void };

  // 路由变化（hash 路由）与兜底轮询刷新显示/高亮/占位
  window.addEventListener("hashchange", refresh);
  // 兜底轮询：极少数情况下 hashchange 未触发（直接改 location、框架内部 pushState、
  // 或冷启动首帧页面栈尚未就绪），用轻量轮询保证底栏始终与实际页面一致，
  // 根除“tabbar 莫名消失/不刷新”的问题。refresh 仅切换 display/调用 setActive，开销极小。
  setInterval(refresh, 400);

  refresh();
}
// #endif
