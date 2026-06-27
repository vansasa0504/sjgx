import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import PartnerView from '../PartnerView.vue'
import IngestView from '../IngestView.vue'
import ServiceView from '../ServiceView.vue'
import CatalogView from '../CatalogView.vue'
import ConsumerView from '../ConsumerView.vue'
import QualityView from '../QualityView.vue'
import BillingView from '../BillingView.vue'
import StatsView from '../StatsView.vue'
import SystemView from '../SystemView.vue'
import MonitorView from '../MonitorView.vue'
import { useAuthStore } from '../../stores/auth'
import * as partnerApi from '../../api/partner'
import * as ingestApi from '../../api/ingest'
import * as serviceApi from '../../api/service'
import * as catalogApi from '../../api/catalog'
import * as consumerApi from '../../api/consumer'
import * as qualityApi from '../../api/quality'
import * as billingApi from '../../api/billing'
import * as statsApi from '../../api/stats'
import * as systemApi from '../../api/system'

vi.mock('echarts', () => ({ init: () => ({ setOption: vi.fn(), dispose: vi.fn() }) }))
vi.mock('../../api/partner', () => ({
  listPartners: vi.fn().mockResolvedValue({ records: [{ id: 1, name: '合作方A', status: 'DRAFT' }], total: 1, current: 1, size: 10 }),
  createPartner: vi.fn().mockResolvedValue({ id: 2, name: '新合作方' }),
  getPartner: vi.fn().mockResolvedValue({ id: 1, name: '合作方A' }),
  listInterfaces: vi.fn().mockResolvedValue([]),
  listPartnerEvents: vi.fn().mockResolvedValue([]),
  updatePartner: vi.fn(),
  submitPartner: vi.fn(),
  approvePartner: vi.fn(),
  admitPartner: vi.fn(),
  rejectPartner: vi.fn(),
  ratePartner: vi.fn(),
  terminatePartner: vi.fn(),
  configureInterface: vi.fn()
}))
vi.mock('../../api/ingest', () => ({
  listIngestTasks: vi.fn().mockResolvedValue([{ id: 1, partnerId: 1, status: 'DRAFT' }]),
  createIngestTask: vi.fn().mockResolvedValue({ id: 2 }),
  getIngestTask: vi.fn(),
  testIngest: vi.fn().mockResolvedValue([]),
  updateMapping: vi.fn(),
  updateRules: vi.fn(),
  listIngestRecords: vi.fn().mockResolvedValue({ records: [], total: 0, current: 1, size: 10 }),
  submitIngest: vi.fn(),
  approveIngest: vi.fn(),
  offlineIngest: vi.fn()
}))
vi.mock('../../api/service', () => ({
  listServices: vi.fn().mockResolvedValue([{ serviceCode: 'svc', name: '服务', routeKey: 'r' }]),
  registerService: vi.fn().mockResolvedValue({ serviceCode: 'svc2' }),
  getService: vi.fn(),
  updateService: vi.fn(),
  listServiceLogs: vi.fn().mockResolvedValue({ records: [], total: 0, current: 1, size: 10 }),
  testService: vi.fn(),
  defineService: vi.fn(),
  publishService: vi.fn(),
  offlineService: vi.fn()
}))
vi.mock('../../api/catalog', () => ({
  listCatalog: vi.fn().mockResolvedValue([{ id: 1, name: '目录A', dataType: 'JSON' }]),
  searchCatalog: vi.fn().mockResolvedValue([{ id: 2, name: '搜索结果' }]),
  getCatalogMeta: vi.fn(),
  previewCatalog: vi.fn(),
  applyCatalog: vi.fn(),
  approveApplication: vi.fn()
}))
vi.mock('../../api/consumer', () => ({
  listConsumers: vi.fn().mockResolvedValue([{ id: 1, name: '消费方A' }]),
  registerConsumer: vi.fn().mockResolvedValue({ id: 2 }),
  getConsumer: vi.fn(),
  configureQuota: vi.fn(),
  applyConsumerEvent: vi.fn(),
  getConsumerAudit: vi.fn().mockResolvedValue({ records: [], total: 0, current: 1, size: 10 }),
  getConsumerLogs: vi.fn().mockResolvedValue({ records: [], total: 0, current: 1, size: 10 })
}))
vi.mock('../../api/quality', () => ({
  listQualityRules: vi.fn().mockResolvedValue([{ id: 1, ruleCode: 'R1', dimension: 'COMPLETENESS', field: 'name', weight: 10 }]),
  createQualityRule: vi.fn().mockResolvedValue({ id: 2 }),
  updateQualityRule: vi.fn(),
  listChecks: vi.fn().mockResolvedValue([]),
  triggerCheck: vi.fn().mockResolvedValue({}),
  listIssues: vi.fn().mockResolvedValue([]),
  assignIssue: vi.fn(),
  resolveIssue: vi.fn(),
  getQualityReport: vi.fn(),
  getQualityScore: vi.fn()
}))
vi.mock('../../api/billing', () => ({
  listBillingRules: vi.fn().mockResolvedValue([{ id: 1, ruleCode: 'B1', ruleName: '规则', unitPrice: 1 }]),
  createBillingRule: vi.fn(),
  updateBillingRule: vi.fn(),
  listBills: vi.fn().mockResolvedValue([{ billNo: 'NO1', status: 'GENERATED' }]),
  generateBill: vi.fn().mockResolvedValue({ billNo: 'NO2' }),
  confirmBill: vi.fn(),
  disputeBill: vi.fn(),
  getBillingStats: vi.fn().mockResolvedValue({})
}))
vi.mock('../../api/stats', () => ({
  fetchDashboard: vi.fn().mockResolvedValue({ invokeCount: 10, successRate: '99%', runningServices: 3, complianceScore: 98, costAmount: 12 }),
  generateReport: vi.fn().mockResolvedValue({ file: 'report.xlsx' }),
  listAudit: vi.fn().mockResolvedValue([{ traceId: 't1', eventType: 'login' }])
}))
vi.mock('../../api/system', () => ({
  listUsers: vi.fn().mockResolvedValue({ records: [{ username: 'admin', permissions: ['system:view'] }], total: 1, current: 1, size: 10 }),
  createUser: vi.fn(),
  updateUser: vi.fn(),
  listRoles: vi.fn().mockResolvedValue([{ name: 'admin', permissions: ['system:view'] }]),
  createRole: vi.fn(),
  updateRolePermissions: vi.fn(),
  listPermissions: vi.fn().mockResolvedValue(['system:view'])
}))

