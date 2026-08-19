<template>
  <div ref="chartRef" style="width: 100%; height: 400px" />
</template>

<script setup lang="ts">
import { ref, onMounted, watch, nextTick } from 'vue'
import * as echarts from 'echarts'
import type { Station, TrackingRecordVO } from '@/types'

const props = defineProps<{
  stations: Station[]
  tracking: TrackingRecordVO[]
  fromStationId: number
  toStationId: number
}>()

const chartRef = ref<HTMLElement>()
let chart: echarts.ECharts | null = null

async function initChart() {
  await nextTick()
  if (!chartRef.value) return
  if (!chart) {
    chart = echarts.init(chartRef.value)
  }

  const sorted = [...props.stations].sort((a, b) => a.sortOrder - b.sortOrder)

  const data: [number, number][] = sorted.map(s => [
    parseFloat(String(s.mileage ?? 0)),
    parseFloat(String(s.elevation ?? 0)),
  ])
  const names = sorted.map(s => s.name)

  // 找到发球站和收球站在排序列表中的索引
  const fromIdx = sorted.findIndex(s => s.id === props.fromStationId)
  const toIdx = sorted.findIndex(s => s.id === props.toStationId)

  // 构建到达状态映射
  const reachedIds = new Set<number>()
  props.tracking.forEach(t => { if (t.actualArrivalTime) reachedIds.add(t.stationId) })

  // 各站点颜色：发球站=蓝色，已到达=绿色，下一站=橙色，其他=灰色
  const markerColors = sorted.map((s, i) => {
    if (i < fromIdx || i > toIdx) return '#dcdfe6' // 不在本次作业范围内
    if (s.id === props.fromStationId) return '#409EFF' // 发球站
    if (reachedIds.has(s.id!)) return '#67c23a' // 已到达
    // 第一个未到达的（紧接最后一个已到达之后）
    const lastReachedIdx = sorted.findLastIndex((st, idx) =>
      idx >= fromIdx && idx <= toIdx && reachedIds.has(st.id!))
    if (i === lastReachedIdx + 1) return '#e6a23c' // 下一站
    return '#909399' // 待到达
  })

  const xMin = data.length > 0 ? Math.min(...data.map(d => d[0])) : 0
  const xMax = data.length > 0 ? Math.max(...data.map(d => d[0])) : 1
  const yMin = data.length > 0 ? Math.min(...data.map(d => d[1])) : 0
  const yMax = data.length > 0 ? Math.max(...data.map(d => d[1])) : 1
  const xPadding = Math.max((xMax - xMin) * 0.1, 1)
  const yPadding = Math.max((yMax - yMin) * 0.15, 10)

  chart.setOption({
    title: {
      text: '管线纵断面 — 清管器位置',
      left: 'center',
      top: 10,
    },
    tooltip: {
      trigger: 'axis',
      formatter: (params: any) => {
        const p = Array.isArray(params) ? params[0] : params
        const idx = p.dataIndex
        const status = reachedIds.has(sorted[idx].id!) ? '已到达' :
          (markerColors[idx] === '#e6a23c' ? '当前位置' : '待到达')
        return `${names[idx]}<br/>里程: ${data[idx][0]} km<br/>高程: ${data[idx][1]} m<br/>状态: ${status}`
      },
    },
    xAxis: {
      type: 'value',
      name: '累计里程 (km)',
      nameLocation: 'center',
      nameTextStyle: { fontSize: 13 },
      nameGap: 35,
      min: Math.max(0, xMin - xPadding),
      max: xMax + xPadding,
      axisLine: { show: true, lineStyle: { color: '#333' } },
      axisTick: { show: true },
      axisLabel: { show: true, fontSize: 12 },
      splitLine: { show: false },
    },
    yAxis: {
      type: 'value',
      name: '高程 (m)',
      nameTextStyle: { fontSize: 13 },
      min: yMin - yPadding,
      max: yMax + yPadding,
      axisLine: { show: true, lineStyle: { color: '#333' } },
      axisTick: { show: true },
      axisLabel: { show: true, fontSize: 12 },
      splitLine: { show: true, lineStyle: { type: 'dashed', color: '#e0e0e0' } },
    },
    series: [
      {
        type: 'line',
        data,
        smooth: false,
        lineStyle: { color: '#409EFF', width: 2 },
        symbol: 'circle',
        symbolSize: (val: any, params: any) => {
          const idx = params.dataIndex
          return markerColors[idx] === '#e6a23c' ? 14 : 8
        },
        itemStyle: {
          color: (params: any) => markerColors[params.dataIndex],
          borderColor: (params: any) =>
            markerColors[params.dataIndex] === '#e6a23c' ? '#e6a23c' : '#fff',
          borderWidth: (params: any) =>
            markerColors[params.dataIndex] === '#e6a23c' ? 4 : 1,
        },
        label: {
          show: true,
          position: 'top',
          formatter: (p: any) => {
            const idx = p.dataIndex
            const s = sorted[idx]
            let suffix = ''
            if (reachedIds.has(s.id!)) suffix = ' ✓'
            else if (markerColors[idx] === '#e6a23c') suffix = ' ←'
            return s.name + suffix
          },
          fontSize: 11,
          color: '#333',
          distance: 14,
        },
      },
    ],
    grid: { left: 65, right: 40, top: 50, bottom: 55 },
  })

  chart.resize()
}

onMounted(() => initChart())
watch(() => [props.stations, props.tracking], () => initChart(), { deep: true })
</script>
