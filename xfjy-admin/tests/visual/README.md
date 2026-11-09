# xfjy-admin 视觉回归（Playwright）

## 命令
```bash
npm run test:visual          # 与基线比对
npm run test:visual:update   # 重建基线（有意改版后才用）
```
基线图位于 `tests/visual/layout.spec.ts-snapshots/`，需随代码提交；`test-results/`、`playwright-report/` 是产物，已在根 `.gitignore` 忽略。

## 基线重录记录
- **2026-11-03 登录页（因设计变更重录）**：登录页由**单栏改为左右分栏**，`login-desktop-1440-win32.png`、`login-desktop-1280-win32.png` 按新设计重录；视口尺寸不变（1440×900、1280×800）。
  - 重录方式：先用 `E2E_SNAPSHOT_DIR` 指向候选目录生成候选基线，人工评审通过后覆盖 `tests/visual/layout.spec.ts-snapshots/`，再**不加 `--update-snapshots`** 裸跑确认 passed。
  - 重新生成命令：
    ```powershell
    $env:E2E_SNAPSHOT_DIR="<候选目录>"; npm run test:visual:update        # 生成候选基线
    Copy-Item "<候选目录>\layout.spec.ts\*.png" tests\visual\layout.spec.ts-snapshots\ -Force   # 评审后入库
    Remove-Item Env:E2E_SNAPSHOT_DIR; npm run test:visual                # 裸跑确认
    ```

## 端口（不写死 5174）
同机常驻 `xfjy-app` 的 uni dev 会占用 5174，因此配置在启动前**动态取一个空闲端口**：

| 环境变量 | 作用 |
|---|---|
| 不设置 | 动态取空闲端口，自动 `npm run build` + `vite preview` 拉起（不写 `node_modules`，避开 dev 依赖预构建的 EPERM） |
| `E2E_WEBSERVER_CMD` | 自定义启动命令（例如改用 `npm run dev -- --port <port>`） |
| `E2E_PORT` | 指定端口（仍会拉起服务） |
| `E2E_BASE_URL` | 直接复用已有服务，不拉起服务（此时忽略 `E2E_PORT`） |
| `E2E_NO_REUSE=1` | 禁止复用同端口/同 URL 上已在运行的服务 |
| `E2E_SNAPSHOT_DIR` | 把基线读写到指定目录（默认写仓库），用于「先出候选基线，评审后再入库」 |

不要为了跑测试去杀别人的 dev 进程，换端口即可。

## 浏览器（不下载 Chromium）
Windows 默认使用本机 Chrome（`channel: 'chrome'`），无需 `npx playwright install`，也避开
`%LOCALAPPDATA%\ms-playwright` 的 `EPERM: mkdir __dirlock`：

| 环境变量 | 作用 |
|---|---|
| 不设置（Windows） | 本机 Chrome |
| `E2E_CHANNEL=chrome` / `msedge` | 显式指定本机浏览器 |
| `PLAYWRIGHT_BROWSERS_PATH=<可写目录>` | 非 Windows 或必须用自带 Chromium 时，把浏览器装到可写目录 |


## 覆盖范围
| 用例 | 页面 | 是否需要登录 | 用途 |
|---|---|---|---|
| 登录页 | `/login` | 否 | 公开页基线，无需任何凭证即可跑 |
| 用户列表 | `/user/list` | 是 | 校验 `PageHeader + SearchBar + TableCard` 骨架 |
| 公告管理 | `/operation/notice` | 是 | 校验列表 + 表单混合页 |
| 内容审核 | `/audit/review` | 是 | 校验工作台/详情分栏 |
| 数据看板 | `/dashboard` | 是 | 校验 KPI/漏斗/图表/空态；动态时间元素 mask 后比对 |

已覆盖视口：1440×900、1280×800。每个用例额外断言「无横向滚动条」。

### 数据看板用例的动态内容处理
看板页 `useDashboardData` 有每秒递减的「N s 后刷新」倒计时、30s 快刷新与 5min 慢刷新轮询，
直接截图会因时间文本每秒变化而持续 diff。用例对三个动态元素使用 `toHaveScreenshot` 的 `mask` 选项排除出像素比对：
两个 `.dash-toolbar__meta`（倒计时、更新于时间）与顶栏铃铛角标 `.todo-badge`（取后端待审数），
而**不用 `page.clock` 冻结**：实测 `setFixedTime` 会让 `requestAnimationFrame` 时间戳恒定，
ECharts 入场动画与 useCountUp 数字滚动卡在初始帧。截图在导航后约 2s 内完成，
30s 轮询不会触发，数据本身保持稳定；截图前等待 1.5s 让 canvas 动画播完。

## 登录态（不写任何真实账号）
需登录的用例仅在设置了 `E2E_ADMIN_TOKEN` 时执行，否则 skip。token 通过环境变量注入，
不会写入仓库。获取方式（任选其一）：

1. 浏览器手动登录后台 → DevTools → Application → Local Storage → 复制 `xfjy_admin_token` 的值；
2. 或调用 `POST /admin/login` 拿返回的 `data.token`。

临时运行：
```powershell
$env:E2E_ADMIN_TOKEN="<粘贴 token>"
$env:E2E_ADMIN_ROLE="SUPER_ADMIN"   # 可选，默认 SUPER_ADMIN
npm run test:visual
```

`.env.e2e.example` 只是字段说明模板（Playwright 不自动读取 `.env`，需自行 export）。
**禁止**把 token、账号、密码写进该模板或任何入库文件。

## 已知限制
- 需登录页面在无真实后端时，API 会失败，页面会走到 401 分支（`src/api/request.ts` 会清空 token），
  因此这类用例必须配合可用的后端环境；否则只跑登录页基线。
- 中文字体渲染受系统影响，`maxDiffPixelRatio` 设为 1%；更换操作系统后需重建基线。
- 若本机 `%LOCALAPPDATA%\ms-playwright` 因权限/占用无法写入（`EPERM: mkdir __dirlock`），
  两种解法：① `$env:E2E_CHANNEL="chrome"`（用本机 Chrome，免下载）；
  ② `$env:PLAYWRIGHT_BROWSERS_PATH="<可写目录>"` 后再执行 `npx playwright install chromium`。
- 若 `test-results/` 被系统拒绝写入（`EPERM` / `Access denied`，目录可能被沙箱或其他进程创建过），
  用 `--output <可写目录>` 覆盖输出目录即可，不影响基线比对。
