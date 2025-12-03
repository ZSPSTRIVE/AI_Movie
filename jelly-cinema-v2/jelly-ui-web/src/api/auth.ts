import { post, get } from '@/utils/request'
import type { LoginForm, RegisterForm, UserInfo } from '@/types/user'
import type { R } from '@/types/common'

/**
 * 登录
 */
export function login(data: LoginForm): Promise<R<UserInfo>> {
  return post('/auth/login', data)
}

/**
 * 注册
 */
export function register(data: RegisterForm): Promise<R<void>> {
  return post('/auth/register', data)
}

/**
 * 退出登录
 */
export function logout(): Promise<R<void>> {
  return post('/auth/logout')
}

/**
 * 获取当前用户信息
 */
export function getUserInfo(): Promise<R<UserInfo>> {
  return get('/auth/user/info')
}
