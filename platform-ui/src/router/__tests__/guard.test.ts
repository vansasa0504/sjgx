import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import router, { permittedRoutes } from '../index'
import { useAuthStore } from '../../stores/auth'

vi.mock('../../api/auth', () => ({
  fetchPermissions: vi.fn().mockResolvedValue(['partner:view']),
  logout: vi.fn().mockResolvedValue(undefined)
}))

describe('router guard', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
  })

  it('filters permitted routes', () => {
    expect(permittedRoutes(['billing:view']).map((route) => route.path)).toContain('billing')
    expect(permittedRoutes(['billing:view']).map((route) => route.path)).not.toContain('quality')
  })

  it('redirects anonymous user to login', async () => {
    await router.push('/partners')
    await router.isReady()
    expect(router.currentRoute.value.path).toBe('/login')
  })

  it('redirects user without permission to 403', async () => {
    const auth = useAuthStore()
    auth.token = 'token'
    auth.permissions = ['partner:view']
    await router.push('/quality')
    await router.isReady()
    expect(router.currentRoute.value.path).toBe('/403')
  })
})
