import { api, unwrap } from './client'
import type { CatalogItem, PageQuery } from './types'

export const listCatalog = async (params: PageQuery = {}) => unwrap<CatalogItem[]>(await api.get('/catalog', { params }))
export const getCatalogMeta = async (id: number) => unwrap<CatalogItem>(await api.get(`/catalog/${id}/meta`))
export const previewCatalog = async (id: number) => unwrap<unknown>(await api.get(`/catalog/${id}/preview`))
export const searchCatalog = async (keyword?: string) => unwrap<CatalogItem[]>(await api.get('/catalog/search', { params: { keyword } }))
export const applyCatalog = async (id: number, data: { reason: string; scope: string }) => unwrap<unknown>(await api.post(`/catalog/${id}/apply`, data))
export const approveApplication = async (id: number) => unwrap<unknown>(await api.post(`/catalog/applications/${id}/approve`))
