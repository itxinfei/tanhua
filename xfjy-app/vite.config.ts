import { defineConfig } from "vite";
import uni from "@dcloudio/vite-plugin-uni";
import path from "path";

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [uni()],
  css: {
    preprocessorOptions: {
      scss: {
        api: "modern",
        // vite 5.2 尚不支持 api 选项（5.4 才有），实际仍走 legacy 接口，故一并静默 legacy-js-api 警告
        // uview-plus 库内部仍在使用 @import，等库升级后移除 import 静默项
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