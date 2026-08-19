<template>
  <div class="warning-page">
    <el-card class="search-card">
      <el-form :inline="true">
        <el-form-item label="预警类型">
          <el-select v-model="filterType" placeholder="全部" clearable @change="fetchPage" style="width: 140px">
            <el-option v-for="(label, key) in WarningType" :key="key" :label="label" :value="label" />
          </el-select>
        </el-form-item>
        <el-form-item label="预警等级">
          <el-select v-model="filterLevel" placeholder="全部" clearable @change="fetchPage" style="width: 120px">
            <el-option v-for="(label, key) in WarningLevel" :key="key" :label="label" :value="label" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchPage">查询</el-button>
          <el-tag v-if="autoRefresh" type="success" size="small" style="margin-left: 8px; cursor: pointer" @click="autoRefresh = false">
            <el-icon style="vertical-align: middle"><Refresh /></el-icon> 自动刷新中 ({{ countdown }}秒) — 点击暂停
          </el-tag>
          <el-tag v-else type="info" size="small" style="margin-left: 8px; cursor: pointer" @click="autoRefresh = true; countdown = REFRESH_INTERVAL">
            已暂停 — 点击恢复
          </el-tag>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card>
      <div style="margin-bottom: 12px; display: flex; justify-content: space-between; align-items: center">
        <el-tabs v-model="activeTab" @tab-change="fetchPage">
          <el-tab-pane label="全部" name="" />
          <el-tab-pane label="未处理" name="未处理" />
          <el-tab-pane label="已确认" name="已确认" />
          <el-tab-pane label="已关闭" name="已关闭" />
        </el-tabs>
        <div>
          <el-button
            :disabled="selectedIds.length === 0"
            @click="batchConfirm"
          >批量确认</el-button>
          <el-button
            :disabled="selectedIds.length === 0"
            type="warning"
            @click="batchResolveDialog = true"
          >批量关闭</el-button>
        </div>
      </div>

      <el-table
        ref="tableRef"
        :data="list"
        stripe
        v-loading="loading"
        @selection-change="(rows: Warning[]) => selectedIds = rows.map(r => r.id!)"
      >
        <el-table-column type="selection" width="45" />
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="operationId" label="作业ID" width="80" />
        <el-table-column label="预警类型" width="110">
          <template #default="{ row }">
            <el-tag :type="typeTag(row.warningType)" size="small">{{ row.warningType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="等级" width="70">
          <template #default="{ row }">
            <el-tag :type="levelTag(row.level)" size="small" effect="dark">{{ row.level }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="预警内容" min-width="240" show-overflow-tooltip />
        <el-table-column prop="suggestion" label="处置建议" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ row.createTime?.replace('T', ' ') }}</template>
        </el-table-column>
        <el-table-column label="关闭时间" width="170">
          <template #default="{ row }">{{ row.resolvedTime?.replace('T', ' ') || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDetail(row)">详情</el-button>
            <el-button
              v-if="row.status === '未处理'"
              size="small"
              type="primary"
              @click="handleConfirm(row.id!)"
            >确认</el-button>
            <el-button
              v-if="row.status !== '已关闭'"
              size="small"
              type="warning"
              @click="openResolve(row)"
            >关闭</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="page.current"
        v-model:page-size="page.size"
        :total="page.total"
        layout="total, prev, pager, next, sizes"
        :page-sizes="[10, 20, 50]"
        @current-change="fetchPage"
        @size-change="fetchPage"
        style="margin-top: 16px; justify-content: flex-end"
      />
    </el-card>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailDialog.visible" title="预警详情" width="600px">
      <template v-if="detailDialog.warning">
        <el-descriptions :column="2" border size="small" style="margin-bottom: 16px">
          <el-descriptions-item label="预警ID">{{ detailDialog.warning.id }}</el-descriptions-item>
          <el-descriptions-item label="作业ID">
            <el-link type="primary" @click="$router.push(`/operations/${detailDialog.warning.operationId}`)">
              #{{ detailDialog.warning.operationId }}
            </el-link>
          </el-descriptions-item>
          <el-descriptions-item label="预警类型">
            <el-tag :type="typeTag(detailDialog.warning.warningType)" size="small">{{ detailDialog.warning.warningType }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="预警等级">
            <el-tag :type="levelTag(detailDialog.warning.level)" size="small" effect="dark">{{ detailDialog.warning.level }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTag(detailDialog.warning.status)" size="small">{{ detailDialog.warning.status }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detailDialog.warning.createTime?.replace('T', ' ') }}</el-descriptions-item>
          <el-descriptions-item label="关闭时间" :span="detailDialog.warning.resolvedTime ? 1 : 2">
            {{ detailDialog.warning.resolvedTime?.replace('T', ' ') || '—' }}
          </el-descriptions-item>
          <el-descriptions-item v-if="detailDialog.warning.remark" label="备注" :span="2">
            {{ detailDialog.warning.remark }}
          </el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">预警内容</el-divider>
        <el-alert :title="detailDialog.warning.content" :type="alertType(detailDialog.warning.warningType)" :closable="false" style="margin-bottom: 12px" />

        <el-divider content-position="left">处置建议</el-divider>
        <div style="padding: 8px 12px; background: #f5f7fa; border-radius: 4px; white-space: pre-wrap; line-height: 1.8">
          {{ detailDialog.warning.suggestion || '暂无处置建议' }}
        </div>

        <template v-if="detailDialog.operation">
          <el-divider content-position="left">关联作业</el-divider>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="管线">{{ detailDialog.pipelineName }}</el-descriptions-item>
            <el-descriptions-item label="清管器">{{ detailDialog.pigLabel }}</el-descriptions-item>
            <el-descriptions-item label="作业类型">{{ detailDialog.operation.operationType }}</el-descriptions-item>
            <el-descriptions-item label="发球时间">{{ detailDialog.operation.dispatchTime?.replace('T', ' ') }}</el-descriptions-item>
            <el-descriptions-item label="路线">
              {{ detailDialog.fromStationName }} → {{ detailDialog.toStationName }}
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag size="small" :type="detailDialog.operation.status === '运行中' ? 'warning' : detailDialog.operation.status === '已完成' ? 'success' : 'info'">
                {{ detailDialog.operation.status }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item v-if="detailDialog.operation.displacement != null" label="排量(m³/h)">
              {{ detailDialog.operation.displacement }}
            </el-descriptions-item>
            <el-descriptions-item v-if="detailDialog.operation.gasFlowRate != null" label="输气量(10⁴Nm³)">
              {{ detailDialog.operation.gasFlowRate }}
            </el-descriptions-item>
          </el-descriptions>
        </template>
      </template>
      <template #footer>
        <el-button @click="detailDialog.visible = false">关闭</el-button>
        <el-button
          v-if="detailDialog.warning?.status === '未处理'"
          type="primary"
          @click="handleConfirm(detailDialog.warning!.id!); detailDialog.visible = false"
        >确认预警</el-button>
        <el-button
          v-if="detailDialog.warning && detailDialog.warning.status !== '已关闭'"
          type="warning"
          @click="detailDialog.visible = false; openResolve(detailDialog.warning)"
        >关闭预警</el-button>
        <el-button type="primary" @click="$router.push(`/operations/${detailDialog.warning?.operationId}`)">
          查看作业跟踪
        </el-button>
      </template>
    </el-dialog>

    <!-- 关闭弹窗 -->
    <el-dialog v-model="resolveDialog.visible" title="关闭预警" width="450px">
      <el-form :model="resolveDialog" label-width="80px">
        <el-form-item label="预警内容">
          <span>{{ resolveDialog.content }}</span>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="resolveDialog.remark" type="textarea" :rows="3" placeholder="填写处理备注（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resolveDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="resolveDialog.loading" @click="handleResolve">确认关闭</el-button>
      </template>
    </el-dialog>

    <!-- 批量关闭弹窗 -->
    <el-dialog v-model="batchResolveDialog" title="批量关闭预警" width="450px">
      <el-form label-width="80px">
        <el-form-item label="已选预警">
          <span>{{ selectedIds.length }} 条</span>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="batchRemark" type="textarea" :rows="3" placeholder="填写统一备注（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchResolveDialog = false">取消</el-button>
        <el-button type="primary" :loading="batchSubmitting" @click="handleBatchResolve">确认关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { warningApi } from '@/api/warning'
import { operationApi } from '@/api/operation'
import { pipelineApi } from '@/api/pipeline'
import { stationApi } from '@/api/station'
import { pigApi } from '@/api/pig'
import { WarningType, WarningLevel } from '@/types'
import type { Warning, Operation } from '@/types'

const activeTab = ref('')
const filterType = ref('')
const filterLevel = ref('')
const autoRefresh = ref(true)

const list = ref<Warning[]>([])
const loading = ref(false)
const page = reactive({ current: 1, size: 10, total: 0 })
const tableRef = ref()
const selectedIds = ref<number[]>([])

const batchResolveDialog = ref(false)
const batchRemark = ref('')
const batchSubmitting = ref(false)

const resolveDialog = reactive({
  visible: false,
  id: 0,
  content: '',
  remark: '',
  loading: false,
})

const detailDialog = reactive({
  visible: false,
  warning: null as Warning | null,
  operation: null as Operation | null,
  pipelineName: '',
  pigLabel: '',
  fromStationName: '',
  toStationName: '',
})

const REFRESH_INTERVAL = 10
const countdown = ref(REFRESH_INTERVAL)
let refreshTimer: ReturnType<typeof setInterval> | null = null

function typeTag(type: string) {
  const map: Record<string, string> = { '延迟': 'warning', '速度异常': '', '卡阻': 'danger' }
  return map[type] || 'info'
}

function alertType(type: string) {
  const map: Record<string, string> = { '延迟': 'warning', '速度异常': 'info', '卡阻': 'error' }
  return map[type] || 'info'
}

function levelTag(level: string) {
  const map: Record<string, string> = { '高': 'danger', '中': 'warning', '低': 'info' }
  return map[level] || 'info'
}

function statusTag(status: string) {
  const map: Record<string, string> = { '未处理': 'danger', '已确认': 'warning', '已关闭': 'info' }
  return map[status] || 'info'
}

async function fetchPage() {
  const savedIds = new Set(selectedIds.value)
  loading.value = true
  try {
    const res = await warningApi.page(page.current, page.size, {
      status: activeTab.value || undefined,
      warningType: filterType.value || undefined,
      level: filterLevel.value || undefined,
    })
    list.value = res.data.data.records
    page.total = res.data.data.total
    await nextTick()
    if (savedIds.size > 0 && tableRef.value) {
      list.value.forEach(row => {
        if (savedIds.has(row.id!)) {
          tableRef.value.toggleRowSelection(row, true)
        }
      })
    }
  } finally {
    loading.value = false
  }
}

async function openDetail(row: Warning) {
  detailDialog.warning = row
  detailDialog.operation = null
  detailDialog.pipelineName = ''
  detailDialog.pigLabel = ''
  detailDialog.fromStationName = ''
  detailDialog.toStationName = ''
  detailDialog.visible = true

  try {
    const opRes = await operationApi.getById(row.operationId)
    const op = opRes.data.data
    detailDialog.operation = op

    const [pipeRes, pigRes, fromStaRes, toStaRes] = await Promise.all([
      pipelineApi.getById(op.pipelineId),
      pigApi.getById(op.pigId),
      stationApi.getById(op.fromStationId),
      stationApi.getById(op.toStationId),
    ])
    detailDialog.pipelineName = pipeRes.data.data.name
    detailDialog.pigLabel = `${pigRes.data.data.type} ${pigRes.data.data.spec}`
    detailDialog.fromStationName = fromStaRes.data.data.name
    detailDialog.toStationName = toStaRes.data.data.name
  } catch { /* context fetch failed, still show warning info */ }
}

async function handleConfirm(id: number) {
  try {
    await warningApi.confirm(id)
    ElMessage.success('已确认')
    fetchPage()
  } catch { /* */ }
}

function openResolve(row: Warning) {
  resolveDialog.id = row.id!
  resolveDialog.content = row.content
  resolveDialog.remark = ''
  resolveDialog.loading = false
  resolveDialog.visible = true
}

async function handleResolve() {
  resolveDialog.loading = true
  try {
    await warningApi.resolve(resolveDialog.id, resolveDialog.remark || undefined)
    ElMessage.success('已关闭')
    resolveDialog.visible = false
    fetchPage()
  } catch { /* */ } finally {
    resolveDialog.loading = false
  }
}

async function batchConfirm() {
  try {
    await warningApi.batchConfirm(selectedIds.value)
    ElMessage.success(`已确认 ${selectedIds.value.length} 条`)
    selectedIds.value = []
    fetchPage()
  } catch { /* */ }
}

async function handleBatchResolve() {
  batchSubmitting.value = true
  try {
    await warningApi.batchResolve(selectedIds.value, batchRemark.value || undefined)
    ElMessage.success(`已关闭 ${selectedIds.value.length} 条`)
    batchResolveDialog.value = false
    selectedIds.value = []
    batchRemark.value = ''
    fetchPage()
  } catch { /* */ } finally {
    batchSubmitting.value = false
  }
}

onMounted(() => {
  fetchPage()
  refreshTimer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) {
      if (autoRefresh.value) fetchPage()
      countdown.value = REFRESH_INTERVAL
    }
  }, 1000)
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
})
</script>
