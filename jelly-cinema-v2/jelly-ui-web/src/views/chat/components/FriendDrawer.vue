<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { setFriendRemark, blockFriend, unblockFriend, deleteFriend, type Friend } from '@/api/im'
import { ElMessage, ElMessageBox } from 'element-plus'
import ReportModal from '@/components/ReportModal.vue'

const props = defineProps<{
  visible: boolean
  friend: Friend | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'deleted'): void
  (e: 'updated', friend: Friend): void
}>()

// 举报弹窗
const reportVisible = ref(false)

// 编辑备注
const editingRemark = ref(false)
const newRemark = ref('')

// 本地好友数据
const localFriend = ref<Friend | null>(null)

watch(() => props.visible, (val) => {
  if (val && props.friend) {
    localFriend.value = { ...props.friend }
    newRemark.value = props.friend.remark || ''
  }
})

// 保存备注
async function saveRemark() {
  if (!localFriend.value) return
  
  try {
    await setFriendRemark({
      friendId: String(localFriend.value.id),
      remark: newRemark.value.trim()
    })
    localFriend.value.remark = newRemark.value.trim()
    emit('updated', localFriend.value)
    ElMessage.success('备注已保存')
  } finally {
    editingRemark.value = false
  }
}

// 拉黑/解除拉黑
async function toggleBlock() {
  if (!localFriend.value) return
  
  const isBlocked = localFriend.value.status === 1
  const action = isBlocked ? '解除拉黑' : '拉黑'
  
  await ElMessageBox.confirm(
    isBlocked 
      ? `确定要解除对"${localFriend.value.remark || localFriend.value.nickname}"的拉黑吗？`
      : `确定要拉黑"${localFriend.value.remark || localFriend.value.nickname}"吗？拉黑后将无法收到对方消息。`,
    action,
    { type: 'warning' }
  )
  
  if (isBlocked) {
    await unblockFriend(String(localFriend.value.id))
    localFriend.value.status = 0
  } else {
    await blockFriend(String(localFriend.value.id))
    localFriend.value.status = 1
  }
  
  emit('updated', localFriend.value)
  ElMessage.success(`已${action}`)
}

// 删除好友
async function handleDelete() {
  if (!localFriend.value) return
  
  try {
    await ElMessageBox.confirm(
      `确定要删除好友"${localFriend.value.remark || localFriend.value.nickname}"吗？`,
      '删除好友',
      {
        type: 'warning',
        distinguishCancelAndClose: true,
        confirmButtonText: '删除并保留聊天记录',
        cancelButtonText: '删除并清空聊天记录',
      }
    )
    // 点击确定：删除好友但保留消息
    await deleteFriend(String(localFriend.value.id), true)
    emit('update:visible', false)
    emit('deleted')
    ElMessage.success('已删除好友')
  } catch (action) {
    if (action === 'cancel') {
      // 点击取消：删除好友并清空消息
      await deleteFriend(String(localFriend.value.id), false)
      emit('update:visible', false)
      emit('deleted')
      ElMessage.success('已删除好友和聊天记录')
    }
  }
}

function copyId() {
  if (localFriend.value) {
    navigator.clipboard.writeText(String(localFriend.value.id))
    ElMessage.success('ID已复制')
  }
}
</script>

