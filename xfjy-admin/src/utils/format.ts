/**
 * 后台通用时间格式化工具
 * 统一各页面重复实现的时间格式化逻辑
 */

function pad(n: number): string {
  return String(n).padStart(2, '0')
}

function formatDate(d: Date): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/**
 * 完整日期时间：支持时间戳 / ISO 字符串 / 已格式化字符串
 * 输出 "YYYY-MM-DD HH:mm"，无法解析时原样返回
 */
export function formatDateTime(val: string | number | null | undefined): string {
  if (val == null || val === '') return '-'
  if (typeof val === 'number') {
    const d = new Date(val)
    return Number.isNaN(d.getTime()) ? String(val) : formatDate(d)
  }

  const str = val.trim()
  // 纯数字字符串：10 位秒级 / 13 位毫秒级时间戳
  if (/^\d{10}$/.test(str)) {
    return formatDate(new Date(Number(str) * 1000))
  }
  if (/^\d{13}$/.test(str)) {
    return formatDate(new Date(Number(str)))
  }
  // 已是 "YYYY-MM-DD HH:mm[:ss]"，裁剪到分钟
  if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}(:\d{2})?$/.test(str)) {
    return str.slice(0, 16)
  }
  // ISO 8601（含 T / 时区 / 毫秒）
  if (/^\d{4}-\d{2}-\d{2}T/.test(str)) {
    const d = new Date(str)
    if (!Number.isNaN(d.getTime())) return formatDate(d)
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

/**
 * 金额分转元：分（整数）→ 元（保留两位小数）
 * 如 3000 → "30.00"，用于 VIP 套餐售价、支付订单金额展示
 */
export function formatAmount(amount: number): string {
  if (amount == null || Number.isNaN(amount)) return '0.00'
  return (amount / 100).toFixed(2)
}
