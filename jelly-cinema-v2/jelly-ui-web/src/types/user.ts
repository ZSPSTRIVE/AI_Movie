/**
 * 登录表单
 */
export interface LoginForm {
  username: string
  password: string
  captcha: string
  captchaKey: string
  emailCode?: string
  email?: string
}

/**
 * 注册表单
 */
export interface RegisterForm {
  username: string
  email: string
  emailCode: string
  password: string
  confirmPassword: string
  nickname?: string
}

/**
 * 图片验证码响应
 */
export interface CaptchaResult {
  captchaKey: string
  captchaImage: string
  expireSeconds: number
}

/**
 * 邮箱验证码请求
 */
export interface EmailCodeForm {
  email: string
  businessType: 'register' | 'login' | 'reset_password' | 'bind_email'
  captcha?: string
  captchaKey?: string
}

/**
 * 用户信息
 */
export interface UserInfo {
  userId: number | string  // 支持字符串类型，避免大数字精度丢失
  username: string
  nickname: string
  avatar?: string
  role: string
  token?: string
  expireIn?: number
  signature?: string
  email?: string
  phone?: string
  createTime?: string
}
