import { api, unwrap } from './client'
import type { QualityRule } from './types'

export const listQualityRules = async (params: Record<string, unknown> = {}) => unwrap<QualityRule[]>(await api.get('/quality/rules', { params }))
export const createQualityRule = async (data: QualityRule) => unwrap<QualityRule>(await api.post('/quality/rules', data))
export const updateQualityRule = async (id: number, data: QualityRule) => unwrap<QualityRule>(await api.put(`/quality/rules/${id}`, data))
export const listChecks = async () => unwrap<unknown[]>(await api.get('/quality/checks'))
export const triggerCheck = async (data: Record<string, unknown>) => unwrap<unknown>(await api.post('/quality/checks', data))
export const listIssues = async () => unwrap<unknown[]>(await api.get('/quality/issues'))
export const assignIssue = async (id: number, assignee: string) => unwrap<unknown>(await api.post(`/quality/issues/${id}/assign`, { assignee }))
export const resolveIssue = async (id: number, resolution: string) => unwrap<unknown>(await api.post(`/quality/issues/${id}/resolve`, { resolution }))
export const getQualityReport = async (partnerId = 'ALL') => unwrap<unknown>(await api.get('/quality/reports', { params: { partnerId } }))
export const getQualityScore = async (partnerId = 'ALL') => unwrap<unknown>(await api.get('/quality/scores', { params: { partnerId } }))
