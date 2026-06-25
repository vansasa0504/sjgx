<template>
  <main class="content">
    <section class="panel">
      <h1>外部数据采集平台</h1>
      <el-form>
        <el-form-item label="账号"><el-input v-model="username" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="password" type="password" /></el-form-item>
        <el-button type="primary" @click="login">登录</el-button>
      </el-form>
    </section>
  </main>
</template>
<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
const username = ref('admin')
const password = ref('demo')
const router = useRouter()
const auth = useAuthStore()
function login() { auth.login(); router.push('/partners') }
</script>