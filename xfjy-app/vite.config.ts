import { defineConfig } from "vite";
import uniPlugin from "@dcloudio/vite-plugin-uni";
import UnoCSS from "unocss/vite";
import path from "path";
import { readFileSync } from "fs";

// 构建期注入应用版本号（M-A7）：从 package.json 读取，避免页面写死 v1.0.0
const appVersion = JSON.parse(
  readFileSync(path.resolve(process.cwd(), "package.json"), "utf-8"),
).version as string;

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
  define: {
    // 版本号单一真源：package.json version，运行时以字面量内联替换
    __APP_VERSION__: JSON.stringify(appVersion),
  },
  build: {
    // 分包优化（H5 端）：只把首屏框架包钉成 vendor-vue，IM SDK 不做 manual 钉桩。
    // 历史教训：曾把 wukongimjssdk 钉成 manual chunk（vendor-im），Rollup 归组时会把
    // @dcloudio/uni-h5-vue、uni-h5 等全站公共运行时 hoist 进该 manual chunk，导致 48 个
    // chunk（含入口、基础组件、vendor-vue 自身）对 IM 包产生静态依赖，动态 import 拆链失效。
    // 现状：wukongim 仅被 pages/chat、pages/kefu 三个路由级异步页面与 App/store 的动态
    // import() 引用，不钉桩时 Rollup 按动态导入边界自动切出异步 chunk，首屏四个 tab
    // （match/circle/video/mine）的静态依赖图中零 IM 代码。
    rollupOptions: {
      output: {
        manualChunks: isH5
          ? (id: string) => {
              if (!id.includes("node_modules")) return undefined;
              const normalized = id.replace(/\\/g, "/");
              // Vue 生态 + uni-app H5 的 Vue 运行时（裸 "vue" 的别名目标，全部 SFC 公共
              // 依赖）统一钉入首屏框架 chunk，避免运行时被 Rollup 吸进其它 manual chunk。
              if (
                normalized.includes("node_modules/vue/") ||
                normalized.includes("node_modules/@vue/") ||
                normalized.includes("node_modules/@dcloudio/uni-h5-vue/") ||
                normalized.includes("node_modules/pinia/") ||
                normalized.includes("node_modules/vue-i18n/") ||
                normalized.includes("node_modules/@intlify/")
              ) {
                return "vendor-vue";
              }
              return undefined;
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