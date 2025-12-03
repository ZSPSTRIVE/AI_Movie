import { get, post, put } from '@/utils/request'
import type { R, PageResult, PageQuery } from '@/types/common'

// ==================== 搜索相关 ====================

/**
 * 用户搜索结果
 */
export interface UserSearchResult {
  id: string
  username: string
  nickname: string
  avatar: string
  signature: string
  isFriend: boolean
}

/**
 * 群组搜索结果
 */
export interface GroupSearchResult {
  id: string
  groupNo: string
  name: string
  avatar: string
  description: string
  memberCount: number
  maxMember: number
  joinType: number
  isJoined: boolean
}

/**
 * 搜索用户
 */
export function searchUser(keyword: string): Promise<R<UserSearchResult[]>> {
  return get('/im/search/user', { keyword })
}

/**
 * 搜索群组
 */
export function searchGroup(keyword: string): Promise<R<GroupSearchResult[]>> {
  return get('/im/search/group', { keyword })
}

// ==================== 好友相关 ====================

/**
 * 好友信息
 */
export interface Friend {
  id: string | number // 后端返回字符串，避免JS大整数精度丢失
  nickname: string
  avatar: string
  remark?: string
  username?: string
  signature?: string
  status?: number // 0-正常 1-已拉黑
  online?: boolean
  createTime?: string
}

/**
 * 获取当前用户好友列表
 */
export function getFriends(): Promise<R<Friend[]>> {
  return get('/im/friend/list')
}

/**
 * 获取好友列表（别名）
 */
export function getFriendList(): Promise<R<Friend[]>> {
  return get('/im/friend/list')
}

/**
 * 删除好友
 */
export function deleteFriend(friendId: string, keepMessages: boolean = false): Promise<R<void>> {
  return post(`/im/friend/delete/${friendId}?keepMessages=${keepMessages}`)
}

/**
 * 设置好友备注
 */
export function setFriendRemark(data: { friendId: string; remark: string }): Promise<R<void>> {
  return post('/im/friend/remark', data)
}

/**
 * 拉黑好友
 */
export function blockFriend(friendId: string): Promise<R<void>> {
  return post(`/im/friend/block/${friendId}`)
}

/**
 * 解除拉黑
 */
export function unblockFriend(friendId: string): Promise<R<void>> {
  return post(`/im/friend/unblock/${friendId}`)
}

/**
 * 获取黑名单列表
 */
export function getBlacklist(): Promise<R<Friend[]>> {
  return get('/im/friend/blacklist')
}

// ==================== 申请相关 ====================

/**
 * 申请记录
 */
export interface ApplyRecord {
  id: string
  type: number // 1-好友申请 2-入群申请
  fromId: string
  fromNickname: string
  fromAvatar: string
  targetId: string
  targetName: string
  reason: string
  status: number // 0-待处理 1-已同意 2-已拒绝 3-已忽略
  createTime: string
}

/**
 * 发起好友申请
 */
export function applyFriend(data: { targetId: string; reason?: string; remark?: string }): Promise<R<void>> {
  return post('/im/apply/friend', data)
}

/**
 * 发起入群申请
 */
export function applyGroup(data: { groupId: string; reason?: string }): Promise<R<void>> {
  return post('/im/apply/group', data)
}

/**
 * 获取申请列表
 */
export function getApplyList(type?: number): Promise<R<ApplyRecord[]>> {
  return get('/im/apply/list', { type })
}

/**
 * 处理申请
 */
export function handleApply(data: { applyId: string; status: number; groupName?: string; remark?: string }): Promise<R<void>> {
  return post('/im/apply/handle', data)
}

/**
 * 获取未读申请数量
 */
export function getUnreadApplyCount(): Promise<R<number>> {
  return get('/im/apply/unread-count')
}

// ==================== 群组相关 ====================

/**
 * 群成员信息
 */
export interface GroupMember {
  userId: string
  username: string
  nickname: string
  avatar: string
  groupNick: string
  role: number // 0-成员 1-管理员 2-群主
  muteEndTime: string
  joinTime: string
  isMuted: boolean
}

/**
 * 群组详情
 */
export interface GroupDetail {
  id: string
  groupNo: string
  name: string
  avatar: string
  description: string
  notice: string
  ownerId: string
  ownerNickname: string
  memberCount: number
  maxMember: number
  joinType: number
  isMuteAll: number
  myRole: number
  myGroupNick: string
  createTime: string
  members: GroupMember[]
}

/**
 * 创建群聊
 */
export function createGroup(data: { name: string; memberIds?: string[] }): Promise<R<string>> {
  return post('/im/group/create', data)
}

