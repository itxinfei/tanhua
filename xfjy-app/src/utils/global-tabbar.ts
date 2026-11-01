/**
 * H5 全局底部 tabbar（仅 H5 端生效）。
 *
 * 背景：uni-app 的原生 tabBar 只在 pages.json tabBar.list 的 4 个 tab 页渲染，
 * navigateTo 进入的二级页（vip / nearby / chat 等）天生没有底栏，逐页补丁必然遗漏。
 * 本模块在应用启动时注入一个与原生 tabbar 视觉一致的固定底栏：
 *   - tab 页：隐藏自身（原生 tabbar 已存在，避免双栏）
 *   - 非 tab 页：显示，并按路径前缀高亮所属 tab（如 /pages/mine/vip → 高亮「我的」）
 *   - 登录等全屏页（/pages/auth/*）：不显示
 *
 * 规则文档：docs/H5-tabbar机制与全局底栏.md
 */
// #ifdef H5
interface TabDef {
  /** 页面路径（pages.json 中的 pagePath） */
  pagePath: string;
  /** 底栏文字 */
  text: string;
  /** 未选中图标 */
  icon: string;
  /** 选中图标 */
  iconActive: string;
  /** 该 tab 管辖的页面路径前缀（用于二级页高亮归属） */
  prefixes: string[];
}

const TABS: TabDef[] = [
  {
    pagePath: "pages/match/index",
    text: "匹配",
    icon: "/static/tabbar/match.png",
    iconActive: "/static/tabbar/match-active.png",
    prefixes: ["pages/match"],
  },
  {
    pagePath: "pages/circle/index",
    text: "圈子",
    icon: "/static/tabbar/circle.png",
    iconActive: "/static/tabbar/circle-active.png",
    prefixes: ["pages/circle"],
  },
  {
    pagePath: "pages/video/index",
    text: "视频",
    icon: "/static/tabbar/video.png",
    iconActive: "/static/tabbar/video-active.png",
    prefixes: ["pages/video"],
  },
  {
    pagePath: "pages/mine/index",
    text: "我的",
    icon: "/static/tabbar/mine.png",
    iconActive: "/static/tabbar/mine-active.png",
    prefixes: ["pages/mine"],
  },
];

/** 中央发布键目标页（与 App.vue 原生 midButton 行为一致） */
const PUBLISH_PAGE = "pages/circle/publish";

/** 全屏页不显示底栏（登录/协议等） */
const EXCLUDED_PREFIXES = ["pages/auth"];

/** 底栏内容高度（px），与原生 tabbar（--tab-bar-height）一致 */
const BAR_HEIGHT_PX = 50;

const STYLE_ID = "xfjy-global-tabbar-style";
const BAR_ID = "xfjy-global-tabbar";

const CSS = `
#${BAR_ID} {
  position: fixed; left: 0; right: 0; bottom: 0; z-index: 998;
  display: flex; align-items: stretch; box-sizing: border-box;
  min-height: ${BAR_HEIGHT_PX}px;
  /* 实色白底：与原生 tabbar 一致，半透明在浅色页面发透 */
  background: #ffffff;
  padding-bottom: env(safe-area-inset-bottom);
  font-size: 0;
}
#${BAR_ID}::before {
  content: ""; position: absolute; left: 0; top: 0; width: 100%; height: 1px;
  background: #ff5e9c; transform: scaleY(0.5);
}
#${BAR_ID} .gtab-item {
  flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center;
  cursor: pointer; -webkit-tap-highlight-color: rgba(0, 0, 0, 0); box-sizing: border-box;
}
#${BAR_ID} .gtab-item img { width: 24px; height: 24px; }
#${BAR_ID} .gtab-item .gtab-label {
  font-size: 10px; line-height: normal; margin-top: 3px; color: #000000;
}
#${BAR_ID} .gtab-item.active .gtab-label { color: #ff5e9c; }
/* 中央发布键：黑灰实心加号（与原生 uni-tabbar__mid 视觉一致） */
#${BAR_ID} .gtab-mid { flex: 0 0 50px; position: relative; }
#${BAR_ID} .gtab-mid::before, #${BAR_ID} .gtab-mid::after {
  content: ""; position: absolute; top: 50%; left: 50%;
  border-radius: 5px; background: #161823; transform: translate(-50%, -50%);
}
#${BAR_ID} .gtab-mid::before { width: 28px; height: 5px; }
#${BAR_ID} .gtab-mid::after { width: 5px; height: 28px; }
`;

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

