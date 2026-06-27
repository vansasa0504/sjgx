import { rootApi, unwrap } from './client'
import type { Page, PageQuery, Role, UserAccount } from './types'

export const listUsers = async (params: PageQuery = {}) => unwrap<Page<UserAccount>>(await rootApi.get('/users', { params }))
export const createUser = async (data: UserAccount) => unwrap<UserAccount>(await rootApi.post('/users', data))
export const updateUser = async (username: string, permissions: string[]) => unwrap<UserAccount>(await rootApi.put(`/users/${username}`, { permissions }))
export const listRoles = async () => unwrap<Role[]>(await rootApi.get('/roles'))
export const createRole = async (data: Role) => unwrap<Role>(await rootApi.post('/roles', data))
export const updateRolePermissions = async (name: string, permissions: string[]) => unwrap<Role>(await rootApi.put(`/roles/${name}/permissions`, { permissions }))
export const listPermissions = async () => unwrap<string[]>(await rootApi.get('/permissions'))
