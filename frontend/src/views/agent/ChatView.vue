<template>
  <div class="chat-page">
    <!-- Left: conversation list -->
    <div class="chat-sidebar">
      <div class="sidebar-header">
        <span>对话历史</span>
        <el-button size="small" text @click="startNewChat">新对话</el-button>
      </div>
      <div class="conversation-list">
        <div
          v-for="c in conversations"
          :key="c.id"
          :class="['conv-item', { active: c.id === currentConvId }]"
          @click="switchConversation(c.id!)"
        >
          <span class="conv-title">{{ c.title || '新对话' }}</span>
          <el-button
            size="small"
            text
            type="danger"
            @click.stop="deleteConv(c.id!)"
          ><el-icon><Delete /></el-icon></el-button>
        </div>
        <el-empty v-if="conversations.length === 0" description="暂无对话" :image-size="48" />
      </div>
    </div>

    <!-- Right: chat area -->
    <div class="chat-main">
      <!-- Top bar -->
      <div class="chat-topbar">
        <span class="chat-title">智能助手</span>
        <el-button type="warning" size="default" @click="settingsVisible = true">
          <el-icon><Setting /></el-icon> API 设置
        </el-button>
      </div>

      <!-- Messages -->
      <div class="chat-messages" ref="msgContainerRef">
        <!-- No API key warning -->
        <div v-if="!apiKey" class="no-key-banner">
          <el-alert
            title="请先配置 DeepSeek API Key"
            type="warning"
            :closable="false"
            show-icon
          >
            <template #default>
              <el-button type="warning" size="small" @click="settingsVisible = true" style="margin-top: 8px">
                立即配置
              </el-button>
            </template>
          </el-alert>
        </div>

        <!-- Welcome message -->
        <div v-if="messages.length === 0" class="welcome-area">
          <div class="welcome-icon"><img src="@/assets/icons/agent-nobg.png" alt="" /></div>
          <div class="welcome-text">
            <p><strong>清管作业智能助手</strong></p>
            <p>自然语言即可完成：创建作业、反馈进度、查询数据、新增记录、查看预警。</p>
            <p>不知道说什么？直接输入"<b>帮助</b>"或点击下方示例开始：</p>
          </div>
          <div class="quick-templates">
            <div
              v-for="t in quickTemplates"
              :key="t.label"
              class="template-chip"
              @click="inputText = t.text; focusInput()"
            >
              {{ t.label }}
            </div>
          </div>
        </div>

        <!-- Chat bubbles -->
        <div
          v-for="(m, i) in messages"
          :key="i"
          :class="['chat-bubble', m.role]"
        >
          <div class="bubble-avatar">
            <img v-if="m.role === 'assistant'" src="@/assets/icons/agent.png" alt="" />
            <span v-else class="avatar-user">👤</span>
          </div>
          <div class="bubble-body">
            <div class="bubble-content" v-if="m.role === 'assistant'">
              <div class="sms-content" v-html="formatSmsContent(m.content)" />
              <div v-if="m.intent === 'CREATE_OPERATION' || m.intent === 'NODE_ARRIVAL'" class="bubble-actions">
                <el-button
                  v-if="m.operationId"
                  size="small"
                  type="primary"
                  @click="$router.push(`/operations/${m.operationId}`)"
                >
                  查看作业详情 →
                </el-button>
              </div>
              <ChatWidget
                v-for="(w, wi) in m.widgets"
                :key="wi"
                :widget="w"
                @select="handleWidgetSelect"
                @submit="handleWidgetSubmit"
                @action="handleWidgetAction"
                @navigate="(route: string) => $router.push(route)"
              />
            </div>
            <div class="bubble-content user-content" v-else>
              {{ m.content }}
            </div>
          </div>
        </div>

        <!-- Typing indicator -->
        <div v-if="loading" class="chat-bubble assistant">
          <div class="bubble-avatar"><img src="@/assets/icons/agent-nobg.png" alt="" /></div>
          <div class="bubble-body">
            <div class="typing-indicator">
              <span></span><span></span><span></span>
            </div>
          </div>
        </div>
      </div>

      <!-- Quick templates row -->
      <div class="templates-row" v-if="messages.length > 0">
        <el-tag
          v-for="t in quickTemplates"
          :key="t.label"
          :type="'info'"
          class="quick-tag"
          @click="inputText = t.text; focusInput()"
        >
          {{ t.label }}
        </el-tag>
      </div>

      <!-- Input area -->
      <div class="chat-input-area">
        <el-input
          ref="inputRef"
          v-model="inputText"
          type="textarea"
          :rows="2"
          placeholder="描述清管作业需求…（Enter 发送，Shift+Enter 换行）"
          :disabled="!apiKey"
          @keydown="handleKeydown"
        />
        <el-button
          type="primary"
          :disabled="!inputText.trim() || loading || !apiKey"
          :loading="loading"
          @click="send"
        >
          发送
        </el-button>
      </div>
    </div>

    <!-- Settings Dialog -->
    <ApiSettingsDialog v-model="settingsVisible" @saved="onSettingsSaved" />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, nextTick, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Setting, Delete } from '@element-plus/icons-vue'
