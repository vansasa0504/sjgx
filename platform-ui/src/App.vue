<template>
  <RouterView />
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from './stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

async function handleAuthExpired() {
  await auth.logout({ remote: false })
  ElMessage.error('登录已过期，请重新登录')
  router.push({ path: '/login', query: { redirect: route.fullPath } })
}

onMounted(() => window.addEventListener('auth-expired', handleAuthExpired))
onBeforeUnmount(() => window.removeEventListener('auth-expired', handleAuthExpired))
</script>
