/**
 * 登录表单
 */
export interface LoginForm {
  username: string
  password: string
  captcha?: string
  captchaKey?: string
}

/**
 * 注册表单
 */
export interface RegisterForm {
  username: string
  password: string
  confirmPassword: string
  nickname?: string
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
