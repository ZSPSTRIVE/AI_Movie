<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getReportList, handleReport, type ReportItem } from '@/api/admin'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const reports = ref<ReportItem[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(12)
const statusFilter = ref<number>(0) // 默认显示待处理
const targetTypeFilter = ref<number | undefined>()

// 处理弹窗
const handleDialogVisible = ref(false)
const currentReport = ref<ReportItem | null>(null)
const handleForm = ref({ action: 1, feedback: '' })

// 图片预览
const previewVisible = ref(false)
const previewUrl = ref('')

const targetTypeOptions = [
  { value: 1, label: '用户', icon: 'User' },
  { value: 2, label: '群组', icon: 'ChatDotRound' },
  { value: 3, label: '消息', icon: 'Message' },
  { value: 4, label: '帖子', icon: 'Document' }
]

const statusOptions = [
  { value: 0, label: '待处理', type: 'warning' },
  { value: 1, label: '已处理', type: 'success' },
  { value: 2, label: '已忽略', type: 'info' }
]

onMounted(() => {
  loadData()
})

async function loadData() {
  loading.value = true
  try {
    const res = await getReportList({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      status: statusFilter.value,
      targetType: targetTypeFilter.value
    })
    reports.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

function getTargetTypeLabel(type: number) {
  return targetTypeOptions.find(t => t.value === type)?.label || '未知'
}

function getStatusType(status: number) {
  return statusOptions.find(s => s.value === status)?.type || 'info'
}

function getStatusLabel(status: number) {
  return statusOptions.find(s => s.value === status)?.label || '未知'
}

function openHandleDialog(report: ReportItem) {
  currentReport.value = report
  handleForm.value = { action: 1, feedback: '' }
  handleDialogVisible.value = true
}

async function confirmHandle() {
  if (!currentReport.value) return
  
  if (handleForm.value.action === 3) {
    await ElMessageBox.confirm('封禁操作将禁止被举报方登录，确定要执行吗？', '警告', { type: 'warning' })
  }
  
  await handleReport({
    id: currentReport.value.id,
    action: handleForm.value.action,
    feedback: handleForm.value.feedback
  })
  
  ElMessage.success('处理成功')
  handleDialogVisible.value = false
  loadData()
}

async function quickIgnore(report: ReportItem) {
  await ElMessageBox.confirm('确定要忽略该举报吗？', '提示')
  await handleReport({ id: report.id, action: 1, feedback: '忽略' })
  ElMessage.success('已忽略')
  loadData()
}

function previewImage(url: string) {
  previewUrl.value = url
  previewVisible.value = true
}

function formatTime(time: string) {
  return time ? new Date(time).toLocaleString() : '-'
}
</script>

<template>
  <div class="p-6">
    <!-- 筛选栏 -->
    <div class="flex gap-4 mb-4">
      <el-radio-group v-model="statusFilter" @change="loadData">
        <el-radio-button :value="0">待处理</el-radio-button>
        <el-radio-button :value="1">已处理</el-radio-button>
        <el-radio-button :value="2">已忽略</el-radio-button>
      </el-radio-group>
      <el-select v-model="targetTypeFilter" placeholder="举报类型" clearable style="width: 120px" @change="loadData">
        <el-option v-for="t in targetTypeOptions" :key="t.value" :label="t.label" :value="t.value" />
      </el-select>
    </div>

    <!-- 举报卡片列表 -->
    <div v-loading="loading" class="grid grid-cols-3 gap-4">
      <el-empty v-if="reports.length === 0 && !loading" description="暂无举报" />
      
      <div v-for="report in reports" :key="report.id" class="report-card">
        <!-- 顶部信息 -->
        <div class="flex items-center justify-between mb-3">
          <div class="flex items-center gap-2">
            <el-avatar :size="32" :src="report.reporterAvatar">{{ report.reporterNickname?.[0] }}</el-avatar>
            <div>
              <div class="text-sm font-medium">{{ report.reporterNickname }}</div>
              <div class="text-xs text-gray-500">{{ formatTime(report.createTime) }}</div>
            </div>
          </div>
          <el-tag :type="getStatusType(report.status)" size="small">{{ getStatusLabel(report.status) }}</el-tag>
        </div>

        <!-- 被举报对象 -->
        <div class="bg-dark-bg rounded p-3 mb-3">
          <div class="flex items-center gap-2 mb-2">
            <el-tag type="info" size="small">{{ getTargetTypeLabel(report.targetType) }}</el-tag>
            <span class="text-sm">{{ report.targetName }}</span>
          </div>
          <div class="text-sm text-gray-400">
            <span class="text-warning">{{ report.reason }}</span>
          </div>
          <div v-if="report.description" class="text-sm text-gray-500 mt-1 line-clamp-2">
            {{ report.description }}
          </div>
        </div>

        <!-- 证据图片 -->
        <div v-if="report.evidenceImgs?.length" class="flex gap-2 mb-3 overflow-x-auto">
          <img
            v-for="(img, idx) in report.evidenceImgs"
            :key="idx"
            :src="img"
            class="w-16 h-16 object-cover rounded cursor-pointer hover:opacity-80"
            @click="previewImage(img)"
          />
        </div>

        <!-- 处理结果 -->
        <div v-if="report.status !== 0" class="text-sm text-gray-400 mb-3">
          处理结果: {{ report.result }}
        </div>

        <!-- 操作按钮 -->
        <div v-if="report.status === 0" class="flex gap-2">
          <el-button type="primary" size="small" @click="openHandleDialog(report)">处理</el-button>
          <el-button size="small" @click="quickIgnore(report)">忽略</el-button>
        </div>
      </div>
    </div>

    <!-- 分页 -->
    <div class="flex justify-center mt-6">
      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[12, 24, 48]"
        layout="total, sizes, prev, pager, next"
        @change="loadData"
      />
    </div>

    <!-- 处理弹窗 -->
    <el-dialog v-model="handleDialogVisible" title="处理举报" width="450px">
      <template v-if="currentReport">
        <div class="mb-4 p-3 bg-dark-card rounded">
          <div class="text-sm text-gray-400 mb-1">被举报对象</div>
          <div class="font-medium">{{ currentReport.targetName }}</div>
          <div class="text-sm text-warning mt-1">{{ currentReport.reason }}</div>
        </div>

        <el-form :model="handleForm" label-width="80px">
          <el-form-item label="处理动作">
            <el-radio-group v-model="handleForm.action">
              <el-radio :value="1">忽略</el-radio>
              <el-radio :value="2">警告</el-radio>
              <el-radio :value="3" class="text-danger">封禁</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="处理反馈">
            <el-input v-model="handleForm.feedback" type="textarea" :rows="3" placeholder="输入处理说明（可选）" />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="handleDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmHandle">确认处理</el-button>
      </template>
    </el-dialog>

    <!-- 图片预览 -->
    <el-dialog v-model="previewVisible" title="证据查看" width="600px">
      <img :src="previewUrl" class="w-full" />
    </el-dialog>
  </div>
</template>

<style scoped>
.report-card {
  @apply bg-dark-card rounded-lg p-4;
}

.line-clamp-2 {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
