import { describe, expect, it, vi, beforeEach } from 'vitest'
import axiosMockAdapter from 'axios-mock-adapter'
import { api } from '../client'

describe('api client', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('injects bearer token', async () => {
    localStorage.setItem('token', 'token-1')
    const mock = new axiosMockAdapter(api)
    mock.onGet('/ping').reply((config) => [200, { auth: config.headers?.Authorization }])

    const response = await api.get('/ping')
    expect(response.data.auth).toBe('Bearer token-1')
  })

  it('dispatches auth-expired on 401', async () => {
    const listener = vi.fn()
    window.addEventListener('auth-expired', listener)
    const mock = new axiosMockAdapter(api)
    mock.onGet('/secure').reply(401, { message: 'unauthorized' })

    await expect(api.get('/secure')).rejects.toThrow('unauthorized')
    expect(listener).toHaveBeenCalled()
    window.removeEventListener('auth-expired', listener)
  })

  it('rejects business errors', async () => {
    const mock = new axiosMockAdapter(api)
    mock.onGet('/bad').reply(200, { success: false, message: 'bad request' })

    await expect(api.get('/bad')).rejects.toThrow('bad request')
  })
})
