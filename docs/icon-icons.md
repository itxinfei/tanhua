# 图标使用规范（心飞交友 xfjy）

> 适用项目：`xfjy-app/`（uni-app + Vue3 + TS + Vite，多端：H5 / App / 微信小程序等）
> 状态：**图标框架选型已锁定（2026-11-02 评审），全端统一，禁止回退。**
> 关联文档：`xfjy-app/src/uni.scss`（品牌色 Token）、`docs/AI-agent交接-品牌主色切换.md`、`AGENTS.md`

## 一、决策结论（防止其他 Agent 改回来）

1. **图标框架固定为：UnoCSS Icons（presetIcons）+ Iconify 的 Phosphor 图标集**。
   - 依赖：`unocss`、`@unocss/preset-icons`（或 presetIcons 内置包）、`@iconify-json/ph`
   - 图标集合前缀：`ph`；样式类名格式：`i-ph-{图标名}`
2. Phosphor 入选理由：9000+ 图标、MIT 协议可免费商用，风格圆润年轻、线条统一，与陌生人交友调性及主色 `#fe3c72` 匹配；同一图标提供多种字重，便于建立层级。
3. **旧方案 `uni-icons`（@dcloudio/uni-ui）冻结**：官方已说明图标无法无限扩充、custom-prefix 扩展即将废弃，且样式只能通过 `color/size` 属性控制、风格偏老。
4. 字节 IconPark、iconfont、Lucide 等**不作为本项目主方案**；iconfont 仅在未来存在 UI 设计师专属品牌图标时另行评估，且必须 base64 才能兼容小程序。

## 二、使用规则

### 2.1 类名与字重

- 标准写法（中划线连接，禁止冒号）：

```html
<!-- 常规字重 -->
<view class="i-ph-heart" />
<!-- 字重/风格后缀：thin / light / bold / fill / duotone -->
<view class="i-ph-heart-bold" />
<view class="i-ph-heart-fill" />
```

- **禁止使用冒号写法** `i-ph:heart`：微信小程序会把冒号分割成两个类名导致图标不显示。
- 图标查询：Icônes（https://icones.js.org/）或 Iconify（https://iconify.design/），选择 Phosphor 集合，取 `ph-` 前缀的类名。

### 2.2 颜色与大小

- 颜色、大小通过 CSS class 控制，禁止再写内联 `color/size` 属性式依赖：

```html
<view class="i-ph-heart text-[var(--color-primary)] text-24px" />
```

- 颜色必须引用主题变量（`--color-primary` / `--color-primary-text` 等），遵守无障碍规则：浅底小字/小图标需要足够对比度时，配合 `#c2185b` 深变体，禁止硬编码十六进制色。

### 2.3 动态图标名

- 支持响应式动态类名（`const icon = ref('i-ph-car')` → `:class="icon"`）。
- 但**拼接生成、后端返回、跨文件无法静态分析**的类名，UnoCSS 预扫描不到，必须二选一：
  1. 在 `xfjy-app/uno.config.ts` 的 `safelist` 中登记完整类名；
  2. 在 SFC 任意位置写出完整类名（可注释）。
- 禁止依赖运行时字符串拼接而不做 safelist，否则生产环境图标丢失。

### 2.4 小程序兼容要点

- 微信小程序 CSS 不支持本地字体/本地文件路径；UnoCSS Icons 经构建处理后输出兼容产物，按脚手架（参考 unibest 实践）默认配置即可，**不要自行引入本地字体文件**。
- 若未来引入 iconfont 字体，项目设置必须勾选 base64，否则非 H5 端不生效。

## 三、uni-icons 冻结与迁移策略

1. **冻结规则**：
   - 存量 `uni-icons` 引用（33 处 / 11 个文件）**保留可运行，不强制立即删除**；
   - **禁止新增任何 `uni-icons` 引用**，新页面、新组件、新功能一律使用 `i-ph-*`；
   - 禁止把已迁移的 Phosphor 图标改回 uni-icons；禁止重新引入其他图标库做主方案。
2. **迁移策略（分批、低风险）**：
   - 按页面逐步替换，修改某个页面时顺手把该页 uni-icons 迁移为 Phosphor；
   - P0 核心页面（匹配/滑卡、聊天、我的）优先；
   - 迁移时保持图标语义一致，逐页验证多端显示，迁移完成后移除该页 uni-icons 标签；
   - 两套图标允许在迁移期共存，不做大爆炸式重写。
3. main.ts 中 `uni-icons` 的全局注册在全部存量迁移完成前保留，最后一批迁移完成后再移除并卸载相关依赖。

## 四、tabbar 等定制图形的特殊处理

- 底部 tabbar 图标与中央发布键**继续使用 `scripts/gen-tabbar-icons.ps1` 生成的 PNG**（输出 `src/static/tabbar/`），不改成字体/类图标——品牌定制图形（心形、发布键渐变）不适合通用图标库。
- 改品牌色后必须重跑该脚本；tabbar 选中色以 `theme.json` + 生成 PNG + App.vue/global-tabbar 三处同步为准。
- 废弃文件 `chat.png`、`chat-active.png` 无引用，确认后人工清理，本规范不自动删除。

## 五、红线（违反即返工）

1. 禁止新增 `uni-icons` 或把 Phosphor 图标回退为 uni-icons。
2. 禁止引入未评审的其他图标库作为主方案（u-icon、IconPark、FontAwesome 等）。
3. 禁止冒号类名、禁止未登记 safelist 的动态拼接类名。
4. 禁止图标颜色硬编码十六进制，必须走主题变量；浅底图标遵守对比度规则。
5. 禁止把 tabbar 定制 PNG 改成通用字体图标。
6. 禁止一次性全量重写存量图标，必须分批迁移并逐页验证。

## 六、交付前自检（图标相关）

- 新增图标是否全部使用 `i-ph-*`，本页/本组件有无新增 uni-icons
- 类名是否为中划线、动态类名是否已 safelist
- 颜色是否引用主题变量、浅底对比度是否达标
- 小程序/H5 端图标是否实际渲染验证
- 若改动 tabbar，是否重跑 `gen-tabbar-icons.ps1` 并三处同步
- `npm run type-check` 是否通过

## 七、落地待办（实施时执行，本文件仅锁定决策）

1. 安装 `unocss` 与 `@iconify-json/ph`，新增 `xfjy-app/uno.config.ts`（presetIcons + presetUno，配置 safelist），在 `vite.config.ts` 接入 UnoCSS 插件。
2. 制作图标对照页（同语义 uni-icons vs Phosphor），确认风格后按页面分批迁移。
3. 迁移完成后清理 main.ts 全局注册与 uni-icons 依赖。
