<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { submitReport } from '@/api/im'

const props = defineProps<{
  visible: boolean
  targetId: string
  targetType: number // 1-用户 2-群组 3-消息 4-帖子
  targetName?: string
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'success'): void
}>()

const loading = ref(false)
const reason = ref('')
const selectedReason = ref('')

const reasonOptions = [
  '发布违规内容',
  '骚扰/辱骂他人',
  '传播垃圾信息',
  '冒充他人身份',
  '欺诈行为',
  '其他原因'
]

const targetTypeText = computed(() => {
  switch (props.targetType) {
    case 1: return '用户'
    case 2: return '群组'
    case 3: return '消息'
    case 4: return '帖子'
    default: return '内容'
  }
})

function handleReasonSelect(r: string) {
  selectedReason.value = r
  if (r !== '其他原因') {
    reason.value = r
  }
}

async function handleSubmit() {
  if (!reason.value.trim()) {
    ElMessage.warning('请填写举报原因')
    return
  }
  
  try {
    loading.value = true
    await submitReport({
      targetId: props.targetId,
      targetType: props.targetType,
      reason: reason.value
    })
    ElMessage.success('举报已提交，我们会尽快处理')
    emit('update:visible', false)
    emit('success')
    reason.value = ''
    selectedReason.value = ''
  } catch (error: any) {
    ElMessage.error(error.message || '提交失败')
  } finally {
    loading.value = false
  }
}

function handleClose() {
  emit('update:visible', false)
  reason.value = ''
  selectedReason.value = ''
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    @update:model-value="emit('update:visible', $event)"
    title="举报"
    width="420px"
    @close="handleClose"
  >
    <div class="space-y-4">
      <div class="text-gray-400 text-sm">
        举报{{ targetTypeText }}：<span class="text-white">{{ targetName || targetId }}</span>
      </div>

      <div>
        <div class="text-sm text-gray-400 mb-2">选择举报原因</div>
        <div class="flex flex-wrap gap-2">
          <el-tag
            v-for="r in reasonOptions"
            :key="r"
            :type="selectedReason === r ? 'primary' : 'info'"
            class="cursor-pointer"
            @click="handleReasonSelect(r)"
          >
            {{ r }}
          </el-tag>
        </div>
      </div>

      <el-input
        v-model="reason"
        type="textarea"
        :rows="3"
        placeholder="请详细描述举报原因..."
        :disabled="selectedReason !== '' && selectedReason !== '其他原因'"
      />
    </div>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="danger" :loading="loading" @click="handleSubmit">
        提交举报
      </el-button>
    </template>
  </el-dialog>
</template>
