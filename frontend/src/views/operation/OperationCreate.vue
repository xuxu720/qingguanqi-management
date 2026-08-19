<template>
  <div class="create-page">
    <el-card>
      <el-steps :active="step" align-center style="margin-bottom: 30px">
        <el-step title="选择管线" />
        <el-step title="选择站点" />
        <el-step title="标记关键站" />
        <el-step title="选择清管器" />
        <el-step title="填写参数" />
      </el-steps>

      <!-- Step 1: 选择管线 -->
      <div v-show="step === 0" class="step-body">
        <el-form label-width="100px">
          <el-form-item label="选择管线">
            <el-select v-model="pipelineId" placeholder="请选择管线" @change="onPipelineChange" style="width: 300px">
              <el-option v-for="p in pipelines" :key="p.id" :label="p.name" :value="p.id!" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="selectedPipeline" label="介质类型">
            <el-tag :type="selectedPipeline.mediumType === '气体' ? 'warning' : 'primary'">
              {{ selectedPipeline.mediumType }}
            </el-tag>
          </el-form-item>
        </el-form>
      </div>

      <!-- Step 2: 选择站点 -->
      <div v-show="step === 1" class="step-body">
        <el-form label-width="100px">
          <el-form-item label="发球站">
            <el-select v-model="fromStationId" placeholder="选择发球站" style="width: 300px">
              <el-option v-for="s in stations" :key="s.id" :label="s.name" :value="s.id!" />
            </el-select>
          </el-form-item>
          <el-form-item label="收球站">
            <el-select v-model="toStationId" placeholder="选择收球站" :disabled="!fromStationId" style="width: 300px">
              <el-option v-for="s in toStationOptions" :key="s.id" :label="s.name" :value="s.id!" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="fromStationId && toStationId" label="途经站数">
            <el-tag>{{ segmentCount }} 个站段</el-tag>
          </el-form-item>
        </el-form>
      </div>

      <!-- Step 3: 标记关键站 -->
      <div v-show="step === 2" class="step-body">
        <el-form label-width="100px">
          <el-form-item label="关键站设置">
            <div style="color: #909399; margin-bottom: 12px; font-size: 13px">
              首尾站自动标记为关键站（不可取消）。中间站请根据需要勾选需要强制反馈的站点。
            </div>
            <el-checkbox-group v-model="keyStationList">
              <div v-for="(s, i) in segmentStations" :key="s.id"
                style="padding: 6px 12px; margin-bottom: 4px; display: flex; align-items: center"
                :style="{ background: i === 0 || i === segmentStations.length - 1 ? '#f5f7fa' : 'transparent', borderRadius: '4px' }">
                <el-checkbox
                  :label="s.id"
                  :disabled="i === 0 || i === segmentStations.length - 1">
                  <span style="font-weight: 500">{{ s.name }}</span>
                  <el-tag size="small" style="margin-left: 8px">{{ s.stationType }}</el-tag>
                  <span style="color: #C0C4CC; margin-left: 8px; font-size: 12px">{{ s.mileage }} km</span>
                </el-checkbox>
              </div>
            </el-checkbox-group>
            <div v-if="segmentStations.length > 2" style="margin-top: 8px; color: #909399; font-size: 12px">
              已选 <b>{{ keyStationList.length }}</b> / {{ segmentStations.length }} 个关键站
            </div>
          </el-form-item>
        </el-form>
      </div>

      <!-- Step 4: 选择清管器 -->
      <div v-show="step === 3" class="step-body">
        <el-form label-width="100px">
          <el-form-item label="选择清管器">
            <el-select v-model="pigId" placeholder="选择清管器" style="width: 300px">
              <el-option v-for="p in availablePigs" :key="p.id" :label="`${p.type} ${p.spec}`" :value="p.id!">
                <span>{{ p.type }} {{ p.spec }}</span>
                <el-tag size="small" style="margin-left: 8px" :type="p.status === '可用' ? 'success' : 'warning'">
                  {{ p.status }}
                </el-tag>
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item v-if="selectedPig" label="详情">
            <el-descriptions :column="3" border size="small">
              <el-descriptions-item label="类型">{{ selectedPig.type }}</el-descriptions-item>
              <el-descriptions-item label="规格">{{ selectedPig.spec }}</el-descriptions-item>
              <el-descriptions-item label="过盈量">{{ selectedPig.interferenceRate }}%</el-descriptions-item>
              <el-descriptions-item label="介质">{{ selectedPig.mediumType }}</el-descriptions-item>
              <el-descriptions-item label="状态">
                <el-tag size="small" :type="selectedPig.status === '可用' ? 'success' : 'warning'">
                  {{ selectedPig.status }}
                </el-tag>
              </el-descriptions-item>
            </el-descriptions>
          </el-form-item>
        </el-form>
      </div>

      <!-- Step 5: 填写参数 -->
      <div v-show="step === 4" class="step-body">
        <el-form ref="paramFormRef" :model="paramForm" :rules="paramRules" label-width="130px">
          <el-form-item label="作业类型">
            <el-select v-model="paramForm.operationType" style="width: 200px">
              <el-option v-for="t in operationTypes" :key="t" :label="t" :value="t" />
            </el-select>
          </el-form-item>
          <template v-if="isGas">
            <el-form-item label="出站压力(MPa)" prop="outletPressure">
              <el-input-number v-model="paramForm.outletPressure" :min="0.01" :precision="4" />
            </el-form-item>
            <el-form-item label="进站压力(MPa)" prop="inletPressure">
              <el-input-number v-model="paramForm.inletPressure" :min="0.01" :precision="4" />
            </el-form-item>
            <el-form-item label="输气量(10⁴Nm³)" prop="gasFlowRate">
              <el-input-number v-model="paramForm.gasFlowRate" :min="0.01" :precision="4" />
            </el-form-item>
          </template>
          <template v-else>
            <el-form-item label="排量(m³/h)" prop="displacement">
              <el-input-number v-model="paramForm.displacement" :min="0.01" :precision="2" />
            </el-form-item>
          </template>
          <el-form-item label="发球时间" prop="dispatchTime">
            <el-date-picker v-model="paramForm.dispatchTime" type="datetime" placeholder="选择发球时间" />
          </el-form-item>
        </el-form>
      </div>

      <!-- Navigation -->
      <div style="text-align: center; margin-top: 24px">
        <el-button v-if="step > 0" @click="step--">上一步</el-button>
        <el-button v-if="step < 4" type="primary" :disabled="!canNext" @click="step++">下一步</el-button>
        <el-button v-if="step === 4" type="primary" :disabled="!canSubmit" :loading="submitting" @click="handleSubmit">
          创建作业
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { pipelineApi } from '@/api/pipeline'
import { stationApi } from '@/api/station'
import { pigApi } from '@/api/pig'
import { operationApi } from '@/api/operation'
import type { Pipeline, Station, Pig } from '@/types'

