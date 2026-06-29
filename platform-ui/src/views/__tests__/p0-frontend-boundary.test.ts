import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus, { ElMessage, ElMessageBox } from 'element-plus'
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
  listPartners: vi.fn(),
  createPartner: vi.fn(),
  getPartner: vi.fn(),
  listInterfaces: vi.fn(),
  listPartnerEvents: vi.fn(),
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
  listIngestTasks: vi.fn(),
  createIngestTask: vi.fn(),
  getIngestTask: vi.fn(),
  testIngest: vi.fn(),
  updateMapping: vi.fn(),
  updateRules: vi.fn(),
  listIngestRecords: vi.fn(),
  submitIngest: vi.fn(),
  approveIngest: vi.fn(),
  offlineIngest: vi.fn()
}))
vi.mock('../../api/service', () => ({
  listServices: vi.fn(),
  registerService: vi.fn(),
  getService: vi.fn(),
  updateService: vi.fn(),
  defineService: vi.fn(),
  testService: vi.fn(),
  publishService: vi.fn(),
  offlineService: vi.fn(),
  listServiceLogs: vi.fn(),
  listServiceCredentials: vi.fn(),
  createServiceCredential: vi.fn(),
  rotateServiceCredential: vi.fn(),
  disableServiceCredential: vi.fn()
}))
vi.mock('../../api/catalog', () => ({
  listCatalog: vi.fn(),
  searchCatalog: vi.fn(),
  getCatalogMeta: vi.fn(),
  previewCatalog: vi.fn(),
  applyCatalog: vi.fn(),
  approveApplication: vi.fn(),
  rejectApplication: vi.fn()
}))
vi.mock('../../api/consumer', () => ({
  listConsumers: vi.fn(),
  registerConsumer: vi.fn(),
  getConsumer: vi.fn(),
  configureQuota: vi.fn(),
  applyConsumerEvent: vi.fn(),
  getConsumerAudit: vi.fn(),
  getConsumerLogs: vi.fn()
}))
vi.mock('../../api/quality', () => ({
  listQualityRules: vi.fn(),
  createQualityRule: vi.fn(),
  updateQualityRule: vi.fn(),
  listChecks: vi.fn(),
  triggerCheck: vi.fn(),
  listIssues: vi.fn(),
  assignIssue: vi.fn(),
  resolveIssue: vi.fn(),
  getQualityReport: vi.fn(),
  getQualityScore: vi.fn()
}))
vi.mock('../../api/billing', () => ({
  listBillingRules: vi.fn(),
  createBillingRule: vi.fn(),
  updateBillingRule: vi.fn(),
  listBills: vi.fn(),
  getBill: vi.fn(),
  generateBill: vi.fn(),
  confirmBill: vi.fn(),
  disputeBill: vi.fn(),
  getBillingStats: vi.fn()
}))
vi.mock('../../api/stats', () => ({
  fetchDashboard: vi.fn(),
  generateReport: vi.fn(),
  listAudit: vi.fn(),
  verifyAudit: vi.fn()
}))
vi.mock('../../api/system', () => ({
  listUsers: vi.fn(),
  createUser: vi.fn(),
  updateUser: vi.fn(),
  listRoles: vi.fn(),
  createRole: vi.fn(),
  updateRolePermissions: vi.fn(),
  listPermissions: vi.fn()
}))

const global = { plugins: [ElementPlus] }
const allPermissions = [
  'partner:view', 'partner:create', 'partner:update', 'partner:approve',
  'ingest:view', 'ingest:create', 'ingest:update', 'ingest:approve',
  'service:view', 'service:create', 'service:update', 'service:approve',
  'catalog:view', 'catalog:apply', 'catalog:approve',
  'consumer:view', 'consumer:create', 'consumer:update', 'consumer:approve',
  'quality:view', 'quality:create', 'quality:update', 'quality:run',
  'billing:view', 'billing:create', 'billing:update', 'billing:approve', 'billing:run',
  'stats:view', 'system:view', 'system:create', 'system:update'
]

function page<T>(records: T[]) {
  return { records, total: records.length, current: 1, size: 10 }
}

