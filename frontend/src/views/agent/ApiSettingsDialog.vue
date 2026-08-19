<template>
  <el-dialog v-model="visible" title="DeepSeek API 设置" width="520px" :close-on-click-modal="false">
    <el-form :model="form" label-width="100px">
      <el-form-item label="API 地址">
        <el-input v-model="form.apiBaseUrl" placeholder="https://api.deepseek.com/v1" />
        <div style="font-size: 12px; color: #909399; margin-top: 4px">
          DeepSeek 兼容 OpenAI 格式，默认地址无需修改
        </div>
      </el-form-item>

      <el-form-item label="模型">
        <el-select v-model="form.model" style="width: 100%">
          <el-option label="deepseek-chat (推荐)" value="deepseek-chat" />
          <el-option label="deepseek-reasoner" value="deepseek-reasoner" />
        </el-select>
      </el-form-item>

      <el-form-item label="API Key" required>
        <el-input
          v-model="form.apiKey"
          type="password"
          show-password
          placeholder="sk-xxxxxxxxxxxxxxxx"
        />
        <div style="font-size: 12px; color: #909399; margin-top: 4px">
          前往 <el-link type="primary" href="https://platform.deepseek.com" target="_blank" style="font-size: 12px">platform.deepseek.com</el-link> 申请 API Key
        </div>
      </el-form-item>

      <el-form-item>
        <el-button @click="testConnection" :loading="testing">
          <el-icon><Connection /></el-icon> 测试连接
        </el-button>
        <span v-if="testResult" :style="{ color: testResult.ok ? '#67c23a' : '#f56c6c', marginLeft: '12px', fontSize: '13px' }">
          {{ testResult.msg }}
        </span>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Connection } from '@element-plus/icons-vue'
import axios from 'axios'
import type { AgentConfig } from './agentConfig'

const STORAGE_KEY = 'qingguanqi_agent_config'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [v: boolean]; saved: [config: AgentConfig] }>()

const visible = ref(props.modelValue)
watch(() => props.modelValue, v => { visible.value = v })
watch(visible, v => emit('update:modelValue', v))

const form = reactive<AgentConfig>({
  apiKey: '',
  apiBaseUrl: 'https://api.deepseek.com/v1',
  model: 'deepseek-chat',
})

const testing = ref(false)
const testResult = ref<{ ok: boolean; msg: string } | null>(null)

function loadConfig() {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved) {
      const cfg = JSON.parse(saved)
      if (cfg.apiKey) form.apiKey = cfg.apiKey
      if (cfg.apiBaseUrl) form.apiBaseUrl = cfg.apiBaseUrl
      if (cfg.model) form.model = cfg.model
    }
  } catch { /* */ }
}

function save() {
  if (!form.apiKey.trim()) {
    ElMessage.warning('请填写 API Key')
    return
  }
  localStorage.setItem(STORAGE_KEY, JSON.stringify({
    apiKey: form.apiKey.trim(),
    apiBaseUrl: form.apiBaseUrl.trim() || 'https://api.deepseek.com/v1',
    model: form.model,
  }))
  ElMessage.success('API 配置已保存')
  visible.value = false
  emit('saved', { ...form })
}

async function testConnection() {
  if (!form.apiKey.trim()) {
    ElMessage.warning('请先填写 API Key')
    return
  }
  testing.value = true
  testResult.value = null
  try {
    const baseUrl = form.apiBaseUrl.trim() || 'https://api.deepseek.com/v1'
    const url = baseUrl.replace(/\/$/, '') + '/chat/completions'
    await axios.post(url, {
      model: form.model || 'deepseek-chat',
      messages: [{ role: 'user', content: 'hi' }],
      max_tokens: 5,
    }, {
      headers: {
        'Authorization': `Bearer ${form.apiKey.trim()}`,
        'Content-Type': 'application/json',
      },
      timeout: 10000,
    })
    testResult.value = { ok: true, msg: '连接成功！API Key 有效' }
  } catch (e: any) {
    const status = e.response?.status
    if (status === 401 || status === 403) {
      testResult.value = { ok: false, msg: 'API Key 无效，请检查' }
    } else if (status === 429) {
      testResult.value = { ok: false, msg: '请求频率限制，请稍后重试' }
    } else {
      testResult.value = { ok: false, msg: '连接失败：' + (e.message || '网络错误') }
    }
  } finally {
    testing.value = false
  }
}

loadConfig()
</script>
