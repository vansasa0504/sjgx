import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import axiosMockAdapter from 'axios-mock-adapter'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import BillingView from '../views/BillingView.vue'
import PartnerView from '../views/PartnerView.vue'
import StatsView from '../views/StatsView.vue'
import { api, fetchDashboard } from '../api/client'
import { permittedRoutes } from '../router'

vi.mock('echarts', () => ({ init: () => ({ setOption: vi.fn() }) }))

describe('M4 console', () => {
  it('renders core governance pages', () => {
    setActivePinia(createPinia())
    expect(mount(PartnerView, { global: { plugins: [ElementPlus] } }).text()).toContain('合作方管理')
    expect(mount(BillingView, { global: { plugins: [ElementPlus] } }).text()).toContain('计费管理')
    expect(mount(StatsView, { global: { plugins: [ElementPlus] } }).text()).toContain('统计监管')
  })

  it('calls dashboard api through axios wrapper', async () => {
    const mock = new axiosMockAdapter(api)
    mock.onGet('/stats/dashboard').reply(200, { invokeCount: 10 })

    await expect(fetchDashboard()).resolves.toEqual({ invokeCount: 10 })
  })

  it('filters routes by permission codes', () => {
    const routes = permittedRoutes(['billing:view'])
    expect(routes.some((route) => route.path === '/billing')).toBe(true)
    expect(routes.some((route) => route.path === '/quality')).toBe(false)
  })
})
