<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { 
  getGroupDetail, getGroupMembers, updateGroupInfo, setGroupAdmin,
  kickGroupMembers, muteGroupMember, muteAllGroupMembers, updateMyGroupNick,
  transferGroupOwner, quitGroup, dissolveGroup, inviteGroupMembers, getFriends,
  type GroupDetail, type GroupMember, type Friend
} from '@/api/im'
import { ElMessage, ElMessageBox } from 'element-plus'
import ReportModal from '@/components/ReportModal.vue'

const props = defineProps<{
  visible: boolean
  groupId: string
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'quit'): void
  (e: 'dissolved'): void
}>()

// 举报弹窗
const reportVisible = ref(false)

const loading = ref(false)
const group = ref<GroupDetail | null>(null)
const allMembers = ref<GroupMember[]>([])

// 编辑状态
const editingNick = ref(false)
const newNick = ref('')
const editingNotice = ref(false)
const newNotice = ref('')
const editingName = ref(false)
const newName = ref('')
const editingDesc = ref(false)
const newDesc = ref('')

// 成员管理弹窗
const memberDialogVisible = ref(false)
const kickMode = ref(false)
const selectedMembers = ref<string[]>([])

// 邀请好友弹窗
const inviteDialogVisible = ref(false)
const inviteFriends = ref<Friend[]>([])
const selectedInvitees = ref<string[]>([])
const loadingInvite = ref(false)

// 我的权限
const isOwner = computed(() => group.value?.myRole === 2)
const isAdmin = computed(() => group.value?.myRole >= 1)

watch(() => props.visible, async (val) => {
  if (val && props.groupId) {
    await loadGroupDetail()
  }
})

async function loadGroupDetail() {
  loading.value = true
  try {
    const res = await getGroupDetail(props.groupId)
    group.value = res.data
  } catch (error: any) {
    console.error('加载群详情失败:', {
      groupId: props.groupId,
      error,
    })
    const msg = error?.message as string | undefined
    if (msg && msg.includes('群聊不存在或已解散')) {
      emit('update:visible', false)
      emit('dissolved')
    } else {
      emit('update:visible', false)
    }
  } finally {
    loading.value = false
  }
}

async function loadAllMembers() {
  const res = await getGroupMembers(props.groupId)
  allMembers.value = res.data || []
  memberDialogVisible.value = true
}

// 修改群名片
async function saveNickname() {
  if (!newNick.value.trim()) {
    editingNick.value = false
    return
  }
  
  try {
    await updateMyGroupNick({ groupId: props.groupId, nickname: newNick.value.trim() })
    if (group.value) {
      group.value.myGroupNick = newNick.value.trim()
    }
    ElMessage.success('群名片已修改')
  } finally {
    editingNick.value = false
  }
}

// 修改群公告
async function saveNotice() {
  try {
    await updateGroupInfo(props.groupId, { notice: newNotice.value })
    if (group.value) {
      group.value.notice = newNotice.value
    }
    ElMessage.success('群公告已更新')
  } finally {
    editingNotice.value = false
  }
}

// 修改群名称
async function saveGroupName() {
  if (!newName.value.trim()) {
    editingName.value = false
    return
  }
  
  try {
    await updateGroupInfo(props.groupId, { name: newName.value.trim() })
    if (group.value) {
      group.value.name = newName.value.trim()
    }
    ElMessage.success('群名称已修改')
  } finally {
    editingName.value = false
  }
}

// 修改群简介
async function saveGroupDesc() {
  try {
    await updateGroupInfo(props.groupId, { description: newDesc.value })
    if (group.value) {
      group.value.description = newDesc.value
    }
    ElMessage.success('群简介已更新')
  } finally {
    editingDesc.value = false
  }
}

// 设置/取消管理员
async function toggleAdmin(member: GroupMember) {
  const type = member.role === 1 ? 0 : 1
  const action = type === 1 ? '设为管理员' : '取消管理员'
  
  await ElMessageBox.confirm(`确定要${action}吗？`, '提示')
  await setGroupAdmin({ groupId: props.groupId, userId: member.userId, type })
  member.role = type
  ElMessage.success(`已${action}`)
}

// 踢出成员
async function confirmKick() {
  if (selectedMembers.value.length === 0) {
    ElMessage.warning('请选择要踢出的成员')
    return
  }
  
  await ElMessageBox.confirm(`确定要踢出 ${selectedMembers.value.length} 名成员吗？`, '提示')
  await kickGroupMembers({ groupId: props.groupId, memberIds: selectedMembers.value })
  
  // 更新本地数据
  allMembers.value = allMembers.value.filter(m => !selectedMembers.value.includes(m.userId))
  if (group.value) {
    group.value.memberCount -= selectedMembers.value.length
  }
  
  selectedMembers.value = []
  kickMode.value = false
  ElMessage.success('已踢出')
}

