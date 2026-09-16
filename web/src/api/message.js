import request from '@/utils/request'

// 获取消息列表
export function getMessages(params) {
  return request.get('/message/list', { params })
}

// 标记已读
export function markRead(id) {
  return request.put(`/message/${id}/read`)
}

// 全部已读
export function markAllRead() {
  return request.put('/message/read-all')
}

// 获取未读数量
export function getUnreadCount() {
  return request.get('/message/unread-count')
}

// 更新订阅设置
export function updateSubscription(data) {
  return request.put('/message/subscription', data)
}