/**
 * 获取群详情
 */
export function getGroupDetail(groupId: string): Promise<R<GroupDetail>> {
  return get(`/im/group/detail/${groupId}`)
}

/**
 * 获取群成员列表
 */
export function getGroupMembers(groupId: string): Promise<R<GroupMember[]>> {
  return get(`/im/group/members/${groupId}`)
}

/**
 * 更新群资料
 */
export function updateGroupInfo(groupId: string, data: { name?: string; description?: string; notice?: string }): Promise<R<void>> {
  return put(`/im/group/info/${groupId}`, data)
}

/**
 * 设置/取消管理员
 */
export function setGroupAdmin(data: { groupId: string; userId: string; type: number }): Promise<R<void>> {
  return post('/im/group/admin/set', data)
}

/**
 * 踢出成员
 */
export function kickGroupMembers(data: { groupId: string; memberIds: string[] }): Promise<R<void>> {
  return post('/im/group/member/kick', data)
}

/**
 * 禁言成员
 */
export function muteGroupMember(data: { groupId: string; memberId: string; duration: number }): Promise<R<void>> {
  return post('/im/group/member/mute', data)
}

/**
 * 全员禁言
 */
export function muteAllGroupMembers(groupId: string, mute: boolean): Promise<R<void>> {
  return post(`/im/group/mute-all/${groupId}?mute=${mute}`, {})
}

/**
 * 修改我的群名片
 */
export function updateMyGroupNick(data: { groupId: string; nickname: string }): Promise<R<void>> {
  return post('/im/group/member/nick', data)
}

/**
 * 转让群主
 */
export function transferGroupOwner(data: { groupId: string; newOwnerId: string }): Promise<R<void>> {
  return post('/im/group/transfer', data)
}

/**
 * 退出群聊
 */
export function quitGroup(groupId: string): Promise<R<void>> {
  return post(`/im/group/quit/${groupId}`)
}

/**
 * 解散群聊
 */
export function dissolveGroup(groupId: string): Promise<R<void>> {
  return post(`/im/group/dissolve/${groupId}`)
}

/**
 * 邀请好友入群
 */
export function inviteGroupMembers(data: { groupId: string; userIds: string[] }): Promise<R<void>> {
  return post('/im/group/invite', data)
}

export function uploadChatImage(file: File): Promise<R<string>> {
  const formData = new FormData()
  formData.append('file', file)
  return post('/oss/upload/image', formData)
}

export function uploadChatFile(file: File): Promise<R<string>> {
  const formData = new FormData()
  formData.append('file', file)
  return post('/oss/upload?folder=im-file', formData)
}

// ==================== 举报相关 ====================

/**
 * 举报请求
 */
export interface ReportDTO {
  targetId: string
  targetType: number // 1-用户 2-群组 3-消息 4-帖子
  reason: string
  evidenceImgs?: string[]
}

/**
 * 提交举报
 */
export function submitReport(data: ReportDTO): Promise<R<void>> {
  return post('/im/report', data)
}

// ==================== 会话相关 ====================

/**
 * 会话信息
 */
export interface Session {
  sessionId: string
  type: number // 1-单聊 2-群聊
  userId?: string | number // 单聊时（后端返回字符串，避免JS大整数精度丢失）
  groupId?: string | number // 群聊时（后端返回字符串，避免JS大整数精度丢失）
  nickname: string
  avatar?: string
  lastMessage: string
  lastTime: string
  unreadCount: number
}

/**
 * 消息信息
 */
export interface Message {
  id: string | number
  sessionId: string
  fromId: string | number
  fromNickname?: string
  fromAvatar?: string
  toId: string | number
  cmdType: number
  msgType: number // 1-文本 2-图片 3-文件 4-语音
  content: string
  extra?: string
  msgSeq: string | number
  createTime: string
  status: number
}

/**
 * 获取会话列表
 */
export function getSessions(): Promise<R<Session[]>> {
  return get('/im/sessions')
}

/**
 * 获取历史消息
 */
export function getHistory(sessionId: string, params: PageQuery): Promise<R<PageResult<Message>>> {
  return get(`/im/history/${sessionId}`, params)
}

/**
 * 撤回消息
 */
export function recallMessage(messageId: number): Promise<R<void>> {
  return post(`/im/recall/${messageId}`)
}

/**
 * 删除会话
 */
export function deleteSession(sessionId: string, keepMessages: boolean = false): Promise<R<void>> {
  return post(`/im/session/delete/${sessionId}?keepMessages=${keepMessages}`)
}

/**
 * 删除单条消息
 */
