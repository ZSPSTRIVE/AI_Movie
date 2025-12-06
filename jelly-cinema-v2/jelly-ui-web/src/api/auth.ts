import { post, get } from '@/utils/request'
import type { LoginForm, RegisterForm, UserInfo, CaptchaResult, EmailCodeForm } from '@/types/user'
import type { R } from '@/types/common'

/**
 * 获取图片验证码
 */
export function getCaptcha(): Promise<R<CaptchaResult>> {
  return get('/auth/captcha')
}

/**
 * 发送邮箱验证码
 */
export function sendEmailCode(data: EmailCodeForm): Promise<R<void>> {
  return post('/auth/email/code', data)
}

/**
 * 检查是否需要邮箱验证
 */
export function checkEmailVerify(username: string): Promise<R<{ needEmailVerify: boolean; maskedEmail?: string }>> {
  return get(`/auth/check/email-verify?username=${encodeURIComponent(username)}`)
}

/**
 * 发送登录验证邮箱验证码
 */
export function sendLoginEmailCode(username: string, captcha: string, captchaKey: string): Promise<R<void>> {
  return post(`/auth/login/email-code?username=${encodeURIComponent(username)}&captcha=${encodeURIComponent(captcha)}&captchaKey=${encodeURIComponent(captchaKey)}`)
}

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
