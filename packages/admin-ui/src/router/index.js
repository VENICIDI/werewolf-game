import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', redirect: '/dashboard' },
  { path: '/dashboard', component: () => import('../views/Dashboard.vue') },
  { path: '/knowledge', component: () => import('../views/Knowledge.vue') },
  { path: '/knowledge/:name', component: () => import('../views/KnowledgeDetail.vue'), props: true },
  { path: '/search', component: () => import('../views/SearchPlayground.vue') },
  { path: '/retrieval-logs', component: () => import('../views/RetrievalLogs.vue') },
  { path: '/llm-logs', component: () => import('../views/LlmLogs.vue') },
  { path: '/llm-logs/:gameId', component: () => import('../views/LlmLogDetail.vue'), props: true },
  { path: '/system', component: () => import('../views/SystemStatus.vue') },
]

const router = createRouter({
  history: createWebHistory('/admin/'),
  routes,
})

export default router
