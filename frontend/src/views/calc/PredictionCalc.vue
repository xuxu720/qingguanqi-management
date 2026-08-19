<template>
  <div class="calc-page">
    <!-- 参数输入 -->
    <el-card class="input-card">
      <el-form :inline="true">
        <el-form-item label="管线">
          <el-select v-model="pipelineId" placeholder="选择管线" @change="onPipelineChange" style="width: 200px">
            <el-option v-for="p in pipelines" :key="p.id" :label="p.name" :value="p.id!" />
          </el-select>
        </el-form-item>
        <el-form-item label="发球站">
          <el-select v-model="fromStationId" placeholder="选择发球站" :disabled="!pipelineId" style="width: 200px">
            <el-option v-for="s in stations" :key="s.id" :label="s.name" :value="s.id!" />
          </el-select>
        </el-form-item>
        <el-form-item label="收球站">
          <el-select v-model="toStationId" placeholder="选择收球站" :disabled="!fromStationId" style="width: 200px">
            <el-option v-for="s in toStationOptions" :key="s.id" :label="s.name" :value="s.id!" />
          </el-select>
        </el-form-item>
      </el-form>

      <el-divider />

      <el-form :inline="true" label-width="120px">
        <template v-if="isGas">
          <el-form-item label="出站压力(MPa)">
            <el-input-number v-model="outletPressure" :min="0.01" :precision="4" />
          </el-form-item>
          <el-form-item label="进站压力(MPa)">
            <el-input-number v-model="inletPressure" :min="0.01" :precision="4" />
          </el-form-item>
          <el-form-item label="输气量(10⁴Nm³)">
            <el-input-number v-model="gasFlowRate" :min="0.01" :precision="4" />
          </el-form-item>
        </template>
        <template v-else>
          <el-form-item label="排量(m³/h)">
            <el-input-number v-model="displacement" :min="0.01" :precision="2" />
          </el-form-item>
        </template>
        <el-form-item label="发出时间">
          <el-date-picker v-model="dispatchTime" type="datetime" placeholder="选择发出时间" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :disabled="!canCalculate" :loading="calcLoading" @click="handleCalc">
            开始计算
          </el-button>
        </el-form-item>
      </el-form>

      <el-alert v-if="pipelineId && pipeline" :type="isGas ? 'warning' : 'primary'" :closable="false" show-icon style="width: 240px">
        {{ isGas ? '气体管道 — 内径/压力/输气量模式' : '液体管道 — 管容/排量模式' }}
      </el-alert>
    </el-card>

    <!-- 计算结果 -->
    <el-card v-if="results.length > 0" class="result-card">
      <template #header>
        <span>计算结果</span>
      </template>

      <el-descriptions :column="4" border style="margin-bottom: 20px">
        <el-descriptions-item label="总站间距">{{ summary.totalDistance }} km</el-descriptions-item>
        <el-descriptions-item label="总运行时长">{{ summary.totalRunning }} h</el-descriptions-item>
        <el-descriptions-item label="平均速度">{{ summary.avgSpeed }} km/h</el-descriptions-item>
        <el-descriptions-item label="预计到达时间">{{ fmtTime(summary.arrivalTime) }}</el-descriptions-item>
      </el-descriptions>

      <el-table :data="results" stripe>
        <el-table-column prop="fromStationName" label="起始站" width="120" />
        <el-table-column prop="toStationName" label="到达站" width="120" />
        <el-table-column label="站间距(km)" width="110">
          <template #default="{ row }">{{ fmt(row.distance) }}</template>
        </el-table-column>
        <el-table-column v-if="isGas" label="平均压力(MPa)" width="110">
          <template #default="{ row }">{{ fmt(row.avgPressure) }}</template>
        </el-table-column>
        <el-table-column v-if="isGas" label="压缩因子" width="100">
          <template #default="{ row }">{{ fmt(row.compressFactor, 6) }}</template>
        </el-table-column>
        <el-table-column label="运行时长(h)" width="110">
          <template #default="{ row }">{{ fmt(row.runningTime, 4) }}</template>
        </el-table-column>
        <el-table-column :label="isGas ? '理论速度(km/h)' : '清管器速度(km/h)'" width="140">
          <template #default="{ row }">{{ fmt(isGas ? row.theoreticalSpeed : row.pigSpeed, 4) }}</template>
        </el-table-column>
        <el-table-column label="预计到达时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.estimatedArrivalTime) }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card v-else-if="!pipelineId">
      <el-empty description="请选择管线、发球站和收球站，输入参数后点击计算" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { pipelineApi } from '@/api/pipeline'
import { stationApi } from '@/api/station'
import { calcApi } from '@/api/calc'
import type { Pipeline, Station, SegmentCalcResult, GasCalcResult } from '@/types'

const pipelines = ref<Pipeline[]>([])
const stations = ref<Station[]>([])
const pipelineId = ref<number>()
const pipeline = computed(() => pipelines.value.find(p => p.id === pipelineId.value))
const isGas = computed(() => pipeline.value?.mediumType === '气体')

