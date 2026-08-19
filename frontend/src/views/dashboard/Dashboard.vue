<template>
  <div class="dashboard">
    <!-- 欢迎横幅 -->
    <div class="welcome-banner">
      <div class="welcome-text">
        <h2>清管作业管理系统</h2>
        <p>实时监控管线清管作业状态，智能预警，高效调度</p>
      </div>
      <div class="welcome-decoration">
        <img src="@/assets/icons/dashboard-nobg.png" alt="" />
      </div>
    </div>

    <!-- 智能助手 Hero -->
    <div class="agent-hero" @click="$router.push('/agent')">
      <div class="agent-hero-left">
        <div class="agent-hero-icon">
          <img src="@/assets/icons/agent-nobg.png" alt="" />
        </div>
        <div class="agent-hero-text">
          <h3>智能助手</h3>
          <p>一句话完成创建作业、反馈进度、查询数据、查看预警。支持交互卡片，选择即可操作，无需记忆命令。</p>
        </div>
      </div>
      <div class="agent-hero-right">
        <div class="agent-hero-btn">
          <span>开始对话</span>
          <span class="agent-hero-arrow">→</span>
        </div>

      </div>
    </div>

    <!-- 快捷操作 -->
    <div class="section-title">其他入口</div>
    <div class="quick-actions">
      <div class="action-card" @click="$router.push('/operations/create')">
        <span class="action-icon"><img src="@/assets/icons/create.png" alt="" /></span>
        <span class="action-label">新建作业</span>
        <span class="action-desc">向导式创建清管任务</span>
      </div>
      <div class="action-card" @click="$router.push('/calc')">
        <span class="action-icon"><img src="@/assets/icons/calc.png" alt="" /></span>
        <span class="action-label">预测计算</span>
        <span class="action-desc">清管到达时间预测</span>
      </div>
      <div class="action-card" @click="$router.push('/warnings')">
        <span class="action-icon"><img src="@/assets/icons/warning.png" alt="" /></span>
        <span class="action-label">预警中心</span>
        <span class="action-desc">查看与处理预警</span>
      </div>
    </div>

    <!-- 数据统计卡片 -->
    <div class="section-title">数据概览</div>
    <el-row :gutter="16" class="stat-grid">
      <el-col :xs="12" :sm="8" :md="4" v-for="card in statCards" :key="card.label">
        <div class="stat-card" :class="card.cls" @click="$router.push(card.route)">
          <div class="stat-icon"><img :src="card.icon" alt="" /></div>
          <div class="stat-num" :class="{ loading: loading }">
            {{ loading ? '—' : card.get() }}
          </div>
          <div class="stat-label">{{ card.label }}</div>
        </div>
      </el-col>
    </el-row>

    <!-- 下半部分：运行中作业 + 未处理预警 -->
    <el-row :gutter="16" class="bottom-row">
      <!-- 运行中作业 -->
      <el-col :span="14">
        <el-card shadow="never" class="list-card">
          <template #header>
            <div class="card-header">
              <span><el-icon><Clock /></el-icon> 运行中作业</span>
              <el-button text type="primary" size="small" @click="$router.push('/operations')">
                查看全部 →
              </el-button>
            </div>
          </template>
          <el-table :data="runningOps" stripe size="small" v-if="runningOps.length" @row-click="(row) => $router.push(`/operations/${row.id}`)">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column label="管线" width="100">
              <template #default="{ row }">
                {{ pipelineNameMap[row.pipelineId] || '—' }}
              </template>
            </el-table-column>
            <el-table-column label="发球站 → 收球站" min-width="140">
              <template #default="{ row }">
                {{ stationNameMap[row.fromStationId] || '?' }} → {{ stationNameMap[row.toStationId] || '?' }}
              </template>
            </el-table-column>
            <el-table-column prop="dispatchTime" label="发球时间" width="160" />
          </el-table>
          <el-empty v-else description="暂无运行中作业" :image-size="60" />
        </el-card>
      </el-col>

      <!-- 未处理预警 -->
      <el-col :span="10">
        <el-card shadow="never" class="list-card">
          <template #header>
            <div class="card-header">
              <span><el-icon><Bell /></el-icon> 未处理预警</span>
              <el-button text type="primary" size="small" @click="$router.push('/warnings')">
                查看全部 →
              </el-button>
            </div>
          </template>
          <div class="warning-list" v-if="recentWarnings.length">
            <div
              v-for="w in recentWarnings"
              :key="w.id"
              class="warning-item"
              :class="w.level"
              @click="$router.push('/warnings')"
            >
              <span class="warn-level-tag" :class="w.level">
                {{ w.level }}
              </span>
              <span class="warn-type">{{ w.warningType }}</span>
              <span class="warn-content">{{ w.content?.substring(0, 30) }}{{ w.content?.length > 30 ? '…' : '' }}</span>
            </div>
          </div>
          <el-empty v-else description="暂无未处理预警" :image-size="60" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { Clock, Bell } from '@element-plus/icons-vue'