<template>
  <el-drawer
    :model-value="visible"
    @update:model-value="emit('update:visible', $event)"
    title="好友资料"
    size="360px"
    class="nb-drawer"
  >
    <div v-if="localFriend" class="space-y-6">
      <!-- 好友信息 - Neo-Brutalism -->
      <div class="text-center bg-pop-blue border-3 border-black rounded-2xl p-6">
        <el-avatar :size="80" :src="localFriend.avatar" class="!border-3 !border-white">
          {{ localFriend.nickname?.[0] }}
        </el-avatar>
        <h3 class="text-xl font-black text-white mt-3">{{ localFriend.nickname }}</h3>
        <p v-if="localFriend.username" class="text-white/80 text-sm font-medium">@{{ localFriend.username }}</p>
        <div class="flex items-center justify-center gap-2 mt-2 bg-white border-2 border-black rounded-lg px-3 py-1 inline-flex">
          <span class="font-bold text-black text-sm">ID: {{ localFriend.id }}</span>
          <el-button link size="small" class="!text-pop-blue" @click="copyId">
            <el-icon><CopyDocument /></el-icon>
          </el-button>
        </div>
        <div v-if="localFriend.status === 1" class="mt-2 bg-pop-red text-white border-2 border-black rounded-lg px-3 py-1 inline-block font-bold text-sm">
          ⛔ 已拉黑
        </div>
      </div>

      <!-- 个性签名 -->
      <div v-if="localFriend.signature" class="bg-white border-3 border-black rounded-xl p-4">
        <div class="flex items-center mb-2">
          <span class="bg-pop-purple text-white border-2 border-black rounded-lg px-3 py-1 font-bold text-sm">✍️ 个性签名</span>
        </div>
        <p class="text-nb-text font-medium bg-nb-bg border-2 border-black rounded-lg p-3">
          {{ localFriend.signature }}
        </p>
      </div>

      <!-- 备注设置 -->
      <div class="bg-white border-3 border-black rounded-xl p-4">
        <div class="flex items-center justify-between">
          <span class="font-bold text-nb-text">好友备注</span>
          <template v-if="editingRemark">
            <div class="flex items-center gap-2">
              <el-input v-model="newRemark" size="small" placeholder="设置备注" style="width: 120px" />
              <el-button size="small" class="!border-2 !border-black" @click="editingRemark = false">取消</el-button>
              <el-button type="primary" size="small" class="!bg-pop-green !text-black !border-2 !border-black !font-bold" @click="saveRemark">保存</el-button>
            </div>
          </template>
          <template v-else>
            <span class="font-bold text-pop-blue cursor-pointer hover:underline" @click="editingRemark = true; newRemark = localFriend.remark || ''">
              {{ localFriend.remark || '点击设置' }}
              <el-icon class="ml-1"><ArrowRight /></el-icon>
            </span>
          </template>
        </div>
      </div>

      <!-- 消息设置 -->
      <div class="bg-white border-3 border-black rounded-xl p-4 space-y-4">
        <div class="flex items-center justify-between">
          <span class="font-bold text-nb-text">消息免打扰</span>
          <el-switch />
        </div>
        <div class="flex items-center justify-between">
          <span class="font-bold text-nb-text">置顶聊天</span>
          <el-switch />
        </div>
      </div>

      <!-- 底部操作 -->
      <div class="pt-4 space-y-3">
        <el-button
          class="w-full !border-3 !border-black !font-bold"
          :class="localFriend.status === 1 ? '!bg-pop-green !text-black' : '!bg-gray-100 !text-black'"
          @click="toggleBlock"
        >
          <el-icon class="mr-2"><Hide /></el-icon>
          {{ localFriend.status === 1 ? '解除拉黑' : '拉黑好友' }}
        </el-button>
        <el-button
          class="w-full !bg-pop-red !text-white !border-3 !border-black !font-black"
          @click="handleDelete"
        >
          <el-icon class="mr-2"><Delete /></el-icon>删除好友
        </el-button>
        <el-button
          class="w-full !bg-orange-400 !text-black !border-3 !border-black !font-bold"
          @click="reportVisible = true"
        >
          <el-icon class="mr-2"><Warning /></el-icon>举报该用户
        </el-button>
      </div>
    </div>

    <!-- 举报弹窗 -->
    <ReportModal
      v-if="localFriend"
      v-model:visible="reportVisible"
      :target-id="String(localFriend.id)"
      :target-type="1"
      :target-name="localFriend.remark || localFriend.nickname"
    />
  </el-drawer>
</template>
