import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import LoginView from '../views/LoginView.vue'
import PartnerView from '../views/PartnerView.vue'
import IngestView from '../views/IngestView.vue'
import ServiceView from '../views/ServiceView.vue'
import CatalogView from '../views/CatalogView.vue'
import ConsumerView from '../views/ConsumerView.vue'
import QualityView from '../views/QualityView.vue'
import BillingView from '../views/BillingView.vue'
import StatsView from '../views/StatsView.vue'
import SystemView from '../views/SystemView.vue'
import MonitorView from '../views/MonitorView.vue'

export const routes: RouteRecordRaw[] = [
  { path: '/login', component: LoginView, meta: { public: true, title: '登录' } },
  { path: '/', redirect: '/partners' },
  { path: '/partners', component: PartnerView, meta: { permission: 'partner:view', title: '合作方管理' } },
  { path: '/ingest', component: IngestView, meta: { permission: 'ingest:view', title: '接入任务' } },
  { path: '/services', component: ServiceView, meta: { permission: 'service:view', title: '数据服务' } },
  { path: '/catalog', component: CatalogView, meta: { permission: 'catalog:view', title: '数据目录' } },
  { path: '/consumers', component: ConsumerView, meta: { permission: 'consumer:view', title: '消费方管理' } },
  { path: '/quality', component: QualityView, meta: { permission: 'quality:view', title: '数据质量' } },
  { path: '/billing', component: BillingView, meta: { permission: 'billing:view', title: '计费管理' } },
  { path: '/stats', component: StatsView, meta: { permission: 'stats:view', title: '统计监管' } },
  { path: '/system', component: SystemView, meta: { permission: 'system:view', title: '系统管理' } },
  { path: '/monitor', component: MonitorView, meta: { permission: 'stats:view', title: '监控大屏' } }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to) => {
  const auth = useAuthStore()
  const permission = to.meta.permission as string | undefined
  if (!to.meta.public && permission && !auth.hasPermission(permission)) {
    return '/login'
  }
  return true
})

export function permittedRoutes(permissions: string[]) {
  return routes.filter((route) => !route.meta?.permission || permissions.includes(route.meta.permission as string))
}

export default router