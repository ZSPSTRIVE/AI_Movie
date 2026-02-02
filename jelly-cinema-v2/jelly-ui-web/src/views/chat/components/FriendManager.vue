<script setup lang="ts">
import { ref, onMounted, computed, watch, onUnmounted } from 'vue'
import {
  getFriendList,
  deleteFriend,
  setFriendRemark,
  blockFriend,
  unblockFriend,
  getBlacklist,
  checkOnlineBatch,
  type Friend
} from '@/api/im'
import { ElMessage, ElMessageBox } from 'element-plus'

const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'chat', friend: Friend): void
  (e: 'refresh'): void
}>()

const activeTab = ref<'friends' | 'blacklist'>('friends')
const loading = ref(false)
const friendList = ref<Friend[]>([])
const blacklist = ref<Friend[]>([])
const ONLINE_STATUS_REFRESH_INTERVAL = 15000
let onlineStatusTimer: number | null = null

// 备注编辑
const editingRemark = ref<string | null>(null)
const remarkInput = ref('')

// 搜索
const searchKeyword = ref('')
const filteredFriends = computed(() => {
  if (!searchKeyword.value.trim()) return friendList.value
  const keyword = searchKeyword.value.toLowerCase()
  return friendList.value.filter(f => {
    const nickname = (f.nickname || '').toLowerCase()
    const remark = (f.remark || '').toLowerCase()
    const username = (f.username || '').toLowerCase()
    return nickname.includes(keyword) || remark.includes(keyword) || username.includes(keyword)
  })
})

onMounted(() => {
  loadFriends()
})

watch(() => props.visible, async (val) => {
  if (val) {
    if (activeTab.value === 'friends') {
      await loadFriends()
      await refreshFriendOnlineStatus()
      startOnlineStatusRefresh()
    } else {
      await loadBlacklist()
    }
  } else {
    stopOnlineStatusRefresh()
  }
})

onUnmounted(() => {
  stopOnlineStatusRefresh()
})

async function loadFriends() {
  loading.value = true
  try {
    const res = await getFriendList()
    friendList.value = res.data || []
  } catch (e) {
    console.error('加载好友列表失败:', e)
  } finally {
    loading.value = false
  }
}

async function refreshFriendOnlineStatus() {
  if (!friendList.value.length) return
  try {
    const ids = friendList.value.map(friend => friend.id)
    const res = await checkOnlineBatch(ids)
    if (res.data) {
      friendList.value.forEach(friend => {
        friend.online = Boolean(res.data?.[String(friend.id)])
      })
    }
  } catch (e) {
    console.warn('刷新好友在线状态失败:', e)
  }
}

async function loadBlacklist() {
  loading.value = true
  try {
    const res = await getBlacklist()
    blacklist.value = res.data || []
  } catch (e) {
    console.error('加载黑名单失败:', e)
  } finally {
    loading.value = false
  }
}

function handleTabChange(tab: 'friends' | 'blacklist') {
  if (tab === 'blacklist') {
    stopOnlineStatusRefresh()
    loadBlacklist()
  } else {
    loadFriends()
    refreshFriendOnlineStatus()
    if (props.visible) {
      startOnlineStatusRefresh()
    }
  }
}

function startOnlineStatusRefresh() {
  if (onlineStatusTimer !== null) return
  onlineStatusTimer = window.setInterval(() => {
    if (!props.visible || activeTab.value !== 'friends') return
    refreshFriendOnlineStatus()
  }, ONLINE_STATUS_REFRESH_INTERVAL)
}

function stopOnlineStatusRefresh() {
  if (onlineStatusTimer === null) return
  window.clearInterval(onlineStatusTimer)
  onlineStatusTimer = null
}

async function handleDelete(friend: Friend) {
  try {
    await ElMessageBox.confirm(
      `确定要删除好友 "${friend.remark || friend.nickname}" 吗？`,
      '删除好友',
      {
        confirmButtonText: '删除并保留聊天记录',
        cancelButtonText: '取消',
        distinguishCancelAndClose: true,
        type: 'warning'
      }
    )
    
    await deleteFriend(String(friend.id), true)
    ElMessage.success('已删除好友，聊天记录已保留')
    loadFriends()
    emit('refresh')
  } catch (action) {
    if (action === 'close') {
      // 用户点击了关闭按钮，尝试提供"删除并清空记录"选项
      try {
        await ElMessageBox.confirm(
          '是否同时删除聊天记录？',
          '删除好友',
          {
            confirmButtonText: '删除并清空记录',
            cancelButtonText: '取消',
            type: 'warning'
          }
        )
        await deleteFriend(String(friend.id), false)
        ElMessage.success('已删除好友和聊天记录')
        loadFriends()
        emit('refresh')
      } catch {
        // 取消
      }
    }
    // 取消操作
  }
}

