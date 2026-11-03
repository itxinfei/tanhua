/**
 * stylelint 基线门禁
 *
 * 作用：warning 总数和单文件数量都不得超过 stylelint-baseline.json 里登记的值。
 * 只用 stylelint 的 Node API，不解析 CLI 文本，避免 Windows / Linux 路径格式差异。
 *
 * 用法：
 *   npm run lint:style:gate             校验（超基线则 exit 1）
 *   npm run lint:style:gate -- --update 把当前结果写回基线（仅在本轮确实减少了 warning 时使用）
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import stylelint from 'stylelint';

const ROOT = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const BASELINE = path.join(ROOT, 'stylelint-baseline.json');
const update = process.argv.includes('--update');

const result = await stylelint.lint({
  files: 'src/**/*.{vue,scss,css}',
  cwd: ROOT,
  configFile: path.join(ROOT, 'stylelint.config.mjs'),
});

const counts = new Map();
let total = 0;
for (const r of result.results) {
  const rel = path.relative(ROOT, r.source).replace(/\\/g, '/');
  const n = (r.warnings || []).length;
  if (n > 0) counts.set(rel, n);
  total += n;
}

const sorted = Object.fromEntries([...counts.entries()].sort((a, b) => b[1] - a[1]));

if (update) {
  fs.writeFileSync(
    BASELINE,
    JSON.stringify(
      { note: 'stylelint warning 基线。只允许下调：修复批次完成时同步改小对应文件的数字。', total, files: sorted },
      null,
      2,
    ) + '\n',
    'utf8',
  );
  console.log(`[update] baseline written: total=${total}, files=${counts.size}`);
  process.exit(0);
}

const base = JSON.parse(fs.readFileSync(BASELINE, 'utf8'));
const errors = [];
if (total > base.total) errors.push(`总数 ${total} 超过基线 ${base.total}`);
for (const [file, n] of Object.entries(sorted)) {
  const allowed = base.files[file] ?? 0;
  if (n > allowed) errors.push(`${file}: ${n} 条，超过基线 ${allowed} 条（新增文件基线为 0）`);
}
for (const file of Object.keys(base.files)) {
  if (!counts.has(file)) console.log(`[down] ${file}: 基线 ${base.files[file]} -> 0，可下调基线`);
}

console.log(`[style-baseline] 当前 ${total} 条 / 基线 ${base.total} 条`);
if (errors.length) {
  errors.forEach((e) => console.error(`  ✖ ${e}`));
  process.exit(1);
}
console.log('[style-baseline] OK：未超过基线');
