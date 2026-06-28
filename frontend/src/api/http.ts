import axios from 'axios'
import { ElMessage } from 'element-plus'

declare module 'axios' {
  interface AxiosRequestConfig {
    silentError?: boolean
  }
}

// 生产由 nginx 反代 /v1；开发用 .env 的 VITE_API_BASE
const baseURL = (import.meta as any).env?.VITE_API_BASE || '/v1'
const http = axios.create({ baseURL, timeout: 15000 })

// 注入 JWT
http.interceptors.request.use((cfg) => {
  const token = localStorage.getItem('quju_token')
  if (token) cfg.headers.Authorization = `Bearer ${token}`
  return cfg
})

// 统一解信封：code=0 取 data；1001 跳登录；其它弹错
http.interceptors.response.use(
  (resp) => {
    const r = resp.data
    if (r && typeof r.code === 'number') {
      if (r.code === 0) return r.data
      if (r.code === 1001) {
        localStorage.removeItem('quju_token')
        if (!location.pathname.startsWith('/login')) location.assign('/login')
      }
      if (!resp.config.silentError) ElMessage.error(r.message || '请求失败')
      return Promise.reject(new Error(r.message || 'error'))
    }
    return r
  },
  (err) => {
    if (!err?.config?.silentError) ElMessage.error(err?.message || '网络错误')
    return Promise.reject(err)
  }
)

export default http
