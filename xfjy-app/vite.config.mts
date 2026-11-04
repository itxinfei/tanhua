import { defineConfig } from "vite";
import uniPlugin from "@dcloudio/vite-plugin-uni";
import UnoCSS from "unocss/vite";
import path from "path";

// @dcloudio/vite-plugin-uni 是 CJS 包（标记 __esModule）：.mts 配置以原生 ESM 加载时，
// default 导入拿到的是命名空间对象，真正的插件工厂在其 .default 属性上
const uni = (uniPlugin as { default?: typeof uniPlugin }).default ?? uniPlugin;

// https://vitejs.dev/config/
export default defineConfig({
  // UnoCSS 必须在 uni 插件之前，保证图标类先转换再交小程序编译器
  plugins: [UnoCSS(), uni()],
  build: {
    // 分包优化：node_modules 第三方依赖单独 chunk，加速首屏加载（H5 端）
    rollupOptions: {
      output: {
        manualChunks: {
          "vendor-vue": ["vue", "vue-i18n", "pinia"],
          "vendor-im": ["wukongimjssdk"],
        },
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
        silenceDeprecations: ["legacy-js-api"],
      },
    },
  },
  resolve: {
    alias: {
      "@": path.resolve(import.meta.dirname, "src"),
    },
  },
});