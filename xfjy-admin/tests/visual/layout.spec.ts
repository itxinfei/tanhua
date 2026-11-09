import { expect, test, type Page } from '@playwright/test'

/**
 * 页面骨架视觉基线。
 *
 * - 公开页（登录页）始终执行，无需任何凭证。
 * - 需登录页仅在提供了 E2E_ADMIN_TOKEN 时执行；否则 skip，避免把"登录页截图"误当业务页基线。
 * - 不在仓库写入任何真实账号/口令；token 通过环境变量注入（见 README.md）。
 */

const AUTH_TOKEN = process.env.E2E_ADMIN_TOKEN
const AUTH_ROLE = process.env.E2E_ADMIN_ROLE || 'SUPER_ADMIN'

/** 冻结动画，避免过渡帧让 diff 抖动；同时固定字体渲染 */
const FREEZE_CSS = `
  *, *::before, *::after {
    transition: none !important;
    animation: none !important;
    animation-duration: 0s !important;
    caret-color: transparent !important;
  }
  html { scroll-behavior: auto !important; }
  body { font-family: 'Microsoft YaHei', 'PingFang SC', sans-serif !important; }
`

async function seedAuth(page: Page) {
  await page.addInitScript(
    ([token, role]) => {
      localStorage.setItem('xfjy_admin_token', token)
      localStorage.setItem(
        'xfjy_admin_info',
        JSON.stringify({ id: 1, username: 'e2e', nickname: 'E2E', avatar: '', role })
      )
    },
    [AUTH_TOKEN as string, AUTH_ROLE]
  )
}

async function assertNoHorizontalScroll(page: Page) {
  const overflow = await page.evaluate(() => {
    const el = document.scrollingElement || document.documentElement
    return el.scrollWidth - el.clientWidth
  })
  expect(overflow, '页面出现横向滚动条').toBeLessThanOrEqual(1)
}

test.describe('公开页', () => {
  test('登录页', async ({ page }) => {
    await page.goto('/login', { waitUntil: 'networkidle' })
    await page.addStyleTag({ content: FREEZE_CSS })
    await expect(page).toHaveScreenshot('login.png', { fullPage: true })
    await assertNoHorizontalScroll(page)
  })
})

test.describe('业务页（需 E2E_ADMIN_TOKEN）', () => {
  test.skip(!AUTH_TOKEN, '未提供 E2E_ADMIN_TOKEN，跳过需登录页面的视觉基线')

  const pages = [
    { name: 'user-list', path: '/user/list' }, // 列表页：PageHeader + SearchBar + TableCard
    { name: 'notice-list', path: '/operation/notice' }, // 列表页：另一套表格+表单组合
    { name: 'review-workbench', path: '/audit/review' }, // 详情/表单混合页
  ]

  for (const p of pages) {
    test(`业务页 ${p.name}`, async ({ page }) => {
      await seedAuth(page)
      await page.goto(p.path, { waitUntil: 'networkidle' })
      await page.addStyleTag({ content: FREEZE_CSS })
      await expect(page).toHaveScreenshot(`${p.name}.png`, { fullPage: true })
      await assertNoHorizontalScroll(page)
    })
  }

  test('业务页 dashboard 数据看板（动态时间元素 mask）', async ({ page }) => {
    await seedAuth(page)
    await page.goto('/dashboard', { waitUntil: 'networkidle' })
    await page.addStyleTag({ content: FREEZE_CSS })
    // ECharts canvas 入场动画与 useCountUp 数字滚动走 requestAnimationFrame，等待播完后再截图
    await page.waitForTimeout(1500)
    // 倒计时每秒递减、「更新于」随轮询变化、顶栏铃铛角标取后端待审数，均属预期内动态内容，
    // 用 mask 排除出像素比对；不用 page.clock 冻结：setFixedTime 会让 rAF 时间戳恒定，
    // 图表/数字滚动卡在初始帧。30s 快刷新轮询在截图窗口（约 2s）内不会触发，数据本身保持稳定
    await expect(page).toHaveScreenshot('dashboard.png', {
      fullPage: true,
      mask: [
        page.locator('.dash-toolbar__meta', { hasText: '后刷新' }),
        page.locator('.dash-toolbar__meta', { hasText: '更新于' }),
        page.locator('.todo-badge'),
      ],
    })
    await assertNoHorizontalScroll(page)
  })
})