import { agentApi } from '@/api/agent'
import ApiSettingsDialog from './ApiSettingsDialog.vue'
import ChatWidget from '@/components/chat/ChatWidget.vue'
import { getConfig, type AgentConfig } from './agentConfig'
import type { Conversation, AgentMessage, WidgetAction } from '@/types'
import gsap from 'gsap'

interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  intent?: string
  operationId?: number
  widgets?: import('@/types').Widget[]
}

const router = useRouter()

const inputRef = ref<InstanceType<typeof import('element-plus').ElInput>>()
const msgContainerRef = ref<HTMLElement>()

const inputText = ref('')
const loading = ref(false)
const currentConvId = ref<number>()
const conversations = ref<Conversation[]>([])
const messages = ref<ChatMessage[]>([])
const settingsVisible = ref(false)

const apiKey = ref('')
const apiBaseUrl = ref('https://api.deepseek.com/v1')
const model = ref('deepseek-chat')

function loadApiConfig() {
  const cfg = getConfig()
  if (cfg) {
    apiKey.value = cfg.apiKey
    apiBaseUrl.value = cfg.apiBaseUrl || 'https://api.deepseek.com/v1'
    model.value = cfg.model || 'deepseek-chat'
  }
}

const quickTemplates = [
  // ── 作业管理 ──
  { label: '🚀 创建作业', text: '发一个清管器' },
  { label: '📍 节点反馈', text: '清管器过站了' },
  { label: '📈 查询进度', text: '清管器到哪了？' },
  // ── 数据查询 ──
  { label: '🔍 查看管线', text: '有哪些管线？' },
  { label: '🔍 查看清管器', text: '有哪些可用的清管器？' },
  { label: '⚠️ 查看预警', text: '最近有什么预警？' },
  // ── 数据维护 ──
  { label: '➕ 添加清管器', text: '添加一个清管器' },
  { label: '➕ 新建管线', text: '新建一条管线' },
  { label: '❓ 使用帮助', text: '帮助' },
]

function focusInput() {
  nextTick(() => {
    const el = document.querySelector('.chat-input-area textarea') as HTMLTextAreaElement
    el?.focus()
  })
}

function scrollToBottom() {
  nextTick(() => {
    if (msgContainerRef.value) {
      msgContainerRef.value.scrollTop = msgContainerRef.value.scrollHeight
    }
  })
}

function animateLatestBubble() {
  nextTick(() => {
    const bubbles = msgContainerRef.value?.querySelectorAll('.chat-bubble')
    if (!bubbles || bubbles.length === 0) return
    const latest = bubbles[bubbles.length - 1] as HTMLElement
    gsap.fromTo(latest, { y: 8, opacity: 0 }, {
      y: 0, opacity: 1, duration: 0.2, ease: 'power3.out',
    })
  })
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    send()
  }
}

function onSettingsSaved(config: AgentConfig) {
  apiKey.value = config.apiKey
  apiBaseUrl.value = config.apiBaseUrl
  model.value = config.model
}

async function loadConversations() {
  try {
    const res = await agentApi.listConversations()
    conversations.value = res.data.data
  } catch { /* */ }
}

