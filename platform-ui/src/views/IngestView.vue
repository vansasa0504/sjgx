<template>
  <section class="panel">
    <div class="page-header">
      <h1>接入任务</h1>
      <el-button v-if="auth.hasPermission('ingest:create')" type="primary" @click="openCreate">创建任务</el-button>
    </div>
    <PageTable ref="tableRef" :columns="columns" :filters="filters" :fetch-data="fetchData">
      <template #actions="{ row }">
        <el-button size="small" @click="openDetail(row as IngestTask)">详情</el-button>
        <el-button v-if="auth.hasPermission('ingest:update')" size="small" @click="runTest(row as IngestTask)">测试</el-button>
        <el-button v-if="auth.hasPermission('ingest:update')" size="small" @click="openMapping(row as IngestTask)">映射</el-button>
        <el-button v-if="auth.hasPermission('ingest:update')" size="small" @click="openRules(row as IngestTask)">规则</el-button>
        <el-button v-if="auth.hasPermission('ingest:view')" size="small" @click="openRecords(row as IngestTask)">记录</el-button>
        <el-button v-if="canSubmit(row as IngestTask)" size="small" @click="flow(row as IngestTask, 'submit')">提交</el-button>
        <el-button v-if="canApprove(row as IngestTask)" size="small" type="success" @click="flow(row as IngestTask, 'approve')">审批</el-button>
        <el-button v-if="canOffline(row as IngestTask)" size="small" type="danger" @click="flow(row as IngestTask, 'offline')">下线</el-button>
      </template>
    </PageTable>
    <FormDialog v-model="dialog.visible" :title="dialog.title" :fields="dialog.fields" :initial="dialog.initial" :submit="dialog.submit" @success="refresh" />
    <el-drawer v-model="detailVisible" title="接入任务详情">
      <pre>{{ detail }}</pre>
      <h3>最近测试结果</h3>
      <pre>{{ testRows }}</pre>
      <h3>执行记录</h3>
      <pre>{{ records }}</pre>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageTable from '../components/PageTable.vue'
import FormDialog, { type FormField } from '../components/FormDialog.vue'
import { approveIngest, createIngestTask, getIngestTask, listIngestRecords, listIngestTasks, offlineIngest, submitIngest, testIngest, updateMapping, updateRules } from '../api/ingest'
import { listPartners } from '../api/partner'
import { toPage, type IngestTask, type Page, type PageQuery } from '../api/types'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const tableRef = ref<{ refresh: () => Promise<void> }>()
const detailVisible = ref(false)
const detail = ref<IngestTask>()
const testRows = ref<unknown[]>([])
const records = ref<unknown>()
const partnerOptions = ref<Array<{ label: string; value: number }>>([])
const filters = [{ prop: 'partnerId', label: '合作方ID' }, { prop: 'status', label: '状态' }]
const columns = [
  { prop: 'id', label: '任务ID' },
  { prop: 'partnerId', label: '合作方' },
  { prop: 'protocol', label: '协议' },
  { prop: 'format', label: '格式' },
  { prop: 'syncMode', label: '同步模式' },
  { prop: 'status', label: '状态' },
  { prop: 'version', label: '版本' },
  { prop: 'actions', label: '操作', width: 460 }
]
const createFields: FormField[] = [
  { prop: 'partnerId', label: '合作方', type: 'select', options: partnerOptions.value, required: true },
  { prop: 'protocol', label: '协议', type: 'input' },
  { prop: 'format', label: '格式', type: 'input' },
  { prop: 'endpoint', label: '端点', type: 'input', required: true },
  { prop: 'syncMode', label: '同步模式', type: 'input' },
  { prop: 'cron', label: 'Cron', type: 'input' },
  { prop: 'fieldMapping', label: '字段映射JSON', type: 'textarea' },
  { prop: 'qualityRules', label: '质量规则CSV', type: 'textarea' }
]
const dialog = ref({ visible: false, title: '', fields: [] as FormField[], initial: {} as Record<string, unknown>, submit: async (_form: Record<string, unknown>) => {} })

async function fetchData(params: PageQuery): Promise<Page<IngestTask>> {
  return toPage(await listIngestTasks(params), Number(params.page || 1), Number(params.size || 10))
}
function refresh() { tableRef.value?.refresh() }
function openCreate() {
  dialog.value = { visible: true, title: '创建接入任务', fields: createFields, initial: { syncMode: 'FULL', protocol: 'HTTP', format: 'JSON' }, submit: async (form) => { await createIngestTask(normalizeTaskForm(form)) } }
}
function openMapping(row: IngestTask) {
  dialog.value = { visible: true, title: '字段映射', fields: [{ prop: 'fieldMapping', label: '映射JSON', type: 'textarea' }], initial: { fieldMapping: JSON.stringify(row.fieldMapping || {}) }, submit: async (form) => { await updateMapping(row.id, JSON.parse(String(form.fieldMapping || '{}'))) } }
}
function openRules(row: IngestTask) {
  dialog.value = { visible: true, title: '质量规则', fields: [{ prop: 'qualityRules', label: '规则CSV', type: 'textarea' }], initial: { qualityRules: (row.qualityRules || []).join(',') }, submit: async (form) => { await updateRules(row.id, csv(form.qualityRules)) } }
}
async function openDetail(row: IngestTask) {
  detail.value = await getIngestTask(row.id)
  detailVisible.value = true
}
async function openRecords(row: IngestTask) {
  records.value = await listIngestRecords({ taskId: row.id, page: 1, size: 10 })
  detail.value = row
  detailVisible.value = true
}
async function runTest(row: IngestTask) {
  testRows.value = await testIngest(row.id)
  detail.value = row
  detailVisible.value = true
}
async function flow(row: IngestTask, action: 'submit' | 'approve' | 'offline') {
  try {
    await ElMessageBox.confirm(`确认${action}？`)
    if (action === 'submit') await submitIngest(row.id)
    if (action === 'approve') await approveIngest(row.id)
    if (action === 'offline') await offlineIngest(row.id)
    ElMessage.success('操作成功')
    refresh()
  } catch {
    // 用户取消确认时静默返回。
  }
}

function csv(value: unknown) {
  return String(value || '').split(',').map((item) => item.trim()).filter(Boolean)
}
function normalizeTaskForm(form: Record<string, unknown>) {
  return {
    partnerId: Number(form.partnerId),
    endpoint: String(form.endpoint || ''),
    syncMode: String(form.syncMode || 'FULL'),
    cron: String(form.cron || ''),
    fieldMapping: form.fieldMapping ? JSON.parse(String(form.fieldMapping)) : {},
    qualityRules: csv(form.qualityRules)
  } as never
}
function status(row: IngestTask) { return String(row.status || '').toUpperCase() }
function canSubmit(row: IngestTask) { return auth.hasPermission('ingest:approve') && status(row) === 'TESTING' }
function canApprove(row: IngestTask) { return auth.hasPermission('ingest:approve') && status(row) === 'PENDING_APPROVAL' }
function canOffline(row: IngestTask) { return auth.hasPermission('ingest:approve') && ['ONLINE'].includes(status(row)) }
async function loadPartnerOptions() {
  const partners = await listPartners({ page: 1, size: 100 })
  partnerOptions.value.splice(0, partnerOptions.value.length, ...partners.records.map((partner) => ({
    label: partner.name || partner.partnerCode || `合作方 ${partner.id}`,
    value: partner.id
  })))
}
onMounted(loadPartnerOptions)
</script>
