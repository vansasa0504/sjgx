<template>
  <el-dialog v-model="visible" :title="title" width="520px" @closed="reset">
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="form-error" />
    <el-form ref="formRef" :model="form" label-width="108px">
      <el-form-item
        v-for="field in fields"
        :key="field.prop"
        :label="field.label"
        :prop="field.prop"
        :rules="field.rules || (field.required ? [{ required: true, message: `请输入${field.label}` }] : [])"
      >
        <el-select v-if="field.type === 'select'" v-model="form[field.prop]" placeholder="请选择">
          <el-option v-for="option in field.options || []" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
        <el-input-number v-else-if="field.type === 'number'" v-model="form[field.prop]" />
        <el-date-picker v-else-if="field.type === 'date'" v-model="form[field.prop]" type="date" value-format="YYYY-MM-DD" />
        <el-input v-else-if="field.type === 'textarea'" v-model="form[field.prop]" type="textarea" />
        <el-input v-else v-model="form[field.prop]" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import type { FormInstance, FormItemRule } from 'element-plus'

export interface FormField {
  prop: string
  label: string
  type: 'input' | 'select' | 'number' | 'date' | 'textarea'
  options?: Array<{ label: string; value: string | number | boolean }>
  required?: boolean
  rules?: FormItemRule[]
}

const props = withDefaults(defineProps<{
  modelValue: boolean
  title: string
  fields: FormField[]
  initial?: Record<string, unknown>
  submit: (form: Record<string, unknown>) => Promise<void>
}>(), {
  initial: () => ({})
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: []
}>()

const formRef = ref<FormInstance>()
const form = reactive<Record<string, unknown>>({})
const error = ref('')
const submitting = ref(false)
const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})

function applyInitial() {
  Object.keys(form).forEach((key) => delete form[key])
  props.fields.forEach((field) => {
    form[field.prop] = props.initial[field.prop] ?? ''
  })
}

function reset() {
  error.value = ''
  formRef.value?.resetFields()
  applyInitial()
}

async function submitForm() {
  error.value = ''
  try {
    await formRef.value?.validate()
    submitting.value = true
    await props.submit({ ...form })
    emit('success')
    visible.value = false
  } catch (err) {
    if (err instanceof Error) {
      error.value = err.message
    }
  } finally {
    submitting.value = false
  }
}

watch(() => props.modelValue, (value) => {
  if (value) applyInitial()
}, { immediate: true })
</script>

<style scoped>
.form-error {
  margin-bottom: 12px;
}
</style>
