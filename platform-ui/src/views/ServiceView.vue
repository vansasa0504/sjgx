<template>
  <section class="panel">
    <div class="page-header">
      <h1>数据服务</h1>
      <el-button v-if="auth.hasPermission('service:create')" type="primary" @click="openCreate">注册服务</el-button>
    </div>
    <PageTable ref="tableRef" :columns="columns" :filters="filters" :fetch-data="fetchData">
      <template #actions="{ row }">
        <el-button size="small" @click="openDetail(row as DataServiceDefinition)">详情</el-button>
        <el-button v-if="auth.hasPermission('service:update')" size="small" @click="openEdit(row as DataServiceDefinition)">编辑</el-button>
        <el-button v-if="canDefine(row as DataServiceDefinition)" size="small" @click="define(row as DataServiceDefinition)">定义</el-button>
        <el-button v-if="canTest(row as DataServiceDefinition)" size="small" @click="test(row as DataServiceDefinition)">测试</el-button>
        <el-button v-if="auth.hasPermission('service:view')" size="small" @click="openLogs(row as DataServiceDefinition)">日志</el-button>
        <el-button v-if="auth.hasPermission('service:view')" size="small" @click="openCredentials(row as DataServiceDefinition)">凭证</el-button>
        <el-button v-if="canPublish(row as DataServiceDefinition)" size="small" type="success" @click="publish(row as DataServiceDefinition)">发布</el-button>
        <el-button v-if="canOffline(row as DataServiceDefinition)" size="small" type="danger" @click="offline(row as DataServiceDefinition)">下线</el-button>
      </template>
    </PageTable>
    <FormDialog v-model="dialog.visible" :title="dialog.title" :fields="fields" :initial="dialog.initial" :submit="dialog.submit" @success="refresh" />
    <el-drawer v-model="detailVisible" title="服务详情">
      <pre v-if="drawerMode === 'detail'">{{ detail }}</pre>
      <template v-else-if="drawerMode === 'logs'">
        <h3>调用日志</h3>
        <PageTable :key="detail?.serviceCode" :columns="logColumns" :fetch-data="fetchLogs" />
      </template>
      <template v-else>
        <div class="drawer-toolbar">
          <el-button v-if="auth.hasPermission('service:update')" type="primary" @click="createCredential">创建凭证</el-button>
        </div>
        <el-table :data="credentials" border>
          <el-table-column v-for="column in credentialColumns" :key="column.prop" :prop="column.prop" :label="column.label" />
          <el-table-column label="操作" width="160">
            <template #default="{ row }">
              <el-button v-if="auth.hasPermission('service:update')" size="small" @click="rotateCredential(row.id)">轮换</el-button>
              <el-button v-if="auth.hasPermission('service:update') && row.status === 'ACTIVE'" size="small" type="danger" @click="disableCredential(row.id)">禁用</el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import PageTable from '../components/PageTable.vue'
import FormDialog, { type FormField } from '../components/FormDialog.vue'
import { createServiceCredential, defineService, disableServiceCredential, getService, listServiceCredentials, listServiceLogs, listServices, offlineService, publishService, registerService, rotateServiceCredential, testService, updateService, type ApiCredentialView, type CreatedCredential } from '../api/service'
import { toPage, type DataServiceDefinition, type Page, type PageQuery } from '../api/types'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const tableRef = ref<{ refresh: () => Promise<void> }>()
const detailVisible = ref(false)
const detail = ref<DataServiceDefinition>()
const drawerMode = ref<'detail' | 'logs' | 'credentials'>('detail')
const credentials = ref<ApiCredentialView[]>([])
const filters = [{ prop: 'keyword', label: '关键词' }, { prop: 'status', label: '状态' }]
const columns = [
  { prop: 'serviceCode', label: '服务编码' },
  { prop: 'name', label: '名称' },
  { prop: 'serviceType', label: '类型' },
  { prop: 'routeKey', label: '路由' },
  { prop: 'rateLimit', label: '限流' },
  { prop: 'status', label: '状态' },
  { prop: 'version', label: '版本' },
  { prop: 'actions', label: '操作', width: 300 }
]
const logColumns = [
  { prop: 'traceId', label: '追踪ID' },
  { prop: 'consumerCode', label: '消费方' },
  { prop: 'serviceCode', label: '服务编码' },
  { prop: 'status', label: '状态' },
  { prop: 'elapsedMillis', label: '耗时(ms)' },
  { prop: 'responseSize', label: '响应(bytes)' },
  { prop: 'requestHash', label: '请求哈希' },
  { prop: 'error', label: '错误' },
  { prop: 'createdAt', label: '时间' }
]
const credentialColumns = [
  { prop: 'apiKey', label: 'API Key' },
  { prop: 'consumerCode', label: '消费方' },
  { prop: 'serviceCode', label: '服务编码' },
  { prop: 'status', label: '状态' },
  { prop: 'rotatedFrom', label: '轮换来源' }
]
const fields: FormField[] = [
  { prop: 'serviceCode', label: '服务编码', type: 'input', required: true },
  { prop: 'name', label: '名称', type: 'input', required: true },
  { prop: 'routeKey', label: '路由', type: 'input', required: true }
]
const dialog = ref({ visible: false, title: '', initial: {} as Record<string, unknown>, submit: async (_form: Record<string, unknown>) => {} })

