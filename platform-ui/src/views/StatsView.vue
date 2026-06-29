<template>
  <section class="panel">
    <div class="page-header">
      <h1>统计监管</h1>
      <el-button @click="loadDashboard">刷新面板</el-button>
    </div>
    <div class="grid">
      <div class="metric">调用量 {{ dashboardValue('invokeCount') }}</div>
      <div class="metric">成功率 {{ dashboardValue('successRate') }}</div>
      <div class="metric">服务数 {{ dashboardValue('runningServices') }}</div>
      <div class="metric">成本 {{ dashboardValue('costAmount') }}</div>
    </div>
    <div ref="chartEl" class="stats-chart"></div>
    <el-divider />
    <el-form :inline="true">
      <el-form-item label="报表类型"><el-input v-model="reportType" /></el-form-item>
      <el-form-item><el-button type="primary" @click="loadReport">生成报表</el-button></el-form-item>
      <el-form-item><el-button @click="exportReport">导出报表</el-button></el-form-item>
    </el-form>
    <pre>{{ report }}</pre>
    <h2>合规审计</h2>
    <div class="audit-tools">
      <el-button @click="loadAuditVerify">校验审计链</el-button>
      <el-tag v-if="auditVerify" :type="auditVerify.intact ? 'success' : 'danger'">
        {{ auditVerify.intact ? '审计链完整' : `断链 ${auditVerify.firstBrokenId}` }}
      </el-tag>
    </div>
    <PageTable :columns="auditColumns" :filters="auditFilters" :fetch-data="fetchAudit" />
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import PageTable from '../components/PageTable.vue'
import { fetchDashboard, generateReport, listAudit, verifyAudit } from '../api/stats'
import { toPage, type Page, type PageQuery } from '../api/types'

const dashboard = ref<Record<string, unknown>>({})
const chartEl = ref<HTMLElement>()
let chart: echarts.ECharts | undefined
const report = ref<unknown>()
const reportType = ref('COMPLIANCE')
const auditVerify = ref<Record<string, unknown> & { intact?: boolean; firstBrokenId?: number }>()
const auditColumns = [{ prop: 'traceId', label: 'TraceID' }, { prop: 'eventType', label: '事件类型' }, { prop: 'actor', label: '操作者' }, { prop: 'action', label: '动作' }, { prop: 'status', label: '状态' }]
const auditFilters = [{ prop: 'traceId', label: 'TraceID' }, { prop: 'eventType', label: '事件类型' }]

function dashboardValue(key: string) { return dashboard.value?.[key] ?? '-' }
async function loadDashboard() {
  dashboard.value = await fetchDashboard() as Record<string, unknown>
  if (chartEl.value && !chart) chart = echarts.init(chartEl.value)
  chart?.setOption({
    tooltip: {},
    xAxis: { type: 'category', data: ['调用量', '服务数', '成本'] },
    yAxis: { type: 'value' },
    series: [{ type: 'bar', data: [Number(dashboardValue('invokeCount')) || 0, Number(dashboardValue('runningServices')) || 0, Number(dashboardValue('costAmount')) || 0] }]
  })
}
async function loadReport() { report.value = await generateReport({ type: reportType.value }) }
function exportReport() { ElMessage.success('报表已生成，可在后端报表目录下载') }
async function loadAuditVerify() { auditVerify.value = await verifyAudit() as Record<string, unknown> & { intact?: boolean; firstBrokenId?: number } }
async function fetchAudit(params: PageQuery): Promise<Page<Record<string, unknown>>> {
  return toPage(await listAudit({ eventType: params.eventType || 'login', traceId: params.traceId, ...params }) as Record<string, unknown>[], Number(params.page || 1), Number(params.size || 10))
}
onMounted(loadDashboard)
</script>

<style scoped>
.stats-chart { height: 260px; margin: 16px 0; }
.audit-tools { display: flex; gap: 12px; align-items: center; margin-bottom: 12px; }
</style>
