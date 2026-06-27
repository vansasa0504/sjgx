import { api, unwrap } from './client'
import type { Consumer, Page, PageQuery } from './types'

export interface ConsumerPayload {
  code: string
  name: string
  bizLine?: string
  systemType?: string
  complianceLevel?: string
}

export const listConsumers = async (params: PageQuery = {}) => unwrap<Consumer[]>(await api.get('/consumers', { params }))
export const getConsumer = async (id: number) => unwrap<Consumer>(await api.get(`/consumers/${id}`))
export const registerConsumer = async (data: ConsumerPayload) => unwrap<Consumer>(await api.post('/consumers', data))
export const configureQuota = async (id: number, data: { maxRequests: number; warnThreshold: number }) =>
  unwrap<unknown>(await api.put(`/consumers/${id}/quota`, data))
export const applyConsumerEvent = async (id: number, event: string) => unwrap<Consumer>(await api.post(`/consumers/${id}/events`, { event }))
export const getConsumerAudit = async (id: number, params: PageQuery = {}) => unwrap<Page<unknown>>(await api.get(`/consumers/${id}/audit`, { params }))
export const getConsumerLogs = async (id: number, params: PageQuery = {}) => unwrap<Page<unknown>>(await api.get(`/consumers/${id}/logs`, { params }))
