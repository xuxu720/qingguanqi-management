<template>
  <div class="segment-page">
    <!-- 管线选择 -->
    <el-card class="search-card">
      <el-form :inline="true">
        <el-form-item label="所属管线">
          <el-select v-model="selectedPipelineId" placeholder="请选择管线" @change="onPipelineChange" style="width: 220px">
            <el-option v-for="p in pipelines" :key="p.id" :label="p.name" :value="p.id!" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="selectedPipeline">
          <el-tag>{{ selectedPipeline.mediumType === '气体' ? '气体管道 → 内径模式' : '液体管道 → 管容模式' }}</el-tag>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 管段列表 -->
    <el-card v-if="selectedPipelineId">
      <el-table :data="segments" stripe v-loading="loading">
        <el-table-column label="管段" width="200">
          <template #default="{ row }">
            {{ row.fromStationName }} → {{ row.toStationName }}
          </template>
        </el-table-column>
        <el-table-column label="站间距(km)" width="160">
          <template #default="{ row }">
            <el-input-number v-model="row.distance" :min="0" :precision="4" size="small" style="width: 140px"
              @blur="saveRow(row)" />
            <el-button v-if="row.autoDistance !== row.distance" size="small" text type="primary"
              @click="row.distance = row.autoDistance; saveRow(row)">
              还原
            </el-button>
          </template>
        </el-table-column>
        <el-table-column v-if="isLiquid" label="单位管容(m³/km)" width="170">
          <template #default="{ row }">
            <el-input-number v-model="row.unitCapacity" :min="0" :precision="4" size="small" style="width: 150px"
              @blur="saveRow(row)" />
          </template>
        </el-table-column>
        <el-table-column v-else label="管道内径(mm)" width="160">
          <template #default="{ row }">
            <el-input-number v-model="row.innerDiameter" :min="0" :precision="2" size="small" style="width: 140px"
              @blur="saveRow(row)" />
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="140">
          <template #default="{ row }">
            <el-input v-model="row.remark" size="small" @blur="saveRow(row)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ $index, row }">
            <el-button size="small" type="danger" @click="handleDelete($index, row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="segments.length === 0 && !loading"
        description="暂无站点数据，请先在站点管理中为管线添加至少2个站点" />
    </el-card>
    <el-card v-else>
      <el-empty description="请先选择一条管线" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pipelineApi } from '@/api/pipeline'
import { stationApi } from '@/api/station'
import { segmentApi } from '@/api/segment'
import type { Pipeline, Station, PipelineSegment } from '@/types'

interface SegmentRow {
  id?: number
  pipelineId: number
  fromStationId: number
  toStationId: number
  fromStationName: string
  toStationName: string
  distance: number
  autoDistance: number
  unitCapacity?: number
  innerDiameter?: number
  remark?: string
}

const pipelines = ref<Pipeline[]>([])
const selectedPipelineId = ref<number>()
const selectedPipeline = ref<Pipeline>()
const segments = ref<SegmentRow[]>([])
const loading = ref(false)
const isLiquid = computed(() => selectedPipeline.value?.mediumType === '液体')

// Load pipeline list on mount
pipelineApi.list().then(res => pipelines.value = res.data.data)

async function onPipelineChange() {
  if (!selectedPipelineId.value) return
  loading.value = true
  try {
    selectedPipeline.value = pipelines.value.find(p => p.id === selectedPipelineId.value)

    const [stationRes, segmentRes] = await Promise.all([
      stationApi.getByPipeline(selectedPipelineId.value),
      segmentApi.getByPipeline(selectedPipelineId.value),
    ])

    const stations = stationRes.data.data.sort((a, b) => a.sortOrder - b.sortOrder)
    const existingSegments = segmentRes.data.data

    // Build segment rows from adjacent station pairs
    const rows: SegmentRow[] = []
    for (let i = 0; i < stations.length - 1; i++) {
      const from = stations[i]
      const to = stations[i + 1]
      const autoDist = Number(to.mileage) - Number(from.mileage)

      const existing = existingSegments.find(
        s => s.fromStationId === from.id && s.toStationId === to.id,
      )

      rows.push({
        id: existing?.id,
        pipelineId: selectedPipelineId.value!,
        fromStationId: from.id!,
        toStationId: to.id!,
        fromStationName: from.name,
        toStationName: to.name,
        distance: existing?.distance ?? autoDist,
        autoDistance: autoDist,
        unitCapacity: existing?.unitCapacity,
        innerDiameter: existing?.innerDiameter,
        remark: existing?.remark,
      })
    }
    segments.value = rows
  } finally {
    loading.value = false
  }
}

async function saveRow(row: SegmentRow) {
  try {
    if (row.id) {
      await segmentApi.update({
        id: row.id,
        pipelineId: row.pipelineId,
        fromStationId: row.fromStationId,
        toStationId: row.toStationId,
        distance: row.distance,
        unitCapacity: row.unitCapacity,
        innerDiameter: row.innerDiameter,
        remark: row.remark,
      })
    } else {
      const res = await segmentApi.create({
        pipelineId: row.pipelineId,
        fromStationId: row.fromStationId,
        toStationId: row.toStationId,
        distance: row.distance,
        unitCapacity: row.unitCapacity,
        innerDiameter: row.innerDiameter,
        remark: row.remark,
      })
      row.id = res.data.data.id
    }
  } catch { /* handled */ }
}

async function handleDelete(index: number, row: SegmentRow) {
  await ElMessageBox.confirm('确定删除该管段？', '警告', { type: 'warning' })
  if (row.id) {
    await segmentApi.delete(row.id)
  }
  segments.value.splice(index, 1)
  ElMessage.success('已删除')
}
</script>
