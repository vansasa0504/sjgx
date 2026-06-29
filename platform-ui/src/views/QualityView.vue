<template>
  <section class="panel">
    <div class="page-header">
      <h1>数据质量</h1>
      <el-button v-if="auth.hasPermission('quality:create')" type="primary" @click="openRule()">新建规则</el-button>
    </div>
    <el-tabs>
      <el-tab-pane label="规则配置">
        <PageTable ref="ruleTable" :columns="ruleColumns" :fetch-data="fetchRules">
          <template #actions="{ row }">
            <el-button v-if="auth.hasPermission('quality:update')" size="small" @click="openRule(row as QualityRule)">编辑</el-button>
          </template>
        </PageTable>
      </el-tab-pane>
      <el-tab-pane label="校验结果">
        <el-button v-if="auth.hasPermission('quality:run')" type="primary" @click="openCheck">手动触发校验</el-button>
        <PageTable :columns="checkColumns" :fetch-data="fetchChecks" />
      </el-tab-pane>
      <el-tab-pane label="问题工单">
        <PageTable :columns="issueColumns" :fetch-data="fetchIssues">
          <template #actions="{ row }">
            <el-button v-if="auth.hasPermission('quality:update')" size="small" @click="assign(row as Record<string, unknown>)">指派</el-button>
            <el-button v-if="auth.hasPermission('quality:update')" size="small" type="success" @click="resolve(row as Record<string, unknown>)">解决</el-button>
          </template>
        </PageTable>
      </el-tab-pane>
      <el-tab-pane label="报告评分">
        <el-button @click="loadReport">刷新报告</el-button>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="报告">{{ report }}</el-descriptions-item>
          <el-descriptions-item label="评分">{{ score }}</el-descriptions-item>
        </el-descriptions>
      </el-tab-pane>
    </el-tabs>
    <FormDialog v-model="dialog.visible" :title="dialog.title" :fields="dialog.fields" :initial="dialog.initial" :submit="dialog.submit" @success="refreshRules" />
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import PageTable from '../components/PageTable.vue'
import FormDialog, { type FormField } from '../components/FormDialog.vue'
import { assignIssue, createQualityRule, getQualityReport, getQualityScore, listChecks, listIssues, listQualityRules, resolveIssue, triggerCheck, updateQualityRule } from '../api/quality'
import { toPage, type Page, type PageQuery, type QualityRule } from '../api/types'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const ruleTable = ref<{ refresh: () => Promise<void> }>()
const report = ref<unknown>()
const score = ref<unknown>()
const ruleColumns = [{ prop: 'ruleCode', label: '编码' }, { prop: 'dimension', label: '维度' }, { prop: 'field', label: '字段' }, { prop: 'weight', label: '权重' }, { prop: 'actions', label: '操作', width: 120 }]
const checkColumns = [{ prop: 'batchNo', label: '批次' }, { prop: 'total', label: '总数' }, { prop: 'passed', label: '通过' }, { prop: 'failed', label: '失败' }]
const issueColumns = [{ prop: 'id', label: 'ID' }, { prop: 'status', label: '状态' }, { prop: 'assignee', label: '指派人' }, { prop: 'actions', label: '操作', width: 160 }]
const ruleFields: FormField[] = [
  { prop: 'ruleCode', label: '编码', type: 'input', required: true },
  { prop: 'dimension', label: '维度', type: 'select', options: ['COMPLETENESS', 'ACCURACY', 'CONSISTENCY', 'TIMELINESS', 'VALIDITY', 'UNIQUENESS'].map((v) => ({ label: v, value: v })), required: true },
  { prop: 'field', label: '字段', type: 'input', required: true },
  { prop: 'weight', label: '权重', type: 'number' }
]
const dialog = ref({ visible: false, title: '', fields: [] as FormField[], initial: {} as Record<string, unknown>, submit: async (_form: Record<string, unknown>) => {} })
async function fetchRules(params: PageQuery): Promise<Page<QualityRule>> { return toPage(await listQualityRules(params), Number(params.page || 1), Number(params.size || 10)) }
async function fetchChecks(params: PageQuery): Promise<Page<Record<string, unknown>>> { return toPage(await listChecks() as Record<string, unknown>[], Number(params.page || 1), Number(params.size || 10)) }
async function fetchIssues(params: PageQuery): Promise<Page<Record<string, unknown>>> { return toPage(await listIssues() as Record<string, unknown>[], Number(params.page || 1), Number(params.size || 10)) }
function refreshRules() { ruleTable.value?.refresh() }
function openRule(row?: QualityRule) { dialog.value = { visible: true, title: row ? '编辑规则' : '新建规则', fields: ruleFields, initial: row as never || { weight: 10 }, submit: async (form) => { row?.id ? await updateQualityRule(row.id, form as never) : await createQualityRule(form as never) } } }
function openCheck() {
  dialog.value = {
    visible: true,
    title: '触发校验',
    fields: [
      { prop: 'batchNo', label: '批次', type: 'input', required: true },
      { prop: 'ruleIds', label: '规则ID CSV', type: 'textarea' }
    ],
    initial: {},
    submit: async (form) => { await triggerCheck({ ...form, rows: [], ruleIds: csv(form.ruleIds).map(Number), failRateThreshold: 1 }) }
  }
}
function assign(row: Record<string, unknown>) {
  dialog.value = {
    visible: true,
    title: '指派工单',
    fields: [{ prop: 'assignee', label: '指派人', type: 'input', required: true }],
    initial: { assignee: 'admin' },
    submit: async (form) => { await assignIssue(Number(row.id), String(form.assignee)) }
  }
}
function resolve(row: Record<string, unknown>) {
  dialog.value = {
    visible: true,
    title: '解决工单',
    fields: [{ prop: 'resolution', label: '解决说明', type: 'textarea', required: true }],
    initial: {},
    submit: async (form) => { await resolveIssue(Number(row.id), String(form.resolution)) }
  }
}
async function loadReport() { report.value = await getQualityReport(); score.value = await getQualityScore() }
function csv(value: unknown) { return String(value || '').split(',').map((item) => item.trim()).filter(Boolean) }
</script>
