/**
 * 数据看板 ECharts 配置工厂
 *
 * 统一收口 tooltip / 坐标轴 / 网格风格，颜色一律取自 brand 色板（读 CSS 变量），
 * 各卡片只负责传数据，不重复拼 option。
 */
import * as echarts from 'echarts'
import { getBrandColors, hexToRgba } from '@/utils/brand'

type Option = echarts.EChartsOption

const BRAND = getBrandColors()

/** 深色 tooltip（与旧看板保持一致，TS 字符串不受 stylelint 约束） */
function tooltipBase() {
  return {
    backgroundColor: 'rgba(31, 35, 41, 0.92)',
    borderWidth: 0,
    padding: [8, 12],
    textStyle: { color: '#ffffff', fontSize: 12 },
  }
}

function categoryXAxis(dates: string[]) {
  return {
    type: 'category' as const,
    boundaryGap: false,
    data: dates,
    axisLine: { lineStyle: { color: BRAND.chartGrid } },
    axisTick: { show: false },
    axisLabel: { fontSize: 11, color: BRAND.chartInk3, hideOverlap: true },
  }
}

function valueYAxis(name?: string) {
  return {
    type: 'value' as const,
    name,
    nameTextStyle: { color: BRAND.chartInk3, fontSize: 10, padding: [0, 0, 0, -20] },
    axisLine: { show: false },
    axisTick: { show: false },
    splitLine: { lineStyle: { color: BRAND.chartGrid } },
    axisLabel: { fontSize: 11, color: BRAND.chartInk3 },
  }
}

export interface LineSeries {
  name: string
  data: Array<number | null>
  color: string
}

/** 主趋势：单/双序列平滑面积图（注册、DAU、匹配 tab） */
export function areaLineOption(dates: string[], series: LineSeries[]): Option {
  return {
    tooltip: {
      ...tooltipBase(),
      trigger: 'axis',
      axisPointer: { type: 'line', lineStyle: { color: BRAND.chartInk3, type: 'dashed' } },
    },
    legend: series.length > 1 ? legendBottom(series.map((s) => s.name)) : undefined,
    grid: { left: 8, right: 16, top: series.length > 1 ? 32 : 12, bottom: series.length > 1 ? 28 : 8, containLabel: true },
    xAxis: categoryXAxis(dates),
    yAxis: valueYAxis(),
    series: series.map((s) => ({
      name: s.name,
      type: 'line',
      smooth: true,
      showSymbol: false,
      symbol: 'circle',
      symbolSize: 6,
      connectNulls: true,
      data: s.data,
      lineStyle: { width: 2, color: s.color },
      itemStyle: { color: s.color },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: hexToRgba(s.color, 0.22) },
          { offset: 1, color: hexToRgba(s.color, 0.01) },
        ]),
      },
    })),
  }
}

/** 付费趋势：订单数柱状（左轴）+ 收入折线（右轴，元） */
export function revenueTrendOption(dates: string[], orders: number[], amountsYuan: number[]): Option {
  return {
    tooltip: {
      ...tooltipBase(),
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      valueFormatter: undefined,
      formatter(params: unknown) {
        const rows = params as Array<{ marker: string; seriesName: string; value: number; dataIndex: number }>
        if (!rows.length) return ''
        const title = dates[rows[0].dataIndex]
        return rows
          .map((r) => {
            const text = r.seriesName === '收入(元)'
              ? `¥${formatYuan(r.value)}`
              : `${r.value} 单`
            return `${r.marker}${r.seriesName}：${text}`
          })
          .join('<br/>')
          .concat('', `<div style="opacity:.7;margin-top:2px">${title}</div>`)
      },
    },
    legend: legendBottom(['支付订单', '收入(元)']),
    grid: { left: 8, right: 8, top: 32, bottom: 28, containLabel: true },
    xAxis: {
      type: 'category',
      data: dates,
      axisLine: { lineStyle: { color: BRAND.chartGrid } },
      axisTick: { show: false },
      axisLabel: { fontSize: 11, color: BRAND.chartInk3, hideOverlap: true },
    },
    yAxis: [
      {
        type: 'value',
        axisLine: { show: false },
        axisTick: { show: false },
        splitLine: { lineStyle: { color: BRAND.chartGrid } },
        axisLabel: { fontSize: 11, color: BRAND.chartInk3 },
      },
      {
        type: 'value',
        axisLine: { show: false },
        axisTick: { show: false },
        splitLine: { show: false },
        axisLabel: {
          fontSize: 11,
          color: BRAND.chartInk3,
          formatter: (v: number) => `¥${formatYuan(v)}`,
        },
      },
    ],
    series: [
      {
        name: '支付订单',
        type: 'bar',
        barMaxWidth: 18,
        itemStyle: { color: BRAND.chartPalette[6], borderRadius: [3, 3, 0, 0] },
        data: orders,
      },
      {
        name: '收入(元)',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 2, color: BRAND.chartPalette[0] },
        itemStyle: { color: BRAND.chartPalette[0] },
        data: amountsYuan,
      },
    ],
  }
}

export interface DonutItem {
  name: string
  value: number
  color?: string
}

