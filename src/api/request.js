import axios from 'axios'
import { getToken, clearSession, sanitizeSession } from '../utils/session'

const apiBaseUrl = process.env.VUE_APP_API_BASE_URL || 'http://127.0.0.1:8080/api'

const request = axios.create({
  baseURL: apiBaseUrl,
  timeout: 15000,
  withCredentials: false
})

function isAuthRequest(url) {
  const normalizedUrl = String(url || '').replace(/^\/+/, '')
  return normalizedUrl.startsWith('auth/')
}

request.interceptors.request.use(config => {
  sanitizeSession()
  const token = getToken()

  if (!isAuthRequest(config.url) && token) {
    config.headers.Authorization = `Bearer ${token}`
  }

  return config
})

request.interceptors.response.use(
  response => {
    const payload = response.data
    if (payload && payload.success) {
      return payload.data
    }
    return Promise.reject(new Error(payload && payload.message ? payload.message : '请求失败'))
  },
  error => {
    if (error.response && error.response.status === 431) {
      clearSession()
      return Promise.reject(new Error('请求头过大，已清理旧登录信息。请刷新页面后重试，并使用 http://127.0.0.1:8081 访问前端。'))
    }

    const message = error.response && error.response.data && error.response.data.message
      ? error.response.data.message
      : error.message || '网络异常'

    if (error.response && error.response.status === 401) {
      clearSession()
    }

    return Promise.reject(new Error(message))
  }
)

export default request
