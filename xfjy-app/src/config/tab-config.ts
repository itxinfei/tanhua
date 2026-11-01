/**
 * 底部导航统一配置（单一数据源）
 *
 * App 内存在三套 tabbar 实现，均以本文件为视觉规格与数据来源：
 *  1. 原生 tabBar —— pages.json tabBar.list（编译期配置，无法 import，改动需人工同步本文件）
 *  2. H5 全局注入底栏 —— utils/global-tabbar.ts（非 tab 二级页）
 *  3. 发布页组件底栏 —— components/custom-tabbar.vue（H5 发布页）
 *
 * 视觉规格（与 App.vue .uni-tabbar 样式锁定值一致）：
 *  底栏高度 50px · 图标 28px · 文字 10px · 实色白底 · 品牌色 1px 发丝线 · 抖音式中央发布键
 *
 * 修改 tab 项时务必同步：本文件 + pages.json tabBar.list（两者无法自动联动）
 */

/** 底栏内容高度（px），与原生 tabbar --tab-bar-height 一致 */
export const TAB_BAR_HEIGHT_PX = 50;

/** tab 图标尺寸（px），与 App.vue .uni-tabbar__icon 锁定值一致 */
export const TAB_ICON_SIZE_PX = 28;

/** tab 文字尺寸（px） */
export const TAB_FONT_SIZE_PX = 10;

/** 中央发布键目标页（与 App.vue 原生 midButton 行为一致；MP 端为原生第 5 个 tab） */
export const PUBLISH_PAGE = "pages/circle/publish";

/**
 * 不显示全局注入底栏的页面路径前缀：
 *  - pages/auth：登录/协议等全屏页
 *  - publish：H5 端由 custom-tabbar 组件渲染底栏，排除注入避免双栏重叠
 */
export const EXCLUDED_PREFIXES = ["pages/auth", PUBLISH_PAGE];

export interface TabDef {
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

/** 4 个主 tab（与 pages.json tabBar.list 一致；MP 端第 5 个"发布"tab 仅编译期存在） */
export const TABS: TabDef[] = [
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