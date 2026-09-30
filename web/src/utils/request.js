import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

// baseURL 优先取 .env/.env.development 里的真实后端地址，未配置时用 /api（走 vite 代理）
const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api'

const request = axios.create({
  baseURL: API_BASE,
  timeout: 10000
})

// 请求拦截器
request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截器：处理后端统一返回 { code, message, data }（业务错误 code!=0 时也走 reject）
request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res && typeof res.code === 'number' && res.code !== 0) {
      if (res.code === 401) {
        ElMessage.error('登录已过期，请重新登录')
        localStorage.removeItem('token')
        localStorage.removeItem('userInfo')
        router.push('/login')
      } else {
        ElMessage.error(res.message || '请求失败')
      }
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res
  },
  (error) => {
    if (error.response) {
      const { status, data } = error.response
      if (status === 401) {
        ElMessage.error('登录已过期，请重新登录')
        localStorage.removeItem('token')
        localStorage.removeItem('userInfo')
        router.push('/login')
      } else if (status === 403) {
        ElMessage.error('没有权限执行此操作')
      } else {
        ElMessage.error(data?.message || '请求失败')
      }
    } else {
      ElMessage.error('网络连接失败，请检查网络')
    }
    return Promise.reject(error)
  }
)

export default request
