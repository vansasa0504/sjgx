<template><section class="panel"><h1>监控大屏</h1><div ref="chartEl" style="height:320px"></div></section></template>
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import * as echarts from 'echarts'
const chartEl = ref<HTMLElement>()
onMounted(() => {
  if (!chartEl.value) return
  const chart = echarts.init(chartEl.value)
  chart.setOption({ xAxis: { type: 'category', data: ['接入', '服务', '合规', '费用'] }, yAxis: { type: 'value' }, series: [{ type: 'bar', data: [88, 96, 100, 73] }] })
})
</script>