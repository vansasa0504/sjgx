<template>
  <section class="panel">
    <div class="page-header"><h1>计费管理</h1></div>
    <el-tabs>
      <el-tab-pane label="规则">
        <el-button v-if="auth.hasPermission('billing:create')" type="primary" @click="openRule()">新增规则</el-button>
        <PageTable ref="ruleTable" :columns="ruleColumns" :fetch-data="fetchRules">
          <template #actions="{ row }">
            <el-button v-if="auth.hasPermission('billing:update')" size="small" @click="openRule(row as BillingRule)">编辑</el-button>
          </template>
        </PageTable>
      </el-tab-pane>
      <el-tab-pane label="账单">
        <el-button v-if="auth.hasPermission('billing:run')" type="primary" @click="openBill">生成账单</el-button>
        <PageTable ref="billTable" :columns="billColumns" :fetch-data="fetchBills">
          <template #actions="{ row }">
            <el-button size="small" @click="showBill(row as Bill)">详情</el-button>
            <el-button v-if="auth.hasPermission('billing:approve')" size="small" @click="confirm(row as Bill)">确认</el-button>
            <el-button v-if="auth.hasPermission('billing:approve')" size="small" @click="dispute(row as Bill)">异议</el-button>
          </template>
        </PageTable>
      </el-tab-pane>
      <el-tab-pane label="费用统计">
        <el-button @click="loadStats">刷新统计</el-button>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="总费用">{{ stats?.totalAmount ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="调用量">{{ stats?.invokeCount ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="账单数">{{ stats?.billCount ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="明细数">{{ stats?.itemCount ?? 0 }}</el-descriptions-item>
        </el-descriptions>
      </el-tab-pane>
    </el-tabs>
    <FormDialog v-model="dialog.visible" :title="dialog.title" :fields="dialog.fields" :initial="dialog.initial" :submit="dialog.submit" @success="refresh" />
    <el-drawer v-model="detailVisible" title="账单明细" size="50%">
      <el-descriptions v-if="selectedBill" :column="2" border>
        <el-descriptions-item label="账单号">{{ selectedBill.billNo }}</el-descriptions-item>
        <el-descriptions-item label="总金额">{{ selectedBill.totalAmount }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="selectedBill?.items ?? []" style="width: 100%; margin-top: 16px">
        <el-table-column prop="itemType" label="类型" />
        <el-table-column prop="refId" label="引用" />
        <el-table-column prop="quantity" label="数量" />
        <el-table-column prop="unitPrice" label="单价" />
        <el-table-column prop="amount" label="金额" />
      </el-table>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageTable from '../components/PageTable.vue'
import FormDialog, { type FormField } from '../components/FormDialog.vue'
import { confirmBill, createBillingRule, disputeBill, generateBill, getBill, getBillingStats, listBillingRules, listBills, updateBillingRule } from '../api/billing'
import { toPage, type Bill, type BillingRule, type BillingStats, type Page, type PageQuery } from '../api/types'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const ruleTable = ref<{ refresh: () => Promise<void> }>()
const billTable = ref<{ refresh: () => Promise<void> }>()
const stats = ref<BillingStats>()
const detailVisible = ref(false)
const selectedBill = ref<Bill>()
const ruleColumns = [{ prop: 'ruleCode', label: '编码' }, { prop: 'ruleName', label: '名称' }, { prop: 'billingModel', label: '模型' }, { prop: 'targetType', label: '目标类型' }, { prop: 'unitPrice', label: '单价' }, { prop: 'actions', label: '操作', width: 120 }]
const billColumns = [{ prop: 'billNo', label: '账单号' }, { prop: 'billType', label: '类型' }, { prop: 'billPeriod', label: '周期' }, { prop: 'totalAmount', label: '金额' }, { prop: 'status', label: '状态' }, { prop: 'actions', label: '操作', width: 220 }]
const ruleFields: FormField[] = [{ prop: 'ruleCode', label: '编码', type: 'input', required: true }, { prop: 'ruleName', label: '名称', type: 'input', required: true }, { prop: 'billingModel', label: '模型', type: 'input' }, { prop: 'targetType', label: '目标类型', type: 'input' }, { prop: 'unitPrice', label: '单价', type: 'number' }]
const billFields: FormField[] = [{ prop: 'billType', label: '类型', type: 'input', required: true }, { prop: 'period', label: '周期', type: 'input', required: true }, { prop: 'start', label: '开始日期', type: 'date' }, { prop: 'end', label: '结束日期', type: 'date' }]
const dialog = ref({ visible: false, title: '', fields: [] as FormField[], initial: {} as Record<string, unknown>, submit: async (_form: Record<string, unknown>) => {} })
async function fetchRules(params: PageQuery): Promise<Page<BillingRule>> { return toPage(await listBillingRules(params), Number(params.page || 1), Number(params.size || 10)) }
async function fetchBills(params: PageQuery): Promise<Page<Bill>> { return toPage(await listBills(params), Number(params.page || 1), Number(params.size || 10)) }
function refresh() { ruleTable.value?.refresh(); billTable.value?.refresh() }
function openRule(row?: BillingRule) { dialog.value = { visible: true, title: row ? '编辑计费规则' : '新增计费规则', fields: ruleFields, initial: row as never || { currency: 'CNY' }, submit: async (form) => { row?.id ? await updateBillingRule(row.id, form as never) : await createBillingRule(form as never) } } }
function openBill() { dialog.value = { visible: true, title: '生成账单', fields: billFields, initial: { billType: 'EXPENSE', period: 'MONTHLY' }, submit: async (form) => { await generateBill(form) } } }
async function confirm(row: Bill) {
  try {
    await confirmBill(row.billNo)
    ElMessage.success('账单已确认')
    refresh()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '账单确认失败')
  }
}
async function showBill(row: Bill) {
  selectedBill.value = await getBill(row.billNo)
  detailVisible.value = true
}
function dispute(row: Bill) {
  dialog.value = {
    visible: true,
    title: '账单异议',
    fields: [{ prop: 'reason', label: '异议原因', type: 'textarea', required: true }],
    initial: {},
    submit: async (form) => { await disputeBill(row.billNo, String(form.reason || '')) }
  }
}
async function loadStats() { stats.value = await getBillingStats() }
</script>
