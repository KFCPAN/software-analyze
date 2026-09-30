import request from '@/utils/request'

// 某条信息的智能匹配候选列表（仅发布者本人）
export function getMatches(itemId) {
  return request.get('/api/matches', { params: { itemId } })
}

// 对匹配结果反馈（是它/不是它，负反馈回流调权）
export function feedbackMatch(id, confirm) {
  return request.post(`/api/matches/${id}/feedback`, { confirm })
}
