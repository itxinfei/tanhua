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
  chartPrimary: string
  chartSecondary: string
  chartThird: string
  chartInk2: string
  chartInk3: string
  chartGrid: string
}

export function getBrandColors(): BrandColors {
  return {
    primary: readCssVar('--xfjy-primary', '#ff5e9c'),
    primaryDeep: readCssVar('--xfjy-primary-deep', '#b13bff'),
    primarySoft: readCssVar('--xfjy-primary-soft', '#ff8fb1'),
    primaryLight7: readCssVar('--el-color-primary-light-7', '#ffcfe1'),
    primaryLight9: readCssVar('--el-color-primary-light-9', '#ffeff5'),
    violet: readCssVar('--xfjy-primary-violet', '#8a5cf6'),
    // CVD 安全图表色（与品牌主色区分度足够，通过色盲模拟验证）
    chartPrimary: readCssVar('--xfjy-chart-1', '#ff5e9c'),    // 玫红
    chartSecondary: readCssVar('--xfjy-chart-2', '#7c3aed'),  // CVD 安全紫（比 #b13bff 更深）
    chartThird: readCssVar('--xfjy-chart-3', '#00b894'),      // 青绿
    // 图表墨色/网格：与 style.css 的 --xfjy-chart-ink-* / --xfjy-chart-grid 同源
    chartInk2: readCssVar('--xfjy-chart-ink-2', '#4e5969'),
    chartInk3: readCssVar('--xfjy-chart-ink-3', '#86909c'),
    chartGrid: readCssVar('--xfjy-chart-grid', '#f2f3f5'),
  }
}
