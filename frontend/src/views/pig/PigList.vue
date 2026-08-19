<template>
  <div class="pig-page">
    <!-- 搜索区域 -->
    <el-card class="search-card">
      <el-form :inline="true" :model="query">
        <el-form-item label="类型">
          <el-select v-model="query.type" placeholder="全部" clearable style="width: 160px">
            <el-option v-for="t in pigTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="s in pigStatuses" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchList">查询</el-button>
          <el-button type="success" @click="openCreate">新增清管器</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 数据表格 -->
    <el-card>
      <el-table :data="list" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="type" label="类型" width="120" />
        <el-table-column prop="spec" label="规格" width="120" />
        <el-table-column prop="interferenceRate" label="过盈量(%)" width="100" />
        <el-table-column prop="mediumType" label="适用介质" width="100" />
        <el-table-column prop="applicableScene" label="适用场景" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="320">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-dropdown @command="(cmd: string) => handleStatusChange(row, cmd)" style="margin:0 8px">
              <el-button size="small" type="warning">状态变更</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="使用中">设为使用中</el-dropdown-item>
                  <el-dropdown-item command="可用">设为可用</el-dropdown-item>
                  <el-dropdown-item command="报废">设为报废</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-button size="small" @click="openOperations(row)">运行记录</el-button>
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

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialog.visible"
      :title="dialog.isEdit ? '编辑清管器' : '新增清管器'"
      width="500px"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="类型" prop="type">
          <el-select v-model="form.type" style="width: 100%">
            <el-option v-for="t in pigTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="规格" prop="spec">
          <el-input v-model="form.spec" placeholder="如 DN200" />
        </el-form-item>
        <el-form-item label="过盈量(%)" prop="interferenceRate">
          <el-input-number v-model="form.interferenceRate" :min="0.01" :max="50" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="适用介质" prop="mediumType">
          <el-select v-model="form.mediumType" style="width: 100%">
            <el-option label="液体" value="液体" />
            <el-option label="气体" value="气体" />
            <el-option label="通用" value="通用" />
          </el-select>
        </el-form-item>
        <el-form-item label="适用场景" prop="applicableScene">
          <el-input v-model="form.applicableScene" placeholder="描述适用场景" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 运行记录对话框 -->
    <el-dialog v-model="opsDialog.visible" title="运行记录" width="800px">
      <el-table :data="opsDialog.records" stripe>
        <el-table-column prop="id" label="作业ID" width="70" />
        <el-table-column prop="operationType" label="类型" width="100" />
        <el-table-column prop="dispatchTime" label="发球时间" width="170" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="fromStationId" label="发球站ID" />
        <el-table-column prop="toStationId" label="收球站ID" />
      </el-table>
      <template #footer>
        <el-button @click="opsDialog.visible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { pigApi } from '@/api/pig'
import type { Pig, Operation } from '@/types'

const pigTypes = ['清管球', '泡沫清管器', '钢刷清管器', '磁力清管器', '智能清管器', '双向清管器']
const pigStatuses = ['可用', '使用中', '报废']

const query = reactive({ type: '', status: '' })

const list = ref<Pig[]>([])
const loading = ref(false)
const page = reactive({ current: 1, size: 10, total: 0 })

const dialog = reactive({ visible: false, isEdit: false })
const form = reactive<Pig>({ type: '', spec: '', interferenceRate: 0, mediumType: '', status: '可用' })
const formRef = ref<FormInstance>()

const rules = {
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  spec: [{ required: true, message: '请输入规格', trigger: 'blur' }],
  interferenceRate: [{ required: true, message: '请输入过盈量', trigger: 'blur' }],
  mediumType: [{ required: true, message: '请选择适用介质', trigger: 'change' }],
}

const opsDialog = reactive({ visible: false, records: [] as Operation[] })

function statusTag(status: string) {
  return status === '可用' ? 'success' : status === '使用中' ? 'warning' : 'danger'
}

async function fetchList() {
  loading.value = true
  try {
    const params = { type: query.type || undefined, status: query.status || undefined }
    const res = await pigApi.list(params)
    list.value = res.data.data
    page.total = list.value.length
  } finally {
    loading.value = false
  }
}

function openCreate() {
  dialog.visible = true
  dialog.isEdit = false
  Object.assign(form, { type: '', spec: '', interferenceRate: 0, mediumType: '', applicableScene: '', remark: '', status: '可用' })
}

function openEdit(row: Pig) {
  if (row.status === '报废') {
    ElMessage.warning('该清管器已报废，无法编辑')
    return
  }
  dialog.visible = true
  dialog.isEdit = true
  Object.assign(form, row)
}

function resetForm() {
  formRef.value?.resetFields()
}

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  try {
    if (dialog.isEdit) {
      await pigApi.update(form)
      ElMessage.success('编辑成功')
    } else {
      await pigApi.create(form)
      ElMessage.success('新增成功')
    }
    dialog.visible = false
    fetchList()
  } catch {
    // 拦截器已提示
  }
}

async function handleDelete(id: number) {
  await ElMessageBox.confirm('确定删除该清管器？', '提示', { type: 'warning' })
  await pigApi.delete(id)
  ElMessage.success('删除成功')
  fetchList()
}

async function handleStatusChange(row: Pig, status: string) {
  if (status === '报废') {
    try {
      await ElMessageBox.confirm(
        `确定将清管器「${row.type} ${row.spec}」设为报废吗？报废后不可恢复，也无法再编辑。`,
        '确认报废',
        { confirmButtonText: '确认报废', cancelButtonText: '取消', type: 'warning' },
      )
    } catch {
      return // 用户取消
    }
  }
  try {
    await pigApi.updateStatus(row.id!, status)
    ElMessage.success('状态已变更')
    fetchList()
  } catch {
    // 拦截器已提示
  }
}

async function openOperations(row: Pig) {
  opsDialog.visible = true
  const res = await pigApi.getOperations(row.id!)
  opsDialog.records = res.data.data
}

onMounted(() => fetchList())
</script>