function resetApiMocks() {
  vi.mocked(partnerApi.listPartners).mockResolvedValue(page([{ id: 1, partnerCode: 'P1', name: '合作方A', status: 'REGISTERED' }]))
  vi.mocked(partnerApi.createPartner).mockResolvedValue({ id: 2, name: '合作方B' })
  vi.mocked(partnerApi.getPartner).mockResolvedValue({ id: 1, name: '合作方A' })
  vi.mocked(partnerApi.listInterfaces).mockResolvedValue([])
  vi.mocked(partnerApi.listPartnerEvents).mockResolvedValue([])
  vi.mocked(partnerApi.submitPartner).mockResolvedValue({ id: 1, name: '合作方A', status: 'SUBMITTED' })
  vi.mocked(partnerApi.approvePartner).mockResolvedValue({ id: 1, name: '合作方A', status: 'APPROVED' })
  vi.mocked(partnerApi.admitPartner).mockResolvedValue({ id: 1, name: '合作方A', status: 'ADMITTED' })
  vi.mocked(partnerApi.terminatePartner).mockResolvedValue({ id: 1, name: '合作方A', status: 'TERMINATED' })

  vi.mocked(ingestApi.listIngestTasks).mockResolvedValue([{ id: 1, partnerId: 1, endpoint: 'http://example.com', status: 'TESTING' }])
  vi.mocked(ingestApi.createIngestTask).mockResolvedValue({ id: 2, partnerId: 1 })
  vi.mocked(ingestApi.submitIngest).mockResolvedValue({ id: 1, partnerId: 1, status: 'PENDING_APPROVAL' })
  vi.mocked(ingestApi.approveIngest).mockResolvedValue({ id: 1, partnerId: 1, status: 'ONLINE' })
  vi.mocked(ingestApi.offlineIngest).mockResolvedValue({ id: 1, partnerId: 1, status: 'OFFLINE' })
  vi.mocked(ingestApi.listIngestRecords).mockResolvedValue(page([]))
  vi.mocked(ingestApi.testIngest).mockResolvedValue([])

  vi.mocked(serviceApi.listServices).mockResolvedValue([{ serviceCode: 'svc', name: '服务', routeKey: 'r', status: 'TESTED' }])
  vi.mocked(serviceApi.registerService).mockResolvedValue({ serviceCode: 'svc2', name: '服务2', routeKey: 'r2' })
  vi.mocked(serviceApi.publishService).mockResolvedValue({ serviceCode: 'svc', name: '服务', routeKey: 'r', status: 'PUBLISHED' })
  vi.mocked(serviceApi.offlineService).mockResolvedValue({ serviceCode: 'svc', name: '服务', routeKey: 'r', status: 'OFFLINE' })
  vi.mocked(serviceApi.defineService).mockResolvedValue({ serviceCode: 'svc', name: '服务', routeKey: 'r', status: 'DEFINED' })
  vi.mocked(serviceApi.testService).mockResolvedValue({ serviceCode: 'svc', name: '服务', routeKey: 'r', status: 'TESTED' })
  vi.mocked(serviceApi.listServiceLogs).mockResolvedValue(page([]))
  vi.mocked(serviceApi.listServiceCredentials).mockResolvedValue([])

  vi.mocked(catalogApi.listCatalog).mockResolvedValue([{ id: 1, name: '目录A', dataType: 'JSON' }])
  vi.mocked(catalogApi.searchCatalog).mockResolvedValue([{ id: 1, name: '目录A', dataType: 'JSON' }])
  vi.mocked(catalogApi.getCatalogMeta).mockResolvedValue({ id: 1, name: '目录A' })
  vi.mocked(catalogApi.previewCatalog).mockResolvedValue({ sample: [], stats: {}, qualityReport: '' })
  vi.mocked(catalogApi.applyCatalog).mockResolvedValue({ id: 99 })
  vi.mocked(catalogApi.approveApplication).mockResolvedValue({ id: 99 })
  vi.mocked(catalogApi.rejectApplication).mockResolvedValue({ id: 99 })

  vi.mocked(consumerApi.listConsumers).mockResolvedValue([{ id: 1, consumerCode: 'C1', name: '消费方A', status: 'REGISTERED' }])
  vi.mocked(consumerApi.registerConsumer).mockResolvedValue({ id: 2, name: '消费方B' })
  vi.mocked(consumerApi.applyConsumerEvent).mockResolvedValue({ id: 1, name: '消费方A', status: 'SUBMITTED' })
  vi.mocked(consumerApi.getConsumer).mockResolvedValue({ id: 1, name: '消费方A' })
  vi.mocked(consumerApi.getConsumerAudit).mockResolvedValue(page([]))
  vi.mocked(consumerApi.getConsumerLogs).mockResolvedValue(page([]))

  vi.mocked(qualityApi.listQualityRules).mockResolvedValue([{ id: 1, ruleCode: 'R1', dimension: 'COMPLETENESS', field: 'name', weight: 10 }])
  vi.mocked(qualityApi.createQualityRule).mockResolvedValue({ id: 2, ruleCode: 'R2', dimension: 'COMPLETENESS', field: 'name', weight: 10 })
  vi.mocked(qualityApi.listChecks).mockResolvedValue([])
  vi.mocked(qualityApi.listIssues).mockResolvedValue([{ id: 1, status: 'OPEN' }])
  vi.mocked(qualityApi.assignIssue).mockResolvedValue({})
  vi.mocked(qualityApi.resolveIssue).mockResolvedValue({})
  vi.mocked(qualityApi.triggerCheck).mockResolvedValue({})
  vi.mocked(qualityApi.getQualityReport).mockResolvedValue({})
  vi.mocked(qualityApi.getQualityScore).mockResolvedValue({})

  vi.mocked(billingApi.listBillingRules).mockResolvedValue([{ id: 1, ruleCode: 'B1', ruleName: '规则', billingModel: 'BY_COUNT', targetType: 'CONSUMER', unitPrice: 1 }])
  vi.mocked(billingApi.listBills).mockResolvedValue([{ billNo: 'NO1', status: 'GENERATED', totalAmount: 10 }])
  vi.mocked(billingApi.createBillingRule).mockResolvedValue({ id: 2, ruleCode: 'B2', ruleName: '规则2', billingModel: 'BY_COUNT', targetType: 'CONSUMER', unitPrice: 1 })
  vi.mocked(billingApi.generateBill).mockResolvedValue({ billNo: 'NO2' })
  vi.mocked(billingApi.confirmBill).mockResolvedValue({ billNo: 'NO1', status: 'CONFIRMED' })
  vi.mocked(billingApi.disputeBill).mockResolvedValue({ billNo: 'NO1', status: 'DISPUTED' })
  vi.mocked(billingApi.getBillingStats).mockResolvedValue({ totalAmount: 0, invokeCount: 0, billCount: 0, itemCount: 0, amountByItemType: {} })

  vi.mocked(statsApi.fetchDashboard).mockResolvedValue({ invokeCount: 10, successRate: '99%', runningServices: 3, costAmount: 12 })
  vi.mocked(statsApi.generateReport).mockResolvedValue({ file: 'report.json', rows: [] })
  vi.mocked(statsApi.listAudit).mockResolvedValue([{ traceId: 't1', eventType: 'login' }])
  vi.mocked(statsApi.verifyAudit).mockResolvedValue({ intact: true, totalChecked: 1 })

  vi.mocked(systemApi.listUsers).mockResolvedValue(page([{ username: 'admin', permissions: ['system:view'] }]))
  vi.mocked(systemApi.listRoles).mockResolvedValue([{ name: 'admin', permissions: ['system:view'] }])
  vi.mocked(systemApi.listPermissions).mockResolvedValue(['system:view'])
  vi.mocked(systemApi.createUser).mockResolvedValue({ username: 'new-user', permissions: [] })
  vi.mocked(systemApi.createRole).mockResolvedValue({ name: 'operator', permissions: [] })
}

