import request from './request'

/** 通用图片上传返回体（对应后端 PicUploadResult，status=done 时 name 为图片 URL） */
export interface UploadResult {
  uid: string
  name: string
  status: string
  response?: string
}

/**
 * 上传单张图片到后台专用通道（/admin/upload/image，走管理员 Sa-Token）。
 * @param file 图片文件
 * @returns 上传结果，取 name 作为图片 URL
 */
export function uploadImageApi(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<unknown, UploadResult>('/admin/upload/image', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