async function handleBlock(friend: Friend) {
  try {
    await ElMessageBox.confirm(
      `确定要拉黑 "${friend.remark || friend.nickname}" 吗？拉黑后对方将无法给你发消息。`,
      '拉黑好友',
      {
        confirmButtonText: '确定拉黑',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    await blockFriend(String(friend.id))
    ElMessage.success('已拉黑')
    loadFriends()
  } catch {
    // 取消
  }
}

async function handleUnblock(friend: Friend) {
  try {
    await unblockFriend(String(friend.id))
    ElMessage.success('已解除拉黑')
    loadBlacklist()
  } catch {
    // 错误已被拦截器处理
  }
}

function startEditRemark(friend: Friend) {
  editingRemark.value = String(friend.id)
  remarkInput.value = friend.remark || ''
}

async function saveRemark(friend: Friend) {
  try {
    await setFriendRemark({
      friendId: String(friend.id),
      remark: remarkInput.value
    })
    friend.remark = remarkInput.value
    ElMessage.success('备注已更新')
  } catch {
    // 错误已被拦截器处理
  } finally {
    editingRemark.value = null
  }
}

function cancelEditRemark() {
  editingRemark.value = null
  remarkInput.value = ''
}

function startChat(friend: Friend) {
  emit('chat', friend)
  emit('update:visible', false)
}
</script>

<template>
  <el-drawer
    :model-value="visible"
    @update:model-value="emit('update:visible', $event)"
    title="好友管理"
    size="400px"
  >
    <!-- Tab 切换 -->
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="好友列表" name="friends" />
      <el-tab-pane label="黑名单" name="blacklist" />
    </el-tabs>

    <!-- 搜索框 -->
    <el-input
      v-if="activeTab === 'friends'"
      v-model="searchKeyword"
      placeholder="搜索好友"
      clearable
      class="mb-4"
    >
      <template #prefix>
        <el-icon><Search /></el-icon>
      </template>
    </el-input>

    <!-- 好友列表 -->
    <div v-if="activeTab === 'friends'" v-loading="loading" class="space-y-2">
      <el-empty v-if="filteredFriends.length === 0 && !loading" description="暂无好友" :image-size="80" />
      
      <div
        v-for="friend in filteredFriends"
        :key="friend.id"
        class="flex items-center gap-3 p-3 bg-dark-card rounded-lg hover:bg-dark-card-hover transition-colors"
      >
        <!-- 头像和在线状态 -->
        <div class="relative">
          <el-avatar :size="44" :src="friend.avatar">
            {{ (friend.remark || friend.nickname)?.[0] }}
          </el-avatar>
          <span
            v-if="friend.online"
            class="absolute bottom-0 right-0 w-3 h-3 bg-green-500 rounded-full border-2 border-dark-card"
          />
        </div>

        <!-- 信息 -->
        <div class="flex-1 min-w-0">
          <div class="flex items-center gap-2">
            <!-- 备注/昵称 -->
            <template v-if="editingRemark === String(friend.id)">
              <el-input
                v-model="remarkInput"
                size="small"
                placeholder="输入备注"
                @keyup.enter="saveRemark(friend)"
                @keyup.esc="cancelEditRemark"
                style="width: 120px"
              />
              <el-button size="small" type="primary" @click="saveRemark(friend)">保存</el-button>
              <el-button size="small" @click="cancelEditRemark">取消</el-button>
            </template>
            <template v-else>
              <span class="font-medium text-white">{{ friend.remark || friend.nickname }}</span>
              <el-button size="small" text @click="startEditRemark(friend)">
                <el-icon><Edit /></el-icon>
              </el-button>
            </template>
          </div>
          <div v-if="friend.remark" class="text-xs text-gray-500">
            昵称: {{ friend.nickname }}
          </div>
          <div class="text-sm text-gray-400 truncate">
            {{ friend.signature || '暂无签名' }}
          </div>
        </div>

        <!-- 操作按钮 -->
        <div class="flex gap-1">
          <el-button size="small" type="primary" @click="startChat(friend)">
            发消息
          </el-button>
          <el-dropdown trigger="click">
            <el-button size="small" text>
              <el-icon><MoreFilled /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="handleBlock(friend)">
                  <el-icon><CircleClose /></el-icon>
                  拉黑
                </el-dropdown-item>
                <el-dropdown-item @click="handleDelete(friend)" divided>
                  <el-icon class="text-red-500"><Delete /></el-icon>
                  <span class="text-red-500">删除好友</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </div>

    <!-- 黑名单 -->
    <div v-else v-loading="loading" class="space-y-2">
      <el-empty v-if="blacklist.length === 0 && !loading" description="黑名单为空" :image-size="80" />
      
      <div
        v-for="friend in blacklist"
        :key="friend.id"
        class="flex items-center gap-3 p-3 bg-dark-card rounded-lg"
      >
        <el-avatar :size="44" :src="friend.avatar">
          {{ (friend.remark || friend.nickname)?.[0] }}
        </el-avatar>

        <div class="flex-1 min-w-0">
          <div class="font-medium text-white">{{ friend.remark || friend.nickname }}</div>
        </div>

        <el-button size="small" @click="handleUnblock(friend)">
          解除拉黑
        </el-button>
      </div>
    </div>
  </el-drawer>
</template>

<style scoped>
.bg-dark-card {
  background-color: #1e1e1e;
}
.bg-dark-card-hover:hover {
  background-color: #2a2a2a;
}
</style>
