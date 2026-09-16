import request from '@/utils/request'

// 发布失物启事
export function postLost(data) {
  return request.post('/post/lost', data)
}

// 发布招领信息
export function postFound(data) {
  return request.post('/post/found', data)
}

// 获取信息流列表
export function getPostList(params) {
  return request.get('/post/list', { params })
}

// 获取物品详情
export function getPostDetail(id) {
  return request.get(`/post/${id}`)
}

// 获取我的发布
export function getMyPosts(params) {
  return request.get('/post/mine', { params })
}

// 编辑发布
export function updatePost(id, data) {
  return request.put(`/post/${id}`, data)
}

// 下架/删除发布
export function deletePost(id) {
  return request.delete(`/post/${id}`)
}

// 标记已找回/已认领
export function markResolved(id) {
  return request.put(`/post/${id}/resolve`)
}

// 搜索
export function searchPosts(params) {
  return request.get('/post/search', { params })
}

// 获取分类列表
export function getCategories() {
  return request.get('/post/categories')
}

// 获取地点词表
export function getLocations() {
  return request.get('/post/locations')
}
