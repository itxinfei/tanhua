import { defineConfig } from "vite";
import uniPlugin from "@dcloudio/vite-plugin-uni";
import UnoCSS from "unocss/vite";
import path from "path";

// 文件名须为 vite.config.ts（HBuilderX 云打包只认 vite.config.js/.ts 来判定 Vue3 CLI 工程），
// 配合 package.json 的 "type":"module" 以原生 ESM 加载，否则 unocss/vite（ESM-only）无法被 require。
// @dcloudio/vite-plugin-uni 是 CJS 包（标记 __esModule）：原生 ESM 加载时 default 拿到的是
// 命名空间对象，真正的插件工厂在其 .default 属性上
const uni = (uniPlugin as { default?: typeof uniPlugin }).default ?? uniPlugin;

// 仅 H5 需要手动拆分 vendor chunk；App/小程序平台 uni 会把 vue 等设为 external，
// 此时 manualChunks 里再引用 "vue" 会被 Rollup 拒绝（"vue" cannot be included in manualChunks）
const isH5 = process.env.UNI_PLATFORM === "h5";

// https://vitejs.dev/config/
export default defineConfig({
  // UnoCSS 必须在 uni 插件之前，保证图标类先转换再交小程序编译器
  plugins: [UnoCSS(), uni()],
  build: {
    // 分包优化：node_modules 第三方依赖单独 chunk，加速首屏加载（H5 端）
    rollupOptions: {
      output: {
        manualChunks: isH5
          ? {
              "vendor-vue": ["vue", "vue-i18n", "pinia"],
              "vendor-im": ["wukongimjssdk"],
            }
          : undefined,
      },
    },
    // 包体积警告阈值从 500kB 提至 2MB，避免第三方库（uni 系列）误报
    chunkSizeWarningLimit: 2048,
    sourcemap: false,
  },
  css: {
    preprocessorOptions: {
      scss: {
        api: "modern",
        // dart-sass 1.80+ 要求显式指定 api 接口，modern 是推荐方式
        silenceDeprecations: ["import", "legacy-js-api"],
      },
    },
  },
  resolve: {
    alias: {
      "@": path.resolve(import.meta.dirname, "src"),
    },
  },
});