// 禁言成员
async function muteMember(member: GroupMember, duration: number) {
  await muteGroupMember({ groupId: props.groupId, memberId: member.userId, duration })
  
  if (duration > 0) {
    member.muteEndTime = new Date(Date.now() + duration * 60000).toISOString()
    member.isMuted = true
    ElMessage.success(`已禁言 ${duration} 分钟`)
  } else {
    member.muteEndTime = ''
    member.isMuted = false
    ElMessage.success('已解除禁言')
  }
}

// 全员禁言
async function toggleMuteAll() {
  const mute = group.value?.isMuteAll !== 1
  await muteAllGroupMembers(props.groupId, mute)
  if (group.value) {
    group.value.isMuteAll = mute ? 1 : 0
  }
  ElMessage.success(mute ? '已开启全员禁言' : '已关闭全员禁言')
}

// 转让群主
async function handleTransfer(member: GroupMember) {
  await ElMessageBox.confirm(
    `确定要将群主转让给 ${member.groupNick || member.nickname || member.username} 吗？\n转让后你将成为普通成员。`,
    '转让群主',
    { type: 'warning' }
  )
  
  await transferGroupOwner({ groupId: props.groupId, newOwnerId: member.userId })
  ElMessage.success('群主已转让')
  await loadGroupDetail()
}

// 退出群聊
async function handleQuit() {
  await ElMessageBox.confirm('确定要退出该群聊吗？', '退出群聊', { type: 'warning' })
  await quitGroup(props.groupId)
  ElMessage.success('已退出群聊')
  emit('update:visible', false)
  emit('quit')
}

// 解散群聊
async function handleDissolve() {
  await ElMessageBox.confirm(
    '解散群聊后，所有群成员将被移出，且无法恢复。确定要解散吗？',
    '解散群聊',
    { type: 'error', confirmButtonText: '确定解散' }
  )
  
  await dissolveGroup(props.groupId)
  ElMessage.success('群聊已解散')
  emit('update:visible', false)
  emit('dissolved')
}

function copyGroupNo() {
  if (group.value?.groupNo) {
    navigator.clipboard.writeText(group.value.groupNo)
    ElMessage.success('群号已复制')
  }
}

function getRoleText(role: number) {
  switch (role) {
    case 2: return '群主'
    case 1: return '管理员'
    default: return ''
  }
}

function getRoleType(role: number) {
  switch (role) {
    case 2: return 'danger'
    case 1: return 'warning'
    default: return 'info'
  }
}

// 打开邀请好友弹窗
async function openInviteDialog() {
  inviteDialogVisible.value = true
  selectedInvitees.value = []
  
  // 每次打开都重新加载好友列表，确保数据最新
  loadingInvite.value = true
  try {
    const res = await getFriends()
    // 过滤掉已经在群里的好友（统一转字符串比较）
    const memberIds = new Set((group.value?.members || []).map(m => String(m.userId)))
    const allFriends = res.data || []
    inviteFriends.value = allFriends.filter(f => !memberIds.has(String(f.id)))
  } catch (e: any) {
    console.error('加载好友列表失败:', e)
    ElMessage.error(e.message || '加载好友列表失败')
  } finally {
    loadingInvite.value = false
  }
}

// 邀请好友入群
async function confirmInvite() {
  if (selectedInvitees.value.length === 0) {
    ElMessage.warning('请选择要邀请的好友')
    return
  }
  
  try {
    await inviteGroupMembers({
      groupId: props.groupId,
      userIds: selectedInvitees.value
    })
    ElMessage.success(`已邀请 ${selectedInvitees.value.length} 位好友`)
    inviteDialogVisible.value = false
    selectedInvitees.value = []
    // 刷新群详情
    await loadGroupDetail()
  } catch (e: any) {
    ElMessage.error(e.message || '邀请失败')
  }
}
</script>

