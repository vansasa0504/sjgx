import { api, unwrap } from './client'
import type { IngestTask, Page, PageQuery } from './types'

export interface IngestPayload {
  partnerId: number
  endpoint: string
  syncMode?: string
  cron?: string
  fieldMapping?: Record<string, string>
  qualityRules?: string[]
}

export const listIngestTasks = async (params: PageQuery = {}) => unwrap<IngestTask[]>(await api.get('/ingest/tasks', { params }))
export const getIngestTask = async (id: number) => unwrap<IngestTask>(await api.get(`/ingest/tasks/${id}`))
export const createIngestTask = async (data: IngestPayload) => unwrap<IngestTask>(await api.post('/ingest/tasks', data))
export const updateMapping = async (id: number, fieldMapping: Record<string, string>) => unwrap<IngestTask>(await api.put(`/ingest/tasks/${id}/mapping`, { fieldMapping }))
export const updateRules = async (id: number, qualityRules: string[]) => unwrap<IngestTask>(await api.put(`/ingest/tasks/${id}/rules`, { qualityRules }))
export const testIngest = async (id: number) => unwrap<unknown[]>(await api.post(`/ingest/tasks/${id}/test`))
export const submitIngest = async (id: number) => unwrap<IngestTask>(await api.post(`/ingest/tasks/${id}/submit`))
export const approveIngest = async (id: number) => unwrap<IngestTask>(await api.post(`/ingest/tasks/${id}/approve`))
export const offlineIngest = async (id: number) => unwrap<IngestTask>(await api.post(`/ingest/tasks/${id}/offline`))
export const listIngestRecords = async (params: PageQuery = {}) => unwrap<Page<unknown>>(await api.get('/ingest/tasks/records', { params }))
