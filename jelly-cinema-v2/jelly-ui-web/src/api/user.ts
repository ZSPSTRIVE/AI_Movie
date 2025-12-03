import { get, post, put } from '@/utils/request'
import type { R } from '@/types/common'

/**
 * 用户信息
 */
export interface UserInfo {
  id: number
  username: string
  nickname?: string
  avatar?: string
  role: string
  signature?: string
  email?: string
  phone?: string
  createTime?: string
}

/**
 * 用户资料更新请求
 */
export interface ProfileUpdateDTO {
  nickname?: string
  signature?: string
  email?: string
  phone?: string
}

/**
 * 获取当前用户信息
 */
export function getUserInfo(): Promise<R<UserInfo>> {
  return get('/auth/user/info')
}

/**
 * 更新用户资料
 */
export function updateProfile(data: ProfileUpdateDTO): Promise<R<void>> {
  return put('/auth/user/profile', data)
}

/**
 * 更新头像
 */
export function updateAvatar(avatar: string): Promise<R<string>> {
  return put(`/auth/user/avatar?avatar=${encodeURIComponent(avatar)}`)
}

/**
 * 上传头像
 */
export function uploadAvatar(file: File): Promise<R<string>> {
  const formData = new FormData()
  formData.append('file', file)
  return post('/oss/upload/avatar', formData)
}
