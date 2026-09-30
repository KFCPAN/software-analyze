import request from '@/utils/request'

// 发布失物启事（后端 type=LOST）
export function postLost(data) {
  return request.post('/api/items', { ...data, type: 'LOST' })
}

// 发布招领信息（后端 type=FOUND）
export function postFound(data) {
  return request.post('/api/items', { ...data, type: 'FOUND' })
}

// 获取信息流列表（分页 + 多条件）
export function getPostList(params) {
  return request.get('/api/items', { params })
}

// 获取物品详情
export function getPostDetail(id) {
  return request.get(`/api/items/${id}`)
}

// 获取我的发布
export function getMyPosts(params) {
  return request.get('/api/items/mine', { params })
}

// 编辑发布
export function updatePost(id, data) {
  return request.put(`/api/items/${id}`, data)
}

// 关闭发布（已找回/已归还）——后端用 close 实现
export function closePost(id) {
  return request.post(`/api/items/${id}/close`)
}

// 兼容旧函数名：关闭发布
export function markResolved(id) {
  return request.post(`/api/items/${id}/close`)
}

// 获取分类列表
export function getCategories() {
  return request.get('/api/categories')
}

// 获取地点词表
export function getLocations() {
  return request.get('/api/locations')
}
