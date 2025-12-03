/**
 * 统一响应结构
 */
export interface R<T = any> {
  code: number
  msg: string
  data: T
  timestamp: number
}

/**
 * 分页结果
 */
export interface PageResult<T = any> {
  rows: T[]
  total: number
  pageNum: number
  pageSize: number
  pages: number
}

/**
 * 分页查询参数
 */
export interface PageQuery {
  pageNum?: number
  pageSize?: number
  orderByColumn?: string
  isAsc?: 'asc' | 'desc'
}
