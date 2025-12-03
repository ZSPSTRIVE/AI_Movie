import { get, post, put, del } from '@/utils/request'
import type { R } from '@/types/common'

export interface PageData<T = any> {
  records: T[]
  total: number
  current?: number
  size?: number
  pages?: number
}

// ==================== 仪表盘 ====================

export interface DashboardStats {
  onlineCount: number
  todayMessageCount: number
  todayNewUsers: number
  totalUsers: number
  activeGroups: number
  totalGroups: number
  pendingReports: number
  userTrend: TrendItem[]
  messageDist: TrendItem[]
}

export interface TrendItem {
  date: string
  value: number
}

export function getDashboardStats(): Promise<R<DashboardStats>> {
  return get('/admin/dashboard/stats')
}

// ==================== 用户管理 ====================

export interface UserListItem {
  id: string
  username: string
  nickname: string
  avatar: string
  phone: string
  email: string
  status: number
  banReason: string
  banExpireTime: string
  createTime: string
  lastLoginTime: string
  lastLoginIp: string
}

export interface UserDetail extends UserListItem {
  signature: string
  friends: { id: string; nickname: string; avatar: string }[]
  groups: { id: string; name: string; avatar: string; memberCount: number }[]
  loginLogs: { ip: string; location: string; loginTime: string }[]
}

export function getUserList(params: { pageNum: number; pageSize: number; keyword?: string; status?: number }): Promise<R<PageData<UserListItem>>> {
  return get('/admin/user/manage/list', params)
}

export function getUserDetail(userId: string): Promise<R<UserDetail>> {
  return get(`/admin/user/manage/detail/${userId}`)
}

export function banUser(data: { userId: string; duration: number; reason: string }): Promise<R<void>> {
  return post('/admin/user/manage/ban', data)
}

export function unbanUser(userId: string): Promise<R<void>> {
  return post(`/admin/user/manage/unban/${userId}`)
}

export function resetUserPassword(userId: string): Promise<R<string>> {
  return post(`/admin/user/manage/reset-password/${userId}`)
}

export function forceLogout(userId: string): Promise<R<void>> {
  return post(`/admin/user/manage/force-logout/${userId}`)
}

export function createUser(data: { username: string; password: string; nickname?: string; role?: string }): Promise<R<number>> {
  return post('/admin/user/manage/create', data)
}

export function updateUser(userId: string, data: { nickname?: string; email?: string; phone?: string; role?: string }): Promise<R<void>> {
  return put(`/admin/user/manage/update/${userId}`, data)
}

export function deleteUser(userId: string): Promise<R<void>> {
  return del(`/admin/user/manage/${userId}`)
}

// ==================== 敏感词管理 ====================

export interface SensitiveWord {
  id: number
  word: string
  type: number
  strategy: number
  status: number
  createTime: string
}

export function getSensitiveWordList(params: { pageNum: number; pageSize: number; keyword?: string; type?: number }): Promise<R<PageData<SensitiveWord>>> {
  return get('/admin/sensitive/list', params)
}

export function addSensitiveWord(data: { word: string; type: number; strategy: number }): Promise<R<void>> {
  return post('/admin/sensitive/add', data)
}

export function batchImportSensitiveWords(data: { words: string; type: number; strategy: number }): Promise<R<number>> {
  return post('/admin/sensitive/import', data)
}

export function deleteSensitiveWord(id: number): Promise<R<void>> {
  return del(`/admin/sensitive/${id}`)
}

export function updateSensitiveWordStatus(data: { id: number; status: number }): Promise<R<void>> {
  return post('/admin/sensitive/status', data)
}

export function refreshSensitiveWordCache(): Promise<R<void>> {
  return post('/admin/sensitive/refresh')
}

// ==================== 举报管理 ====================

export interface ReportItem {
  id: string
  reporterId: string
  reporterNickname: string
  reporterAvatar: string
  targetId: string
  targetType: number
  targetName: string
  targetAvatar: string
  reason: string
  description: string
  evidenceImgs: string[]
  status: number
  result: string
  handlerName: string
  handleTime: string
  createTime: string
}

export function getReportList(params: { pageNum: number; pageSize: number; status?: number; targetType?: number }): Promise<R<PageData<ReportItem>>> {
  return get('/admin/report/list', params)
}

export function getReportDetail(id: string): Promise<R<ReportItem>> {
  return get(`/admin/report/${id}`)
}

export function handleReport(data: { id: string; action: number; feedback: string }): Promise<R<void>> {
  return post('/admin/report/handle', data)
}

export function getPendingReportCount(): Promise<R<number>> {
  return get('/admin/report/pending-count')
}

// ==================== 群组管理 ====================

export interface GroupListItem {
  id: string
  groupNo: string
  name: string
  avatar: string
  ownerId: string
  ownerNickname: string
  memberCount: number
  status: number
  createTime: string
}

export function getGroupList(params: { pageNum: number; pageSize: number; keyword?: string }): Promise<R<PageData<GroupListItem>>> {
  return get('/admin/audit/groups', params)
}

export function getGroupMessages(groupId: string, params: { pageNum: number; pageSize: number }): Promise<R<PageData<any>>> {
  return get(`/admin/audit/group/${groupId}/messages`, params)
}

export function dismissGroup(data: { groupId: string; reason: string }): Promise<R<void>> {
  return post('/admin/audit/group/dismiss', data)
}
