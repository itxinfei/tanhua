# 品牌色使用规范（心飞交友 / 心飞为你飞）

> 适用项目：xfjy-admin（运营后台，Vue3 + Element Plus）、xfjy-app（用户端 App，uni-app + uview-plus）
> 状态：主色已锁定，全端统一。最近更新 2026-10-30。

## 1. 主色与定位

| 角色 | 颜色 | 色值 | 说明 |
| --- | --- | --- | --- |
| **品牌主色** | 玫红 | `#ff5e9c` | 全局唯一主色，用于主按钮、链接、高亮、关键图标、品牌渐变 |
| 辅助主色 | 深紫 | `#b13bff` | 主色渐变收尾、次级强调、深色背景上的主色 |
| 浅粉 | 柔粉 | `#ff8fb1` | 主色浅色调，用于渐变过渡、浅底 |
| 紫罗兰 | 紫 | `#8a5cf6` | 辅助色，用于多卡片区分等 |
| 雾粉 | 极浅粉 | `#ffd1e8` | 辅助浅色 |

定位：恋爱/社交调性，玫红为主、深紫为辅，整体浪漫温暖。

## 2. 色板总表（变量 → 色值）

### 2.1 xfjy-admin（src/style.css :root）

| CSS 变量 | 色值 | 用途 |
| --- | --- | --- |
| `--xfjy-primary` | `#ff5e9c` | 品牌主色 |
| `--xfjy-primary-deep` | `#b13bff` | 辅助深紫 |
| `--xfjy-primary-soft` | `#ff8fb1` | 柔粉 |
| `--xfjy-primary-violet` | `#8a5cf6` | 紫罗兰 |
| `--xfjy-primary-mist` | `#ffd1e8` | 雾粉 |
| `--el-color-primary` | `#ff5e9c` | 覆盖 Element Plus 默认蓝，按钮/链接/选中态等自动生效 |
| `--el-color-primary-light-3` | `#ff7eb0` | Element 主色浅 30% |
| `--el-color-primary-light-5` | `#ffafce` | Element 主色浅 50% |
| `--el-color-primary-light-7` | `#ffcfe1` | Element 主色浅 70% |
| `--el-color-primary-light-8` | `#ffdfeb` | Element 主色浅 80% |
| `--el-color-primary-light-9` | `#ffeff5` | Element 主色浅 90%（极浅底） |
| `--el-color-primary-dark-2` | `#cc4b7d` | Element 主色深 20%（按下/激活） |

> 说明：`--el-color-primary-*` 是 Element Plus 的衍生色，必须随 `--el-color-primary` 同步手算更新（Element 不会自动按主色生成）。

### 2.2 xfjy-app

| 入口 | 变量 | 色值 |
| --- | --- | --- |
| `src/uni.scss` | `$uni-color-primary` | `#ff5e9c` |
| `src/App.vue`（浅色模式 `:root`） | `--color-primary` | `#ff5e9c` |
| `src/App.vue`（深色模式 `@media dark`） | `--color-primary` | `#ff8eb9` |
| 组件字面量（渐变浅粉） | — | `#ff8fb1` |
| 组件字面量（浅底） | — | `#ffeff5` |

## 3. 唯一真源（改色只改这里）

- **xfjy-admin**：`xfjy-admin/src/style.css` 顶部 `:root` 中的 `--xfjy-primary`、衍生 `--el-color-primary-*`。
- **xfjy-app**：`xfjy-app/src/uni.scss` 的 `$uni-color-primary` 与 `xfjy-app/src/App.vue` 的 `--color-primary`（含深色模式）。

两个工程各自维护同一组色值（`#ff5e9c` / `#b13bff`），目前以「相同十六进制」保持一致；尚未抽公共 npm 包。

## 4. 如何使用

### 4.1 xfjy-admin

- **CSS / scoped 样式**：直接用 `var(--xfjy-primary)`；Element 组件（el-button type=primary、链接、选中）会自动套用 `--el-color-primary`，无需手写。
- **SVG 插画**：用内联样式引用变量，例如 `<stop offset="0%" style="stop-color: var(--xfjy-primary-deep)" />`。
- **ECharts 等 Canvas 图表**：CSS 变量无法进入 canvas，须读取变量值后再传入。已封装工具：

  ```ts
  import { getBrandColors, hexToRgba } from '@/utils/brand'
  const BRAND = getBrandColors() // 运行时 getComputedStyle 读取 :root 变量
  // 线/柱颜色
  itemStyle: { color: BRAND.primary }
  // 渐变区域（带透明度）
  color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
    { offset: 0, color: hexToRgba(BRAND.primaryDeep, 0.3) },
    { offset: 1, color: hexToRgba(BRAND.primaryDeep, 0.02) },
  ])
  ```

  这样主色变更后，图表在下次渲染时自动跟随，无需改十六进制。

### 4.2 xfjy-app

- **uview-plus 组件**：直接生效 `$uni-color-primary`（在 `uni.scss` 配置）。
- **页面样式**：优先用 `var(--color-primary)`；uni-app 跨端对 CSS 变量支持有限，渐变/边框等场景可用字面量 `#ff5e9c` / `#ff8fb1` / `#ffeff5`（已全局统一）。
- **JS 动态色**：如 `:style="{ background: item.color }"`，传 `#ff5e9c` 等字面量即可。

## 5. 更换主色步骤

1. 在「唯一真源」处修改色值（admin 改 `--xfjy-primary` 及 `--el-color-primary-*`，app 改 `$uni-color-primary` 与 `--color-primary`）。
2. 重新计算 Element 衍生色（`light-3/5/7/8/9`、`dark-2`）——按比例与白/黑混合，参考现有值推算。
3. 重新构建即可，图表（`getBrandColors`）与 Element 组件自动跟随。

## 6. 约定 / 禁止事项

- ❌ 禁止新增写死旧蓝 `#409EFF`、`#667eea`、`#764ba2`、`#4facfe` 等历史主题色（与现品牌冲突）。
- ❌ 不要在图表里写死品牌十六进制（应用 `BRAND.*`，保证跟随变量）。
- ✅ 语义色可保留，不与主色混淆：点赞红 `#ff3b30`/`#ff2d55`、评论蓝 `#4a90d9`、成功绿 `#4cd964`、心动金 `#ffd700` 等属于功能语义，不纳入主色体系。
- ✅ 新增品牌相关颜色时，优先扩展 `--xfjy-*` 变量并在本文档总表登记。

## 7. 参考实现

- 后台变量定义：`xfjy-admin/src/style.css`（`:root`）
- 后台图表读取：`xfjy-admin/src/utils/brand.ts`
- 后台已统一页面：登录页 `views/login/index.vue`、404 `views/error/404.vue`、数据看板 `views/dashboard/index.vue`
- App 变量定义：`xfjy-app/src/uni.scss`、`xfjy-app/src/App.vue`
