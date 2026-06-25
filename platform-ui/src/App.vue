<template>
  <RouterView v-if="$route.path === '/login'" />
  <div v-else class="shell">
    <aside class="sidebar">
      <h2>外部数据平台</h2>
      <RouterLink v-for="item in menu" :key="item.path" :to="item.path">{{ item.meta?.title }}</RouterLink>
    </aside>
    <main class="content">
      <RouterView />
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView } from 'vue-router'
import { permittedRoutes } from './router'
import { useAuthStore } from './stores/auth'

const auth = useAuthStore()
const menu = computed(() => permittedRoutes(auth.permissions).filter((route) => route.path !== '/login' && route.path !== '/'))
</script>