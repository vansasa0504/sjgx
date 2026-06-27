import axios, { type AxiosInstance, type AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'

export interface ApiResult<T> {
  success?: boolean
  code?: string
  message?: string
  data?: T
}

export const api = createClient('/api/v1')
export const rootApi = createClient('')

let lastErrorMessage = ''
let lastErrorAt = 0

export function createClient(baseURL: string): AxiosInstance {
  const instance = axios.create({ baseURL, timeout: 10000 })

  instance.interceptors.request.use((config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  })

  instance.interceptors.response.use(
    (response) => {
      const payload = response.data as ApiResult<unknown>
      if (payload && typeof payload === 'object' && payload.success === false) {
        showErrorOnce(payload.message || '请求失败')
        return Promise.reject(new Error(payload.message || '请求失败'))
      }
      return response
    },
    (error) => {
      if (error.response?.status === 401) {
        window.dispatchEvent(new CustomEvent('auth-expired'))
      }
      const payload = error.response?.data as ApiResult<unknown> | undefined
      const message = payload?.message || error.message || '请求失败'
      if (error.response?.status !== 401) {
        showErrorOnce(message)
      }
      return Promise.reject(new Error(message))
    }
  )

  return instance
}

export function unwrap<T>(response: AxiosResponse<ApiResult<T> | T>): T {
  const payload = response.data as ApiResult<T> | T
  if (payload && typeof payload === 'object' && 'success' in payload) {
    const result = payload as ApiResult<T>
    if (result.success === false) {
      throw new Error(result.message || '请求失败')
    }
    return result.data as T
  }
  return payload as T
}

function showErrorOnce(message: string) {
  const now = Date.now()
  if (message === lastErrorMessage && now - lastErrorAt < 800) {
    return
  }
  lastErrorMessage = message
  lastErrorAt = now
  ElMessage.error(message)
}
