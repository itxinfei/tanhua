import { createSSRApp } from "vue";
import { createPinia } from "pinia";
import uviewPlus from "uview-plus";
import UniIcons from "@dcloudio/uni-ui/lib/uni-icons/uni-icons.vue";
import App from "./App.vue";
import { installErrorHandler } from "./utils/logger";
import { installAuthGuard } from "./utils/auth-guard";
// UnoCSS 图标样式（由 unocss/vite 生成的虚拟模块）
// 注意：uni-app dev 插件在 Windows 下会把 virtual:uno.css 的 \0 前缀反解成含 null 字节的
// 文件路径导致 500；按 unocss-applet 官方 uni-app 示例用 "uno.css" 导入，走另一解析通道规避
import "uno.css";

export function createApp() {
  // 尽早安装 auth-guard，确保在 App onLaunch 之前生效
  installAuthGuard();
  const app = createSSRApp(App);
  const pinia = createPinia();
  app.use(pinia);
  app.use(uviewPlus);
  app.component("uni-icons", UniIcons);
  installErrorHandler(app);
  return {
    app,
    pinia,
  };
}