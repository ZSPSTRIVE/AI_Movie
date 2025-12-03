<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { getApplyList, handleApply, type ApplyRecord } from '@/api/im'
import { ElMessage, ElMessageBox } from 'element-plus'

const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'refresh'): void
}>()

const activeTab = ref<'all' | 'friend' | 'group'>('all')
const loading = ref(false)
const applyList = ref<ApplyRecord[]>([])

// 分组显示
const friendApplies = computed(() => applyList.value.filter(a => a.type === 1))
const groupApplies = computed(() => applyList.value.filter(a => a.type === 2))
const displayList = computed(() => {
  if (activeTab.value === 'friend') return friendApplies.value
  if (activeTab.value === 'group') return groupApplies.value
  return applyList.value
})

onMounted(() => {
  loadApplyList()
})

async function loadApplyList() {
  loading.value = true
  try {
    const type = activeTab.value === 'friend' ? 1 : activeTab.value === 'group' ? 2 : undefined
    console.log('加载申请列表, type:', type)
    const res = await getApplyList(type)
    console.log('申请列表结果:', res.data)
    applyList.value = res.data || []
  } catch (e) {
    console.error('加载申请列表失败:', e)
  } finally {
    loading.value = false
  }
}

async function handleAccept(apply: ApplyRecord) {
  try {
    await handleApply({ applyId: apply.id, status: 1 })
    apply.status = 1
    ElMessage.success(apply.type === 1 ? '已添加好友' : '已同意入群')
    emit('refresh')
  } catch (e) {
    // handled by interceptor
  }
}

async function handleReject(apply: ApplyRecord) {
  try {
    await ElMessageBox.confirm('确定要拒绝该申请吗？', '提示', {
      confirmButtonText: '拒绝',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    await handleApply({ applyId: apply.id, status: 2 })
    apply.status = 2
    ElMessage.success('已拒绝')
  } catch (e) {
    // cancelled or error
  }
}

function formatTime(time: string): string {
  const date = new Date(time)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
  if (diff < 604800000) return Math.floor(diff / 86400000) + '天前'
  return date.toLocaleDateString()
}

function getStatusText(status: number) {
  switch (status) {
    case 0: return ''
    case 1: return '已添加'
    case 2: return '已拒绝'
    case 3: return '已忽略'
    default: return ''
  }
}

function getStatusType(status: number) {
  switch (status) {
    case 1: return 'success'
    case 2: return 'danger'
    case 3: return 'info'
    default: return 'info'
  }
}
</script>

<template>
  <el-drawer
    :model-value="visible"
    @update:model-value="emit('update:visible', $event)"
    title="新的朋友"
    size="380px"
  >
    <!-- Tab 切换 -->
    <el-radio-group v-model="activeTab" class="mb-4 w-full" @change="loadApplyList">
      <el-radio-button value="all" class="flex-1">全部</el-radio-button>
      <el-radio-button value="friend" class="flex-1">好友通知</el-radio-button>
      <el-radio-button value="group" class="flex-1">群通知</el-radio-button>
    </el-radio-group>

    <!-- 申请列表 -->
    <div v-loading="loading" class="space-y-3">
      <el-empty v-if="displayList.length === 0 && !loading" description="暂无通知" :image-size="80" />
      
      <div
        v-for="apply in displayList"
        :key="apply.id"
        class="flex items-start gap-3 p-3 bg-dark-card rounded-lg"
      >
        <!-- 头像 -->
        <el-avatar :size="44" :src="apply.fromAvatar">
          {{ apply.fromNickname?.[0] }}
        </el-avatar>

        <!-- 内容 -->
        <div class="flex-1 min-w-0">
          <div class="flex items-center gap-2">
            <span class="font-medium text-white">{{ apply.fromNickname }}</span>
            <el-tag v-if="apply.type === 2" size="small" type="warning">入群申请</el-tag>
          </div>
          
          <div class="text-sm text-gray-400 mt-1">
            <template v-if="apply.type === 1">
              申请加为好友
            </template>
            <template v-else>
              申请加入 <span class="text-primary">{{ apply.targetName }}</span>
            </template>
          </div>
          
          <div v-if="apply.reason" class="text-sm text-gray-500 mt-1 truncate">
            {{ apply.reason }}
          </div>
          
          <div class="text-xs text-gray-600 mt-2">{{ formatTime(apply.createTime) }}</div>
        </div>

        <!-- 操作按钮 -->
        <div class="flex flex-col gap-1">
          <template v-if="apply.status === 0">
            <el-button type="primary" size="small" @click="handleAccept(apply)">
              接受
            </el-button>
            <el-button size="small" text @click="handleReject(apply)">
              拒绝
            </el-button>
          </template>
          <template v-else>
            <el-tag :type="getStatusType(apply.status)" size="small">
              {{ getStatusText(apply.status) }}
            </el-tag>
          </template>
        </div>
      </div>
    </div>
  </el-drawer>
</template>

<style scoped>
:deep(.el-radio-group) {
  display: flex;
}
:deep(.el-radio-button) {
  flex: 1;
}
:deep(.el-radio-button__inner) {
  width: 100%;
}
</style>