<template>
  <el-drawer
    :model-value="visible"
    @update:model-value="emit('update:visible', $event)"
    title="群聊设置"
    size="380px"
    class="nb-drawer"
  >
    <div v-loading="loading" class="space-y-6">
      <template v-if="group">
        <!-- 群信息 - Neo-Brutalism -->
        <div class="text-center bg-pop-blue border-3 border-black rounded-2xl p-6">
          <el-avatar :size="80" :src="group.avatar" shape="square" class="!border-3 !border-white">
            {{ group.name?.[0] }}
          </el-avatar>
          <!-- 群名称（可编辑） -->
          <template v-if="editingName">
            <div class="mt-3 flex items-center justify-center gap-2">
              <el-input v-model="newName" size="small" style="width: 150px" placeholder="群名称" />
              <el-button size="small" class="!bg-white !text-black !border-2 !border-black" @click="editingName = false">取消</el-button>
              <el-button size="small" class="!bg-pop-green !text-black !border-2 !border-black !font-bold" @click="saveGroupName">保存</el-button>
            </div>
          </template>
          <template v-else>
            <h3 
              class="text-xl font-black text-white mt-3 uppercase"
              :class="{ 'cursor-pointer hover:underline': isOwner }"
              @click="isOwner && (editingName = true, newName = group.name)"
            >
              {{ group.name }}
              <el-icon v-if="isOwner" class="text-sm ml-1"><Edit /></el-icon>
            </h3>
          </template>
          <div class="flex items-center justify-center gap-2 mt-2 bg-white border-2 border-black rounded-lg px-3 py-1 inline-flex">
            <span class="font-bold text-black">群号: {{ group.groupNo }}</span>
            <el-button link size="small" class="!text-pop-blue" @click="copyGroupNo">
              <el-icon><CopyDocument /></el-icon>
            </el-button>
          </div>
          <div class="mt-2 bg-pop-yellow border-2 border-black rounded-lg px-3 py-1 inline-block font-bold text-black">
            👥 {{ group.memberCount }}/{{ group.maxMember }} 人
          </div>
        </div>

        <!-- 群简介 - Neo-Brutalism -->
        <div class="bg-white border-3 border-black rounded-xl p-4">
          <div class="flex items-center justify-between mb-3">
            <span class="bg-pop-green border-2 border-black rounded-lg px-3 py-1 font-bold text-black">📝 群简介</span>
            <el-button v-if="isOwner && !editingDesc" size="small" class="!border-2 !border-black !font-bold" @click="editingDesc = true; newDesc = group.description || ''">
              编辑
            </el-button>
          </div>
          <template v-if="editingDesc">
            <el-input v-model="newDesc" type="textarea" :rows="2" placeholder="输入群简介" class="nb-input" />
            <div class="flex justify-end gap-2 mt-3">
              <el-button size="small" class="!border-2 !border-black" @click="editingDesc = false">取消</el-button>
              <el-button type="primary" size="small" class="!bg-pop-green !text-black !border-2 !border-black !font-bold" @click="saveGroupDesc">保存</el-button>
            </div>
          </template>
          <p v-else class="text-nb-text font-medium bg-nb-bg border-2 border-black rounded-lg p-3">
            {{ group.description || '暂无简介' }}
          </p>
        </div>

        <!-- 群公告 - Neo-Brutalism -->
        <div class="bg-white border-3 border-black rounded-xl p-4">
          <div class="flex items-center justify-between mb-3">
            <span class="bg-pop-orange border-2 border-black rounded-lg px-3 py-1 font-bold text-black">📢 群公告</span>
            <el-button v-if="isAdmin && !editingNotice" size="small" class="!border-2 !border-black !font-bold" @click="editingNotice = true; newNotice = group.notice || ''">
              编辑
            </el-button>
          </div>
          <template v-if="editingNotice">
            <el-input v-model="newNotice" type="textarea" :rows="3" placeholder="输入群公告" class="nb-input" />
            <div class="flex justify-end gap-2 mt-3">
              <el-button size="small" class="!border-2 !border-black" @click="editingNotice = false">取消</el-button>
              <el-button type="primary" size="small" class="!bg-pop-green !text-black !border-2 !border-black !font-bold" @click="saveNotice">保存</el-button>
            </div>
          </template>
          <p v-else class="text-nb-text font-medium whitespace-pre-wrap bg-nb-bg border-2 border-black rounded-lg p-3">
            {{ group.notice || '暂无公告' }}
          </p>
        </div>

        <!-- 群成员 - Neo-Brutalism -->
        <div class="bg-white border-3 border-black rounded-xl p-4">
          <div class="flex items-center justify-between mb-3">
            <span class="bg-pop-purple text-white border-2 border-black rounded-lg px-3 py-1 font-bold">👥 群成员 ({{ group.memberCount }})</span>
            <el-button size="small" class="!border-2 !border-black !font-bold" @click="loadAllMembers">查看全部</el-button>
          </div>
          <div class="flex flex-wrap gap-3">
            <div
              v-for="member in group.members"
              :key="member.userId"
              class="text-center"
            >
              <el-avatar :size="44" :src="member.avatar" class="!border-2 !border-black">
                {{ (member.groupNick || member.nickname)?.[0] }}
              </el-avatar>
              <div class="text-xs font-bold text-nb-text mt-1 w-12 truncate">
                {{ member.groupNick || member.nickname }}
              </div>
            </div>
            <!-- 邀请按钮 -->
            <div class="text-center cursor-pointer hover:scale-110 transition-transform" @click="openInviteDialog">
              <div class="w-11 h-11 rounded-full border-3 border-dashed border-pop-green bg-pop-green/10 flex items-center justify-center">
                <el-icon class="text-pop-green text-lg"><Plus /></el-icon>
              </div>
            </div>
            <!-- 踢人按钮 -->
            <div v-if="isAdmin" class="text-center cursor-pointer hover:scale-110 transition-transform" @click="loadAllMembers(); kickMode = true">
              <div class="w-11 h-11 rounded-full border-3 border-dashed border-pop-red bg-pop-red/10 flex items-center justify-center">
                <el-icon class="text-pop-red text-lg"><Minus /></el-icon>
              </div>
            </div>
          </div>
        </div>

        <!-- 我的群名片 - Neo-Brutalism -->
        <div class="bg-white border-3 border-black rounded-xl p-4">
          <div class="flex items-center justify-between">
            <span class="font-bold text-nb-text">我在本群的昵称</span>
            <template v-if="editingNick">
              <div class="flex items-center gap-2">
                <el-input v-model="newNick" size="small" style="width: 100px" />
                <el-button size="small" class="!border-2 !border-black" @click="editingNick = false">取消</el-button>
                <el-button type="primary" size="small" class="!bg-pop-green !text-black !border-2 !border-black !font-bold" @click="saveNickname">保存</el-button>
              </div>
            </template>
            <template v-else>
              <span class="font-bold text-pop-blue cursor-pointer hover:underline" @click="editingNick = true; newNick = group.myGroupNick || ''">
                {{ group.myGroupNick || '点击设置' }}
                <el-icon class="ml-1"><ArrowRight /></el-icon>
              </span>
            </template>
          </div>
        </div>

        <!-- 管理员选项 - Neo-Brutalism -->
        <template v-if="isAdmin">
          <div class="bg-pop-yellow border-3 border-black rounded-xl px-4 py-2 text-center">
            <span class="font-black text-black uppercase flex items-center justify-center">
              <el-icon class="mr-2"><Setting /></el-icon>管理员功能
            </span>
          </div>
          
          <div class="bg-white border-3 border-black rounded-xl p-4">
            <div class="flex items-center justify-between">
              <span class="font-bold text-nb-text">全员禁言</span>
              <el-switch
                :model-value="group.isMuteAll === 1"
                @change="toggleMuteAll"
              />
            </div>
          </div>
        </template>

        <!-- 底部操作 - Neo-Brutalism -->
        <div class="pt-4 space-y-3">
          <el-button
            v-if="isOwner"
            class="w-full !bg-red-500 !text-white !border-3 !border-black !font-black !shadow-brutal-sm hover:!translate-x-0.5 hover:!-translate-y-0.5 transition-all"
            @click="handleDissolve"
          >
            <el-icon class="mr-2"><Delete /></el-icon>解散群聊
          </el-button>
          <el-button
            v-else
            class="w-full !bg-red-500 !text-white !border-3 !border-black !font-black !shadow-brutal-sm hover:!translate-x-0.5 hover:!-translate-y-0.5 transition-all"
            @click="handleQuit"
          >
            <el-icon class="mr-2"><SwitchButton /></el-icon>退出群聊
          </el-button>
          <el-button
            class="w-full !bg-orange-400 !text-black !border-3 !border-black !font-bold"
            @click="reportVisible = true"
          >
            <el-icon class="mr-2"><Warning /></el-icon>举报该群
          </el-button>
        </div>
      </template>
    </div>
    
    <!-- 举报弹窗 -->
    <ReportModal
      v-model:visible="reportVisible"
      :target-id="groupId"
      :target-type="2"
      :target-name="group?.name"
    />

    <!-- 成员管理弹窗 - Neo-Brutalism -->
    <el-dialog
      v-model="memberDialogVisible"
      :title="kickMode ? '选择要踢出的成员' : '群成员'"
      width="420px"
      append-to-body
      class="nb-dialog"
    >
      <div class="max-h-96 overflow-y-auto space-y-2">
        <div
          v-for="member in allMembers"
          :key="member.userId"
          class="flex items-center gap-3 p-3 rounded-xl border-2 border-transparent hover:border-black hover:bg-nb-bg transition-all"
        >
          <!-- 踢人模式的复选框 -->
          <el-checkbox
            v-if="kickMode && member.role < (group?.myRole || 0)"
            v-model="selectedMembers"
            :value="member.userId"
          />
          
          <el-avatar :size="44" :src="member.avatar" class="!border-2 !border-black">
            {{ (member.groupNick || member.nickname)?.[0] }}
          </el-avatar>
          
          <div class="flex-1 min-w-0">
            <div class="flex items-center gap-2">
              <span class="font-bold text-nb-text">{{ member.groupNick || member.nickname }}</span>
              <span v-if="member.role > 0" class="text-xs font-bold px-2 py-0.5 rounded border-2 border-black" :class="member.role === 2 ? 'bg-pop-red text-white' : 'bg-pop-orange text-black'">
                {{ getRoleText(member.role) }}
              </span>
              <span v-if="member.isMuted" class="text-xs font-bold px-2 py-0.5 rounded border-2 border-black bg-gray-200">🔇 禁言中</span>
            </div>
          </div>

          <!-- 操作下拉菜单 -->
          <el-dropdown v-if="isAdmin && member.role < (group?.myRole || 0) && !kickMode" trigger="click">
            <el-button circle size="small" class="!border-2 !border-black">
              <el-icon><MoreFilled /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu class="!border-3 !border-black !shadow-brutal !rounded-xl">
                <el-dropdown-item v-if="isOwner" @click="toggleAdmin(member)">
                  {{ member.role === 1 ? '取消管理员' : '设为管理员' }}
                </el-dropdown-item>
                <el-dropdown-item v-if="isOwner" @click="handleTransfer(member)">
                  转让群主
                </el-dropdown-item>
                <el-dropdown-item divided>
                  <el-dropdown trigger="hover" placement="right-start">
                    <span>禁言</span>
                    <template #dropdown>
                      <el-dropdown-menu class="!border-2 !border-black !rounded-lg">
                        <el-dropdown-item @click="muteMember(member, 10)">10分钟</el-dropdown-item>
                        <el-dropdown-item @click="muteMember(member, 60)">1小时</el-dropdown-item>
                        <el-dropdown-item @click="muteMember(member, 1440)">1天</el-dropdown-item>
                        <el-dropdown-item v-if="member.isMuted" divided @click="muteMember(member, 0)">
                          解除禁言
                        </el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
      
      <template v-if="kickMode" #footer>
        <el-button class="!border-2 !border-black !font-bold" @click="kickMode = false; selectedMembers = []">取消</el-button>
        <el-button class="!bg-pop-red !text-white !border-2 !border-black !font-bold" @click="confirmKick">
          踢出 ({{ selectedMembers.length }})
        </el-button>
      </template>
    </el-dialog>

    <!-- 邀请好友弹窗 - Neo-Brutalism -->
    <el-dialog
      v-model="inviteDialogVisible"
      title="邀请好友入群"
      width="420px"
      append-to-body
      class="nb-dialog"
    >
      <div v-loading="loadingInvite" class="max-h-80 overflow-y-auto">
        <div v-if="inviteFriends.length === 0 && !loadingInvite" class="text-center py-8">
          <div class="text-5xl mb-4">👥</div>
          <div class="nb-badge">暂无可邀请的好友</div>
        </div>
        <el-checkbox-group v-model="selectedInvitees">
          <div class="space-y-2">
            <div
              v-for="friend in inviteFriends"
              :key="friend.id"
              class="flex items-center gap-3 p-3 rounded-xl border-2 border-transparent hover:border-black hover:bg-pop-green/10 transition-all"
            >
              <el-checkbox :value="String(friend.id)" />
              <el-avatar :size="40" :src="friend.avatar" class="!border-2 !border-black">{{ friend.nickname?.[0] }}</el-avatar>
              <span class="font-bold text-nb-text">{{ friend.remark || friend.nickname }}</span>
            </div>
          </div>
        </el-checkbox-group>
      </div>
      <template #footer>
        <el-button class="!border-2 !border-black !font-bold" @click="inviteDialogVisible = false">取消</el-button>
        <el-button class="!bg-pop-green !text-black !border-2 !border-black !font-bold" :disabled="selectedInvitees.length === 0" @click="confirmInvite">
          ✅ 邀请 ({{ selectedInvitees.length }})
        </el-button>
      </template>
    </el-dialog>
  </el-drawer>
</template>
