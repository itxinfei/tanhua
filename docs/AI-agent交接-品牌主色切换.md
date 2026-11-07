# AI Agent 交接文档：品牌主色切换（必读）

> 面向对象：在本仓库（mytanhua / 心飞交友 xfjy）工作的所有 AI Agent（Claude Code、CodeBuddy、Qoder、JoyCode、AtomCode、WorkBuddy 等）及人类开发者。
> 交接日期：2026-11-02
> 变更性质：**全站品牌主色切换，已完成并验证通过**。后续任何 UI/样式工作必须以本文件和
> `xfjy-app/src/uni.scss`（SCSS Token）、`xfjy-app/src/App.vue`（CSS 变量）为准，禁止回退到旧色。
> （原 `docs/brand-colors.md` 已随 2026-11-03 文档整理删除，不再作为权威文档。）

## 一、这次做了什么（背景与决策）

1. 产品定位：陌生人交友 App，对标探探/Tinder 的滑卡匹配形态。
2. 旧主色 `#ff5e9c` 是**冷调玫粉（色相 337°，带紫味，饱和度 63%）**，语义偏"甜美/少女/女性社区"，与陌生人交友需要的"心动、暧昧、热情"不符，且对男性用户偏软。
3. 经色值实测与竞品（Tinder 品牌色 `#fe3c72` + `#ff655b`）对标，选定**方案 A**：主色切换为暖调玫红 `#fe3c72`，并以玫红→珊瑚渐变作为主品牌渐变。
4. 紫色系（`#b13bff` 等）**保留为辅助色**，但不再进入主品牌渐变，不得作为主色使用。

## 二、新品牌色板（唯一权威口径）

| 角色 | 色值 | app 端变量（uni.scss） | admin 端变量（style.css） |
| --- | --- | --- | --- |
| 品牌主色 | `#fe3c72` | `$uni-color-primary` / `--color-primary` | `--xfjy-primary` / `--el-color-primary` |
| 珊瑚红橙 | `#ff655b` | `$uni-color-coral` / `--color-coral` | `--xfjy-coral` |
| 主色深变体 | `#c2185b` | `$uni-color-primary-deep` / `--color-primary-text` | （浅底文字场景同值） |
| 主渐变（135°） | `linear-gradient(135deg, #fe3c72, #ff655b)` | `$uni-gradient-primary` / `--gradient-brand` | `--xfjy-gradient-brand` |
| 主渐变（90°） | `linear-gradient(90deg, #fe3c72, #ff655b)` | `$uni-gradient-primary-h` / `--gradient-brand-h` | — |
| 辅助深紫 | `#b13bff` | `--color-primary-deep` | `--xfjy-primary-deep` |
| 柔粉 | `#ff8fb1` | `--color-primary-soft` | `--xfjy-primary-soft` |
| 浅底 | `#ffeff5` | `--bg-icon-soft` | `--el-color-primary-light-9` 对应 `#ffe7ed` |

> 深色模式（`@media (prefers-color-scheme: dark)`）：主色为 `#ff8eb9`，渐变为 `linear-gradient(135deg, #f9578f, #ff7a6b)`，以 `App.vue` 实际配置为准。

### 无障碍对比度（WCAG，已实测，禁止再凭印象给数字）

| 色值 | 白底对比度 | 浅粉 `#ffeff5` 底对比度 |
| --- | --- | --- |
| `#fe3c72` | 3.44:1 | 3.10:1 |
| `#ff655b` | 2.89:1 | 2.60:1 |
| `#c2185b` | ≥5.4:1 | ≥4.5:1 |

口径：普通文本 AA 需 4.5:1；大号/粗体文本 AA Large 需 3:1。

## 三、强制规则（红线，违反即返工）

1. **禁止使用旧色 `#ff5e9c`**（含大小写变体），它已废弃；CHANGELOG 中的历史记录和本文件的说明性引用除外，不得新增任何生效引用。
2. **禁止硬编码品牌色**：颜色、渐变、圆角、字号一律引用 `uni.scss` / `style.css` 变量；旧蓝 `#409EFF`、`#667eea`、`#1677ff` 等历史/近似色同样禁止。
3. **浅底小字/小图标必须用深变体 `#c2185b`**，禁止在 `#ffeff5` 等浅底上用主色写普通字号文本；主色仅用于按钮大字（满足 AA Large）和图标。
4. **主品牌渐变只能是玫红→珊瑚**，禁止恢复旧的紫→粉渐变（`#b13bff → #...`）；紫色仅用于辅助场景。
5. **跨页面同组件必须一致**：按钮优先复用 `components/a11y-button.vue` 或 uview-plus `u-button`，禁止重新造按钮样式。
6. 图表（ECharts 等）禁止写死品牌十六进制，admin 端走 `getBrandColors()` 运行时读取变量。
7. 语义色（点赞红、评论蓝、成功绿、VIP 金等）属于功能语义，维持原色，不与品牌主色混淆。

