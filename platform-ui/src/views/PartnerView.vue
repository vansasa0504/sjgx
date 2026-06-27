<template>
  <section class="panel">
    <div class="page-header">
      <h1>合作方管理</h1>
      <el-button v-if="auth.hasPermission('partner:create')" type="primary" @click="openCreate">新建合作方</el-button>
    </div>

    <PageTable ref="tableRef" :columns="columns" :filters="filters" :fetch-data="fetchData">
      <template #actions="{ row }">
        <el-button size="small" @click="openDetail(row as Partner)">详情</el-button>
        <el-button v-if="auth.hasPermission('partner:update')" size="small" @click="openEdit(row as Partner)">编辑</el-button>
        <el-button v-if="canSubmit(row as Partner)" size="small" @click="flow(row as Partner, 'submit')">提交</el-button>
        <el-button v-if="canApprove(row as Partner)" size="small" type="success" @click="flow(row as Partner, 'approve')">审批</el-button>
        <el-button v-if="canAdmit(row as Partner)" size="small" type="success" @click="flow(row as Partner, 'admit')">准入</el-button>
        <el-button v-if="canApprove(row as Partner)" size="small" type="warning" @click="openReject(row as Partner)">驳回</el-button>
        <el-button v-if="auth.hasPermission('partner:update')" size="small" @click="openRate(row as Partner)">评级</el-button>
        <el-button v-if="auth.hasPermission('partner:update')" size="small" @click="openInterface(row as Partner)">接口</el-button>
        <el-button v-if="canTerminate(row as Partner)" size="small" type="danger" @click="flow(row as Partner, 'terminate')">退出</el-button>
      </template>
    </PageTable>

    <FormDialog v-model="dialog.visible" :title="dialog.title" :fields="dialog.fields" :initial="dialog.initial" :submit="dialog.submit" @success="refresh" />

    <el-drawer v-model="detailVisible" title="合作方详情" size="42%">
      <el-descriptions v-if="detail" :column="1" border>
        <el-descriptions-item label="名称">{{ detail.name }}</el-descriptions-item>
        <el-descriptions-item label="数据类型">{{ detail.dataType }}</el-descriptions-item>
        <el-descriptions-item label="行业">{{ detail.industry }}</el-descriptions-item>
        <el-descriptions-item label="合规等级">{{ detail.complianceLevel }}</el-descriptions-item>
        <el-descriptions-item label="状态"><StatusTag :status="detail.status || 'UNKNOWN'" /></el-descriptions-item>
      </el-descriptions>
      <h3>接口配置</h3>
      <pre>{{ interfaces }}</pre>
      <h3>生命周期事件</h3>
      <el-timeline>
        <el-timeline-item v-for="event in events" :key="event">{{ event }}</el-timeline-item>
      </el-timeline>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageTable from '../components/PageTable.vue'
import FormDialog, { type FormField } from '../components/FormDialog.vue'
import StatusTag from '../components/StatusTag.vue'
import { useAuthStore } from '../stores/auth'
import { admitPartner, approvePartner, configureInterface, createPartner, getPartner, listInterfaces, listPartnerEvents, listPartners, ratePartner, rejectPartner, submitPartner, terminatePartner, updatePartner } from '../api/partner'
import type { Page, PageQuery, Partner } from '../api/types'

const auth = useAuthStore()
const tableRef = ref<{ refresh: () => Promise<void> }>()
const detailVisible = ref(false)
const detail = ref<Partner>()
const interfaces = ref<unknown[]>([])
const events = ref<string[]>([])
const filters = [
  { prop: 'keyword', label: '关键词' },
  { prop: 'dataType', label: '数据类型' },
  { prop: 'status', label: '状态' }
]
const columns = [
  { prop: 'partnerCode', label: '合作方编码' },
  { prop: 'name', label: '名称' },
  { prop: 'dataType', label: '数据类型' },
  { prop: 'industry', label: '行业' },
  { prop: 'complianceLevel', label: '合规等级' },
  { prop: 'status', label: '状态' },
  { prop: 'rating', label: '评级' },
  { prop: 'actions', label: '操作', width: 420 }
]

