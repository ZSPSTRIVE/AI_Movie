<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick, computed } from 'vue'
import { 
  getSessions, getHistory, getUnreadApplyCount, recallMessage, createGroup, 
  uploadChatImage, uploadChatFile, deleteSession, clearMessages, markAsRead,
  deleteFriend, blockFriend, checkOnlineBatch,
  type Session, type Message, type Friend, IMWebSocket 
} from '@/api/im'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import AddContactModal from './components/AddContactModal.vue'
import NotificationCenter from './components/NotificationCenter.vue'
import GroupDrawer from './components/GroupDrawer.vue'
import CreateGroupModal from './components/CreateGroupModal.vue'
import FriendManager from './components/FriendManager.vue'
import FriendDrawer from './components/FriendDrawer.vue'
import SettingsDrawer from './components/SettingsDrawer.vue'

const userStore = useUserStore()

// 弹窗状态
const addContactVisible = ref(false)
const notificationVisible = ref(false)
const groupDrawerVisible = ref(false)
const friendDrawerVisible = ref(false)
const createGroupVisible = ref(false)
const friendManagerVisible = ref(false)
const settingsVisible = ref(false)
const showEmojiPicker = ref(false)
const unreadApplyCount = ref(0)
const searchKeyword = ref('')

// 当前好友信息（用于好友抽屉）
const currentFriend = ref<Friend | null>(null)

// 消息搜索
const searchVisible = ref(false)
const messageSearchKeyword = ref('')
const searchResults = ref<Message[]>([])

// 群公告弹窗
const showGroupNotice = ref(false)
const groupNoticeContent = ref('')
const noticeReadKey = ref('') // 用于标记已读

// 会话右键菜单
const contextMenuVisible = ref(false)
const contextMenuPosition = ref({ x: 0, y: 0 })
const contextMenuSession = ref<Session | null>(null)

// 消息右键菜单
const msgContextMenuVisible = ref(false)
const msgContextMenuPosition = ref({ x: 0, y: 0 })
const contextMenuMessage = ref<Message | null>(null)

// 表情列表
const emojis = ['😀', '😂', '🤣', '😍', '🥰', '😘', '😎', '🤔', '😅', '😭', '😱', '🥺', '👍', '👎', '❤️', '🔥', '🎉', '👏', '🙏', '💪']

// 会话和消息
const sessions = ref<Session[]>([])
const activeSession = ref<Session | null>(null)
const messages = ref<Message[]>([])
const messageInput = ref('')
const loading = ref(false)
const messagesRef = ref<HTMLElement>()

// 上传相关
const imageInputRef = ref<HTMLInputElement | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)
const uploading = ref(false)

// 用户设置
const chatSettings = ref({
  enableNotification: true,
  enableSound: true,
  enterToSend: true,
  showOnlineStatus: true,
  showReadStatus: true
})

// 在线状态
const onlineStatus = ref<Record<string, boolean>>({})

// 消息提示音 (使用 Web Audio API)
let audioContext: AudioContext | null = null

// WebSocket
let ws: IMWebSocket | null = null

onMounted(async () => {
  if (!userStore.isLogin) {
    ElMessage.warning('请先登录')
    return
  }
  
  // 加载用户设置
  loadChatSettings()
  
  // 初始化 Web Audio Context (用于生成提示音)
  try {
    audioContext = new (window.AudioContext || (window as any).webkitAudioContext)()
  } catch (e) {
    console.warn('Web Audio API 不支持')
  }
  
  // 加载会话列表和未读申请数
  await Promise.all([loadSessions(), loadUnreadCount()])
  
  // 初始化 WebSocket
  ws = new IMWebSocket(userStore.token!)
  ws.on('connected', () => console.log('IM 已连接'))
  ws.on('message', handleNewMessage)
  ws.on('recall', handleRecall)
  ws.on('apply', () => loadUnreadCount()) // 收到新申请
  ws.on('read', handleReadStatus) // 消息已读通知
  ws.on('online', handleOnlineStatus) // 用户在线状态变化
  ws.connect()
})

// 加载聊天设置
function loadChatSettings() {
  const saved = localStorage.getItem('chat_settings')
  if (saved) {
    try {
      Object.assign(chatSettings.value, JSON.parse(saved))
    } catch {
      // 忽略
    }
  }
}

// 播放消息提示音 (使用 Web Audio API 生成)
function playNotificationSound() {
  if (!chatSettings.value.enableSound || !audioContext) return
  
  try {
    // 如果 AudioContext 被暂停，先恢复
    if (audioContext.state === 'suspended') {
      audioContext.resume()
    }
    
    const oscillator = audioContext.createOscillator()
    const gainNode = audioContext.createGain()
    
    oscillator.connect(gainNode)
    gainNode.connect(audioContext.destination)
    
    // 设置音调 (C5 = 523Hz, E5 = 659Hz)
    oscillator.frequency.setValueAtTime(659, audioContext.currentTime)
    oscillator.frequency.setValueAtTime(784, audioContext.currentTime + 0.1)
    
    // 设置音量和淡出
    gainNode.gain.setValueAtTime(0.3, audioContext.currentTime)
    gainNode.gain.exponentialRampToValueAtTime(0.01, audioContext.currentTime + 0.3)
    
    oscillator.start(audioContext.currentTime)
    oscillator.stop(audioContext.currentTime + 0.3)
  } catch (e) {
    console.warn('播放提示音失败:', e)
  }
}

// 发送桌面通知
function sendDesktopNotification(title: string, body: string) {
  if (!chatSettings.value.enableNotification) return
  
  if (Notification.permission === 'granted') {
    new Notification(title, { body, icon: '/favicon.ico' })
  } else if (Notification.permission !== 'denied') {
    Notification.requestPermission().then(permission => {
      if (permission === 'granted') {
        new Notification(title, { body, icon: '/favicon.ico' })
      }
    })
  }
}

// 处理 Enter 键
function handleInputKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter') {
    if (chatSettings.value.enterToSend && !e.shiftKey) {
      e.preventDefault()
      sendMessage()
    }
    // 如果不是 enterToSend 模式，或者按了 Shift+Enter，则正常换行
  }
}

function chooseImage() {
  imageInputRef.value?.click()
}

function chooseFile() {
  fileInputRef.value?.click()
}

