import request from '@/utils/request'

// 提交认领申请
export function submitClaim(postId, data) {
  return request.post(`/claim/${postId}`, data)
}

// 获取认领进度
export function getClaimProgress(params) {
  return request.get('/claim/progress', { params })
}

// 获取认领详情
export function getClaimDetail(id) {
  return request.get(`/claim/${id}`)
}

// 拾获者核验认领
export function verifyClaim(id, data) {
  return request.put(`/claim/${id}/verify`, data)
}

// 线下核销（扫码）
export function confirmClaim(id) {
  return request.put(`/claim/${id}/confirm`)
}

// 提交评价
export function submitReview(id, data) {
  return request.post(`/claim/${id}/review`, data)
}

// 争议申诉
export function submitAppeal(id, data) {
  return request.post(`/claim/${id}/appeal`, data)
}
