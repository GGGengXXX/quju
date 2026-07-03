import axios from 'axios'
import { ElMessage } from 'element-plus'

declare module 'axios' {
  interface AxiosRequestConfig {
    silentError?: boolean
  }
}

function resolveBaseUrl() {
  const envBase = (import.meta as any).env?.VITE_API_BASE as string | undefined
  if (envBase && envBase.trim()) {
    return envBase
      .replace('127.0.0.1', location.hostname)
      .replace('localhost', location.hostname)
  }
  if ((import.meta as any).env?.DEV) {
    return `${location.protocol}//${location.hostname}:8541/v1`
  }
  return '/v1'
}

const http = axios.create({
  baseURL: resolveBaseUrl(),
  timeout: 15000,
})

// 注入 JWT（管理员接口用 admin token，其它用普通 token）
http.interceptors.request.use((cfg) => {
  const isAdmin = cfg.url?.startsWith('/admin')
  const token = localStorage.getItem(isAdmin ? 'quju_admin_token' : 'quju_token')
  if (token) cfg.headers.Authorization = `Bearer ${token}`
  return cfg
})

http.interceptors.response.use(
  (resp) => {
    const r = resp.data
    if (r && typeof r.code === 'number') {
      if (r.code === 0) return r.data
      if (r.code === 1001) {
        const isAdmin = location.pathname.startsWith('/admin')
        localStorage.removeItem(isAdmin ? 'quju_admin_token' : 'quju_token')
        const loginPath = isAdmin ? '/admin/login' : '/login'
        if (!location.pathname.startsWith(loginPath)) location.assign(loginPath)
      }
      if (!resp.config.silentError) ElMessage.error(r.message || '请求失败')
      return Promise.reject(new Error(r.message || 'error'))
    }
    return r
  },
  (err) => {
    if (!err?.config?.silentError) ElMessage.error(err?.message || 'Network Error')
    return Promise.reject(err)
  }
)

export default http
