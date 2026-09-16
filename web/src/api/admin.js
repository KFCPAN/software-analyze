import request from '@/utils/request'

// 数据看板统计
export function getDashboardStats() {
  return request.get('/admin/dashboard/stats')
}

// 内容审核列表
export function getAuditList(params) {
  return request.get('/admin/audit/list', { params })
}

// 审核通过
export function auditApprove(id) {
  return request.put(`/admin/audit/${id}/approve`)
}

// 审核驳回
export function auditReject(id, data) {
  return request.put(`/admin/audit/${id}/reject`, data)
}

// 用户列表
export function getUserList(params) {
  return request.get('/admin/users', { params })
}

// 封禁用户
export function banUser(id, data) {
  return request.put(`/admin/users/${id}/ban`, data)
}

// 解封用户
export function unbanUser(id) {
  return request.put(`/admin/users/${id}/unban`)
}

// 获取匹配参数配置
export function getMatchConfig() {
  return request.get('/admin/config/match')
}

// 更新匹配参数配置
export function updateMatchConfig(data) {
  return request.put('/admin/config/match', data)
}

// 分类管理
export function manageCategory(data) {
  return request.post('/admin/config/category', data)
}

// 地点词表管理
export function manageLocation(data) {
  return request.post('/admin/config/location', data)
}