async function handleImageChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file || !activeSession.value) return

  try {
    uploading.value = true
    const res = await uploadChatImage(file)
    const url = res.data
    if (!url) return

    const { cmdType, toId } = getChatTarget()

    ws?.send({
      cmdType,
      toId,
      msgType: 2,
      content: url,
      extra: file.name,
    })

    messages.value.push({
      id: Date.now(),
      sessionId: activeSession.value.sessionId,
      fromId: userStore.userInfo!.userId,
      toId,
      cmdType,
      msgType: 2,
      content: url,
      extra: file.name,
      msgSeq: Date.now(),
      createTime: new Date().toISOString(),
      status: 0,
      readStatus: 0,
    })
    nextTick(() => scrollToBottom())
  } catch (error: any) {
    ElMessage.error(error.message || '发送图片失败')
  } finally {
    uploading.value = false
    if (imageInputRef.value) {
      imageInputRef.value.value = ''
    }
  }
}

async function handleFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file || !activeSession.value) return

  try {
    uploading.value = true
    const res = await uploadChatFile(file)
    const url = res.data
    if (!url) return

    const { cmdType, toId } = getChatTarget()

    ws?.send({
      cmdType,
      toId,
      msgType: 3,
      content: url,
      extra: file.name,
    })

    messages.value.push({
      id: Date.now(),
      sessionId: activeSession.value.sessionId,
      fromId: userStore.userInfo!.userId,
      toId,
      cmdType,
      msgType: 3,
      content: url,
      extra: file.name,
      msgSeq: Date.now(),
      createTime: new Date().toISOString(),
      status: 0,
      readStatus: 0,
    })
    nextTick(() => scrollToBottom())
  } catch (error: any) {
    ElMessage.error(error.message || '发送文件失败')
  } finally {
    uploading.value = false
    if (fileInputRef.value) {
      fileInputRef.value.value = ''
    }
  }
}

function getFileName(url: string): string {
  try {
    const u = new URL(url)
    const parts = u.pathname.split('/')
    return parts[parts.length - 1] || url
  } catch {
    const parts = url.split('/')
    return parts[parts.length - 1] || url
  }
}

async function loadUnreadCount() {
  try {
    const res = await getUnreadApplyCount()
    unreadApplyCount.value = res.data || 0
  } catch (e) {
    // ignore
  }
}

onUnmounted(() => {
  ws?.disconnect()
})

async function loadSessions() {
  try {
    const res = await getSessions()
    sessions.value = res.data || []
    // 调试：打印群聊会话的 groupId
    console.log('会话列表:', sessions.value.map(s => ({ 
      sessionId: s.sessionId, 
      type: s.type, 
      groupId: s.groupId, 
      userId: s.userId,
      nickname: s.nickname 
    })))
    
    // 加载在线状态（仅私聊）
    await loadOnlineStatus()
  } catch (e) {
    console.error('加载会话失败', e)
  }
}

// 加载在线状态
async function loadOnlineStatus() {
  if (!chatSettings.value.showOnlineStatus) return
  
  // 获取所有私聊用户ID
  const userIds = sessions.value
    .filter(s => s.type === 1 && s.userId)
    .map(s => s.userId!)
  
  if (userIds.length === 0) return
  
  try {
    const res = await checkOnlineBatch(userIds)
    if (res.data) {
      onlineStatus.value = res.data
    }
  } catch (e) {
    console.warn('加载在线状态失败', e)
  }
}

async function selectSession(session: Session) {
  console.log('选择会话:', {
    sessionId: session.sessionId,
    type: session.type,
    userId: session.userId,
    groupId: session.groupId,
    nickname: session.nickname,
  })
  
  // 如果 sessionId 为空，尝试生成
  if (!session.sessionId) {
    if (session.type === 1 && session.userId) {
      // 私聊：生成 sessionId
      const generatedId = generatePrivateSessionId(session.userId)
      if (!generatedId) {
        console.error('无法生成私聊 sessionId，用户信息不可用')
        ElMessage.error('用户信息加载中，请稍后再试')
        return
      }
      session.sessionId = generatedId
      console.log('生成私聊 sessionId:', session.sessionId)
    } else if (session.type === 2 && session.groupId) {
      // 群聊：生成 sessionId
      session.sessionId = `group_${session.groupId}`
      console.log('生成群聊 sessionId:', session.sessionId)
    } else {
      console.error('无法生成 sessionId，缺少必要信息:', session)
      return
    }
  }
  
  // 最终检查 - 确保 sessionId 有效
  if (!session.sessionId) {
    console.error('sessionId 无效，无法加载历史消息')
    return
  }
  
  activeSession.value = session
  loading.value = true
  
  // 切换会话时清空群公告内容（避免显示上一个群的公告）
  if (session.type !== 2) {
    groupNoticeContent.value = ''
  }
  
  try {
    const res = await getHistory(session.sessionId, { pageNum: 1, pageSize: 50 })
    console.log('加载历史消息成功:', {
      sessionId: session.sessionId,
      total: res.data?.total,
      pageNum: 1,
      pageSize: 50,
    })
    messages.value = (res.data?.rows || []).reverse()
    await nextTick()
    scrollToBottom()
    
    // 清除未读并标记已读（调用后端 API）
    // 私聊时无条件调用，确保对方收到已读通知
    if (session.type === 1) {
      markAsRead(session.sessionId).catch(e => console.warn('标记已读失败', e))
    }
    session.unreadCount = 0
    
    // 群聊：检查是否需要显示群公告
    if (session.type === 2) {
      checkGroupNotice(session)
    }
  } catch (error) {
    console.error('加载历史消息失败:', session.sessionId, error)
  } finally {
    loading.value = false
  }
}

// 检查群公告是否需要显示
async function checkGroupNotice(session: Session) {
  try {
    const groupId = String(session.groupId || session.userId)
    const { getGroupDetail } = await import('@/api/im')
    const res = await getGroupDetail(groupId)
    const group = res.data
    
    if (group?.notice) {
      // 检查是否已读（使用 sessionId + notice 内容的 hash 作为 key）
      const readKey = `notice_read_${session.sessionId}_${hashCode(group.notice)}`
      const isRead = localStorage.getItem(readKey) === 'true'
      
      if (!isRead) {
        groupNoticeContent.value = group.notice
        noticeReadKey.value = readKey
        showGroupNotice.value = true
      }
    }
  } catch (e) {
    // 忽略错误
  }
}

