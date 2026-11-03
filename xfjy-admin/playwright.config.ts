import { defineConfig, devices } from '@playwright/test'
import { execFileSync } from 'node:child_process'

/**
 * xfjy-admin 视觉回归配置
 * 用法：
 *   npm run test:visual:update   # 首次/有意改版后重建基线
 *   npm run test:visual          # 日常比对
 * 登录态：设置 E2E_ADMIN_TOKEN 后才会跑需登录的页面（见 tests/visual/README.md）
 *
 * 端口：不再写死 5174（同机 xfjy-app 的 uni dev 常占用该端口），
 * 启动前动态取一个空闲端口；也可用 E2E_PORT 指定，或用 E2E_BASE_URL 直接复用已有服务。
 */

function pickFreePort(): number {
  if (process.env.E2E_PORT) return Number(process.env.E2E_PORT)
  const probe =
    'const s=require("node:net").createServer();' +
    's.listen(0,"127.0.0.1",()=>{process.stdout.write(String(s.address().port));s.close()})'
  try {
    return Number(execFileSync(process.execPath, ['-e', probe], { encoding: 'utf8' }).trim())
  } catch {
    return 5188
  }
}

const PORT = pickFreePort()
const BASE_URL = process.env.E2E_BASE_URL || `http://localhost:${PORT}`

export default defineConfig({
  testDir: './tests/visual',
  outputDir: './test-results',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  use: {
    baseURL: BASE_URL,
    locale: 'zh-CN',
    timezoneId: 'Asia/Shanghai',
    deviceScaleFactor: 1,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    // 默认用本机 Chrome（免下载 Chromium，也避开 %LOCALAPPDATA%\ms-playwright 的 EPERM）；
    // 可用 E2E_CHANNEL=msedge 切换，非 Windows 环境回落为 Playwright 自带浏览器。
    channel:
      (process.env.E2E_CHANNEL as 'chrome' | 'msedge' | undefined) ||
      (process.platform === 'win32' ? 'chrome' : undefined),
  },
  expect: {
    toHaveScreenshot: {
      maxDiffPixelRatio: 0.01,
      threshold: 0.2,
      animations: 'disabled',
    },
  },
  // 基线默认放仓库；设置 E2E_SNAPSHOT_DIR 时改写到指定目录（用于「先出候选基线、评审后再入库」）
  snapshotPathTemplate: process.env.E2E_SNAPSHOT_DIR
    ? `${process.env.E2E_SNAPSHOT_DIR.replace(/\\/g, '/')}/{testFilePath}/{arg}-{projectName}-{platform}{ext}`
    : undefined,
  projects: [
    {
      name: 'desktop-1440',
      use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } },
    },
    {
      name: 'desktop-1280',
      use: { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 800 } },
    },
  ],
  // 给了 E2E_BASE_URL 就直接用外部服务，否则在动态端口拉起服务。
  // 默认用 build + vite preview 而不是 vite dev：dev 的依赖预构建会写 node_modules/.vite，
  // 在部分 Windows 环境会 EPERM 卡住；preview 读的是产物，也不需要预构建。
  webServer: process.env.E2E_BASE_URL
    ? undefined
    : {
        command:
          process.env.E2E_WEBSERVER_CMD ||
          `npm run build && npx vite preview --configLoader runner --host 127.0.0.1 --port ${PORT} --strictPort`,
        url: BASE_URL,
        // 复用已在运行的服务（本机 CI=true 时 Playwright 默认会拒绝复用，故显式判断）
        reuseExistingServer: process.env.E2E_NO_REUSE !== '1',
        timeout: 180_000,
      },
})