function prepare(permissions = allPermissions) {
  setActivePinia(createPinia())
  const auth = useAuthStore()
  auth.token = 'token'
  auth.permissions = permissions
}

async function mountPage(component: unknown, permissions = allPermissions) {
  prepare(permissions)
  const wrapper = mount(component, { global, attachTo: document.body })
  await flushPromises()
  return wrapper
}

function textButton(wrapper: VueWrapper, text: string) {
  return wrapper.findAll('button').find((button) => button.text().includes(text))
}

async function clickButton(wrapper: VueWrapper, text: string) {
  const button = textButton(wrapper, text)
  expect(button, `button ${text}`).toBeTruthy()
  await button!.trigger('click')
  await flushPromises()
}

async function clickTab(wrapper: VueWrapper, text: string) {
  const tab = wrapper.findAll('.el-tabs__item').find((item) => item.text().includes(text))
  expect(tab, `tab ${text}`).toBeTruthy()
  await tab!.trigger('click')
  await flushPromises()
}

async function expectValidationBlocksSubmit(component: unknown, openText: string, submitApi: ReturnType<typeof vi.fn>) {
  const wrapper = await mountPage(component)
  await clickButton(wrapper, openText)
  await clickButton(wrapper, '确定')
  expect(submitApi).not.toHaveBeenCalled()
  wrapper.unmount()
}

