# H5 底部 tabbar 机制与全局底栏

> 版本：v1.1 · 2026-11-03
> 关联：`docs/需求文档/01-需求文档.md` 附录 B1/B2、`AGENTS.md` §7 uni-app 硬约束（CLAUDE.md 已退化为指向 AGENTS.md 的指针）

---

## 一、架构背景

uni-app 的原生 tabBar 只在 `pages.json` 的 `tabBar.list` 中列出的 4 个 tab 页渲染。通过 `navigateTo` 进入的二级页（vip / nearby / chat 等）**天生没有底栏**。

为了统一体验，项目在 `App.vue`（H5 条件编译内）调用 `installGlobalTabbar()`，在 `document.body` 上挂载一个 `position: fixed` 的全局底栏（`global-tabbar.ts` + `AppTabbar.vue`），接管所有页面（含 tab 页），原生 tabBar 由 App.vue 隐藏避免双栏。

**关键规则：**
- tab 页：显示注入底栏，原生 tabBar 已隐藏
- 非 tab 二级页：显示，按路径前缀高亮归属 tab
- 登录页（`pages/auth/*`）和发布页（`pages/circle/publish`）：不显示

## 二、底栏高度：用 `--tab-bar-height`，禁用 `--window-bottom`

### 2.1 正确的 CSS 变量

uni-app H5 运行时会在 `:root` 上自动设置 `--tab-bar-height`（从 `pages.json` 的 `tabBar.height` 读取，本项目为 50px）。**scoped 样式可直接继承，可靠生效。**

```scss
/* ✅ 正确：用 uni-app 原生变量 */
.page-container {
  padding-bottom: calc(var(--tab-bar-height, 0px) + 40rpx);
}
```

### 2.2 错误的 CSS 变量（已踩坑）

`global-tabbar.ts` 的 `applyWindowBottom()` 会把 `--window-bottom` 写到 `uni-page-body` 元素上。但：

- 页面 scoped 样式作用域内**可能取不到值**（实测 `match/index.vue` 取到 0px）
- `circle/index.vue`、`mine/index.vue` 用了 `var(--window-bottom)` 但也可能被遮挡
- `match/index.vue` 第 504 行注释已记录此问题

```scss
/* ❌ 错误：global-tabbar 写到 uni-page-body，scoped 内可能为 0px */
.page-container {
  padding-bottom: calc(var(--window-bottom, 0px) + 40rpx);
}
```

### 2.3 判断标准

| 变量 | 定义位置 | scoped 样式是否可靠 | 结论 |
|------|----------|---------------------|------|
| `--tab-bar-height` | uni-app 运行时 `:root` | ✅ 可靠 | **使用这个** |
| `--window-bottom` | `global-tabbar.ts` → `uni-page-body` | ❌ 不可靠（可能 0px） | 禁用 |

## 三、底部固定按钮的正确写法

非 tab 页（navigateTo 进入）如需底部固定按钮（如 vip 页支付按钮），**必须**：

1. 页面容器用 `display: flex; flex-direction: column;`
2. 按钮用 `margin-top: auto` 推到底部（**不要用 `position: fixed`**，会与全局底栏重叠）
3. 页面容器 `padding-bottom` 用 `calc(var(--tab-bar-height, 0px) + 视觉间距)`

```scss
/* ✅ 正确示例（vip.vue） */
.vip-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  padding-bottom: calc(var(--tab-bar-height, 0px) + 40rpx);
}

.pay-btn {
  margin-top: auto;   /* 推到父容器底部 */
  margin-left: 32rpx;
  margin-right: 32rpx;
  height: 96rpx;
}
```

```scss
/* ❌ 错误示例：position: fixed 会被全局底栏遮挡 */
.pay-btn {
  position: fixed;
  bottom: calc(40rpx + env(safe-area-inset-bottom, 0px));
}
```

## 四、tab 页高度计算

tab 页（match/index、circle/index、video/index）内容高度应减去底栏：

```scss
/* ✅ 正确 */
.page-container {
  height: calc(100vh - var(--tab-bar-height, 0px));
}
```

## 五、排查清单

| 现象 | 根因 | 修复 |
|------|------|------|
| 底部按钮被遮挡 | 用了 `position: fixed` + 全局底栏覆盖 | 改 `margin-top: auto` + `padding-bottom: var(--tab-bar-height)` |
| 底部按钮被遮挡 | 用了 `var(--window-bottom)` 但 scoped 内为 0px | 改 `var(--tab-bar-height)` |
| 内容被底栏裁切 | tab 页高度未减 `--tab-bar-height` | `height: calc(100vh - var(--tab-bar-height))` |
| 双栏重叠 | 页面自己写了底栏 + 全局底栏 | 删除页面内底栏，用全局底栏 |

## 六、相关文件索引

| 文件 | 作用 |
|------|------|
| `src/utils/global-tabbar.ts` | H5 全局底栏注入逻辑 |
| `src/components/AppTabbar.vue` | 底栏 UI 组件 |
| `src/config/tab-config.ts` | tab 项配置（高度常量 50px） |
| `src/pages/match/index.vue` | 已修复的 tab 页示例 |
| `src/pages/mine/vip.vue` | 已修复的非 tab 页示例 |
