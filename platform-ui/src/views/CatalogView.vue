<template>
  <section class="panel">
    <div class="page-header">
      <h1>数据目录</h1>
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="检索关键词" clearable @keyup.enter="search" />
        <el-input v-model="filters.subject" placeholder="主题" clearable />
        <el-input v-model="filters.partnerId" placeholder="合作方ID" clearable />
        <el-input v-model="filters.dataType" placeholder="数据类型" clearable />
        <el-input v-model="filters.scenario" placeholder="场景" clearable />
        <el-button @click="search">检索</el-button>
        <el-button @click="load">筛选</el-button>
      </div>
    </div>
    <div class="grid">
      <el-card v-for="item in items" :key="item.id" class="catalog-card">
        <h3>{{ item.name || item.subject || `资产 ${item.id}` }}</h3>
        <p>类型：{{ item.dataType || '-' }}</p>
        <p>场景：{{ item.scenario || '-' }}</p>
        <el-button size="small" @click="openMeta(item)">元信息</el-button>
        <el-button size="small" @click="openPreview(item)">预览</el-button>
        <el-button v-if="auth.hasPermission('catalog:apply')" size="small" type="primary" @click="openApply(item)">申请</el-button>
        <el-button
          v-if="auth.hasPermission('catalog:approve') && pendingApplicationId(item)"
          size="small"
          type="success"
          @click="approveApplicationFor(item)"
        >
          审批申请
        </el-button>
      </el-card>
    </div>
    <FormDialog v-model="applyDialog.visible" title="申请使用" :fields="applyFields" :initial="{}" :submit="submitApply" @success="load" />
    <el-drawer v-model="drawerVisible" title="数据目录详情">
      <el-table v-if="previewSample.length" :data="previewSample" border />
      <pre>{{ selectedDetail }}</pre>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import FormDialog, { type FormField } from '../components/FormDialog.vue'
import { applyCatalog, approveApplication, getCatalogMeta, listCatalog, previewCatalog, searchCatalog } from '../api/catalog'
import type { CatalogItem } from '../api/types'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const keyword = ref('')
const filters = ref({ subject: '', partnerId: '', dataType: '', scenario: '' })
const items = ref<CatalogItem[]>([])
const current = ref<CatalogItem>()
const pendingApplicationIds = ref<Record<number, number>>({})
const drawerVisible = ref(false)
const selectedDetail = ref<unknown>()
const previewSample = ref<Record<string, unknown>[]>([])
const applyDialog = ref({ visible: false })
const applyFields: FormField[] = [
  { prop: 'reason', label: '申请原因', type: 'textarea', required: true },
  { prop: 'scope', label: '使用范围', type: 'textarea', required: true }
]

async function load() {
  items.value = await listCatalog({
    subject: filters.value.subject || undefined,
    partnerId: filters.value.partnerId ? Number(filters.value.partnerId) : undefined,
    dataType: filters.value.dataType || undefined,
    scenario: filters.value.scenario || undefined
  })
}
async function search() { items.value = keyword.value ? await searchCatalog(keyword.value) : await listCatalog() }
async function openMeta(item: CatalogItem) { previewSample.value = []; selectedDetail.value = await getCatalogMeta(item.id); drawerVisible.value = true }
async function openPreview(item: CatalogItem) {
  const result = await previewCatalog(item.id) as { sample?: Record<string, unknown>[] }
  previewSample.value = result.sample || []
  selectedDetail.value = result
  drawerVisible.value = true
}
function openApply(item: CatalogItem) { current.value = item; applyDialog.value.visible = true }
async function submitApply(form: Record<string, unknown>) {
  if (current.value) {
    const application = await applyCatalog(current.value.id, form as never) as { id?: number }
    if (application.id) pendingApplicationIds.value[current.value.id] = application.id
  }
}
function pendingApplicationId(item: CatalogItem) {
  return pendingApplicationIds.value[item.id]
}
async function approveApplicationFor(item: CatalogItem) {
  const applicationId = pendingApplicationId(item)
  if (applicationId) {
    await approveApplication(applicationId)
    delete pendingApplicationIds.value[item.id]
  }
}
onMounted(load)
</script>