function startNewChat() {
  currentConvId.value = undefined
  messages.value = []
  inputText.value = ''
  focusInput()
}

async function switchConversation(id: number) {
  currentConvId.value = id
  try {
    const res = await agentApi.getMessages(id)
    messages.value = res.data.data.map(m => ({
      role: m.role as 'user' | 'assistant',
      content: m.content,
      intent: m.intent,
      operationId: m.operationId,
    }))
    scrollToBottom()
  } catch { /* */ }
}

async function deleteConv(id: number) {
  try {
    await agentApi.deleteConversation(id)
    ElMessage.success('已删除')
    if (currentConvId.value === id) {
      startNewChat()
    }
    loadConversations()
  } catch { /* */ }
}

async function send() {
  const text = inputText.value.trim()
  if (!text || loading.value) return

  messages.value.push({ role: 'user', content: text })
  inputText.value = ''
  loading.value = true
  scrollToBottom()
  animateLatestBubble()

  try {
    const res = await agentApi.chat(
      { message: text, conversationId: currentConvId.value },
      apiKey.value,
      apiBaseUrl.value !== 'https://api.deepseek.com/v1' ? apiBaseUrl.value : undefined,
    )
    const reply = res.data.data
    messages.value.push({
      role: 'assistant',
      content: reply.reply,
      intent: reply.intent,
      operationId: reply.data?.operationId as number | undefined,
      widgets: reply.widgets,
    })
    animateLatestBubble()
    if (!currentConvId.value) {
      currentConvId.value = reply.conversationId
      loadConversations()
    }
  } catch { /* interceptor handles error */ } finally {
    loading.value = false
    scrollToBottom()
  }
}

// ── Widget event handlers ──

function handleWidgetSelect(value: string) {
  inputText.value = value
  send()
}

function handleWidgetSubmit(text: string) {
  inputText.value = text
  send()
}

