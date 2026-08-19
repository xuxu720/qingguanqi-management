import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: () => import('@/layouts/MainLayout.vue'),
      redirect: '/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'Dashboard',
          component: () => import('@/views/dashboard/Dashboard.vue'),
          meta: { title: '首页' },
        },
        {
          path: 'pigs',
          name: 'Pig',
          component: () => import('@/views/pig/PigList.vue'),
          meta: { title: '清管器管理' },
        },
        {
          path: 'pipelines',
          name: 'Pipeline',
          component: () => import('@/views/pipeline/PipelineList.vue'),
          meta: { title: '管线管理' },
        },
        {
          path: 'stations',
          name: 'Station',
          component: () => import('@/views/station/StationList.vue'),
          meta: { title: '站点管理' },
        },
        {
          path: 'segments',
          name: 'Segment',
          component: () => import('@/views/segment/PipelineSegmentList.vue'),
          meta: { title: '管段管理' },
        },
        {
          path: 'operations',
          name: 'Operation',
          component: () => import('@/views/operation/OperationList.vue'),
          meta: { title: '清管作业' },
        },
        {
          path: 'operations/create',
          name: 'OperationCreate',
          component: () => import('@/views/operation/OperationCreate.vue'),
          meta: { title: '新建作业' },
        },
        {
          path: 'operations/:id',
          name: 'OperationDetail',
          component: () => import('@/views/operation/OperationDetail.vue'),
          meta: { title: '作业跟踪' },
        },
        {
          path: 'calc',
          name: 'Calc',
          component: () => import('@/views/calc/PredictionCalc.vue'),
          meta: { title: '预测计算' },
        },
        {
          path: 'warnings',
          name: 'Warning',
          component: () => import('@/views/warning/WarningList.vue'),
          meta: { title: '预警管理' },
        },
        {
          path: 'agent',
          name: 'Agent',
          component: () => import('@/views/agent/ChatView.vue'),
          meta: { title: '智能助手' },
        },
      ],
    },
  ],
})

export default router
