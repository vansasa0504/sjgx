<template>
  <section class="panel">
    <div class="page-header">
      <h1>消费方管理</h1>
      <el-button v-if="auth.hasPermission('consumer:create')" type="primary" @click="openCreate">注册消费方</el-button>
    </div>
    <PageTable ref="tableRef" :columns="columns" :filters="filters" :fetch-data="fetchData">
      <template #actions="{ row }">
        <el-button size="small" @click="openDetail(row as Consumer)">详情</el-button>
        <el-button v-if="auth.hasPermission('consumer:update')" size="small" @click="openQuota(row as Consumer)">配额</el-button>
        <el-button v-if="auth.hasPermission('consumer:view')" size="small" @click="openAudit(row as Consumer)">审计</el-button>
        <el-button v-if="auth.hasPermission('consumer:view')" size="small" @click="openLogs(row as Consumer)">日志</el-button>
        <el-button v-if="canSubmit(row as Consumer)" size="small" @click="applyEvent(row as Consumer, 'SUBMIT')">提交</el-button>
        <el-button v-if="canApprove(row as Consumer)" size="small" type="success" @click="applyEvent(row as Consumer, 'APPROVE')">审批</el-button>
      </template>
    </PageTable>
    <FormDialog v-model="dialog.visible" :title="dialog.title" :fields="dialog.fields" :initial="dialog.initial" :submit="dialog.submit" @success="refresh" />
    <el-drawer v-model="detailVisible" title="消费方详情"><pre>{{ detail }}</pre><h3>{{ drawerSection }}</h3><pre>{{ drawerData }}</pre></el-drawer>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import PageTable from '../components/PageTable.vue'
import FormDialog, { type FormField } from '../components/FormDialog.vue'
import { applyConsumerEvent, configureQuota, getConsumer, getConsumerAudit, getConsumerLogs, listConsumers, registerConsumer } from '../api/consumer'
import { toPage, type Consumer, type Page, type PageQuery } from '../api/types'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const tableRef = ref<{ refresh: () => Promise<void> }>()
const detailVisible = ref(false)
const detail = ref<Consumer>()
const drawerSection = ref('详情')
const drawerData = ref<unknown>()
const filters = [{ prop: 'keyword', label: '关键词' }, { prop: 'bizLine', label: '业务条线' }, { prop: 'status', label: '状态' }]
const columns = [
  { prop: 'consumerCode', label: '编码' },
  { prop: 'name', label: '名称' },
  { prop: 'bizLine', label: '业务条线' },
  { prop: 'systemType', label: '系统类型' },
  { prop: 'complianceLevel', label: '合规等级' },
  { prop: 'status', label: '状态' },
  { prop: 'actions', label: '操作', width: 260 }
]
const createFields: FormField[] = [
  { prop: 'code', label: '编码', type: 'input', required: true },
  { prop: 'name', label: '名称', type: 'input', required: true },
  { prop: 'bizLine', label: '业务条线', type: 'input' },
  { prop: 'systemType', label: '系统类型', type: 'input' },
  { prop: 'complianceLevel', label: '合规等级', type: 'input' }
]
const dialog = ref({ visible: false, title: '', fields: [] as FormField[], initial: {} as Record<string, unknown>, submit: async (_form: Record<string, unknown>) => {} })

async function fetchData(params: PageQuery): Promise<Page<Consumer>> {
  return toPage(await listConsumers(params), Number(params.page || 1), Number(params.size || 10))
}
function refresh() { tableRef.value?.refresh() }
function openCreate() { dialog.value = { visible: true, title: '注册消费方', fields: createFields, initial: {}, submit: async (form) => { await registerConsumer(form as never) } } }
function openQuota(row: Consumer) { dialog.value = { visible: true, title: '配置配额', fields: [{ prop: 'maxRequests', label: '最大请求数', type: 'number', required: true }, { prop: 'warnThreshold', label: '预警阈值', type: 'number' }], initial: { maxRequests: 1000, warnThreshold: 800 }, submit: async (form) => { await configureQuota(row.id, form as never) } } }
async function openDetail(row: Consumer) { detail.value = await getConsumer(row.id); drawerSection.value = '详情'; drawerData.value = detail.value; detailVisible.value = true }
async function openAudit(row: Consumer) { detail.value = row; drawerSection.value = '行为审计'; drawerData.value = await getConsumerAudit(row.id); detailVisible.value = true }
async function openLogs(row: Consumer) { detail.value = row; drawerSection.value = '调用日志'; drawerData.value = await getConsumerLogs(row.id); detailVisible.value = true }
async function applyEvent(row: Consumer, event: string) {
  try { await ElMessageBox.confirm(`确认${event}？`); await applyConsumerEvent(row.id, event); refresh() } catch {}
}
function status(row: Consumer) { return String(row.status || '').toUpperCase() }
function canSubmit(row: Consumer) { return auth.hasPermission('consumer:approve') && ['DRAFT', 'REGISTERED', 'PENDING'].includes(status(row)) }
function canApprove(row: Consumer) { return auth.hasPermission('consumer:approve') && ['SUBMITTED', 'PENDING_APPROVAL', 'PENDING'].includes(status(row)) }
</script>
