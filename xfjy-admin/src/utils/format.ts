/**
 * 后台通用时间格式化工具
 * 统一各页面重复实现的时间格式化逻辑
 */

function pad(n: number): string {
  return String(n).padStart(2, '0')
}

/**
 * 完整日期时间：支持时间戳 / ISO 字符串 / 已格式化字符串
 * 输出 "YYYY-MM-DD HH:mm"，无法解析时原样返回
 */
export function formatDateTime(val: string | number | null | undefined): string {
  if (val == null || val === '') return '-'
  if (typeof val === 'number') {
    const d = new Date(val)
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
  }
  return val
}

/**
 * 紧凑时间：字符串 "YYYY-MM-DD HH:mm:ss" → "MM-DD HH:mm"
 * 今天则只显示 "HH:mm"，无法解析时原样返回
 */
export function formatShortTime(val: string | number | null | undefined): string {
  if (val == null || val === '') return '-'
  const str = typeof val === 'number' ? formatDateTime(val) : val
  const match = str.match(/(\d{4})-(\d{2})-(\d{2})\s+(\d{2}:\d{2})/)
  if (!match) return str
  const now = new Date()
  const isToday =
    Number(match[1]) === now.getFullYear() &&
    Number(match[2]) === now.getMonth() + 1 &&
    Number(match[3]) === now.getDate()
  return isToday ? match[4] : `${match[2]}-${match[3]} ${match[4]}`
}