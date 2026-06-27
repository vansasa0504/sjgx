<template>
  <section class="page-table">
    <el-form v-if="filters.length" :inline="true" class="page-table-filters">
      <el-form-item v-for="filter in filters" :key="filter.prop" :label="filter.label">
        <el-select
          v-if="filter.options?.length"
          v-model="filterModel[filter.prop]"
          clearable
          placeholder="请选择"
          @change="search"
        >
          <el-option v-for="option in filter.options" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
        <el-input v-else v-model="filterModel[filter.prop]" clearable placeholder="请输入" @change="search" />
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="records" border>
      <el-table-column
        v-for="column in columns"
        :key="column.prop"
        :prop="column.prop"
        :label="column.label"
        :width="column.width"
        :formatter="column.formatter"
      />
      <template #empty>
        <el-empty description="暂无数据" />
      </template>
    </el-table>

    <el-pagination
      class="page-table-pagination"
      layout="total, sizes, prev, pager, next"
      :current-page="page"
      :page-size="size"
      :page-sizes="pageSizes"
      :total="total"
      @current-change="changePage"
      @size-change="changeSize"
    />
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'

export interface TableColumn {
  prop: string
  label: string
  width?: string | number
  formatter?: (row: Record<string, unknown>, column: unknown, value: unknown) => string
}

export interface TableFilter {
  prop: string
  label: string
  options?: Array<{ label: string; value: string | number | boolean }>
}

const props = withDefaults(defineProps<{
  columns: TableColumn[]
  fetchData: (params: Record<string, unknown>) => Promise<{ records: Record<string, unknown>[]; total: number }>
  filters?: TableFilter[]
  pageSizes?: number[]
}>(), {
  filters: () => [],
  pageSizes: () => [10, 20, 50]
})

const loading = ref(false)
const records = ref<Record<string, unknown>[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const filterModel = reactive<Record<string, unknown>>({})
const filters = props.filters

async function load() {
  loading.value = true
  try {
    const result = await props.fetchData({ page: page.value, size: size.value, ...filterModel })
    records.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function changePage(nextPage: number) {
  page.value = nextPage
  load()
}

function changeSize(nextSize: number) {
  size.value = nextSize
  page.value = 1
  load()
}

onMounted(load)
defineExpose({ refresh: load })
</script>

<style scoped>
.page-table-filters {
  margin-bottom: 12px;
}
.page-table-pagination {
  justify-content: flex-end;
  margin-top: 14px;
}
</style>
