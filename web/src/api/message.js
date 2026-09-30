import request from '@/utils/request'

// 获取消息列表（分页，MessagePageVO 含 unreadCount）
export function getMessages(params) {
  return request.get('/api/messages', { params })
}

// 标记已读
export function markRead(id) {
  return request.post(`/api/messages/${id}/read`)
}

// 未读数量：后端无独立接口，从列表响应的 unreadCount 拿
export function getUnreadCount() {
  return request.get('/api/messages', { params: { page: 1, size: 1 } })
}

// 全部已读：后端无批量接口，页面层循环 markRead（预留函数名）
export function markAllRead() {
  return request.post('/api/messages/read-all')
}
