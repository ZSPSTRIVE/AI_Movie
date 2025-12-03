import { get, post, del } from '@/utils/request'
import type { R, PageResult, PageQuery } from '@/types/common'

/**
 * 帖子信息
 */
export interface Post {
  id: string | number
  userId: string | number
  username?: string
  userAvatar?: string
  title: string
  contentSummary: string
  contentHtml?: string
  voteUp: number
  voteDown: number
  viewCount: number
  commentCount: number
  filmId?: number
  filmTitle?: string
  createTime: string
  voteStatus: number // 1-赞同 -1-反对 0-未投票
}

/**
 * 评论信息
 */
export interface Comment {
  id: number
  postId: number
  userId: number
  username?: string
  userAvatar?: string
  parentId?: number
  rootId?: number
  replyUserId?: number
  replyUsername?: string
  content: string
  likeCount: number
  createTime: string
  liked: boolean
  children?: Comment[]
}

/**
 * 获取帖子列表
 */
export function getPostList(params: PageQuery & { keyword?: string; filmId?: number }): Promise<R<PageResult<Post>>> {
  return get('/post/list', params)
}

/**
 * 获取帖子详情
 */
export function getPostDetail(id: string | number): Promise<R<Post>> {
  return get(`/post/detail/${id}`)
}

/**
 * 发布帖子
 */
export function createPost(data: { title: string; contentHtml: string; filmId?: number }): Promise<R<number>> {
  return post('/post/create', data)
}

/**
 * 删除帖子
 */
export function deletePost(id: number): Promise<R<void>> {
  return del(`/post/${id}`)
}

/**
 * 投票
 */
export function vote(id: string | number, type: number): Promise<R<void>> {
  return post(`/post/vote/${id}?type=${type}`)
}

/**
 * 获取评论列表
 */
export function getCommentList(postId: string | number, params: PageQuery): Promise<R<PageResult<Comment>>> {
  return get(`/comment/list/${postId}`, params)
}

/**
 * 发布评论
 */
export function createComment(data: { postId: number; content: string; parentId?: number; replyUserId?: number }): Promise<R<number>> {
  return post('/comment/create', data)
}

/**
 * 删除评论
 */
export function deleteComment(id: number): Promise<R<void>> {
  return del(`/comment/${id}`)
}

/**
 * 点赞评论
 */
export function likeComment(id: number): Promise<R<void>> {
  return post(`/comment/like/${id}`)
}

/**
 * 取消点赞
 */
export function unlikeComment(id: number): Promise<R<void>> {
  return del(`/comment/like/${id}`)
}

/**
 * 获取我的帖子
 */
export function getMyPosts(params: PageQuery): Promise<R<PageResult<Post>>> {
  return get('/post/my', params)
}

/**
 * 收藏的电影
 */
export interface FavoriteFilm {
  id: number
  title: string
  poster: string
  year: number
  rating: number
}

/**
 * 观看记录
 */
export interface WatchHistoryItem {
  id: number
  filmId: number
  title: string
  poster: string
  progress: number
  watchTime: string
}

/**
 * 获取我的收藏
 */
export function getMyFavorites(params: PageQuery): Promise<R<PageResult<FavoriteFilm>>> {
  return get('/user/favorites', params)
}

/**
 * 获取观看历史
 */
export function getWatchHistory(params: PageQuery): Promise<R<PageResult<WatchHistoryItem>>> {
  return get('/user/history', params)
}
