<template>
  <el-container class="layout">
    <!-- 侧边栏 -->
    <el-aside :width="isCollapse ? '64px' : '240px'" class="layout-aside">
      <!-- Logo -->
      <div class="aside-logo" @click="isCollapse = !isCollapse">
        <span class="logo-icon"><img src="@/assets/icons/logo-nobg.png" alt="" /></span>
        <span v-show="!isCollapse" class="logo-text">清管作业管理系统</span>
      </div>

      <!-- 菜单 -->
      <el-menu
        :default-active="route.path"
        :collapse="isCollapse"
        :collapse-transition="false"
        router
        background-color="transparent"
        :text-color="'var(--app-sidebar-text)'"
        :active-text-color="'var(--app-sidebar-text-active)'"
        class="aside-menu"
      >
        <el-menu-item index="/dashboard">
          <el-icon><HomeFilled /></el-icon>
          <span>首页</span>
        </el-menu-item>
        <el-menu-item index="/pigs">
          <el-icon><Tools /></el-icon>
          <span>清管器管理</span>
        </el-menu-item>
        <el-menu-item index="/pipelines">
          <el-icon><Share /></el-icon>
          <span>管线管理</span>
        </el-menu-item>
        <el-menu-item index="/stations">
          <el-icon><Location /></el-icon>
          <span>站点管理</span>
        </el-menu-item>
        <el-menu-item index="/segments">
          <el-icon><Connection /></el-icon>
          <span>管段管理</span>
        </el-menu-item>
        <el-menu-item index="/operations">
          <el-icon><Clock /></el-icon>
          <span>清管作业</span>
        </el-menu-item>
        <el-menu-item index="/calc">
          <el-icon><DataAnalysis /></el-icon>
          <span>预测计算</span>
        </el-menu-item>
        <el-menu-item index="/agent">
          <el-icon><ChatDotRound /></el-icon>
          <span>智能助手</span>
        </el-menu-item>
        <el-menu-item index="/warnings">
          <el-icon><Bell /></el-icon>
          <span>预警管理</span>
          <el-badge
            v-if="!isCollapse && unprocessedCount > 0"
            :value="unprocessedCount"
            class="nav-warn-badge"
          />
        </el-menu-item>
      </el-menu>

      <!-- 折叠按钮 -->
      <div class="aside-collapse" @click="isCollapse = !isCollapse">
        <el-icon :size="18">
          <DArrowLeft v-if="!isCollapse" /><DArrowRight v-else />
        </el-icon>
      </div>
    </el-aside>

    <!-- 右侧主体 -->
    <el-container class="layout-main">
      <!-- 顶部导航 -->
      <el-header class="layout-header">
        <div class="header-left">
          <span class="header-breadcrumb">
            <el-icon><HomeFilled /></el-icon>
            <span class="breadcrumb-sep">/</span>
            <span>{{ route.meta.title || '' }}</span>
          </span>
        </div>
        <div class="header-right">
          <!-- 主题切换 -->
          <el-tooltip :content="theme === 'gold' ? '切换至 深海主题' : '切换至 金色主题'" placement="bottom">
            <span class="theme-toggle" @click="toggleTheme">
              <span class="theme-dot" :class="theme" />
              <span class="theme-label">{{ theme === 'gold' ? '金' : '海' }}</span>
            </span>
          </el-tooltip>
          <span class="header-time">{{ nowText }}</span>
        </div>
      </el-header>

      <!-- 内容区 -->
      <el-main class="layout-content">
        <router-view v-slot="{ Component }">
          <transition name="page-fade" mode="out-in" appear>
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import {
  HomeFilled, Tools, Share, Location, Clock, Connection,
  DataAnalysis, Bell, ChatDotRound, DArrowLeft, DArrowRight,
} from '@element-plus/icons-vue'
import { warningApi } from '@/api/warning'
import { useTheme } from '@/composables/useTheme'

const route = useRoute()
const { theme, toggle: toggleTheme } = useTheme()

const isCollapse = ref(false)
const unprocessedCount = ref(0)
const nowText = ref('')
let badgeTimer: ReturnType<typeof setInterval> | null = null
let clockTimer: ReturnType<typeof setInterval> | null = null

