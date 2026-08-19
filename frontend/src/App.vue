<template>
  <router-view />
</template>

<script setup lang="ts">
import { useTheme } from '@/composables/useTheme'
useTheme() // apply on startup
</script>

<style>
/* ===================================================================
   主题 1 — Gold  主色 #002EA6 / 点缀 #FFE76F
   =================================================================== */
:root,
[data-theme="gold"] {
  --el-color-primary: #002EA6;
  --el-color-primary-light-3: #1a4fcc;
  --el-color-primary-light-5: #3359b8;
  --el-color-primary-light-7: #8099d9;
  --el-color-primary-light-8: #b3c2e8;
  --el-color-primary-light-9: #d9e0f3;
  --el-color-primary-dark-2: #002585;

  --el-color-warning: #FFE76F;
  --el-color-warning-light-3: #ffed8c;
  --el-color-warning-light-5: #fff0a3;
  --el-color-warning-light-7: #fff5c2;
  --el-color-warning-light-9: #fffae0;
  --el-color-warning-dark-2: #e6c940;

  /* 侧边栏 */
  --app-sidebar-start: #002EA6;
  --app-sidebar-end: #002585;
  --app-sidebar-accent: #FFE76F;
  --app-sidebar-text: rgba(255,255,255,0.75);
  --app-sidebar-text-active: #FFE76F;
  --app-sidebar-hover-bg: rgba(255,255,255,0.1);
  --app-sidebar-active-bg: rgba(255,231,111,0.15);
  --app-sidebar-logo-border: rgba(255,255,255,0.1);
  --app-sidebar-collapse: rgba(255,255,255,0.5);
  --app-badge-bg: #FFE76F;
  --app-badge-text: #002EA6;

  /* Element 弹出菜单 */
  --app-popup-bg: #002EA6;
  --app-popup-text: rgba(255,255,255,0.75);
  --app-popup-hover-bg: rgba(255,255,255,0.08);
  --app-popup-active: #FFE76F;
}

/* ===================================================================
   主题 2 — Ocean  主色 #113056 / 点缀 #91CFD5
   =================================================================== */
[data-theme="ocean"] {
  --el-color-primary: #113056;
  --el-color-primary-light-3: #2d5278;
  --el-color-primary-light-5: #4a6d91;
  --el-color-primary-light-7: #89a3bd;
  --el-color-primary-light-8: #b9c9da;
  --el-color-primary-light-9: #dce4ec;
  --el-color-primary-dark-2: #0e2745;

  --el-color-warning: #91CFD5;
  --el-color-warning-light-3: #a7d9dd;
  --el-color-warning-light-5: #bce2e6;
  --el-color-warning-light-7: #d1ecee;
  --el-color-warning-light-9: #e8f5f6;
  --el-color-warning-dark-2: #7ab8bd;

  /* 侧边栏 */
  --app-sidebar-start: #113056;
  --app-sidebar-end: #0e2745;
  --app-sidebar-accent: #91CFD5;
  --app-sidebar-text: rgba(255,255,255,0.75);
  --app-sidebar-text-active: #91CFD5;
  --app-sidebar-hover-bg: rgba(255,255,255,0.08);
  --app-sidebar-active-bg: rgba(145,207,213,0.15);
  --app-sidebar-logo-border: rgba(255,255,255,0.08);
  --app-sidebar-collapse: rgba(255,255,255,0.45);
  --app-badge-bg: #91CFD5;
  --app-badge-text: #113056;

  /* Element 弹出菜单 */
  --app-popup-bg: #113056;
  --app-popup-text: rgba(255,255,255,0.75);
  --app-popup-hover-bg: rgba(255,255,255,0.08);
  --app-popup-active: #91CFD5;
}

/* ===== Element Plus 弹出菜单（非 scoped，使用 CSS 变量） ===== */
.el-menu--popup {
  --el-menu-bg-color: var(--app-popup-bg);
  --el-menu-text-color: var(--app-popup-text);
  --el-menu-hover-bg-color: var(--app-popup-hover-bg);
  --el-menu-active-color: var(--app-popup-active);
}

/* ===== 全局重置 ===== */
html, body, #app {
  margin: 0;
  padding: 0;
  height: 100%;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC',
    'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  color: #303133;
  background: #f5f6f8;
}

/* ── Impeccable motion tokens (product: expo curves, no bounce) ── */
:root {
  --ease-out-expo: cubic-bezier(0.16, 1, 0.3, 1);   /* ease-out-expo */
  --ease-out-quint: cubic-bezier(0.22, 1, 0.36, 1);  /* ease-out-quint */
  --ring-card: 0 0 0 1px rgba(0,0,0,0.06), 0 1px 3px rgba(0,0,0,0.04);
  --shadow-float: 0 4px 24px rgba(0,0,0,0.06);
}

/* ===== Element Plus 组件全局微调 ===== */

/* 主按钮 — taste-skill magnetic */
.el-button--primary {
  --el-button-bg-color: var(--el-color-primary);
  --el-button-border-color: var(--el-color-primary);
  --el-button-hover-bg-color: var(--el-color-primary-light-3);
  --el-button-hover-border-color: var(--el-color-primary-light-3);
  --el-button-active-bg-color: var(--el-color-primary-dark-2);
  --el-button-active-border-color: var(--el-color-primary-dark-2);
  transition: transform 0.15s var(--ease-out-expo), box-shadow 0.2s var(--ease-out-expo);
}
.el-button--primary:active { transform: scale(0.98); }

/* 点缀色按钮（用 warning 通道承载） */
.el-button--warning {
  --el-button-bg-color: var(--el-color-warning);
  --el-button-border-color: var(--el-color-warning);
  --el-button-text-color: var(--el-color-primary);
  --el-button-hover-bg-color: var(--el-color-warning-light-3);
  --el-button-hover-border-color: var(--el-color-warning-light-3);
  --el-button-hover-text-color: var(--el-color-primary);
  --el-button-active-bg-color: var(--el-color-warning-dark-2);
  --el-button-active-border-color: var(--el-color-warning-dark-2);
}

.el-tag--primary {
  --el-tag-bg-color: var(--el-color-primary-light-9);
  --el-tag-border-color: var(--el-color-primary-light-8);
  --el-tag-text-color: var(--el-color-primary);
}

.el-table th.el-table__cell {
  background: #f5f7fa;
  color: var(--el-color-primary);
  font-weight: 600;
}

.el-pagination .el-pager li.is-active {
  background-color: var(--el-color-primary-light-3);
  color: #fff;
}

.el-pagination .btn-prev,
.el-pagination .btn-next {
  background: transparent;
}

.el-tabs__item.is-active { color: var(--el-color-primary); }
.el-tabs__active-bar { background-color: var(--el-color-primary); }

.el-checkbox__input.is-checked .el-checkbox__inner,
.el-radio__input.is-checked .el-radio__inner {
  background-color: var(--el-color-primary);
  border-color: var(--el-color-primary);
}

:root,
[data-theme="gold"],
[data-theme="ocean"] {
  --el-border-radius-base: 6px;
  --el-border-radius-small: 4px;
}
</style>
