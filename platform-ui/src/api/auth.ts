import { createClient, unwrap } from './client'

const authApi = createClient('/auth')

interface TokenResponse {
  token: string
}

export async function login(username: string, password: string): Promise<string> {
  const response = await authApi.post('/login', { username, password })
  return unwrap<TokenResponse>(response).token
}

export async function refresh(): Promise<string> {
  const response = await authApi.post('/refresh')
  return unwrap<TokenResponse>(response).token
}

export async function logout(): Promise<void> {
  const response = await authApi.post('/logout')
  unwrap<void>(response)
}

export async function fetchPermissions(): Promise<string[]> {
  const response = await authApi.get('/permissions')
  return unwrap<string[]>(response)
}
