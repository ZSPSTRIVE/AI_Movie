import { get } from '@/utils/request'

/**
 * 获取首页推荐列表（持久化数据）
 */
export function getHomepageRecommend(limit: number = 18) {
    return get('/admin/homepage/recommend', { limit })
}

/**
 * 获取首页热门列表（持久化数据）
 */
export function getHomepageHot(limit: number = 10) {
    return get('/admin/homepage/hot', { limit })
}

/**
 * 获取首页电影列表（持久化数据）
 */
export function getHomepageMovies(limit: number = 18) {
    return get('/admin/homepage/movies', { limit })
}

/**
 * 获取首页电视剧列表（持久化数据）
 */
export function getHomepageTvSeries(limit: number = 12) {
    return get('/admin/homepage/tv-series', { limit })
}

/**
 * 获取AI精选列表
 */
export function getHomepageAiBest(limit: number = 6) {
    return get('/admin/homepage/ai-best', { limit })
}

/**
 * 获取新片列表
 */
export function getHomepageNew(limit: number = 12) {
    return get('/admin/homepage/new', { limit })
}

/**
 * 获取趋势/热门话题列表
 */
export function getHomepageTrending(limit: number = 8) {
    return get('/admin/homepage/trending', { limit })
}

/**
 * 获取分板块首页内容
 */
export function getHomepageSections() {
    return get('/admin/homepage/sections')
}

