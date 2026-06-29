<template>
  <section class="panel">
    <div class="page-header"><h1>系统管理</h1></div>
    <el-tabs @tab-change="loadAux">
      <el-tab-pane label="用户">
        <el-button v-if="auth.hasPermission('system:create')" type="primary" @click="openUser()">新建用户</el-button>
        <PageTable ref="userTable" :columns="userColumns" :fetch-data="fetchUsers">
          <template #actions="{ row }">
            <el-button v-if="auth.hasPermission('system:update')" size="small" @click="openUser(row as UserAccount)">编辑权限</el-button>
          </template>
        </PageTable>
      </el-tab-pane>
      <el-tab-pane label="角色">
        <el-button v-if="auth.hasPermission('system:create')" type="primary" @click="openRole">新建角色</el-button>
        <el-table :data="roles" border>
          <el-table-column prop="name" label="角色" />
          <el-table-column label="权限数"><template #default="{ row }">{{ row.permissions?.length || 0 }}</template></el-table-column>
          <el-table-column label="操作"><template #default="{ row }"><el-button v-if="auth.hasPermission('system:update')" size="small" @click="openRolePerm(row)">配置权限</el-button></template></el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="权限">
        <el-tag v-for="permission in permissions" :key="permission" class="permission-tag">{{ permission }}</el-tag>
      </el-tab-pane>
    </el-tabs>
    <FormDialog v-model="dialog.visible" :title="dialog.title" :fields="dialog.fields" :initial="dialog.initial" :submit="dialog.submit" @success="refresh" />
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import PageTable from '../components/PageTable.vue'
import FormDialog, { type FormField } from '../components/FormDialog.vue'
import { createRole, createUser, listPermissions, listRoles, listUsers, updateRolePermissions, updateUser } from '../api/system'
import type { Page, PageQuery, Role, UserAccount } from '../api/types'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const userTable = ref<{ refresh: () => Promise<void> }>()
const roles = ref<Role[]>([])
const permissions = ref<string[]>([])
const userColumns = [{ prop: 'username', label: '用户名' }, { prop: 'permissions', label: '权限' }, { prop: 'actions', label: '操作', width: 140 }]
const userFields: FormField[] = [{ prop: 'username', label: '用户名', type: 'input', required: true }, { prop: 'password', label: '密码', type: 'input', required: true }, { prop: 'permissions', label: '权限CSV', type: 'textarea' }]
const roleFields: FormField[] = [{ prop: 'name', label: '角色名', type: 'input', required: true }, { prop: 'permissions', label: '权限CSV', type: 'textarea' }]
const dialog = ref({ visible: false, title: '', fields: [] as FormField[], initial: {} as Record<string, unknown>, submit: async (_form: Record<string, unknown>) => {} })

async function fetchUsers(params: PageQuery): Promise<Page<UserAccount>> { return listUsers(params) }
function csv(value: unknown) { return String(value || '').split(',').map((v) => v.trim()).filter(Boolean) }
function openUser(row?: UserAccount) {
  const permissions = Array.isArray(row?.permissions) ? row.permissions : []
  dialog.value = { visible: true, title: row ? '编辑用户' : '新建用户', fields: row ? userFields.filter((field) => field.prop !== 'password') : userFields, initial: row ? { ...row, permissions: permissions.join(',') } : {}, submit: async (form) => { row ? await updateUser(row.username, csv(form.permissions)) : await createUser({ username: String(form.username), password: String(form.password), permissions: csv(form.permissions) }) } }
}
function openRole() { dialog.value = { visible: true, title: '新建角色', fields: roleFields, initial: {}, submit: async (form) => { await createRole({ name: String(form.name), permissions: csv(form.permissions) }) } } }
function openRolePerm(row: Role) {
  const permissions = Array.isArray(row.permissions) ? row.permissions : []
  dialog.value = { visible: true, title: '配置角色权限', fields: roleFields, initial: { name: row.name, permissions: permissions.join(',') }, submit: async (form) => { await updateRolePermissions(row.name, csv(form.permissions)) } }
}
async function loadAux() {
  if (roles.value.length === 0) roles.value = await listRoles()
  if (permissions.value.length === 0) permissions.value = await listPermissions()
}
function refresh() { userTable.value?.refresh(); loadAux() }
onMounted(loadAux)
</script>

<style scoped>
.permission-tag { margin: 4px; }
</style>
