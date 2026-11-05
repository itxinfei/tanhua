import { createSSRApp, type App as VueApp } from "vue";
import { createPinia } from "pinia";
import UniIcons from "@dcloudio/uni-ui/lib/uni-icons/uni-icons.vue";
import App from "./App.vue";
import { installErrorHandler } from "./utils/logger";
// UnoCSS 图标样式（由 unocss/vite 生成的虚拟模块）
// 注意：uni-app dev 插件在 Windows 下会把 virtual:uno.css 的 \0 前缀反解成含 null 字节的
// 文件路径导致 500；按 unocss-applet 官方 uni-app 示例用 "uno.css" 导入，走另一解析通道规避
import "uno.css";

/**
 * uni-h5-vue 插槽更新兼容补丁。
 *
 * `@dcloudio/uni-h5-vue` 的运行时 fork 自 Vue 3.4.21：`initSlots` 用 `def(children, "_", type)`
 * 把插槽对象上的内部标记 `_` 定义为**只读**，而 `updateSlots` 更新插槽时用
 * `Object.assign(slots, children)` 把新插槽对象整份覆盖上去 —— 向只读的 `_` 写入即抛
 * `TypeError: Cannot assign to read only property '_'`。
 *
 * 该异常发生在组件 reactive effect 内部，会中断 Vue 的 scheduler flush，此后整个页面
 * 所有响应式更新全部丢失：状态确实变了，但界面永远不刷新（典型表现是“点了完全没反应”）。
 *
 * Vue 3.5 已修复此处：改用 `assignSlots()` 只逐个拷贝插槽键（跳过 `_`），并以
 * `def(slots, "_", type, true)` 写入可写的 `_`。本补丁等价地把每个组件插槽的 `_`
 * 放宽为可写，从而让 3.4.21 的 `Object.assign` 路径不再抛错。
 *
 * 待 `@dcloudio/*` 升级到含该修复的版本后，删除本函数及其调用即可。
 */
function installSlotUpdatePatch(app: VueApp): void {
  app.mixin({
    mounted() {
      const slots = (this as unknown as { $slots?: Record<string, unknown> }).$slots;
      if (!slots || typeof slots !== "object") return;
      const marker = Object.getOwnPropertyDescriptor(slots, "_");
      if (marker && marker.writable === false) {
        Object.defineProperty(slots, "_", { ...marker, writable: true });
      }
    },
  });
}

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
  installSlotUpdatePatch(app);
  return {
    app,
    pinia,
  };
}