const baseFields: FormField[] = [
  { prop: 'name', label: '名称', type: 'input', required: true },
  { prop: 'dataType', label: '数据类型', type: 'input' },
  { prop: 'industry', label: '行业', type: 'input' },
  { prop: 'complianceLevel', label: '合规等级', type: 'input' }
]

const dialog = ref({
  visible: false,
  title: '',
  fields: [] as FormField[],
  initial: {} as Record<string, unknown>,
  submit: async (_form: Record<string, unknown>) => {}
})

async function fetchData(params: PageQuery): Promise<Page<Partner>> {
  return listPartners(params)
}

function refresh() {
  tableRef.value?.refresh()
}

function openCreate() {
  dialog.value = {
    visible: true,
    title: '新建合作方',
    fields: baseFields,
    initial: {},
    submit: async (form) => { await createPartner(form as never) }
  }
}

function openEdit(row: Partner) {
  dialog.value = {
    visible: true,
    title: '编辑合作方',
    fields: baseFields,
    initial: row as never,
    submit: async (form) => { await updatePartner(row.id, form as never) }
  }
}

function openRate(row: Partner) {
  dialog.value = {
    visible: true,
    title: '合作方评级',
    fields: [{ prop: 'score', label: '评级', type: 'input', required: true }],
    initial: { score: row.rating || 'A' },
    submit: async (form) => { await ratePartner(row.id, String(form.score)) }
  }
}

function openReject(row: Partner) {
  dialog.value = {
    visible: true,
    title: '驳回合作方',
    fields: [{ prop: 'reason', label: '驳回原因', type: 'textarea', required: true }],
    initial: {},
    submit: async (form) => { await rejectPartner(row.id, String(form.reason || '')) }
  }
}

function openInterface(row: Partner) {
  dialog.value = {
    visible: true,
    title: '配置接口',
    fields: [
      { prop: 'protocol', label: '协议', type: 'input', required: true },
      { prop: 'endpoint', label: '地址', type: 'input', required: true },
      { prop: 'credential', label: '凭证', type: 'textarea' }
    ],
    initial: { protocol: 'HTTP' },
    submit: async (form) => { await configureInterface(row.id, form as never) }
  }
}

async function openDetail(row: Partner) {
  detail.value = await getPartner(row.id)
  interfaces.value = await listInterfaces(row.id)
  events.value = await listPartnerEvents(row.id)
  detailVisible.value = true
}

async function flow(row: Partner, action: 'submit' | 'approve' | 'admit' | 'terminate') {
  try {
    await ElMessageBox.confirm(`确认执行 ${action}？`)
    if (action === 'submit') await submitPartner(row.id)
    if (action === 'approve') await approvePartner(row.id)
    if (action === 'admit') await admitPartner(row.id)
    if (action === 'terminate') await terminatePartner(row.id)
    ElMessage.success('操作成功')
    refresh()
  } catch {
    // 用户取消确认时静默返回。
  }
}

function normalizedStatus(row: Partner) {
  return String(row.status || '').toUpperCase()
}
function canSubmit(row: Partner) {
  return auth.hasPermission('partner:approve') && normalizedStatus(row) === 'REGISTERED'
}
function canApprove(row: Partner) {
  return auth.hasPermission('partner:approve') && normalizedStatus(row) === 'SUBMITTED'
}
function canAdmit(row: Partner) {
  return auth.hasPermission('partner:approve') && normalizedStatus(row) === 'APPROVED'
}
function canTerminate(row: Partner) {
  return auth.hasPermission('partner:approve') && ['ADMITTED', 'RATED', 'SUSPENDED'].includes(normalizedStatus(row))
}
</script>
