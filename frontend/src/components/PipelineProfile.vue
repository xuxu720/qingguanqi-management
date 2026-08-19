<template>
  <div ref="chartRef" style="width: 100%; height: 400px" />
</template>

<script setup lang="ts">
import { ref, onMounted, watch, nextTick } from 'vue'
import * as echarts from 'echarts'
import type { Station } from '@/types'

const props = defineProps<{
  stations: Station[]
  pipelineName?: string
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

  // 确保数值类型正确
  const data: [number, number][] = sorted.map(s => [
    parseFloat(String(s.mileage ?? 0)),
    parseFloat(String(s.elevation ?? 0)),
  ])
  const names = sorted.map(s => s.name)

  const xMin = data.length > 0 ? Math.min(...data.map(d => d[0])) : 0
  const xMax = data.length > 0 ? Math.max(...data.map(d => d[0])) : 1
  const yMin = data.length > 0 ? Math.min(...data.map(d => d[1])) : 0
  const yMax = data.length > 0 ? Math.max(...data.map(d => d[1])) : 1
  const xPadding = Math.max((xMax - xMin) * 0.1, 1)
  const yPadding = Math.max((yMax - yMin) * 0.1, 10)

  chart.setOption({
    title: {
      text: props.pipelineName ? `${props.pipelineName} 纵断面` : '管线纵断面',
      left: 'center',
      top: 10,
    },
    tooltip: {
      trigger: 'axis',
      formatter: (params: any) => {
        const p = Array.isArray(params) ? params[0] : params
        return `${names[p.dataIndex]}<br/>里程: ${data[p.dataIndex][0]} km<br/>高程: ${data[p.dataIndex][1]} m`
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
        itemStyle: { color: '#409EFF' },
        symbol: 'circle',
        symbolSize: 8,
        label: {
          show: true,
          position: 'top',
          formatter: (p: any) => names[p.dataIndex],
          fontSize: 11,
          color: '#333',
          distance: 12,
        },
      },
    ],
    grid: { left: 65, right: 40, top: 50, bottom: 55 },
  })

  chart.resize()
}

onMounted(() => initChart())
watch(() => props.stations, () => initChart(), { deep: true })
</script>