function handleWidgetAction(action: WidgetAction) {
  if (action.action === 'navigate') {
    router.push(action.value || '/')
  } else if (action.action === 'arrival') {
    // action.value format: "站名|管线名"
    const [station, pipeline] = (action.value || '').split('|')
    const now = new Date()
    const pad = (n: number) => String(n).padStart(2, '0')
    const time = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}`
    inputText.value = `${pipeline || ''} 清管器过${station} ${time}`
    send()
  } else if (action.action === 'ack_warning') {
    // Navigate to warning page for precise operations
    router.push('/warnings')
    ElMessage.info('已跳转到预警管理页面')
  }
}

/** Highlight SMS template text: bold tags, colored values */
function formatSmsContent(text: string): string {
  let html = text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
  // Bold 【...】 blocks
  html = html.replace(/【(.+?)】/g, '<b class="sms-tag">【$1】</b>')
  // Highlight time patterns (M月d日HH:mm or HH:mm)
  html = html.replace(/(\d+月\d+日\d+:\d+)/g, '<span class="sms-time">$1</span>')
  html = html.replace(/(\d+:\d+)/g, '<span class="sms-time">$1</span>')
  // Highlight speed (Xkm/h)
  html = html.replace(/(\d+\.?\d*km\/h)/g, '<span class="sms-speed">$1</span>')
  // Highlight distances
  html = html.replace(/(\d+\.?\d*km)/g, '<span class="sms-dist">$1</span>')
  // Highlight remaining labels
  html = html.replace(/(剩余管段长度|该管段长度|管径|设计压力|当前球速|平均球速|清管总耗时|计划今日取球)/g,
    '<span class="sms-label">$1</span>')
  return html.replace(/\n/g, '<br>')
}

onMounted(() => {
  loadApiConfig()
  loadConversations()
})
</script>

<style scoped>
.chat-page { display: flex; height: calc(100vh - 120px); }
.chat-sidebar {
  width: 240px; border-right: 1px solid #e4e7ed; background: #fafafa;
  display: flex; flex-direction: column; overflow: hidden;
}
.sidebar-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 12px 16px; border-bottom: 1px solid #e4e7ed; font-weight: 600;
}
.conversation-list { flex: 1; overflow-y: auto; padding: 8px; }
.conv-item {
  display: flex; justify-content: space-between; align-items: center;
  padding: 10px 12px; border-radius: 6px; cursor: pointer; margin-bottom: 4px;
}
.conv-item:hover { background: #e8e8e8; }
.conv-item.active { background: #d9ecff; }
.conv-title { font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1; }

.chat-main { flex: 1; display: flex; flex-direction: column; overflow: hidden; }
.chat-topbar {
  display: flex; justify-content: space-between; align-items: center;
  padding: 12px 20px; border-bottom: 1px solid #e4e7ed; background: #fff;
}
.chat-title { font-size: 18px; font-weight: 600; }

.chat-messages { flex: 1; overflow-y: auto; padding: 20px; background: #f5f6fa; }

.no-key-banner { margin-bottom: 20px; }

.welcome-area { text-align: center; padding: 60px 20px; }
.welcome-icon { font-size: 48px; margin-bottom: 16px; }
.welcome-icon img { width: 48px; height: 48px; }
.welcome-text p { margin: 6px 0; color: #606266; }
.quick-templates { display: flex; flex-wrap: wrap; gap: 10px; justify-content: center; margin-top: 24px; }
.template-chip {
  background: #ecf5ff; border: 1px solid #b3d8ff; border-radius: 8px;
  padding: 10px 16px; cursor: pointer; font-size: 13px; color: #409eff;
  transition: all .2s; white-space: nowrap;
}
.template-chip:hover { background: #d9ecff; border-color: #409eff; }

.chat-bubble { display: flex; gap: 12px; margin-bottom: 20px; }
.chat-bubble.user { flex-direction: row-reverse; }
.bubble-avatar { font-size: 32px; line-height: 1; flex-shrink: 0; }
.bubble-avatar img { width: 32px; height: 32px; }
.avatar-user { font-size: 32px; line-height: 1; }
.bubble-body { max-width: 75%; }
.bubble-content {
  padding: 12px 16px; border-radius: 12px; line-height: 1.7;
}
.chat-bubble.user .bubble-content {
  background: #409eff; color: #fff; border-bottom-right-radius: 4px;
}
.chat-bubble.assistant .bubble-content {
  background: #fff; box-shadow: 0 0 0 1px rgba(0,0,0,0.04), 0 1px 4px rgba(0,0,0,0.04); border-bottom-left-radius: 6px;
}

/* SMS template styling inside assistant bubbles */
.sms-content {
  font-family: 'SF Mono', 'Cascadia Code', 'Consolas', 'Microsoft YaHei', monospace;
  font-size: 13px; line-height: 1.9; white-space: pre-wrap;
}
:deep(.sms-tag) { color: #e6a23c; font-weight: 700; }
:deep(.sms-time) { color: #409eff; font-weight: 600; }
:deep(.sms-speed) { color: #67c23a; font-weight: 600; }
:deep(.sms-dist) { color: #e6a23c; }
:deep(.sms-label) { color: #909399; font-weight: 500; }

.bubble-actions { margin-top: 12px; padding-top: 10px; border-top: 1px solid #ebeef5; }

.typing-indicator { display: flex; gap: 6px; padding: 8px 16px; }
.typing-indicator span {
  width: 8px; height: 8px; border-radius: 50%; background: #c0c4cc;
  animation: typing 1.4s infinite ease-in-out both;
}
.typing-indicator span:nth-child(1) { animation-delay: 0s; }
.typing-indicator span:nth-child(2) { animation-delay: .2s; }
.typing-indicator span:nth-child(3) { animation-delay: .4s; }
@keyframes typing {
  0%, 80%, 100% { transform: scale(0.6); opacity: .4; }
  40% { transform: scale(1); opacity: 1; }
}

.templates-row { display: flex; gap: 8px; padding: 8px 20px; flex-wrap: wrap; background: #fff; border-top: 1px solid #f0f0f0; }
.quick-tag { cursor: pointer; }

.chat-input-area {
  display: flex; gap: 12px; padding: 12px 20px;
  background: #fff; border-top: 1px solid #e4e7ed; align-items: flex-end;
}
.chat-input-area .el-textarea { flex: 1; }
.chat-input-area .el-button { height: 58px; width: 80px; }
</style>