## 四、本次改动文件清单（2026-11-02 批次）

### xfjy-app（uni-app 端）

- `src/uni.scss`：主色改 `#fe3c72`，新增珊瑚、深变体、两个方向主渐变 Token
- `src/App.vue`：浅色/深色模式 CSS 变量、品牌渐变、主色投影同步
- `src/theme.json`：两处 `tabSelectedColor` 改 `#FE3C72`
- `src/components/AppTabbar.vue`：局部 `--color-primary` 更新
- `src/pages/video/index.vue`：点赞/飞心图标色更新
- `src/pages/circle/publish.vue`：发布按钮主色、active 浅底文字改 `#c2185b`
- `src/pages/mine/index.vue`：三处菜单图标色更新
- `src/pages/kefu/feedback.vue`、`src/pages/kefu/chat.vue`、`src/pages/mine/change-phone.vue`、`src/pages/mine/blacklist.vue`、`src/pages/match/nearby.vue`：无障碍注释中的色值与对比度数字更新
- `scripts/gen-tabbar-icons.ps1`：ACTIVE 改 `#fe3c72`，新增 CORAL，发布键渐变改玫红→珊瑚
- `src/static/tabbar/*.png`：已重绘（match/circle/video/mine 的 active 图标 + publish 发布键）

### xfjy-admin（管理端）

- `src/style.css`：主色、珊瑚、主渐变、侧边栏 active、图表色、Element Plus 覆盖色阶（light-3/5/7/8/9、dark-2 按白/黑混合重算）
- `src/utils/brand.ts`：fallback 默认值同步

### 文档

- `docs/brand-colors.md`：整体重写（该文件已于 2026-11-03 删除，色值以 `uni.scss` / `App.vue` 为准）
- `docs/AI-agent交接-品牌主色切换.md`：本文件（新建）
- `docs/brand-color-compare.png`：色卡对比图（该图已于 2026-11-03 删除，不再维护）
- `CLAUDE.md`：主色规范与红线更新（已收为 `@AGENTS.md` 引用）
- `docs/xfjy-app设计文档.md`、`docs/H5-tabbar机制与全局底栏.md`、`docs/需求文档/开发规范.md`、`docs/需求文档/需求文档.md`、`docs/需求文档/视频页抖音对标设计.md`：色值与渐变描述同步（上述旧路径已随文档重构删除，现行路径见 `docs/需求文档/03-开发规范.md`）
- `CHANGELOG.md`：已新增 2026-11-02 变更条目

## 五、常用操作指引

### 1. 改了品牌色后如何重绘 tabbar 图标

```powershell
cd D:\MyCode\mytanhua\xfjy-app
pwsh scripts/gen-tabbar-icons.ps1
```

输出目录：`src/static/tabbar/`。注意：脚本只生成 match/circle/video/mine 四个业务图标和中央发布键 publish.png。

### 2. 提交前验证（必须执行）

```powershell
cd D:\MyCode\mytanhua\xfjy-app
npm run type-check      # vue-tsc --noEmit，必须 EXIT=0
```

并自行用 Grep 确认没有新增旧色/硬编码：

```
搜索关键词：ff5e9c（应只剩 CHANGELOG 历史与废弃说明）
```

### 3. 实际效果确认

- H5：`npm run dev:h5`；小程序：`npm run dev:mp-weixin` 等。
- README/文档截图必须来自真实运行环境实测，禁止占位图。

## 六、已知遗留事项

1. `src/static/tabbar/chat.png`、`chat-active.png` 经核实**无任何配置引用**，属废弃文件，本次未删除；确认后可人工清理。
2. 两个工程（app/admin）仍以"相同十六进制"手动保持色值一致，尚未抽公共 npm 包；改色时两边都要改。
3. 深色模式仍属 WIP：变量层已就绪，部分页面硬编码色待逐步迁移到变量。

## 七、验证记录（2026-11-02）

- 全局 Grep `ff5e9c`：生效代码零残留，仅 CHANGELOG 历史条目与废弃说明保留
- `npm run type-check`：通过（EXIT=0，无错误输出）
- tabbar 图标与发布键：已重绘并目视确认
- 对比度数据：经 Python 按 WCAG 相对亮度公式复算，已纠正早期错误口径

---

**一句话给后续 Agent：主色是 `#fe3c72`，渐变是玫红→珊瑚，浅底小字用 `#c2185b`，旧色 `#ff5e9c` 不许再用，所有颜色走变量。**
