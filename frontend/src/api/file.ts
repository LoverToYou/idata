import request from './request'
import type { ApiResult, FileManage, PageResult } from '@/types'

export function uploadFile(file: File, description?: string): Promise<ApiResult<FileManage>> {
  const formData = new FormData()
  formData.append('file', file)
  if (description) formData.append('description', description)
  return request.post('/files/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function listFilesPage(
  keyword?: string,
  fileExt?: string,
  page: number = 1,
  pageSize: number = 10
): Promise<ApiResult<PageResult<FileManage>>> {
  const params: Record<string, any> = { page, pageSize }
  if (keyword) params.keyword = keyword
  if (fileExt) params.fileExt = fileExt
  return request.get('/files/page', { params })
}

export function getFile(id: number): Promise<ApiResult<FileManage>> {
  return request.get(`/files/${id}`)
}

export function deleteFile(id: number): Promise<ApiResult<null>> {
  return request.delete(`/files/${id}`)
}

/** 下载文件，返回二进制 Blob */
export async function downloadFile(id: number): Promise<Blob> {
  const res = await request.get(`/files/${id}/download`, { responseType: 'blob' })
  return res.data
}

/** 文件管理中所有 jar 文件，供 UDF 选择 */
export function listJarFiles(keyword?: string): Promise<ApiResult<FileManage[]>> {
  const params: Record<string, any> = {}
  if (keyword) params.keyword = keyword
  return request.get('/files/jars', { params })
}

/** 列出 HDFS 文件管理存储目录下实际存在的 jar（含非经文件管理上传的），供 UDF 表单选择 */
export function listHdfsJars(): Promise<ApiResult<FileManage[]>> {
  return request.get('/files/hdfs-jars')
}
