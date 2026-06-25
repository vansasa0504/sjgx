import axios from 'axios'

export const api = axios.create({ baseURL: '/api/v1', timeout: 5000 })

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      window.dispatchEvent(new CustomEvent('auth-expired'))
    }
    return Promise.reject(error)
  }
)

export async function fetchDashboard() {
  const { data } = await api.get('/stats/dashboard')
  return data
}