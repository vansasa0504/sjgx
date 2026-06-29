<template>
  <section class="panel monitor-panel">
    <div class="page-header"><h1>监控大屏</h1><el-button v-if="auth.hasPermission('stats:view')" @click="load">刷新</el-button></div>
    <div class="grid">
      <div class="metric">调用量 {{ value('invokeCount') }}</div>
      <div class="metric">成功率 {{ value('successRate') }}</div>
      <div class="metric">服务数 {{ value('runningServices') }}</div>
      <div class="metric">账单金额 {{ value('costAmount') }}</div>
    </div>
    <div ref="chartEl" class="monitor-chart"></div>
  </section>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { fetchDashboard } from '../api/stats'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const chartEl = ref<HTMLElement>()
const dashboard = ref<Record<string, unknown>>({})
let chart: echarts.ECharts | undefined
let timer: number | undefined

function value(key: string) { return dashboard.value[key] ?? '-' }
async function load() {
  try {
    dashboard.value = await fetchDashboard() as Record<string, unknown>
    if (chartEl.value && !chart) chart = echarts.init(chartEl.value)
    chart?.setOption({
      xAxis: { type: 'category', data: ['调用', '服务', '合规', '费用'] },
      yAxis: { type: 'value' },
      series: [{ type: 'bar', data: [Number(value('invokeCount')) || 0, Number(value('runningServices')) || 0, Number(value('complianceScore')) || 0, Number(value('costAmount')) || 0] }]
    })
  } catch (err) {
    dashboard.value = {}
    ElMessage.error(err instanceof Error ? err.message : '监控数据加载失败')
  }
}
onMounted(() => { load(); timer = window.setInterval(load, 30000) })
onBeforeUnmount(() => { if (timer) window.clearInterval(timer); chart?.dispose() })
</script>

<style scoped>
.monitor-panel { background: #101827; color: #f8fafc; }
.monitor-chart { height: 320px; margin-top: 16px; }
</style>