// 标记公告已读
function markNoticeRead() {
  if (noticeReadKey.value) {
    localStorage.setItem(noticeReadKey.value, 'true')
  }
  showGroupNotice.value = false
}

// 简单的字符串 hash 函数
function hashCode(str: string): number {
  let hash = 0
  for (let i = 0; i < str.length; i++) {
    hash = ((hash << 5) - hash) + str.charCodeAt(i)
    hash |= 0 // 转为32位整数
  }
  return hash
}

function handleNewMessage(data: any) {
  console.log('收到新消息:', data)
  const msg = data.data as Message
  console.log('解析后的消息:', msg)
  console.log('当前会话 sessionId:', activeSession.value?.sessionId)
  console.log('消息 sessionId:', msg.sessionId)
  
  // 如果是当前会话的消息
  if (activeSession.value?.sessionId === msg.sessionId) {
    console.log('消息属于当前会话，添加到列表')
    messages.value.push(msg)
    nextTick(() => scrollToBottom())
    
    // 当前正在查看该私聊会话：收到对方消息后立即标记已读
    if (activeSession.value.type === 1 && !isMyMessage(msg)) {
      markAsRead(msg.sessionId).catch(e => console.warn('实时标记已读失败', e))
    }
  } else {
    console.log('消息不属于当前会话')
    // 不是当前会话的消息，播放提示音和发送桌面通知
    playNotificationSound()
    sendDesktopNotification(
      msg.fromNickname || '新消息',
      msg.msgType === 1 ? msg.content : (msg.msgType === 2 ? '[图片]' : '[文件]')
    )
  }
  
  // 更新会话列表
  let session = sessions.value.find(s => s.sessionId === msg.sessionId)
  if (session) {
    session.lastMessage = msg.content
    session.lastTime = msg.createTime
    if (activeSession.value?.sessionId !== msg.sessionId) {
      session.unreadCount = (session.unreadCount || 0) + 1
    }
    // 将该会话移到顶部
    const index = sessions.value.indexOf(session)
    if (index > 0) {
      sessions.value.splice(index, 1)
      sessions.value.unshift(session)
    }
  } else {
    // 新会话：创建一个临时会话项（之后会被 loadSessions 覆盖）
    const newSession: Session = {
      sessionId: msg.sessionId,
      type: msg.sessionId.startsWith('group_') ? 2 : 1,
      userId: msg.fromId,
      nickname: msg.fromNickname || '新消息',
      avatar: msg.fromAvatar,
      lastMessage: msg.content,
      lastTime: msg.createTime,
      unreadCount: 1
    }
    sessions.value.unshift(newSession)
    // 异步刷新会话列表获取完整信息
    loadSessions()
  }
}

function handleRecall(data: any) {
  const index = messages.value.findIndex(m => m.id === data.messageId)
  if (index > -1) {
    messages.value[index].status = 1
    messages.value[index].content = '[消息已撤回]'
  }
}

// 处理消息已读通知（发送方收到）
function handleReadStatus(data: any) {
  console.log('收到已读通知:', data)
  const { sessionId } = data
  
  // 如果是当前会话，更新所有消息的已读状态
  if (activeSession.value?.sessionId === sessionId) {
    messages.value.forEach(msg => {
      // 只更新自己发送的消息
      if (isMyMessage(msg)) {
        msg.readStatus = 1
      }
    })
  }
}

// 处理用户在线状态变化
function handleOnlineStatus(data: any) {
  console.log('收到在线状态变化:', data)
  const { userId, online } = data
  if (userId) {
    onlineStatus.value[String(userId)] = online
  }
}

// 撤回消息
async function handleRecallMessage(msg: Message) {
  // 只能撤回2分钟内的消息
  const diff = Date.now() - new Date(msg.createTime).getTime()
  if (diff > 2 * 60 * 1000) {
    ElMessage.warning('只能撤回2分钟内的消息')
    return
  }
  
  await ElMessageBox.confirm('确定要撤回这条消息吗？', '提示')
  await recallMessage(msg.id)
  msg.status = 1
  msg.content = '[消息已撤回]'
  ElMessage.success('已撤回')
}

// 插入表情
function insertEmoji(emoji: string) {
  messageInput.value += emoji
  showEmojiPicker.value = false
}

function getChatTarget() {
  if (!activeSession.value) {
    throw new Error('无当前会话')
  }
  const isGroup = activeSession.value.type === 2
  const rawToId = isGroup
    ? (activeSession.value.groupId ?? activeSession.value.userId)
    : activeSession.value.userId
  if (!rawToId) {
    throw new Error('目标用户/群ID不存在')
  }
  // 直接使用字符串 ID，避免大数字精度丢失
  const toId = String(rawToId)
  return {
    cmdType: isGroup ? 2 : 1,
    toId,
  }
}

// 创建群聊成功
async function handleGroupCreated() {
  createGroupVisible.value = false
  await loadSessions()
  ElMessage.success('群聊创建成功')
}

// 过滤会话
const filteredSessions = computed(() => {
  if (!searchKeyword.value) return sessions.value
  const keyword = searchKeyword.value.toLowerCase()
  return sessions.value.filter(s => 
    s.nickname?.toLowerCase().includes(keyword)
  )
})

function sendMessage() {
  if (!messageInput.value.trim() || !activeSession.value) return
  const { cmdType, toId } = getChatTarget()
  
  console.log('发送消息:', { cmdType, toId, content: messageInput.value.trim() })

  ws?.send({
    cmdType,
    toId,
    msgType: 1,
    content: messageInput.value.trim()
  })
  
  // 添加到本地消息列表
  messages.value.push({
    id: Date.now(),
    sessionId: activeSession.value.sessionId,
    fromId: userStore.userInfo!.userId,
    toId,
    cmdType,
    msgType: 1,
    content: messageInput.value.trim(),
    msgSeq: Date.now(),
    createTime: new Date().toISOString(),
    status: 0,
    readStatus: 0  // 私聊消息默认未读
  })
  
  messageInput.value = ''
  nextTick(() => scrollToBottom())
}

function scrollToBottom() {
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }
}

