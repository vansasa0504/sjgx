import { api, unwrap } from './client'
import type { CatalogItem, PageQuery } from './types'

export interface CatalogPreview {
  sample: Record<string, string>[]
  stats: Record<string, unknown>
  qualityReport: string
}

export interface CatalogApplication {
  id: number
  catalogId: number
  applicant: string
  reason: string
  scope: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  approver?: string
  createdAt: string
  approvedAt?: string
}

export const listCatalog = async (params: PageQuery = {}) => unwrap<CatalogItem[]>(await api.get('/catalog', { params }))
export const getCatalogMeta = async (id: number) => unwrap<CatalogItem>(await api.get(`/catalog/${id}/meta`))
export const previewCatalog = async (id: number) => unwrap<CatalogPreview>(await api.get(`/catalog/${id}/preview`))
export const searchCatalog = async (keyword?: string) => unwrap<CatalogItem[]>(await api.get('/catalog/search', { params: { keyword } }))
export const applyCatalog = async (id: number, data: { reason: string; scope: string }) => unwrap<CatalogApplication>(await api.post(`/catalog/${id}/apply`, data))
export const approveApplication = async (id: number) => unwrap<CatalogApplication>(await api.post(`/catalog/applications/${id}/approve`))
export const rejectApplication = async (id: number) => unwrap<CatalogApplication>(await api.post(`/catalog/applications/${id}/reject`))
