/**
 * H5 演示 GIF 录制脚本（一次性工具，按 .gitignore 白名单约定以 `_` 前缀不入库）
 *
 * 用法（在 xfjy-app 下）：
 *   $env:XFJY_LOGIN_PHONE="<测试账号手机号>"
 *   node scripts/_record-demo-gif.mjs
 *
 * 流程：登录页真实输入手机号 + 测试验证码 → 真实点击登录 → 首页起录 10fps 逐帧
 *      （首页 1.2s → 圈子 / 视频 / 我的 / 匹配 各 1.2s）。
 * 产出：临时目录逐帧 PNG + manifest.json，再由 ffmpeg 合成 GIF。
 * 说明：全部用 locator.click 触发真实指针事件，不调用 uni.switchTab 等 JS 跳转；
 *      登录阶段不进入帧序列，成片不含手机号。
 */
import { createRequire } from "node:module";
import { mkdir, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";

const require = createRequire("C:/Users/itxinfei/.npm-global/node_modules/");
const { chromium } = require("playwright");

/** 页面地址：端口以实际可访问为准，可由 DEMO_URL 覆盖 */
const BASE = process.env.DEMO_URL || "http://localhost:5175";
/** 登录用测试手机号（仅登录页使用，不进入录制帧） */
const LOGIN_PHONE = process.env.XFJY_LOGIN_PHONE || "";
/** 测试账号固定验证码 */
const LOGIN_CODE = process.env.XFJY_LOGIN_CODE || "123456";
/** 逐帧输出目录 */
const FRAME_DIR = path.join(tmpdir(), "xfjy-demo-frames");
/** 录屏帧率 */
const FPS = 10;
/** 每帧目标间隔（ms） */
const FRAME_INTERVAL = Math.round(1000 / FPS);
/** 每次页面停留时长（ms） */
const HOLD_MS = 1200;

const TAB_SEQUENCE = ["圈子", "视频", "我的", "匹配"];

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

await rm(FRAME_DIR, { recursive: true, force: true });
await mkdir(FRAME_DIR, { recursive: true });

const browser = await chromium.launch({ channel: "chrome", headless: true });
const context = await browser.newContext({
  viewport: { width: 390, height: 844 },
  deviceScaleFactor: 2,
  isMobile: true,
  hasTouch: true,
  userAgent:
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
});
const page = await context.newPage();
await page.goto(`${BASE}/`, { waitUntil: "domcontentloaded", timeout: 60000 });
await page.waitForTimeout(2000);

// ===== 登录（真实 UI 交互，截图不进入 GIF） =====
if (LOGIN_PHONE) {
  await page.locator("input").nth(0).fill(LOGIN_PHONE);
  await page.getByText("获取验证码").first().click();
  await page.waitForTimeout(1200);
  await page.locator("input").nth(1).fill(LOGIN_CODE);
  await page.locator(".agreement").click();
  await page.waitForTimeout(300);
  await page.getByText("登录 / 注册").first().click();
  await page.waitForTimeout(3500);
  const toast = await page.evaluate(() =>
    [...document.querySelectorAll(".uni-toast")].map((n) => n.textContent?.trim()).filter(Boolean).join(" || ")
  );
  console.log("LOGIN:", page.url(), "|", toast || "(无 toast)");
}

// 底部导航可见即代表已进入首页（登录墙会隐藏底栏）
await page.locator(".app-tabbar").first().waitFor({ state: "visible", timeout: 20000 });

// 预热：先真实点一遍各 tab，让列表数据/图片进入内存缓存，避免成片里出现长时间骨架屏
for (const label of ["圈子", "视频", "我的", "匹配"]) {
  await page.locator(".app-tabbar__item", { hasText: label }).first().click({ timeout: 10000 });
  await page.waitForTimeout(label === "匹配" ? 1500 : 3500);
}

await page.waitForTimeout(1500);

const frames = [];
const startedAt = Date.now();
let capturing = true;

/** 逐帧截图循环：JPEG 编码比 PNG 快一倍，才能在 deviceScaleFactor=2 下稳定取到 ~10fps */
async function captureLoop() {
  let index = 0;
  while (capturing) {
    const t0 = Date.now();
    const file = path.join(FRAME_DIR, `frame-${String(index).padStart(4, "0")}.jpg`);
    await page.screenshot({ path: file, type: "jpeg", quality: 85, animations: "allow" });
    frames.push({ file: path.basename(file), at: Date.now() - startedAt });
    index += 1;
    await sleep(Math.max(0, FRAME_INTERVAL - (Date.now() - t0)));
  }
}

const captureTask = captureLoop();

await sleep(HOLD_MS);
for (const label of TAB_SEQUENCE) {
  const item = page.locator(".app-tabbar__item", { hasText: label }).first();
  await item.click({ timeout: 10000 });
  await sleep(HOLD_MS);
}

capturing = false;
await captureTask;
const endedAt = Date.now() - startedAt;
await browser.close();

await writeFile(
  path.join(FRAME_DIR, "manifest.json"),
  JSON.stringify({ fps: FPS, frames: frames.length, durationMs: endedAt }, null, 2)
);

console.log(
  JSON.stringify({ dir: FRAME_DIR, frames: frames.length, durationMs: endedAt, actualFps: +(frames.length / (endedAt / 1000)).toFixed(2) })
);