function updateClock() {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  nowText.value = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

async function fetchUnprocessedCount() {
  try {
    const res = await warningApi.list({ status: '未处理' })
    unprocessedCount.value = res.data.data.length
  } catch { /* */ }
}

onMounted(async () => {
  fetchUnprocessedCount()
  badgeTimer = setInterval(fetchUnprocessedCount, 10_000)
  updateClock()
  clockTimer = setInterval(updateClock, 1000)
})

onUnmounted(() => {
  if (badgeTimer) clearInterval(badgeTimer)
  if (clockTimer) clearInterval(clockTimer)
})
</script>

<style scoped>
/* ===== 整体布局 ===== */
.layout { height: 100vh; }

/* ===== 侧边栏 ===== */
.layout-aside {
  background: linear-gradient(180deg, var(--app-sidebar-start) 0%, var(--app-sidebar-end) 100%);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transition: width 0.3s;
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.15);
}

.aside-logo {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  cursor: pointer;
  user-select: none;
  border-bottom: 1px solid var(--app-sidebar-logo-border);
  flex-shrink: 0;
}

.logo-icon {
  font-size: 24px;
  color: var(--app-sidebar-accent);
  flex-shrink: 0;
}
.logo-icon img { width: 24px; height: 24px; }

.logo-text {
  font-size: 15px;
  font-weight: 700;
  color: #fff;
  letter-spacing: 1px;
  white-space: nowrap;
}

/* ===== 菜单 ===== */
.aside-menu {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  border-right: none;
  padding: 8px 0;
}

:deep(.aside-menu .el-menu-item) {
  margin: 2px 12px;
  border-radius: 8px;
  height: 48px;
  line-height: 48px;
  font-size: 14px;
  transition: all 0.2s ease;
  color: var(--app-sidebar-text) !important;
}

:deep(.aside-menu .el-menu-item:hover) {
  background: var(--app-sidebar-hover-bg) !important;
  color: #fff !important;
}

:deep(.aside-menu .el-menu-item.is-active) {
  background: var(--app-sidebar-active-bg) !important;
  color: var(--app-sidebar-text-active) !important;
  font-weight: 600;
}

:deep(.aside-menu .el-menu-item.is-active::before) {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 20px;
  background: var(--app-sidebar-accent);
  border-radius: 0 3px 3px 0;
}

:deep(.aside-menu .el-icon) {
  font-size: 18px;
}

/* 预警徽标 */
.nav-warn-badge :deep(.el-badge__content) {
  background: var(--app-badge-bg);
  color: var(--app-badge-text);
  font-weight: 700;
  position: static;
  transform: none;
  margin-left: 8px;
}

/* ===== 折叠按钮 ===== */
.aside-collapse {
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--app-sidebar-collapse);
  border-top: 1px solid var(--app-sidebar-logo-border);
  flex-shrink: 0;
  transition: color 0.2s;
}

.aside-collapse:hover {
  color: var(--app-sidebar-accent);
}

/* ===== 主体 ===== */
.layout-main { flex-direction: column; }

/* ===== 顶部导航 ===== */
.layout-header {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
  padding: 0 24px;
  flex-shrink: 0;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}

.header-left  { display: flex; align-items: center; }
.header-right { display: flex; align-items: center; gap: 16px; }

.header-breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: #606266;
}

.header-breadcrumb .el-icon { color: var(--el-color-primary); }
.breadcrumb-sep { color: #c0c4cc; }

/* 主题切换按钮 */
.theme-toggle {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  cursor: pointer;
  padding: 4px 10px;
  border-radius: 12px;
  background: #f5f7fa;
  transition: background 0.2s;
  user-select: none;
}

.theme-toggle:hover { background: #ebeef5; }

.theme-dot {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  display: inline-block;
}

.theme-dot.gold  { background: #FFE76F; border: 2px solid #002EA6; }
.theme-dot.ocean { background: #91CFD5; border: 2px solid #113056; }

.theme-label {
  font-size: 12px;
  font-weight: 600;
  color: #606266;
}

.header-time {
  font-size: 13px;
  color: #909399;
  font-variant-numeric: tabular-nums;
}

/* ===== 内容区 ===== */
.layout-content {
  background: #f0f2f5;
  padding: 20px;
  overflow-y: auto;
}

/* Page transition */
.page-fade-enter-active,
.page-fade-leave-active {
  transition: opacity 0.15s var(--ease-out-expo), transform 0.15s var(--ease-out-expo);
}
.page-fade-enter-from {
  opacity: 0;
  transform: translateY(6px);
}
.page-fade-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}
</style>
