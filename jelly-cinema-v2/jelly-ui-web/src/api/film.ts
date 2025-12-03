import { get, post } from '@/utils/request'
import type { Film, FilmQuery, Category } from '@/types/film'
import type { R, PageResult } from '@/types/common'

/**
 * 分页查询电影列表
 */
export function getFilmList(params: FilmQuery): Promise<R<PageResult<Film>>> {
  return get('/film/list', params)
}

/**
 * 获取电影详情
 */
export function getFilmDetail(id: number): Promise<R<Film>> {
  return get(`/film/detail/${id}`)
}

/**
 * 搜索电影
 */
export function searchFilm(keyword: string): Promise<R<Film[]>> {
  return get('/film/search', { keyword })
}

/**
 * 获取推荐电影
 */
export function getRecommendFilm(size?: number): Promise<R<Film[]>> {
  return get('/film/recommend/feed', { size })
}

/**
 * 获取热门榜单
 */
export function getHotRank(size?: number): Promise<R<Film[]>> {
  return get('/film/recommend/hot', { size })
}

/**
 * 获取所有分类
 */
export function getCategoryList(): Promise<R<Category[]>> {
  return get('/film/category/list')
}

/**
 * 增加播放量
 */
export function incrementPlayCount(id: number): Promise<R<void>> {
  return post(`/film/play/${id}`)
}