const fromStationId = ref<number>()
const toStationId = ref<number>()

const toStationOptions = computed(() => {
  if (!fromStationId.value) return stations.value
  const idx = stations.value.findIndex(s => s.id === fromStationId.value)
  if (idx === -1) return stations.value
  return stations.value.slice(idx + 1)
})

watch(fromStationId, () => {
  toStationId.value = undefined
})

// 液体参数
const displacement = ref(100)
// 气体参数
const outletPressure = ref(4.0)
const inletPressure = ref(2.0)
const gasFlowRate = ref(3200)

const dispatchTime = ref<Date>(new Date())
const calcLoading = ref(false)

interface ResultRow {
  fromStationName: string
  toStationName: string
  distance: number
  avgPressure?: number
  compressFactor?: number
  runningTime: number
  pigSpeed?: number
  theoreticalSpeed?: number
  estimatedArrivalTime: string
}

const results = ref<ResultRow[]>([])

const canCalculate = computed(() =>
  pipelineId.value && fromStationId.value && toStationId.value && dispatchTime.value &&
  (isGas.value ? (outletPressure.value && inletPressure.value && gasFlowRate.value) : displacement.value > 0)
)

const summary = computed(() => {
  if (results.value.length === 0) return { totalDistance: 0, totalRunning: 0, avgSpeed: 0, arrivalTime: '' }
  let totalDist = 0, totalRun = 0
  for (const r of results.value) {
    totalDist += r.distance
    totalRun += r.runningTime
  }
  return {
    totalDistance: totalDist.toFixed(4),
    totalRunning: totalRun.toFixed(4),
    avgSpeed: totalRun > 0 ? (totalDist / totalRun).toFixed(4) : '0',
    arrivalTime: results.value[results.value.length - 1].estimatedArrivalTime,
  }
})

function fmt(v: number | undefined | null, scale = 4) {
  if (v == null) return '-'
  return Number(v).toFixed(scale)
}

function formatLocalISO(d: Date) {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function fmtTime(t: string) {
  return t.replace('T', ' ').substring(0, 19)
}

onMounted(async () => {
  try {
    const res = await pipelineApi.list()
    pipelines.value = res.data.data
  } catch { /* */ }
})

async function onPipelineChange() {
  fromStationId.value = undefined
  toStationId.value = undefined
  results.value = []
  if (!pipelineId.value) { stations.value = []; return }
  try {
    const res = await stationApi.getByPipeline(pipelineId.value)
    stations.value = res.data.data.sort((a, b) => a.sortOrder - b.sortOrder)
  } catch { /* */ }
}

async function handleCalc() {
  if (!pipelineId.value || !fromStationId.value || !toStationId.value || !dispatchTime.value) return
  calcLoading.value = true
  try {
    const time = formatLocalISO(dispatchTime.value)
    if (isGas.value) {
      const res = await calcApi.gasPipeline(
        pipelineId.value, fromStationId.value, toStationId.value,
        outletPressure.value, inletPressure.value, gasFlowRate.value, time,
      )
      results.value = res.data.data.map((r: GasCalcResult) => ({
        fromStationName: r.distance > 0 ? '' : '',
        ...r,
        pigSpeed: undefined,
      }))
      // Re-map with station names — need to annotate properly
      // gas result doesn't have station names, we infer from stations array
      const startIdx = stations.value.findIndex(s => s.id === fromStationId.value)
      results.value = res.data.data.map((r: GasCalcResult, i: number) => ({
        fromStationName: stations.value[startIdx + i]?.name ?? '',
        toStationName: stations.value[startIdx + i + 1]?.name ?? '',
        distance: Number(r.distance),
        avgPressure: Number(r.avgPressure),
        compressFactor: Number(r.compressFactor),
        runningTime: Number(r.runningTime),
        theoreticalSpeed: Number(r.theoreticalSpeed),
        estimatedArrivalTime: r.estimatedArrivalTime,
      }))
    } else {
      const res = await calcApi.liquidPipeline(
        pipelineId.value, fromStationId.value, toStationId.value, displacement.value, time,
      )
      results.value = res.data.data.map((r: SegmentCalcResult) => ({
        fromStationName: r.fromStationName,
        toStationName: r.toStationName,
        distance: Number(r.distance),
        runningTime: Number(r.runningTime),
        pigSpeed: Number(r.pigSpeed),
        estimatedArrivalTime: r.estimatedArrivalTime,
      }))
    }
    if (results.value.length === 0) {
      ElMessage.warning('该站点为管线末站，无下游管段可计算')
    } else {
      ElMessage.success('计算完成')
    }
  } catch {
    // handled by interceptor
  } finally {
    calcLoading.value = false
  }
}
</script>

<style scoped>
.input-card { margin-bottom: 16px; }
</style>
