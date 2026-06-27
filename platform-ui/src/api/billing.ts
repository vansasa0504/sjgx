import { api, unwrap } from './client'
import type { Bill, BillingRule } from './types'

export const listBillingRules = async (params: Record<string, unknown> = {}) => unwrap<BillingRule[]>(await api.get('/billing/rules', { params }))
export const createBillingRule = async (data: BillingRule) => unwrap<BillingRule>(await api.post('/billing/rules', data))
export const updateBillingRule = async (id: number, data: BillingRule) => unwrap<BillingRule>(await api.put(`/billing/rules/${id}`, data))
export const listBills = async (params: Record<string, unknown> = {}) => unwrap<Bill[]>(await api.get('/billing/bills', { params }))
export const generateBill = async (data: Record<string, unknown>) => unwrap<Bill>(await api.post('/billing/bills/generate', data))
export const confirmBill = async (billNo: string) => unwrap<Bill>(await api.post(`/billing/bills/${billNo}/confirm`))
export const disputeBill = async (billNo: string, reason: string) => unwrap<Bill>(await api.post(`/billing/bills/${billNo}/dispute`, { reason }))
export const getBillingStats = async (params: Record<string, unknown> = {}) => unwrap<unknown>(await api.get('/billing/stats', { params }))
