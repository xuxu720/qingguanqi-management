<template>
  <div class="station-page">
    <!-- 管线选择 -->
    <el-card class="search-card">
      <el-form :inline="true">
        <el-form-item label="所属管线">
          <el-select v-model="selectedPipelineId" placeholder="请选择管线" @change="onPipelineChange" style="width: 220px">
            <el-option v-for="p in pipelines" :key="p.id" :label="p.name" :value="p.id!" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="success" :disabled="!selectedPipelineId" @click="openCreate">新增站点</el-button>
          <el-button :disabled="!selectedPipelineId" @click="batchUpdateSort">保存排序</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 站点表格（可拖拽排序） -->
    <el-card v-if="selectedPipelineId">
      <el-table :data="stations" stripe v-loading="loading" row-key="id" ref="tableRef">
        <el-table-column label="拖拽" width="50">
          <template #default>
            <span class="drag-handle" style="cursor: grab; font-size: 18px; color: #909399; user-select: none;">⋮⋮</span>
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="序号" width="60" />
        <el-table-column label="站点名称" width="180">
          <template #default="{ row }">
            <el-input v-model="row.name" size="small" @blur="updateRow(row)" />
          </template>
        </el-table-column>
        <el-table-column label="站点类型" width="120">
          <template #default="{ row }">
            <el-select v-model="row.stationType" size="small" @change="updateRow(row)">
              <el-option label="站场" value="站场" />
              <el-option label="阀室" value="阀室" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="累计里程(km)" width="150">
          <template #default="{ row }">
            <el-input-number v-model="row.mileage" :min="0" :precision="4" size="small"
              @blur="updateRow(row)" style="width: 130px" />
          </template>
        </el-table-column>
        <el-table-column label="高程(m)" width="130">
          <template #default="{ row }">
            <el-input-number v-model="row.elevation" :precision="2" size="small"
              @blur="updateRow(row)" style="width: 110px" />
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="140">
          <template #default="{ row }">
            <el-input v-model="row.remark" size="small" @blur="updateRow(row)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ $index }">
            <el-button size="small" type="danger" @click="handleDelete($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    <el-card v-else>
      <el-empty description="请先选择一条管线" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import Sortable, { type SortableEvent } from 'sortablejs'
import { pipelineApi } from '@/api/pipeline'
import { stationApi } from '@/api/station'
import type { Pipeline, Station } from '@/types'

const pipelines = ref<Pipeline[]>([])
const selectedPipelineId = ref<number>()
const stations = ref<Station[]>([])
const loading = ref(false)
const tableRef = ref()

let sortable: Sortable | null = null

onMounted(async () => {
  const res = await pipelineApi.list()
  pipelines.value = res.data.data
})

async function onPipelineChange() {
  if (!selectedPipelineId.value) return
  loading.value = true
  try {
    const res = await stationApi.getByPipeline(selectedPipelineId.value)
    stations.value = res.data.data.sort((a, b) => a.sortOrder - b.sortOrder)
    await nextTick()
    initSortable()
  } finally {
    loading.value = false
  }
}

function initSortable() {
  if (sortable) sortable.destroy()
  const el = tableRef.value?.$el?.querySelector('tbody')
  if (!el) return
  sortable = Sortable.create(el, {
    handle: '.drag-handle',
    animation: 150,
    onEnd(evt: SortableEvent) {
      const { oldIndex, newIndex } = evt
      if (oldIndex !== undefined && newIndex !== undefined) {
        const item = stations.value.splice(oldIndex, 1)[0]
        stations.value.splice(newIndex, 0, item)
      }
    },
  })
}

async function updateRow(row: Station) {
  try {
    if (row.id) {
      await stationApi.update({ ...row })
    } else {
      const res = await stationApi.create({ ...row })
      row.id = res.data.data.id
      ElMessage.success('站点已创建')
    }
  } catch { /* handled */ }
}

async function batchUpdateSort() {
  for (let i = 0; i < stations.value.length; i++) {
    stations.value[i].sortOrder = i
  }
  // 确保所有站点都已入库（无 id 的先创建）
  await Promise.all(stations.value.map(async (s) => {
    if (s.id) {
      await stationApi.update(s)
    } else {
      const res = await stationApi.create(s)
      s.id = res.data.data.id
    }
  }))
  ElMessage.success('排序已保存')
  onPipelineChange()
}

async function openCreate() {
  const maxOrder = stations.value.length > 0
    ? Math.max(...stations.value.map(s => s.sortOrder)) : -1
  // 先入库获取 id，再加入列表
  const res = await stationApi.create({
    pipelineId: selectedPipelineId.value!,
    name: '新站点',
    stationType: '阀室',
    mileage: 0,
    sortOrder: maxOrder + 1,
  })
  stations.value.push(res.data.data)
  ElMessage.success('站点已创建')
}

async function handleDelete(index: number) {
  const s = stations.value[index]
  if (s.id) {
    await stationApi.delete(s.id)
  }
  stations.value.splice(index, 1)
  ElMessage.success('已删除')
}
</script>