const router = useRouter()
const operationTypes = ['常规清管', '应急清管']

const step = ref(0)
const submitting = ref(false)

// Step 1
const pipelines = ref<Pipeline[]>([])
const pipelineId = ref<number>()
const selectedPipeline = computed(() => pipelines.value.find(p => p.id === pipelineId.value))
const isGas = computed(() => selectedPipeline.value?.mediumType === '气体')

// Step 2
const stations = ref<Station[]>([])
const fromStationId = ref<number>()
const toStationId = ref<number>()
const toStationOptions = computed(() => {
  if (!fromStationId.value) return stations.value
  const idx = stations.value.findIndex(s => s.id === fromStationId.value)
  if (idx === -1) return stations.value
  return stations.value.slice(idx + 1)
})
const segmentCount = computed(() => {
  if (!fromStationId.value || !toStationId.value) return 0
  const fi = stations.value.findIndex(s => s.id === fromStationId.value)
  const ti = stations.value.findIndex(s => s.id === toStationId.value)
  return ti - fi
})

const segmentStations = computed(() => {
  if (!fromStationId.value || !toStationId.value) return []
  const fi = stations.value.findIndex(s => s.id === fromStationId.value)
  const ti = stations.value.findIndex(s => s.id === toStationId.value)
  if (fi === -1 || ti === -1) return []
  return stations.value.slice(fi, ti + 1)
})