/** 环形图（性别、支付方式）；centerTitle/centerValue 为中心文案 */
export function donutOption(data: DonutItem[], centerTitle?: string, centerValue?: string): Option {
  return {
    tooltip: { ...tooltipBase(), trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: {
      orient: 'horizontal',
      bottom: 0,
      icon: 'circle',
      itemWidth: 6,
      itemHeight: 6,
      itemGap: 8,
      textStyle: { fontSize: 11, color: BRAND.chartInk2 },
    },
    title:
      centerValue !== undefined
        ? {
            text: centerValue,
            subtext: centerTitle ?? '',
            left: 'center',
            top: '34%',
            textStyle: { fontSize: 22, fontWeight: 600, color: BRAND.chartInk },
            subtextStyle: { fontSize: 11, color: BRAND.chartInk3 },
          }
        : undefined,
    series: [
      {
        type: 'pie',
        radius: ['56%', '76%'],
        center: ['50%', '44%'],
        avoidLabelOverlap: false,
        label: { show: false },
        labelLine: { show: false },
        data: data.map((d) => ({
          name: d.name,
          value: d.value,
          itemStyle: d.color ? { color: d.color } : undefined,
        })),
      },
    ],
  }
}

/** 横向条形图（年龄段、城市 TOP10） */
export function horizontalBarOption(
  categories: string[],
  values: number[],
  color: string,
  unit = '人',
): Option {
  return {
    tooltip: {
      ...tooltipBase(),
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter(params: unknown) {
        const rows = params as Array<{ name: string; value: number }>
        if (!rows.length) return ''
        return `${rows[0].name}：${rows[0].value.toLocaleString('zh-CN')} ${unit}`
      },
    },
    grid: { left: 8, right: 40, top: 8, bottom: 8, containLabel: true },
    xAxis: {
      type: 'value',
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { lineStyle: { color: BRAND.chartGrid } },
      axisLabel: { fontSize: 11, color: BRAND.chartInk3 },
    },
    yAxis: {
      type: 'category',
      inverse: true,
      data: categories,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { fontSize: 11, color: BRAND.chartInk2 },
    },
    series: [
      {
        type: 'bar',
        barMaxWidth: 14,
        data: values,
        itemStyle: { color, borderRadius: [0, 3, 3, 0] },
        label: {
          show: true,
          position: 'right',
          fontSize: 11,
          color: BRAND.chartInk2,
          formatter: (p) => Number(p.value as number).toLocaleString('zh-CN'),
        },
      },
    ],
  }
}

export interface StackSeries {
  name: string
  data: number[]
  color: string
}

/** 堆叠柱状（审核/举报每日序列） */
export function stackedBarOption(dates: string[], series: StackSeries[]): Option {
  return {
    tooltip: { ...tooltipBase(), trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: legendBottom(series.map((s) => s.name)),
    grid: { left: 8, right: 16, top: 28, bottom: 28, containLabel: true },
    xAxis: {
      type: 'category',
      data: dates,
      axisLine: { lineStyle: { color: BRAND.chartGrid } },
      axisTick: { show: false },
      axisLabel: { fontSize: 11, color: BRAND.chartInk3, hideOverlap: true },
    },
    yAxis: valueYAxis(),
    series: series.map((s) => ({
      name: s.name,
      type: 'bar',
      stack: 'total',
      barMaxWidth: 22,
      itemStyle: { color: s.color },
      emphasis: { focus: 'series' as const },
      data: s.data,
    })),
  }
}

/**
 * 内容安全趋势：每日新增（平滑折线，灰色表示流入量）+ 处理结果堆叠柱
 * 审核：handled=[通过, 拒绝]；举报：handled=[已处理, 已驳回]
 */
export function moderationTrendOption(
  dates: string[],
  created: number[],
  handled: StackSeries[],
  createdName = '新增',
): Option {
  return {
    tooltip: { ...tooltipBase(), trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: legendBottom([createdName, ...handled.map((s) => s.name)]),
    grid: { left: 8, right: 16, top: 28, bottom: 28, containLabel: true },
    xAxis: {
      type: 'category',
      data: dates,
      axisLine: { lineStyle: { color: BRAND.chartGrid } },
      axisTick: { show: false },
      axisLabel: { fontSize: 11, color: BRAND.chartInk3, hideOverlap: true },
    },
    yAxis: valueYAxis(),
    series: [
      ...handled.map((s) => ({
        name: s.name,
        type: 'bar' as const,
        stack: 'handled',
        barMaxWidth: 18,
        itemStyle: { color: s.color },
        emphasis: { focus: 'series' as const },
        data: s.data,
      })),
      {
        name: createdName,
        type: 'line' as const,
        smooth: true,
        showSymbol: false,
        z: 2,
        lineStyle: { width: 2, color: BRAND.chartInk2, type: 'dashed' as const },
        itemStyle: { color: BRAND.chartInk2 },
        data: created,
      },
    ],
  }
}

/** KPI 卡迷你 sparkline（无坐标轴） */
export function sparklineOption(data: Array<number | null>, color: string): Option {
  return {
    animationDuration: 400,
    grid: { left: 0, right: 0, top: 4, bottom: 0 },
    xAxis: { type: 'category', show: false, boundaryGap: false, data: data.map((_, i) => i) },
    yAxis: { type: 'value', show: false, min: (v: { min: number }) => Math.floor(v.min * 0.9) },
    tooltip: { show: false },
    series: [
      {
        type: 'line',
        smooth: true,
        showSymbol: false,
        connectNulls: true,
        data,
        lineStyle: { width: 1.6, color },
        itemStyle: { color },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: hexToRgba(color, 0.2) },
            { offset: 1, color: hexToRgba(color, 0.01) },
          ]),
        },
      },
    ],
  }
}

function legendBottom(names: string[]) {
  return {
    data: names,
    bottom: 0,
    icon: 'circle',
    itemWidth: 6,
    itemHeight: 6,
    itemGap: 8,
    textStyle: { fontSize: 11, color: BRAND.chartInk2 },
  }
}

/** 元金额紧凑格式化（tooltip / 轴用） */
export function formatYuan(v: number): string {
  if (Math.abs(v) >= 10000) {
    return `${(v / 10000).toFixed(1)}万`
  }
  return v.toLocaleString('zh-CN', { maximumFractionDigits: 0 })
}
