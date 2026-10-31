# H5 tabBar 机制与全局底栏

> 适用范围：xfjy-app H5 端。小程序端 tabBar 为原生组件，不受本文影响。

## 一、机制：为什么二级页"tabbar 不见了"

uni-app 的原生 tabBar **只渲染在 `pages.json → tabBar.list` 声明的 tab 页上**（本项目 4 个：
`pages/match/index`、`pages/circle/index`、`pages/video/index`、`pages/mine/index`）。

任何通过 `uni.navigateTo` 进入的普通页面（如 `mine/vip`、`match/nearby`、`chat/chat`），
运行时根本不存在 tabbar 实例——**这是框架机制，不是页面 bug**，因此逐页打补丁必然遗漏。

## 二、全局修复方案（已落地）

`src/utils/global-tabbar.ts` 在 `App.vue onLaunch` 时（`#ifdef H5`）注入一个与原生 tabbar
视觉一致的固定底栏，规则：

| 当前页面 | 行为 |
|---|---|
| tab 页 | 隐藏自身（原生 tabbar 已存在，避免双栏） |
| `/pages/auth/*`（登录等全屏页） | 不显示 |
| 其它二级页 | 显示，并按**路径前缀**高亮所属 tab |

- 前缀归属：`pages/match/* → 匹配`、`pages/circle/* → 圈子`、`pages/video/* → 视频`、`pages/mine/* → 我的`；无归属（如 `pages/chat/*`）不高亮
- 中央发布键 "+"：跳转 `pages/circle/publish`（与原生 midButton 行为一致）
- 同步维护 `--window-bottom`（50px），避免页面底部内容被底栏遮挡
- tabbar 配色（白底黑字、选中主色 #FF5E9C）以 `theme.json`（文字）+ `static/tabbar/*.png`（图标）+ `App.vue`（原生栏样式）/ `global-tabbar.ts`（注入栏样式）为准，**改配色需同步这几处**

## 三、开发规则（新增页面必读）

1. **新增二级页不需要（也不应该）自己实现底部导航**——全局底栏自动生效，无需任何代码。
2. 新增页面若不想显示底栏（全屏沉浸页，如播放器、客服全屏聊天），把路径前缀加入
   `global-tabbar.ts` 的 `EXCLUDED_PREFIXES`。
3. 新增/调整 tab 归属：修改 `global-tabbar.ts` 的 `TABS[].prefixes`，保持与 `pages.json`
   `tabBar.list` 一致；`tabBar.list` 增删 tab 时**必须**同步 `TABS` 数组（pagePath/icon/前缀）。
4. 登录态：全局底栏无登录判断，未登录访问业务页由 `utils/auth-guard.ts` 弹回登录页
   （登录页在排除列表中），不需要页面处理。
5. 页面底部留白：如页面自身 `padding-bottom` 不足，直接依赖 `--window-bottom`（底栏已维护）。

## 四、相关文件

| 文件 | 职责 |
|---|---|
| `src/utils/global-tabbar.ts` | H5 全局底栏注入（本文方案主体） |
| `src/App.vue` | `onLaunch` 安装；原生 tabbar 样式覆盖（`.uni-tabbar` 等） |
| `src/pages.json` + `src/theme.json` | tabBar 页面清单与文字配色（构建期配置，改动需重启 dev server） |
| `src/static/tabbar/*.png` | 图标素材：未选中纯黑、选中主色粉 |
