<template>
  <el-container class="console-layout">
    <el-aside class="console-aside" :width="collapsed ? '64px' : '232px'">
      <div class="brand">{{ collapsed ? '外' : '外部数据平台' }}</div>
      <el-menu :default-active="route.path" router class="console-menu" :collapse="collapsed">
        <el-menu-item v-for="item in menu" :key="item.path" :index="`/${item.path}`">
          <span>{{ item.meta?.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="console-header">
        <div class="header-title">
          <el-button class="collapse-button" size="small" @click="collapsed = !collapsed">
            {{ collapsed ? '展开' : '收起' }}
          </el-button>
          <span>{{ currentTitle }}</span>
        </div>
        <div class="header-actions">
          <span class="username">{{ auth.username || 'admin' }}</span>
          <el-button size="small" @click="logout">退出</el-button>
        </div>
      </el-header>
      <el-main class="content">
        <RouterView />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import { permittedRoutes } from '../router'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const collapsed = ref(false)
const menu = computed(() => permittedRoutes(auth.permissions))
const currentTitle = computed(() => route.meta.title || '控制台')

async function logout() {
  await auth.logout()
  router.push('/login')
}
</script>

<style scoped>
.console-layout {
  min-height: 100vh;
}
.console-aside {
  background: #17202a;
}
.brand {
  color: #fff;
  font-size: 18px;
  font-weight: 700;
  padding: 20px 18px;
  white-space: nowrap;
}
.console-menu {
  border-right: 0;
  background: transparent;
}
.console-menu :deep(.el-menu-item) {
  color: #dbe7f3;
}
.console-menu :deep(.el-menu-item.is-active) {
  color: #fff;
  background: #1f7a8c;
}
.console-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  border-bottom: 1px solid #e5e9f0;
}
.header-title {
  display: flex;
  align-items: center;
  gap: 12px;
}
.collapse-button {
  display: none;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
@media (max-width: 760px) {
  .collapse-button {
    display: inline-flex;
  }
}
</style>
