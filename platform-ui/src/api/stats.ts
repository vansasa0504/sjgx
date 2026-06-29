import { api, unwrap } from './client'

export const fetchDashboard = async () => unwrap<unknown>(await api.get('/stats/dashboard'))
export const generateReport = async (params: { type: string; from?: string; to?: string }) => unwrap<unknown>(await api.get('/stats/reports', { params }))
export const listAudit = async (params: Record<string, unknown> = {}) => unwrap<unknown[]>(await api.get('/stats/audit', { params }))
export const verifyAudit = async () => unwrap<unknown>(await api.get('/stats/audit/verify'))
