import request from '@/utils/request'

// 登录
export function login(data) {
  return request.post('/api/auth/login', data)
}

// 注册
export function register(data) {
  return request.post('/api/auth/register', data)
}

// 获取当前用户信息
export function getUserInfo() {
  return request.get('/api/users/me')
}

// 更新用户资料
export function updateUserInfo(data) {
  return request.put('/api/users/me', data)
}

// 信用分记录（后端暂无独立接口，预留）
export function getCreditRecords() {
  return request.get('/api/users/credit')
}
