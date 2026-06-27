import { api, unwrap } from './client'
import type { Page, PageQuery, Partner } from './types'

export interface PartnerPayload {
  name: string
  dataType?: string
  industry?: string
  complianceLevel?: string
}

export interface InterfacePayload {
  protocol: string
  endpoint: string
  credential: string
  authType?: string
  rateLimit?: number
}

export const listPartners = async (params: PageQuery = {}) =>
  unwrap<Page<Partner>>(await api.get('/partners', { params }))
export const getPartner = async (id: number) => unwrap<Partner>(await api.get(`/partners/${id}`))
export const createPartner = async (data: PartnerPayload) => unwrap<Partner>(await api.post('/partners', data))
export const updatePartner = async (id: number, data: PartnerPayload) => unwrap<Partner>(await api.put(`/partners/${id}`, data))
export const submitPartner = async (id: number) => unwrap<Partner>(await api.post(`/partners/${id}/submit`))
export const approvePartner = async (id: number) => unwrap<Partner>(await api.post(`/partners/${id}/approve`))
export const admitPartner = async (id: number) => unwrap<Partner>(await api.post(`/partners/${id}/admit`))
export const rejectPartner = async (id: number, reason: string) => unwrap<Partner>(await api.post(`/partners/${id}/reject`, { reason }))
export const ratePartner = async (id: number, score: string) => unwrap<Partner>(await api.put(`/partners/${id}/rating`, { score }))
export const terminatePartner = async (id: number) => unwrap<Partner>(await api.post(`/partners/${id}/terminate`))
export const configureInterface = async (id: number, data: InterfacePayload) => unwrap<unknown>(await api.post(`/partners/${id}/interfaces`, data))
export const listInterfaces = async (id: number) => unwrap<unknown[]>(await api.get(`/partners/${id}/interfaces`))
export const listPartnerEvents = async (id: number) => unwrap<string[]>(await api.get(`/partners/${id}/events`))
