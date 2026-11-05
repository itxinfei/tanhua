import { createSSRApp } from "vue";
import { createPinia } from "pinia";
import UniIcons from "@dcloudio/uni-ui/lib/uni-icons/uni-icons.vue";
import App from "./App.vue";
import { installErrorHandler } from "./utils/logger";
// UnoCSS 图标样式（由 unocss/vite 生成的虚拟模块）
// 注意：uni-app dev 插件在 Windows 下会把 virtual:uno.css 的 \0 前缀反解成含 null 字节的
// 文件路径导致 500；按 unocss-applet 官方 uni-app 示例用 "uno.css" 导入，走另一解析通道规避
import "uno.css";

export function createApp() {
  // 注意：installAuthGuard 不能在此调用 —— 生产构建下此函数执行时 uni-h5 插件
  // 尚未通过 app.use(__plugin) 注册 uni.navigateTo 等 API（uni.xxx 为 undefined，
  // 读取 .bind 直接 TypeError 导致白屏）。正确安装时机是 App.vue 的 onLaunch
  // （插件 install 之后、任何页面导航之前），见 src/App.vue。
  const app = createSSRApp(App);
  const pinia = createPinia();
  app.use(pinia);
  app.component("uni-icons", UniIcons);
  installErrorHandler(app);
  return {
    app,
    pinia,
  };
}