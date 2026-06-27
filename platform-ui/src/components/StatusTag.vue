<template>
  <el-tag :type="entry.type">{{ entry.text }}</el-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue'

type TagType = 'success' | 'warning' | 'danger' | 'info'

const props = defineProps<{
  status: string
  map?: Record<string, { type: TagType; text: string }>
}>()

const defaults: Record<string, { type: TagType; text: string }> = {
  PENDING: { type: 'warning', text: '待处理' },
  SUBMITTED: { type: 'warning', text: '已提交' },
  PENDING_APPROVAL: { type: 'warning', text: '待审批' },
  APPROVED: { type: 'success', text: '已审批' },
  REJECTED: { type: 'danger', text: '已驳回' },
  ACTIVE: { type: 'success', text: '有效' },
  SUSPENDED: { type: 'warning', text: '暂停' },
  TERMINATED: { type: 'danger', text: '已终止' },
  DRAFT: { type: 'info', text: '草稿' },
  TESTING: { type: 'warning', text: '测试中' },
  RUNNING: { type: 'success', text: '运行中' },
  ONLINE: { type: 'success', text: '在线' },
  OFFLINE: { type: 'info', text: '下线' },
  PUBLISHED: { type: 'success', text: '已发布' },
  GENERATED: { type: 'info', text: '已生成' },
  CONFIRMED: { type: 'success', text: '已确认' },
  DISPUTED: { type: 'warning', text: '异议中' },
  SETTLED: { type: 'success', text: '已结算' },
  OPEN: { type: 'warning', text: '打开' },
  ASSIGNED: { type: 'warning', text: '已指派' },
  FIXING: { type: 'warning', text: '整改中' },
  VERIFYING: { type: 'warning', text: '验证中' },
  CLOSED: { type: 'success', text: '关闭' },
  FAILED: { type: 'danger', text: '失败' },
  SUCCESS: { type: 'success', text: '成功' }
}

const entry = computed(() => props.map?.[props.status] || defaults[props.status] || { type: 'info' as TagType, text: props.status })
</script>
