/**
 * 校验 pages.json 的 tabBar.list 与 src/config/tab-config.ts 的 TABS 是否一致。
 *
 * 两处配置无法在运行时自动联动（pages.json 是编译期配置），故用本脚本在构建前兜底：
 * 页面路径、文字、图标（未选中/选中）、顺序有任何差异即非零退出，并打印「哪一项、期望值 vs 实际值」。
 *
 * 用法：node --experimental-strip-types scripts/check-tabbar.mjs
 * 说明：MP 端多一个「发布」tab（pages.json 里被 #ifdef MP-WEIXIN 注释包裹），比对时先剔除。
 */
import { readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const PAGES_JSON = path.join(ROOT, "src", "pages.json");
const TAB_CONFIG = path.join(ROOT, "src", "config", "tab-config.ts");

/** pages.json 含 JSON 注释与条件编译注释，先剥离注释再解析（保留字符串内的 // 等字符） */
function stripJsonComments(text) {
  let out = "";
  let inString = false;
  let inLineComment = false;
  let inBlockComment = false;

  for (let i = 0; i < text.length; i += 1) {
    const ch = text[i];
    const next = text[i + 1];

    if (inLineComment) {
      if (ch === "\n") {
        inLineComment = false;
        out += ch;
      }
      continue;
    }
    if (inBlockComment) {
      if (ch === "*" && next === "/") {
        inBlockComment = false;
        i += 1;
      }
      continue;
    }
    if (inString) {
      out += ch;
      if (ch === "\\") {
        out += next ?? "";
        i += 1;
      } else if (ch === '"') {
        inString = false;
      }
      continue;
    }

    if (ch === '"') {
      inString = true;
      out += ch;
      continue;
    }
    if (ch === "/" && next === "/") {
      inLineComment = true;
      i += 1;
      continue;
    }
    if (ch === "/" && next === "*") {
      inBlockComment = true;
      i += 1;
      continue;
    }
    out += ch;
  }
  return out;
}

/** 图标路径归一化：pages.json 用 static/...，tab-config 用 /static/... */
const normalizeIcon = (value) => (typeof value === "string" ? value.replace(/^\/+/, "") : "");

const { TABS, PUBLISH_PAGE } = await import(pathToFileURL(TAB_CONFIG).href);

let pages;
try {
  pages = JSON.parse(stripJsonComments(await readFile(PAGES_JSON, "utf8")));
} catch (error) {
  console.error(`✗ 无法解析 ${path.relative(ROOT, PAGES_JSON)}：${error.message}`);
  process.exit(1);
}

const rawList = pages?.tabBar?.list;
if (!Array.isArray(rawList)) {
  console.error("✗ pages.json 缺少 tabBar.list");
  process.exit(1);
}

const diffs = [];
const addDiff = (message) => diffs.push(message);
const expectedLabel = "期望（tab-config.ts）";
const actualLabel = "实际（pages.json）";

const publishEntries = rawList.filter((item) => item.pagePath === PUBLISH_PAGE);
if (publishEntries.length !== 1) {
  addDiff(`发布 tab：${expectedLabel} 恰好 1 项 ${PUBLISH_PAGE}，${actualLabel} ${publishEntries.length} 项`);
}

const compareList = rawList.filter((item) => item.pagePath !== PUBLISH_PAGE);

const maxLength = Math.max(TABS.length, compareList.length);
for (let i = 0; i < maxLength; i += 1) {
  const expected = TABS[i];
  const actual = compareList[i];
  const label = `第 ${i + 1} 项`;

  if (!expected) {
    addDiff(`${label}：${expectedLabel} 无此项，${actualLabel} 多出 "${actual.text ?? ""}"（${actual.pagePath ?? ""}）`);
    continue;
  }
  if (!actual) {
    addDiff(`${label}：${expectedLabel} "${expected.text}"（${expected.pagePath}），${actualLabel} 缺此项`);
    continue;
  }

  const fields = [
    ["pagePath", expected.pagePath, actual.pagePath],
    ["text", expected.text, actual.text],
    ["icon", normalizeIcon(expected.icon), normalizeIcon(actual.iconPath)],
    ["iconActive", normalizeIcon(expected.iconActive), normalizeIcon(actual.selectedIconPath)],
  ];
  for (const [field, exp, act] of fields) {
    if (String(exp) !== String(act)) {
      addDiff(`${label} ${field}：${expectedLabel} "${exp}"，${actualLabel} "${act}"（页面 ${actual.pagePath || "?"}）`);
    }
  }
}

if (diffs.length > 0) {
  console.error(`✗ tabbar 配置不一致（${diffs.length} 处）：`);
  for (const diff of diffs) console.error(`  - ${diff}`);
  console.error("  请同步修改 src/config/tab-config.ts 与 src/pages.json 的 tabBar.list 后重试。");
  process.exit(1);
}

console.log(`✓ tabbar 一致：${TABS.length} 个主 tab + MP 端发布 tab（${PUBLISH_PAGE}）`);
process.exit(0);
