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
import { useUnreadStore } from "@/store/unread";

/** 挂载目标容器 id */
const BAR_ID = "xfjy-global-tabbar";

/** AppTabbar 实例暴露面（defineExpose 的 setActive / setUnreadCount） */
type TabbarVm = {
  setActive?: (i: number) => void;
  setUnreadCount?: (count: number) => void;
};

/** 挂载目标 div */
let bar: HTMLElement | null = null;

/**
 * 从挂载容器现场解析 AppTabbar 暴露面（懒解析，不做安装时一次性捕获）。
 * 背景：实测 uni-h5-vue 在 createApp().mount() 返回瞬间，instance.exposed 可能尚未填充，
 * 此时一次性捕获会落到空的 setupState（{}），之后 exposed 就绪也与闭包无关，
 * setActive 静默 no-op 且永不自愈（页面间表现为概率性高亮失效）。
 * 每帧经 DOM 容器现取，本帧取不到就跳过，1.5s 兜底轮询下一帧自愈。
 */
function resolveVm(): TabbarVm | null {
  const host = document.getElementById(BAR_ID) as
    | (HTMLElement & {
        __vue_app__?: {
          _instance?: { exposed?: TabbarVm; setupState?: TabbarVm };
        };
      })
    | null;
  const instance = host?.__vue_app__?._instance;
  // exposed 优先（defineExpose 标准通道），setupState 兜底（兼容 dev 构建）
  return instance?.exposed ?? instance?.setupState ?? null;
}

/** 当前路由（去掉 #/ 前缀与查询串），如 pages/mine/vip */
function currentRoute(): string {
  // hash 路由下 hash 与显示页严格一致，是主真源；
  // getCurrentPages() 栈顶在 navigateBack 过渡期间可能仍是已出栈页，仅作 hash 为空时的兜底。
  const hash = location.hash.replace(/^#/, "");
  const fromHash = hash.split("?")[0].replace(/^\//, "");
  if (fromHash) return fromHash;
  try {
    const pages = getCurrentPages();
    const top = pages[pages.length - 1];
    if (top && top.route) {
      return top.route;
    }
  } catch (_) { /* 非 H5 或不支持时忽略 */ }
  return "";
}

function isExcluded(route: string): boolean {
  return EXCLUDED_PREFIXES.some((p) => route.startsWith(p));
}

/** 二级页高亮归属：返回所属 tab 索引，无归属返回 -1 */
function activeTabIndex(route: string): number {
  return TABS.findIndex((t) => t.prefixes.some((p) => route.startsWith(p)));
}

/**
 * 与原生 tabbar 对齐：把 --window-bottom / --tab-bar-height 写到所有页面 body 上。
 * 值取底栏**真实渲染高度**（含 env(safe-area-inset-bottom)）：
 * 有刘海/底部横条的设备上恒为 TAB_BAR_HEIGHT_PX，会导致所有用
 * calc(100vh - var(--tab-bar-height)) 的页面多算一个安全区高度，底部被遮挡。
 */
function currentTabBarHeight(): string {
  const el = document.getElementById(BAR_ID);
  const barEl = el?.querySelector(".app-tabbar") as HTMLElement | null;
  const h = barEl?.getBoundingClientRect().height;
  if (h && Number.isFinite(h) && h > 0) return `${h}px`;
  // 兜底：内容高度 + 安全区（与 AppTabbar.vue 的 min-height + padding-bottom 等价）
  return `calc(${TAB_BAR_HEIGHT_PX}px + env(safe-area-inset-bottom, 0px))`;
}

function applyWindowBottom(value: string): void {
  document.querySelectorAll("uni-page-body").forEach((el) => {
    (el as HTMLElement).style.setProperty("--window-bottom", value);
    (el as HTMLElement).style.setProperty("--tab-bar-height", value);
  });
}

/** 启动未读数刷新：立即拉一次，之后每 30s 轮询。
 *  H5 单例与页面生命周期同级，无卸载时机，不保留定时器句柄 */
function startUnreadRefresh(): void {
  const unreadStore = useUnreadStore();
  unreadStore.refreshUnread().then(() => {
    resolveVm()?.setUnreadCount?.(unreadStore.unreadCount);
  });
  setInterval(() => {
    unreadStore.refreshUnread().then(() => {
      resolveVm()?.setUnreadCount?.(unreadStore.unreadCount);
    });
  }, 30_000);
}

function refresh(): void {
  if (!bar) return;
  const route = currentRoute();
  // 注入底栏接管所有页面（含 tab 页）：tab 页原生 tabBar 已由 App.vue 隐藏，
  // 避免依赖 uni-app H5 原生 midButton（渲染不可靠）导致加号不显示
  const show = !!route && !isExcluded(route);
  bar.style.display = show ? "flex" : "none";
  // display 变更后立即读取高度可能仍是旧值，下一帧再写变量；
  // 1.5s 兜底轮询会纠正，这里先写一次避免首帧抖动。
  const height = show ? currentTabBarHeight() : "0px";
  applyWindowBottom(height);
  if (show) {
    const active = activeTabIndex(route);
    resolveVm()?.setActive?.(active);
  }
}

/** 兜底轮询句柄：页面切后台时暂停，避免不可见标签页空转 */
let fallbackTimer: ReturnType<typeof setInterval> | null = null;

/** 启动兜底轮询（1.5s）：主路径 hashchange 即时响应，轮询只纠正漏网场景 */
function startFallbackPolling(): void {
  if (fallbackTimer) return;
  fallbackTimer = setInterval(refresh, 1500);
}

/** 在 App.vue onLaunch（H5 条件编译内）调用一次 */
export function installGlobalTabbar(): void {
  if (document.getElementById(BAR_ID)) return;

  bar = document.createElement("div");
  bar.id = BAR_ID;
  document.body.appendChild(bar);

  // 用 createApp 挂载统一 AppTabbar 组件（替代原手写 DOM + 内联 CSS）。
  // 不在 mount 后捕获实例：uni-h5-vue 在 mount 返回瞬间 instance.exposed 可能尚未填充，
  // 一次性捕获会落到空 setupState（{}）且之后永不自愈；改由 resolveVm() 每帧懒解析。
  createApp(AppTabbar).mount(bar);

  // 启动未读数刷新：每 30s 拉一次 IM 会话未读数，更新 tabbar badge
  startUnreadRefresh();

  // 路由变化（hash 路由）即时刷新显示/高亮/占位
  window.addEventListener("hashchange", refresh);
  // 兜底轮询：极少数情况下 hashchange 未触发（直接改 location、框架内部 pushState、
  // 或冷启动首帧页面栈尚未就绪），用轻量轮询保证底栏始终与实际页面一致，
  // 根除“tabbar 莫名消失/不刷新”的问题。间隔由 400ms 放宽到 1.5s：主路径仍是
  // hashchange 即时响应，轮询只做纠正；页面切后台时暂停，消除不可见标签页的空转唤醒。
  startFallbackPolling();
  document.addEventListener("visibilitychange", () => {
    if (document.hidden) {
      if (fallbackTimer) {
        clearInterval(fallbackTimer);
        fallbackTimer = null;
      }
    } else {
      refresh();
      startFallbackPolling();
    }
  });

  refresh();
}
// #endif
