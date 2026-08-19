<template>
  <div class="op-page">
    <el-card class="search-card">
      <el-form :inline="true">
        <el-form-item label="管线">
          <el-select v-model="filterPipelineId" placeholder="全部" clearable @change="fetchList" style="width: 180px">
            <el-option v-for="p in pipelines" :key="p.id" :label="p.name" :value="p.id!" />
          </el-select>
        </el-form-item>
        <el-form-item label="作业类型">
          <el-select v-model="filterType" placeholder="全部" clearable @change="fetchList" style="width: 140px">
            <el-option v-for="t in operationTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchList">查询</el-button>
          <el-button type="success" @click="$router.push('/operations/create')">新建作业</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card>
      <el-tabs v-model="activeTab" @tab-change="fetchList">
        <el-tab-pane label="全部" name="" />
        <el-tab-pane label="准备" name="准备" />
        <el-tab-pane label="运行中" name="运行中" />
        <el-tab-pane label="已完成" name="已完成" />
        <el-tab-pane label="异常" name="异常" />
      </el-tabs>

      <el-table :data="list" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column label="管线" width="120">
          <template #default="{ row }">{{ pipelineName(row.pipelineId) }}</template>
        </el-table-column>
        <el-table-column label="清管器" width="140">
          <template #default="{ row }">{{ row.pigName ?? `#${row.pigId}` }}</template>
        </el-table-column>
        <el-table-column prop="operationType" label="作业类型" width="100" />
        <el-table-column label="路线" min-width="180">
          <template #default="{ row }">
            {{ row.fromStationName ?? `#${row.fromStationId}` }} → {{ row.toStationName ?? `#${row.toStationId}` }}
          </template>
        </el-table-column>
        <el-table-column label="发球时间" width="170">
          <template #default="{ row }">{{ row.dispatchTime?.replace('T', ' ') }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260">
          <template #default="{ row }">
            <el-button size="small" @click="$router.push(`/operations/${row.id}`)">详情</el-button>
            <el-dropdown @command="(cmd: string) => handleStatusChange(row, cmd)" style="margin: 0 8px">
              <el-button size="small" type="warning">状态变更</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="运行中">设为运行中</el-dropdown-item>
                  <el-dropdown-item command="已完成">设为已完成</el-dropdown-item>
                  <el-dropdown-item command="异常">设为异常</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-button size="small" type="danger" @click="handleDelete(row.id!)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="page.current"
        v-model:page-size="page.size"
        :total="page.total"
        layout="total, prev, pager, next, sizes"
        :page-sizes="[10, 20, 50]"
        @current-change="fetchList"
        @size-change="fetchList"
        style="margin-top: 16px; justify-content: flex-end;"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { operationApi } from '@/api/operation'
import { pipelineApi } from '@/api/pipeline'
import type { OperationVO, Pipeline } from '@/types'

const operationTypes = ['常规清管', '应急清管']

const activeTab = ref('')
const filterPipelineId = ref<number>()
const filterType = ref('')

const list = ref<OperationVO[]>([])
const loading = ref(false)
const page = reactive({ current: 1, size: 10, total: 0 })

const pipelines = ref<Pipeline[]>([])

function statusTag(status: string) {
  const map: Record<string, string> = { '准备': 'info', '运行中': 'warning', '已完成': 'success', '异常': 'danger' }
  return map[status] || 'info'
}

function pipelineName(id: number) {
  return pipelines.value.find(p => p.id === id)?.name ?? `#${id}`
}

async function fetchList() {
  loading.value = true
  try {
    const res = await operationApi.list({
      pipelineId: filterPipelineId.value,
      status: activeTab.value || undefined,
    })
    let data = res.data.data as OperationVO[]
    if (filterType.value) {
      data = data.filter(o => o.operationType === filterType.value)
    }
    list.value = data
    page.total = data.length
  } finally {
    loading.value = false
  }
}

async function handleStatusChange(row: OperationVO, status: string) {
  try {
    await operationApi.updateStatus(row.id!, status)
    ElMessage.success('状态已变更')
    fetchList()
  } catch { /* */ }
}

async function handleDelete(id: number) {
  await ElMessageBox.confirm('确定删除该作业？关联的跟踪记录也将被删除。', '提示', { type: 'warning' })
  await operationApi.delete(id)
  ElMessage.success('删除成功')
  fetchList()
}

onMounted(async () => {
  const res = await pipelineApi.list()
  pipelines.value = res.data.data
  fetchList()
})
</script>