function prepare() {
  setActivePinia(createPinia())
  const auth = useAuthStore()
  auth.token = 'token'
  auth.permissions = [
    'partner:view', 'partner:create', 'partner:update', 'partner:approve',
    'ingest:view', 'ingest:create', 'ingest:update', 'ingest:approve',
    'service:view', 'service:create', 'service:update', 'service:approve',
    'catalog:view', 'catalog:apply', 'catalog:approve',
    'consumer:view', 'consumer:create', 'consumer:update', 'consumer:approve',
    'quality:view', 'quality:create', 'quality:update', 'quality:run',
    'billing:view', 'billing:create', 'billing:update', 'billing:approve', 'billing:run',
    'stats:view', 'system:view', 'system:create', 'system:update'
  ]
}

const global = { plugins: [ElementPlus] }

describe('M7-C pages', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    prepare()
  })

  it('loads partners and can open create dialog', async () => {
    const wrapper = mount(PartnerView, { global })
    await flushPromises()
    expect(partnerApi.listPartners).toHaveBeenCalled()
    await wrapper.findAll('button').find((button) => button.text().includes('新建合作方'))?.trigger('click')
    expect(wrapper.text()).toContain('新建合作方')
    const form = wrapper.findComponent({ name: 'FormDialog' })
    await form.props('submit')({ name: '新合作方' })
    expect(partnerApi.createPartner).toHaveBeenCalledWith({ name: '新合作方' })
  })

  it('only shows partner state actions that match backend status transitions', async () => {
    vi.mocked(partnerApi.listPartners).mockResolvedValueOnce({ records: [{ id: 1, name: '草稿合作方', status: 'DRAFT' }], total: 1, current: 1, size: 10 })
    const draftWrapper = mount(PartnerView, { global })
    await flushPromises()
    expect(draftWrapper.text()).not.toContain('提交')

    vi.mocked(partnerApi.listPartners).mockResolvedValueOnce({ records: [{ id: 2, name: '已注册合作方', status: 'REGISTERED' }], total: 1, current: 1, size: 10 })
    const registeredWrapper = mount(PartnerView, { global })
    await flushPromises()
    expect(registeredWrapper.text()).toContain('提交')
  })

  it('hides partner create button without permission', async () => {
    const auth = useAuthStore()
    auth.permissions = ['partner:view']
    const wrapper = mount(PartnerView, { global })
    await flushPromises()
    expect(wrapper.text()).not.toContain('新建合作方')
  })

  it('loads ingest tasks', async () => {
    const wrapper = mount(IngestView, { global })
    await flushPromises()
    expect(ingestApi.listIngestTasks).toHaveBeenCalled()
    await wrapper.findAll('button').find((button) => button.text().includes('创建任务'))?.trigger('click')
    await wrapper.findComponent({ name: 'FormDialog' }).props('submit')({ partnerId: 1, endpoint: 'http://example.com' })
    expect(ingestApi.createIngestTask).toHaveBeenCalled()
  })

  it('loads services', async () => {
    const wrapper = mount(ServiceView, { global })
    await flushPromises()
    expect(serviceApi.listServices).toHaveBeenCalled()
    await wrapper.findAll('button').find((button) => button.text().includes('注册服务'))?.trigger('click')
    await wrapper.findComponent({ name: 'FormDialog' }).props('submit')({ serviceCode: 'svc2', name: '服务2', routeKey: 'r2' })
    expect(serviceApi.registerService).toHaveBeenCalled()
  })

  it('loads service logs through paged table drawer', async () => {
    vi.mocked(serviceApi.listServices).mockResolvedValueOnce([{ serviceCode: 'svc', name: '服务', routeKey: 'r', status: 'PUBLISHED' }])
    vi.mocked(serviceApi.listServiceLogs).mockResolvedValueOnce({ records: [{ traceId: 'trace-1', status: 'SUCCESS' }], total: 1, current: 1, size: 10 })
    const wrapper = mount(ServiceView, { global })
    await flushPromises()
    await (wrapper.vm as unknown as { openLogs: (row: { serviceCode: string }) => Promise<void> }).openLogs({ serviceCode: 'svc' })
    await flushPromises()
    expect(serviceApi.listServiceLogs).toHaveBeenCalledWith('svc', expect.objectContaining({ page: 1, size: 10 }))
    expect(wrapper.text()).toContain('trace-1')
  })

  it('loads catalog and searches', async () => {
    const wrapper = mount(CatalogView, { global })
    await flushPromises()
    expect(catalogApi.listCatalog).toHaveBeenCalled()
    await wrapper.find('input').setValue('目录')
    await wrapper.findAll('button').find((button) => button.text().includes('检索'))?.trigger('click')
    expect(catalogApi.searchCatalog).toHaveBeenCalled()
  })

  it('approves the application created for the selected catalog item', async () => {
    vi.mocked(catalogApi.applyCatalog).mockResolvedValueOnce({ id: 99 })
    const wrapper = mount(CatalogView, { global })
    await flushPromises()
    await wrapper.findAll('button').find((button) => button.text().includes('申请'))?.trigger('click')
    await wrapper.findComponent({ name: 'FormDialog' }).props('submit')({ reason: '测试', scope: '回归' })
    await flushPromises()
    await wrapper.findAll('button').find((button) => button.text().includes('审批申请'))?.trigger('click')
    expect(catalogApi.approveApplication).toHaveBeenCalledWith(99)
  })

  it('loads consumers', async () => {
    const wrapper = mount(ConsumerView, { global })
    await flushPromises()
    expect(consumerApi.listConsumers).toHaveBeenCalled()
    await wrapper.findAll('button').find((button) => button.text().includes('注册消费方'))?.trigger('click')
    await wrapper.findComponent({ name: 'FormDialog' }).props('submit')({ code: 'c1', name: '消费方' })
    expect(consumerApi.registerConsumer).toHaveBeenCalled()
  })

  it('loads consumer audit and logs through paged table drawer', async () => {
    vi.mocked(consumerApi.listConsumers).mockResolvedValueOnce([{ id: 1, name: '消费方A', status: 'ENABLED' }])
    vi.mocked(consumerApi.getConsumerAudit).mockResolvedValueOnce({ records: [{ traceId: 'audit-1', eventType: 'APPROVE' }], total: 1, current: 1, size: 10 })
    const wrapper = mount(ConsumerView, { global })
    await flushPromises()
    await (wrapper.vm as unknown as { openAudit: (row: { id: number; name: string }) => Promise<void> }).openAudit({ id: 1, name: '消费方A' })
    await flushPromises()
    expect(consumerApi.getConsumerAudit).toHaveBeenCalledWith(1, expect.objectContaining({ page: 1, size: 10 }))
    expect(wrapper.text()).toContain('audit-1')
  })

  it('loads quality rules and checks', async () => {
    const wrapper = mount(QualityView, { global })
    await flushPromises()
    expect(qualityApi.listQualityRules).toHaveBeenCalled()
    await wrapper.findAll('button').find((button) => button.text().includes('新建规则'))?.trigger('click')
    await wrapper.findComponent({ name: 'FormDialog' }).props('submit')({ ruleCode: 'R2', dimension: 'COMPLETENESS', field: 'name', weight: 10 })
    expect(qualityApi.createQualityRule).toHaveBeenCalled()
  })

  it('loads billing rules and bills', async () => {
    const wrapper = mount(BillingView, { global })
    await flushPromises()
    expect(billingApi.listBillingRules).toHaveBeenCalled()
    await wrapper.findAll('button').find((button) => button.text().includes('生成账单'))?.trigger('click')
    await wrapper.findComponent({ name: 'FormDialog' }).props('submit')({ billType: 'CONSUMER', period: 'MONTHLY' })
    expect(billingApi.generateBill).toHaveBeenCalled()
  })

  it('loads stats dashboard and audit', async () => {
    const wrapper = mount(StatsView, { global })
    await flushPromises()
    expect(statsApi.fetchDashboard).toHaveBeenCalled()
    expect(statsApi.listAudit).toHaveBeenCalled()
    expect(wrapper.text()).toContain('服务数 3')
  })

  it('loads system users and roles', async () => {
    mount(SystemView, { global })
    await flushPromises()
    expect(systemApi.listUsers).toHaveBeenCalled()
    expect(systemApi.listRoles).toHaveBeenCalled()
  })

  it('loads monitor dashboard', async () => {
    vi.useFakeTimers()
    mount(MonitorView, { global })
    await flushPromises()
    expect(statsApi.fetchDashboard).toHaveBeenCalled()
    vi.useRealTimers()
  })
})
