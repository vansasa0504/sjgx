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
        <el-button v-if="auth.hasPermission('service:update')" size="small" @click="test(row as DataServiceDefinition)">测试</el-button>
        <el-button v-if="auth.hasPermission('service:view')" size="small" @click="openLogs(row as DataServiceDefinition)">日志</el-button>
        <el-button v-if="canPublish(row as DataServiceDefinition)" size="small" type="success" @click="publish(row as DataServiceDefinition)">发布</el-button>
        <el-button v-if="canOffline(row as DataServiceDefinition)" size="small" type="danger" @click="offline(row as DataServiceDefinition)">下线</el-button>
      </template>
    </PageTable>
    <FormDialog v-model="dialog.visible" :title="dialog.title" :fields="fields" :initial="dialog.initial" :submit="dialog.submit" @success="refresh" />
    <el-drawer v-model="detailVisible" title="服务详情"><pre>{{ detail }}</pre><h3>调用日志</h3><pre>{{ logs }}</pre></el-drawer>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import PageTable from '../components/PageTable.vue'
import FormDialog, { type FormField } from '../components/FormDialog.vue'
import { getService, listServiceLogs, listServices, offlineService, publishService, registerService, testService, updateService } from '../api/service'
import { toPage, type DataServiceDefinition, type Page, type PageQuery } from '../api/types'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const tableRef = ref<{ refresh: () => Promise<void> }>()
const detailVisible = ref(false)
const detail = ref<DataServiceDefinition>()
const logs = ref<unknown>()
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
async function openDetail(row: DataServiceDefinition) { detail.value = await getService(row.serviceCode); detailVisible.value = true }
async function openLogs(row: DataServiceDefinition) { logs.value = await listServiceLogs(row.serviceCode); detail.value = row; detailVisible.value = true }
async function test(row: DataServiceDefinition) { await testService(row.serviceCode); refresh() }
async function publish(row: DataServiceDefinition) {
  try { await ElMessageBox.confirm('确认发布？'); await publishService(row.serviceCode); refresh() } catch {}
}
async function offline(row: DataServiceDefinition) {
  try { await ElMessageBox.confirm('确认下线？'); await offlineService(row.serviceCode); refresh() } catch {}
}
function status(row: DataServiceDefinition) { return String(row.status || '').toUpperCase() }
function canPublish(row: DataServiceDefinition) {
  return auth.hasPermission('service:approve') && ['DRAFT', 'TESTED', 'DEFINED'].includes(status(row))
}
function canOffline(row: DataServiceDefinition) {
  return auth.hasPermission('service:approve') && ['ONLINE', 'PUBLISHED'].includes(status(row))
}
</script>
