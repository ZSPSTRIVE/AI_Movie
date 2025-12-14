import { post, get } from '@/utils/request'
import type { R } from '@/types/common'

export interface SignStatusVO {
  signedToday: boolean
  continuousDays: number
  monthTotalDays: number
  signedDays: number[]
}

export interface CouponTemplateVO {
  id: number
  title: string
  totalCount: number
  usedCount: number
  pointsRequired: number
  startTime: string
  endTime: string
  status: number
  remainStock: number
}

export interface UserCouponVO {
  id: number
  templateId: number
  title: string
  status: number
  createTime: string
  expireTime: string
  useTime: string | null
}

export interface PointLogVO {
  id: number
  type: number
  typeName: string
  amount: number
  remark: string
  createTime: string
}

export interface PageResult<T> {
  rows: T[]
  total: number
}

/**
 * 每日签到
 */
export function checkin(): Promise<R<boolean>> {
  return post('/auth/growth/sign/checkin')
}

/**
 * 获取签到状态
 */
export function getSignStatus(): Promise<R<SignStatusVO>> {
  return get('/auth/growth/sign/status')
}

/**
 * 获取积分余额
 */
export function getPointBalance(): Promise<R<number>> {
  return get('/auth/growth/points/balance')
}

/**
 * 获取积分流水
 */
export function getPointLogs(pageNum = 1, pageSize = 20): Promise<R<PageResult<PointLogVO>>> {
  return get(`/auth/growth/points/logs?pageNum=${pageNum}&pageSize=${pageSize}`)
}

/**
 * 获取可兑换优惠券列表
 */
export function getCouponTemplates(): Promise<R<CouponTemplateVO[]>> {
  return get('/auth/growth/coupons/templates')
}

/**
 * 兑换优惠券
 */
export function exchangeCoupon(templateId: number): Promise<R<void>> {
  return post(`/auth/growth/coupons/${templateId}/exchange`)
}

/**
 * 我的优惠券
 */
export function getMyCoupons(): Promise<R<UserCouponVO[]>> {
  return get('/auth/growth/coupons/my')
}

/**
 * 使用优惠券
 */
export function useCoupon(couponId: number): Promise<R<void>> {
  return post(`/auth/growth/coupons/${couponId}/use`)
}
