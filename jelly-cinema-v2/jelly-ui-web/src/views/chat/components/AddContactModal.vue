<script setup lang="ts">
import { ref, watch } from 'vue'
import { searchUser, searchGroup, applyFriend, applyGroup, type UserSearchResult, type GroupSearchResult } from '@/api/im'
import { ElMessage } from 'element-plus'

const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'success'): void
}>()

const activeTab = ref<'user' | 'group'>('user')
const keyword = ref('')
const loading = ref(false)
const userResults = ref<UserSearchResult[]>([])
const groupResults = ref<GroupSearchResult[]>([])

// 申请弹窗
const applyDialogVisible = ref(false)
const applyTarget = ref<UserSearchResult | GroupSearchResult | null>(null)
const applyForm = ref({
  reason: '',
  remark: ''
})
const submitting = ref(false)

watch(() => props.visible, (val) => {
  if (!val) {
    keyword.value = ''
    userResults.value = []
    groupResults.value = []
  }
})

async function handleSearch() {
  if (!keyword.value.trim()) {
    ElMessage.warning('请输入搜索关键词')
    return
  }

  if (!navigator.onLine) {
    ElMessage.error('网络已断开，请检查网络后重试')
    return
  }

  loading.value = true
  try {
    if (activeTab.value === 'user') {
      const res = await searchUser(keyword.value.trim())
      userResults.value = res.data || []
    } else {
      const res = await searchGroup(keyword.value.trim())
      groupResults.value = res.data || []
    }
  } catch (e: any) {
    console.error('搜索失败:', e)
    ElMessage.error(e?.message || '搜索失败，请检查网络或服务状态')
  } finally {
    loading.value = false
  }
}

function openApplyDialog(target: UserSearchResult | GroupSearchResult) {
  applyTarget.value = target
  applyForm.value = { reason: '', remark: '' }
  applyDialogVisible.value = true
}

async function submitApply() {
  if (!applyTarget.value) return

  if (!navigator.onLine) {
    ElMessage.error('网络已断开，请检查网络后重试')
    return
  }

  submitting.value = true
  try {
    if (activeTab.value === 'user') {
      console.log('发送好友申请:', {
        targetId: applyTarget.value.id,
        reason: applyForm.value.reason,
        remark: applyForm.value.remark
      })
      await applyFriend({
        targetId: applyTarget.value.id,
        reason: applyForm.value.reason,
        remark: applyForm.value.remark
      })
      console.log('好友申请发送成功')
      ElMessage.success('好友申请已发送')
    } else {
      await applyGroup({
        groupId: applyTarget.value.id,
        reason: applyForm.value.reason
      })
      const group = applyTarget.value as GroupSearchResult
      if (group.joinType === 0) {
        ElMessage.success('已加入群聊')
      } else {
        ElMessage.success('入群申请已发送')
      }
    }
    applyDialogVisible.value = false
    emit('success')
  } catch (e: any) {
    console.error('申请失败:', e)
    ElMessage.error(e?.message || '操作失败，请检查网络或服务状态')
  } finally {
    submitting.value = false
  }
}

function close() {
  emit('update:visible', false)
}