function isTabRoute(route: string): boolean {
  return TABS.some((t) => route === t.pagePath);
}

function isExcluded(route: string): boolean {
  return EXCLUDED_PREFIXES.some((p) => route.startsWith(p));
}

/** 二级页高亮归属：返回所属 tab 索引，无归属返回 -1 */
function activeTabIndex(route: string): number {
  return TABS.findIndex((t) => t.prefixes.some((p) => route.startsWith(p)));
}

/** 与原生 tabbar 对齐：把 --window-bottom 写到所有页面 body 上 */
function applyWindowBottom(value: string): void {
  document.querySelectorAll("uni-page-body").forEach((el) => {
    (el as HTMLElement).style.setProperty("--window-bottom", value);
  });
}

function refresh(bar: HTMLElement): void {
  const route = currentRoute();
  const show = !!route && !isTabRoute(route) && !isExcluded(route);
  bar.style.display = show ? "flex" : "none";
  applyWindowBottom(show ? `${BAR_HEIGHT_PX}px` : "0px");
  if (!show) return;
  const active = activeTabIndex(route);
  // 仅遍历 tab 项（mid 发布键无 data-tab-index，且插入后 DOM 顺序与 TABS 数组不再对齐）
  bar.querySelectorAll<HTMLElement>(".gtab-item[data-tab-index]").forEach((el) => {
    const i = Number(el.dataset.tabIndex);
    const tab = TABS[i];
    const isActive = i === active;
    el.classList.toggle("active", isActive);
    const img = el.querySelector("img");
    if (img) {
      img.src = isActive ? tab.iconActive : tab.icon;
    }
  });
}

function buildBar(): HTMLElement {
  document.getElementById(BAR_ID)?.remove();
  const bar = document.createElement("div");
  bar.id = BAR_ID;

  TABS.forEach((tab, tabIndex) => {
    const item = document.createElement("div");
    item.className = "gtab-item";
    item.dataset.tabIndex = String(tabIndex);
    item.setAttribute("role", "button");
    item.setAttribute("aria-label", `切换到${tab.text}`);
    const img = document.createElement("img");
    img.src = tab.icon;
    img.alt = "";
    const label = document.createElement("div");
    label.className = "gtab-label";
    label.textContent = tab.text;
    item.appendChild(img);
    item.appendChild(label);
    item.addEventListener("click", () => {
      uni.switchTab({ url: `/${tab.pagePath}` });
    });
    bar.appendChild(item);
  });

  // 中央发布键（抖音式）：跳转发布动态页
  const mid = document.createElement("div");
  mid.className = "gtab-item gtab-mid";
  mid.setAttribute("role", "button");
  mid.setAttribute("aria-label", "发布动态");
  mid.addEventListener("click", () => {
    uni.navigateTo({ url: `/${PUBLISH_PAGE}` });
  });
  // 插入到第 3 位（匹配 / 圈子 之后），与原生 tabbar 布局一致
  bar.insertBefore(mid, bar.children[2] || null);

  return bar;
}

/** 在 App.vue onLaunch（H5 条件编译内）调用一次 */
export function installGlobalTabbar(): void {
  if (document.getElementById(BAR_ID)) return;

  if (!document.getElementById(STYLE_ID)) {
    const style = document.createElement("style");
    style.id = STYLE_ID;
    style.textContent = CSS;
    document.head.appendChild(style);
  }

  const bar = buildBar();
  document.body.appendChild(bar);

  // 路由变化（hash 路由）与页面节点增删时刷新显示/高亮/占位
  window.addEventListener("hashchange", () => refresh(bar));
  const observer = new MutationObserver(() => {
    requestAnimationFrame(() => {
      const show = bar.style.display !== "none";
      applyWindowBottom(show ? `${BAR_HEIGHT_PX}px` : "0px");
    });
  });
  observer.observe(document.body, { childList: true, subtree: true });

  // 兜底轮询：极少数情况下 hashchange 未触发（直接改 location、框架内部 pushState、
  // 或冷启动首帧页面栈尚未就绪），用轻量轮询保证底栏始终与实际页面一致，
  // 根除“tabbar 莫名消失/不刷新”的问题。refresh 仅切换 display/class，开销极小。
  setInterval(() => refresh(bar), 400);

  refresh(bar);
}
// #endif
