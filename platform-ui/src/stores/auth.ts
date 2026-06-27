import { defineStore } from 'pinia'
import * as authApi from '../api/auth'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    permissions: [] as string[],
    username: localStorage.getItem('username') || ''
  }),
  actions: {
    async login(username: string, password: string) {
      const token = await authApi.login(username, password)
      this.token = token
      this.username = username
      localStorage.setItem('token', this.token)
      localStorage.setItem('username', username)
      await this.fetchPermissions()
    },
    async fetchPermissions() {
      if (!this.token) {
        this.permissions = []
        return
      }
      this.permissions = await authApi.fetchPermissions()
    },
    async refresh() {
      const token = await authApi.refresh()
      this.token = token
      localStorage.setItem('token', token)
    },
    async logout(options?: { remote?: boolean }) {
      const shouldCallRemote = options?.remote !== false && Boolean(this.token)
      try {
        if (shouldCallRemote) {
          await authApi.logout()
        }
      } catch {
        // 本地登出优先，服务端失败不阻断清理状态。
      } finally {
        this.token = ''
        this.permissions = []
        this.username = ''
        localStorage.removeItem('token')
        localStorage.removeItem('username')
      }
    },
    hasPermission(permission: string) {
      return this.permissions.includes(permission)
    },
    hasAnyPermission(permissions: string[]) {
      return permissions.some((permission) => this.hasPermission(permission))
    }
  }
})
