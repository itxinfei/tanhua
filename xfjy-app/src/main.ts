import { createSSRApp } from "vue";
import { createPinia } from "pinia";
import uviewPlus from "uview-plus";
import UniIcons from "@dcloudio/uni-ui/lib/uni-icons/uni-icons.vue";
import App from "./App.vue";
import { installErrorHandler } from "./utils/logger";

export function createApp() {
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