export function deleteMessage(messageId: string): Promise<R<void>> {
  return post(`/im/message/delete/${messageId}`)
}

/**
 * 清空会话消息
 */
export function clearMessages(sessionId: string): Promise<R<void>> {
  return post(`/im/session/clear/${sessionId}`)
}

/**
 * 标记消息已读
 */
export function markAsRead(sessionId: string): Promise<R<void>> {
  return post(`/im/session/read/${sessionId}`)
}

/**
 * WebSocket 连接管理
 */
export class IMWebSocket {
  private ws: WebSocket | null = null
  private url: string
  private reconnectAttempts = 0
  private maxReconnectAttempts = 5
  private listeners: Map<string, Function[]> = new Map()

  constructor(token: string) {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    // 开发环境前端运行在 3000 端口，后端网关在 8080，需要显式指定网关端口
    const host = import.meta.env.DEV ? 'localhost:8080' : window.location.host
    this.url = `${protocol}//${host}/ws/chat?token=${token}`
  }

  connect() {
    this.ws = new WebSocket(this.url)

    this.ws.onopen = () => {
      console.log('WebSocket 连接成功')
      this.reconnectAttempts = 0
      this.emit('connected')
    }

    this.ws.onmessage = (event) => {
      try {
        console.log('WebSocket 收到原始消息:', event.data)
        const data = JSON.parse(event.data)
        console.log('WebSocket 解析后消息:', data)
        this.emit(data.type, data)
      } catch (e) {
        console.error('消息解析失败', e)
      }
    }

    this.ws.onclose = () => {
      console.log('WebSocket 连接断开')
      this.emit('disconnected')
      this.tryReconnect()
    }

    this.ws.onerror = (error) => {
      console.error('WebSocket 错误', error)
      this.emit('error', error)
    }
  }

  private tryReconnect() {
    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      this.reconnectAttempts++
      console.log(`尝试重连 (${this.reconnectAttempts}/${this.maxReconnectAttempts})`)
      setTimeout(() => this.connect(), 3000 * this.reconnectAttempts)
    }
  }

  send(data: { cmdType?: number; toId: number | string; msgType?: number; content: string; extra?: string }) {
    if (this.ws?.readyState === WebSocket.OPEN) {
      const payload = {
        cmdType: data.cmdType || 1,
        toId: String(data.toId),  // 转为字符串避免大数字精度丢失
        msgType: data.msgType || 1,
        content: data.content,
        extra: data.extra
      }
      console.log('WebSocket 发送消息:', payload)
      this.ws.send(JSON.stringify(payload))
    } else {
      console.error('WebSocket 未连接')
    }
  }

  on(event: string, callback: Function) {
    if (!this.listeners.has(event)) {
      this.listeners.set(event, [])
    }
    this.listeners.get(event)!.push(callback)
  }

  off(event: string, callback?: Function) {
    if (callback) {
      const callbacks = this.listeners.get(event)
      if (callbacks) {
        const index = callbacks.indexOf(callback)
        if (index > -1) callbacks.splice(index, 1)
      }
    } else {
      this.listeners.delete(event)
    }
  }

  private emit(event: string, data?: any) {
    const callbacks = this.listeners.get(event)
    if (callbacks) {
      callbacks.forEach(cb => cb(data))
    }
  }

  disconnect() {
    if (this.ws) {
      this.ws.close()
      this.ws = null
    }
  }
}

// ==================== 用户设置相关 ====================

/**
 * 用户设置类型
 */
export interface UserSetting {
  id: string
  userId: string
  enableNotification: number // 0-关闭, 1-开启
  enableSound: number
  showOnlineStatus: number
  allowStrangerMsg: number
  enterToSend: number
  showReadStatus: number
}

/**
 * 获取用户设置
 */
export function getUserSetting(): Promise<R<UserSetting>> {
  return get('/im/setting')
}

/**
 * 更新用户设置
 */
export function updateUserSetting(data: {
  enableNotification?: boolean
  enableSound?: boolean
  showOnlineStatus?: boolean
  allowStrangerMsg?: boolean
  enterToSend?: boolean
  showReadStatus?: boolean
}): Promise<R<void>> {
  return put('/im/setting', data)
}

// ==================== 在线状态相关 ====================

/**
 * 查询单个用户在线状态
 */
export function checkOnline(userId: string | number): Promise<R<boolean>> {
  return get(`/im/online/${userId}`)
}

/**
 * 批量查询用户在线状态
 */
export function checkOnlineBatch(userIds: (string | number)[]): Promise<R<Record<string, boolean>>> {
  return post('/im/online/batch', userIds)
}
