<template>
  <div class="pipeline-page">
    <!-- 搜索 / 操作 -->
    <el-card class="search-card">
      <el-form :inline="true" :model="query">
        <el-form-item label="介质类型">
          <el-select v-model="query.mediumType" placeholder="全部" clearable style="width: 140px">
            <el-option label="液体" value="液体" />
            <el-option label="气体" value="气体" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchList">查询</el-button>
          <el-button type="success" @click="openCreate">新增管线</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 管线表格 -->
    <el-card>
      <el-table :data="list" stripe v-loading="loading" row-key="id"
        @expand-change="handleExpand">
        <el-table-column type="expand" label="详情">
          <template #default="{ row }">
            <div class="expand-section">
              <h4>站点序列</h4>
              <el-table :data="stationMap[row.id!] ?? []" size="small" stripe v-if="(stationMap[row.id!]?.length ?? 0) > 0">
                <el-table-column prop="sortOrder" label="序号" width="60" />
                <el-table-column prop="name" label="站点名称" />
                <el-table-column prop="stationType" label="类型" width="80" />
                <el-table-column prop="mileage" label="累计里程(km)" width="130" />
                <el-table-column prop="elevation" label="高程(m)" width="100" />
              </el-table>
              <el-empty v-else description="暂无站点" :image-size="60" />

              <el-button type="primary" size="small" style="margin-top: 12px"
                @click.stop="openProfile(row)">
                查看纵断面图
              </el-button>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="name" label="管线名称" width="140" />
        <el-table-column prop="mediumType" label="介质类型" width="90">
          <template #default="{ row }">
            <el-tag :type="row.mediumType === '气体' ? '' : 'success'" size="small">{{ row.mediumType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="diameter" label="管径(mm)" width="100" />
        <el-table-column prop="designPressureMin" label="压力下限(MPa)" width="130" />
        <el-table-column prop="designPressureMax" label="压力上限(MPa)" width="130" />
        <el-table-column prop="totalLength" label="总长(km)" width="100" />
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button size="small" @click.stop="openEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click.stop="handleDelete(row.id!)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialog.visible" :title="dialog.isEdit ? '编辑管线' : '新增管线'"
      width="500px" @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="150px">
        <el-form-item label="管线名称" prop="name">
          <el-input v-model="form.name" placeholder="如 石兰线" />
        </el-form-item>
        <el-form-item label="介质类型" prop="mediumType">
          <el-select v-model="form.mediumType" style="width: 100%">
            <el-option label="液体" value="液体" />
            <el-option label="气体" value="气体" />
          </el-select>
        </el-form-item>
        <el-form-item label="管径(mm)">
          <el-input-number v-model="form.diameter" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="设计压力下限(MPa)">
          <el-input-number v-model="form.designPressureMin" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="设计压力上限(MPa)">
          <el-input-number v-model="form.designPressureMax" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="总长度(km)">
          <el-input-number v-model="form.totalLength" :min="0" :precision="4" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 纵断面图对话框 -->
    <el-dialog v-model="profileDialog.visible" :title="profileDialog.title" width="900px">
      <PipelineProfile :stations="profileDialog.stations" :pipeline-name="profileDialog.title" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { pipelineApi } from '@/api/pipeline'
import { stationApi } from '@/api/station'
import type { Pipeline, Station } from '@/types'
import PipelineProfile from '@/components/PipelineProfile.vue'

const query = reactive({ mediumType: '' })
const list = ref<Pipeline[]>([])
const loading = ref(false)
const stationMap = ref<Record<number, Station[]>>({})

const dialog = reactive({ visible: false, isEdit: false })
const form = reactive<Pipeline>({ name: '', mediumType: '' })
const formRef = ref<FormInstance>()
const rules = {
  name: [{ required: true, message: '请输入管线名称', trigger: 'blur' }],
  mediumType: [{ required: true, message: '请选择介质类型', trigger: 'change' }],
}

const profileDialog = reactive({ visible: false, title: '', stations: [] as Station[] })

async function fetchList() {
  loading.value = true
  try {
    const params = { mediumType: query.mediumType || undefined }
    const res = await pipelineApi.list(params)
    list.value = res.data.data
  } finally {
    loading.value = false
  }
}

async function handleExpand(row: Pipeline, expanded: boolean) {
  if (!expanded) return
  if (!stationMap.value[row.id!]) {
    try {
      const res = await stationApi.getByPipeline(row.id!)
      stationMap.value[row.id!] = res.data.data
    } catch { /* ignore */ }
  }
}

function openCreate() {
  dialog.visible = true
  dialog.isEdit = false
  Object.assign(form, { name: '', mediumType: '', diameter: undefined, designPressureMin: undefined, designPressureMax: undefined, totalLength: undefined, remark: '' })
}

function openEdit(row: Pipeline) {
  dialog.visible = true
  dialog.isEdit = true
  Object.assign(form, { ...row })
}

function resetForm() {
  formRef.value?.resetFields()
}

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  try {
    if (dialog.isEdit) {
      await pipelineApi.update(form)
      ElMessage.success('编辑成功')
    } else {
      await pipelineApi.create(form)
      ElMessage.success('新增成功')
    }
    dialog.visible = false
    fetchList()
  } catch { /* handled */ }
}

async function handleDelete(id: number) {
  await ElMessageBox.confirm('删除管线将同时影响关联的站点和管段，确定？', '警告', { type: 'warning' })
  await pipelineApi.delete(id)
  ElMessage.success('删除成功')
  fetchList()
}

async function openProfile(row: Pipeline) {
  profileDialog.title = row.name
  profileDialog.visible = true
  if (!stationMap.value[row.id!]) {
    try {
      const res = await stationApi.getByPipeline(row.id!)
      stationMap.value[row.id!] = res.data.data
    } catch { /* ignore */ }
  }
  profileDialog.stations = stationMap.value[row.id!] ?? []
}

onMounted(() => fetchList())
</script>

<style scoped>
.expand-section { padding: 8px 40px; }
.expand-section h4 { margin: 0 0 8px 0; }
</style>
