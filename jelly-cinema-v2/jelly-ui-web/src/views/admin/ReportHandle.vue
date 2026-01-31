<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getReportList, handleReport, type ReportItem } from '@/api/admin'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const reports = ref<ReportItem[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(15)
const statusFilter = ref<number>(0)
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

function handleSearch() {
  pageNum.value = 1
  loadData()
}

function getTargetTypeLabel(type: number) {
  return targetTypeOptions.find(t => t.value === type)?.label || '未知'
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
  <div class="h-full flex flex-col gap-6 bg-gray-50 p-6">
    <!-- 顶部标题卡片 -->
    <div class="bg-white p-6 rounded-2xl shadow-sm border border-gray-100 flex flex-col md:flex-row justify-between items-center gap-4 animate-fade-in-down">
      <div class="flex items-center gap-4">
        <div class="w-12 h-12 rounded-xl bg-red-50 flex items-center justify-center text-red-600">
          <el-icon size="24"><svg-icon name="icon-jubao" /></el-icon>
        </div>
        <div>
          <h2 class="text-2xl font-bold text-gray-900 tracking-wide">举报管理</h2>
          <p class="text-gray-500 text-sm mt-1">处理用户提交的违规举报信息</p>
        </div>
      </div>
    </div>

    <!-- 数据区域 -->
    <div class="bg-white flex-1 rounded-2xl shadow-sm border border-gray-100 flex flex-col overflow-hidden animate-fade-in-up" style="animation-delay: 0.1s">
      <!-- 搜索筛选栏 -->
      <div class="p-5 border-b border-gray-100 flex gap-4 justify-between bg-gray-50/50">
        <div class="flex gap-4">
          <el-radio-group v-model="statusFilter" @change="handleSearch">
            <el-radio-button :value="0">待处理</el-radio-button>
            <el-radio-button :value="1">已处理</el-radio-button>
            <el-radio-button :value="2">已忽略</el-radio-button>
          </el-radio-group>
          
          <el-select v-model="targetTypeFilter" placeholder="举报类型" clearable class="w-40" @change="handleSearch">
            <el-option v-for="t in targetTypeOptions" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </div>
        
        <el-button circle @click="handleSearch">
          <el-icon><Refresh /></el-icon>
        </el-button>
      </div>

      <!-- 表格区域 -->
      <div class="flex-1 overflow-hidden p-4">
        <el-table 
          :data="reports" 
          v-loading="loading" 
          height="100%"
          style="width: 100%"
          :row-style="{ height: '72px' }"
        >
          <!-- 举报人 -->
          <el-table-column label="举报人" width="200">
            <template #default="{ row }">
              <div class="flex items-center gap-3">
                <el-avatar :size="36" :src="row.reporterAvatar" class="bg-gray-100">{{ row.reporterNickname?.[0] }}</el-avatar>
                <div class="flex flex-col">
                   <span class="font-medium text-gray-900">{{ row.reporterNickname }}</span>
                   <span class="text-xs text-gray-500">{{ formatTime(row.createTime) }}</span>
                </div>
              </div>
            </template>
          </el-table-column>

          <!-- 被举报对象 -->
          <el-table-column label="举报内容" min-width="300">
             <template #default="{ row }">
               <div class="flex flex-col gap-1">
                 <div class="flex items-center gap-2">
                    <el-tag size="small" type="info" effect="plain" class="border-gray-200 bg-gray-50 text-gray-600">
                      {{ getTargetTypeLabel(row.targetType) }}
                    </el-tag>
                    <span class="text-gray-900 font-medium">{{ row.targetName }}</span>
                 </div>
                 <div class="text-sm text-red-600 font-medium bg-red-50 px-2 py-0.5 rounded w-fit">
                    原因: {{ row.reason }}
                 </div>
                 <div v-if="row.description" class="text-xs text-gray-500 line-clamp-1">
                    {{ row.description }}
                 </div>
               </div>
             </template>
          </el-table-column>

          <!-- 证据 -->
          <el-table-column label="证据" width="180">
            <template #default="{ row }">
              <div v-if="row.evidenceImgs?.length" class="flex -space-x-2 overflow-hidden py-1">
                 <img
                    v-for="(img, idx) in row.evidenceImgs.slice(0, 3)"
                    :key="idx"
                    :src="img"
                    class="inline-block h-8 w-8 rounded-full ring-2 ring-white cursor-pointer object-cover shadow-sm"
                    @click="previewImage(img)"
                  />
                  <div v-if="row.evidenceImgs.length > 3" class="h-8 w-8 rounded-full bg-gray-100 flex items-center justify-center text-xs text-gray-600 ring-2 ring-white">
                    +{{ row.evidenceImgs.length - 3 }}
                  </div>
              </div>
              <span v-else class="text-gray-400 text-xs">无证据</span>
            </template>
          </el-table-column>

          <!-- 状态 -->
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.status === 0" type="warning" effect="light">待处理</el-tag>
              <el-tag v-else-if="row.status === 1" type="success" effect="light">已处理</el-tag>
              <el-tag v-else type="info" effect="light">已忽略</el-tag>
            </template>
          </el-table-column>

          <!-- 处理结果 -->
          <el-table-column label="处理结果" width="180">
             <template #default="{ row }">
               <span v-if="row.result" class="text-sm text-gray-600">{{ row.result }}</span>
               <span v-else class="text-xs text-gray-400">-</span>
             </template>
          </el-table-column>

          <!-- 操作 -->
          <el-table-column label="操作" width="150" fixed="right" align="center">
            <template #default="{ row }">
              <div v-if="row.status === 0" class="flex items-center justify-center gap-2">
                <el-button size="small" type="primary" plain @click="openHandleDialog(row)">处理</el-button>
                <el-button size="small" type="info" plain @click="quickIgnore(row)">忽略</el-button>
              </div>
              <span v-else class="text-gray-400 text-xs">已归档</span>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 分页 -->
      <div class="p-4 border-t border-gray-100 flex justify-end">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[15, 30, 50]"
          layout="total, sizes, prev, pager, next"
          @change="loadData"
          background
        />
      </div>
    </div>

    <!-- 处理弹窗 -->
    <el-dialog v-model="handleDialogVisible" title="处理举报" width="450px">
      <template v-if="currentReport">
        <div class="mb-6 p-4 bg-gray-50 rounded-xl border border-gray-100">
          <div class="flex items-center justify-between mb-2">
             <span class="text-sm text-gray-500">被举报对象</span>
             <el-tag size="small" type="info" effect="plain">{{ getTargetTypeLabel(currentReport.targetType) }}</el-tag>
          </div>
          <div class="text-base font-bold text-gray-900 mb-1">{{ currentReport.targetName }}</div>
          <div class="text-sm text-red-600 bg-red-50 px-2 py-1 rounded inline-block">违规原因: {{ currentReport.reason }}</div>
        </div>

        <el-form :model="handleForm" label-width="80px">
          <el-form-item label="动作">
            <el-radio-group v-model="handleForm.action">
              <el-radio :value="1">忽略</el-radio>
              <el-radio :value="2">警告</el-radio>
              <el-radio :value="3" class="text-red-500">封禁</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="反馈">
            <el-input 
              v-model="handleForm.feedback" 
              type="textarea" 
              :rows="3" 
              placeholder="请输入处理说明" 
            />
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
      <img :src="previewUrl" class="w-full rounded-lg" />
    </el-dialog>
  </div>
</template>

<style scoped>
/* 无需特殊的玻璃态样式覆盖，使用 Element Plus 默认样式即可 */
</style>
