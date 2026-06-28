import { api, unwrap } from './client'
import type { DataServiceDefinition, Page, PageQuery } from './types'

export interface ServicePayload {
  serviceCode: string
  name: string
  routeKey: string
}

export interface ApiCredentialView {
  id: number
  apiKey: string
  consumerCode: string
  serviceCode: string
  status: string
  rotatedFrom?: number
}

export interface CreatedCredential extends ApiCredentialView {
  secret: string
}

export const listServices = async (params: PageQuery = {}) => unwrap<DataServiceDefinition[]>(await api.get('/services', { params }))
export const getService = async (serviceCode: string) => unwrap<DataServiceDefinition>(await api.get(`/services/${serviceCode}`))
export const registerService = async (data: ServicePayload) => unwrap<DataServiceDefinition>(await api.post('/services', data))
export const updateService = async (serviceCode: string, data: Omit<ServicePayload, 'serviceCode'>) => unwrap<DataServiceDefinition>(await api.put(`/services/${serviceCode}`, data))
export const testService = async (serviceCode: string) => unwrap<DataServiceDefinition>(await api.post(`/services/${serviceCode}/test`))
export const defineService = async (serviceCode: string) => unwrap<DataServiceDefinition>(await api.post(`/services/${serviceCode}/define`))
export const publishService = async (serviceCode: string) => unwrap<DataServiceDefinition>(await api.post(`/services/${serviceCode}/publish`))
export const offlineService = async (serviceCode: string) => unwrap<DataServiceDefinition>(await api.post(`/services/${serviceCode}/offline`))
export const listServiceLogs = async (serviceCode: string, params: PageQuery = {}) => unwrap<Page<unknown>>(await api.get(`/services/${serviceCode}/logs`, { params }))
export const invokeService = async (serviceCode: string, data: Record<string, unknown>) => unwrap<string>(await api.post(`/services/${serviceCode}/invoke`, data))
export const listServiceCredentials = async (serviceCode: string) => unwrap<ApiCredentialView[]>(await api.get(`/services/${serviceCode}/credentials`))
export const createServiceCredential = async (serviceCode: string, consumerCode: string) => unwrap<CreatedCredential>(await api.post(`/services/${serviceCode}/credentials`, { consumerCode }))
export const rotateServiceCredential = async (id: number) => unwrap<CreatedCredential>(await api.post(`/services/credentials/${id}/rotate`))
export const disableServiceCredential = async (id: number) => unwrap<ApiCredentialView>(await api.post(`/services/credentials/${id}/disable`))
