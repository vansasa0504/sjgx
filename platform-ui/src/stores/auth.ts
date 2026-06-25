import { defineStore } from 'pinia'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('token') || 'mock-token',
    permissions: ['partner:view', 'ingest:view', 'service:view', 'catalog:view', 'consumer:view', 'quality:view', 'billing:view', 'stats:view', 'system:view']
  }),
  actions: {
    login() {
      this.token = 'mock-token'
      localStorage.setItem('token', this.token)
    },
    hasPermission(permission: string) {
      return this.permissions.includes(permission)
    }
  }
})