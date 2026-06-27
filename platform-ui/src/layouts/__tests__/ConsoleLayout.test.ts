import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import router from '../../router'
import { useAuthStore } from '../../stores/auth'
import ConsoleLayout from '../ConsoleLayout.vue'

vi.mock('../../api/auth', () => ({
  logout: vi.fn().mockResolvedValue(undefined)
}))

describe('ConsoleLayout', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('shows menu by permission and logs out', async () => {
    const auth = useAuthStore()
    auth.username = 'admin'
    auth.permissions = ['partner:view']
    const wrapper = mount(ConsoleLayout, { global: { plugins: [ElementPlus, router] } })

    expect(wrapper.text()).toContain('合作方管理')
    expect(wrapper.text()).not.toContain('数据质量')
    await wrapper.find('button').trigger('click')
    expect(auth.token).toBe('')
  })
})