function getJoinTypeText(type: number) {
  switch (type) {
    case 0: return '自由加入'
    case 1: return '需要验证'
    case 2: return '禁止加入'
    default: return ''
  }
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    @update:model-value="emit('update:visible', $event)"
    title="添加好友/群聊"
    width="560px"
    :close-on-click-modal="false"
  >
    <!-- Tab 切换 -->
    <el-tabs v-model="activeTab" class="mb-4">
      <el-tab-pane label="找人" name="user">
        <template #label>
          <el-icon class="mr-1"><User /></el-icon>
          找人
        </template>
      </el-tab-pane>
      <el-tab-pane label="找群" name="group">
        <template #label>
          <el-icon class="mr-1"><ChatDotRound /></el-icon>
          找群
        </template>
      </el-tab-pane>
    </el-tabs>

    <!-- 搜索框 -->
    <div class="flex gap-2 mb-4">
      <el-input
        v-model="keyword"
        :placeholder="activeTab === 'user' ? '输入用户ID或昵称搜索' : '输入群号或群名搜索'"
        clearable
        @keyup.enter="handleSearch"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <el-button type="primary" :loading="loading" @click="handleSearch">搜索</el-button>
    </div>

    <!-- 搜索结果 - 用户 -->
    <div v-if="activeTab === 'user'" class="max-h-80 overflow-y-auto">
      <el-empty v-if="userResults.length === 0 && !loading" description="搜索用户试试" :image-size="80" />
      
      <div
        v-for="user in userResults"
        :key="user.id"
        class="flex items-center gap-3 p-3 rounded-lg hover:bg-dark-card transition-colors"
      >
        <el-avatar :size="48" :src="user.avatar">{{ user.nickname?.[0] || user.username?.[0] }}</el-avatar>
        <div class="flex-1 min-w-0">
          <div class="font-medium text-white">{{ user.nickname || user.username }}</div>
          <div class="text-sm text-gray-400 truncate">{{ user.signature || '暂无签名' }}</div>
        </div>
        <el-button
          v-if="user.isFriend"
          type="info"
          size="small"
          disabled
        >
          已是好友
        </el-button>
        <el-button
          v-else
          type="primary"
          size="small"
          @click="openApplyDialog(user)"
        >
          添加
        </el-button>
      </div>
    </div>

    <!-- 搜索结果 - 群组 -->
    <div v-else class="max-h-80 overflow-y-auto">
      <el-empty v-if="groupResults.length === 0 && !loading" description="搜索群聊试试" :image-size="80" />
      
      <div
        v-for="group in groupResults"
        :key="group.id"
        class="flex items-center gap-3 p-3 rounded-lg hover:bg-dark-card transition-colors"
      >
        <el-avatar :size="48" :src="group.avatar" shape="square">
          {{ group.name?.[0] }}
        </el-avatar>
        <div class="flex-1 min-w-0">
          <div class="flex items-center gap-2">
            <span class="font-medium text-white">{{ group.name }}</span>
            <el-tag size="small" type="info">{{ group.memberCount }}/{{ group.maxMember }}</el-tag>
          </div>
          <div class="text-sm text-gray-400 truncate">
            群号: {{ group.groupNo }} · {{ getJoinTypeText(group.joinType) }}
          </div>
          <div v-if="group.description" class="text-sm text-gray-500 truncate mt-1">
            {{ group.description }}
          </div>
        </div>
        <el-button
          v-if="group.isJoined"
          type="info"
          size="small"
          disabled
        >
          已加入
        </el-button>
        <el-button
          v-else-if="group.joinType === 2"
          type="info"
          size="small"
          disabled
        >
          禁止加入
        </el-button>
        <el-button
          v-else
          type="primary"
          size="small"
          @click="openApplyDialog(group)"
        >
          {{ group.joinType === 0 ? '加入' : '申请' }}
        </el-button>
      </div>
    </div>

    <!-- 申请弹窗 -->
    <el-dialog
      v-model="applyDialogVisible"
      :title="activeTab === 'user' ? '添加好友' : '申请加入群聊'"
      width="400px"
      append-to-body
    >
      <el-form :model="applyForm" label-width="80px">
        <el-form-item label="验证信息">
          <el-input
            v-model="applyForm.reason"
            type="textarea"
            :rows="3"
            :placeholder="activeTab === 'user' ? '请输入验证信息，如：我是xxx群的Tom' : '请输入申请理由'"
            maxlength="100"
            show-word-limit
          />
        </el-form-item>
        <el-form-item v-if="activeTab === 'user'" label="备注名">
          <el-input
            v-model="applyForm.remark"
            placeholder="给好友设置备注名（可选）"
            maxlength="20"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="applyDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitApply">
          {{ activeTab === 'user' ? '发送申请' : '提交申请' }}
        </el-button>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<style scoped>
:deep(.el-tabs__nav-wrap::after) {
  display: none;
}
</style>