describe('P0-09 frontend boundary states', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
    vi.restoreAllMocks()
    resetApiMocks()
    vi.spyOn(ElMessage, 'error').mockImplementation(() => ({ close: vi.fn() }) as never)
    vi.spyOn(ElMessage, 'success').mockImplementation(() => ({ close: vi.fn() }) as never)
    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as never)
    Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: vi.fn(() => 'blob:report') })
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: vi.fn() })
  })

  const emptyCases = [
    ['PartnerView', PartnerView, () => vi.mocked(partnerApi.listPartners).mockResolvedValue(page([])), '暂无数据'],
    ['ConsumerView', ConsumerView, () => vi.mocked(consumerApi.listConsumers).mockResolvedValue([]), '暂无数据'],
    ['IngestView', IngestView, () => vi.mocked(ingestApi.listIngestTasks).mockResolvedValue([]), '暂无数据'],
    ['ServiceView', ServiceView, () => vi.mocked(serviceApi.listServices).mockResolvedValue([]), '暂无数据'],
    ['CatalogView', CatalogView, () => vi.mocked(catalogApi.listCatalog).mockResolvedValue([]), '暂无数据'],
    ['QualityView', QualityView, () => vi.mocked(qualityApi.listQualityRules).mockResolvedValue([]), '暂无数据'],
    ['BillingView', BillingView, () => { vi.mocked(billingApi.listBillingRules).mockResolvedValue([]); vi.mocked(billingApi.listBills).mockResolvedValue([]) }, '暂无数据'],
    ['StatsView', StatsView, () => vi.mocked(statsApi.listAudit).mockResolvedValue([]), '暂无数据'],
    ['SystemView', SystemView, () => { vi.mocked(systemApi.listUsers).mockResolvedValue(page([])); vi.mocked(systemApi.listRoles).mockResolvedValue([]); vi.mocked(systemApi.listPermissions).mockResolvedValue([]) }, '暂无数据'],
    ['MonitorView', MonitorView, () => vi.mocked(statsApi.fetchDashboard).mockResolvedValue({}), '调用量 -']
  ] as const

  it.each(emptyCases)('%s renders empty state', async (_name, component, setup, expected) => {
    setup()
    const wrapper = await mountPage(component)
    expect(wrapper.text()).toContain(expected)
    wrapper.unmount()
  })

  const failureCases = [
    ['PartnerView', PartnerView, () => vi.mocked(partnerApi.listPartners).mockRejectedValue(new Error('partner failed'))],
    ['ConsumerView', ConsumerView, () => vi.mocked(consumerApi.listConsumers).mockRejectedValue(new Error('consumer failed'))],
    ['IngestView', IngestView, () => vi.mocked(ingestApi.listIngestTasks).mockRejectedValue(new Error('ingest failed'))],
    ['ServiceView', ServiceView, () => vi.mocked(serviceApi.listServices).mockRejectedValue(new Error('service failed'))],
    ['CatalogView', CatalogView, () => vi.mocked(catalogApi.listCatalog).mockRejectedValue(new Error('catalog failed'))],
    ['QualityView', QualityView, () => vi.mocked(qualityApi.listQualityRules).mockRejectedValue(new Error('quality failed'))],
    ['BillingView', BillingView, () => vi.mocked(billingApi.listBillingRules).mockRejectedValue(new Error('billing failed'))],
    ['StatsView', StatsView, () => vi.mocked(statsApi.fetchDashboard).mockRejectedValue(new Error('stats failed'))],
    ['SystemView', SystemView, () => vi.mocked(systemApi.listUsers).mockRejectedValue(new Error('system failed'))],
    ['MonitorView', MonitorView, () => vi.mocked(statsApi.fetchDashboard).mockRejectedValue(new Error('monitor failed'))]
  ] as const

  it.each(failureCases)('%s reports loading failure', async (_name, component, setup) => {
    setup()
    const wrapper = await mountPage(component)
    expect(ElMessage.error).toHaveBeenCalled()
    wrapper.unmount()
  })

  it.each([
    ['PartnerView', PartnerView, '新建合作方', partnerApi.createPartner],
    ['ConsumerView', ConsumerView, '注册消费方', consumerApi.registerConsumer],
    ['IngestView', IngestView, '创建任务', ingestApi.createIngestTask],
    ['ServiceView', ServiceView, '注册服务', serviceApi.registerService],
    ['CatalogView', CatalogView, '申请', catalogApi.applyCatalog],
    ['QualityView', QualityView, '新建规则', qualityApi.createQualityRule],
    ['BillingView', BillingView, '新增规则', billingApi.createBillingRule],
    ['SystemView', SystemView, '新建用户', systemApi.createUser]
  ] as const)('%s blocks invalid form submission', async (_name, component, openText, submitApi) => {
    await expectValidationBlocksSubmit(component, openText, submitApi as ReturnType<typeof vi.fn>)
  })

  it('StatsView keeps export safe when report generation fails validation boundary', async () => {
    vi.mocked(statsApi.generateReport).mockRejectedValueOnce(new Error('report type invalid'))
    const wrapper = await mountPage(StatsView)
    await clickButton(wrapper, '导出报表')
    expect(ElMessage.error).toHaveBeenCalledWith('report type invalid')
    wrapper.unmount()
  })

  it('MonitorView exposes no mutation form and remains read-only under validation boundary', async () => {
    const wrapper = await mountPage(MonitorView)
    expect(wrapper.findComponent({ name: 'FormDialog' }).exists()).toBe(false)
    expect(wrapper.text()).toContain('监控大屏')
    wrapper.unmount()
  })

  it.each([
    ['PartnerView', PartnerView, ['partner:view'], '新建合作方'],
    ['ConsumerView', ConsumerView, ['consumer:view'], '注册消费方'],
    ['IngestView', IngestView, ['ingest:view'], '创建任务'],
    ['ServiceView', ServiceView, ['service:view'], '注册服务'],
    ['CatalogView', CatalogView, ['catalog:view'], '申请'],
    ['QualityView', QualityView, ['quality:view'], '新建规则'],
    ['BillingView', BillingView, ['billing:view'], '新增规则'],
    ['StatsView', StatsView, [], '校验审计链'],
    ['SystemView', SystemView, ['system:view'], '新建用户'],
    ['MonitorView', MonitorView, [], '刷新']
  ] as const)('%s hides privileged action without permission', async (_name, component, permissions, hiddenText) => {
    const wrapper = await mountPage(component, permissions)
    expect(wrapper.text()).not.toContain(hiddenText)
    wrapper.unmount()
  })
})