function formatTime(time: string): string {
  if (!time) return ''
  const date = new Date(time)
  if (Number.isNaN(date.getTime())) return ''

  const now = new Date()
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const startOfDate = new Date(date.getFullYear(), date.getMonth(), date.getDate())
  const diffDays = Math.round((startOfToday.getTime() - startOfDate.getTime()) / (1000 * 60 * 60 * 24))

  const timeStr = date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })

  // 今天：只显示时间，例如 16:32
  if (diffDays === 0) {
    return timeStr
  }

  // 昨天：显示“昨天 16:32”
  if (diffDays === 1) {
    return `昨天 ${timeStr}`
  }

  // 同一年：显示“M月d日 16:32”
  if (date.getFullYear() === now.getFullYear()) {
    const dateStr = date.toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' })
    return `${dateStr} ${timeStr}`
  }

  // 不同年份：显示“yyyy/M/d 16:32”
  const fullDateStr = date.toLocaleDateString('zh-CN', { year: 'numeric', month: 'numeric', day: 'numeric' })
  return `${fullDateStr} ${timeStr}`
}

const isMyMessage = (msg: Message) => String(msg.fromId) === String(userStore.userInfo?.userId)

// 右键菜单
function handleSessionContextMenu(e: MouseEvent, session: Session) {
  e.preventDefault()
  contextMenuSession.value = session
  contextMenuPosition.value = { x: e.clientX, y: e.clientY }
  contextMenuVisible.value = true
}

function closeContextMenu() {
  contextMenuVisible.value = false
}

async function handleDeleteSession(keepMessages: boolean) {
  if (!contextMenuSession.value) return
  
  const session = contextMenuSession.value
  const name = session.nickname || '该会话'
  
  try {
    await ElMessageBox.confirm(
      keepMessages 
        ? `确定要删除与"${name}"的会话吗？聊天记录将保留。`
        : `确定要删除与"${name}"的会话和所有聊天记录吗？此操作不可恢复。`,
      '删除会话',
      { type: 'warning' }
    )
    
    await deleteSession(session.sessionId, keepMessages)
    sessions.value = sessions.value.filter(s => s.sessionId !== session.sessionId)
    
    if (activeSession.value?.sessionId === session.sessionId) {
      activeSession.value = null
      messages.value = []
    }
    
    ElMessage.success('已删除会话')
  } catch {
    // 取消
  } finally {
    closeContextMenu()
  }
}

async function handleClearMessages() {
  if (!contextMenuSession.value) return
  
  const session = contextMenuSession.value
  
  try {
    await ElMessageBox.confirm(
      `确定要清空与"${session.nickname}"的所有聊天记录吗？此操作不可恢复。`,
      '清空消息',
      { type: 'warning' }
    )
    
    await clearMessages(session.sessionId)
    
    if (activeSession.value?.sessionId === session.sessionId) {
      messages.value = []
    }
    
    // 更新会话列表中的最后消息
    session.lastMessage = '[已清空]'
    
    ElMessage.success('已清空聊天记录')
  } catch {
    // 取消
  } finally {
    closeContextMenu()
  }
}

async function handleMarkAsRead() {
  if (!contextMenuSession.value) return
  
  const session = contextMenuSession.value
  await markAsRead(session.sessionId)
  session.unreadCount = 0
  closeContextMenu()
}

// 聊天框右上角菜单命令
async function handleChatCommand(command: string) {
  if (!activeSession.value) return
  
  switch (command) {
    case 'friendSettings':
      // 打开好友资料抽屉
      currentFriend.value = {
        id: activeSession.value.userId!,
        nickname: activeSession.value.nickname,
        avatar: activeSession.value.avatar,
        remark: activeSession.value.nickname,
      }
      friendDrawerVisible.value = true
      break
    case 'groupSettings':
      groupDrawerVisible.value = true
      break
    case 'clearMessages':
      try {
        await ElMessageBox.confirm(
          `确定要清空与"${activeSession.value.nickname}"的所有聊天记录吗？此操作不可恢复。`,
          '清空消息',
          { type: 'warning' }
        )
        await clearMessages(activeSession.value.sessionId)
        messages.value = []
        ElMessage.success('已清空聊天记录')
      } catch {
        // 取消
      }
      break
    case 'blockFriend':
      try {
        await ElMessageBox.confirm(
          `确定要拉黑"${activeSession.value.nickname}"吗？拉黑后将无法收到对方消息。`,
          '拉黑好友',
          { type: 'warning' }
        )
        await blockFriend(String(activeSession.value.userId))
        ElMessage.success('已拉黑该好友')
      } catch {
        // 取消
      }
      break
    case 'deleteFriend':
      try {
        await ElMessageBox.confirm(
          `确定要删除好友"${activeSession.value.nickname}"吗？`,
          '删除好友',
          {
            type: 'warning',
            distinguishCancelAndClose: true,
            confirmButtonText: '删除并保留聊天记录',
            cancelButtonText: '删除并清空聊天记录',
          }
        )
        // 点击确定：删除好友但保留消息
        await deleteFriend(String(activeSession.value.userId), true)
        sessions.value = sessions.value.filter(s => s.sessionId !== activeSession.value?.sessionId)
        activeSession.value = null
        messages.value = []
        ElMessage.success('已删除好友')
        loadSessions()
      } catch (action) {
        if (action === 'cancel') {
          // 点击取消：删除好友并清空消息
          await deleteFriend(String(activeSession.value.userId), false)
          sessions.value = sessions.value.filter(s => s.sessionId !== activeSession.value?.sessionId)
          activeSession.value = null
          messages.value = []
          ElMessage.success('已删除好友和聊天记录')
          loadSessions()
        }
        // close: 直接关闭，不做任何操作
      }
      break
  }
}

// 从好友管理发起聊天
function startChatWithFriend(friend: Friend) {
  // 查找或创建会话
  const friendId = String(friend.id)
  const sessionId = generatePrivateSessionId(friendId)
  let session = sessions.value.find(s => s.sessionId === sessionId)
  
  if (!session) {
    // 创建新会话
    session = {
      sessionId,
      type: 1,
      userId: friendId,
      nickname: friend.remark || friend.nickname,
      avatar: friend.avatar,
      lastMessage: '',
      lastTime: new Date().toISOString(),
      unreadCount: 0
    }
    sessions.value.unshift(session)
  }
  
  selectSession(session)
}