const keyStationList = ref<number[]>([])

watch(fromStationId, () => { toStationId.value = undefined; keyStationList.value = [] })
watch([fromStationId, toStationId], ([f, t]) => {
  if (f) keyStationList.value = [f]
  if (t) {
    if (!keyStationList.value.includes(t)) keyStationList.value.push(t)
    keyStationList.value = [...new Set(keyStationList.value)]
  }
})

// Step 4
const pigs = ref<Pig[]>([])
const pigId = ref<number>()
const availablePigs = computed(() =>
  pigs.value.filter(p => {
    if (p.status !== '可用') return false
    if (!isGas.value) return p.mediumType === '液体' || p.mediumType === '通用'
    return p.mediumType === '气体' || p.mediumType === '通用'
  })
)
const selectedPig = computed(() => pigs.value.find(p => p.id === pigId.value))

// Step 5
const paramFormRef = ref<FormInstance>()
const paramForm = reactive({
  operationType: '常规清管',
  outletPressure: 4.0,
  inletPressure: 2.0,
  gasFlowRate: 3200,
  displacement: 100,
  dispatchTime: null as Date | null,
})
const paramRules = computed(() => ({
  operationType: [{ required: true, message: '请选择作业类型', trigger: 'change' }],
  ...(isGas.value ? {
    outletPressure: [{ required: true, message: '请输入出站压力', trigger: 'blur' }],
    inletPressure: [{ required: true, message: '请输入进站压力', trigger: 'blur' }],
    gasFlowRate: [{ required: true, message: '请输入输气量', trigger: 'blur' }],
  } : {
    displacement: [{ required: true, message: '请输入排量', trigger: 'blur' }],
  }),
  dispatchTime: [{ required: true, message: '请选择发球时间', trigger: 'change' }],
}))

// Can proceed to next step
const canNext = computed(() => {
  if (step.value === 0) return !!pipelineId.value
  if (step.value === 1) return !!fromStationId.value && !!toStationId.value
  if (step.value === 2) return segmentStations.value.length > 0
  if (step.value === 3) return !!pigId.value
  return true
})

// Can submit
const canSubmit = computed(() => !!paramForm.dispatchTime && !!paramForm.operationType)

onMounted(async () => {
  const res = await pipelineApi.list()
  pipelines.value = res.data.data
  // load all pigs
  try {
    const pr = await pigApi.list({})
    pigs.value = pr.data.data
  } catch { /* */ }
})

async function onPipelineChange() {
  fromStationId.value = undefined
  toStationId.value = undefined
  if (!pipelineId.value) { stations.value = []; return }
  try {
    const res = await stationApi.getByPipeline(pipelineId.value)
    stations.value = res.data.data.sort((a, b) => a.sortOrder - b.sortOrder)
  } catch { /* */ }
}

async function handleSubmit() {
  const valid = await paramFormRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const dispatchTime = paramForm.dispatchTime!
    const pad = (n: number) => String(n).padStart(2, '0')
    const time = `${dispatchTime.getFullYear()}-${pad(dispatchTime.getMonth() + 1)}-${pad(dispatchTime.getDate())}T${pad(dispatchTime.getHours())}:${pad(dispatchTime.getMinutes())}:${pad(dispatchTime.getSeconds())}`

    const res = await operationApi.createWithTracking({
      pipelineId: pipelineId.value!,
      pigId: pigId.value!,
      operationType: paramForm.operationType,
      fromStationId: fromStationId.value!,
      toStationId: toStationId.value!,
      dispatchTime: time,
      displacement: !isGas.value ? paramForm.displacement : undefined,
      gasFlowRate: isGas.value ? paramForm.gasFlowRate : undefined,
      outletPressure: isGas.value ? paramForm.outletPressure : undefined,
      inletPressure: isGas.value ? paramForm.inletPressure : undefined,
      status: '准备',
      keyStationIds: keyStationList.value,
    })
    ElMessage.success('作业创建成功，已生成跟踪记录')
    router.push(`/operations/${res.data.data.id}`)
  } catch {
    // interceptor handles error
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.step-body { max-width: 500px; margin: 0 auto; }
</style>
