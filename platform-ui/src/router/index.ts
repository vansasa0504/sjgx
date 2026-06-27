import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import ConsoleLayout from '../layouts/ConsoleLayout.vue'
import LoginView from '../views/LoginView.vue'
import ForbiddenView from '../views/ForbiddenView.vue'
import NotFoundView from '../views/NotFoundView.vue'
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
  { path: '/403', component: ForbiddenView, meta: { public: true, title: '权限不足' } },
  { path: '/404', component: NotFoundView, meta: { public: true, title: '页面不存在' } },
  {
    path: '/',
    component: ConsoleLayout,
    redirect: '/partners',
    children: [
      { path: 'partners', component: PartnerView, meta: { permission: 'partner:view', title: '合作方管理' } },
      { path: 'ingest', component: IngestView, meta: { permission: 'ingest:view', title: '接入任务' } },
      { path: 'services', component: ServiceView, meta: { permission: 'service:view', title: '数据服务' } },
      { path: 'catalog', component: CatalogView, meta: { permission: 'catalog:view', title: '数据目录' } },
      { path: 'consumers', component: ConsumerView, meta: { permission: 'consumer:view', title: '消费方管理' } },
      { path: 'quality', component: QualityView, meta: { permission: 'quality:view', title: '数据质量' } },
      { path: 'billing', component: BillingView, meta: { permission: 'billing:view', title: '计费管理' } },
      { path: 'stats', component: StatsView, meta: { permission: 'stats:view', title: '统计监管' } },
      { path: 'system', component: SystemView, meta: { permission: 'system:view', title: '系统管理' } },
      { path: 'monitor', component: MonitorView, meta: { permission: 'stats:view', title: '监控大屏' } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/404' }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (to.path === '/login') {
    if (!auth.token) return true
    if (auth.permissions.length === 0) {
      try {
        await auth.fetchPermissions()
      } catch {
        await auth.logout({ remote: false })
        return true
      }
    }
    return firstPermittedPath(auth.permissions)
  }
  if (to.meta.public) {
    return true
  }
  if (!auth.token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (auth.permissions.length === 0) {
    try {
      await auth.fetchPermissions()
    } catch {
      await auth.logout()
      return { path: '/login', query: { redirect: to.fullPath } }
    }
  }
  const permission = to.meta.permission as string | undefined
  const permissions = to.meta.permissions as string[] | undefined
  if (permission && !auth.hasPermission(permission)) {
    return '/403'
  }
  if (permissions?.length && !auth.hasAnyPermission(permissions)) {
    return '/403'
  }
  return true
})

export function permittedRoutes(permissions: string[]) {
  const consoleRoute = routes.find((route) => route.path === '/')
  return (consoleRoute?.children || []).filter((route) => {
    const permission = route.meta?.permission as string | undefined
    return !permission || permissions.includes(permission)
  })
}

export function firstPermittedPath(permissions: string[]) {
  const first = permittedRoutes(permissions)[0]
  return first ? `/${first.path}` : '/403'
}

export default router
