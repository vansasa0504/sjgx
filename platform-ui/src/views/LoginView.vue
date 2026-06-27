<template>
  <main class="content">
    <section class="panel login-panel">
      <h1>外部数据采集平台</h1>
      <el-alert
        v-if="error"
        :title="error"
        type="error"
        show-icon
        :closable="false"
        class="login-alert"
      />
      <el-form @submit.prevent="login">
        <el-form-item label="账号">
          <el-input v-model="username" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="password" type="password" autocomplete="current-password" show-password />
        </el-form-item>
        <el-button type="primary" :loading="loading" @click="login">登录</el-button>
      </el-form>
      <p class="login-hint">默认账号：admin / admin123</p>
    </section>
  </main>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const username = ref('admin')
const password = ref('admin123')
const loading = ref(false)
const error = ref('')
const router = useRouter()
const auth = useAuthStore()

async function login() {
  loading.value = true
  error.value = ''
  try {
    await auth.login(username.value, password.value)
    router.push('/partners')
  } catch (err) {
    error.value = err instanceof Error ? err.message : '登录失败，请检查账号密码或服务状态'
  } finally {
    loading.value = false
  }
}
</script>
