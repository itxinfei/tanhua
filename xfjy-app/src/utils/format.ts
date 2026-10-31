/**
 * 数字格式化（对标抖音）
 *
 * 规则：
 * - 小于 1 万：直接显示完整整数，不使用“千”单位（如 999、1000、9999）
 * - 1 万（含）以上：以“万”为单位，保留 1 位小数；小数为 0 时省略
 *   （如 10000 -> 1万，12300 -> 1.2万，10500 -> 1.1万，1235000 -> 123.5万）
 * - 1 亿（含）以上：以“亿”为单位，同样保留 1 位小数
 *   （如 100000000 -> 1亿，150000000 -> 1.5亿）
 */
export function formatCount(value: number | string | null | undefined): string {
  if (value === null || value === undefined || value === "") return "0";
  const n = Number(value);
  if (!Number.isFinite(n)) return "0";

  const num = Math.floor(n);
  if (num < 10000) return String(num);

  if (num < 100000000) {
    const rounded = Math.round((num / 10000) * 10) / 10;
    // 四舍五入后若进位到 1 亿（如 99999999 -> 10000 万），改用“亿”显示
    if (rounded >= 10000) {
      const y = Math.round((num / 100000000) * 10) / 10;
      return `${Number.isInteger(y) ? y : y.toFixed(1)}亿`;
    }
    return `${Number.isInteger(rounded) ? rounded : rounded.toFixed(1)}万`;
  }

  const rounded = Math.round((num / 100000000) * 10) / 10;
  return `${Number.isInteger(rounded) ? rounded : rounded.toFixed(1)}亿`;
}
