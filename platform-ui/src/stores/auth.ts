import { defineStore } from 'pinia'
import axios from 'axios'

const DEFAULT_PERMISSIONS = [
  'partner:view',
  'ingest:view',
  'service:view',
  'catalog:view',
  'consumer:view',
  'quality:view',
  'billing:view',
  'stats:view',
  'system:view'
]

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    permissions: DEFAULT_PERMISSIONS
  }),
  actions: {
    async login(username: string, password: string) {
      const { data } = await axios.post('/auth/login', { username, password })
      if (!data?.success || !data?.data?.token) {
        throw new Error(data?.message || '登录失败')
      }
      this.token = data.data.token
      localStorage.setItem('token', this.token)
    },
    logout() {
      this.token = ''
      localStorage.removeItem('token')
    },
    hasPermission(permission: string) {
      return this.permissions.includes(permission)
    }
  }
})
