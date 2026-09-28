import request from './request'
import type { ApiResult } from '@/types'

export type BizType = 'REPORT' | 'DASHBOARD'
export type FolderSelection = 'all' | 'none' | number

export interface FolderItem {
  id: number
  name: string
  parentId: number | null
  bizType: BizType
  sortOrder?: number
  createdAt?: string
  updatedAt?: string
}

export function listFolders(bizType: BizType): Promise<ApiResult<FolderItem[]>> {
  return request.get('/folder/list', { params: { bizType } })
}

export function createFolder(data: {
  name: string
  parentId?: number | null
  bizType: BizType
}): Promise<ApiResult<FolderItem>> {
  return request.post('/folder/create', data)
}

export function updateFolder(data: {
  id: number
  name?: string
  parentId?: number | null
  bizType: BizType
}): Promise<ApiResult<FolderItem>> {
  return request.put('/folder/update', data)
}

export function deleteFolder(id: number): Promise<ApiResult<null>> {
  return request.delete(`/folder/${id}`)
}

/** 扁平文件夹列表 → el-tree / el-tree-select 需要的树结构 */
export function buildFolderTree(
  folders: FolderItem[],
): Array<{ value: number; label: string; children?: any[] }> {
  const nodes = new Map<number, any>()
  folders.forEach((f) => nodes.set(f.id, { value: f.id, label: f.name, children: [] }))
  const roots: any[] = []
  folders.forEach((f) => {
    const node = nodes.get(f.id)
    if (f.parentId && nodes.has(f.parentId)) {
      nodes.get(f.parentId).children.push(node)
    } else {
      roots.push(node)
    }
  })
  const prune = (list: any[]) => {
    list.forEach((n) => {
      if (n.children && n.children.length === 0) delete n.children
      else if (n.children) prune(n.children)
    })
  }
  prune(roots)
  return roots
}

/** 某个文件夹及其所有子孙文件夹的 ID */
export function descendantFolderIds(folders: FolderItem[], folderId: number): number[] {
  const result: number[] = []
  const walk = (id: number) => {
    if (result.includes(id)) return
    result.push(id)
    folders.filter((f) => f.parentId === id).forEach((f) => walk(f.id))
  }
  walk(folderId)
  return result
}