function generatePrivateSessionId(friendId: string | number): string {
  const rawMyId = userStore.userInfo?.userId
  if (!rawMyId) return ''
  const myIdStr = String(rawMyId)
  const friendIdStr = String(friendId)
  const isNumeric = (v: string) => /^\d+$/.test(v)

  let min: string
  let max: string

  if (isNumeric(myIdStr) && isNumeric(friendIdStr)) {
    // 两个 ID 都是纯数字时，使用数值比较，保证和后端 Long min/max 规则一致
    const my = BigInt(myIdStr)
    const other = BigInt(friendIdStr)
    if (my <= other) {
      min = myIdStr
      max = friendIdStr
    } else {
      min = friendIdStr
      max = myIdStr
    }
  } else {
    // 非纯数字（几乎不会出现），退回字符串比较
    ;[min, max] = myIdStr < friendIdStr ? [myIdStr, friendIdStr] : [friendIdStr, myIdStr]
  }

  return `private_${min}_${max}`
}

// 消息右键菜单
function handleMessageContextMenu(e: MouseEvent, msg: Message) {
  e.preventDefault()
  contextMenuMessage.value = msg
  msgContextMenuPosition.value = { x: e.clientX, y: e.clientY }
  msgContextMenuVisible.value = true
}

function closeMsgContextMenu() {
  msgContextMenuVisible.value = false
}

async function handleDeleteMessage() {
  if (!contextMenuMessage.value) return
  
  const msg = contextMenuMessage.value
  
  try {
    await deleteMessage(String(msg.id))
    messages.value = messages.value.filter(m => m.id !== msg.id)
    ElMessage.success('消息已删除')
  } catch {
    // 错误已被拦截器处理
  } finally {
    closeMsgContextMenu()
  }
}

async function handleCopyMessage() {
  if (!contextMenuMessage.value) return
  
  try {
    await navigator.clipboard.writeText(contextMenuMessage.value.content)
    ElMessage.success('已复制')
  } catch {
    ElMessage.error('复制失败')
  } finally {
    closeMsgContextMenu()
  }
}

// 搜索消息
function searchMessages() {
  if (!messageSearchKeyword.value.trim()) {
    searchResults.value = []
    return
  }
  
  const keyword = messageSearchKeyword.value.toLowerCase()
  searchResults.value = messages.value.filter(msg => 
    msg.msgType === 1 && msg.content.toLowerCase().includes(keyword)
  )
}

// 滚动到指定消息
function scrollToMessage(msg: Message) {
  searchVisible.value = false
  messageSearchKeyword.value = ''
  searchResults.value = []
  
  // 滚动到该消息
  nextTick(() => {
    const msgElement = messagesRef.value?.querySelector(`[data-msg-id="${msg.id}"]`)
    if (msgElement) {
      msgElement.scrollIntoView({ behavior: 'smooth', block: 'center' })
      // 高亮效果
      msgElement.classList.add('bg-pop-orange/30', 'rounded-xl')
      setTimeout(() => {
        msgElement.classList.remove('bg-pop-orange/30', 'rounded-xl')
      }, 2000)
    }
  })
}
</script>

