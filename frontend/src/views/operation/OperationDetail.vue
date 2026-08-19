<template>
  <div class="detail-page">
    <!-- 返回 + 标题 -->
    <div style="margin-bottom: 16px">
      <el-button @click="$router.push('/operations')" :icon="'ArrowLeft'">返回列表</el-button>
    </div>

    <!-- 作业概要 -->
    <el-card v-if="op" class="info-card">
      <template #header>
        <span style="font-weight: bold">作业 #{{ op.id }} — {{ pipelineName }}</span>
        <el-tag :type="statusTag(op.status)" style="margin-left: 12px">{{ op.status }}</el-tag>
      </template>
      <el-descriptions :column="4" border size="small">
        <el-descriptions-item label="管线">{{ pipelineName }}</el-descriptions-item>
        <el-descriptions-item label="清管器">{{ pigLabel }}</el-descriptions-item>
        <el-descriptions-item label="作业类型">{{ op.operationType }}</el-descriptions-item>
        <el-descriptions-item label="发球时间">{{ fmtTime(op.dispatchTime) }}</el-descriptions-item>
        <el-descriptions-item label="发球站">{{ stationName(op.fromStationId) }}</el-descriptions-item>
        <el-descriptions-item label="收球站">{{ stationName(op.toStationId) }}</el-descriptions-item>
        <el-descriptions-item v-if="op.displacement != null" label="排量(m³/h)">{{ op.displacement }}</el-descriptions-item>
        <el-descriptions-item v-if="op.gasFlowRate != null" label="输气量(10⁴Nm³)">{{ op.gasFlowRate }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 节点进度 -->
    <el-card v-if="tracking.length > 0" class="progress-card">
      <template #header>节点进度</template>
      <el-steps :active="currentStepIndex" finish-status="success" align-center>
        <el-step
          v-for="(r, i) in tracking"
          :key="r.id"
          :title="r.isKeyStation ? '⭐' + r.stationName : r.stationName"
          :description="r.actualArrivalTime ? `到达 ${fmtTime(r.actualArrivalTime)}` : `预计 ${fmtTime(r.predictedArrivalTime)}`"
        />
      </el-steps>
    </el-card>

    <!-- 管线纵断面 — 清管器位置 -->
    <el-card v-if="tracking.length > 0 && profileStations.length > 0" class="profile-card">
      <template #header>管线纵断面示意</template>
      <OperationProfile
        :stations="profileStations"
        :tracking="tracking"
        :from-station-id="op?.fromStationId ?? 0"
        :to-station-id="op?.toStationId ?? 0"
      />
    </el-card>

    <!-- 跟踪时间线 -->
    <el-card v-if="tracking.length > 0" class="timeline-card">
      <template #header>跟踪记录</template>
      <el-timeline>
        <el-timeline-item
          v-for="r in tracking"
          :key="r.id"
          :type="r.actualArrivalTime ? 'success' : (isOverdue(r) ? 'warning' : 'primary')"
          :hollow="!r.actualArrivalTime && !isOverdue(r)"
          :timestamp="r.actualArrivalTime ? '实际到达 ' + fmtTime(r.actualArrivalTime) : '预计 ' + fmtTime(r.predictedArrivalTime)"
          placement="top"
        >
          <el-card shadow="hover" class="track-card" :class="{ 'key-station': r.isKeyStation }">
            <div class="track-header">
              <strong>{{ r.stationName }}</strong>
              <el-tag v-if="r.isKeyStation" type="danger" size="small" effect="dark">关键站</el-tag>
              <el-tag v-if="r.isRevised" type="warning" size="small">已修正({{ r.revisionCount }})</el-tag>
            </div>
            <el-descriptions :column="3" size="small" style="margin-top: 8px">
              <el-descriptions-item label="段间距">{{ Number(r.segmentDistance).toFixed(4) }} km</el-descriptions-item>
              <el-descriptions-item label="速度">{{ Number(r.pigSpeed).toFixed(2) }} km/h</el-descriptions-item>
              <el-descriptions-item v-if="r.actualArrivalTime" label="偏差">
                <span :style="{ color: diffColor(r) }">{{ timeDiff(r) }}</span>
              </el-descriptions-item>
            </el-descriptions>
            <div v-if="!r.actualArrivalTime && isNextArrival(r)" style="margin-top: 8px">
              <el-button type="warning" size="small" @click="openArrival(r)">到达反馈</el-button>
            </div>
            <div v-if="r.actualArrivalTime" style="margin-top: 8px; color: #67c23a">
              ✓ 已到达 — {{ fmtTime(r.actualArrivalTime) }}
            </div>
          </el-card>
        </el-timeline-item>
      </el-timeline>
    </el-card>

    <el-card v-else-if="op">
      <el-empty description="暂无跟踪记录" />
    </el-card>

    <!-- 到达反馈弹窗 -->
    <el-dialog v-model="arrivalDialog.visible" title="节点到达反馈" width="450px">
      <el-form :model="arrivalDialog" label-width="120px">
        <el-form-item label="站点">
          <el-input :model-value="arrivalDialog.stationName" disabled />
        </el-form-item>
        <el-form-item label="预测到达时间">
          <el-input :model-value="fmtTime(arrivalDialog.predictedTime)" disabled />
        </el-form-item>
        <el-form-item label="实际到达时间" required>
          <el-date-picker v-model="arrivalDialog.actualTime" type="datetime" placeholder="选择实际到达时间" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="arrivalDialog.visible = false">取消</el-button>
        <el-button type="primary" :disabled="!arrivalDialog.actualTime" :loading="arrivalDialog.loading" @click="submitArrival">
          确认到达
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { operationApi } from '@/api/operation'
import { pipelineApi } from '@/api/pipeline'
import { stationApi } from '@/api/station'
import { pigApi } from '@/api/pig'
import OperationProfile from '@/components/OperationProfile.vue'
import type { Operation, TrackingRecordVO, Station } from '@/types'

const route = useRoute()
const op = ref<Operation>()
const tracking = ref<TrackingRecordVO[]>([])
const profileStations = ref<Station[]>([])

const pipelineName = ref('')
const stationNameMap = ref<Record<number, string>>({})
const pigLabel = ref('')

function statusTag(s: string) {
  const m: Record<string, string> = { '准备': 'info', '运行中': 'warning', '已完成': 'success', '异常': 'danger' }
  return m[s] || 'info'
}

function stationName(id: number) {
  return stationNameMap.value[id] ?? `#${id}`
}

function fmtTime(t: string | undefined | null) {
  if (!t) return ''
  return t.replace('T', ' ')
}

const currentStepIndex = computed(() => {
  for (let i = tracking.value.length - 1; i >= 0; i--) {
    if (tracking.value[i].actualArrivalTime) return i + 1
  }
  return 0
})

function isOverdue(r: TrackingRecordVO) {
  if (r.actualArrivalTime) return false
  return new Date(r.predictedArrivalTime) < new Date()
}

function isNextArrival(r: TrackingRecordVO) {
  if (r.actualArrivalTime) return false
  // 非关键站不显示反馈按钮
  if (!r.isKeyStation) return false
  const idx = tracking.value.findIndex(t => t.id === r.id)
  // 所有前面的关键站必须已反馈
  for (let j = 0; j < idx; j++) {
    if (tracking.value[j].isKeyStation && !tracking.value[j].actualArrivalTime) {
      return false
    }
  }
  return true
}

function timeDiff(r: TrackingRecordVO) {
  if (!r.actualArrivalTime || !r.predictedArrivalTime) return ''
  const diff = new Date(r.actualArrivalTime).getTime() - new Date(r.predictedArrivalTime).getTime()
  const minutes = Math.round(diff / 60000)
  const absMin = Math.abs(minutes)
  if (absMin < 1) return '基本准时'
  const h = Math.floor(absMin / 60)
  const m = absMin % 60
  const prefix = minutes > 0 ? '延迟 ' : '提前 '
  return prefix + (h > 0 ? `${h}h${m}min` : `${m}min`)
}

function diffColor(r: TrackingRecordVO) {
  if (!r.actualArrivalTime || !r.predictedArrivalTime) return ''
  const diff = new Date(r.actualArrivalTime).getTime() - new Date(r.predictedArrivalTime).getTime()
  return Math.abs(diff) > 30 * 60000 ? '#f56c6c' : '#67c23a'
}

// 节点到达反馈
const arrivalDialog = reactive({
  visible: false,
  stationName: '',
  predictedTime: '',
  stationId: 0,
  actualTime: null as Date | null,
  loading: false,
})

function openArrival(r: TrackingRecordVO) {
  arrivalDialog.stationName = r.stationName
  arrivalDialog.predictedTime = r.predictedArrivalTime
  arrivalDialog.stationId = r.stationId
  arrivalDialog.actualTime = null
  arrivalDialog.loading = false
  arrivalDialog.visible = true
}

async function submitArrival() {
  if (!arrivalDialog.actualTime || !op.value) return
  arrivalDialog.loading = true
  try {
    const d = arrivalDialog.actualTime
    const pad = (n: number) => String(n).padStart(2, '0')
    const time = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
    await operationApi.nodeArrival(op.value.id!, {
      stationId: arrivalDialog.stationId,
      actualArrivalTime: time,
    })
    ElMessage.success('节点到达已反馈，下游站点已重新预测')
    arrivalDialog.visible = false
    await refresh()
  } catch { /* */ } finally {
    arrivalDialog.loading = false
  }
}

async function refresh() {
  const id = Number(route.params.id)
  const [opRes, trackRes] = await Promise.all([
    operationApi.getById(id),
    operationApi.getTracking(id),
  ])
  op.value = opRes.data.data
  tracking.value = trackRes.data.data
}

onMounted(async () => {
  const id = Number(route.params.id)
  try {
    const [opRes, trackRes] = await Promise.all([
      operationApi.getById(id),
      operationApi.getTracking(id),
    ])
    op.value = opRes.data.data
    tracking.value = trackRes.data.data

    // 解析管线名
    if (op.value) {
      const pr = await pipelineApi.getById(op.value.pipelineId)
      pipelineName.value = pr.data.data.name
      // 加载全线站点用于纵断面图
      const stationsRes = await stationApi.getByPipeline(op.value.pipelineId)
      profileStations.value = stationsRes.data.data.sort((a, b) => a.sortOrder - b.sortOrder)
      // 收集所有站点 ID 用于名称映射
      const sids = new Set<number>([op.value.fromStationId, op.value.toStationId])
      tracking.value.forEach(t => sids.add(t.stationId))
      for (const sid of sids) {
        const found = profileStations.value.find(s => s.id === sid)
        if (found) stationNameMap.value[sid] = found.name
      }
      // 清管器标签
      const pigRes = await pigApi.getById(op.value.pigId)
      const p = pigRes.data.data
      pigLabel.value = `${p.type} ${p.spec}`
    }
  } catch { /* */ }
})
</script>

<style scoped>
.info-card { margin-bottom: 16px; }
.progress-card { margin-bottom: 16px; }
.profile-card { margin-bottom: 16px; }
.track-card { max-width: 480px; }
.track-header { display: flex; align-items: center; gap: 8px; }
.key-station { border-left: 3px solid #f56c6c; }
</style>
