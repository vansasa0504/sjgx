import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '../auth'

vi.mock('../../api/auth', () => ({
  login: vi.fn().mockResolvedValue('token-1'),
  fetchPermissions: vi.fn().mockResolvedValue(['partner:view']),
  refresh: vi.fn().mockResolvedValue('token-2'),
  logout: vi.fn().mockResolvedValue(undefined)
}))

describe('auth store', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
  })

  it('loads permissions after login', async () => {
    const auth = useAuthStore()
    await auth.login('admin', 'admin123')

    expect(auth.token).toBe('token-1')
    expect(auth.username).toBe('admin')
    expect(auth.hasPermission('partner:view')).toBe(true)
    expect(localStorage.getItem('token')).toBe('token-1')
  })

  it('clears state on logout', async () => {
    const auth = useAuthStore()
    await auth.login('admin', 'admin123')
    await auth.logout()

    expect(auth.token).toBe('')
    expect(auth.permissions).toEqual([])
    expect(localStorage.getItem('token')).toBeNull()
  })
})