<template>
  <div class="h-screen flex bg-nb-bg p-4 gap-4">
    <!-- 左侧功能栏 - Neo-Brutalism 风格 -->
    <div class="w-20 bg-pop-blue border-3 border-nb-border shadow-brutal rounded-2xl flex flex-col items-center py-6">
      <router-link to="/" class="mb-6 text-3xl hover:animate-shake">
        🍮
      </router-link>
      <el-button circle class="mb-4 !bg-pop-yellow !text-black" type="primary">
        <el-icon><ChatDotRound /></el-icon>
      </el-button>
      <el-badge :value="unreadApplyCount" :hidden="unreadApplyCount === 0" :max="99" class="mb-4">
        <el-button circle class="!bg-white" @click="notificationVisible = true">
          <el-icon><Bell /></el-icon>
        </el-button>
      </el-badge>
      <el-button circle class="mb-4 !bg-pop-green" @click="friendManagerVisible = true">
        <el-icon><User /></el-icon>
      </el-button>
      <el-dropdown trigger="click">
        <el-button circle class="mb-4 !bg-pop-orange">
          <el-icon><Plus /></el-icon>
        </el-button>
        <template #dropdown>
          <el-dropdown-menu class="!border-3 !border-black !shadow-brutal !rounded-xl">
            <el-dropdown-item @click="addContactVisible = true">
              <el-icon class="mr-2"><User /></el-icon>添加好友/群
            </el-dropdown-item>
            <el-dropdown-item @click="createGroupVisible = true">
              <el-icon class="mr-2"><ChatLineSquare /></el-icon>创建群聊
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <div class="flex-1" />
      <el-button circle class="!bg-white" @click="settingsVisible = true">
        <el-icon><Setting /></el-icon>
      </el-button>
    </div>

    <!-- 会话列表 - Neo-Brutalism 风格 -->
    <div class="w-80 bg-white border-3 border-nb-border shadow-brutal rounded-2xl flex flex-col overflow-hidden">
      <!-- 会话列表头部 -->
      <div class="p-4 bg-pop-yellow border-b-3 border-nb-border">
        <h2 class="font-black text-xl uppercase mb-3">消息</h2>
        <el-input v-model="searchKeyword" placeholder="搜索会话" prefix-icon="Search" clearable />
      </div>
      
      <!-- 会话项列表 -->
      <div class="flex-1 overflow-y-auto bg-nb-bg p-2">
        <div v-if="filteredSessions.length === 0" class="p-4 text-center">
          <div class="nb-badge">暂无会话</div>
        </div>
        
        <div
          v-for="session in filteredSessions"
          :key="session.sessionId"
          @click="selectSession(session)"
          @contextmenu.prevent="handleSessionContextMenu($event, session)"
          class="flex items-center gap-3 p-3 cursor-pointer border-2 border-transparent hover:border-nb-border hover:bg-white rounded-xl transition-all mb-2"
          :class="{ 'bg-white border-nb-border shadow-brutal-sm': activeSession?.sessionId === session.sessionId }"
        >
          <!-- 头像 + 在线状态 + 未读红点 -->
          <div class="relative">
            <el-avatar :size="48" :src="session.avatar" class="!border-2 !border-black">{{ session.nickname?.[0] }}</el-avatar>
            <!-- 在线状态指示器（仅私聊） -->
            <div
              v-if="session.type === 1 && chatSettings.showOnlineStatus"
              class="absolute -bottom-0.5 -right-0.5 w-4 h-4 rounded-full border-2 border-white"
              :class="onlineStatus[String(session.userId)] ? 'bg-green-500' : 'bg-gray-400'"
              :title="onlineStatus[String(session.userId)] ? '在线' : '离线'"
            />
            <!-- 未读消息红点 -->
            <div
              v-if="session.unreadCount && session.unreadCount > 0"
              class="absolute -top-1 -right-1 min-w-[20px] h-5 px-1.5 bg-pop-red text-white text-xs font-bold rounded-full border-2 border-white flex items-center justify-center"
            >
              {{ session.unreadCount > 99 ? '99+' : session.unreadCount }}
            </div>
          </div>
          <div class="flex-1 min-w-0">
            <div class="flex items-center justify-between">
              <span class="font-bold text-nb-text truncate">{{ session.nickname }}</span>
              <span class="text-xs font-semibold text-nb-text-sub bg-gray-100 px-2 py-0.5 rounded border border-gray-300">{{ formatTime(session.lastTime) }}</span>
            </div>
            <div class="flex items-center justify-between mt-1">
              <p class="text-sm text-nb-text-sub truncate flex-1 mr-2">{{ session.lastMessage }}</p>
              <!-- 群聊标识 -->
              <span v-if="session.type === 2" class="text-xs bg-pop-blue text-white px-1.5 py-0.5 rounded font-bold shrink-0">群</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 聊天区域 - Neo-Brutalism 风格 -->
    <div class="flex-1 bg-white border-3 border-nb-border shadow-brutal rounded-2xl flex flex-col overflow-hidden">
      <!-- 无选中状态 -->
      <div v-if="!activeSession" class="flex-1 flex items-center justify-center bg-nb-bg">
        <div class="text-center">
          <el-icon size="64" class="mb-6 animate-bounce-in"><ChatLineSquare /></el-icon>
          <div class="nb-badge text-lg">选择一个会话开始聊天</div>
        </div>
      </div>
      
      <!-- 聊天内容 -->
      <template v-else>
        <!-- 顶部栏 - Neo-Brutalism -->
        <div class="h-18 px-6 bg-pop-blue border-b-3 border-nb-border flex items-center justify-between">
          <div class="flex items-center gap-3">
            <div class="relative">
              <el-avatar :size="40" :src="activeSession.avatar" class="!border-2 !border-white">{{ activeSession.nickname?.[0] }}</el-avatar>
              <!-- 在线状态指示器 -->
              <div
                v-if="activeSession.type === 1 && chatSettings.showOnlineStatus"
                class="absolute -bottom-0.5 -right-0.5 w-3.5 h-3.5 rounded-full border-2 border-white"
                :class="onlineStatus[String(activeSession.userId)] ? 'bg-green-500' : 'bg-gray-400'"
              />
            </div>
            <div>
              <span class="font-bold text-white text-lg uppercase">{{ activeSession.nickname }}</span>
              <!-- 在线状态文字 -->
              <div v-if="activeSession.type === 1 && chatSettings.showOnlineStatus" class="text-xs text-white/70">
                {{ onlineStatus[String(activeSession.userId)] ? '在线' : '离线' }}
              </div>
            </div>
          </div>
          <div class="flex items-center gap-2">
            <!-- 搜索按钮 -->
            <el-popover
              :visible="searchVisible"
              placement="bottom"
              :width="300"
              trigger="click"
            >
              <template #reference>
                <el-button circle size="small" class="!bg-white !text-black" @click="searchVisible = !searchVisible">
                  <el-icon><Search /></el-icon>
                </el-button>
              </template>
              <div class="space-y-3">
                <el-input
                  v-model="messageSearchKeyword"
                  placeholder="搜索聊天记录"
                  prefix-icon="Search"
                  clearable
                  @keyup.enter="searchMessages"
                />
                <div v-if="searchResults.length > 0" class="max-h-60 overflow-y-auto space-y-2">
                  <div
                    v-for="msg in searchResults"
                    :key="msg.id"
                    class="p-2 rounded-lg bg-nb-bg hover:bg-pop-yellow cursor-pointer border-2 border-transparent hover:border-black transition-all"
                    @click="scrollToMessage(msg)"
                  >
                    <div class="text-xs text-gray-500 mb-1">{{ formatTime(msg.createTime) }}</div>
                    <div class="text-sm font-medium truncate">{{ msg.content }}</div>
                  </div>
                </div>
                <div v-else-if="messageSearchKeyword && searchResults.length === 0" class="text-center text-gray-400 py-4">
                  未找到相关消息
                </div>
              </div>
            </el-popover>
            <el-dropdown trigger="click" @command="handleChatCommand">
            <el-button circle size="small" class="!bg-white !text-black">
              <el-icon><MoreFilled /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <template v-if="activeSession?.type === 1">
                  <!-- 私聊菜单 -->
                  <el-dropdown-item command="friendSettings">
                    <el-icon><User /></el-icon> 好友资料
                  </el-dropdown-item>
                  <el-dropdown-item command="clearMessages" divided>
                    <el-icon><Delete /></el-icon> 清空聊天记录
                  </el-dropdown-item>
                  <el-dropdown-item command="blockFriend">
                    <el-icon><Hide /></el-icon> 拉黑好友
                  </el-dropdown-item>
                  <el-dropdown-item command="deleteFriend">
                    <el-icon><RemoveFilled /></el-icon> 删除好友
                  </el-dropdown-item>
                </template>
                <template v-else>
                  <!-- 群聊菜单 -->
                  <el-dropdown-item command="groupSettings">
                    <el-icon><Setting /></el-icon> 群设置
                  </el-dropdown-item>
                  <el-dropdown-item command="clearMessages">
                    <el-icon><Delete /></el-icon> 清空聊天记录
                  </el-dropdown-item>
                </template>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          </div>
        </div>

        <!-- 群公告提示条（群聊时显示） -->
        <div 
          v-if="activeSession?.type === 2 && groupNoticeContent"
          class="px-4 py-2 bg-pop-orange/20 border-b-2 border-pop-orange flex items-center gap-2 cursor-pointer hover:bg-pop-orange/30 transition-colors"
          @click="showGroupNotice = true"
        >
          <el-icon class="text-pop-orange"><Notification /></el-icon>
          <span class="text-sm font-medium text-nb-text truncate flex-1">
            📢 群公告：{{ groupNoticeContent.length > 30 ? groupNoticeContent.slice(0, 30) + '...' : groupNoticeContent }}
          </span>
          <el-icon class="text-gray-400"><ArrowRight /></el-icon>
        </div>
        
        <!-- 消息列表 - Neo-Brutalism -->
        <div ref="messagesRef" class="flex-1 overflow-y-auto p-6 space-y-4 bg-nb-bg">
          <div v-if="loading" class="flex justify-center">
            <div class="nb-badge animate-pulse">加载中...</div>
          </div>
          
          <div
            v-for="msg in messages"
            :key="msg.id"
            :data-msg-id="msg.id"
            class="flex group transition-colors duration-500"
            :class="isMyMessage(msg) ? 'justify-end' : 'justify-start'"
          >
            <div class="flex gap-3 max-w-[70%]" :class="isMyMessage(msg) ? 'flex-row-reverse' : ''">
              <el-avatar :size="40" :src="isMyMessage(msg) ? userStore.userInfo?.avatar : (msg.fromAvatar || activeSession.avatar)" class="!border-2 !border-black !shadow-brutal-sm">
                {{ isMyMessage(msg) ? userStore.userInfo?.nickname?.[0] : (msg.fromNickname?.[0] || activeSession.nickname?.[0]) }}
              </el-avatar>
              <div class="relative" @contextmenu="handleMessageContextMenu($event, msg)">
                <div
                  class="rounded-xl px-4 py-3 cursor-pointer border-2 border-black shadow-brutal-sm transition-all hover:translate-x-0.5 hover:-translate-y-0.5"
                  :class="isMyMessage(msg) ? 'bg-pop-yellow text-black' : 'bg-white text-nb-text'"
                >
                  <template v-if="msg.status === 1">
                    <p class="text-gray-400 italic">{{ msg.content }}</p>
                  </template>
                  <template v-else>
                    <template v-if="msg.msgType === 2">
                      <img
                        :src="msg.content"
                        class="max-w-[240px] rounded cursor-pointer"
                        alt="图片消息"
                      />
                    </template>
                    <template v-else-if="msg.msgType === 3">
                      <a
                        :href="msg.content"
                        target="_blank"
                        class="flex items-center gap-2 text-sm underline break-all"
                      >
                        <el-icon><FolderOpened /></el-icon>
                        <span class="truncate max-w-[180px]">
                          {{ msg.extra || getFileName(msg.content) }}
                        </span>
                      </a>
                    </template>
                    <template v-else>
                      <p class="whitespace-pre-wrap break-words">{{ msg.content }}</p>
                    </template>
                  </template>
                  <div class="flex items-center justify-end gap-2 mt-1">
                    <span class="text-xs opacity-60">{{ formatTime(msg.createTime) }}</span>
                    <!-- 已读状态（仅自己发送的私聊消息显示） -->
                    <span
                      v-if="isMyMessage(msg) && activeSession?.type === 1 && chatSettings.showReadStatus"
                      class="text-xs"
                      :class="msg.readStatus ? 'text-green-500' : 'text-gray-400'"
                    >
                      {{ msg.readStatus ? '已读' : '未读' }}
                    </span>
                  </div>
                </div>
                <!-- 消息操作菜单 -->
                <div
                  v-if="isMyMessage(msg) && msg.status !== 1"
                  class="absolute top-0 opacity-0 group-hover:opacity-100 transition-opacity"
                  :class="isMyMessage(msg) ? 'right-full mr-2' : 'left-full ml-2'"
                >
                  <el-button size="small" text @click="handleRecallMessage(msg)">
                    撤回
                  </el-button>
                </div>
              </div>
            </div>
          </div>
        </div>
        
        <!-- 输入区域 - Neo-Brutalism -->
        <div class="p-4 bg-white border-t-3 border-nb-border">
          <!-- 工具栏 -->
          <div class="flex items-center gap-2 mb-3">
            <el-popover :visible="showEmojiPicker" placement="top" :width="320" trigger="click">
              <template #reference>
                <el-button circle size="small" class="!bg-pop-orange !text-black" @click="showEmojiPicker = !showEmojiPicker">
                  <el-icon><Sunny /></el-icon>
                </el-button>
              </template>
              <div class="grid grid-cols-10 gap-2 p-2">
                <span
                  v-for="emoji in emojis"
                  :key="emoji"
                  class="text-xl cursor-pointer hover:bg-gray-100 rounded p-1 text-center"
                  @click="insertEmoji(emoji)"
                >
                  {{ emoji }}
                </span>
              </div>
            </el-popover>
            <el-button circle size="small" class="!bg-pop-purple !text-white" :loading="uploading" @click="chooseImage">
              <el-icon><Picture /></el-icon>
            </el-button>
            <el-button circle size="small" class="!bg-pop-pink !text-white" :loading="uploading" @click="chooseFile">
              <el-icon><FolderOpened /></el-icon>
            </el-button>
          </div>
          <!-- 隐藏的文件选择器 -->
          <input
            ref="imageInputRef"
            type="file"
            accept="image/*"
            class="hidden"
            @change="handleImageChange"
          />
          <input
            ref="fileInputRef"
            type="file"
            class="hidden"
            @change="handleFileChange"
          />
          <div class="flex gap-3 items-end">
            <el-input
              v-model="messageInput"
              type="textarea"
              :rows="1"
              :autosize="{ minRows: 1, maxRows: 4 }"
              placeholder="输入消息... (Shift+Enter 换行)"
              class="flex-1"
              resize="none"
              @keydown="handleInputKeydown"
            />
            <el-button type="primary" size="large" class="!bg-pop-green !px-6 !h-10" @click="sendMessage">
              <el-icon class="mr-1"><Promotion /></el-icon>
              发送
            </el-button>
          </div>
          <div class="text-xs text-gray-400 mt-1">
            {{ chatSettings.enterToSend ? 'Enter 发送，Shift+Enter 换行' : 'Ctrl+Enter 发送' }}
          </div>
        </div>
      </template>
    </div>

    <!-- 添加联系人弹窗 -->
    <AddContactModal
      v-model:visible="addContactVisible"
      @success="loadSessions"
    />

    <!-- 通知中心 -->
    <NotificationCenter
      v-model:visible="notificationVisible"
      @refresh="loadSessions(); loadUnreadCount()"
    />

    <!-- 群设置抽屉 -->
    <GroupDrawer
      v-if="activeSession?.type === 2"
      v-model:visible="groupDrawerVisible"
      :group-id="String(activeSession.groupId || activeSession.userId)"
      @quit="activeSession = null; loadSessions()"
      @dissolved="activeSession = null; loadSessions()"
    />

    <!-- 好友资料抽屉 -->
    <FriendDrawer
      v-model:visible="friendDrawerVisible"
      :friend="currentFriend"
      @deleted="activeSession = null; loadSessions()"
      @updated="(f) => { if (activeSession) activeSession.nickname = f.remark || f.nickname }"
    />
    
    <!-- 创建群聊弹窗 -->
    <CreateGroupModal
      v-model:visible="createGroupVisible"
      @success="handleGroupCreated"
    />

    <!-- 好友管理 -->
    <FriendManager
      v-model:visible="friendManagerVisible"
      @chat="startChatWithFriend"
      @refresh="loadSessions"
    />

    <!-- 设置抽屉 -->
    <SettingsDrawer v-model:visible="settingsVisible" />

    <!-- 群公告弹窗 - QQ风格 -->
    <el-dialog
      v-model="showGroupNotice"
      title=""
      width="400px"
      :show-close="false"
      class="nb-dialog group-notice-dialog"
    >
      <div class="text-center">
        <div class="bg-pop-orange border-3 border-black rounded-xl p-4 mb-4">
          <div class="flex items-center justify-center gap-2 mb-2">
            <el-icon class="text-2xl"><Notification /></el-icon>
            <span class="font-black text-xl">群公告</span>
          </div>
        </div>
        <div class="bg-nb-bg border-3 border-black rounded-xl p-4 text-left max-h-60 overflow-y-auto">
          <p class="whitespace-pre-wrap text-nb-text font-medium">{{ groupNoticeContent || '暂无公告' }}</p>
        </div>
      </div>
      <template #footer>
        <div class="flex justify-center gap-3">
          <el-button class="!border-2 !border-black !font-bold" @click="showGroupNotice = false">
            我知道了
          </el-button>
          <el-button 
            class="!bg-pop-blue !text-white !border-2 !border-black !font-bold"
            @click="markNoticeRead"
          >
            不再提示
          </el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 会话右键菜单 - Neo-Brutalism -->
    <Teleport to="body">
      <div
        v-if="contextMenuVisible"
        class="fixed z-50 bg-white border-3 border-nb-border rounded-xl shadow-brutal py-2 min-w-[180px] animate-bounce-in"
        :style="{ left: contextMenuPosition.x + 'px', top: contextMenuPosition.y + 'px' }"
        @click.stop
      >
        <div
          v-if="contextMenuSession?.unreadCount"
          class="px-4 py-2 text-sm font-bold text-nb-text hover:bg-pop-yellow cursor-pointer flex items-center"
          @click="handleMarkAsRead"
        >
          <el-icon class="mr-2"><Check /></el-icon>标记已读
        </div>
        <div
          class="px-4 py-2 text-sm font-bold text-nb-text hover:bg-pop-orange cursor-pointer flex items-center"
          @click="handleClearMessages"
        >
          <el-icon class="mr-2"><Delete /></el-icon>清空消息
        </div>
        <div
          class="px-4 py-2 text-sm font-bold text-nb-text hover:bg-gray-100 cursor-pointer flex items-center"
          @click="handleDeleteSession(true)"
        >
          <el-icon class="mr-2"><FolderRemove /></el-icon>删除会话
        </div>
        <div
          class="px-4 py-2 text-sm font-bold text-pop-red hover:bg-pop-red hover:text-white cursor-pointer flex items-center"
          @click="handleDeleteSession(false)"
        >
          <el-icon class="mr-2"><DeleteFilled /></el-icon>删除会话和消息
        </div>
      </div>
      <!-- 点击其他地方关闭菜单 -->
      <div
        v-if="contextMenuVisible"
        class="fixed inset-0 z-40"
        @click="closeContextMenu"
        @contextmenu.prevent="closeContextMenu"
      />

      <!-- 消息右键菜单 - Neo-Brutalism -->
      <div
        v-if="msgContextMenuVisible"
        class="fixed z-50 bg-white border-3 border-nb-border rounded-xl shadow-brutal py-2 min-w-[160px] animate-bounce-in"
        :style="{ left: msgContextMenuPosition.x + 'px', top: msgContextMenuPosition.y + 'px' }"
        @click.stop
      >
        <div
          v-if="contextMenuMessage?.msgType === 1"
          class="px-4 py-2 text-sm font-bold text-nb-text hover:bg-pop-blue hover:text-white cursor-pointer flex items-center"
          @click="handleCopyMessage"
        >
          <el-icon class="mr-2"><CopyDocument /></el-icon>复制
        </div>
        <div
          v-if="isMyMessage(contextMenuMessage!) && contextMenuMessage?.status !== 1"
          class="px-4 py-2 text-sm font-bold text-nb-text hover:bg-pop-orange cursor-pointer flex items-center"
          @click="handleRecallMessage(contextMenuMessage!); closeMsgContextMenu()"
        >
          <el-icon class="mr-2"><RefreshLeft /></el-icon>撤回
        </div>
        <div
          class="px-4 py-2 text-sm font-bold text-pop-red hover:bg-pop-red hover:text-white cursor-pointer flex items-center"
          @click="handleDeleteMessage"
        >
          <el-icon class="mr-2"><Delete /></el-icon>删除
        </div>
      </div>
      <!-- 点击其他地方关闭消息菜单 -->
      <div
        v-if="msgContextMenuVisible"
        class="fixed inset-0 z-40"
        @click="closeMsgContextMenu"
        @contextmenu.prevent="closeMsgContextMenu"
      />
    </Teleport>
  </div>
</template>
