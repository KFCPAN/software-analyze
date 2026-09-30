import request from '@/utils/request'

// 图片上传（multipart，返回 { path: '/files/yyyy-MM/xxx.jpg' }）
export function uploadFile(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/api/files', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
