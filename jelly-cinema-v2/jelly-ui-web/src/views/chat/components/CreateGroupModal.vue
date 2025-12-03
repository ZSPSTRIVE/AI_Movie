<script setup lang="ts">
import { ref, watch } from 'vue'
import { createGroup, getFriends, type Friend } from '@/api/im'
import { ElMessage } from 'element-plus'

const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'success'): void
}>()

const loading = ref(false)
const loadingFriends = ref(false)
const groupName = ref('')
const selectedFriends = ref<string[]>([])

// 好友列表
const friends = ref<Friend[]>([])

// 当弹窗打开时加载好友列表
watch(() => props.visible, async (val) => {
  if (val && friends.value.length === 0) {
    await loadFriends()
  }
})

async function loadFriends() {
  loadingFriends.value = true
  try {
    const res = await getFriends()
    friends.value = res.data || []
  } catch (e) {
    console.error('加载好友列表失败', e)
  } finally {
    loadingFriends.value = false
  }
}

async function handleCreate() {
  if (!groupName.value.trim()) {
    ElMessage.warning('请输入群名称')
    return
  }
  
  try {
    loading.value = true
    await createGroup({
      name: groupName.value.trim(),
      memberIds: selectedFriends.value
    })
    
    emit('success')
    handleClose()
  } catch (error: any) {
    ElMessage.error(error.message || '创建失败')
  } finally {
    loading.value = false
  }
}

function handleClose() {
  emit('update:visible', false)
  groupName.value = ''
  selectedFriends.value = []
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    @update:model-value="emit('update:visible', $event)"
    title="创建群聊"
    width="420px"
    @close="handleClose"
  >
    <el-form label-width="80px">
      <el-form-item label="群名称" required>
        <el-input
          v-model="groupName"
          placeholder="请输入群名称"
          maxlength="20"
          show-word-limit
        />
      </el-form-item>
      
      <el-form-item label="邀请好友">
        <div class="text-sm text-gray-400 mb-2">
          创建后可在群设置中邀请更多好友
        </div>
        <div v-loading="loadingFriends">
          <el-checkbox-group v-model="selectedFriends">
            <div class="space-y-2 max-h-48 overflow-y-auto">
              <div
                v-for="friend in friends"
                :key="friend.id"
                class="flex items-center gap-2 p-2 rounded hover:bg-dark-card"
              >
                <el-checkbox :value="String(friend.id)" />
                <el-avatar :size="32" :src="friend.avatar">{{ friend.nickname?.[0] }}</el-avatar>
                <span class="text-white">{{ friend.remark || friend.nickname }}</span>
              </div>
              <el-empty v-if="friends.length === 0 && !loadingFriends" description="暂无好友" :image-size="40" />
            </div>
          </el-checkbox-group>
        </div>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleCreate">
        创建群聊
      </el-button>
    </template>
  </el-dialog>
</template>