describe('P0-09 frontend interaction fixes', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
    vi.restoreAllMocks()
    resetApiMocks()
    vi.spyOn(ElMessage, 'error').mockImplementation(() => ({ close: vi.fn() }) as never)
    vi.spyOn(ElMessage, 'success').mockImplementation(() => ({ close: vi.fn() }) as never)
    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as never)
    Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: vi.fn(() => 'blob:report') })
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: vi.fn() })
  })

  it('submits partner lifecycle action through visible button', async () => {
    const wrapper = await mountPage(PartnerView)
    await clickButton(wrapper, '提交')
    expect(partnerApi.submitPartner).toHaveBeenCalledWith(1)
    wrapper.unmount()
  })

  it('uses partner select options when creating ingest task', async () => {
    const wrapper = await mountPage(IngestView)
    expect(partnerApi.listPartners).toHaveBeenCalledWith({ page: 1, size: 100 })
    await clickButton(wrapper, '创建任务')
    const form = wrapper.findComponent({ name: 'FormDialog' })
    await form.props('submit')({ partnerId: 1, endpoint: 'http://example.com', syncMode: 'FULL' })
    expect(ingestApi.createIngestTask).toHaveBeenCalledWith(expect.objectContaining({ partnerId: 1 }))
    wrapper.unmount()
  })

  it('submits ingest lifecycle action through visible button', async () => {
    const wrapper = await mountPage(IngestView)
    await clickButton(wrapper, '提交')
    expect(ingestApi.submitIngest).toHaveBeenCalledWith(1)
    wrapper.unmount()
  })

  it('publishes service through visible state button', async () => {
    const wrapper = await mountPage(ServiceView)
    await clickButton(wrapper, '发布')
    expect(serviceApi.publishService).toHaveBeenCalledWith('svc')
    wrapper.unmount()
  })

  it('submits consumer lifecycle action through visible button', async () => {
    const wrapper = await mountPage(ConsumerView)
    await clickButton(wrapper, '提交')
    expect(consumerApi.applyConsumerEvent).toHaveBeenCalledWith(1, 'SUBMIT')
    wrapper.unmount()
  })

  it('applies and approves catalog application from selected item', async () => {
    const wrapper = await mountPage(CatalogView)
    await clickButton(wrapper, '申请')
    const form = wrapper.findComponent({ name: 'FormDialog' })
    await form.props('submit')({ reason: '测试', scope: '风控' })
    await flushPromises()
    await clickButton(wrapper, '审批申请')
    expect(catalogApi.applyCatalog).toHaveBeenCalledWith(1, expect.objectContaining({ reason: '测试', scope: '风控' }))
    expect(catalogApi.approveApplication).toHaveBeenCalledWith(99)
    wrapper.unmount()
  })

  it('triggers quality check through operation dialog', async () => {
    const wrapper = await mountPage(QualityView)
    await clickButton(wrapper, '手动触发校验')
    const form = wrapper.findComponent({ name: 'FormDialog' })
    await form.props('submit')({ batchNo: 'B001', ruleIds: '1' })
    expect(qualityApi.triggerCheck).toHaveBeenCalledWith(expect.objectContaining({ batchNo: 'B001', ruleIds: [1] }))
    wrapper.unmount()
  })

  it('confirms bill with success and failure feedback', async () => {
    const wrapper = await mountPage(BillingView)
    await clickTab(wrapper, '账单')
    await clickButton(wrapper, '确认')
    expect(billingApi.confirmBill).toHaveBeenCalled()
    expect(ElMessage.success).toHaveBeenCalledWith('账单已确认')

    vi.mocked(billingApi.confirmBill).mockRejectedValueOnce(new Error('confirm failed'))
    await clickButton(wrapper, '确认')
    expect(ElMessage.error).toHaveBeenCalledWith('confirm failed')
    wrapper.unmount()
  })

  it('exports stats report through generated Blob download', async () => {
    const wrapper = await mountPage(StatsView)
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    await clickButton(wrapper, '导出报表')
    expect(statsApi.generateReport).toHaveBeenCalledWith({ type: 'COMPLIANCE' })
    expect(URL.createObjectURL).toHaveBeenCalled()
    expect(clickSpy).toHaveBeenCalled()
    expect(ElMessage.success).toHaveBeenCalledWith('报表已导出')
    wrapper.unmount()
  })

  it('creates system user through form submission', async () => {
    const wrapper = await mountPage(SystemView)
    await clickButton(wrapper, '新建用户')
    const form = wrapper.findComponent({ name: 'FormDialog' })
    await form.props('submit')({ username: 'new-user', password: 'pw', permissions: 'stats:view' })
    expect(systemApi.createUser).toHaveBeenCalledWith({ username: 'new-user', password: 'pw', permissions: ['stats:view'] })
    wrapper.unmount()
  })

  it('refreshes monitor dashboard on demand', async () => {
    const wrapper = await mountPage(MonitorView)
    vi.mocked(statsApi.fetchDashboard).mockClear()
    await clickButton(wrapper, '刷新')
    expect(statsApi.fetchDashboard).toHaveBeenCalled()
    wrapper.unmount()
  })
})
