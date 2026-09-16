import request from '@/utils/request'

// 登录
export function login(data) {
  return request.post('/user/login', data)
}

// 注册
export function register(data) {
  return request.post('/user/register', data)
}

// 获取用户信息
export function getUserInfo() {
  return request.get('/user/info')
}

// 更新用户资料
export function updateUserInfo(data) {
  return request.put('/user/info', data)
}

// 获取信用分记录
export function getCreditRecords() {
  return request.get('/user/credit')
}