async function fetchData(params: PageQuery): Promise<Page<DataServiceDefinition>> {
  return toPage(await listServices(params), Number(params.page || 1), Number(params.size || 10))
}
function refresh() { tableRef.value?.refresh() }
function openCreate() { dialog.value = { visible: true, title: '注册服务', initial: {}, submit: async (form) => { await registerService(form as never) } } }
function openEdit(row: DataServiceDefinition) { dialog.value = { visible: true, title: '编辑服务', initial: row as never, submit: async (form) => { await updateService(row.serviceCode, form as never) } } }
async function openDetail(row: DataServiceDefinition) { detail.value = await getService(row.serviceCode); drawerMode.value = 'detail'; detailVisible.value = true }
async function openLogs(row: DataServiceDefinition) { detail.value = row; drawerMode.value = 'logs'; detailVisible.value = true }
async function openCredentials(row: DataServiceDefinition) {
  detail.value = row
  drawerMode.value = 'credentials'
  credentials.value = await listServiceCredentials(row.serviceCode)
  detailVisible.value = true
}
async function fetchLogs(params: PageQuery) {
  if (!detail.value) return { records: [], total: 0, current: Number(params.page || 1), size: Number(params.size || 10) }
  return listServiceLogs(detail.value.serviceCode, params) as Promise<Page<Record<string, unknown>>>
}
async function showCreatedCredential(created: CreatedCredential) {
  await ElMessageBox.alert(`API Key: ${created.apiKey}\nSecret: ${created.secret}`, '一次性凭证', { confirmButtonText: '确定' })
}
async function reloadCredentials() {
  if (detail.value) credentials.value = await listServiceCredentials(detail.value.serviceCode)
}
async function createCredential() {
  if (!detail.value) return
  const { value } = await ElMessageBox.prompt('消费方编码', '创建凭证', { inputPattern: /.+/, inputErrorMessage: '请输入消费方编码' })
  const created = await createServiceCredential(detail.value.serviceCode, value)
  await showCreatedCredential(created)
  await reloadCredentials()
}
async function rotateCredential(id: number) {
  const created = await rotateServiceCredential(id)
  await showCreatedCredential(created)
  await reloadCredentials()
}
async function disableCredential(id: number) {
  try { await ElMessageBox.confirm('确认禁用？'); await disableServiceCredential(id); await reloadCredentials() } catch {}
}
async function define(row: DataServiceDefinition) { await defineService(row.serviceCode); refresh() }
async function test(row: DataServiceDefinition) { await testService(row.serviceCode); refresh() }
async function publish(row: DataServiceDefinition) {
  try { await ElMessageBox.confirm('确认发布？'); await publishService(row.serviceCode); refresh() } catch {}
}
async function offline(row: DataServiceDefinition) {
  try { await ElMessageBox.confirm('确认下线？'); await offlineService(row.serviceCode); refresh() } catch {}
}
function status(row: DataServiceDefinition) { return String(row.status || '').toUpperCase() }
function canDefine(row: DataServiceDefinition) {
  return auth.hasPermission('service:update') && status(row) === 'REGISTERED'
}
function canTest(row: DataServiceDefinition) {
  return auth.hasPermission('service:update') && status(row) === 'DEFINED'
}
function canPublish(row: DataServiceDefinition) {
  return auth.hasPermission('service:approve') && status(row) === 'TESTED'
}
function canOffline(row: DataServiceDefinition) {
  return auth.hasPermission('service:approve') && ['PUBLISHED', 'VERSIONED'].includes(status(row))
}
</script>

<style scoped>
.drawer-toolbar {
  margin-bottom: 12px;
}
</style>