import { pigApi } from '@/api/pig'
import { pipelineApi } from '@/api/pipeline'
import { stationApi } from '@/api/station'
import { segmentApi } from '@/api/segment'
import { operationApi } from '@/api/operation'
import { warningApi } from '@/api/warning'
import type { Warning, Operation } from '@/types'
import gsap from 'gsap'

const router = useRouter()
const loading = ref(true)

const stats = reactive({
  pigCount: 0,
  pipelineCount: 0,
  stationCount: 0,
  segmentCount: 0,
  runningCount: 0,
  unprocessedWarnCount: 0,
})

const runningOps = ref<Operation[]>([])
const recentWarnings = ref<Warning[]>([])
const pipelineNameMap = ref<Record<number, string>>({})
const stationNameMap = ref<Record<number, string>>({})

const iconPath = (name: string) => new URL(`/src/assets/icons/${name}.png`, import.meta.url).href

const statCards = [
  { icon: iconPath('pig'),       label: '清管器总数',  get: () => stats.pigCount,           route: '/pigs',       cls: 'card-blue' },
  { icon: iconPath('pipeline'),  label: '管线数量',    get: () => stats.pipelineCount,       route: '/pipelines',   cls: 'card-blue' },
  { icon: iconPath('station'),   label: '站点总数',    get: () => stats.stationCount,        route: '/stations',    cls: 'card-blue' },
  { icon: iconPath('segment'),   label: '管段总数',    get: () => stats.segmentCount,        route: '/segments',    cls: 'card-blue' },
  { icon: iconPath('running'),   label: '运行中作业',  get: () => stats.runningCount,        route: '/operations',  cls: 'card-accent' },
  { icon: iconPath('warning'),   label: '未处理预警',  get: () => stats.unprocessedWarnCount, route: '/warnings',    cls: 'card-warn' },
]

onMounted(async () => {
  // 每个 API 独立 try-catch，防止一个失败导致全部归零
  const safe = async <T>(fn: () => Promise<T>, fallback: T): Promise<T> => {
    try { return await fn() } catch { return fallback }
  }

  const [pigs, pipelines, stations, segments, ops, warnings] = await Promise.all([
    safe(() => pigApi.list(),       { data: { data: [] as any[] } } as any),
    safe(() => pipelineApi.list(),  { data: { data: [] as any[] } } as any),
    safe(() => stationApi.list(),   { data: { data: [] as any[] } } as any),
    safe(() => segmentApi.list(),   { data: { data: [] as any[] } } as any),
    safe(() => operationApi.list(), { data: { data: [] as any[] } } as any),
    safe(() => warningApi.list({ status: '未处理' }), { data: { data: [] as any[] } } as any),
  ])

  stats.pigCount = pigs.data.data?.length ?? 0
  stats.pipelineCount = pipelines.data.data?.length ?? 0
  stats.stationCount = stations.data.data?.length ?? 0
  stats.segmentCount = segments.data.data?.length ?? 0

  const allOps = (ops.data.data ?? []) as Operation[]
  stats.runningCount = allOps.filter(o => o.status === '运行中').length
  runningOps.value = allOps.filter(o => o.status === '运行中').slice(0, 5)

  stats.unprocessedWarnCount = warnings.data.data?.length ?? 0
  recentWarnings.value = (warnings.data.data ?? []).slice(0, 5)

  // Build name lookup maps
  for (const p of (pipelines.data.data ?? []) as any[]) pipelineNameMap.value[p.id!] = p.name
  for (const s of (stations.data.data ?? []) as any[])   stationNameMap.value[s.id!] = s.name

  loading.value = false

  // ── Load transition (product register: one fast fade, no orchestrated sequence) ──
  await nextTick()
  gsap.to('.dashboard', { opacity: 1, duration: 0.2, ease: 'power2.out' })
})
</script>

<style scoped>
.dashboard { max-width: 1400px; margin: 0 auto; }

/* ===== 欢迎横幅 ===== */
.welcome-banner {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 28px 32px;
  border-radius: 20px;
  margin-bottom: 24px;
  background: linear-gradient(135deg, var(--el-color-primary) 0%, var(--el-color-primary-dark-2) 100%);
  color: #fff;
}

.welcome-banner h2 {
  margin: 0 0 6px 0;
  font-size: 22px;
  font-weight: 700;
}

.welcome-banner p {
  margin: 0;
  font-size: 14px;
  opacity: 0.85;
}

.welcome-decoration { font-size: 48px; }
.welcome-decoration img { width: 48px; height: 48px; }

/* ===== 智能助手 Hero ===== */
.agent-hero {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24px 28px;
  border-radius: 24px;
  margin-bottom: 24px;
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
  box-shadow: 0 0 0 1px rgba(64, 158, 255, 0.2), 0 8px 32px rgba(0, 20, 60, 0.15);
  cursor: pointer;
  transition: all 0.3s;
  position: relative;
  overflow: hidden;
}

