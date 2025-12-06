import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { getSessions, getUnreadApplyCount } from '@/api/im'

export const useMessageStore = defineStore('message', () => {
  // 未读消息数（会话中的未读消息总数）
  const unreadMessageCount = ref(0)
  
  // 未读申请数（好友申请/群申请）
  const unreadApplyCount = ref(0)
  
  // 总未读数
  const totalUnreadCount = computed(() => unreadMessageCount.value + unreadApplyCount.value)
  
  // 加载未读消息数（从会话列表汇总）
  async function loadUnreadMessageCount() {
    try {
      const res = await getSessions()
      const sessions = res.data || []
      unreadMessageCount.value = sessions.reduce((sum, s) => sum + (s.unreadCount || 0), 0)
    } catch (e) {
      console.warn('加载未读消息数失败', e)
    }
  }
  
  // 加载未读申请数
  async function loadUnreadApplyCount() {
    try {
      const res = await getUnreadApplyCount()
      unreadApplyCount.value = res.data || 0
    } catch (e) {
      console.warn('加载未读申请数失败', e)
    }
  }
  
  // 加载所有未读计数
  async function loadAllUnreadCounts() {
    await Promise.all([loadUnreadMessageCount(), loadUnreadApplyCount()])
  }
  
  // 更新未读消息数（直接设置）
  function setUnreadMessageCount(count: number) {
    unreadMessageCount.value = count
  }
  
  // 更新未读申请数
  function setUnreadApplyCount(count: number) {
    unreadApplyCount.value = count
  }
  
  // 增加未读消息数
  function incrementUnreadMessage(count: number = 1) {
    unreadMessageCount.value += count
  }
  
  // 减少未读消息数
  function decrementUnreadMessage(count: number) {
    unreadMessageCount.value = Math.max(0, unreadMessageCount.value - count)
  }
  
  // 清空所有未读
  function clearAllUnread() {
    unreadMessageCount.value = 0
    unreadApplyCount.value = 0
  }
  
  return {
    unreadMessageCount,
    unreadApplyCount,
    totalUnreadCount,
    loadUnreadMessageCount,
    loadUnreadApplyCount,
    loadAllUnreadCounts,
    setUnreadMessageCount,
    setUnreadApplyCount,
    incrementUnreadMessage,
    decrementUnreadMessage,
    clearAllUnread
  }
})
