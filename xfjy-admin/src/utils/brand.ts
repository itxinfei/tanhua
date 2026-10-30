/**
 * 品牌色集中读取工具（修改点）
 * 与 src/style.css 中 --xfjy-* / --el-color-primary-* 同源：
 * 主色变更时，只需改 style.css 一处，图表等非 CSS 场景即可跟随。
 */
function readCssVar(name: string, fallback: string): string {
  if (typeof window === 'undefined' || !window.document) return fallback
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return value || fallback
}

/** 把十六进制色转为带透明度的 rgba，便于图表渐变区域着色 */
export function hexToRgba(hex: string, alpha: number): string {
  const clean = hex.replace('#', '')
  const r = parseInt(clean.slice(0, 2), 16)
  const g = parseInt(clean.slice(2, 4), 16)
  const b = parseInt(clean.slice(4, 6), 16)
  return 'rgba(' + r + ', ' + g + ', ' + b + ', ' + alpha + ')'
}

export interface BrandColors {
  primary: string
  primaryDeep: string
  primarySoft: string
  primaryLight7: string
  primaryLight9: string
  violet: string
}

export function getBrandColors(): BrandColors {
  return {
    primary: readCssVar('--xfjy-primary', '#ff5e9c'),
    primaryDeep: readCssVar('--xfjy-primary-deep', '#b13bff'),
    primarySoft: readCssVar('--xfjy-primary-soft', '#ff8fb1'),
    primaryLight7: readCssVar('--el-color-primary-light-7', '#ffcfe1'),
    primaryLight9: readCssVar('--el-color-primary-light-9', '#ffeff5'),
    violet: readCssVar('--xfjy-primary-violet', '#8a5cf6'),
  }
}
