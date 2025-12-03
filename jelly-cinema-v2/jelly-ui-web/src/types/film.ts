/**
 * 电影信息
 */
export interface Film {
  id: number
  title: string
  coverUrl: string
  videoUrl: string
  description: string
  categoryId: number
  categoryName?: string
  tags: string[]
  rating: number
  playCount: number
  year: number
  director: string
  actors: string
  region: string
  duration: number
  createTime: string
}

/**
 * 电影查询参数
 */
export interface FilmQuery {
  pageNum?: number
  pageSize?: number
  keyword?: string
  categoryId?: number
  year?: number
  region?: string
  sort?: 'hot' | 'new' | 'rating'
}

/**
 * 电影分类
 */
export interface Category {
  id: number
  name: string
  icon?: string
  sort: number
  parentId?: number
}
