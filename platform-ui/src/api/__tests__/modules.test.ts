import { describe, expect, it } from 'vitest'
import axiosMockAdapter from 'axios-mock-adapter'
import { api } from '../client'
import { listPartners, createPartner } from '../partner'
import { listServices, publishService } from '../service'
import { rejectApplication } from '../catalog'

describe('module api wrappers', () => {
  it('calls partner endpoints with params and body', async () => {
    const mock = new axiosMockAdapter(api)
    mock.onGet('/partners', { params: { page: 2, size: 20 } }).reply(200, {
      success: true,
      data: { records: [], total: 0, current: 2, size: 20 }
    })
    mock.onPost('/partners', { name: 'p1' }).reply(200, { success: true, data: { id: 1, name: 'p1' } })

    await expect(listPartners({ page: 2, size: 20 })).resolves.toMatchObject({ current: 2 })
    await expect(createPartner({ name: 'p1' })).resolves.toMatchObject({ id: 1 })
  })

  it('calls service endpoints', async () => {
    const mock = new axiosMockAdapter(api)
    mock.onGet('/services', { params: { status: 'ONLINE' } }).reply(200, { success: true, data: [] })
    mock.onPost('/services/svc-1/publish').reply(200, { success: true, data: { serviceCode: 'svc-1' } })

    await expect(listServices({ status: 'ONLINE' })).resolves.toEqual([])
    await expect(publishService('svc-1')).resolves.toMatchObject({ serviceCode: 'svc-1' })
  })

  it('calls catalog reject endpoint', async () => {
    const mock = new axiosMockAdapter(api)
    mock.onPost('/catalog/applications/9/reject').reply(200, {
      success: true,
      data: { id: 9, status: 'REJECTED' }
    })

    await expect(rejectApplication(9)).resolves.toMatchObject({ id: 9, status: 'REJECTED' })
  })
})
