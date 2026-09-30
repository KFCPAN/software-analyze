import request from '@/utils/request'

// 提交认领申请（foundItemId + 隐藏特征问答）
export function submitClaim(postId, data) {
  return request.post('/api/claims', { foundItemId: postId, answers: data?.answers || [] })
}

// 认领进度：scope=mine 我的认领；scope=verify 待我核验
export function getClaimProgress(params) {
  if (params?.scope === 'verify') {
    return request.get('/api/claims/todo', { params: {} })
  }
  return request.get('/api/claims/mine', { params: {} })
}

// 核验认领（拾获者视角：action=APPROVE/REJECT）
export function verifyClaim(id, data) {
  return request.post(`/api/claims/${id}/review`, { action: data?.pass ? 'APPROVE' : 'REJECT', reason: data?.reason || '' })
}

// 认领人获取交接核销码
export function getClaimCode(id) {
  return request.get(`/api/claims/${id}/code`)
}

// 拾获者扫码核销（交接完成）
export function confirmClaim(verifyCode) {
  return request.post('/api/claims/verify', { verifyCode })
}

// 提交评价（后端暂并入核验流程，预留）
export function submitReview(id, data) {
  return request.post(`/api/claims/${id}/review`, data)
}

// 争议申诉（后端暂未独立接口，预留）
export function submitAppeal(id, data) {
  return request.post(`/api/claims/${id}/appeal`, data)
}