.agent-hero::before {
  content: '';
  position: absolute;
  top: -50%;
  right: -10%;
  width: 300px;
  height: 300px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(64, 158, 255, 0.15) 0%, transparent 70%);
  pointer-events: none;
}

.agent-hero:hover {
  border-color: rgba(64, 158, 255, 0.6);
  box-shadow: 0 4px 24px rgba(64, 158, 255, 0.2);
  transform: translateY(-2px);
}

.agent-hero-left {
  display: flex;
  align-items: center;
  gap: 20px;
  flex: 1;
}

.agent-hero-icon {
  font-size: 48px;
  flex-shrink: 0;
}
.agent-hero-icon img { width: 48px; height: 48px; }

.agent-hero-text h3 {
  margin: 0 0 6px 0;
  font-size: 18px;
  font-weight: 700;
  color: #fff;
}

.agent-hero-text p {
  margin: 0;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.7);
  line-height: 1.6;
  max-width: 480px;
}

.agent-hero-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
  flex-shrink: 0;
  margin-left: 24px;
}

.agent-hero-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 24px;
  border-radius: 8px;
  background: linear-gradient(135deg, #409eff, #66b1ff);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  white-space: nowrap;
  transition: all 0.2s;
}

.agent-hero:hover .agent-hero-btn {
  background: linear-gradient(135deg, #66b1ff, #79bbff);
  box-shadow: 0 2px 12px rgba(64, 158, 255, 0.4);
}

.agent-hero-arrow {
  font-size: 18px;
  transition: transform 0.2s;
}

.agent-hero:hover .agent-hero-arrow {
  transform: translateX(4px);
}

.agent-hero-hint {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.45);
  text-align: right;
}

/* ===== 分区标题 ===== */
.section-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
  padding-left: 4px;
}

/* ===== 快捷操作 ===== */
.quick-actions {
  display: flex;
  gap: 12px;
  margin-bottom: 28px;
}

.action-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 18px 12px;
  border-radius: 16px;
  background: #fff;
  box-shadow: var(--ring-card);
  cursor: pointer;
  transition: transform 0.25s var(--ease-out-expo), box-shadow 0.3s var(--ease-out-expo);
}

.action-card:hover {
  box-shadow: var(--shadow-float);
  transform: translateY(-2px);
}

.action-icon  { font-size: 28px; }
.action-icon img { width: 28px; height: 28px; vertical-align: middle; }
.action-label { font-size: 14px; font-weight: 600; color: #303133; }
.action-desc  { font-size: 12px; color: #909399; }

/* ===== 统计卡片 ===== */
.stat-grid { margin-bottom: 28px; }

.stat-card {
  text-align: center;
  padding: 24px 12px;
  border-radius: 16px;
  background: #fff;
  box-shadow: var(--ring-card);
  cursor: pointer;
  transition: transform 0.25s var(--ease-out-expo), box-shadow 0.3s var(--ease-out-expo);
  margin-bottom: 16px;
}

.stat-card:hover {
  transform: translateY(-3px);
  box-shadow: var(--shadow-float);
}

.stat-icon { font-size: 28px; margin-bottom: 8px; }
.stat-icon img { width: 28px; height: 28px; }

.stat-num {
  font-size: 32px;
  font-weight: 800;
  color: var(--el-color-primary);
  line-height: 1.2;
}

.stat-num.loading { color: #c0c4cc; }

.stat-card.card-accent .stat-num { color: var(--el-color-warning-dark-2); }
.stat-card.card-warn   .stat-num { color: #f56c6c; }

.stat-label {
  font-size: 13px;
  color: #909399;
  margin-top: 4px;
}

/* ===== 下半部分 ===== */
.bottom-row { margin-top: 4px; }

.list-card {
  border: none;
  border-radius: 16px;
  box-shadow: var(--ring-card);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 15px;
  font-weight: 600;
}

.card-header .el-icon { margin-right: 4px; vertical-align: middle; }

/* 运行中作业表格行可点击 */
.list-card :deep(.el-table__row) { cursor: pointer; }

/* ===== 预警列表 ===== */
.warning-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.warning-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #fafafa;
  cursor: pointer;
  transition: background 0.2s;
}

.warning-item:hover { background: #f0f2f5; }

.warn-level-tag {
  font-size: 11px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 4px;
  flex-shrink: 0;
}

.warn-level-tag.高 { background: #fef0f0; color: #f56c6c; }
.warn-level-tag.中 { background: #fdf6ec; color: #e6a23c; }
.warn-level-tag.低 { background: #ecf5ff; color: #409eff; }

.warn-type {
  font-size: 13px;
  font-weight: 500;
  color: #303133;
  flex-shrink: 0;
}

.warn-content {
  font-size: 12px;
  color: #909399;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* Prevent FOUC — dashboard fades in on load */
.dashboard {
  opacity: 0;
}

/* 响应式 */
@media (max-width: 768px) {
  .quick-actions { flex-wrap: wrap; }
  .action-card { flex: 1 1 45%; }
  .bottom-row .el-col { flex: 0 0 100%; max-width: 100%; margin-bottom: 16px; }
}
</style>
