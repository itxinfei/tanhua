import { defineConfig } from "unocss";
import { presetIcons } from "@unocss/preset-icons";

/**
 * UnoCSS 配置（仅启用 Icons 预设，不引入原子类/reset，避免影响既有 SCSS 样式体系）。
 *
 * 图标集：Phosphor（@iconify-json/ph）——6 档字重、风格统一圆润，页面内图标的唯一来源，
 * 用于根治 uni-icons / PNG / CSS 图形多套并存。
 * 用法：class="i-ph-heart"（线性）/ "i-ph-heart-bold"（粗线）/ "i-ph-heart-fill"（实心），
 *       颜色由 CSS color 控制（mask + currentColor），尺寸由 width/height 控制（统一 rpx）。
 * 仅源码中静态出现的完整类名会被收集，动态拼接的类名必须保证完整字符串出现在源码中。
 *
 * 渲染：presetIcons 用 CSS mask（含 -webkit-mask），H5 与微信小程序构建产物均已验证生成
 * 对应规则；运行时以微信开发者工具/真机实测为准，若个别基础库不支持 mask 再切 ?bg 背景模式。
 * 例外：pages.json 原生 tabBar 的 iconPath 只认位图，底栏 PNG 由 scripts/gen-icons.mjs
 * 取同一套 Phosphor 图标光栅化产出，保证双端造型一致。
 */
export default defineConfig({
  presets: [
    presetIcons({
      warn: true,
      collections: {
        // 动态 import：构建期按需取图标，整个 JSON 不进业务包
        ph: () =>
          import("@iconify-json/ph/icons.json").then((i) => i.default as any),
      },
    }),
  ],
});
