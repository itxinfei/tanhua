// -*- coding: utf-8 -*-
/**
 * 心飞交友 App 底部 tabBar 图标产线（Node 版）。
 *
 * 设计说明：
 * - 图标源：Phosphor（@iconify-json/ph）——6 档字重、风格统一圆润；
 *   tabBar 普通态取 bold 字重（28px 小尺寸下笔画足够实），选中态取 fill 实心；
 * - 页面内图标不走本产线，统一用 UnoCSS Icons 的 class（i-ph-*，见 uno.config.ts），
 *   颜色随 CSS color、暗色模式自动跟随；只有 pages.json 原生 tabBar 的 iconPath
 *   只认位图，故 tabBar 这 9 张 PNG 仍由本脚本光栅化产出；
 * - 渲染：@resvg/resvg-js 光栅化 SVG → PNG（Rust 预编译二进制，无需系统依赖）；
 * - 颜色 / 尺寸全部参数化，品牌色与 App.vue token 保持一致，换主色后重跑本脚本即可；
 * - tabBar 文件名与 pages.json / config/tab-config.ts 的引用严格对齐，勿擅自改名。
 *
 * 运行：npm run gen:icons
 * 输出：src/static/tabbar/*.png
 */
import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { Resvg } from "@resvg/resvg-js";
import ph from "@iconify-json/ph/icons.json" with { type: "json" };

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const TABBAR_DIR = join(ROOT, "src", "static", "tabbar");

// ---- 品牌色（与 App.vue page 级 token 对齐）----
const INACTIVE = "#9aa3af"; // 普通态中性灰
const ACTIVE = "#fe3c72"; // 选中态品牌玫红
const CORAL = "#ff655b"; // 珊瑚红橙（发布键渐变终点）
const WHITE = "#ffffff";

// Phosphor 画布为 256x256（图标本体四周自带约 9% 留白）
const VB = 256;
const PAD = 8; // 额外安全边距，避免贴边笔画缩放时被裁半像素

/**
 * 统一输出：SVG 字符串光栅化为 PNG 写盘。
 * @param {string} svg 完整 svg 字符串（自带 viewBox）
 * @param {string} file 输出绝对路径
 * @param {number} outSize 输出 PNG 边长（px）
 */
function savePng(svg, file, outSize) {
  const png = new Resvg(svg, {
    fitTo: { mode: "width", value: outSize },
    background: "rgba(0,0,0,0)",
  })
    .render()
    .asPng();
  writeFileSync(file, png);
}

/** 取 Phosphor 图标的内部节点，并把 currentColor 替换为目标色 */
function phosphorBody(name, color) {
  const data = ph.icons[name];
  if (!data) throw new Error(`Phosphor 图标不存在: ${name}`);
  return data.body.replace(/currentColor/g, color);
}

/**
 * 加安全边距 + 可选视觉补偿缩放（绕画布中心，不改变 viewBox 占位）。
 * 三角形/心形等斜切造型天生视觉面积偏小，tabBar 并排时用 scale 补齐重量。
 */
function frame(body, scale = 1) {
  const vb = VB + PAD * 2;
  const content =
    scale === 1
      ? body
      : `<g transform="translate(${VB / 2} ${VB / 2}) scale(${scale}) translate(${-VB / 2} ${-VB / 2})">${body}</g>`;
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="${-PAD} ${-PAD} ${vb} ${vb}">${content}</svg>`;
}

/** 发布键：玫红→珊瑚 135° 渐变圆角方块 + 白色加粗加号（微信小程序原生 tabBar 使用） */
function publishSvg() {
  // plus-bold 本体缩放到约 52% 居中；rx 取画布 22%，iOS 图标风圆角
  const plus = phosphorBody("plus-bold", WHITE);
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${VB} ${VB}">
  <defs>
    <linearGradient id="brand" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="${ACTIVE}"/>
      <stop offset="1" stop-color="${CORAL}"/>
    </linearGradient>
  </defs>
  <rect x="18" y="18" width="220" height="220" rx="56" fill="url(#brand)"/>
  <g transform="translate(61 61) scale(0.523)">${plus}</g>
</svg>`;
}

// ---- tabBar 配置：[文件名, 普通态 bold 图标, 选中态 fill 图标, 视觉补偿] ----
const TAB_ICONS = [
  ["match", "heart-bold", "heart-fill", 1.06],
  ["circle", "users-three-bold", "users-three-fill", 1],
  ["video", "play-bold", "play-fill", 1.12],
  ["mine", "user-bold", "user-fill", 1],
];

function buildTabbarIcons() {
  mkdirSync(TABBAR_DIR, { recursive: true });
  for (const [name, boldIcon, fillIcon, scale] of TAB_ICONS) {
    savePng(
      frame(phosphorBody(boldIcon, INACTIVE), scale),
      join(TABBAR_DIR, `${name}.png`),
      192,
    );
    savePng(
      frame(phosphorBody(fillIcon, ACTIVE), scale),
      join(TABBAR_DIR, `${name}-active.png`),
      192,
    );
  }
  savePng(publishSvg(), join(TABBAR_DIR, "publish.png"), 192);
}

buildTabbarIcons();
console.log("tabbar icons generated ->", TABBAR_DIR);
